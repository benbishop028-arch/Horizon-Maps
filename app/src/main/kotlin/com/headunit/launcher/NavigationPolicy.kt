package com.headunit.launcher

/** Pure navigation policies kept separate so their edge cases can be covered by local unit tests. */
internal object NavigationPolicy {
    private val freeDriveSpeedPoints = doubleArrayOf(0.0, 3.0, 6.0, 11.0, 17.0, 24.0, 31.0, 40.0)
    private val freeDriveZoomPoints = doubleArrayOf(18.4, 18.25, 17.95, 17.45, 16.95, 16.45, 16.0, 15.7)
    private val routeSpeedPoints = doubleArrayOf(0.0, 4.0, 8.0, 11.0, 13.4, 17.0, 22.0, 27.0, 31.0, 36.0)
    private val routeZoomPoints = doubleArrayOf(18.7, 18.5, 18.2, 17.9, 17.6, 17.1, 16.6, 16.2, 15.9, 15.6)

    /** Interpolated free-drive zoom. Cached point arrays avoid allocating two arrays on every camera tick. */
    fun speedZoom(speedMps: Double): Double {
        if (!speedMps.isFinite()) return freeDriveZoomPoints.last()
        if (speedMps <= freeDriveSpeedPoints.first()) return freeDriveZoomPoints.first()
        if (speedMps >= freeDriveSpeedPoints.last()) return freeDriveZoomPoints.last()
        for (i in 1 until freeDriveSpeedPoints.size) {
            if (speedMps <= freeDriveSpeedPoints[i]) {
                val fraction = (speedMps - freeDriveSpeedPoints[i - 1]) /
                    (freeDriveSpeedPoints[i] - freeDriveSpeedPoints[i - 1])
                return freeDriveZoomPoints[i - 1] +
                    (freeDriveZoomPoints[i] - freeDriveZoomPoints[i - 1]) * fraction
            }
        }
        return freeDriveZoomPoints.last()
    }

    /** Route zoom interpolation plus a modest road-class adjustment, with no per-frame array allocation. */
    fun routeSpeedZoom(speedMps: Double, roadClass: String?): Double {
        val base = interpolate(speedMps, routeSpeedPoints, routeZoomPoints)
        return when {
            roadClass == null -> base
            roadClass.startsWith("motorway") || roadClass.startsWith("trunk") -> base - 0.35
            roadClass == "street" || roadClass == "street_limited" || roadClass == "service" -> base + 0.3
            else -> base
        }
    }

    private fun interpolate(value: Double, xs: DoubleArray, ys: DoubleArray): Double {
        if (!value.isFinite() || value >= xs.last()) return ys.last()
        if (value <= xs.first()) return ys.first()
        for (i in 1 until xs.size) if (value <= xs[i]) {
            val fraction = (value - xs[i - 1]) / (xs[i] - xs[i - 1])
            return ys[i - 1] + (ys[i] - ys[i - 1]) * fraction
        }
        return ys.last()
    }

    /** Keep the opening-route match window until the vehicle is actually moving and close to that section. */
    fun shouldReleaseInitialRouteSnap(speedMps: Double, distanceMeters: Double): Boolean =
        speedMps.isFinite() && distanceMeters.isFinite() && speedMps > 2.5 && distanceMeters <= 60.0

    /**
     * Whether this screen may decide the trip has arrived (which ends it on both screens). Not the phone while Android Auto is
     * connected: the phone app's GPS rests while it's put away, so its
     * position is where it was last open (often home), and a route started on the
     * car screen counted as arrived at once and ended both a few seconds later (carlb051, #360, #419). The car screen decides
     * then. And never on a position older than the route: there has to be a real fix since it started. Demo drives always may.
     */
    fun mayDeclareArrival(isCar: Boolean, carConnected: Boolean, simulating: Boolean, navStartMs: Long, liveFixAtMs: Long): Boolean = when {
        simulating -> true
        !isCar && carConnected -> false
        navStartMs > 0L && liveFixAtMs < navStartMs -> false
        else -> true
    }

    /**
     * Whether a trip keeps off motorways: always, or with "Short trips" when where you're going is less than [underM] away in a
     * straight line from where the trip started ([tripM], null when not known) (kr-nn #413).
     */
    fun avoidMotorways(always: Boolean, shortTrips: Boolean, underM: Int, tripM: Double?): Boolean =
        always || (shortTrips && tripM != null && tripM < underM)

    /** Unknown accuracy is allowed for compatibility; known poor fixes should not trigger a reroute. */
    fun gpsReliableForReroute(accuracyMeters: Double): Boolean =
        !accuracyMeters.isFinite() || accuracyMeters <= 0.0 || accuracyMeters <= 45.0

    /**
     * Reject uncertain network positions while following a route. Once GPS has been gone for a minute or more (a weak GPS aerial on a
     * head unit, or a very long tunnel), a rough position up to 150 m is taken again, or the arrow would only ever be estimated.
     */
    fun shouldRejectNetworkPositionOnRoute(accuracyMeters: Double?, gpsLostMs: Long = 0L): Boolean {
        if (accuracyMeters == null || !accuracyMeters.isFinite() || accuracyMeters <= 0.0) return true
        val limit = if (gpsLostMs >= 60_000L) 150.0 else 30.0
        return accuracyMeters > limit
    }

