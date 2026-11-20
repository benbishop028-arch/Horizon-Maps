package com.headunit.launcher

import android.content.Context

/**
 * The last few times the app lowered the music and gave it back, for the problem report (GitHub ajg0001, #224: the music stayed
 * quiet after a speed limit alert). Each line says when, what lowered it and when it came back, so a report sent straight after
 * shows whether the app gave the sound back or something else kept it down. Kept small: the last 8 lines.
 */
object SoundLog {
    private const val KEY = "sound_log"

    fun note(ctx: Context, what: String) {
        try {
            val t = java.text.SimpleDateFormat("dd MMM HH:mm:ss", java.util.Locale.UK).format(java.util.Date())
            val sp = ctx.getSharedPreferences("diag", Context.MODE_PRIVATE)
            val old = sp.getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }
            sp.edit().putString(KEY, (old + "$t · $what").takeLast(8).joinToString("\n")).apply()
        } catch (e: Throwable) { }
    }

    fun text(ctx: Context): String =
        ctx.getSharedPreferences("diag", Context.MODE_PRIVATE).getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }.joinToString("\n") + "\n"
}
