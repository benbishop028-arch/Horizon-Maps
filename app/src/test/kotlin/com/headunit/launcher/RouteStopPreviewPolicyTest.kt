package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteStopPreviewPolicyTest {
    @Test fun estimatesStopDistanceAndArrivalTimeAlongSelectedRoute() {
        val result = RouteStopPreviewPolicy.estimate(
            doubleArrayOf(500.0), doubleArrayOf(20.0), 900.0, 600.0,
        ) { offset -> if (offset >= 500.0) 300.0 else 600.0 }

        assertEquals(1, result.size)
        assertEquals(500.0, result[0].distanceMeters, 0.001)
        assertEquals(450.0, result[0].travelSeconds, 0.001)
    }

    @Test fun ignoresStopsTooFarFromRouteOrWithoutMatchingDistance() {
        val result = RouteStopPreviewPolicy.estimate(
            doubleArrayOf(300.0, 700.0, 900.0),
            doubleArrayOf(399.0, 400.0),
            600.0, 600.0,
        ) { 300.0 }

        assertEquals(1, result.size)
        assertEquals(0, result[0].index)
    }

    @Test fun invalidRouteTimesProduceNoEstimates() {
        assertTrue(RouteStopPreviewPolicy.estimate(doubleArrayOf(100.0), doubleArrayOf(1.0), 0.0, 60.0) { 30.0 }.isEmpty())
        assertTrue(RouteStopPreviewPolicy.estimate(doubleArrayOf(100.0), doubleArrayOf(1.0), 60.0, Double.NaN) { 30.0 }.isEmpty())
    }
}
