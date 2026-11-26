package com.headunit.launcher

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * The app's Brightness slider (Settings › Display) and "Dim the screen at night", as the window brightness to ask Android for.
 *
 * The slider is read the way Android's own brightness slider is: as how bright it looks, on Android's own curve. The window's
 * brightness is not: it's a share of the screen's real light output. Setting it straight from the slider made 10% about a
 * tenth of full power, far brighter than 10% on the phone's own slider, and brighter than auto-brightness picks in a dark
 * car (benkkwok #417).
 */
internal object BrightnessPolicy {
    // Android's curve between how bright the slider looks and the screen's light (BrightnessUtils, Hybrid Log-Gamma)
    private const val R = 0.5f
    private const val A = 0.17883277f
    private const val B = 0.28466892f
    private const val C = 0.55991073f
    /** The lowest it goes: a head unit's backlight can go black below this. */
    const val FLOOR = 0.01f

    /** How bright it looks (0..1) to the screen's light (0..1). */
    fun perceivedToLinear(perceived: Float): Float {
        val v = perceived.coerceIn(0f, 1f)
        val r = if (v <= R) (v / R) * (v / R) else exp((v - C) / A) + B
        return (r / 12f).coerceIn(0f, 1f)
    }

    /** The screen's light (0..1) to how bright it looks (0..1). */
    fun linearToPerceived(linear: Float): Float {
        val r = linear.coerceIn(0f, 1f) * 12f
        return (if (r <= 1f) R * sqrt(r) else A * ln(r - B) + C).coerceIn(0f, 1f)
    }

    /**
     * The window brightness to set, or -1 to leave it to the system.
     * [percent] is the slider (100 = the system's own), [dim] true when dimming at night now, [systemLinear] the system's manual
     * level (0..1), [autoBrightness] true when the phone's auto-brightness is on.
     */
    fun windowBrightness(percent: Int, dim: Boolean, systemLinear: Float, autoBrightness: Boolean): Float {
        if (percent >= 100) {
            // auto-brightness already dims for the dark; its manual level (all that can be read) is often far brighter
            if (!dim || autoBrightness) return -1f
            // half as bright as the system's level looks, never brighter than it
            return perceivedToLinear(linearToPerceived(systemLinear) * 0.5f).coerceIn(FLOOR, systemLinear.coerceAtLeast(FLOOR))
        }
        val looks = percent.coerceIn(1, 100) / 100f * (if (dim) 0.5f else 1f)
        return perceivedToLinear(looks).coerceIn(FLOOR, 1f)
    }
}
