package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BorderDrivingInfoTest {
    @Test fun norwayShowsItsSpeedRangeAndDistinctEmergencyNumbers() {
        val info = BorderDrivingInfo.forCountry("no")!!
        assertEquals("Norway", info.country)
        assertEquals("50 / 80 / 90–110", info.limits)
        assertTrue(info.emergency.contains("113 ambulance"))
        assertTrue(info.emergency.contains("110 fire"))
    }

    @Test fun germanyMakesClearThatMotorwayLimitIsAdvisory() {
        val info = BorderDrivingInfo.forCountry("DE")!!
        assertTrue(info.limits.contains("130 recommended"))
        assertTrue(info.limits.contains("none"))
    }

    @Test fun validButUnlistedCountryDoesNotGetInventedRules() {
        val info = BorderDrivingInfo.forCountry("JP")!!
        assertTrue(info.limits.contains("no verified default"))
        assertTrue(info.emergency.contains("Not verified"))
    }

    @Test fun invalidCountryCodeIsRejected() {
        assertEquals(null, BorderDrivingInfo.forCountry("XX"))
    }
}
