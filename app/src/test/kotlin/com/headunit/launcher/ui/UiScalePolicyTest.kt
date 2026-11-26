package com.headunit.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiScalePolicyTest {
    @Test fun ordinaryPhonesAndLargeScreensStayAtTheBaseline() {
        assertEquals(100, recommendedMainUiScale(412, 915))   // a typical phone, portrait
        assertEquals(100, recommendedMainUiScale(915, 412))   // the same phone sideways
        assertEquals(100, recommendedMainUiScale(1024, 600))  // a 10-inch head unit
        assertEquals(100, recommendedMainUiScale(800, 480))   // a 7-inch head unit
        assertEquals(100, recommendedMainUiScale(2000, 1200)) // never bigger on a big screen
    }

    @Test fun narrowOrCrampedScreensStepDown() {
        assertEquals(90, recommendedMainUiScale(360, 800))    // a narrow phone
        assertEquals(85, recommendedMainUiScale(640, 360))    // a small landscape screen
        assertEquals(85, recommendedMainUiScale(600, 600))    // a square screen
        assertEquals(85, recommendedMainUiScale(320, 240))    // never below 85%
    }

    @Test fun invalidDimensionsUseSafeDefault() {
        assertEquals(100, recommendedMainUiScale(0, 0))
    }
}
