package com.headunit.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * What's playing on the head unit (any music or radio app), for the media bar on the driving screen (Reddit, mrEnmi).
 *
 * Play / pause, next and back always work: they're sent as the media buttons a steering wheel or keyboard would press, which the
 * app that last played picks up, with no permission. The song, artist and artwork need notification access (the same permission
 * home screen launchers use for their media widget); with it the bar follows the app's own media session and controls it directly.
 * Without it the bar shows only while something is playing (Android says whether music is playing; that's all it says).
 *
 * Light on purpose for 2 GB head units: nothing runs while the driving screen isn't showing, with access it only listens (no
 * polling), without it it asks Android once every 3 seconds, and the artwork is kept small (96 px).
 */
object MediaNow {
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var app by mutableStateOf("")
    var art by mutableStateOf<ImageBitmap?>(null)
    var playing by mutableStateOf(false)
    /** When something was last heard playing (uptime ms), so the bar stays a few minutes after a pause. */
    var lastPlayAt by mutableStateOf(0L)
    var hasAccess by mutableStateOf(false)

    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private var users = 0
    private var mgr: MediaSessionManager? = null
    private var ctrl: MediaController? = null
    private var artFrom: Bitmap? = null
    private var activeHits = 0

    /** How long the bar stays after the music stops. */
    private const val KEEP_MS = 3 * 60_000L

    /** The bar is worth showing: playing now, or paused within the last few minutes. */
    fun showing(now: Long = SystemClock.uptimeMillis()): Boolean = playing || (lastPlayAt > 0L && now - lastPlayAt < KEEP_MS)

    /**
     * The song and artwork need notification access, which only the Google Play edition asks for: Play Protect blocks apps that
     * ask for it when installed from a file (GitHub, desko1973). The GitHub edition's bar has the buttons without the song.
     */
    val songInfoPossible: Boolean get() = BuildConfig.STORE == "play"

