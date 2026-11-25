package com.headunit.launcher

/** A stop's distance along a candidate route and its estimated travel time from the route start. */
data class RouteStopPreview(val index: Int, val distanceMeters: Double, val travelSeconds: Double)

/** Pure calculation shared by route-preview UI and tests. */
object RouteStopPreviewPolicy {
    /** Ignore stops that are not close enough to the route to have a trustworthy along-route estimate. */
    fun estimate(
        offsets: DoubleArray,
        nearDistances: DoubleArray,
        routeTimeSeconds: Double,
        totalRemainingSeconds: Double,
        remainingAt: (Double) -> Double,
    ): List<RouteStopPreview> {
        if (!routeTimeSeconds.isFinite() || routeTimeSeconds <= 0.0 ||
            !totalRemainingSeconds.isFinite() || totalRemainingSeconds <= 0.0) return emptyList()
        return offsets.indices.mapNotNull { index ->
            val offset = offsets[index]
            val near = nearDistances.getOrElse(index) { Double.POSITIVE_INFINITY }
            if (!offset.isFinite() || offset < 0.0 || !near.isFinite() || near >= 400.0) return@mapNotNull null
            val remaining = remainingAt(offset)
            if (!remaining.isFinite()) return@mapNotNull null
            val elapsed = routeTimeSeconds * (1.0 - remaining / totalRemainingSeconds).coerceIn(0.0, 1.0)
            RouteStopPreview(index, offset, elapsed)
        }
    }
}
