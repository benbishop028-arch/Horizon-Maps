package com.headunit.launcher

import android.content.Context
import android.os.Build
import android.os.SystemClock

/**
 * Catches the whole head unit restarting while Horizon Nav is on screen, the one kind of failure nothing else can see. A graphics
 * driver or the screen compositor locks up and the unit reboots, so there's no crash, no "isn't responding" and no 25-second
 * start-up failure (it happens later). This was reported on a built-in Android 9 unit (Snapdragon 8155).
 *
 * While the app is on screen it writes a small note every 10 seconds: time, memory, speed and frame rate. On the next start:
 *  - the note was never closed off (the app didn't stop normally), and
 *  - the unit itself restarted since (its uptime is now lower than the note's).
 * That's either the unit crashing, or the car simply being switched off. Switching off almost always comes after a while and
 * when you've stopped, so it only counts as "suspicious" when the screen had already locked up, when it happened while you
 * were moving, or within 90 seconds of opening the app. Two suspicious ones in a row step the drawing down one level (see [applyLevel]), and the app says so.
 * A normal session of 10+ minutes clears the count. The notes go in the feedback report (Settings > About).
 */
object SessionWatch {
    private const val FILE = "sessionwatch"
    private const val RUNNING = "running"
    private const val START = "start"
    private const val BEAT = "beat"
    private const val BEAT_UP = "beatup"
    private const val SPEED = "speed"
    private const val NOTE = "note"
    private const val STUCK = "stuck"
    private const val STRIKES = "strikes"
    private const val LEVEL = "level"
    private const val REPORT = "report"

    /** Your speed now (m/s), for the notes: set by the screen once the engine exists. */
    @Volatile var speed: () -> Double = { 0.0 }
    /** The frame-rate line, for the notes. */
    @Volatile var perf: () -> String = { "" }
    @Volatile private var beating = false
    @Volatile private var thread: Thread? = null

    /**
     * Call at start-up, before the map is made. Looks at how the last session ended and, after two suspicious endings, turns
     * the drawing down one step. Returns the new level (1 to 3) when it stepped down this start, so the app can say so; else 0.
     */
    fun onStartup(ctx: Context, prefs: Prefs): Int {
        // (the newest native crash's record is read now, in the background, so the problem report never waits on it, #475)
        if (android.os.Build.VERSION.SDK_INT >= 30) try { val app = ctx.applicationContext; Thread({ try { exitHistory(app) } catch (e: Throwable) { } }, "crash-record").apply { priority = Thread.MIN_PRIORITY; isDaemon = true }.start() } catch (e: Throwable) { }
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        var stepped = 0
        try {
            if (p.getBoolean(RUNNING, false)) {
                val start = p.getLong(START, 0L); val beat = p.getLong(BEAT, 0L); val beatUp = p.getLong(BEAT_UP, 0L)
                val unitRestarted = beatUp > SystemClock.elapsedRealtime()
                val lenS = ((beat - start) / 1000L).coerceAtLeast(0L)
                val lastSpeed = p.getFloat(SPEED, 0f)
                val wasStuck = p.getBoolean(STUCK, false)
                // the screen had locked up before the end, it ended while moving, or it ended within 90 s of opening (a short
                // drive switched off normally takes longer than that, and ends parked)
                // A screen that locked up counts whoever ended it: Android closing a stuck app (the unit kept running) used not to
                // count at all, so a unit that kept freezing never had its drawing turned down
                // what Android itself recorded about how the app ended (Android 11 on): swiped away in the recent apps, low memory, a
                // crash in the map's own code... On phones the app can be swiped away without being told, which used to read as
                // "closed by Android" (GitHub #267)
                val exit = lastExit(ctx, start)
                val byUser = exit != null && exit.first in USER_ENDED
                val suspicious = beat > 0L && !byUser && (wasStuck || (unitRestarted && (lastSpeed > 4f || lenS < 90L)))
                val report = buildString {
                    append("Last session didn't close normally: open ${lenS / 60} min ${lenS % 60} s, ")
                    append(when {
                        unitRestarted -> "and the head unit itself restarted afterwards"
                        exit != null -> "Android says: " + exit.second
                        else -> "the app was closed by Android (the unit kept running)"
                    })
                    append(String.format(java.util.Locale.UK, ". Speed then: %.0f mph. ", lastSpeed / 0.44704f))
                    if (wasStuck) append("The screen thread had stopped responding. ")
                    append("Last note (${java.util.Date(beat)}): ${p.getString(NOTE, "") ?: ""}")
                    if (suspicious) append(" [counted as a possible crash]")
                }
                // (a lock-up of the screen is serious enough to step down straight away; anything else needs two)
                var strikes = if (suspicious) p.getInt(STRIKES, 0) + (if (wasStuck) 2 else 1) else p.getInt(STRIKES, 0)
                var level = p.getInt(LEVEL, 0)
                if (strikes >= 2 && level < 3) {
                    level += 1; strikes = 0; stepped = level
                    applyLevel(prefs, level)
                }
                p.edit().putInt(STRIKES, strikes).putInt(LEVEL, level).putString(REPORT, report).commit()
            }
        } catch (e: Throwable) { }
        return stepped
    }

