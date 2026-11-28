package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The size rules checked against real screens, so a fix for one car or phone can't quietly break another (#367, #418, #449, #455,
 * #470). Add a reporter's screen here when one comes in: its pixels and the dpi it reports (in the problem report).
 */
class ScreenFitTest {
    private val S = ScreenFit::Screen

    /** Car screens seen in reports and the common Android Auto sizes. */
    private val cars = listOf(
        S("7-inch 800x480", 800, 480, 160),
        S("7-inch 1024x600", 1024, 600, 160),
        S("1280x720", 1280, 720, 160),
        S("1280x720 sharp", 1280, 720, 240),
        S("wide 1920x720", 1920, 720, 160),
        S("wide 1920x720 sharp", 1920, 720, 240),
        S("1920x1080", 1920, 1080, 240),
        S("1920x1080 low dpi", 1920, 1080, 160),
        S("ultra-wide 1280x480", 1280, 480, 120),
        S("small screen, high dpi", 800, 480, 240),
        S("2560x1440", 2560, 1440, 320),
        S("ultra-wide 2400x900", 2400, 900, 240),
        S("ultra-wide 1440x540", 1440, 540, 160),
        S("Renault upright", 1024, 1200, 200),
        S("tall 9:16", 1080, 1920, 320),
        S("split-screen tile", 640, 720, 160),
    )

    /** The rule before it moved here: unchanged for the common screens. */
    private fun oldUnit(s: ScreenFit.Screen): Float {
        val dens = s.dpi / 160f; val short = minOf(s.wPx, s.hPx).toFloat()
        val narrow = s.wPx < 760 || s.wPx < s.hPx
        return (short / 1050f).coerceIn(dens * (if (narrow) 0.45f else 0.5f), dens * 0.98f) * CarScalePolicy.shapeFactor(s.wPx, s.hPx)
    }

    @Test fun commonCarScreensKeepTheirSize() {
        for (s in cars) {
            if (s.name == "1920x1080 low dpi" || s.name == "small screen, high dpi") continue
            assertEquals(s.name, oldUnit(s), ScreenFit.carUnitPx(s.wPx, s.hPx, s.dpi), 0.001f)
        }
    }

    @Test fun pixelRuleKeepsCardsInBoundsWhateverTheDpi() {
        // a small screen that says it's sharp no longer gets cards made for a big one...
        val small = cars.first { it.name == "small screen, high dpi" }
        assertTrue(ScreenFit.carUnitPx(small.wPx, small.hPx, small.dpi) < oldUnit(small))
        // ...and every screen's short edge is between the limits (before the shape and split-screen adjustments)
        for (s in cars) {
            val units = minOf(s.wPx, s.hPx) / (ScreenFit.carUnitPx(s.wPx, s.hPx, s.dpi) / ScreenFit.shapeFactor(s.wPx, s.hPx))
            assertTrue("${s.name}: $units", units >= ScreenFit.MIN_UNITS - 1f && units <= ScreenFit.MAX_UNITS + 1f)
        }
    }

    @Test fun cardsFitEveryCarScreenAtEveryOverallSize() {
        for (s in cars) for (pct in listOf(40, 70, 100, 130, 150, 200, 250, 300)) {
            val u = ScreenFit.carUnitPx(s.wPx, s.hPx, s.dpi)
            val eff = CarScalePolicy.resolve(pct, s.wPx / u, s.hPx / u).effective
            // (when they'd cover too much, the screen makes room: a size down)
            val k = if (ScreenFit.needsRoom(ScreenFit.carCardsCover(s, eff), false)) ScreenFit.TIGHT_SCALE else 1f
            assertTrue("${s.name} at $pct%", ScreenFit.carCardsFit(s, eff * k))
            assertTrue("${s.name} at $pct%: covers ${ScreenFit.carCardsCover(s, eff * k)}", ScreenFit.carCardsCover(s, eff * k) <= 0.4f)
        }
    }

    @Test fun cardsLeaveMostOfTheMapAtNormalSize() {
        for (s in cars) assertTrue("${s.name}: ${ScreenFit.carCardsCover(s)}", ScreenFit.carCardsCover(s) <= 0.15f)
    }

    @Test fun mapSignsFollowTheCardsWithinABand() {
        for (s in cars) {
            val short = minOf(s.wPx, s.hPx)
            val sign = ScreenFit.carSignPx(ScreenFit.carUnitPx(s.wPx, s.hPx, s.dpi), short) * 56f / short
            assertTrue("${s.name}: $sign", sign >= ScreenFit.SIGN_MIN_SHARE - 0.001f && sign <= ScreenFit.SIGN_MAX_SHARE + 0.001f)
        }
        // on the usual screens the speed limit sign is about 7.5% of the short edge, as it was
        val wide = cars.first { it.name == "wide 1920x720" }
        assertEquals(0.075f, ScreenFit.carSignPx(ScreenFit.carUnitPx(wide.wPx, wide.hPx, wide.dpi), 720) * 56f / 720f, 0.003f)
    }

