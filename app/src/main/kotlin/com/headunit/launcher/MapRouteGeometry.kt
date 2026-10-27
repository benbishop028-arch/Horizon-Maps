package com.headunit.launcher

import kotlin.math.cos
import kotlin.math.hypot

/** Pure geometry used to splice lane guidance and turn-arrow gaps into the rendered route line. */
internal object MapRouteGeometry {

    /** Move a route-related point onto the nearby lane-shift polyline, using the existing 10 m tolerance. */
    fun shiftPoint(point: LL, shift: List<LL>): LL {
        if (shift.size < 3) return point
        val longitudeScale = cos(Math.toRadians(point.lat)) * 111320.0
        val latitudeScale = 110574.0
        var bestDistance = 10.0
        var bestPoint: LL? = null
        for (i in 0 until shift.size - 1) {
            val a = shift[i]
            val b = shift[i + 1]
            val ax = (a.lon - point.lon) * longitudeScale
            val ay = (a.lat - point.lat) * latitudeScale
            val bx = (b.lon - point.lon) * longitudeScale
            val by = (b.lat - point.lat) * latitudeScale
            if ((ax > bestDistance && bx > bestDistance) || (ax < -bestDistance && bx < -bestDistance) ||
                (ay > bestDistance && by > bestDistance) || (ay < -bestDistance && by < -bestDistance)) continue
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            if (lengthSquared < 0.01) continue
            val fraction = -(ax * dx + ay * dy) / lengthSquared
            if (fraction < 0.0 || fraction > 1.0) continue
            val distance = hypot(ax + dx * fraction, ay + dy * fraction)
            if (distance < bestDistance) {
                bestDistance = distance
                bestPoint = LL(a.lat + (b.lat - a.lat) * fraction, a.lon + (b.lon - a.lon) * fraction)
            }
        }
        return bestPoint ?: point
    }

    /** Splice lane-shift points into a route and preserve the original route-point index for traffic colouring. */
    fun spliceShift(points: List<LL>, shift: List<LL>): Pair<List<LL>, IntArray?> {
        if (shift.size < 3 || points.size < 2) return points to null
        var k = 0
        val p0 = points[0]
        val p1 = points[1]
        val longitudeScale = cos(Math.toRadians(p0.lat)) * 111320.0
        val latitudeScale = 110574.0
        while (k < shift.size - 2) {
            val q = shift[k]
            val dot = (q.lon - p0.lon) * longitudeScale * (p1.lon - p0.lon) * longitudeScale +
                (q.lat - p0.lat) * latitudeScale * (p1.lat - p0.lat) * latitudeScale
            if (dot >= 0.0) break
            k++
        }
        val part = shift.subList(k, shift.size)
        val a = projectOn(points, part.first()) ?: return points to null
        val b = projectOn(points, part.last()) ?: return points to null
        if (b.first < a.first) return points to null
        val out = ArrayList<LL>(points.size + part.size)
        val originalIndices = ArrayList<Int>(points.size + part.size)
        for (i in 0..a.first) { out += points[i]; originalIndices += i }
        for (q in part) { out += q; originalIndices += a.first }
        for (i in b.first + 1 until points.size) { out += points[i]; originalIndices += i }
        return out to originalIndices.toIntArray()
    }

    /** Nearest route segment and projected point, provided it is within the existing 40 m tolerance. */
    fun projectOn(points: List<LL>, query: LL): Pair<Int, LL>? {
        if (points.size < 2) return null
        val longitudeScale = cos(Math.toRadians(query.lat)) * 111320.0
        val latitudeScale = 110574.0
        var bestDistance = 40.0
        var bestIndex = -1
        var bestPoint: LL? = null
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val ax = (a.lon - query.lon) * longitudeScale
            val ay = (a.lat - query.lat) * latitudeScale
            val bx = (b.lon - query.lon) * longitudeScale
            val by = (b.lat - query.lat) * latitudeScale
            if ((ax > bestDistance && bx > bestDistance) || (ax < -bestDistance && bx < -bestDistance) ||
                (ay > bestDistance && by > bestDistance) || (ay < -bestDistance && by < -bestDistance)) continue
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            val fraction = if (lengthSquared > 0.0) (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0) else 0.0
            val distance = hypot(ax + dx * fraction, ay + dy * fraction)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = i
                bestPoint = LL(a.lat + (b.lat - a.lat) * fraction, a.lon + (b.lon - a.lon) * fraction)
            }
        }
        return if (bestIndex >= 0 && bestPoint != null) bestIndex to bestPoint else null
    }
}
