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

    /** Unknown accuracy is allowed for compatibility; known poor fixes should not trigger a reroute. */
    fun gpsReliableForReroute(accuracyMeters: Double): Boolean =
        !accuracyMeters.isFinite() || accuracyMeters <= 0.0 || accuracyMeters <= 45.0

    /** Distance from the route tolerated before a credible GPS fix counts as off-route. */
    fun offRouteLimit(accuracyMeters: Double): Double {
        val usableAccuracy = if (accuracyMeters.isFinite() && accuracyMeters > 0.0) accuracyMeters else 8.0
        return maxOf(30.0, usableAccuracy * 1.5 + 12.0)
    }

    /** A large departure or impossible progress jump contributes two strikes to the reroute counter. */
    fun offRouteStrikeCount(progressJump: Boolean, distanceMeters: Double, limitMeters: Double): Int =
        if (progressJump || distanceMeters > limitMeters * 2.0) 2 else 1

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

    /** Clamp the map renderer frame cap to a safe range; settings expose 20/30/45/60 and auto defaults to 30. */
    fun mapFrameLimit(requestedFps: Int): Int = requestedFps.coerceIn(15, 60)

    /** Smooths zoom at a fixed maximum change per camera update to avoid visible zoom pulses. */
    fun smoothZoom(current: Double, target: Double, maxStep: Double = 0.10): Double {
        if (!current.isFinite() || current <= 0.0) return target
        val step = maxStep.coerceAtLeast(0.0)
        return current + (target - current).coerceIn(-step, step)
    }
}
