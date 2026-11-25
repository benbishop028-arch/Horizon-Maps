package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineRoadPreferenceTest {
    @Test fun disabledLeavesValhallaDefaultUnchanged() {
        assertNull(OfflineRoadPreference.useHighways(false))
    }

    @Test fun enabledUsesSoftHighwayPreference() {
        assertEquals(0.8, OfflineRoadPreference.useHighways(true)!!, 0.0)
    }
}
