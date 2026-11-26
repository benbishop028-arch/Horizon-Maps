package com.headunit.launcher

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Result of projecting a GPS position onto a route polyline. */
internal data class RouteSnap(val dist: Double, val segmentIndex: Int, val progress: Double, val point: LL)

/**
 * Stateless route geometry helpers. Keeping the scan here makes the most frequently used route-matching
 * calculation testable without constructing NavEngine or a Mapbox map.
 */
internal object RouteMatcher {
    /**
     * Find the closest point on a route. Search near [previousIndex] first, then fall back to the full
     * route only when the local result is more than 60 m away. The limits intentionally match the old
     * NavEngine implementation to avoid changing snapping behaviour.
     */
    fun snap(route: Route, position: LL, previousIndex: Int, heading: Double? = null, maxProgressMeters: Double? = null): RouteSnap {
        val pts = route.pts
        require(pts.size >= 2) { "A route needs at least two points to snap a position" }
        require(route.cum.size >= pts.size) { "Route cumulative distances do not cover its points" }

        var bestDistance = 1e12
        var bestScore = 1e12
        var bestIndex = 0
        var bestFraction = 0.0
        val longitudeScale = cos(Geo.rad(position.lat)) * 111320.0
        val latitudeScale = 110574.0

        fun scan(from: Int, until: Int) {
            for (i in from until min(until, pts.size - 1)) {
                // On the very first GPS fix, don't match a nearby return leg further along a looping route.
                if (maxProgressMeters != null && route.cum[i] > maxProgressMeters) break;
                val ax = (pts[i].lon - position.lon) * longitudeScale
                val ay = (pts[i].lat - position.lat) * latitudeScale
                val bx = (pts[i + 1].lon - position.lon) * longitudeScale
                val by = (pts[i + 1].lat - position.lat) * latitudeScale
                val dx = bx - ax
                val dy = by - ay
                val lengthSquared = dx * dx + dy * dy
                val fraction = if (lengthSquared > 0.0) {
                    (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
                } else 0.0
                val distance = hypot(ax + dx * fraction, ay + dy * fraction)
                // A reliable heading helps distinguish opposite carriageways and nearby return legs.
                val headingPenalty = if (heading != null && lengthSquared > 4.0) {
                    val bearing = (Math.toDegrees(kotlin.math.atan2(dx, dy)) + 360.0) % 360.0
                    val delta = kotlin.math.abs(((bearing - heading + 540.0) % 360.0) - 180.0)
                    when { delta > 135.0 -> 100.0; delta > 90.0 -> 35.0; else -> 0.0 }
                } else 0.0
                val score = distance + headingPenalty
                if (score < bestScore) {
                    bestScore = score
                    bestDistance = distance
                    bestIndex = i
                    bestFraction = fraction
                }
            }
        }

        scan(max(0, previousIndex - 20), previousIndex + 500)
        if (bestDistance > 60.0 && maxProgressMeters == null) scan(0, pts.size)

        val a = pts[bestIndex]
        val b = pts[bestIndex + 1]
        val segmentLength = route.cum[bestIndex + 1] - route.cum[bestIndex]
        return RouteSnap(
            dist = bestDistance,
            segmentIndex = bestIndex,
            progress = route.cum[bestIndex] + bestFraction * segmentLength,
            point = LL(
                a.lat + (b.lat - a.lat) * bestFraction,
                a.lon + (b.lon - a.lon) * bestFraction,
            ),
        )
    }
}
