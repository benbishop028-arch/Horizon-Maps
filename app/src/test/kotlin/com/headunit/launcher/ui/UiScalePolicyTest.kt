package com.headunit.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiScalePolicyTest {
    @Test fun ordinaryAndLargeScreensKeepNormalSizing() {
        assertEquals(100, recommendedMainUiScale(800, 480))
        assertEquals(100, recommendedMainUiScale(2000, 1200))
    }

    @Test fun compactScreensScaleDownGradually() {
        assertEquals(92, recommendedMainUiScale(320, 240))
        assertEquals(96, recommendedMainUiScale(800, 360))
        assertEquals(98, recommendedMainUiScale(900, 450))
        assertEquals(100, recommendedMainUiScale(1000, 550))
    }

    @Test fun invalidDimensionsUseSafeDefault() {
        assertEquals(100, recommendedMainUiScale(0, 0))
    }
}
