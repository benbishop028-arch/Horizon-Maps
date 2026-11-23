package com.headunit.launcher.car

import android.content.Context
import com.headunit.launcher.ui.CarSearch

/**
 * The last few times the search card on the car screen was closed, and by what part of the app, so a card that closes by itself
 * (after voice search, TheDMAl #326) can be traced from a problem report. Only the time, what had been said or typed (whether
 * anything had), and the app's own code lines are kept, on this device.
 */
object CarCardLog {
    private const val KEY = "car_card_closes"

    fun note(ctx: Context) {
        try {
            if (!CarSearch.open) return
            val frames = Throwable().stackTrace
                .filter { it.className.startsWith("com.headunit.launcher") && !it.className.startsWith("com.headunit.launcher.car.CarCardLog") }
                .take(4)
                .joinToString(" < ") { it.className.substringAfterLast('.').substringBefore('$') + ":" + it.lineNumber }
            val t = java.text.SimpleDateFormat("dd MMM HH:mm:ss", java.util.Locale.UK).format(java.util.Date())
            val what = when { CarSearch.listening -> "listening"; CarSearch.query.isNotBlank() -> "with a search"; else -> "empty" }
            val sp = ctx.getSharedPreferences("diag", Context.MODE_PRIVATE)
            val old = sp.getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }
            sp.edit().putString(KEY, (old + "$t · $what · $frames").takeLast(5).joinToString("\n")).apply()
        } catch (e: Exception) { }
    }

    fun text(ctx: Context): String =
        ctx.getSharedPreferences("diag", Context.MODE_PRIVATE).getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }.joinToString("\n").let { if (it.isBlank()) "" else it + "\n" }
}
