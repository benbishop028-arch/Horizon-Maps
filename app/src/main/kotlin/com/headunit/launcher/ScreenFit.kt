package com.headunit.launcher

import kotlin.math.max
import kotlin.math.min

/**
 * Every rule for how big things are on a screen and which layout it gets, in one place and in plain maths (no Android), so they
 * can be checked against real screens in ScreenFitTest. Before this the car screen's cards worked their size out one way and
 * the signs on its map another, and each layout had its own thresholds scattered through the screens: fixing one car's screen
 * kept breaking another's (#367, #418, #449, #455, #470).
 *
 * The app's cards are laid out in "units". On a phone a unit is Android's own (a dp, at the app's size setting). On the car
 * screen it's worked out below from what the car reports.
 */
internal object ScreenFit {

    // ---- Android Auto: the size of a unit ----

    /**
     * Screen pixels per unit on the car screen at Overall size 100%. Based on the screen size the car reports (its dpi, which
     * Android Auto gets from the car maker for the screen's real size), at half Android's standard size (the app's cards are
     * made for a phone held at arm's length; a car screen is further away but has fewer pixels per inch), then kept within
     * the pixel rule: the screen's short edge is never fewer than [MIN_UNITS] units (so cards can't swamp a small screen that
     * reports a high dpi) nor more than [MAX_UNITS] (so they can't come out tiny on a big screen that reports a low one).
     * A squarer screen (Renault's upright ones) goes smaller again for its shape ([shapeFactor], #418).
     */
    fun carUnitPx(wPx: Int, hPx: Int, dpi: Int): Float {
        val dens = (dpi / 160f).coerceAtLeast(0.5f)
        val short = min(wPx, hPx).coerceAtLeast(1).toFloat()
        // (a narrow part of the screen, Android Auto's split screen, a little smaller so the cards fit beside the map)
        val narrow = wPx < 760 || wPx < hPx
        val byDpi = dens * (if (narrow) 0.45f else 0.5f)
        return byDpi.coerceIn(short / MAX_UNITS, short / MIN_UNITS) * shapeFactor(wPx, hPx)
    }
    /** The car screen's short edge is at least this many units: the cards never take more of a small screen than this allows. */
    const val MIN_UNITS = 800f
    /** ...and at most this many: the cards never come out smaller on a big screen than this allows. */
    const val MAX_UNITS = 1050f

    /** How much smaller the cards are drawn on a car screen of this shape (1 = no change). See [CarScalePolicy.shapeFactor]. */
    fun shapeFactor(wPx: Int, hPx: Int): Float = CarScalePolicy.shapeFactor(wPx, hPx)

    /**
     * Screen pixels per unit for the signs on the car's map (speed limits, cameras, hazards), at Overall size 100%. The same unit
     * as the cards, 1.4 times over (a sign has to be read at a glance from further away than a card), kept so the speed limit sign
     * (56 units wide) is between [SIGN_MIN_SHARE] and [SIGN_MAX_SHARE] of the screen's short edge. They used to be worked out from
     * the pixels alone, apart from the cards, and the two didn't agree.
     */
    fun carSignPx(unitPx: Float, shortPx: Int): Float {
        val short = shortPx.coerceAtLeast(1).toFloat()
        return (unitPx * 1.4f).coerceIn(short * SIGN_MIN_SHARE / 56f, short * SIGN_MAX_SHARE / 56f)
    }
    const val SIGN_MIN_SHARE = 0.07f
    const val SIGN_MAX_SHARE = 0.09f

    // ---- Which layout: picked from the room there is, the same way on the phone and the car ----

    /**
     * How much room a screen has, in units (at Overall size 100%, so a bigger Overall size makes things bigger in the same layout
     * rather than switching to a cramped one, #242).
     *  - TILE: hardly any (Android Auto's split-screen tile, a very short screen): the turn, a one-line trip strip and the speed.
     *  - SHORT: short and wide (a 7-inch 800 x 480 unit, a phone held sideways): side by side, the essentials, one row of shortcuts.
     *  - NARROW: too narrow for cards beside the map: stacked, across the width.
     *  - NORMAL and WIDE: cards beside the map; WIDE has room for everything.
     */
    enum class Room { TILE, SHORT, NARROW, NORMAL, WIDE }

    /** The layout for the car screen, as the car screen's own flags (thresholds as they were, now in one place). */
    class CarLayout(
        val room: Room,
        /** Short, narrow or very wide: smaller gaps and lines. */
        val compact: Boolean,
        /** Really short: no junction close-up or advice. */
        val tiny: Boolean,
        /** Cards across the width, stacked at the bottom. */
        val portrait: Boolean,
        val tile: Boolean,
        val short: Boolean,
    ) { val small: Boolean get() = tile || short }

