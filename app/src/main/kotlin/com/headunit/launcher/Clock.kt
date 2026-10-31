package com.headunit.launcher

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * How clock times are shown (arrival times, leave-by times, the trip log...): 24-hour ("16:52") or 12-hour ("4:52 pm").
 * Follows the phone's or head unit's own 12 / 24-hour setting unless Settings picks one ([PreferenceKeys.CLOCK]).
 */
object Clock {
    @Volatile private var prefs: Prefs? = null
    @Volatile private var system24 = true

    /** Called when the app or the car screen starts: reads the device's own 12 / 24-hour setting. */
    fun init(ctx: Context, p: Prefs) {
        prefs = p
        system24 = try { android.text.format.DateFormat.is24HourFormat(ctx) } catch (e: Exception) { true }
    }

    fun is24(): Boolean = when (try { prefs?.s(PreferenceKeys.CLOCK, "auto") } catch (e: Exception) { null }) {
        "12" -> false
        "24" -> true
        else -> system24
    }

    /** The hours-and-minutes part of a date pattern. */
    fun hmPattern(): String = if (is24()) "HH:mm" else "h:mm a"

    /** A formatter with [before] (e.g. "EEE ") in front of the time. */
    fun format(before: String = ""): SimpleDateFormat = SimpleDateFormat(before + hmPattern(), Locale.getDefault())

    fun hm(ms: Long): String = try { format().format(Date(ms)) } catch (e: Exception) { "" }
}
