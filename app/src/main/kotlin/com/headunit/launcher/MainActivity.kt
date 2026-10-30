package com.headunit.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.headunit.launcher.ui.App
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private lateinit var map: MapBinding
    private lateinit var engine: NavEngine

    private val perm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private fun hasLocation() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** A route is being followed (not just previewed) or the demo drive is on, so guidance should carry on when another app is in front. */
    private fun guiding() = (engine.route != null && !engine.previewing) || engine.simMode != 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // keep the last crash so it can be read in Settings > About & backup
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        val crashFile = java.io.File(applicationContext.filesDir, "lastcrash.txt")
        val boot = getSharedPreferences(CrashGuardPrefs.FILE, android.content.Context.MODE_PRIVATE)
        val startedAt = System.currentTimeMillis()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { crashFile.writeText("Thread ${t.name}\n" + android.util.Log.getStackTraceString(e).take(3000)) } catch (x: Throwable) { }
            // a crash in the first 25 seconds counts as "crashed while starting"
            try { if (System.currentTimeMillis() - startedAt < 25_000L) boot.edit().putInt(CrashGuardPrefs.START_FAILURES, boot.getInt(CrashGuardPrefs.START_FAILURES, 0) + 1).commit() } catch (x: Throwable) { }
            prev?.uncaughtException(t, e)
        }
        // freezes ("isn't responding") don't reach the crash handler: watch for them, and pick up Android's record of the last one
        FreezeWatch.start(applicationContext)
        Thread { FreezeWatch.collectLastExit(applicationContext) }.start()
        // it crashed while starting twice running: show a plain page that can send the crash report (it doesn't start the map),
        // instead of crashing again before anything can be tapped
        if (boot.getInt(CrashGuardPrefs.START_FAILURES, 0) >= 2 && crashFile.exists()) {
            setContent { StartupCrashPage(crashReport(crashFile)) { boot.edit().putInt(CrashGuardPrefs.START_FAILURES, 0).commit(); recreate() } }
            return
        }
        // running fine for 25 seconds: clear the counts and the "starting" marker
        window.decorView.postDelayed({ try { boot.edit().putInt(CrashGuardPrefs.START_FAILURES, 0).putBoolean(CrashGuardPrefs.STARTING, false).putInt(CrashGuardPrefs.BAD_STARTS, 0).apply() } catch (x: Throwable) { } }, 25_000L)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideBars()
        // on Android 10 and older the bars come back whenever anything pops up (a dialog, the keyboard, a system message): hide them again
        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { vis ->
            if (vis and android.view.View.SYSTEM_UI_FLAG_FULLSCREEN == 0) window.decorView.postDelayed({ if (!isFinishing) hideBars() }, 2500)
        }

        val prefs = Prefs(applicationContext)
        // the Mapbox map needs OpenGL ES 3.0: on an older unit, say so plainly instead of showing a black map (or closing)
        val gl = try { (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).deviceConfigurationInfo.reqGlEsVersion } catch (e: Throwable) { 0x30000 }
        if (gl in 1 until 0x30000) {
            setContent { NoGlesPage(gl) }
            return
        }
        // Safe start. Some freezes never reach the crash handler: the graphics driver takes the app (or the whole head unit) down
        // with it, so nothing is recorded and it just happens again on every start. A marker is set while starting and cleared
        // once the map has run for 25 seconds, or when the app is closed normally; if it's still set on the next start, that start
        // went wrong. After three in a row the heavy map features are turned down (a fourth turns the map down to Lightest), and
        // the app says so once, so it can be turned back up in Settings.
        val safeLevel = try { safeStart(boot, prefs) } catch (e: Throwable) { 0 }
        // the whole head unit restarting while the app was open (which nothing above can see): after two, safer drawing
        val sessionStep = try { SessionWatch.onStartup(applicationContext, prefs) } catch (e: Throwable) { 0 }
        // the Mapbox token from Settings, before the map is made
        val tok = MapboxRest.clean(prefs.s(PreferenceKeys.MAPBOX_TOKEN, ""))
        try { prefs.migrateToMapbox() } catch (e: Throwable) { }
        if (tok.isNotEmpty()) try { com.mapbox.common.MapboxOptions.accessToken = tok } catch (e: Throwable) { }
        map = MapBinding(this, prefs.b(PreferenceKeys.MAPFAST, true), (prefs.s(PreferenceKeys.MAP_RESOLUTION, "80").toIntOrNull() ?: 80) / 100f).also { it.setArrowScale((prefs.s(PreferenceKeys.ARROW_PERCENT, "240").toIntOrNull() ?: 240) / 100f) }
        lifecycle.addObserver(map)
        // open where you were last time (the GPS takes over as soon as it answers)
        try {
            val sp = prefs.s(PreferenceKeys.STARTPOS, "").split(',')
            val la = sp.getOrNull(0)?.toDoubleOrNull(); val lo = sp.getOrNull(1)?.toDoubleOrNull(); val br = sp.getOrNull(2)?.toDoubleOrNull()
            if (la != null && lo != null) map.startAt(LL(la, lo), br?.takeIf { it >= 0 })
        } catch (e: Throwable) { }
        engine = NavEngine(applicationContext, prefs, map)
        SessionWatch.speed = { try { engine.fix?.speed ?: 0.0 } catch (e: Throwable) { 0.0 } }
        SessionWatch.perf = { try { engine.perfLine } catch (e: Throwable) { "" } }
        SessionWatch.resumed(applicationContext)
        if (sessionStep > 0) window.decorView.postDelayed({ try { engine.toast(SessionWatch.message(sessionStep), 12_000) } catch (e: Throwable) { } }, 5000L)
        if (safeLevel > 0) window.decorView.postDelayed({
            try { engine.toast(if (safeLevel >= 2) "Horizon Nav had trouble starting, so the map is on Lightest. You can change it in Settings > Map look" else "Horizon Nav had trouble starting, so the heaviest 3D map features are off. You can turn them back on in Settings > Map look and On the map", 10_000) } catch (e: Throwable) { }
        }, 4000L)

        setContent { App(prefs, engine, map) }

        // while a route runs, a foreground service keeps GPS and voice going in the background; it stops when the route ends
        lifecycleScope.launch {
            snapshotFlow { if (guiding()) (engine.dest?.name ?: "your destination") else null }
                .distinctUntilChanged()
                .collect { to ->
                    if (to != null && hasLocation()) NavService.start(applicationContext, "Navigating to $to")
                    else if (to == null) {
                        NavService.stop(applicationContext)
                        // the route ended (arrived or cancelled) while another app was in front: GPS can rest too
                        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) engine.stopLocation()
                    }
                }
        }

        if (savedInstanceState == null) handleShared(intent)

        val wanted = arrayListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        val missing = wanted.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) perm.launch(missing.toTypedArray())
    }

    /** Hides the status and navigation bars: the newer way, plus the older flags that Android 10 and older head units go by. */
    private fun hideBars() {
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            if (Build.VERSION.SDK_INT < 30) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
            }
        } catch (e: Exception) { }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // coming back from another app, a dialog or the notification shade: the bars may be showing again
        if (hasFocus) hideBars()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::engine.isInitialized) handleShared(intent)
    }

    /** A destination shared from another app (a Google Maps link, a geo: link or an address). */
    private fun handleShared(i: android.content.Intent?) {
        val text = ExternalLinks.textOf(i) ?: return
        lifecycleScope.launch {
            val t = try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ExternalLinks.parse(text) } } catch (e: Exception) { null }
            // wait (briefly) for a first position so the route can start from where you are
            var n = 0
            while (t?.pos != null && engine.fix == null && n < 20) { kotlinx.coroutines.delay(500); n++ }
            if (t != null) engine.openShared(t) else engine.toast("Couldn't read a place from that", 3500)
        }
    }

    override fun onResume() {
        super.onResume()
        hideBars()
        if (!::engine.isInitialized) return
        // back in front: look for a new version and new map data
        engine.onOpened()
        // after the permission dialog closes, wait a beat so the UI is settled before starting GPS
        if (hasLocation()) window.decorView.postDelayed({ if (!isFinishing && !isDestroyed) engine.startLocation() }, 400)
    }

    override fun onStart() {
        super.onStart()
        if (::engine.isInitialized) { engine.appVisible = true; SessionWatch.resumed(applicationContext) }
    }

    override fun onStop() {
        super.onStop()
        // closed or put away normally: not a failed start
        try { SessionWatch.stopped(applicationContext) } catch (e: Throwable) { }
        try { getSharedPreferences(CrashGuardPrefs.FILE, android.content.Context.MODE_PRIVATE).edit().putBoolean(CrashGuardPrefs.STARTING, false).apply() } catch (e: Throwable) { }
        if (!::engine.isInitialized) return
        engine.appVisible = false
        // with no route there is nothing to guide, so GPS is switched off while the app is in the background
        if (!guiding()) engine.stopLocation()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!::engine.isInitialized) return
        NavService.stop(applicationContext)
        engine.shutdown()
    }
}