    /** [layoutW]/[layoutH]: the screen in units at Overall size 100%; [w]/[h]: in the units the cards are drawn in now. */
    fun carLayout(layoutW: Float, layoutH: Float, w: Float, h: Float): CarLayout {
        val aspect = w / h.coerceAtLeast(1f)
        val lowVertical = layoutH < 460f
        val narrow = layoutW < 900f
        val wideLandscape = aspect > 2.0f
        val tiny = layoutH < 330f
        // short, wide screens (800 x 480) are side by side, not stacked: stacked, the cards ran into each other and the speed (#67)
        val shortWide = lowVertical && aspect >= 1.4f
        val portrait = h > w * 1.1f || (narrow && !shortWide)
        val tile = tiny || (layoutW < 600f && lowVertical)
        val short = !tile && lowVertical
        val room = when {
            tile -> Room.TILE
            short -> Room.SHORT
            portrait -> Room.NARROW
            layoutW >= 1400f && layoutH >= 640f -> Room.WIDE
            else -> Room.NORMAL
        }
        return CarLayout(room, lowVertical || narrow || wideLandscape, tiny, portrait, tile, short)
    }

    /** The phone's (and a head unit running the app itself) room, in the app's units (dp at its size setting). */
    fun phoneRoom(w: Float, h: Float): Room = when {
        h < 330f -> Room.TILE
        h < 460f && w > h -> Room.SHORT
        phoneNarrow(w) -> Room.NARROW
        w >= 1200f && h >= 640f -> Room.WIDE
        else -> Room.NORMAL
    }
    /**
     * The app's automatic size on a phone or head unit, in percent: 100 on a screen at least as roomy as a usual phone (390 x 820
     * dp), down to 85 on a smaller one, in steps of 5. Android's own dp already makes things the same physical size on every
     * screen, as Google Maps relies on; this only stops a small screen being crowded.
     */
    fun phoneScalePercent(widthDp: Int, heightDp: Int): Int {
        if (widthDp <= 0 || heightDp <= 0) return 100
        val short = min(widthDp, heightDp).toFloat(); val long = max(widthDp, heightDp).toFloat()
        val fit = min(short / 390f, long / 820f).coerceIn(0.85f, 1f)
        return (Math.round(fit * 100f / 5f) * 5).coerceIn(85, 100)
    }

    /** Too narrow for the turn card and the search beside the map buttons: they take the screen's width (the phone's "narrow"). */
    fun phoneNarrow(w: Float): Boolean = w < 720f

    /** The car screen's layout and whether it's making room, for the problem report. */
    fun carNote(wPx: Int, hPx: Int, dpi: Int, user: Float): String {
        val u = carUnitPx(wPx, hPx, dpi)
        val w = wPx / u; val h = hPx / u
        val l = carLayout(w, h, w / max(user, 0.01f), h / max(user, 0.01f))
        return "%dx%d px at %d dpi: %.0fx%.0f units, %s layout".format(java.util.Locale.US, wPx, hPx, dpi, w, h, l.room.name.lowercase())
    }

    // ---- Making room: when the cards would cover too much of the map ----

    /**
     * The cards on one side cover [coverShare] of the screen: whether to make room (step the cards down a size and leave the
     * optional cards, such as services and car parks, off). Over [TIGHT_AT] of a landscape screen the road ahead and your arrow
     * have too little room beside them.
     */
    fun needsRoom(coverShare: Float, portrait: Boolean): Boolean = !portrait && coverShare > TIGHT_AT
    const val TIGHT_AT = 0.34f
    /** How much smaller the cards are drawn while making room. */
    const val TIGHT_SCALE = 0.9f

    // ---- For checking against real screens (ScreenFitTest) ----

    /** A screen: its size in pixels and the dpi it reports. */
    class Screen(val name: String, val wPx: Int, val hPx: Int, val dpi: Int)

    /**
     * Roughly how much of a car screen the turn card and the trip card under it take at Overall size [user], in units, as the
     * car screen lays them out (turn card width as CarOverlay works it out; heights of a full turn card with one line under it
     * and a trip card with its route bar). For the checks only: the real cards are measured as they're drawn.
     */
    fun carCardsCover(s: Screen, user: Float = 1f): Float {
        val unit = carUnitPx(s.wPx, s.hPx, s.dpi) * user
        val w = s.wPx / unit; val h = s.hPx / unit
        val l = carLayout(w * max(user, 1f), h * max(user, 1f), w, h)
        if (l.portrait) return 0f
        val turnW = if (l.small) min(340f, w * 0.42f) else min(420f, w * 0.36f)
        val turnH = if (l.tile) 70f else if (l.small) 110f else 150f
        val tripH = if (l.tile) 44f else 96f
        return turnW * (turnH + tripH + 40f) / (w * h)
    }

    /** Whether the turn card, trip card and speedometer fit the car screen's height at Overall size [user] (units). */
    fun carCardsFit(s: Screen, user: Float = 1f): Boolean {
        val unit = carUnitPx(s.wPx, s.hPx, s.dpi) * user
        val w = s.wPx / unit; val h = s.hPx / unit
        val l = carLayout(w * max(user, 1f), h * max(user, 1f), w, h)
        val turnH = if (l.tile) 70f else if (l.small) 110f else 150f
        val tripH = if (l.tile) 44f else 96f
        return turnH + tripH + 40f <= h
    }
}
