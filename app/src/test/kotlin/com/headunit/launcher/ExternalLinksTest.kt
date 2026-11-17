package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Navigation links from Gemini / Google Assistant: "navigate to …" replaces the destination, "add a stop at …" doesn't. */
class ExternalLinksTest {
    @Test fun plainNavigationIsNotAStop() {
        val t = ExternalLinks.parse("geo:0,0?q=Googleplex")
        assertNotNull(t)
        assertFalse(t!!.addStop)
        assertEquals("Googleplex", t.query)
        assertFalse(ExternalLinks.parse("geo:0,0?q=coffee+shop&mode=w&intent=navigation")!!.addStop)
    }

    @Test fun addAStopByName() {
        // (the coordinates before the ? are where the car is, not the stop: the stop is what q names)
        val t = ExternalLinks.parse("geo:47.61594547836694,-122.20373173098756?q=575+Bellevue+Square,+Bellevue,+WA+98004&intent=add_a_stop")
        assertNotNull(t)
        assertTrue(t!!.addStop)
        assertNull(t.pos)
        assertEquals("575 Bellevue Square, Bellevue, WA 98004", t.query)
    }

    @Test fun addAStopByCoordinates() {
        val t = ExternalLinks.parse("geo:0,0?q=51.5007,-0.1246(Big+Ben)&intent=add_a_stop")
        assertNotNull(t)
        assertTrue(t!!.addStop)
        assertEquals(51.5007, t.pos!!.lat, 1e-6)
        assertEquals("Big Ben", t.name)
    }
}