    /**
     * The steps down, each on top of the one before:
     *  1: the map is drawn into the app's own window instead of straight onto the screen ("Fast map drawing" off: the direct
     *     way is quicker, but on some built-in units it's what locks the screen up), 30 frames a second, no heavy 3D extras.
     *  2: the Lite map at "Light" sharpness.
     *  3: the Lightest map at the lowest sharpness.
     */
    private fun applyLevel(prefs: Prefs, level: Int) {
        prefs.put(PreferenceKeys.MAPFAST, false)
        prefs.put(PreferenceKeys.MAP_FPS, "30")
        for (k in listOf(PreferenceKeys.LAMPS, PreferenceKeys.FURNITURE3D, PreferenceKeys.TERRAIN, PreferenceKeys.RAIN_EFFECTS,
            PreferenceKeys.FACADES, PreferenceKeys.LANDMARKS3D)) prefs.put(k, false)
        if (prefs.s(PreferenceKeys.LOCATOR, "arrow") == "model") prefs.put(PreferenceKeys.LOCATOR, "arrow")
        if (level >= 2) {
            if (prefs.s(PreferenceKeys.MAP_DETAIL, "full") == "full") prefs.put(PreferenceKeys.MAP_DETAIL, "lite")
            prefs.put(PreferenceKeys.MAP_RESOLUTION, "65")
        }
        if (level >= 3) {
            prefs.put(PreferenceKeys.MAP_DETAIL, "lightest")
            prefs.put(PreferenceKeys.MAP_RESOLUTION, "50")
        }
    }

    /** What to say once it has stepped down to [level]. */
    fun message(level: Int): String = when (level) {
        1 -> "Horizon Nav froze or closed unexpectedly. To be safe, the map now draws in a gentler way, at 30 frames a second, without the heaviest 3D extras. If it happens again, please send a feedback report from Settings > About & backup"
        2 -> "Horizon Nav froze or closed again, so the map is now on Lite with a lower sharpness. Please send a feedback report from Settings > About & backup so this unit can be fixed"
        else -> "Horizon Nav keeps freezing or closing, so the map is now on its lightest setting. Please send a feedback report from Settings > About & backup so this unit can be fixed"
    }