    @Test fun eachCarScreenGetsTheLayoutForItsRoom() {
        fun room(s: ScreenFit.Screen): ScreenFit.Room {
            val u = ScreenFit.carUnitPx(s.wPx, s.hPx, s.dpi); val w = s.wPx / u; val h = s.hPx / u
            return ScreenFit.carLayout(w, h, w, h).room
        }
        assertEquals(ScreenFit.Room.NARROW, room(cars.first { it.name == "tall 9:16" }))
        assertEquals(ScreenFit.Room.NARROW, room(cars.first { it.name == "Renault upright" }))
        assertEquals(ScreenFit.Room.NARROW, room(cars.first { it.name == "split-screen tile" }))
        assertEquals(ScreenFit.Room.WIDE, room(cars.first { it.name == "wide 1920x720" }))
        // a bigger Overall size keeps the layout picked at 100% (#242)
        val s = cars.first { it.name == "7-inch 800x480" }
        val u = ScreenFit.carUnitPx(s.wPx, s.hPx, s.dpi); val w = s.wPx / u; val h = s.hPx / u
        assertEquals(ScreenFit.carLayout(w, h, w, h).room, ScreenFit.carLayout(w, h, w / 2f, h / 2f).room)
    }

    @Test fun carLayoutFlagsAreAsTheyWere() {
        // a short split-screen strip: the tile layout
        val t = ScreenFit.carLayout(560f, 300f, 560f, 300f)
        assertTrue(t.tile && t.small && t.tiny)
        // short and wide: side by side, not stacked (#67)
        val sw = ScreenFit.carLayout(1000f, 440f, 1000f, 440f)
        assertTrue(sw.short && !sw.portrait && !sw.tile)
        // narrow and tall: stacked
        assertTrue(ScreenFit.carLayout(800f, 1000f, 800f, 1000f).portrait)
    }

    @Test fun phoneRooms() {
        assertEquals(ScreenFit.Room.NARROW, ScreenFit.phoneRoom(390f, 840f))
        assertEquals(ScreenFit.Room.SHORT, ScreenFit.phoneRoom(840f, 390f))
        assertEquals(ScreenFit.Room.NORMAL, ScreenFit.phoneRoom(1024f, 600f))
        assertEquals(ScreenFit.Room.WIDE, ScreenFit.phoneRoom(1280f, 800f))
        assertTrue(ScreenFit.phoneNarrow(700f) && !ScreenFit.phoneNarrow(720f))
    }

    /** Phones and head units running the app itself: their size in dp (Android's own units), as Android reports it. */
    private val phones = listOf(
        Triple("small phone", 360, 740), Triple("usual phone", 390, 844), Triple("big phone", 412, 915),
        Triple("7-inch head unit 1024x600", 1024, 600), Triple("head unit 1280x720", 1280, 720), Triple("wide head unit 1920x720", 1280, 480),
        Triple("tablet", 800, 1280),
    )

    @Test fun phonesAndHeadUnitsGetAReadableSizeAndTheRightLayout() {
        for ((name, w, h) in phones) {
            val pct = ScreenFit.phoneScalePercent(w, h)
            assertTrue("$name: $pct", pct in 85..100)
            // the app's units: dp at the app's size (0.85 at 100%, as App does)
            val k = 0.85f * pct / 100f
            for ((ww, hh) in listOf(w to h, h to w)) {
                val room = ScreenFit.phoneRoom(ww / k, hh / k)
                if (ww < hh && ww < 500) assertEquals("$name upright", ScreenFit.Room.NARROW, room)
            }
        }
        // a usual phone held sideways is the short, side-by-side layout; a 7-inch head unit has room for everything beside the map
        assertEquals(ScreenFit.Room.SHORT, ScreenFit.phoneRoom(844f / 0.85f, 390f / 0.85f))
        assertEquals(ScreenFit.Room.WIDE, ScreenFit.phoneRoom(1024f / 0.85f, 600f / 0.85f))
        assertEquals(100, ScreenFit.phoneScalePercent(390, 844))
        assertEquals(85, ScreenFit.phoneScalePercent(300, 600))
    }

    @Test fun makingRoomOnlyOnLandscapeScreensThatAreReallyCovered() {
        assertTrue(ScreenFit.needsRoom(0.4f, false))
        assertTrue(!ScreenFit.needsRoom(0.2f, false))
        assertTrue(!ScreenFit.needsRoom(0.6f, true))
    }
}
