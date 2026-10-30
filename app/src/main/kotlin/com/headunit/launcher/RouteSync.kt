package com.headunit.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.util.Collections
import java.util.WeakHashMap

/**
 * Keeps the app on the phone's screen and the one on the Android Auto screen on the same trip: starting a route on either starts it
 * on the other, and ending it on either ends both. Only one of them speaks (the car's, while it's connected), so directions and
 * alerts aren't said twice.
 */
object RouteSync {
    private val engines: MutableSet<NavEngine> = Collections.newSetFromMap(WeakHashMap())
    @Volatile private var applying = false
    /** The trip under way (destination and stops), so an engine that starts later joins it. */
    @Volatile var current: Pair<Place, List<Place>>? = null
        private set

    /** True while an Android Auto screen is connected (for the phone's "navigating on your car" screen). */
    var carOn by androidx.compose.runtime.mutableStateOf(false)
        private set

    private fun noteCar() {
        val on = carConnected()
        android.os.Handler(android.os.Looper.getMainLooper()).post { carOn = on }
    }

    fun register(e: NavEngine) {
        synchronized(engines) { engines.add(e) }
        noteCar()
        val cur = current
        if (cur != null && e.route == null) apply { e.followRoute(cur.first, cur.second) }
    }

    fun unregister(e: NavEngine) {
        // Android Auto has disconnected: where the car was is kept as "Your car" on the phone (cleared next time you connect)
        // (only when the car is stopped: switching to another app on the car screen mid-drive also closes it)
        if (e.isCar) e.fix?.takeIf { it.speed < 2.5 }?.pos?.let { p ->
            try { e.prefs.put(PreferenceKeys.PARKED_SPOT, String.format(java.util.Locale.US, "%.6f,%.6f,%d", p.lat, p.lon, System.currentTimeMillis())) } catch (x: Exception) { }
        }
        synchronized(engines) { engines.remove(e) }; noteCar()
    }

    /**
     * A place shared to the phone (from Messages, WhatsApp, a calendar...) while the car is connected: the car screen shows the same
     * route choice, so it can be started from there.
     */
    fun offerToCar(from: NavEngine, p: Place) {
        if (applying || from.isCar) return
        for (e in others(from)) if (e.isCar && (e.route == null || e.previewing)) apply { e.startRoute(p) }
    }

    private fun others(e: NavEngine) = synchronized(engines) { engines.filter { it !== e } }

    private inline fun apply(block: () -> Unit) {
        applying = true
        try { block() } finally { applying = false }
    }

    /** [from] has started guidance to [dest]: the others follow. */
    fun started(from: NavEngine, dest: Place, via: List<Place>) {
        if (applying) return
        current = dest to via
        for (e in others(from)) apply { e.followRoute(dest, via) }
    }

    /** True when some engine still connected is guiding (so the background GPS service must stay on). */
    fun anyGuiding(): Boolean = synchronized(engines) { engines.any { it.route != null && !it.previewing } }

    /** [from] has ended its route: the others end theirs. */
    fun ended(from: NavEngine) {
        // an engine that has already left (the car screen being unplugged) doesn't end the phone's trip
        if (applying || synchronized(engines) { from !in engines }) return
        current = null
        for (e in others(from)) if (e.route != null) apply { e.endRoute() }
    }

    /** True when an Android Auto screen is connected (then the phone stays quiet and the car does the talking). */
    fun carConnected(): Boolean = synchronized(engines) { engines.any { it.isCar } }
}
