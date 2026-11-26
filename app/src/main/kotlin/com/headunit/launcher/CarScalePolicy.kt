package com.headunit.launcher

import kotlin.math.floor
import kotlin.math.min

/** Resolves Android Auto's requested overall size against the available screen dimensions. */
internal object CarScalePolicy {
    data class Result(
        val requested: Float,
        val effective: Float,
        val maximumPercent: Int
    )

    fun resolve(userPercent: Int, widthDp: Float, heightDp: Float): Result {
        val user = userPercent.coerceIn(40, 300) / 100f
        val rawW = widthDp * user
        val rawH = heightDp * user
        val cap = min(rawW / 560f, rawH / 300f).coerceAtLeast(1f)
        val effective = if (user <= 1f) user else min(user, cap).coerceAtLeast(1f)
        val maximum = (floor(cap.coerceAtMost(3f) * 20.0) * 5).toInt().coerceAtLeast(100)
        return Result(user, effective, maximum)
    }

    /**
     * How much smaller the app's cards are drawn on a car screen of this shape (1 = no change). The automatic size goes by the
     * screen's short edge, which suits a widescreen or a tall 9:16 screen; but a squarer screen (Renault's upright ones, about
     * 5:6) has a short edge nearly as long as its long one, so the cards came out as big as on a widescreen of far more area and
     * filled it (joel941703-hash, #418). Such a screen is now measured like a 16:9 one with the same area, easing back to
     * no change by 4:3, so landscape head units and widescreens are as before.
     */
    fun shapeFactor(widthPx: Int, heightPx: Int): Float {
        val w = widthPx.coerceAtLeast(1).toFloat(); val h = heightPx.coerceAtLeast(1).toFloat()
        val short = min(w, h)
        val sameArea169 = kotlin.math.sqrt(w * h) * 0.75f
        val geo = (sameArea169 / short).coerceAtMost(1f)
        val ratio = w / h
        // (between square and 4:3 landscape it eases back up to 1, so a screen whose shape changes doesn't jump in size)
        val t = ((ratio - 1f) / (4f / 3f - 1f)).coerceIn(0f, 1f)
        return if (ratio <= 1f) geo else geo + (1f - geo) * t
    }
}