    /** Distance from the route tolerated before a credible GPS fix counts as off-route. */
    fun offRouteLimit(accuracyMeters: Double): Double {
        val usableAccuracy = if (accuracyMeters.isFinite() && accuracyMeters > 0.0) accuracyMeters else 8.0
        return maxOf(30.0, usableAccuracy * 1.5 + 12.0)
    }

    /** A large departure or impossible progress jump contributes two strikes to the reroute counter. */
    fun offRouteStrikeCount(progressJump: Boolean, distanceMeters: Double, limitMeters: Double): Int = when {
        // clearly on another road (three times the limit, with a GPS fix good enough to count at all): reroute on this fix
        // rather than wait for the next ones, as Google Maps does (GitHub, karthikeyansr-lgtm)
        !progressJump && distanceMeters > limitMeters * 3.0 -> 3
        progressJump || distanceMeters > limitMeters * 2.0 -> 2
        else -> 1
    }

    /** A close-to-route fix can still indicate a wrong turn when the vehicle is moving away from the route heading. */
    fun shouldCountHeadingAwayStrike(distanceMeters: Double, speedMps: Double, headingDifferenceDegrees: Double): Boolean =
        distanceMeters > 15.0 && speedMps > 4.0 && headingDifferenceDegrees.isFinite() && kotlin.math.abs(headingDifferenceDegrees) > 70.0

    /** Reject implausible forward snaps that would skip too much route progress between trusted fixes. */
    fun isProgressJump(candidateProgressMeters: Double, trustedProgressMeters: Double, speedMps: Double, elapsedSeconds: Double): Boolean {
        if (!candidateProgressMeters.isFinite() || !trustedProgressMeters.isFinite() ||
            !speedMps.isFinite() || !elapsedSeconds.isFinite() || trustedProgressMeters < 0.0) return false
        val allowedAdvance = maxOf(400.0, speedMps * 1.6 * (elapsedSeconds.coerceAtLeast(0.0)) + 250.0)
        return candidateProgressMeters > trustedProgressMeters + allowedAdvance
    }

    /** Camera zoom compensation for Android Auto viewports, independent of Android/Mapbox objects. */
    fun carViewportZoomOffset(widthPx: Int, heightPx: Int, dpi: Int): Double {
        if (widthPx <= 0 || heightPx <= 0 || dpi <= 0) return 0.0
        val density = (dpi / 160.0).coerceAtLeast(0.5)
        val shortDp = minOf(widthPx, heightPx) / density
        val aspect = maxOf(widthPx, heightPx).toDouble() / minOf(widthPx, heightPx).coerceAtLeast(1)
        val sizeOffset = when {
            shortDp < 360.0 -> -0.45
            shortDp < 440.0 -> -0.30
            shortDp < 540.0 -> -0.15
            shortDp < 640.0 -> -0.08
            else -> 0.0
        }
        val shapeOffset = when {
            aspect < 1.15 -> -0.18
            aspect < 1.45 -> -0.10
            aspect > 2.50 -> 0.05
            else -> 0.0
        }
        // (close to the phone's zoom: it used to be 1.15 to 1.8 levels further out, too far for Mapbox's 3D trees to be drawn)
        return (-0.30 + sizeOffset + shapeOffset).coerceIn(-0.90, -0.15)
    }

    /**
     * Remaining time from per-segment route durations. Returns null when the annotation doesn't line up
     * with the route geometry, allowing callers to use the provider's whole-route fallback safely.
     */
    fun remainingSegmentTime(cumulativeDistances: DoubleArray, segmentSeconds: DoubleArray, progressMeters: Double): Double? {
        if (cumulativeDistances.size < 2 || segmentSeconds.size != cumulativeDistances.size - 1 ||
            !progressMeters.isFinite()) return null
        var remaining = 0.0
        for (i in segmentSeconds.indices) {
            val start = cumulativeDistances[i]
            val end = cumulativeDistances[i + 1]
            val seconds = segmentSeconds[i]
            if (!start.isFinite() || !end.isFinite() || !seconds.isFinite() || seconds < 0.0 || end < start) return null
            val length = end - start
            if (length <= 0.0 || progressMeters >= end) continue
            val fraction = ((end - maxOf(progressMeters, start)) / length).coerceIn(0.0, 1.0)
            remaining += seconds * fraction
        }
        return remaining.takeIf { it.isFinite() }
    }

    /** The signed sweep from the entry (bottom) to a roundabout exit, following the local traffic direction. */
    fun roundaboutSweep(exitRelativeDegrees: Double, clockwise: Boolean): Float {
        if (!exitRelativeDegrees.isFinite()) return 0f
        val exitCanvas = ((exitRelativeDegrees - 90.0) % 360.0 + 360.0) % 360.0
        val startCanvas = 90.0
        return if (clockwise) ((exitCanvas - startCanvas + 360.0) % 360.0).toFloat()
        else -((startCanvas - exitCanvas + 360.0) % 360.0).toFloat()
    }

    /** Clamp the map renderer frame cap to a safe range; settings expose 20/30/45/60 and auto defaults to 30. */
    fun mapFrameLimit(requestedFps: Int): Int = requestedFps.coerceIn(15, 60)

    /** Smooths zoom at a fixed maximum change per camera update to avoid visible zoom pulses. */
    fun smoothZoom(current: Double, target: Double, maxStep: Double = 0.10): Double {
        if (!current.isFinite() || current <= 0.0) return target
        val step = maxStep.coerceAtLeast(0.0)
        return current + (target - current).coerceIn(-step, step)
    }
}