    /** The app is on screen: start the 10-second notes. */
    fun resumed(ctx: Context) {
        val app = ctx.applicationContext
        val p = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        p.edit().putBoolean(RUNNING, true).putBoolean(STUCK, false).putLong(START, System.currentTimeMillis()).putLong(BEAT, System.currentTimeMillis())
            .putLong(BEAT_UP, SystemClock.elapsedRealtime()).commit()
        beating = true
        if (thread?.isAlive == true) return
        thread = Thread({
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val mi = android.app.ActivityManager.MemoryInfo()
            while (beating) {
                try {
                    am?.getMemoryInfo(mi)
                    val rt = Runtime.getRuntime()
                    val stuck = FreezeWatch.stuckSince
                    val note = (if (stuck > 0L) "SCREEN THREAD STUCK for ${(System.currentTimeMillis() - stuck) / 1000} s; " else "") + "app memory ${(rt.totalMemory() - rt.freeMemory()) / 1_048_576} MB + native ${android.os.Debug.getNativeHeapAllocatedSize() / 1_048_576} MB; " +
                        "unit free ${mi.availMem / 1_048_576} of ${mi.totalMem / 1_048_576} MB${if (mi.lowMemory) " (LOW)" else ""}; " + perf()
                    // (written straight away: a unit that loses power or reboots never runs anything that was left for later)
                    p.edit().putLong(BEAT, System.currentTimeMillis()).putLong(BEAT_UP, SystemClock.elapsedRealtime())
                        .putFloat(SPEED, speed().toFloat()).putBoolean(STUCK, stuck > 0L).putString(NOTE, note).commit()
                } catch (e: Throwable) { }
                try { Thread.sleep(10_000L) } catch (e: InterruptedException) { break }
            }
        }, "session-watch").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
        thread?.start()
    }

    /** Exit reasons that mean you closed the app yourself (swiped away, force stopped) or it was updated: not a fault. */
    private val USER_ENDED = setOf(10, 11, 16)

    /** Android's reason code for [code] in plain words. */
    private fun reasonName(code: Int): String = when (code) {
        1 -> "the app closed itself"; 2 -> "ended by a signal"; 3 -> "closed for low memory"; 4 -> "crashed"
        5 -> "crashed in native code (the map or another library)"; 6 -> "not responding (ANR)"; 7 -> "failed to start"
        8 -> "a permission was changed"; 9 -> "used too much battery or CPU"; 10 -> "closed by you (swiped away or Close)"
        11 -> "force stopped by you"; 12 -> "something it depends on closed"; 13 -> "other"; 14 -> "frozen by Android"
        15 -> "app state changed"; 16 -> "the app was updated"; else -> "unknown"
    }

