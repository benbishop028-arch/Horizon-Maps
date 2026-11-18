package com.headunit.launcher

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.asin
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

    /**
     * Where the sun is: [first] its direction in degrees clockwise from north, [second] its height above the horizon in degrees
     * (below zero at night). NOAA approximation, good to about a degree.
     */
    fun position(lat: Double, lon: Double, nowMs: Long): Pair<Double, Double> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = nowMs }
        val n = cal.get(Calendar.DAY_OF_YEAR)
        val hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0
        val g = 2 * PI / 365.0 * (n - 1 + (hour - 12) / 24.0)
        val eq = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val trueMin = hour * 60 + eq + 4 * lon
        val ha = Math.toRadians(trueMin / 4 - 180)
        val la = Math.toRadians(lat)
        val cosZen = (sin(la) * sin(decl) + cos(la) * cos(decl) * cos(ha)).coerceIn(-1.0, 1.0)
        val zen = acos(cosZen)
        val elev = 90 - Math.toDegrees(zen)
        val sz = sin(zen)
        var az = if (sz < 1e-6) 180.0 else {
            val c = ((sin(la) * cosZen - sin(decl)) / (cos(la) * sz)).coerceIn(-1.0, 1.0)
            val a = Math.toDegrees(acos(c))
            if (ha > 0) (a + 180) % 360 else (540 - a) % 360
        }
        if (az < 0) az += 360
        return Pair(az, elev)
    }

    /**
     * Where the moon is now, roughly (to a degree or so, as SunCalc does it): its compass bearing (0 = north, clockwise), its height
     * above the horizon in degrees, and how much of it is lit (0 new to 1 full). For the night light when "Sun in its real place"
     * is on (dubrava4ever, #178: the night shadows always came from the west).
     */
    fun moon(lat: Double, lon: Double, nowMs: Long): Triple<Double, Double, Double> {
        val rad = PI / 180.0
        val d = nowMs / 86_400_000.0 - 10_957.5          // days since 1 Jan 2000, noon
        val e = rad * 23.4397
        val ml = rad * (218.316 + 13.176396 * d)
        val mm = rad * (134.963 + 13.064993 * d)
        val mf = rad * (93.272 + 13.229350 * d)
        val l = ml + rad * 6.289 * sin(mm)
        val b = rad * 5.128 * sin(mf)
        val ra = atan2(sin(l) * cos(e) - tan(b) * sin(e), cos(l))
        val dec = asin(sin(b) * cos(e) + cos(b) * sin(e) * sin(l))
        val lw = rad * -lon
        val phi = rad * lat
        val h = rad * (280.16 + 360.9856235 * d) - lw - ra
        val alt = asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(h))
        val azSouth = atan2(sin(h), cos(h) * sin(phi) - tan(dec) * cos(phi))
        val az = ((azSouth / rad + 180.0) % 360.0 + 360.0) % 360.0
        val age = (((d - 5.26) % 29.530588853) + 29.530588853) % 29.530588853
        val lit = (1 - cos(2 * PI * age / 29.530588853)) / 2
        return Triple(az, alt / rad, lit)
    }
}
