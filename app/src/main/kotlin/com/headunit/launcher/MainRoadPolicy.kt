package com.headunit.launcher

import kotlin.math.max

/**
 * Prefer main roads for online routes (d3stiny999 #402): of the routes Mapbox offers, the one that keeps off small roads
 * (residential streets, country lanes, service roads) goes first, when it's no more than about 5 minutes (or 10% on a long
 * trip) slower than the quickest and clearly uses fewer of them. Only Mapbox says what kind of road each junction is on, so
 * TomTom's routes are left as they come; offline routes have their own preference in the router.
 */
internal object MainRoadPolicy {
    /** Mapbox's road classes counted as small roads. */
    fun isMinor(cls: String): Boolean = cls == "street" || cls == "street_limited" || cls == "service" || cls == "track"

    /** Whether a route with [m] metres of small roads uses clearly more of them than one with [base] (either -1 = not known). */
    fun clearlyMoreMinor(m: Double, base: Double): Boolean = m >= 0 && base >= 0 && m - base >= max(300.0, base / 3.0)

    /**
     * Which of the routes to put first: the index into [times] / [minorM] (minorM -1 = not known), or -1 to leave the order
     * as it is. Index 0 is the route that would otherwise go first.
     */
    fun pick(times: List<Double>, minorM: List<Double>): Int {
        if (times.size < 2 || times.size != minorM.size) return -1
        val topMinor = minorM[0]
        if (topMinor < 0) return -1
        val quickest = times.minOrNull() ?: return -1
        val slack = max(300.0, quickest * 0.10)
        var best = -1
        for (i in 1 until times.size) {
            val m = minorM[i]
            if (m < 0 || times[i] > quickest + slack) continue
            // clearly fewer small roads than the first route: at least 300 m fewer, and the first has a third more than it
            if (!clearlyMoreMinor(topMinor, m)) continue
            if (best < 0 || m < minorM[best] || (m == minorM[best] && times[i] < times[best])) best = i
        }
        return best
    }
}