    /**
     * How Android says the app last ended, if that was after [since] (ms): its reason code and a line for the report, with
     * Android's own description and whether the app was on screen. Null before Android 11, or when there's nothing recorded.
     */
    fun lastExit(ctx: Context, since: Long): Pair<Int, String>? {
        if (Build.VERSION.SDK_INT < 30) return null
        return try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val e = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 1).firstOrNull() ?: return null
            if (e.timestamp < since - 5_000L) return null
            var code = e.reason
            val desc = e.description.orEmpty()
            // (many phones record a swipe from the recent apps as "other: remove task")
            if (code == 13 && (desc.contains("remove task", true) || desc.contains("swipe", true) || desc.contains("task removed", true))) code = 10
            // (Xiaomi records its own cleaner, which closes apps when the screen locks, as "you": say what it really was)
            if (desc.contains("LockScreenClean", true)) return code to ("closed by the phone's cleaner when the screen locked (" + desc.take(80) + ")")
            val seen = if (e.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) "on screen" else "in the background"
            code to (reasonName(code) + " (" + seen + (if (desc.isNotBlank()) "; " + desc.take(160) else "") + ")")
        } catch (e: Throwable) { null }
    }

    /** The last few times the app ended and why, by Android's own record, for the problem report ("" before Android 11). */
    fun exitHistory(ctx: Context): String {
        if (Build.VERSION.SDK_INT < 30) return ""
        return try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val fmt = java.text.SimpleDateFormat("dd MMM HH:mm:ss", java.util.Locale.UK)
            var nativeShown = false
            am.getHistoricalProcessExitReasons(ctx.packageName, 0, 4).joinToString("") { e ->
                val desc = e.description.orEmpty()
                val code = if (e.reason == 13 && desc.contains("remove task", true)) 10 else e.reason
                fmt.format(java.util.Date(e.timestamp)) + " · " + (if (desc.contains("LockScreenClean", true)) "closed by the phone's cleaner when the screen locked" else reasonName(code)) + (if (e.reason == 5 && !nativeShown) { nativeShown = true; nativeCrash(e) } else "") + (if (desc.isNotBlank()) " · " + desc.take(120) else "") + "\n"
            }
        } catch (e: Throwable) { "" }
    }

    /**
     * For a crash in native code (the map's own engine, a graphics driver): where it was, from the crash record Android keeps (its
     * trace, on Android 11 and newer): the signal and the first library or function names in it, without folders or addresses. It's
     * the only way to see where a crash that leaves nothing else behind happened (MAIS-dev, #460). Only the newest one.
     */
    @Volatile private var nativeSeen: Pair<Long, String>? = null
    private fun nativeCrash(e: android.app.ApplicationExitInfo): String = nativeSeen?.takeIf { it.first == e.timestamp }?.second
        ?: nativeRead(e).also { nativeSeen = e.timestamp to it }
    /** (read once per crash: the record can be a few hundred KB, and the report is built on a tap, on the main thread of a slow unit) */
    private fun nativeRead(e: android.app.ApplicationExitInfo): String = try {
        val bytes = e.traceInputStream?.use { it.readBytes().let { b -> if (b.size > 300_000) b.copyOf(300_000) else b } } ?: return ""
        // the readable runs of text in it, picked out byte by byte: a regular expression over the whole record froze a slow head
        // unit's screen for more than 5 seconds while the report was being made (MAIS-dev, #475)
        val strings = ArrayList<String>()
        var start = -1
        for (k in 0..bytes.size) {
            val b = if (k < bytes.size) bytes[k].toInt() and 0xFF else 0
            val printable = b in 0x20..0x7E
            if (printable && start < 0) start = k
            else if (!printable && start >= 0) { if (k - start >= 5) strings += String(bytes, start, minOf(k - start, 300), Charsets.ISO_8859_1).trim(); start = -1 }
        }
        // why it stopped (an abort's own message: "JNI DETECTED ERROR…", "Check failed…", "pthread_create failed…"), which says
        // far more than the libraries; then the signal and the first libraries and functions, without their build numbers
        val why = strings.firstOrNull { s -> listOf("Abort message", "JNI DETECTED", "Check failed", "pthread_create", "Out of memory", "java.lang.", "terminating", "FORTIFY", "assert")
            .any { s.contains(it, ignoreCase = true) } }?.take(200)
        val keep = LinkedHashSet<String>()
        for (s in strings) {
            val t = when {
                s.startsWith("SIG") || s.startsWith("SEGV_") || s.startsWith("BUS_") || s.startsWith("ILL_") -> s.take(40)
                s.contains(".so") -> s.substringAfterLast('/').substringBefore(".so") + ".so"
                s.startsWith("_Z") && s.length > 8 -> s.take(70)
                else -> null
            } ?: continue
            keep += t
            if (keep.size >= 10) break
        }
        if (keep.isEmpty() && why == null) "" else " [native: " + listOfNotNull(why?.let { "\"$it\"" }).plus(keep).joinToString(", ") + "]"
    } catch (x: Throwable) { "" }

    /** Put away or closed normally: not a crash. A good long session clears the count. */
    fun stopped(ctx: Context) {
        beating = false
        thread?.interrupt(); thread = null
        try {
            val p = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val lenMs = System.currentTimeMillis() - p.getLong(START, System.currentTimeMillis())
            val e = p.edit().putBoolean(RUNNING, false)
            if (lenMs > 10 * 60_000L) e.putInt(STRIKES, 0)
            e.apply()
        } catch (e: Throwable) { }
    }

    /** For the feedback report: how the last session ended, and the unit's details. */
    fun report(ctx: Context): String {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return buildString {
            appendLine("Chip: ${Build.HARDWARE} / ${Build.BOARD}${if (Build.VERSION.SDK_INT >= 31) " / " + Build.SOC_MODEL else ""} · safer drawing step ${p.getInt(LEVEL, 0)} · fast map drawing: ${if (Prefs(ctx).b(PreferenceKeys.MAPFAST, true)) "on" else "off"}")
            p.getString(REPORT, null)?.let { appendLine(it) }
        }
    }
}
