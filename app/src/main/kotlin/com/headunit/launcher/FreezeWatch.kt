package com.headunit.launcher

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Catches freezes ("Horizon Nav isn't responding: close or wait"), which never reach the crash handler. A watchdog thread checks
 * the screen thread answers within 5 seconds; when it doesn't, what it was stuck doing is written to lastfreeze.txt. On Android 11
 * and newer, Android's own record of the last freeze is picked up too, the next time the app starts. Feedback reports include it.
 */
object FreezeWatch {
    @Volatile private var started = false
    /** When the screen thread stopped answering (0 when it's fine): SessionWatch notes it every 10 seconds. */
    @Volatile var stuckSince = 0L

    fun file(ctx: Context) = File(ctx.filesDir, "lastfreeze.txt")

    @Synchronized
    fun start(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread({
            var reported = false
            while (true) {
                val answered = AtomicBoolean(false)
                main.post { answered.set(true) }
                try { Thread.sleep(5000) } catch (e: InterruptedException) { return@Thread }
                if (!answered.get()) {
                    if (stuckSince == 0L) stuckSince = System.currentTimeMillis() - 5000L
                    if (!reported) {
                        reported = true
                        try {
                            val st = Looper.getMainLooper().thread.stackTrace.take(40).joinToString("\n") { "  at $it" }
                            file(app).writeText("Freeze: the screen thread was stuck for 5+ s (${java.util.Date()})\n$st")
                        } catch (e: Throwable) { }
                    }
                } else { reported = false; stuckSince = 0L }
            }
        }, "freeze-watch").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    /** Android 11+: if the app was last closed for not responding, keeps the screen thread's part of Android's record of it. */
    fun collectLastExit(ctx: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val last = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 1).firstOrNull() ?: return
            if (last.reason != android.app.ApplicationExitInfo.REASON_ANR) return
            val prefs = ctx.getSharedPreferences(CrashGuardPrefs.FILE, Context.MODE_PRIVATE)
            if (prefs.getLong(CrashGuardPrefs.LAST_ANR_SEEN, 0L) >= last.timestamp) return
            prefs.edit().putLong(CrashGuardPrefs.LAST_ANR_SEEN, last.timestamp).apply()
            val text = last.traceInputStream?.bufferedReader()?.use { it.readText() } ?: ""
            // just the screen thread ("main") from the full dump of every thread
            val i = text.indexOf("\"main\"")
            val mainPart = if (i >= 0) text.substring(i).substringBefore("\n\n") else text
            file(ctx).writeText("Not responding (Android's record, ${java.util.Date(last.timestamp)}): ${last.description ?: ""}\n" + mainPart.take(3500))
        } catch (e: Throwable) { }
    }
}
