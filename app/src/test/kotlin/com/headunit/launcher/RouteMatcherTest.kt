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

    @Test fun restoringPreDemoMatcherIndexAvoidsSnappingToDemoEndpoint() {
        // The genuine vehicle was around segment 100 before the demo. A later segment loops
        // back to the same coordinates, where the demo eventually stopped.
        val points = (0..700).map { i -> LL(50.0, -1.0 + i * 0.0001) }.toMutableList()
        points[650] = LL(50.00002, -0.99)
        points[651] = LL(50.0, -0.99)
        val r = route(points)
        val genuinePosition = points[100]

        val restoredBaseline = RouteMatcher.snap(r, genuinePosition, previousIndex = 100)
        val staleDemoBaseline = RouteMatcher.snap(r, genuinePosition, previousIndex = 650)

        assertEquals(99, restoredBaseline.segmentIndex)
        assertTrue(staleDemoBaseline.segmentIndex >= 649)
        assertTrue(restoredBaseline.progress < staleDemoBaseline.progress)
    }

    @Test fun headingChoosesTheCorrectDirectionOnOverlappingRoadGeometry() {
        val west = LL(50.0, -0.99)
        val east = LL(50.0, -1.0)
        val r = route(listOf(west, east, west))
        val result = RouteMatcher.snap(r, LL(50.0, -0.995), 0, 90.0)
        assertEquals(1, result.segmentIndex)
        assertTrue(result.dist < 1.0)
    }

    @Test fun initialSnapStaysNearRouteStartWhenLaterLegPassesCloser() {
        // The route begins about 110 m north of the GPS fix, then loops back to within 6 m
        // after travelling more than 300 m. Initial matching must prefer the start section.
        val r = route(listOf(
            LL(50.001, -1.0),
            LL(50.001, -0.996),
            LL(50.00005, -0.996),
            LL(50.00005, -1.0),
        ))
        val position = LL(50.0, -1.0)

        val unrestricted = RouteMatcher.snap(r, position, 0)
        val initial = RouteMatcher.snap(r, position, 0, maxProgressMeters = 300.0)

        assertEquals(2, unrestricted.segmentIndex)
        assertEquals(0, initial.segmentIndex)
        assertTrue(initial.progress < 300.0)
        assertTrue(initial.dist > unrestricted.dist)
    }

    @Test fun repeatedStationaryStartFixesKeepMatchingTheOpeningRouteSection() {
        val r = route(listOf(
            LL(50.001, -1.0),
            LL(50.001, -0.996),
            LL(50.00005, -0.996),
            LL(50.00005, -1.0),
        ))
        val position = LL(50.0, -1.0)

        // Both fixes are stationary: keep the initial 300 m window active for each one.
        val first = RouteMatcher.snap(r, position, 0, heading = null, maxProgressMeters = 300.0)
        val second = RouteMatcher.snap(r, position, first.segmentIndex, heading = null, maxProgressMeters = 300.0)
        val unrestrictedWithStaleHeading = RouteMatcher.snap(r, position, 0, heading = 270.0)

        assertEquals(0, first.segmentIndex)
        assertEquals(0, second.segmentIndex)
        assertTrue(second.progress < 300.0)
        assertTrue(unrestrictedWithStaleHeading.progress > 300.0)
    }

    @Test fun degenerateSegmentDoesNotDivideByZero() {
        val r = route(listOf(LL(50.0, -1.0), LL(50.0, -1.0), LL(50.0, -0.99)))
        val result = RouteMatcher.snap(r, LL(50.0, -1.0), 0)
        assertEquals(0.0, result.dist, 0.01)
        assertTrue(result.progress.isFinite())
    }
}
