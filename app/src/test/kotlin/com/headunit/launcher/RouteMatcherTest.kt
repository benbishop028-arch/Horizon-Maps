package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMatcherTest {
    private fun route(points: List<LL>): Route {
        val cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) cumulative[i] = cumulative[i - 1] + Geo.dist(points[i - 1], points[i])
        return Route(points, cumulative, emptyList(), cumulative.last(), 0.0, 0.0)
    }

    @Test fun snapsOntoMiddleOfSegmentAndComputesProgress() {
        val r = route(listOf(LL(50.0, -1.0), LL(50.0, -0.99), LL(50.0, -0.98)))
        val result = RouteMatcher.snap(r, LL(50.0002, -0.995), 0)
        assertEquals(0, result.segmentIndex)
        assertEquals(50.0, result.point.lat, 0.00001)
        assertEquals(-0.995, result.point.lon, 0.00001)
        assertTrue(result.dist in 20.0..25.0)
        assertEquals(Geo.dist(r.pts[0], result.point), result.progress, 2.0)
    }

    @Test fun fallsBackToFullRouteWhenLocalScanIsFarAway() {
        val points = (0..700).map { i -> LL(50.0, -1.0 + i * 0.0001) }
        val r = route(points)
        val result = RouteMatcher.snap(r, points[650], 0)
        assertEquals(649, result.segmentIndex)
        assertEquals(0.0, result.dist, 0.01)
    }

    @Test fun degenerateSegmentDoesNotDivideByZero() {
        val r = route(listOf(LL(50.0, -1.0), LL(50.0, -1.0), LL(50.0, -0.99)))
        val result = RouteMatcher.snap(r, LL(50.0, -1.0), 0)
        assertEquals(0.0, result.dist, 0.01)
        assertTrue(result.progress.isFinite())
    }
}
