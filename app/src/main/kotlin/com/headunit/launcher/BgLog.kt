package com.headunit.launcher

import android.content.Context

/**
 * What the app did while it wasn't on screen today, for the problem report (battery: Samsung flagging "high background battery
 * usage"): how long GPS was running in the background, with and without a route, and how many online requests were made and by
 * which part of the app. Counts only, kept for the current day.
 */
object BgLog {
    @Volatile var appCtx: Context? = null
    /** False while the app (phone screen) is in the background. Set by the phone's engine. */
    @Volatile var foreground = true

    private const val FILE = "diag"
    private var day = ""
    private var gpsMs = 0L; private var routeMs = 0L; private var requests = 0
    private val bySource = HashMap<String, Int>()
    private var lastFixAt = 0L
    private var savedAt = 0L

    private fun today() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.UK).format(java.util.Date())

    @Synchronized private fun roll() {
        val t = today()
        if (t == day) return
        val ctx = appCtx
        day = t; gpsMs = 0L; routeMs = 0L; requests = 0; bySource.clear()
        if (ctx != null) try {
            val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            if (p.getString("bg_day", "") == t) {
                gpsMs = p.getLong("bg_gps", 0L); routeMs = p.getLong("bg_route", 0L); requests = p.getInt("bg_req", 0)
                p.getString("bg_src", "").orEmpty().split(';').forEach { kv -> val i = kv.lastIndexOf('='); if (i > 0) bySource[kv.substring(0, i)] = kv.substring(i + 1).toIntOrNull() ?: 0 }
            }
        } catch (e: Throwable) { }
    }

    @Synchronized private fun maybeSave(force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - savedAt < 60_000L) return
        savedAt = now
        val ctx = appCtx ?: return
        try {
            ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("bg_day", day).putLong("bg_gps", gpsMs).putLong("bg_route", routeMs)
                .putInt("bg_req", requests).putString("bg_src", bySource.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
        } catch (e: Throwable) { }
    }

    /** A GPS position arrived (phone engine), [routing] when a route is being followed. */
    @Synchronized fun fix(routing: Boolean) {
        val now = android.os.SystemClock.elapsedRealtime()
        val gap = now - lastFixAt
        lastFixAt = now
        if (foreground || gap <= 0L || gap > 15_000L) return
        roll()
        gpsMs += gap; if (routing) routeMs += gap
        maybeSave()
    }

    /** An online request is about to go out (from Net.check). */
    fun request() {
        if (foreground) return
        try {
            // which part of the app asked: the first caller outside the network helpers
            val src = Throwable().stackTrace.asSequence().map { it.className.substringAfterLast('.').substringBefore('$') }
                .firstOrNull { it != "Net" && it != "BgLog" && it != "Geo" && it != "MapboxRest" && !it.startsWith("Http") && it.isNotBlank() } ?: "?"
            synchronized(this) { roll(); requests++; bySource[src] = (bySource[src] ?: 0) + 1; maybeSave() }
        } catch (e: Throwable) { }
    }

    /** Today's line for the problem report ("" when nothing happened in the background). */
    @Synchronized fun text(): String {
        roll(); maybeSave(true)
        if (gpsMs < 60_000L && requests == 0) return ""
        fun hm(ms: Long) = "${ms / 3_600_000L} h ${(ms / 60_000L) % 60} min"
        val top = bySource.entries.sortedByDescending { it.value }.take(6).joinToString(", ") { "${it.key} ${it.value}" }
        return "GPS ${hm(gpsMs)} (with a route ${hm(routeMs)}) · online requests $requests" + (if (top.isNotBlank()) " ($top)" else "") + "\n"
    }
}
