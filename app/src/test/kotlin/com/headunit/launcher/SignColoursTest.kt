package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignColoursTest {
    @Test fun ukMotorwaysBlueOtherRoadsGreen() {
        assertEquals(0xFF0065B3, SignColours.card("GB", true)?.first)
        assertEquals(0xFF00703C, SignColours.card("gb", false)?.first)
    }

    @Test fun nordicMotorwaysGreenOtherRoadsBlue() {
        assertEquals(0xFF007A3D, SignColours.card("NO", true)?.first)
        assertEquals(0xFF0B4EA2, SignColours.card("SE", false)?.first)
    }

    @Test fun lightSignsAndUnknownCountriesKeepTheUsualColour() {
        assertEquals(0xFF0065B3, SignColours.card("DE", true)?.first)
        assertNull(SignColours.card("DE", false))   // yellow signs: the card stays green
        assertNull(SignColours.card("US", true))
        assertNull(SignColours.card("", false))
    }
}