    fun accessGranted(c: Context): Boolean = songInfoPossible && try {
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)
    } catch (e: Throwable) { false }

    /** The Android page where notification access is given (some head units hide it: then the app's own info page). */
    fun openAccessSettings(c: Context) {
        val tries = listOf(
            android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + c.packageName)),
        )
        for (i in tries) {
            try { c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: Throwable) { }
        }
    }

    /** The driving screen is showing: start following what's playing. Pairs with [stop]. */
    fun start(c: Context) {
        users++
        if (users > 1) return
        ctx = c.applicationContext
        connect()
        main.post(poll)
    }

    fun stop() {
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        main.removeCallbacks(poll)
        disconnect()
    }

    /** Notification access was just given or taken away (MediaListener calls this, as does coming back from the settings page). */
    fun recheck() { main.post { if (users > 0) { disconnect(); connect() } } }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> main.post { pick(list) } }

    private fun connect() {
        val c = ctx ?: return
        hasAccess = accessGranted(c)
        if (!hasAccess) return
        try {
            val m = c.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val who = ComponentName(c, MediaListener::class.java)
            m.addOnActiveSessionsChangedListener(sessionsChanged, who, main)
            mgr = m
            pick(m.getActiveSessions(who))
        } catch (e: Throwable) {
            // (access can be listed but not yet usable for a moment after it's given)
            hasAccess = false
        }
    }

    private fun disconnect() {
        try { mgr?.removeOnActiveSessionsChangedListener(sessionsChanged) } catch (e: Throwable) { }
        mgr = null
        follow(null)
    }

    /** The session to follow: one that's playing, else the most recent (Android lists the most recent first). Never our own. */
    private fun pick(list: List<MediaController>?) {
        val c = ctx ?: return
        val others = list.orEmpty().filter { it.packageName != c.packageName }
        val best = others.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: others.firstOrNull()
        if (best?.sessionToken != ctrl?.sessionToken) follow(best)
    }

    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { readMeta(metadata) }
        override fun onPlaybackStateChanged(state: PlaybackState?) { readState(state) }
        override fun onSessionDestroyed() { main.post { follow(null); try { mgr?.let { pick(it.getActiveSessions(ComponentName(ctx ?: return@post, MediaListener::class.java))) } } catch (e: Throwable) { } } }
    }

    private fun follow(m: MediaController?) {
        try { ctrl?.unregisterCallback(cb) } catch (e: Throwable) { }
        ctrl = m
        if (m == null) {
            if (hasAccess) { title = ""; artist = ""; art = null; artFrom = null; playing = false }
            return
        }
        try { m.registerCallback(cb, main) } catch (e: Throwable) { }
        app = try {
            val pm = ctx!!.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(m.packageName, 0)).toString()
        } catch (e: Throwable) { "" }
        readMeta(m.metadata)
        readState(m.playbackState)
    }

    private fun readMeta(md: MediaMetadata?) {
        // (a field sent empty counts as missing: some players send an empty title and put the name in the display title or the
        // description instead, which left the bar saying just "Media", bijindas)
        fun f(k: String): String? = try { md?.getString(k)?.trim()?.takeIf { it.isNotEmpty() } } catch (e: Throwable) { null }
        val desc = try { md?.description } catch (e: Throwable) { null }
        title = f(MediaMetadata.METADATA_KEY_TITLE) ?: f(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: desc?.title?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: ""
        artist = f(MediaMetadata.METADATA_KEY_ARTIST) ?: f(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: f(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: f(MediaMetadata.METADATA_KEY_ALBUM) ?: desc?.subtitle?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: ""
        val b = try {
            md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        } catch (e: Throwable) { null }
        if (b === artFrom) return
        artFrom = b
        art = try {
            if (b == null || b.isRecycled) null
            else {
                // small: the bar shows it at about 36 dp
                val s = 96f / maxOf(b.width, b.height).coerceAtLeast(1)
                (if (s < 1f) Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true) else b).asImageBitmap()
            }
        } catch (e: Throwable) { null }
    }

    private fun readState(st: PlaybackState?) {
        val now = st?.state == PlaybackState.STATE_PLAYING || st?.state == PlaybackState.STATE_BUFFERING
        if (now || playing) lastPlayAt = SystemClock.uptimeMillis()
        playing = now
    }

    /** Without access: is anything playing? Asked every 3 seconds while the driving screen shows. Twice running counts, so a
     *  spoken direction on the Media channel doesn't bring the bar up by itself. */
    private val poll = object : Runnable {
        override fun run() {
            val c = ctx
            if (users <= 0 || c == null) return
            // (the few minutes after a pause are up: hide the bar)
            if (!playing && lastPlayAt > 0L && SystemClock.uptimeMillis() - lastPlayAt >= KEEP_MS) lastPlayAt = 0L
            if (!hasAccess) {
                // (given access in Android's settings while we were away)
                if (accessGranted(c)) { connect(); if (hasAccess) { main.postDelayed(this, 3000L); return } }
                val am = c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val active = try { am?.isMusicActive == true } catch (e: Throwable) { false }
                activeHits = if (active) activeHits + 1 else 0
                val now = activeHits >= 2
                if (now || playing) lastPlayAt = SystemClock.uptimeMillis()
                playing = now
            }
            main.postDelayed(this, 3000L)
        }
    }

    /** "toggle", "next" or "prev". */
    fun press(c: Context, what: String) {
        val m = ctrl
        if (m != null) {
            try {
                val t = m.transportControls
                when (what) {
                    "next" -> t.skipToNext()
                    "prev" -> t.skipToPrevious()
                    else -> if (m.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
                }
                return
            } catch (e: Throwable) { }
        }
        val code = when (what) { "next" -> KeyEvent.KEYCODE_MEDIA_NEXT; "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS; else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE }
        try {
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val t = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
            am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
        } catch (e: Throwable) { }
        // (no access: show the change straight away rather than wait for the next check)
        if (!hasAccess && what == "toggle") { playing = !playing; activeHits = if (playing) 2 else 0; lastPlayAt = SystemClock.uptimeMillis() }
    }
}

/**
 * Only here so Android can give Horizon Nav notification access, which is what lets the media bar see what's playing. It reads no
 * notifications.
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() { MediaNow.recheck() }
    override fun onListenerDisconnected() { MediaNow.recheck() }
}
