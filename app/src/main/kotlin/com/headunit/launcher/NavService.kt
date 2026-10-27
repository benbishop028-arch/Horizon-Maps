package com.headunit.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Keeps navigation running while another app is in front (music, phone projection...).
 * It does no work itself: being a foreground location service lets the app keep its GPS updates and voice
 * guidance going in the background. It runs only while a route is active and shows a notification that opens the map.
 */
class NavService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT).orEmpty().ifBlank { "Navigating" }
        val n = notification(this, text)
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(ID, n)
        } catch (e: Exception) {
            // not allowed to start in the foreground right now (e.g. no location permission): just stop
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val ID = 7
        private const val CHANNEL = "nav"
        private const val EXTRA_TEXT = "text"

        private fun notification(ctx: Context, text: String): Notification {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Navigation", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while a route is running"
                    setShowBadge(false)
                })
            }
            val open = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_nav)
                .setContentTitle("Horizon Nav")
                .setContentText(text)
                .setContentIntent(open)
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
    }
}
