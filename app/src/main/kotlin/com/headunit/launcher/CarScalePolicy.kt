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
}
