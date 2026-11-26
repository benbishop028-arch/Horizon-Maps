package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessPolicyTest {
    @Test fun sliderFollowsAndroidsCurve() {
        assertEquals(1f, BrightnessPolicy.perceivedToLinear(1f), 0.01f)
        assertEquals(1f / 12f, BrightnessPolicy.perceivedToLinear(0.5f), 0.001f)
        // 10% on the slider is much dimmer than a tenth of full power (#417)
        assertTrue(BrightnessPolicy.windowBrightness(10, dim = false, systemLinear = 0.5f, autoBrightness = true) < 0.02f)
    }

    @Test fun curveRoundTrips() {
        for (p in listOf(0.05f, 0.3f, 0.5f, 0.7f, 0.95f))
            assertEquals(p, BrightnessPolicy.linearToPerceived(BrightnessPolicy.perceivedToLinear(p)), 0.001f)
    }

    @Test fun dimmingNeverBrightensAndRespectsAutoBrightness() {
        assertEquals(-1f, BrightnessPolicy.windowBrightness(100, dim = false, systemLinear = 0.3f, autoBrightness = false), 0f)
        assertEquals(-1f, BrightnessPolicy.windowBrightness(100, dim = true, systemLinear = 0.3f, autoBrightness = true), 0f)
        val dimmed = BrightnessPolicy.windowBrightness(100, dim = true, systemLinear = 0.3f, autoBrightness = false)
        assertTrue(dimmed < 0.3f && dimmed >= BrightnessPolicy.FLOOR)
        assertTrue(BrightnessPolicy.windowBrightness(40, dim = true, systemLinear = 0.3f, autoBrightness = false) <
            BrightnessPolicy.windowBrightness(40, dim = false, systemLinear = 0.3f, autoBrightness = false))
        assertTrue(BrightnessPolicy.windowBrightness(1, dim = true, systemLinear = 0f, autoBrightness = false) >= BrightnessPolicy.FLOOR)
    }
}
