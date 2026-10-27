package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyTest {
    @Test fun speedZoomIsCloserWhenSlowAndWiderAtSpeed() {
        assertEquals(18.4, NavigationPolicy.speedZoom(0.0), 0.0001)
        assertEquals(15.7, NavigationPolicy.speedZoom(40.0), 0.0001)
        assertTrue(NavigationPolicy.speedZoom(8.0) > NavigationPolicy.speedZoom(30.0))
        assertTrue(NavigationPolicy.speedZoom(0.0) > NavigationPolicy.speedZoom(10.0))
        assertTrue(NavigationPolicy.speedZoom(10.0) > NavigationPolicy.speedZoom(25.0))
        assertTrue(NavigationPolicy.speedZoom(25.0) > NavigationPolicy.speedZoom(40.0))
    }

    @Test fun speedZoomClampsAndHandlesInvalidSpeed() {
        assertEquals(18.4, NavigationPolicy.speedZoom(-4.0), 0.0001)
        assertEquals(15.7, NavigationPolicy.speedZoom(100.0), 0.0001)
        assertEquals(15.7, NavigationPolicy.speedZoom(Double.NaN), 0.0001)
    }

    @Test fun routeZoomAdjustsForRoadType() {
        val town = NavigationPolicy.routeSpeedZoom(15.0, "street")
        val motorway = NavigationPolicy.routeSpeedZoom(15.0, "motorway")
        assertTrue(town > motorway)
        assertEquals(18.7, NavigationPolicy.routeSpeedZoom(0.0, null), 0.0001)
        assertEquals(15.6, NavigationPolicy.routeSpeedZoom(36.0, null), 0.0001)
    }

    @Test fun poorGpsAccuracyCannotTriggerReroute() {
        assertTrue(NavigationPolicy.gpsReliableForReroute(0.0)) // unknown accuracy from older providers
        assertTrue(NavigationPolicy.gpsReliableForReroute(45.0))
        assertFalse(NavigationPolicy.gpsReliableForReroute(45.1))
        assertFalse(NavigationPolicy.gpsReliableForReroute(100.0))
    }

    @Test fun offRouteLimitWidensWithGpsAccuracyAndHandlesUnknownAccuracy() {
        assertEquals(30.0, NavigationPolicy.offRouteLimit(0.0), 0.0001)
        assertEquals(30.0, NavigationPolicy.offRouteLimit(Double.NaN), 0.0001)
        assertEquals(30.0, NavigationPolicy.offRouteLimit(8.0), 0.0001)
        assertEquals(87.0, NavigationPolicy.offRouteLimit(50.0), 0.0001)
    }

    @Test fun largeOffRouteDeparturesCountAsTwoStrikes() {
        assertEquals(2, NavigationPolicy.offRouteStrikeCount(true, 10.0, 30.0))
        assertEquals(2, NavigationPolicy.offRouteStrikeCount(false, 61.0, 30.0))
        assertEquals(1, NavigationPolicy.offRouteStrikeCount(false, 60.0, 30.0))
        assertEquals(1, NavigationPolicy.offRouteStrikeCount(false, 31.0, 30.0))
    }

    @Test fun headingAwayRerouteRequiresDistanceSpeedAndClearHeadingDifference() {
        assertTrue(NavigationPolicy.shouldCountHeadingAwayStrike(15.1, 4.1, 70.1))
        assertTrue(NavigationPolicy.shouldCountHeadingAwayStrike(20.0, 8.0, -90.0))
        assertFalse(NavigationPolicy.shouldCountHeadingAwayStrike(15.0, 8.0, 90.0))
        assertFalse(NavigationPolicy.shouldCountHeadingAwayStrike(20.0, 4.0, 90.0))
        assertFalse(NavigationPolicy.shouldCountHeadingAwayStrike(20.0, 8.0, 70.0))
        assertFalse(NavigationPolicy.shouldCountHeadingAwayStrike(20.0, 8.0, Double.NaN))
    }

    @Test fun progressJumpRequiresAdvanceBeyondSpeedAndTimeAllowance() {
        assertFalse(NavigationPolicy.isProgressJump(500.0, 100.0, 0.0, 1.0)) // exactly the 400 m boundary
        assertTrue(NavigationPolicy.isProgressJump(500.1, 100.0, 0.0, 1.0))
        assertFalse(NavigationPolicy.isProgressJump(600.0, 100.0, 20.0, 10.0))
        assertTrue(NavigationPolicy.isProgressJump(671.0, 100.0, 20.0, 10.0))
    }

    @Test fun progressJumpPolicyRejectsNonFiniteInputsSafely() {
        assertFalse(NavigationPolicy.isProgressJump(Double.NaN, 100.0, 10.0, 1.0))
        assertFalse(NavigationPolicy.isProgressJump(500.0, Double.POSITIVE_INFINITY, 10.0, 1.0))
        assertFalse(NavigationPolicy.isProgressJump(500.0, 100.0, Double.NaN, 1.0))
        assertFalse(NavigationPolicy.isProgressJump(500.0, 100.0, 10.0, Double.NaN))
    }

    @Test fun roundaboutGuidanceDoesNotShowRawLeftTurnMessage() {
        val step = Step(300.0, "ROUNDABOUT_CROSS", "", "Turn left", 2)
        assertEquals("at the roundabout, take the 2nd exit", Geo.guidanceLabel(step))
    }

    @Test fun roundaboutKeepsRoadNameWhenAvailable() {
        val step = Step(300.0, "ROUNDABOUT_CROSS", "A3057", "Turn left", 2)
        assertEquals("A3057", Geo.guidanceLabel(step))
    }

    @Test fun zoomChangesAreDamped() {
        assertEquals(15.1, NavigationPolicy.smoothZoom(15.0, 16.0), 0.0001)
        assertEquals(14.9, NavigationPolicy.smoothZoom(15.0, 14.0), 0.0001)
        assertEquals(16.0, NavigationPolicy.smoothZoom(0.0, 16.0), 0.0001)
    }
    @Test fun gpsReroutePolicyHandlesBoundaryAndNonFiniteValues() {
        assertTrue(NavigationPolicy.gpsReliableForReroute(45.0))
        assertFalse(NavigationPolicy.gpsReliableForReroute(45.01))
        assertTrue(NavigationPolicy.gpsReliableForReroute(Double.NaN))
        assertTrue(NavigationPolicy.gpsReliableForReroute(Double.POSITIVE_INFINITY))
    }

    @Test fun mapFrameLimitHonoursConfiguredRateAndClampsInvalidValues() {
        assertEquals(20, NavigationPolicy.mapFrameLimit(20))
        assertEquals(30, NavigationPolicy.mapFrameLimit(30))
        assertEquals(45, NavigationPolicy.mapFrameLimit(45))
        assertEquals(60, NavigationPolicy.mapFrameLimit(60))
        assertEquals(15, NavigationPolicy.mapFrameLimit(0))
        assertEquals(60, NavigationPolicy.mapFrameLimit(120))
    }

    @Test fun smoothZoomDoesNotOvershootTarget() {
        assertEquals(15.1, NavigationPolicy.smoothZoom(15.0, 100.0), 0.0001)
        assertEquals(14.9, NavigationPolicy.smoothZoom(15.0, -100.0), 0.0001)
        assertEquals(15.0, NavigationPolicy.smoothZoom(15.0, 100.0, -1.0), 0.0001)
    }


    @Test fun carViewportZoomOffsetHandlesInvalidAndTypicalDisplays() {
        assertEquals(0.0, NavigationPolicy.carViewportZoomOffset(0, 1200, 240), 0.0)
        val compact = NavigationPolicy.carViewportZoomOffset(800, 480, 160)
        val wide = NavigationPolicy.carViewportZoomOffset(2000, 1200, 240)
        assertTrue(compact in -1.80..-1.00)
        assertTrue(wide in -1.80..-1.00)
        assertTrue(compact < wide)
    }
}
