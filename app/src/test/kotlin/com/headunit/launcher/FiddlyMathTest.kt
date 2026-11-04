package com.headunit.launcher

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The small calculations that are easy to get subtly wrong: lorry limits, prices, distances, camera directions. */
class FiddlyMathTest {
    @After fun backToCar() { Vehicle.lorry = false; Vehicle.heightCm = 400; Vehicle.widthCm = 255; Vehicle.lengthCm = 1650; Vehicle.weightT10 = 440; Vehicle.axleT10 = 115; Vehicle.hazmat = false }

    // ---- lorry limits read from OpenStreetMap ----
    @Test fun heightsInMetres() {
        assertEquals(4.1, LorryLimits.parseLen("4.1")!!, 0.001)
        assertEquals(4.1, LorryLimits.parseLen("4.1 m")!!, 0.001)
        assertEquals(4.1, LorryLimits.parseLen(" 4.1m ")!!, 0.001)
    }

    @Test fun heightsInFeetAndInches() {
        assertEquals((13 * 12 + 6) * 0.0254, LorryLimits.parseLen("13'6\"")!!, 0.001)
        assertEquals((13 * 12 + 6) * 0.0254, LorryLimits.parseLen("13 ft 6 in")!!, 0.001)
        assertEquals(14 * 12 * 0.0254, LorryLimits.parseLen("14'")!!, 0.001)
    }

    @Test fun heightsThatAreNotNumbers() {
        assertNull(LorryLimits.parseLen("none"))
        assertNull(LorryLimits.parseLen("default"))
        assertNull(LorryLimits.parseLen(""))
        assertNull(LorryLimits.parseLen(null))
    }

    @Test fun weights() {
        assertEquals(7.5, LorryLimits.parseWeight("7.5")!!, 0.001)
        assertEquals(7.5, LorryLimits.parseWeight("7.5 t")!!, 0.001)
        assertEquals(7.5, LorryLimits.parseWeight("7500 kg")!!, 0.001)
        assertEquals(9.07, LorryLimits.parseWeight("10 st")!!, 0.01)
        assertNull(LorryLimits.parseWeight("unsigned"))
    }

    @Test fun limitSigns() {
        val low = LorryLimits.Lim(0.0, LL(51.0, -1.0), "height", 4.1, true)
        assertEquals("4.1m", LorryLimits.signText(low, false))
        assertEquals("13′5″", LorryLimits.signText(low, true))
        assertEquals("7.5t", LorryLimits.signText(LorryLimits.Lim(0.0, LL(51.0, -1.0), "weight", 7.5, true), false))
        assertEquals("18t", LorryLimits.signText(LorryLimits.Lim(0.0, LL(51.0, -1.0), "weight", 18.0, true), false))
    }

    // ---- what the route services are asked for a lorry ----
    @Test fun carAsksForNothingExtra() {
        Vehicle.lorry = false
        assertEquals("", Vehicle.tomtom())
        assertEquals("", Vehicle.mapbox())
    }

    @Test fun lorryAsksTomTomAndMapboxForItsSize() {
        Vehicle.lorry = true; Vehicle.heightCm = 420; Vehicle.weightT10 = 440; Vehicle.axleT10 = 115
        val tt = Vehicle.tomtom()
        assertTrue(tt.contains("travelMode=truck"))
        assertTrue(tt.contains("vehicleHeight=4.20"))
        assertTrue(tt.contains("vehicleWeight=44000"))
        assertTrue(tt.contains("vehicleAxleWeight=11500"))
        assertTrue(!tt.contains("vehicleLoadType"))
        Vehicle.hazmat = true
        assertTrue(Vehicle.tomtom().contains("vehicleLoadType=otherHazmatGeneral"))
        val mb = Vehicle.mapbox()
        assertTrue(mb.contains("max_height=4.20"))
        assertTrue(mb.contains("max_weight=44.00"))
    }

    @Test fun lorryHeightInFeetToo() {
        assertEquals("4.20 m · 13′ 9″", Vehicle.heightText(420, true))
        assertEquals("4.20 m", Vehicle.heightText(420, false))
    }

    // ---- fuel prices ----
    @Test fun fuelPricesInEachCurrency() {
        assertEquals("139.9p", fmtFuelPrice(139.9, "p"))
        assertEquals("€1.789", fmtFuelPrice(178.9, "€"))
        assertEquals("189.9c", fmtFuelPrice(189.9, "c"))
        assertEquals("c", EuFuel.unit("AU"))
        assertEquals("€", EuFuel.unit("FR"))
    }

    // ---- distances on the cards ----
    @Test fun distances() {
        assertEquals("150 m", Geo.fmtDist(150.0, false))
        assertEquals("1.5 km", Geo.fmtDist(1500.0, false))
        assertEquals("1.0 mi", Geo.fmtDist(1609.344, true))
        assertEquals("70 ft", Geo.fmtDist(20.0, true))
    }

    // ---- which way a speed camera points ----
    @Test fun cameraDirections() {
        assertEquals(45.0, Overpass.parseDir("NE")!!, 0.001)
        assertEquals(10.0, Overpass.parseDir("370")!!, 0.001)
        assertNull(Overpass.parseDir("90;270"))
        assertNull(Overpass.parseDir(""))
    }
}
