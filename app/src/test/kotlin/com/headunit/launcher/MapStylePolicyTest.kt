package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class MapStylePolicyTest {
    @Test fun satelliteTakesPrecedenceOverDetailAndNightSettings() {
        assertEquals(BaseMapStyle.STANDARD_SATELLITE, MapStylePolicy.choose(true, "lightest", true))
        assertEquals(BaseMapStyle.STANDARD_SATELLITE, MapStylePolicy.choose(true, "full", false))
    }

    @Test fun lightestModeSelectsClassicStreetsStyle() {
        assertEquals(BaseMapStyle.STREETS, MapStylePolicy.choose(false, "lightest", false))
        assertEquals(BaseMapStyle.DARK_STREETS, MapStylePolicy.choose(false, "lightest", true))
    }

    @Test fun onlyFullDetailEnablesHeavyMapFeatures() {
        assertEquals(true, MapStylePolicy.supportsFullDetailFeatures("full"))
        assertEquals(false, MapStylePolicy.supportsFullDetailFeatures("lite"))
        assertEquals(false, MapStylePolicy.supportsFullDetailFeatures("lightest"))
        assertEquals(false, MapStylePolicy.supportsFullDetailFeatures("unknown"))
    }

    @Test fun otherDetailLevelsUseStandardStyle() {
        assertEquals(BaseMapStyle.STANDARD, MapStylePolicy.choose(false, "full", false))
        assertEquals(BaseMapStyle.STANDARD, MapStylePolicy.choose(false, "full", true))
        assertEquals(BaseMapStyle.STANDARD, MapStylePolicy.choose(false, "lite", true))
    }
}
