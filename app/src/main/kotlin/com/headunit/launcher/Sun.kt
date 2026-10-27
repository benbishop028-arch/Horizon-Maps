package com.headunit.launcher

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Sunrise / sunset from a position and the date (NOAA approximation, good to a few minutes). */
object Sun {
    private fun norm(x: Double) = ((x % 1440.0) + 1440.0) % 1440.0

    private fun times(lat: Double, lon: Double, nowMs: Long): Pair<Double, Double>? {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = nowMs }
        val n = cal.get(Calendar.DAY_OF_YEAR)
        val g = 2 * PI / 365.0 * (n - 1 + (cal.get(Calendar.HOUR_OF_DAY) - 12) / 24.0)
        val eq = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val la = Math.toRadians(lat)
        val cosHa = cos(Math.toRadians(90.833)) / (cos(la) * cos(decl)) - tan(la) * tan(decl)
        if (cosHa > 1.0 || cosHa < -1.0) return null
        val ha = Math.toDegrees(acos(cosHa))
        return Pair(norm(720 - 4 * (lon + ha) - eq), norm(720 - 4 * (lon - ha) - eq))
    }

    fun isDay(lat: Double, lon: Double, nowMs: Long): Boolean {
        val t = times(lat, lon, nowMs)
        if (t == null) {
            // polar day or polar night: decide by the sign of the declination effect (summer = day)
            val month = Calendar.getInstance().get(Calendar.MONTH)
            val northSummer = month in 3..8
            return if (lat >= 0) northSummer else !northSummer
        }
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = nowMs }
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val (sr, ss) = t
        return if (sr < ss) minutes >= sr && minutes < ss else minutes >= sr || minutes < ss
    }

    /**
     * "dawn" from 40 minutes before to 25 minutes after sunrise, "dusk" from 25 minutes before to 40 minutes after sunset,
     * otherwise "" (also in polar day or night).
     */
    fun phase(lat: Double, lon: Double, nowMs: Long): String {
        val t = times(lat, lon, nowMs) ?: return ""
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = nowMs }
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60.0 + cal.get(Calendar.MINUTE)
        fun near(x: Double, before: Double, after: Double): Boolean {
            val d = ((minutes - x + 720.0) % 1440.0 + 1440.0) % 1440.0 - 720.0
            return d >= -before && d <= after
        }
        return when {
            near(t.first, 40.0, 25.0) -> "dawn"
            near(t.second, 25.0, 40.0) -> "dusk"
            else -> ""
        }
    }
}
