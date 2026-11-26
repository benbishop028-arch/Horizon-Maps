package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarScalePolicyTest {
    @Test fun requestedOverallScaleIsAppliedWhenTheScreenHasRoom() {
        val result = CarScalePolicy.resolve(150, 2000f, 1200f)
        assertEquals(1.5f, result.requested, 0.0001f)
        assertEquals(1.5f, result.effective, 0.0001f)
        assertEquals(300, result.maximumPercent)
    }

    @Test fun overallScaleIsCappedByTheAvailableScreen() {
        val result = CarScalePolicy.resolve(300, 500f, 250f)
        assertEquals(3f, result.requested, 0.0001f)
        assertEquals(2.5f, result.effective, 0.0001f)
        assertEquals(250, result.maximumPercent)
    }

    @Test fun sizesBelowNormalAreNotRaisedToTheFitCap() {
        val result = CarScalePolicy.resolve(70, 500f, 250f)
        assertEquals(0.7f, result.effective, 0.0001f)
        assertEquals(100, result.maximumPercent)
    }

    @Test fun requestedSizeIsClampedToSupportedRange() {
        assertEquals(0.4f, CarScalePolicy.resolve(10, 2000f, 1200f).requested, 0.0001f)
        assertEquals(3f, CarScalePolicy.resolve(500, 2000f, 1200f).requested, 0.0001f)
    }

    @Test fun widescreenAndLandscapeHeadUnitsKeepTheirSize() {
        assertEquals(1f, CarScalePolicy.shapeFactor(1920, 1080), 0.0001f)
        assertEquals(1f, CarScalePolicy.shapeFactor(800, 480), 0.0001f)
        assertEquals(1f, CarScalePolicy.shapeFactor(1024, 768), 0.0001f)
        // a tall 9:16 screen measures by its width, as before
        assertEquals(1f, CarScalePolicy.shapeFactor(1080, 1920), 0.0001f)
    }

    @Test fun squarerScreensGetSmallerCards() {
        // an upright, almost square car screen (#418)
        val upright = CarScalePolicy.shapeFactor(1024, 1200)
        assertTrue(upright in 0.75f..0.85f)
        assertEquals(0.75f, CarScalePolicy.shapeFactor(1000, 1000), 0.0001f)
        // easing back to no change by 4:3, with no jump
        val a = CarScalePolicy.shapeFactor(1100, 1000); val b = CarScalePolicy.shapeFactor(1110, 1000)
        assertTrue(a in 0.75f..1f && b >= a && b - a < 0.02f)
    }
}
