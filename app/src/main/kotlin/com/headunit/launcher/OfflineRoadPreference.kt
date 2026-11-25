package com.headunit.launcher

/** Small, opt-in Valhalla preference. This is deliberately not applied to TomTom or Mapbox routes. */
internal object OfflineRoadPreference {
    /** Valhalla's use_highways is a soft 0..1 preference, not a road exclusion. */
    fun useHighways(enabled: Boolean): Double? = if (enabled) 0.8 else null
}
