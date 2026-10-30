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
                val suspicious = unitRestarted && beat > 0L && (wasStuck || lastSpeed > 4f || lenS < 90L)
                val report = buildString {
                    append("Last session didn't close normally: open ${lenS / 60} min ${lenS % 60} s, ")
                    append(if (unitRestarted) "and the head unit itself restarted afterwards" else "the app was closed by Android (the unit kept running)")
                    append(String.format(java.util.Locale.UK, ". Speed then: %.0f mph. ", lastSpeed / 0.44704f))
                    if (wasStuck) append("The screen thread had stopped responding. ")
                    append("Last note (${java.util.Date(beat)}): ${p.getString(NOTE, "") ?: ""}")
                    if (suspicious) append(" [counted as a possible crash]")
                }
                var strikes = if (suspicious) p.getInt(STRIKES, 0) + 1 else p.getInt(STRIKES, 0)
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
        1 -> "Your head unit restarted while Horizon Nav was open, twice. To be safe, the map now draws in a gentler way, at 30 frames a second, without the heaviest 3D extras. If it happens again, please send a feedback report from Settings > About & backup"
        2 -> "Your head unit restarted again, so the map is now on Lite with a lower sharpness. Please send a feedback report from Settings > About & backup so this unit can be fixed"
        else -> "Your head unit keeps restarting, so the map is now on its lightest setting. Please send a feedback report from Settings > About & backup so this unit can be fixed"
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
