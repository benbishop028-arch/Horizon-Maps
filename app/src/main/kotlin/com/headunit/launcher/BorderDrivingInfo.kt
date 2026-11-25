package com.headunit.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Locale

/**
 * Reference passenger-car speed limits and emergency numbers for border-crossing notices.
 * Values are general defaults; posted signs, temporary limits, weather and vehicle-specific rules take precedence.
 * Speed-limit reference: ITF/IRTAD Road Safety Annual Report 2025 (data valid 12 December 2025).
 */
internal data class BorderDrivingInfo(
    val code: String, val country: String, val limits: String, val unit: String, val emergency: String,
) {
    fun notificationText(): String {
        val speed = if (unit.isBlank()) limits else "$limits $unit"
        return "Typical car limits (urban / rural / motorway): $speed. Emergency: $emergency. Follow local signs; limits can vary by road, conditions and vehicle."
    }

    companion object {
        private data class Rule(val limits: String, val unit: String = "km/h", val emergency: String = "112")
        private val rules = mapOf(
            "NO" to Rule("50 / 80 / 90–110", emergency = "112 police · 113 ambulance · 110 fire"),
            "SE" to Rule("50 / 60–100 / 110–120"),
            "DK" to Rule("50 / 80 / 110–130"),
            "FI" to Rule("30–60 / 80–100 / 100–120"),
            "IS" to Rule("50 / 80–90 / no motorways"),
            "GB" to Rule("30 / 60 / 70", "mph", "999 or 112"),
            "IE" to Rule("up to 60 / 60–100 / 120", emergency = "112 or 999"),
            "FR" to Rule("50 / 80–90 / 130 (110 in wet weather)"),
            "DE" to Rule("50 / 100 / none (130 recommended)", emergency = "112 ambulance/fire · 110 police"),
            "NL" to Rule("30–50 / 60–80 / 100 by day; 100–130 at night"),
            "BE" to Rule("30–50 / 70–90 / 120"),
            "LU" to Rule("50 / 90 / 130 (110 in wet weather)"),
            "ES" to Rule("20–50 / 90 / 120"),
            "PT" to Rule("50 / 90 / 120"),
            "IT" to Rule("50 / 70–90 / 130"),
            "AT" to Rule("50 / 100 / 130"),
            "CH" to Rule("50 / 80 / 120"),
            "PL" to Rule("50 / 90–100 / 140"),
            "CZ" to Rule("50 / 90 / 130"),
            "SK" to Rule("50 / 90 / 130"),
            "SI" to Rule("50 / 90 / 130"),
            "HR" to Rule("50 / 90 / 130"),
            "HU" to Rule("50 / 90 / 130"),
            "RO" to Rule("50 / 90 / 130"),
            "BG" to Rule("50 / 90 / 140"),
            "GR" to Rule("30–50 / 90 / 130 (urban rules changed in 2026)"),
            "EE" to Rule("50 / 90 / 110–120"),
            "LV" to Rule("50 / 90 / 120"),
            "LT" to Rule("50 / 90 / 130"),
            "CY" to Rule("50 / 80 / 100"),
            "MT" to Rule("50 / 80 / no motorways"),
            "TR" to Rule("50 / 90 / 120"),
            "US" to Rule("Varies by state and road", "mph", "911"),
            "CA" to Rule("40–70 / 80–90 / 100–110", "km/h", "911"),
            "AU" to Rule("50 / 100–110 / 100–110 (up to 130 in Northern Territory)", "km/h", "000"),
            "NZ" to Rule("50 / 100 / 100 (some sections 110–120)", "km/h", "111"),
        )

        fun forCountry(code: String): BorderDrivingInfo? {
            val cc = code.trim().uppercase()
            if (cc !in Locale.getISOCountries()) return null
            val country = Locale("", cc).getDisplayCountry(Locale.UK).ifBlank { cc }
            val rule = rules[cc]
            val other112 = setOf("AL", "AD", "BA", "LI", "MD", "ME", "MK", "NO", "RS", "CH", "TR")
            return BorderDrivingInfo(
                code = cc, country = country,
                limits = rule?.limits ?: "Check posted signs (no verified default in the reference list)",
                unit = rule?.unit.orEmpty(),
                emergency = rule?.emergency ?: if (cc in other112) "112" else "Not verified in the offline reference list",
            )
        }
    }
}

/** A separate, auto-cancelling notification so the ongoing navigation notification is never replaced. */
internal object BorderCrossingNotifier {
    private const val CHANNEL = "border_crossings"
    private const val ID = 389

    fun show(ctx: Context, info: BorderDrivingInfo) {
        try {
            val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL, "Country changes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Speed-limit and emergency-number information when entering another country"
                    setShowBadge(false)
                })
            }
            val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            val pending = PendingIntent.getActivity(ctx, ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val body = info.notificationText()
            val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else {
                @Suppress("DEPRECATION")
                Notification.Builder(ctx)
            }
            val notification = builder.setSmallIcon(R.drawable.ic_stat_nav)
                .setContentTitle("Entering ${info.country}")
                .setContentText("Emergency: ${info.emergency} · ${info.limits} ${info.unit}".trim())
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setContentIntent(pending)
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            manager.notify(ID, notification)
        } catch (_: SecurityException) {
            // Notifications may be disabled by the user or denied by Android 13's runtime permission.
        } catch (_: Exception) { }
    }
}
