package com.headunit.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class JunctionSignPlacesTest {
    @Test fun keepsPlacesAndDropsRoadNumbers() {
        assertEquals("Southampton, Portsmouth", junctionSignPlaces("M27: Southampton, Portsmouth", "M27"))
        assertEquals("Leeds", junctionSignPlaces("A1(M), Leeds", ""))
    }

    @Test fun dropsJunctionNumbersAndTheRoadItself() {
        assertEquals("Winchester", junctionSignPlaces("J9 / A33 / Winchester", ""))
        assertEquals("", junctionSignPlaces("London Road", "London Road"))
        assertEquals("", junctionSignPlaces("", "M27"))
    }
}
