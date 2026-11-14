package com.headunit.launcher

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Sharp bends along a route, worked out from the route's own shape: for the bend warning ("Sharp bend · take it at 25 mph") and the
 * yellow stretch with black chevrons drawn on the route through each one.
 *
 * The road's heading is read every [STEP] metres, each from a 20 m stretch, and the turn over a 60 m window gives the bend's radius.
 * A run of samples tighter than [MAX_R] turning the same way, adding up to at least [MIN_TURN] degrees, is one bend; its tightest
 * radius gives a comfortable speed through it (the speed at which you'd feel [LAT_ACC] sideways). Junction turns (any step that isn't
 * just "carry on") aren't bends: there the guidance already says to turn.
 */
object Bends {
    /** One bend: from [start] to [end] metres along the route, tightest at [apex]; [mps] is a comfortable speed through it; [right] which way it goes. */
    class Bend(val start: Double, val end: Double, val apex: Double, val mps: Double, val pts: List<LL>, val right: Boolean)

    private const val STEP = 8.0
    private const val WIN = 60.0
    private const val MAX_R = 150.0
    private const val MIN_TURN = 40.0
    private const val LAT_ACC = 3.0

    fun find(r: Route): List<Bend> = try { findInner(r) } catch (e: Exception) { emptyList() }

    private fun findInner(r: Route): List<Bend> {
        if (r.pts.size < 3 || r.len < 200.0) return emptyList()
        val n = (r.len / STEP).toInt()
        if (n < 10 || n > 250_000) return emptyList()
        // the road's heading at each sample, from 10 m behind to 10 m ahead
        val head = DoubleArray(n + 1) { i ->
            val d = i * STEP
            Geo.bearing(at(r, max(0.0, d - 10.0)), at(r, min(r.len, d + 10.0)))
        }
        val half = (WIN / 2 / STEP).toInt()
        // signed turn over the window around each sample (degrees, + to the right) and the radius that gives
        val turn = DoubleArray(n + 1)
        val rad = DoubleArray(n + 1) { 1e9 }
        for (i in half..(n - half)) {
            val t = diff(head[i - half], head[i + half])
            turn[i] = t
            if (abs(t) > 1.0) rad[i] = (2 * half * STEP) / Math.toRadians(abs(t))
        }
        // turns at junctions: a bend there is the turn itself
        val junctions = r.steps.filter { s ->
            val m = s.maneuver
            !(m == "STRAIGHT" || m == "FOLLOW" || m.startsWith("DEPART"))
        }.map { it.off }
        val out = ArrayList<Bend>()
        var i = 0
        while (i <= n) {
            if (rad[i] >= MAX_R) { i++; continue }
            val sign = if (turn[i] >= 0) 1 else -1
            var j = i
            var minR = rad[i]; var apex = i
            while (j + 1 <= n && rad[j + 1] < MAX_R && (if (turn[j + 1] >= 0) 1 else -1) == sign) {
                j++
                if (rad[j] < minR) { minR = rad[j]; apex = j }
            }
            val start = max(0.0, (i - half) * STEP + STEP * half / 2)
            val end = min(r.len, (j + half) * STEP - STEP * half / 2)
            val total = abs(diff(head[max(0, i - half)], head[min(n, j + half)]))
            val nearTurn = junctions.any { it > start - 35.0 && it < end + 35.0 }
            if (total >= MIN_TURN && !nearTurn && end - start >= 15.0) {
                val mps = sqrt(LAT_ACC * minR)
                val pts = ArrayList<LL>()
                var d = start
                while (d < end) { pts += at(r, d); d += 5.0 }
                pts += at(r, end)
                out += Bend(start, end, apex * STEP, mps, pts, sign > 0)
            }
            i = j + 1
        }
        return out
    }

    /** The turn from heading [a] to [b], -180..180 (+ to the right). */
    private fun diff(a: Double, b: Double): Double = ((b - a + 540.0) % 360.0) - 180.0

    private fun at(r: Route, d: Double): LL {
        val c = r.cum
        var lo = 0; var hi = c.size - 1
        while (lo < hi - 1) { val m = (lo + hi) ushr 1; if (c[m] <= d) lo = m else hi = m }
        val seg = (c[hi] - c[lo]).takeIf { it > 0 } ?: 1.0
        val t = ((d - c[lo]) / seg).coerceIn(0.0, 1.0)
        val a = r.pts[lo]; val b = r.pts[hi]
        return LL(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }
}
