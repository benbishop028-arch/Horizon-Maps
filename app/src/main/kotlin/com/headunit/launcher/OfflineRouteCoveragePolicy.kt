package com.headunit.launcher

/** Decides whether an offline route leg can use one pack, must cross two packs, or must stop at coverage. */
internal object OfflineRouteCoveragePolicy {
    enum class Leg { SAME_AREA, DIFFERENT_AREAS, DESTINATION_UNCOVERED, START_UNCOVERED }

    fun leg(startArea: String?, endArea: String?): Leg = when {
        startArea == null -> Leg.START_UNCOVERED
        endArea == null -> Leg.DESTINATION_UNCOVERED
        startArea == endArea -> Leg.SAME_AREA
        else -> Leg.DIFFERENT_AREAS
    }
}
