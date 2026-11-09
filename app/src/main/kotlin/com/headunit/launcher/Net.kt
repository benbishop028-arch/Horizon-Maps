package com.headunit.launcher

/**
 * Offline mode (Settings › Offline maps): when it's on, nothing in the app goes online. Every online request (routes, search,
 * traffic, weather, speed limits, fuel prices, updates…) stops here at once instead of trying and waiting, so the app works only
 * from the downloaded pack. Downloads you start yourself (an offline pack, place names, an update) still go through.
 */
object Net {
    @Volatile var offline: Boolean = false

    /** Throws straight away in offline mode, before any connection is tried. */
    fun check() {
        if (offline) throw java.io.IOException("Offline mode is on")
    }
}