/** The crash report for the startup-crash page: the app, the unit and what went wrong. */
private fun crashReport(f: java.io.File): String = buildString {
    appendLine("Horizon Nav ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR}) crashed while starting")
    appendLine("Unit: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), 64-bit: ${Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()} (${Build.SUPPORTED_ABIS.joinToString()})")
    appendLine()
    append(try { f.readText().take(3000) } catch (e: Exception) { "(couldn't read the crash)" })
}

@androidx.compose.runtime.Composable
private fun NoGlesPage(gl: Int) {
    val bg = androidx.compose.ui.graphics.Color(0xFF15171C)
    val fg = androidx.compose.ui.graphics.Color(0xFFF2F3F5)
    androidx.compose.foundation.layout.Column(
        androidx.compose.ui.Modifier.fillMaxSize().background(bg).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
    ) {
        androidx.compose.material3.Text("This head unit can't draw the Mapbox map", color = fg, fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        androidx.compose.material3.Text("Horizon Nav needs OpenGL ES 3.0 graphics, and this unit has version ${gl shr 16}.${gl and 0xFFFF}. " +
            "Versions of Horizon Nav from before the Mapbox map work on older graphics, if you still have one.", color = fg.copy(alpha = 0.8f), fontSize = 16.sp)
    }
}

/**
 * Notes this start and, if the last start never got going (see onCreate), turns the map down. Returns 0 (normal), 1 (heavy
 * features off) or 2 (map on Lightest as well). Only ever turns things down, and only after repeated failures.
 */
private fun safeStart(boot: android.content.SharedPreferences, prefs: Prefs): Int {
    val bad = if (boot.getBoolean(CrashGuardPrefs.STARTING, false)) boot.getInt(CrashGuardPrefs.BAD_STARTS, 0) + 1 else 0
    boot.edit().putBoolean(CrashGuardPrefs.STARTING, true).putInt(CrashGuardPrefs.BAD_STARTS, bad).commit()
    // (three in a row before anything changes: a head unit losing power within 25 seconds of starting looks the same, and that
    // happening once or twice shouldn't turn anyone's map down)
    if (bad < 3) return 0
    // the heaviest drawing: 3D street furniture and lamps, real terrain, rain and snow on the map, the 3D model locator, facades
    // and landmarks, and an uncapped frame rate
    for (k in listOf(PreferenceKeys.LAMPS, PreferenceKeys.FURNITURE3D, PreferenceKeys.TERRAIN, PreferenceKeys.RAIN_EFFECTS,
        PreferenceKeys.FACADES, PreferenceKeys.LANDMARKS3D)) prefs.put(k, false)
    if (prefs.s(PreferenceKeys.LOCATOR, "arrow") == "model") prefs.put(PreferenceKeys.LOCATOR, "arrow")
    prefs.put(PreferenceKeys.MAP_FPS, "30")
    if (bad >= 4) { prefs.put(PreferenceKeys.MAP_DETAIL, "lightest"); return 2 }
    if (prefs.s(PreferenceKeys.MAP_DETAIL, "full") == "full") prefs.put(PreferenceKeys.MAP_DETAIL, "lite")
    return 1
}

@androidx.compose.runtime.Composable
private fun StartupCrashPage(report: String, onRetry: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bg = androidx.compose.ui.graphics.Color(0xFF15171C)
    val fg = androidx.compose.ui.graphics.Color(0xFFF2F3F5)
    androidx.compose.foundation.layout.Column(
        androidx.compose.ui.Modifier.fillMaxSize().background(bg).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
    ) {
        androidx.compose.material3.Text("Horizon Nav keeps closing while it starts", color = fg, fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        androidx.compose.material3.Text("Sorry about that. Sending the report below lets it be fixed for this head unit. Nothing personal is in it.", color = fg.copy(alpha = 0.75f), fontSize = 15.sp)
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.Button({
                val url = "https://github.com/${BuildConfig.UPDATE_REPO}/issues/new?title=" + android.net.Uri.encode("Crash on start: ${Build.MANUFACTURER} ${Build.MODEL}") + "&body=" + android.net.Uri.encode(report.take(5000))
                try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (e: Exception) { android.widget.Toast.makeText(ctx, "No browser on this unit: use Copy instead", android.widget.Toast.LENGTH_LONG).show() }
            }) { androidx.compose.material3.Text("Send on GitHub") }
            androidx.compose.material3.Button({
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Horizon Nav crash", report))
                android.widget.Toast.makeText(ctx, "Copied: paste it into a message or a Reddit comment", android.widget.Toast.LENGTH_LONG).show()
            }) { androidx.compose.material3.Text("Copy") }
            androidx.compose.material3.OutlinedButton(onRetry) { androidx.compose.material3.Text("Try again", color = fg) }
        }
        androidx.compose.material3.Text(report, color = fg.copy(alpha = 0.6f), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}
