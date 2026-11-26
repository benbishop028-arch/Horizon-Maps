package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainRoadPolicyTest {
    @Test fun smallRoadClasses() {
        assertTrue(MainRoadPolicy.isMinor("street"))
        assertTrue(MainRoadPolicy.isMinor("service"))
        assertFalse(MainRoadPolicy.isMinor("primary"))
        assertFalse(MainRoadPolicy.isMinor("motorway"))
    }

    @Test fun picksTheMainRoadRouteWhenOnlyAFewMinutesSlower() {
        // the quickest cuts through 3 km of back streets; the second is 4 minutes slower with 400 m of them
        assertEquals(1, MainRoadPolicy.pick(listOf(1800.0, 2040.0), listOf(3000.0, 400.0)))
    }

    @Test fun leavesTheOrderWhenTheMainRoadRouteIsMuchSlower() {
        assertEquals(-1, MainRoadPolicy.pick(listOf(1800.0, 2400.0), listOf(3000.0, 400.0)))
    }

    @Test fun longTripsAllowTenPercent() {
        // 2 hours: up to 12 minutes slower is fine
        assertEquals(1, MainRoadPolicy.pick(listOf(7200.0, 7800.0), listOf(5000.0, 500.0)))
    }

    @Test fun smallDifferencesAndUnknownRoadsAreIgnored() {
        assertEquals(-1, MainRoadPolicy.pick(listOf(1800.0, 1850.0), listOf(1000.0, 900.0)))
        assertEquals(-1, MainRoadPolicy.pick(listOf(1800.0, 1850.0), listOf(-1.0, 100.0)))
        assertEquals(-1, MainRoadPolicy.pick(listOf(1800.0, 1850.0), listOf(3000.0, -1.0)))
    }

    @Test fun motorwayChoiceForShortTrips() {
        assertTrue(NavigationPolicy.avoidMotorways(always = true, shortTrips = false, underM = 32187, tripM = 100_000.0))
        assertTrue(NavigationPolicy.avoidMotorways(always = false, shortTrips = true, underM = 32187, tripM = 10_000.0))
        assertFalse(NavigationPolicy.avoidMotorways(always = false, shortTrips = true, underM = 32187, tripM = 50_000.0))
        assertFalse(NavigationPolicy.avoidMotorways(always = false, shortTrips = true, underM = 32187, tripM = null))
        assertFalse(NavigationPolicy.avoidMotorways(always = false, shortTrips = false, underM = 32187, tripM = 1_000.0))
    }
}
