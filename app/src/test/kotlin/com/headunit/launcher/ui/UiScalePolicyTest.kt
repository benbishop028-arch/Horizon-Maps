package com.headunit.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiScalePolicyTest {
    @Test fun automaticScaleIsConsistentAcrossScreenSizes() {
        assertEquals(100, recommendedMainUiScale(800, 480))
        assertEquals(100, recommendedMainUiScale(2000, 1200))
        assertEquals(100, recommendedMainUiScale(320, 240))
        assertEquals(100, recommendedMainUiScale(800, 360))
        assertEquals(100, recommendedMainUiScale(900, 450))
        assertEquals(100, recommendedMainUiScale(1000, 550))
    }

    @Test fun invalidDimensionsUseSameSafeDefault() {
        assertEquals(100, recommendedMainUiScale(0, 0))
    }
}
