package com.headunit.launcher

import android.content.Context

/**
 * The last few times a trip under way was ended, and by what part of the app, so a route that cancels itself can be traced from a
 * problem report. Only the time, which screen, whether it was to Home, Work or elsewhere, and the app's own code lines are kept, on this device.
 */
object RouteEnds {
    private const val KEY = "route_ends"

    fun note(ctx: Context, screen: String, dest: String, why: String = "") {
        val frames = Throwable().stackTrace
            .filter { it.className.startsWith("com.headunit.launcher") && !it.className.startsWith("com.headunit.launcher.RouteEnds") }
            .drop(1).take(4)
            .joinToString(" < ") { it.className.substringAfterLast('.').substringBefore('$') + ":" + it.lineNumber }
        val t = java.text.SimpleDateFormat("dd MMM HH:mm:ss", java.util.Locale.UK).format(java.util.Date())
        val sp = ctx.getSharedPreferences("diag", Context.MODE_PRIVATE)
        val old = sp.getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }
        val line = "$t · $screen · $dest · $frames" + (if (why.isNotEmpty()) " · $why" else "")
        sp.edit().putString(KEY, (old + line).takeLast(5).joinToString("\n")).apply()
    }

    fun text(ctx: Context): String =
        ctx.getSharedPreferences("diag", Context.MODE_PRIVATE).getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }.joinToString("\n") + "\n"
}
