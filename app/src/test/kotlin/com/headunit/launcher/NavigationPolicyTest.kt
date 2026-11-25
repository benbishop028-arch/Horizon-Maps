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

    @Test fun poorOrMissingNetworkAccuracyIsRejectedWhileFollowingRoute() {
        assertFalse(NavigationPolicy.shouldRejectNetworkPositionOnRoute(30.0))
        assertFalse(NavigationPolicy.shouldRejectNetworkPositionOnRoute(5.0))
        assertTrue(NavigationPolicy.shouldRejectNetworkPositionOnRoute(30.01))
        assertTrue(NavigationPolicy.shouldRejectNetworkPositionOnRoute(null))
        assertTrue(NavigationPolicy.shouldRejectNetworkPositionOnRoute(0.0))
        assertTrue(NavigationPolicy.shouldRejectNetworkPositionOnRoute(-1.0))
        assertTrue(NavigationPolicy.shouldRejectNetworkPositionOnRoute(Double.NaN))
        assertTrue(NavigationPolicy.shouldRejectNetworkPositionOnRoute(Double.POSITIVE_INFINITY))
        // after a minute without GPS, rough positions up to 150 m are taken again
        assertFalse(NavigationPolicy.shouldRejectNetworkPositionOnRoute(120.0, 61_000L))
        assertTrue(NavigationPolicy.shouldRejectNetworkPositionOnRoute(120.0, 30_000L))
        assertTrue(NavigationPolicy.shouldRejectNetworkPositionOnRoute(400.0, 61_000L))
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
        assertEquals(2, NavigationPolicy.offRouteStrikeCount(false, 90.0, 30.0))
        assertEquals(3, NavigationPolicy.offRouteStrikeCount(false, 91.0, 30.0))
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

    // (two tests of Geo.guidanceLabel were here: that function is no longer in the app, so they could not compile. Fix by Smile9799)

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

    @Test fun remainingSegmentTimeUsesActualRoadDurationsAndCurrentProgress() {
        val cumulative = doubleArrayOf(0.0, 100.0, 300.0, 400.0)
        val seconds = doubleArrayOf(60.0, 300.0, 30.0)
        assertEquals(390.0, NavigationPolicy.remainingSegmentTime(cumulative, seconds, 0.0)!!, 0.001)
        assertEquals(285.0, NavigationPolicy.remainingSegmentTime(cumulative, seconds, 150.0)!!, 0.001)
        assertEquals(15.0, NavigationPolicy.remainingSegmentTime(cumulative, seconds, 350.0)!!, 0.001)
        assertEquals(0.0, NavigationPolicy.remainingSegmentTime(cumulative, seconds, 450.0)!!, 0.001)
    }

    @Test fun remainingSegmentTimeRejectsMismatchedOrInvalidAnnotations() {
        assertTrue(NavigationPolicy.remainingSegmentTime(doubleArrayOf(0.0, 100.0), doubleArrayOf(), 0.0) == null)
        assertTrue(NavigationPolicy.remainingSegmentTime(doubleArrayOf(0.0, 100.0), doubleArrayOf(-1.0), 0.0) == null)
        assertTrue(NavigationPolicy.remainingSegmentTime(doubleArrayOf(0.0, Double.NaN), doubleArrayOf(10.0), 0.0) == null)
    }

    @Test fun roundaboutSweepFollowsLocalTrafficSide() {
        // First exit: left-hand traffic takes the clockwise arc; right-hand traffic takes the anticlockwise arc.
        assertEquals(90f, NavigationPolicy.roundaboutSweep(270.0, true), 0.001f)
        assertEquals(-90f, NavigationPolicy.roundaboutSweep(90.0, false), 0.001f)
        // Straight ahead is a half-turn in either direction; invalid bearings draw no misleading arc.
        assertEquals(180f, NavigationPolicy.roundaboutSweep(0.0, true), 0.001f)
        assertEquals(-180f, NavigationPolicy.roundaboutSweep(0.0, false), 0.001f)
        assertEquals(0f, NavigationPolicy.roundaboutSweep(Double.NaN, true), 0.001f)
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
        assertTrue(compact in -0.90..-0.15)
        assertTrue(wide in -0.90..-0.15)
        assertTrue(compact < wide)
    }
}
