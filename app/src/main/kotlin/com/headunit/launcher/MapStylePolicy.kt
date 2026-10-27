package com.headunit.launcher

/** Pure decision for which Mapbox base style should be requested for the current map settings. */
internal enum class BaseMapStyle {
    STANDARD_SATELLITE,
    DARK_STREETS,
    STREETS,
    STANDARD,
}

internal object MapStylePolicy {
    /** Full detail is the only mode that enables the heavier optional map layers. */
    fun supportsFullDetailFeatures(mapDetail: String): Boolean = mapDetail == "full"

    /** Satellite takes precedence; lightest mode uses the classic streets style, darkened at night. */
    fun choose(satellite: Boolean, mapDetail: String, nightStyle: Boolean): BaseMapStyle = when {
        satellite -> BaseMapStyle.STANDARD_SATELLITE
        mapDetail == "lightest" && nightStyle -> BaseMapStyle.DARK_STREETS
        mapDetail == "lightest" -> BaseMapStyle.STREETS
        else -> BaseMapStyle.STANDARD
    }
}
