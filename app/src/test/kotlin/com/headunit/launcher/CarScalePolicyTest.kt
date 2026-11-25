package com.headunit.launcher

import org.junit.Assert.assertEquals
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
}
