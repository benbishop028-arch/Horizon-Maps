package com.headunit.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.os.SystemClock

/**
 * Keeps navigation running while another app is in front (music, phone projection...).
 * It does no work itself: being a foreground location service lets the app keep its GPS updates and voice
 * guidance going in the background. It runs only while a route is active and shows a notification that opens the map.
 *
 * While navigating, the notification also shows the next turn (arrow, distance and road), marked as a navigation
 * notification, so car launchers, smartwatches and other screens that read navigation notifications can show it.
 */
class NavService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT).orEmpty().ifBlank { "Navigating" }
        plainText = text
        lastSig = ""
        val n = notification(this, text)
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(ID, n)
            running = true
        } catch (e: Exception) {
            // not allowed to start in the foreground right now (e.g. no location permission): just stop
            running = false
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        lastSig = ""
        super.onDestroy()
    }

    companion object {
        private const val ID = 7
        private const val CHANNEL = "nav"
        private const val EXTRA_TEXT = "text"
        private const val GREEN = 0xFF1F6F43.toInt()

        @Volatile private var running = false
        @Volatile private var plainText = "Navigating"
        private var lastSig = ""
        private var lastPost = 0L
        private val arrows = HashMap<Int, Bitmap>()

        private fun channel(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Navigation", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while a route is running, with the next turn"
                    setShowBadge(false)
                })
            }
        }

        private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun notification(ctx: Context, text: String): Notification {
            channel(ctx)
            return Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_nav)
                .setContentTitle("Horizon Nav")
                .setContentText(text)
                .setContentIntent(openApp(ctx))
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        }

        fun start(ctx: Context, text: String) {
            try {
                val i = Intent(ctx, NavService::class.java).putExtra(EXTRA_TEXT, text)
                ctx.startForegroundService(i)
            } catch (e: Exception) { /* background start not allowed: guidance still works while the app is open */ }
        }

        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, NavService::class.java)) } catch (e: Exception) { }
        }

        /** The arrow picture for each kind of turn (the same arrows Android Auto's turn card uses). */
        private fun arrowRes(maneuver: String, exit: Int, left: Boolean): Int {
            val m = maneuver.uppercase()
            return when {
                m.startsWith("ROUNDABOUT") -> if (left) R.drawable.ic_man_roundabout_cw else R.drawable.ic_man_roundabout_ccw
                m == "TURN_LEFT" -> R.drawable.ic_man_turn_left
                m == "TURN_RIGHT" -> R.drawable.ic_man_turn_right
                m == "SHARP_LEFT" -> R.drawable.ic_man_sharp_left
                m == "SHARP_RIGHT" -> R.drawable.ic_man_sharp_right
                m == "BEAR_LEFT" || m.startsWith("MOTORWAY_EXIT_LEFT") -> R.drawable.ic_man_slight_left
                m == "BEAR_RIGHT" || m.startsWith("MOTORWAY_EXIT_RIGHT") -> R.drawable.ic_man_slight_right
                m == "KEEP_LEFT" -> R.drawable.ic_man_keep_left
                m == "KEEP_RIGHT" -> R.drawable.ic_man_keep_right
                m.contains("UTURN") || m.contains("U_TURN") -> if (left) R.drawable.ic_man_uturn_right else R.drawable.ic_man_uturn_left
                m.startsWith("ARRIVE") -> R.drawable.ic_man_destination
                else -> R.drawable.ic_man_straight
            }
        }

        /** What to do, in words: "Turn left", "Take exit 2", "Arrive"... */
        private fun what(t: TurnInfo): String {
            val m = t.maneuver.uppercase()
            return when {
                m.startsWith("ROUNDABOUT") && t.exit > 0 -> "Take exit ${t.exit}"
                m.startsWith("ROUNDABOUT") -> "At the roundabout"
                m.startsWith("ARRIVE") -> "Arrive"
                t.tag.isNotBlank() -> t.tag
                m == "STRAIGHT" || m == "FOLLOW" -> "Continue"
                m == "DEPART" -> "Head off"
                m.startsWith("MOTORWAY_EXIT_LEFT") -> "Exit left"
                m.startsWith("MOTORWAY_EXIT_RIGHT") -> "Exit right"
                else -> m.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
            }
        }

        /** The arrow, white on a green rounded square, for the notification's big picture (made once per arrow). */
        private fun bigArrow(ctx: Context, res: Int): Bitmap? = arrows[res] ?: try {
            val size = 144
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 32f, 32f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN })
            val d = ctx.getDrawable(res)
            if (d != null) {
                val pad = 20
                d.setBounds(pad, pad, size - pad, size - pad)
                d.draw(c)
            }
            if (arrows.size > 20) arrows.clear()
            arrows[res] = bmp
            bmp
        } catch (e: Throwable) { null }

        /**
         * Shows the next turn in the notification. Cheap to call often: it only posts when what's shown changes
         * (the distance as displayed, the turn or the road), and at most about once a second, so it never floods
         * the system (Android drops apps that update notifications too fast).
         * [on] false (the setting is off) keeps the plain "Navigating to ..." notification.
         */
        fun update(ctx: Context, t: TurnInfo?, eta: EtaInfo?, imperial: Boolean, left: Boolean, on: Boolean) {
            if (!running) return
            try {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (!on || t == null) {
                    if (lastSig != "plain") {
                        lastSig = "plain"
                        nm.notify(ID, notification(ctx, plainText))
                    }
                    return
                }
                val dist = Geo.fmtDist(t.dist, imperial)
                val doing = what(t)
                val road = t.street.ifBlank { t.sign }
                val arrive = eta?.arrive.orEmpty()
                val res = arrowRes(t.maneuver, t.exit, left)
                val sig = "$dist|$doing|$road|$arrive|$res"
                if (sig == lastSig) return
                val now = SystemClock.elapsedRealtime()
                val sameTurn = lastSig.substringAfter('|') == sig.substringAfter('|')
                if (sameTurn && now - lastPost < 1000) return
                lastSig = sig
                lastPost = now
                channel(ctx)
                val b = Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(res)
                    .setContentTitle("$dist · $doing")
                    .setContentText(road.ifBlank { plainText })
                    .setContentIntent(openApp(ctx))
                    .setCategory(Notification.CATEGORY_NAVIGATION)
                    .setColor(GREEN)
                    .setShowWhen(false)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                if (arrive.isNotBlank()) b.setSubText("Arrive $arrive")
                bigArrow(ctx, res)?.let { b.setLargeIcon(it) }
                nm.notify(ID, b.build())
            } catch (e: Throwable) { /* a notification problem must never stop guidance */ }
        }
    }
}
