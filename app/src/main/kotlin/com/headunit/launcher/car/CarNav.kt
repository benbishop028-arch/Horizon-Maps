package com.headunit.launcher.car

import com.headunit.launcher.ui.drawManeuver
import androidx.compose.ui.graphics.asImageBitmap
import com.headunit.launcher.PreferenceKeys
import com.headunit.launcher.carSpeedSpot
import com.headunit.launcher.mapWeather
import com.headunit.launcher.setCarSpeedSpot
import com.headunit.launcher.L
import com.headunit.launcher.Logos
import com.headunit.launcher.Geo
import android.app.Presentation
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.CarHardwareLocation
import androidx.car.app.hardware.info.CarSensors
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import androidx.car.app.navigation.model.LaneDirection
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Destination
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.headunit.launcher.KEEP_LEFT
import com.headunit.launcher.MapBinding
import com.headunit.launcher.MapboxRest
import com.headunit.launcher.carArrowScale
import com.headunit.launcher.carScreenFit
import com.headunit.launcher.locatorSpec
import com.headunit.launcher.mapDetail
import com.headunit.launcher.NavEngine
import com.headunit.launcher.NavService
import com.headunit.launcher.Place
import com.headunit.launcher.Prefs
import com.headunit.launcher.R
import com.headunit.launcher.TurnInfo
import java.util.TimeZone

/**
 * Horizon Nav on Android Auto. Android Auto connects to this service when the phone is plugged into the car; the car screen shows
 * the map (drawn by the app onto a surface Android Auto hands over) under Google's navigation layout: the turn card, the arrival
 * estimate and the buttons. Search and places use Google's list and search screens (third-party navigation apps can't use their own).
 *
 * The car has its own engine and map, separate from the app on the phone's screen, so it works with the phone locked.
 * (Sideloaded copies only appear in Android Auto with its developer setting "Unknown sources" turned on.)
 */
class CarNavService : CarAppService() {
    // any Android Auto host may connect: the app isn't distributed through Play, where Google would vouch for the host
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = CarNavSession()
    // (Android Auto 6 and newer say which screen the session is for: the driver's display gets just the map)
    override fun onCreateSession(sessionInfo: androidx.car.app.SessionInfo): Session =
        if (sessionInfo.displayType == androidx.car.app.SessionInfo.DISPLAY_TYPE_CLUSTER) ClusterSession() else CarNavSession()
}

/** The engine and map for one connection to the car. */
/** The car screen's map area is short (an 800 x 480 unit, or a split-screen tile): Android Auto's buttons go icon-only (#67). */
object CarLayout {
    @Volatile var short = false
    /** The trip card's red "end the route" button is showing on the car screen (see CarOverlay). */
    @Volatile var endOnCard = false
}

class CarHolder(val carContext: CarContext) {
    var engine: NavEngine? = null
    var map: MapBinding? = null
}

class CarNavSession : Session() {
    private lateinit var holder: CarHolder
    private lateinit var surface: CarSurface
    private var mapScreen: MapScreen? = null
    /** A "navigate to" from Google Assistant (or another app) that came before the map was ready. */
    private var pendingNav: Intent? = null
    @Volatile private var pendingNavWait: Thread? = null
    @Volatile private var pendingNavWaitToken = 0L
    private val main = Handler(Looper.getMainLooper())
    private var carSensors: CarSensors? = null
    private var carLocationListener: OnCarDataAvailableListener<CarHardwareLocation>? = null

    override fun onNewIntent(intent: Intent) { takeNav(intent) }

    /**
     * "Hey Google, navigate to …" (and other apps' navigate links) arrive as a geo: address: exact coordinates go straight to the
     * route choice; a name or address opens the search with it filled in; "home" and "work" go to yours.
     */
    private fun takeNav(i: Intent?) {
        if (i == null || i.action != CarContext.ACTION_NAVIGATE) return
        val e = holder.engine
        if (e == null) { pendingNav = i; return }
        val raw = i.dataString ?: return
        pendingNavWaitToken++
        val token = pendingNavWaitToken
        pendingNavWait?.interrupt()
        val worker = Thread {
            val t = try { com.headunit.launcher.ExternalLinks.parse(raw) } catch (x: Exception) { null }
            // a moment for a first position, so the route starts from where you are
            var n = 0
            while (t?.pos != null && e.fix == null && n < 30 && !Thread.currentThread().isInterrupted) {
                try { Thread.sleep(500) } catch (x: InterruptedException) { Thread.currentThread().interrupt(); return@Thread }
                n++
            }
            if (Thread.currentThread().isInterrupted) return@Thread
            main.post {
                if (pendingNavWaitToken == token) pendingNavWait = null
                val ms = mapScreen ?: return@post
                if (holder.engine !== e) return@post
                try { ms.screenManager.popToRoot() } catch (x: Exception) { }
                val p = t?.pos
                val q = t?.query?.trim().orEmpty()
                val asStop = t?.addStop == true && e.driving()
                // "navigate to …" while a route is being driven, on a car that passes taps on: asked about on the map (Add as a
                // stop / Go here instead) rather than throwing the trip away. The Assistant offers to add it as a stop, and
                // then sends the same plain "navigate" as for a new destination, so the app can't tell which was meant
                val ask = !asStop && e.driving() && e.prefs.b(PreferenceKeys.CAR_TAPS_SEEN, false)
                android.util.Log.i("CarNav", "navigate intent: $raw stop=${t?.addStop} driving=${e.driving()} ask=$ask coords=${p != null}")
                when {
                    // "add a stop at …" while driving (intent=add_a_stop): the next stop, keeping where you're going. It used to
                    // replace the destination, whatever had been asked for
                    asStop && p != null -> e.addStop(Place(t?.name ?: L.t("Stop"), L.t("From Google Assistant"), p), next = true)
                    asStop && q.equals("home", true) && e.places.slot("home") != null -> e.addStop(e.places.slot("home")!!, next = true)
                    asStop && q.equals("work", true) && e.places.slot("work") != null -> e.addStop(e.places.slot("work")!!, next = true)
                    asStop && q.isNotEmpty() -> e.addStopByQuery(q)
                    p != null -> e.goOrAsk(Place(t?.name ?: L.t("Destination"), L.t("From Google Assistant"), p), ask)
                    q.equals("home", true) && e.places.slot("home") != null -> e.goOrAsk(e.places.slot("home")!!, ask)
                    q.equals("work", true) && e.places.slot("work") != null -> e.goOrAsk(e.places.slot("work")!!, ask)
                    q.isNotEmpty() -> ms.screenManager.push(SearchScreen(carContext, holder, q, autoGo = true, mode = if (ask) "ask" else ""))
                    else -> try { androidx.car.app.CarToast.makeText(carContext, L.t("Couldn't read a place from that"), androidx.car.app.CarToast.LENGTH_LONG).show() } catch (x: Exception) { }
                }
            }
        }.apply { isDaemon = true; name = "car-nav-intent" }
        pendingNavWait = worker
        worker.start()
    }

    /** Night colours on the car screen: follow the car, the same as the phone, or always day / always night (quick settings on the car). */
    private fun carDark(): Boolean = when (Prefs(carContext.applicationContext).s(PreferenceKeys.CARTHEME, "car")) {
        "day" -> false
        "night" -> true
        "phone" -> phoneDark()
        // sunrise and sunset where you are: for cars that go dark whenever the headlights are on, even by day (GitHub #140)
        "sun" -> sunDark() ?: carContext.isDarkMode
        else -> carContext.isDarkMode
    }

    private fun sunDark(): Boolean? {
        val p = Prefs(carContext.applicationContext)
        val pos = holder.engine?.fix?.pos
        val lat = pos?.lat ?: p.s(PreferenceKeys.LASTLAT, "").toDoubleOrNull() ?: return null
        val lon = pos?.lon ?: p.s(PreferenceKeys.LASTLON, "").toDoubleOrNull() ?: return null
        return !com.headunit.launcher.Sun.isDay(lat, lon, System.currentTimeMillis())
    }

    /** With Sun times chosen, the car screen is checked once a minute so it changes at sunrise and sunset. */
    private val sunTick = object : Runnable {
        var last: Boolean? = null
        override fun run() {
            try {
                if (Prefs(carContext.applicationContext).s(PreferenceKeys.CARTHEME, "car") == "sun") {
                    val d = carDark(); if (d != last) { last = d; applyTheme() }
                } else last = null
                // whatever the setting: if the map's day / night doesn't match what it should be now, put it right (a run of quick
                // switches, through tunnels and underpasses, could leave it on night for good, GitHub #292)
                if (appliedDark != null && appliedDark != carDark()) applyThemeNow()
            } catch (e: Exception) { }
            sunHandler.postDelayed(this, 20_000L)
        }
    }
    private val sunHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** The phone's own light / dark mode (the system setting, not the car's), GitHub #69. */
    private fun phoneDark(): Boolean = try {
        (android.content.res.Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    } catch (e: Exception) { carContext.isDarkMode }

    /** The phone switching light / dark (by hand or on its schedule) while the car screen is set to match it. */
    private val phoneThemeWatch = object : android.content.ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            try { if (Prefs(carContext.applicationContext).s(PreferenceKeys.CARTHEME, "car") == "phone") android.os.Handler(android.os.Looper.getMainLooper()).post { try { applyTheme() } catch (e: Exception) { } } } catch (e: Exception) { }
        }
        @Deprecated("Deprecated in Java")
        override fun onLowMemory() {}
    }

    /**
     * Day or night from the car: settled for a second before the map changes, then checked again, so a quick run of switches
     * (tunnels and underpasses one after another) ends on what the car says now, rather than on whichever map style finished
     * loading last (GitHub #292).
     */
    private val themeSettle = Runnable { try { applyThemeNow() } catch (e: Exception) { } }
    private fun applyTheme() {
        sunHandler.removeCallbacks(themeSettle)
        if (appliedDark == null) applyThemeNow() else sunHandler.postDelayed(themeSettle, 1200L)
    }
    @Volatile private var appliedDark: Boolean? = null
    private fun applyThemeNow() {
        val d = carDark()
        appliedDark = d
        holder.engine?.applyStyle(d)
        if (::surface.isInitialized) surface.dark.value = d
    }

    private lateinit var voice: CarVoice

    override fun onCreateScreen(intent: Intent): Screen {
        holder = CarHolder(carContext)
        val screen = MapScreen(carContext, holder)
        mapScreen = screen
        surface = CarSurface(carContext) { map -> startEngine(map) }
        surface.actions = object : com.headunit.launcher.ui.CarActions {
            override fun search() { com.headunit.launcher.ui.CarSearch.let { if (!it.open) it.reset(); it.open = true }; com.headunit.launcher.ui.CarStops.open = false; com.headunit.launcher.ui.CarSettingsCard.open = false; screen.invalidate() }
            override fun stops() { com.headunit.launcher.ui.CarSearch.open = false; com.headunit.launcher.ui.CarSettingsCard.open = false; com.headunit.launcher.ui.CarStops.show(); screen.invalidate() }
            override fun addStopSearch() { com.headunit.launcher.ui.CarStops.open = false; com.headunit.launcher.ui.CarSearch.let { it.reset(); it.forStop = true; it.open = true }; screen.invalidate() }
            override fun voiceSearch() { com.headunit.launcher.ui.CarSearch.let { if (!it.open) it.reset(); it.open = true }; screen.invalidate(); voice.toggle() }
            // (the card's search bar: Android Auto's search screen, with the car's keyboard; it starts with what was said, if anything)
            override fun typeSearch() { voice.finish(""); screen.screenManager.push(SearchScreen(carContext, holder, com.headunit.launcher.ui.CarSearch.query.trim(), mode = if (com.headunit.launcher.ui.CarSearch.forStop) "stop" else "ask")) }
            override fun closeSearch() { screen.closeSearch() }
            override fun places() { screen.screenManager.push(PlacesScreen(carContext, holder)) }
            override fun nearby(kind: String) { screen.screenManager.push(NearbyScreen(carContext, holder, kind)) }
            override fun settings() { screen.openSettings() }
            override fun closeSettings() { screen.closeSearch() }
            override fun themeChanged() { applyThemeNow() }
            override fun otherRoutes() { screen.screenManager.push(AltRoutesScreen(carContext, holder)) }
        }
        // the settings card over the map on cars that pass taps on to the app; Android Auto's own list on the others (a rotary
        // knob can only reach Android Auto's screens)
        screen.openSettings = {
            if (Prefs(carContext.applicationContext).b(PreferenceKeys.CAR_TAPS_SEEN, false)) {
                com.headunit.launcher.ui.CarSearch.open = false; com.headunit.launcher.ui.CarSettingsCard.open = true; screen.invalidate()
            } else screen.screenManager.push(CarSettingsScreen(carContext, holder) { applyTheme() })
        }
        // what was said is searched for in the card, as if it had been typed
        voice = CarVoice(carContext, onNoMic = { screen.screenManager.push(SearchScreen(carContext, holder, keyboard = false, mode = if (com.headunit.launcher.ui.CarSearch.forStop) "stop" else "ask")) }) { text -> com.headunit.launcher.ui.CarSearch.query = text }
        screen.closeSearch = { CarCardLog.note(carContext.applicationContext); voice.finish(""); holder.engine?.pendingSave = null; com.headunit.launcher.ui.CarSearch.open = false; com.headunit.launcher.ui.CarSettingsCard.open = false; com.headunit.launcher.ui.CarStops.open = false; screen.invalidate() }
        // (Android Auto works the visible area out again when it lays the screen out)
        surface.onAreaDoubt = { try { screen.invalidate() } catch (e: Exception) { } }
        try { carContext.getCarService(AppManager::class.java).setSurfaceCallback(surface) } catch (e: Exception) { }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                screen.stop()
                sunHandler.removeCallbacks(sunTick)
                try { voice.finish(""); com.headunit.launcher.ui.CarSearch.open = false; com.headunit.launcher.ui.CarSettingsCard.open = false; com.headunit.launcher.ui.CarStops.open = false } catch (e: Exception) { }
                try {
                    val listener = carLocationListener
                    val sensors = carSensors
                    if (listener != null && sensors != null) sensors.removeCarHardwareLocationListener(listener)
                } catch (e: Exception) { }
                carLocationListener = null; carSensors = null
                try { carContext.applicationContext.unregisterComponentCallbacks(phoneThemeWatch) } catch (e: Exception) { }
                pendingNavWaitToken++
                pendingNavWait?.interrupt()
                pendingNavWait = null
                // unplugged: the car's engine stops, but a trip the phone is also following carries on there (with its voice back)
                if (ClusterLink.engine === holder.engine) ClusterLink.engine = null
                // (switched off close to the destination: you've arrived, so the trip ends here and on the phone too)
                try { holder.engine?.let { if (it.closeToEnd()) it.endRoute() } } catch (e: Exception) { }
                try { holder.engine?.shutdown() } catch (e: Exception) { }
                try { if (!com.headunit.launcher.RouteSync.anyGuiding()) NavService.stop(carContext.applicationContext) } catch (e: Exception) { }
                CarTicks.tick = null
                surface.release()
                holder.engine = null; holder.map = null
            }
        })
        try { carContext.applicationContext.registerComponentCallbacks(phoneThemeWatch) } catch (e: Exception) { }
        sunHandler.postDelayed(sunTick, 60_000L)
        pendingNav = intent
        return screen
    }

    /** The map is on the car screen: start an engine for it, with the same settings as the app on the phone. */
    private fun startEngine(map: MapBinding) {
        val app = carContext.applicationContext
        val prefs = Prefs(app)
        map.setTheme("immersive")
        map.setArrowScale((prefs.s(PreferenceKeys.ARROW_PERCENT, "240").toIntOrNull() ?: 240) / 100f)
        // the same map look and locator as on the phone, your own picture included (it's read from the same app's files; with
        // none saved the map falls back to the 3D arrow by itself)
        val (loc, locCol) = prefs.locatorSpec()
        map.setLocator(loc, locCol)
        // Keep the locator proportionate to the more compact Android Auto overlay sizing.
        // Android Auto uses a smaller map surface than the main head-unit app. Keep the
        // user preference, but normalise it so the 240% main-app default doesn't create an enormous car-screen locator.
        map.setArrowScale(prefs.carArrowScale())
        map.setMapDetail(prefs.mapDetail())
        map.setTrafficGreen(prefs.b(PreferenceKeys.TRAFFIC_GREEN, false))
        map.setBlendTraffic(prefs.b(PreferenceKeys.BLEND_TRAFFIC, true))
        map.setCarModel(prefs.s(PreferenceKeys.CAR_MODEL, "saloon"))
        map.setRoadNameScale(prefs.i(PreferenceKeys.CAR_ROAD_NAME_SIZE, 100).coerceIn(70, 200) / 100f)
        val mapCols = prefs.s(PreferenceKeys.MAP_COLOURS, "mapbox")
        map.setColours(if (mapCols == "custom") prefs.s(PreferenceKeys.CUSTOM_BASE, "mapbox") else mapCols)
        map.setFont(prefs.s(PreferenceKeys.MAP_FONT, ""))
        map.setCustom(mapCols == "custom", prefs.s(PreferenceKeys.CUSTOM_DAY, ""), prefs.s(PreferenceKeys.CUSTOM_NIGHT, ""), prefs.i(PreferenceKeys.CUSTOM_ROUTE_W, 100))
        map.setCameraSize(prefs.i(PreferenceKeys.CAM_SIZE, 100))
        map.setFlags(prefs.b(PreferenceKeys.FLAGS_3D, true))
        map.setHillShading(prefs.b(PreferenceKeys.HILLSHADE, false))
        map.setCarTint(prefs.s(PreferenceKeys.CAR_TINT, "#8A5AD0"), prefs.i(PreferenceKeys.CAR_TINT_STR, 40))
        map.setLightOpts(com.headunit.launcher.lightOptsFrom(prefs))
        // (3D buildings and trees as set on the phone: the car's map used to keep them on whatever the setting, GitHub)
        map.setView(prefs.b(PreferenceKeys.SATELLITE, false) && !com.headunit.launcher.Plus.free, prefs.b(PreferenceKeys.BUILDINGS_3D, true))
        map.treesEnabled(prefs.b(PreferenceKeys.TREES, true))
        map.lampsEnabled(prefs.b(PreferenceKeys.LAMPS, true))
        map.applyLampGap(prefs.s(PreferenceKeys.LAMPGAP, "40").toDoubleOrNull() ?: 40.0)
        map.setLogoCorner(prefs.s(PreferenceKeys.MAPBOX_LOGO, "tr"))
        map.setExtras(prefs.b(PreferenceKeys.FACADES, true), prefs.b(PreferenceKeys.LANDMARKS3D, true), prefs.b(PreferenceKeys.PATHS, true), prefs.b(PreferenceKeys.TERRAIN, false),
            false, prefs.b(PreferenceKeys.FURNITURE3D, true), prefs.b(PreferenceKeys.CLEARVIEW, true))
        map.setHdRoads(com.headunit.launcher.hdRoadsWanted(prefs, prefs.s(PreferenceKeys.COUNTRYDETECTED, "")))
        val engine = NavEngine(app, prefs, map)
        // the car screen does the talking while it's connected, and shares the trip with the phone
        engine.isCar = true
        ClusterLink.engine = engine
        // (the app's own short messages don't show on the car screen: a tapped number-plate camera says what it is with Android Auto's)
        map.onAlprTap = { who -> try { androidx.car.app.CarToast.makeText(carContext, L.t("Number-plate camera") + (if (who.isNotBlank()) " · $who" else ""), androidx.car.app.CarToast.LENGTH_SHORT).show() } catch (e: Exception) { } }
        // one of your own reports tapped: what it is, with Remove / Keep
        map.onReportTap = { p ->
            try { engine.reportAt(p)?.let { r -> mapScreen?.screenManager?.push(RemoveReportScreen(carContext, engine, r)) } } catch (e: Exception) { }
        }
        // back in the car: the "Your car" spot on the phone isn't needed any more
        try { prefs.put(PreferenceKeys.PARKED_SPOT, "") } catch (e: Exception) { }
        holder.map = map; holder.engine = engine
        surface.engine = engine
        // the weather's light, and (Reddit) the rain and snow falling and the haze to the horizon, which were only ever put on the
        // phone's map: each call does nothing unless the weather has changed
        CarTicks.tick = {
            surface.tick()
            try {
                val code = holder.engine?.weather?.code
                val p = Prefs(carContext.applicationContext)
                holder.map?.setWeatherCode(code)
                val shown = p.mapWeather(code)
                holder.map?.setWeather(if (p.b(PreferenceKeys.RAIN_EFFECTS, false)) shown else null)
                holder.map?.setSky(if (p.b(PreferenceKeys.SKYWEATHER, true)) shown else null)
            } catch (e: Exception) { }
        }
        applyTheme()
        try { engine.startLocation() } catch (e: Exception) { }
        // Prefer the head unit's GPS when its Android Auto host supports Car App API 3.
        // NavEngine keeps the phone LocationManager feed as an automatic fallback.
        try {
            if (carContext.carAppApiLevel >= 3 &&
                androidx.core.content.ContextCompat.checkSelfPermission(carContext, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val sensors = carContext.getCarService(CarHardwareManager::class.java).carSensors
                val listener = OnCarDataAvailableListener<CarHardwareLocation> { data ->
                    val value = data.location
                    if (value.status == CarValue.STATUS_SUCCESS) value.value?.let { holder.engine?.onCarLocation(it) }
                }
                sensors.addCarHardwareLocationListener(CarSensors.UPDATE_RATE_NORMAL, androidx.core.content.ContextCompat.getMainExecutor(carContext), listener)
                carSensors = sensors
                carLocationListener = listener
            }
        } catch (e: Exception) { android.util.Log.w("CarNav", "Car GPS unavailable; using phone location", e) }
        // the location service runs the whole time the map is on Android Auto, not only during a route: without it, driving with
        // no route and the phone locked, Android could hold back location updates, and the arrow froze or stayed where the drive
        // began, such as at home (timblr1989, #214)
        try { NavService.start(carContext.applicationContext, L.t("Android Auto")) } catch (e: Exception) { }
        mapScreen?.start()
        // asked for a destination before the map was up (Android Auto opened the app for it)
        pendingNav?.let { pendingNav = null; takeNav(it) }
    }

    override fun onCarConfigurationChanged(newConfiguration: Configuration) {
        // day / night on the car screen follows the car (its headlights or its own setting), unless set otherwise on the car
        applyTheme()
    }
}

/**
 * Puts the app's map on the car screen: Android Auto gives a surface; a private virtual display shows on it, and the map (a normal
 * map view with the locator and road name on top) runs on that display. Drags and pinches on the car screen move it.
 */
class CarSurface(private val carContext: CarContext, private val onReady: (MapBinding) -> Unit) : SurfaceCallback {
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var map: MapBinding? = null
    private var overlay: androidx.compose.ui.platform.ComposeView? = null
    private val owner = CarComposeOwner()
    /** The part of the car screen Android Auto leaves free (for the cards), and whether the car wants night colours. */
    val area = androidx.compose.runtime.mutableStateOf<Rect?>(null)
    /** The visible area exactly as Android Auto last reported it (for the feedback report). */
    @Volatile var lastVisible: Rect? = null
    /** Asks Android Auto to lay its screen out again, which makes it report the visible area afresh (set by the session). */
    var onAreaDoubt: () -> Unit = {}
    val dark = androidx.compose.runtime.mutableStateOf(true)
    var engine: NavEngine? = null
        set(v) {
            field = v
            if (v != null) {
                v.setCarViewport(width, height, currentDpi)
                overlay?.setContent { com.headunit.launcher.ui.CarOverlay(v, area, dark, uiScale, actions) }
            }
        }
    /** What the app's own search bar and "Saved & recent" chip open (set by the map screen). */
    var actions: com.headunit.launcher.ui.CarActions = object : com.headunit.launcher.ui.CarActions { override fun search() {}; override fun places() {}; override fun nearby(kind: String) {}; override fun settings() {} }
    /** How much larger the app's cards are drawn on the car screen than its own pixel density would make them (same as the map). */
    private val uiScale = androidx.compose.runtime.mutableStateOf(1.7f)
    private var displayDensity = 1f
    private var currentDpi = 160
    private var width = 1; private var height = 1

    /**
     * Establish a sensible baseline for the app-drawn overlay on different Android Auto displays.
     * The old 720px target and 2.4x upper bound made overlays disproportionately large on high-resolution
     * or tall displays. Use a more conservative short-edge target, with tighter bounds; CarOverlay then
     * uses the available width/height to choose an appropriate arrangement. The user's carscale setting
     * remains a separate multiplier in CarOverlay.
     */
    private fun fitScale() {
        val dens = displayDensity.coerceAtLeast(0.5f)
        val short = minOf(width, height).toFloat()
        // Keep Android Auto overlays close to standard navigation UI sizing; large screens should
        // not inflate buttons/cards. Only allow a small adjustment for unusually compact surfaces.
        // a narrow part of the screen (Android Auto's split screen) can go smaller still, so the cards fit beside the map
        val narrow = width < 760 || width < height
        // a small, low-resolution screen (7" 1024x600, 800x480) gets smaller cards, text and arrow so the map stays usable (#367)
        val perUnit = (short / 1050f).coerceIn(dens * (if (narrow) 0.45f else 0.5f), dens * 0.98f)
        uiScale.value = perUnit / dens
        val fit = (short / 900f).coerceIn(0.55f, 1f)
        if (fit != carScreenFit) {
            carScreenFit = fit
            try { map?.setArrowScale(Prefs(carContext.applicationContext).carArrowScale()) } catch (_: Exception) { }
        }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val s = surfaceContainer.surface ?: return
        width = surfaceContainer.width.coerceAtLeast(1); height = surfaceContainer.height.coerceAtLeast(1)
        currentDpi = surfaceContainer.dpi.coerceAtLeast(80)
        engine?.setCarViewport(width, height, currentDpi)
        val existing = display
        if (existing != null) {
            // The car screen came back or changed size: keep the same map, but refresh its dimensions and scale.
            existing.surface = s
            existing.resize(width, height, surfaceContainer.dpi)
            displayDensity = presentation?.context?.resources?.displayMetrics?.density
                ?: (surfaceContainer.dpi / 160f).coerceAtLeast(0.5f)
            fitScale()
            map?.carResume()
            // the area reported for the old size may not hold for the new one: checked against it again, and asked for afresh
            lastVisible?.let { applyArea(it) }
            onAreaDoubt()
            return
        }
        try {
            val dm = carContext.getSystemService(DisplayManager::class.java)
            val vd = dm.createVirtualDisplay("HorizonNavCar", width, height, surfaceContainer.dpi, s, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
            display = vd
            // Android closes a presentation by itself when its screen changes size (Android Auto's split screen, going between the
            // big and the small part): the map then wasn't redrawn for the new size. It's kept open instead, and simply laid out again
            val p = object : Presentation(carContext, vd.display) {
                override fun cancel() { if (closing) super.cancel() }
            }
            // car screens have few pixels per inch for their size: drawn at the phone's own scale the map looked far too zoomed out
            // (and had to draw a huge area, which made it laggy), so it's drawn larger, like the app on a head unit
            val dens = p.context.resources.displayMetrics.density
            // (1.3x, down from 1.7x: with the zoom out that went with it, the car map sat so far out that Mapbox's 3D trees and other
            // close-up detail never appeared. Now it's drawn at close to the phone's zoom, with text a little smaller than before)
            val ratio = (dens * 1.3f).coerceIn(1.25f, 2.5f)
            displayDensity = dens
            fitScale()
            // the Mapbox token has to be in place before the map is made. The phone app sets it when it opens, but when Android Auto
            // starts the app by itself (after an update, or a restart, before the app has been opened on the phone) it wasn't set yet,
            // so the map couldn't be made and the car screen stayed black
            try {
                val tok = MapboxRest.clean(Prefs(p.context.applicationContext).s(PreferenceKeys.MAPBOX_TOKEN, ""))
                if (tok.isNotEmpty() && com.mapbox.common.MapboxOptions.accessToken != tok) com.mapbox.common.MapboxOptions.accessToken = tok
            } catch (e: Throwable) { }
            // drawn straight to its own surface (no extra copy each frame, which a texture view would cost)
            val m = MapBinding(p.context, true, 1f, ratio)
            m.onCar = true; m.placeBranding(null)
            // street lamps as set on the phone (Settings › Street lamps), like the trees and 3D details: they were kept off on the
            // car before, which left Android Auto without them (ChrisSantosGarage, #211). Set in startEngine with the rest.
            // a touch thicker route line on the car screen, where it looked thin against the buildings
            m.routeBoost = 1.2f
            val root = FrameLayout(p.context)
            root.addView(m.mapView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            (m.beamView.parent as? android.view.ViewGroup)?.removeView(m.beamView)
            root.addView(m.beamView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            root.addView(m.roadView, FrameLayout.LayoutParams(1, 1))
            root.addView(m.locatorView, FrameLayout.LayoutParams(1, 1))
            // the app's own cards (turn card, speed, trip figures) drawn on top with the app's own designs
            owner.start()
            root.setViewTreeLifecycleOwner(owner)
            root.setViewTreeSavedStateRegistryOwner(owner)
            root.setViewTreeViewModelStoreOwner(owner)
            overlay = androidx.compose.ui.platform.ComposeView(p.context)
            root.addView(overlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            p.setContentView(root)
            p.show()
            try { m.mapView.onStart() } catch (e: Throwable) { }
            presentation = p; map = m
            area.value?.let { onVisibleAreaChanged(it) }
            onReady(m)
        } catch (e: Exception) { android.util.Log.w("CarNav", "couldn't put the map on the car screen", e) }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        // nothing to draw on until it comes back: the map rests instead of drawing to nowhere
        map?.carPause()
        display?.surface = null
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        lastVisible = Rect(visibleArea)
        // (in the log, for working out layouts on a head unit or the Desktop Head Unit)
        android.util.Log.i("CarNav", "visible area ${visibleArea.toShortString()} of ${width}x$height at $currentDpi dpi")
        applyArea(visibleArea)
    }

    /** Lays the cards and the map out for the part of the screen Android Auto leaves free. */
    private fun applyArea(visibleArea: Rect) {
        CarLayout.short = visibleArea.height() in 1..519 || visibleArea.width() in 1..699
        try { com.headunit.launcher.CarDiag.screen = "${width}x$height, visible ${visibleArea.left},${visibleArea.top},${visibleArea.right},${visibleArea.bottom}" } catch (e: Exception) { }
        // some head units report a visible area that can't be right (a sliver of the screen, or one off the edge): going by it put
        // the arrow under the cards on the left. Then the whole screen is used, as when nothing is reported
        // (a narrow part is real, though: Android Auto's split screen gives the app a narrow strip of a wide screen. Turning that
        // down kept the map centred on the whole screen, with only its right-hand part showing, and the route and cards out of view)
        val plausible = width <= 1 || height <= 1 || (visibleArea.width() >= minOf(width * 0.2, 240.0) && visibleArea.height() >= height * 0.3 &&
            visibleArea.left >= 0 && visibleArea.top >= 0 && visibleArea.right <= width + 2 && visibleArea.bottom <= height + 2)
        if (!plausible) {
            area.value = null
            map?.let { it.viewInset = null; it.maxTopFrac = 1.0 }
            return
        }
        area.value = Rect(visibleArea)
        // the map's locator goes in the part Android Auto leaves visible (on tall screens its cards cover the bottom of the map),
        // and sits a little higher on tall screens so the speed and trip cards at the bottom don't cover it
        val m = map ?: return
        if (visibleArea.width() > 0 && visibleArea.height() > 0) {
            m.viewInset = Rect(visibleArea.left.coerceAtLeast(0), visibleArea.top.coerceAtLeast(0),
                (width - visibleArea.right).coerceAtLeast(0), (height - visibleArea.bottom).coerceAtLeast(0))
            // Mapbox's logo and credits stay inside the part of the screen you can see
            m.placeBranding(m.viewInset)
            // (the cards along the bottom of a tall screen are kept clear by the engine, which the overlay tells where they are)
            m.maxTopFrac = 1.0
        }
    }

    /**
     * The stable area is the visible area with everything Android Auto may put over the map counted as always there, so it lies
     * inside the visible area. When a new one doesn't (leaving split screen: the stable area is the whole screen again while the
     * last visible area is still the tile), the visible area is out of date: the cards and the arrow stayed in the tile's half
     * of the screen until something else made Android Auto report it again. The stable area is used until it does, and Android
     * Auto is asked to lay its screen out again, which makes it report.
     */
    override fun onStableAreaChanged(stableArea: Rect) {
        android.util.Log.i("CarNav", "stable area ${stableArea.toShortString()} of ${width}x$height, visible ${lastVisible?.toShortString()}")
        if (stableArea.isEmpty) return
        val v = lastVisible
        val inside = v != null && Rect(v).apply { inset(-8, -8) }.contains(stableArea)
        if (!inside) {
            applyArea(stableArea)
            onAreaDoubt()
        }
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        com.headunit.launcher.ui.CarButtons.poke()
        // the app's search card is open: a drag scrolls its list, and the map under it stays put
        if (com.headunit.launcher.ui.CarSearch.scrollBy(distanceY) || com.headunit.launcher.ui.CarSettingsCard.scrollBy(distanceY) || com.headunit.launcher.ui.CarStops.scrollBy(distanceY)) return
        val m = map ?: return
        m.onUserGesture?.invoke()
        try { m.panBy(distanceX, distanceY) } catch (e: Exception) { }
    }

    override fun onFling(velocityX: Float, velocityY: Float) {}

    // a tap on the car screen (newer cars): the app's own cards and buttons react, as in the app
    override fun onClick(x: Float, y: Float) {
        com.headunit.launcher.ui.CarButtons.poke()
        // this car passes taps on: remembered, so the app's own buttons can stand in for Android Auto's
        if (!tapNoted) { tapNoted = true; try { Prefs(carContext.applicationContext).let { if (!it.b(PreferenceKeys.CAR_TAPS_SEEN, false)) it.put(PreferenceKeys.CAR_TAPS_SEEN, true) } } catch (e: Exception) { } }
        try {
            // the app's cards and buttons first, then the map itself (alternative route cards and lines, skipping the fly-through)
            if (!com.headunit.launcher.ui.CarTaps.hit(x, y)) map?.tapAt(android.graphics.PointF(x, y))
        } catch (e: Exception) { }
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        com.headunit.launcher.ui.CarButtons.poke()
        val m = map ?: return
        m.onUserGesture?.invoke()
        // a double tap comes as a negative factor: zoom in one step
        val dz = if (scaleFactor <= 0f) 1.0 else Math.log(scaleFactor.toDouble()) / Math.log(2.0)
        // zoomed about the middle of the map: around the fingers, on a tilted 3D map, the view jumped about between steps
        try { m.zoomAround(dz, Float.NaN, Float.NaN) } catch (e: Exception) { }
    }

    /** Called about once a second (the cards update themselves). */
    fun tick() {}

    private var tapNoted = false

    /** Only true while the car screen is really being closed (see the presentation above). */
    @Volatile private var closing = false

    fun release() {
        closing = true
        try { owner.stop() } catch (e: Exception) { }
        try { map?.destroy() } catch (e: Exception) { }
        try { presentation?.dismiss() } catch (e: Exception) { }
        try { display?.release() } catch (e: Exception) { }
        map = null; presentation = null; display = null
    }
}

/** Lifecycle and saved state for the cards drawn on the car screen (Compose needs them; there's no activity on the car). */
class CarComposeOwner : androidx.lifecycle.LifecycleOwner, androidx.savedstate.SavedStateRegistryOwner, androidx.lifecycle.ViewModelStoreOwner {
    private val reg = androidx.lifecycle.LifecycleRegistry(this)
    private val saved = androidx.savedstate.SavedStateRegistryController.create(this)
    private val store = androidx.lifecycle.ViewModelStore()
    override val lifecycle: androidx.lifecycle.Lifecycle get() = reg
    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore: androidx.lifecycle.ViewModelStore get() = store
    private var on = false
    fun start() {
        if (on) return
        on = true
        saved.performRestore(null)
        reg.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
    }
    fun stop() {
        if (!on) return
        reg.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
        store.clear()
    }
}

/** The main car screen: the map with the turn card and arrival estimate while navigating, and buttons for search and places. */
class MapScreen(carContext: CarContext, private val holder: CarHolder) : Screen(carContext) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastSig = ""
    private var lastTrip = ""
    private var navigating = false
    private var autoDrive = false
    /** Opens the car's quick settings (set by the session). */
    var openSettings: () -> Unit = {}
    /** Closes the app's search or settings card (set by the session). */
    var closeSearch: () -> Unit = {}
    private val nav: NavigationManager? = try { carContext.getCarService(NavigationManager::class.java) } catch (e: Exception) { null }

    init {
        try {
            nav?.setNavigationManagerCallback(object : NavigationManagerCallback {
                // the car (or another navigation app) asked to stop
                override fun onStopNavigation() { holder.engine?.endRoute() }
                // Google's test tool: simulate driving to the chosen destination
                override fun onAutoDriveEnabled() { autoDrive = true }
            })
        } catch (e: Exception) { }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    fun start() { if (!running) { running = true; handler.post(tick) } }
    fun stop() {
        running = false; handler.removeCallbacks(tick)
        if (navigating) try { nav?.navigationEnded() } catch (e: Exception) { }
        navigating = false
    }

    private fun refresh() {
        val e = holder.engine ?: return
        val guiding = e.route != null && !e.previewing
        // tell Android Auto when navigation starts and ends (it shows turns in the car's dashboard and notifications)
        if (guiding != navigating) {
            navigating = guiding
            lastTrip = ""
            try {
                if (guiding) {
                    nav?.navigationStarted()
                    try { NavService.start(holder.carContext.applicationContext, L.t("Navigating to {0}", e.dest?.name ?: L.t("your destination"))) } catch (x: Exception) { }
                } else {
                    nav?.navigationEnded()
                    // (the location service stays on while the map is on Android Auto, back to its plain wording)
                    try { NavService.start(holder.carContext.applicationContext, L.t("Android Auto")) } catch (x: Exception) { }
                }
            } catch (x: Exception) { }
        }
        if (autoDrive && guiding && e.simMode == 0) e.cycleSim()
        // a route was picked (from the card, a search or by voice) and its choices are up: the search card has done its job
        // (while navigating it stays until a place is picked: it's how a stop is searched for)
        if (e.previewing && (com.headunit.launcher.ui.CarSearch.open || com.headunit.launcher.ui.CarStops.open)) closeSearch()
        if (e.route == null && com.headunit.launcher.ui.CarStops.open) closeSearch()
        val t = e.turn; val eta = e.eta
        // Android Auto's buttons only change with the state (rebuilding them every second while the distance counts down made the
        // host redo its layout for nothing); the turn and distance go to the car's displays through updateTrip
        val sig = "${guiding}|${e.previewing}|${e.active}|${e.routes.size}|${e.busy}|${e.simMode}|${e.faster?.savedMin}|${e.soundMode}|${e.demoNext}|${stopOnCard(e)}|${com.headunit.launcher.ui.CarSearch.open}|${com.headunit.launcher.ui.CarSettingsCard.open}|${com.headunit.launcher.ui.CarStops.open}|${e.resume != null}"
        if (sig != lastSig) { lastSig = sig; invalidate() }
        val tsig = "${t?.maneuver}|${t?.street}|${t?.landmark}|${t?.exit}|${t?.dist?.let { Math.round(it / 10) }}|${eta?.secs?.let { Math.round(it / 30) }}|${eta?.dist?.let { Math.round(it / 100) }}|${System.identityHashCode(t?.lanes)}"
        if (guiding && t != null && eta != null && tsig != lastTrip) {
            lastTrip = tsig
            try { nav?.updateTrip(trip(e, t, eta)) } catch (x: Exception) { }
            try { NavService.update(carContext.applicationContext, t, eta, e.imperial, !e.mirrored, e.roundaboutClockwise, e.prefs.b(PreferenceKeys.TURN_NOTIF, true)) } catch (x: Exception) { }
        }
        CarTicks.tick?.invoke()
    }

    private fun icon(res: Int) = CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build()

    /**
     * The trip card's red button already ends the route, so Android Auto's Stop beside it is left out: only when that button is
     * showing and this car is known to pass taps on to the app (otherwise Stop is the only way to end the route from the car;
     * "End route" is also the first row of the car's settings, for a rotary knob).
     */
    // (a car with a rotary knob, Search bar and places on the map switched off for it: Stop, sound and Report stay in Android Auto's
    // own row, the only buttons a knob can reach. A tap on the screen while parked, which some such cars allow, used to move them
    // onto the trip card, out of reach while driving, corocota1975 #333)
    private fun stopOnCard(e: NavEngine?): Boolean = e != null && CarLayout.endOnCard && e.prefs.b(PreferenceKeys.CAR_TAPS_SEEN, false) &&
        e.prefs.b(PreferenceKeys.CAR_MAPUI, true)

    /** Report (police, crash, hazard...): opens the "Report here" screen. It has the Search button's place. */
    private fun reportAction(): Action = (if (CarLayout.short) Action.Builder().setIcon(icon(R.drawable.ic_car_report)) else Action.Builder().setTitle(L.t("Report")))
        .setOnClickListener { screenManager.push(ReportScreen(carContext, holder)) }.build()

    override fun onGetTemplate(): Template {
        // (Android Auto brings its buttons back up when they're laid out again)
        com.headunit.launcher.ui.CarButtons.poke()
        val e = holder.engine
        val guiding = e != null && e.route != null && !e.previewing
        val strip = ActionStrip.Builder()
        val previewing = e != null && e.route != null && e.previewing
        if (previewing) {
            // the route choice (drawn by the app on the map): start, try the next route, or cancel
            strip.addAction(Action.Builder().setTitle(L.t(if (e?.demoNext == true) "Start demo drive" else "Start")).setOnClickListener { e?.beginNavigation(); invalidate() }.build())
            // Demo (with the demo drive switched on): Start, or the countdown, then begins the trip as a demo drive
            if (e != null && e.prefs.b(PreferenceKeys.DEMO, false)) strip.addAction(Action.Builder().setTitle(if (e.demoNext) "\u2713 " + L.t("Demo") else L.t("Demo"))
                .setOnClickListener { e.toggleDemoNext(); invalidate() }.build())
            if ((e?.routes?.size ?: 0) > 1) strip.addAction(Action.Builder().setTitle(L.t("Next route")).setOnClickListener {
                e?.let { en -> en.selectRoute((en.active + 1) % en.routes.size.coerceAtMost(3)) }; invalidate()
            }.build())
            strip.addAction(Action.Builder().setTitle(L.t("Cancel")).setOnClickListener { e?.endRoute(); invalidate() }.build())
            return NavigationTemplate.Builder().setActionStrip(strip.build()).build()
        }
        // voice guidance: all / alerts only / off (the same as the app's sound button)
        val soundIcon = when (e?.soundMode ?: 0) { 0 -> R.drawable.ic_car_sound_on; 1 -> R.drawable.ic_car_sound_alerts; else -> R.drawable.ic_car_sound_off }
        val sound = Action.Builder().setIcon(icon(soundIcon)).setOnClickListener { e?.cycleSound(); invalidate() }.build()
        // (2D / 3D used to be a button here too: it's in the settings, under Map, and doesn't need to be on the screen all the time)
        val demoOn = e != null && (e.prefs.b(PreferenceKeys.DEMO, false) || e.simMode != 0)
        val fr = e?.faster
        if (com.headunit.launcher.ui.CarSettingsCard.open || (guiding && (com.headunit.launcher.ui.CarSearch.open || com.headunit.launcher.ui.CarStops.open))) {
            // the app's settings card is over the map: one button to close it (as for the search card below; while navigating,
            // the search and stops cards too)
            strip.addAction(Action.Builder().setIcon(icon(R.drawable.ic_car_close)).setOnClickListener { closeSearch() }.build())
        } else if (guiding && fr != null) {
            // a faster route turned up: switch or keep (also on the map card, for cars that pass taps on)
            strip.addAction(Action.Builder().setTitle(L.t("Switch −{0} min", fr.savedMin)).setOnClickListener { e?.acceptFaster(); invalidate() }.build())
            strip.addAction(Action.Builder().setTitle(L.t("Keep")).setOnClickListener { e?.dismissFaster(); invalidate() }.build())
            strip.addAction(sound)
            if (!stopOnCard(e)) strip.addAction(Action.Builder().setTitle(L.t("Stop")).setOnClickListener { e?.endRoute(); invalidate() }.build())
        } else if (guiding) {
            // only four fit: Settings stays while navigating (2D / 3D is the first row in it)
            // the demo drive (for trying it out parked; only with Demo switched on in Settings, as in the app): starts it, speeds it
            // up, stops it. It has Report's place while it's on
            // While the demo plays (four buttons only): End demo in Stop's place, its speed (1x / 2x / 4x, tap to change) and
            // Skip turn in sound's and Report's, and Settings
            if (e != null && e.simMode != 0) {
                strip.addAction(Action.Builder().setTitle(L.t("End demo")).setOnClickListener { e.stopSim(); invalidate() }.build())
                strip.addAction(Action.Builder().setTitle("${e.simMode}\u00D7").setOnClickListener { e.cycleSimSpeed(); invalidate() }.build())
                strip.addAction(Action.Builder().setTitle(L.t("Skip turn")).setOnClickListener { e.skipTurn(); invalidate() }.build())
            } else {
                if (stopOnCard(e)) {
                    // the route is ended from the trip card, which has only that button: the whole route and other ways to go are
                    // round buttons here instead, which Android Auto folds away with its others (only four fit, so the sound
                    // button gives way: Voice guidance is the first thing in the settings)
                    strip.addAction(Action.Builder().setIcon(icon(R.drawable.ic_car_overview)).setOnClickListener { e?.overview() }.build())
                    strip.addAction(Action.Builder().setIcon(icon(R.drawable.ic_car_altroute)).setOnClickListener { e?.findAlternatives() }.build())
                    strip.addAction(if (demoOn) Action.Builder().setTitle(L.t("Demo")).setOnClickListener { e?.cycleSim(); invalidate() }.build() else reportAction())
                } else {
                    strip.addAction(Action.Builder().setTitle(L.t("Stop")).setOnClickListener { e?.endRoute(); invalidate() }.build())
                    if (demoOn) strip.addAction(Action.Builder().setTitle(L.t("Demo")).setOnClickListener { e?.cycleSim(); invalidate() }.build())
                    strip.addAction(sound)
                    if (!demoOn) strip.addAction(reportAction())
                }
            }
            strip.addAction(Action.Builder().setIcon(icon(R.drawable.ic_car_settings)).setOnClickListener { openSettings() }.build())
        } else if (com.headunit.launcher.ui.CarSearch.open) {
            // the app's search card is over the map: Android Auto's buttons would sit on top of it, so there's just one to close it
            // (which a rotary knob can reach too)
            strip.addAction(Action.Builder().setIcon(icon(R.drawable.ic_car_close)).setOnClickListener { closeSearch() }.build())
        } else {
            // Report has the Search button's place, and Search is the first row in Places. Every car gets Places: a rotary knob or
            // the car's buttons can only reach Android Auto's own buttons, never the search bar and chips the app draws on the map
            // (cars that pass taps on still have those too)
            // a trip to carry on (paused by a stop on the way): first, where a rotary knob can reach it (pc4096)
            val rp = e?.resume
            if (rp != null && e != null && e.route == null) strip.addAction(Action.Builder().setTitle(L.t("Carry on")).setOnClickListener { e.startRoute(rp, startNow = true); invalidate() }.build())
            strip.addAction(reportAction())
            // (on a short car screen these are icons, so Android Auto's row of buttons takes less of the map)
            strip.addAction((if (CarLayout.short) Action.Builder().setIcon(icon(R.drawable.ic_car_star)) else Action.Builder().setTitle(L.t("Places")))
                .setOnClickListener { screenManager.push(PlacesScreen(carContext, holder)) }.build())
            strip.addAction(Action.Builder().setIcon(icon(R.drawable.ic_car_settings)).setOnClickListener { openSettings() }.build())
        }
        val mapStrip = ActionStrip.Builder()
            .addAction(Action.PAN)
            .addAction(Action.Builder().setIcon(icon(R.drawable.ic_car_plus)).setOnClickListener { e?.zoom(0.7) }.build())
            .addAction(Action.Builder().setIcon(icon(R.drawable.ic_car_minus)).setOnClickListener { e?.zoom(-0.7) }.build())
            .addAction(Action.Builder().setIcon(icon(R.drawable.ic_car_recenter)).setOnClickListener { e?.recenter(byUser = true) }.build())
            .build()
        val b = NavigationTemplate.Builder().setActionStrip(strip.build()).setMapActionStrip(mapStrip)
        // the turn card, trip figures and the "working out the route" spinner are the app's own, drawn on the map (see CarOverlay):
        // Android Auto's own loading card took the top-left corner and pushed the app's cards aside. The turns still go to Android
        // Auto through updateTrip, for the car's dashboard display and notifications.
        return b.build()
    }

    private fun trip(e: NavEngine, t: TurnInfo, eta: com.headunit.launcher.EtaInfo): Trip {
        val est = estimate(eta.dist, eta.secs, e.imperial)
        val tb = Trip.Builder().addStep(step(e, t, carContext), estimate(t.dist, 0.0, e.imperial))
        val d = e.dest
        tb.addDestination(Destination.Builder().setName(d?.name ?: L.t("Destination")).setAddress(d?.sub ?: "").build(), est)
        if (e.roadNow.isNotBlank()) tb.setCurrentRoad(e.roadNow)
        return tb.build()
    }

    companion object {
        // the same steps as the app's own screen (Geo.fmtDist): feet under a tenth of a mile, then miles. The car's dashboard and
        // head-up display show whatever unit is sent here, so this is what keeps them matching the app (they showed yards before)
        fun distance(m: Double, imperial: Boolean): Distance = if (imperial) {
            val ft = m * 3.28084
            val mi = m / 1609.344
            when {
                ft < 100 -> Distance.create(Math.max(10L, Math.round(ft / 10) * 10).toDouble(), Distance.UNIT_FEET)
                ft < 528 -> Distance.create(Math.max(100L, Math.round(ft / 50) * 50).toDouble(), Distance.UNIT_FEET)
                mi < 10 -> Distance.create(Math.round(mi * 10) / 10.0, Distance.UNIT_MILES_P1)
                else -> Distance.create(Math.round(mi).toDouble(), Distance.UNIT_MILES)
            }
        } else {
            when {
                m < 100 -> Distance.create(Math.max(10L, Math.round(m / 10) * 10).toDouble(), Distance.UNIT_METERS)
                m < 1000 -> Distance.create((Math.round(m / 50) * 50).toDouble(), Distance.UNIT_METERS)
                m < 10_000 -> Distance.create(Math.round(m / 100.0) / 10.0, Distance.UNIT_KILOMETERS_P1)
                else -> Distance.create(Math.round(m / 1000.0).toDouble(), Distance.UNIT_KILOMETERS)
            }
        }

        fun estimate(m: Double, secs: Double, imperial: Boolean): TravelEstimate {
            val arrive = System.currentTimeMillis() + (secs * 1000).toLong()
            return TravelEstimate.Builder(distance(m, imperial), DateTimeWithZone.create(arrive, TimeZone.getDefault()))
                .setRemainingTimeSeconds(secs.toLong().coerceAtLeast(0)).build()
        }

        /** The engine's turn as Android Auto's step: the kind of turn (for its arrow), what to do, and the road. */
        /** The arrow picture for each kind of turn (Android Auto only draws the turn card when the step has one). */
        private fun maneuverIcon(type: Int): Int = when (type) {
            Maneuver.TYPE_TURN_NORMAL_LEFT -> R.drawable.ic_man_turn_left
            Maneuver.TYPE_TURN_NORMAL_RIGHT -> R.drawable.ic_man_turn_right
            Maneuver.TYPE_TURN_SHARP_LEFT -> R.drawable.ic_man_sharp_left
            Maneuver.TYPE_TURN_SHARP_RIGHT -> R.drawable.ic_man_sharp_right
            Maneuver.TYPE_TURN_SLIGHT_LEFT, Maneuver.TYPE_OFF_RAMP_NORMAL_LEFT -> R.drawable.ic_man_slight_left
            Maneuver.TYPE_TURN_SLIGHT_RIGHT, Maneuver.TYPE_OFF_RAMP_NORMAL_RIGHT -> R.drawable.ic_man_slight_right
            Maneuver.TYPE_KEEP_LEFT -> R.drawable.ic_man_keep_left
            Maneuver.TYPE_KEEP_RIGHT -> R.drawable.ic_man_keep_right
            Maneuver.TYPE_U_TURN_LEFT -> R.drawable.ic_man_uturn_left
            Maneuver.TYPE_U_TURN_RIGHT -> R.drawable.ic_man_uturn_right
            Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW, Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW_WITH_ANGLE, Maneuver.TYPE_ROUNDABOUT_ENTER_CW -> R.drawable.ic_man_roundabout_cw
            Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW, Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW_WITH_ANGLE, Maneuver.TYPE_ROUNDABOUT_ENTER_CCW -> R.drawable.ic_man_roundabout_ccw
            Maneuver.TYPE_DESTINATION, Maneuver.TYPE_DESTINATION_LEFT, Maneuver.TYPE_DESTINATION_RIGHT -> R.drawable.ic_man_destination
            else -> R.drawable.ic_man_straight
        }

        /** The last turn arrow picture (sent again with every update on the way to the turn, so it's only drawn once). */
        private var arrowCache: Pair<String, android.graphics.Bitmap>? = null

        /** The turn card's arrow (ui/Components.kt drawManeuver) drawn into a picture for Android Auto; null if it can't be. */
        private fun arrowPicture(t: TurnInfo, clockwise: Boolean): android.graphics.Bitmap? = try {
            val m = t.maneuver.uppercase()
            val rb = m.startsWith("ROUNDABOUT")
            val ea = if (rb) com.headunit.launcher.ui.roundaboutTurn(t.rbIn, t.rbOut) else Float.NaN
            val num = if (rb) t.exit else 0
            val key = "$m|$num|${if (ea.isNaN()) "-" else Math.round(ea / 5f).toString()}|$clockwise|${t.sides?.joinToString(",") { Math.round(it / 10.0).toString() }}"
            arrowCache?.takeIf { it.first == key }?.second ?: run {
                val px = 144
                val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
                androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
                    androidx.compose.ui.graphics.Canvas(bmp.asImageBitmap()), androidx.compose.ui.geometry.Size(px.toFloat(), px.toFloat())) {
                    drawManeuver(t.maneuver, com.headunit.launcher.Geo.maneuverAngle(t.maneuver), androidx.compose.ui.graphics.Color.White, clockwise, ea, num, t.sides)
                }
                arrowCache = key to bmp
                bmp
            }
        } catch (e: Throwable) { null }

        /** The last lanes picture (the same junction is sent many times on the approach). */
        private var lanesCache: Pair<String, android.graphics.Bitmap>? = null

        private fun laneHit(dir: String, maneuver: String): Boolean {
            val m = maneuver.uppercase()
            return when {
                m.contains("U_TURN") || m.contains("UTURN") -> dir.contains("U_TURN")
                m.contains("LEFT") -> dir.contains("LEFT") && !dir.contains("U_TURN")
                m.contains("RIGHT") -> dir.contains("RIGHT") && !dir.contains("U_TURN")
                else -> dir == "STRAIGHT"
            }
        }

        private fun laneShape(d: String): Int {
            val left = d.contains("LEFT")
            return when {
                d.contains("U_TURN") -> if (left) LaneDirection.SHAPE_U_TURN_LEFT else LaneDirection.SHAPE_U_TURN_RIGHT
                d.contains("SHARP") -> if (left) LaneDirection.SHAPE_SHARP_LEFT else LaneDirection.SHAPE_SHARP_RIGHT
                d.contains("SLIGHT") || d.contains("BEAR") -> if (left) LaneDirection.SHAPE_SLIGHT_LEFT else LaneDirection.SHAPE_SLIGHT_RIGHT
                left -> LaneDirection.SHAPE_NORMAL_LEFT
                d.contains("RIGHT") -> LaneDirection.SHAPE_NORMAL_RIGHT
                d == "STRAIGHT" -> LaneDirection.SHAPE_STRAIGHT
                else -> LaneDirection.SHAPE_UNKNOWN
            }
        }

        /** The lanes drawn side by side: white arrows for the lanes to use, grey for the rest. */
        private fun lanesPicture(lanes: List<List<Pair<String, Boolean>>>): android.graphics.Bitmap {
            val lw = 72; val h = 96
            val bmp = android.graphics.Bitmap.createBitmap(lw * lanes.size, h, android.graphics.Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
            val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val sep = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF; strokeWidth = 3f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 8f), 0f) }
            for ((i, dirs) in lanes.withIndex()) {
                val cx = lw * i + lw / 2f
                if (i > 0) c.drawLine(lw * i.toFloat(), 8f, lw * i.toFloat(), h - 8f, sep)
                // the lane's other arrows first, then the ones to use on top
                for ((d, ok) in dirs.sortedBy { it.second }) {
                    val col = if (ok) Color.WHITE else 0x80FFFFFF.toInt()
                    line.color = col; head.color = col
                    val ang = when {
                        d.contains("U_TURN") -> 180.0
                        d.contains("SHARP") -> 135.0
                        d.contains("SLIGHT") || d.contains("BEAR") -> 45.0
                        d.contains("LEFT") || d.contains("RIGHT") -> 90.0
                        else -> 0.0
                    } * (if (d.contains("LEFT")) -1 else 1)
                    val baseY = h - 10f; val midY = h * 0.52f
                    val p = android.graphics.Path(); p.moveTo(cx, baseY)
                    val tipX: Float; val tipY: Float; val dirA: Double
                    if (Math.abs(ang) >= 179.0) {
                        // a U-turn: up, over and back down
                        val side = if (ang < 0) -1f else 1f
                        p.lineTo(cx, h * 0.34f)
                        p.quadTo(cx, 12f, cx + side * 14f, 12f)
                        p.quadTo(cx + side * 26f, 12f, cx + side * 26f, h * 0.34f)
                        p.lineTo(cx + side * 26f, h * 0.48f)
                        tipX = cx + side * 26f; tipY = h * 0.48f + 4f; dirA = 180.0
                    } else {
                        p.lineTo(cx, midY)
                        val len = if (ang == 0.0) midY - 16f else 26f
                        val r = Math.toRadians(ang)
                        tipX = cx + (Math.sin(r) * len).toFloat(); tipY = midY - (Math.cos(r) * len).toFloat()
                        p.lineTo(tipX, tipY); dirA = ang
                    }
                    c.drawPath(p, line)
                    // the arrowhead, pointing the way the arrow goes
                    val r = Math.toRadians(dirA)
                    val fx = Math.sin(r).toFloat(); val fy = -Math.cos(r).toFloat()
                    val px = -fy; val py = fx
                    val hp = android.graphics.Path()
                    hp.moveTo(tipX + fx * 12f, tipY + fy * 12f)
                    hp.lineTo(tipX - fx * 4f + px * 12f, tipY - fy * 4f + py * 12f)
                    hp.lineTo(tipX - fx * 4f - px * 12f, tipY - fy * 4f - py * 12f)
                    hp.close()
                    c.drawPath(hp, head)
                }
            }
            return bmp
        }

        /**
         * How far round the roundabout your exit is, in degrees (1 to 360) in the direction the traffic goes round: clockwise where
         * you drive on the left, anticlockwise where you drive on the right. Straight on is 180; the first exit to the side is
         * about 90; going all the way round is 360. From the roads' real directions in and out when the route has them (Mapbox),
         * otherwise from the turn's direction (left, right, straight on, back). Null when neither is known.
         */
        private fun roundaboutExitAngle(t: TurnInfo, m: String, clockwise: Boolean): Int? {
            // the turn: 0 straight on, positive to the right, negative to the left (-180 to 180)
            val exact = com.headunit.launcher.ui.roundaboutTurn(t.rbIn, t.rbOut).toDouble()
            val d: Double = if (!exact.isNaN()) exact else when {
                m.contains("BACK") || m.contains("UTURN") || m.contains("U_TURN") -> 180.0
                m.contains("SHARP_LEFT") -> -135.0
                m.contains("SHARP_RIGHT") -> 135.0
                m.contains("SLIGHT_LEFT") || m.contains("BEAR_LEFT") -> -45.0
                m.contains("SLIGHT_RIGHT") || m.contains("BEAR_RIGHT") -> 45.0
                m.contains("LEFT") -> -90.0
                m.contains("RIGHT") -> 90.0
                m.contains("CROSS") || m.contains("STRAIGHT") -> 0.0
                else -> return null
            }
            // going round clockwise, a left turn is the first quarter; anticlockwise, a right turn is
            val a = Math.round(((if (clockwise) 180.0 + d else 180.0 - d) % 360.0 + 360.0) % 360.0).toInt()
            return if (a <= 0 || a > 360) 360 else a
        }

        fun step(e: NavEngine, t: TurnInfo, ctx: CarContext): Step {
            val left = !e.mirrored
            val m = t.maneuver.uppercase()
            // A roundabout's exit angle: how far round the roundabout the exit is (degrees, the way the traffic goes round). The
            // car's own displays (a head-up display, the dashboard) draw the exit from it; without it some, like the VW ID.7's
            // head-up display, showed every roundabout as a right turn (desko1973)
            val rbAngle = if (m.startsWith("ROUNDABOUT") && t.exit > 0) roundaboutExitAngle(t, m, e.roundaboutClockwise) else null
            val type = when {
                m.startsWith("ROUNDABOUT") -> if (t.exit > 0) (
                                                  if (rbAngle != null) (if (e.roundaboutClockwise) Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW_WITH_ANGLE else Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW_WITH_ANGLE)
                                                  else (if (e.roundaboutClockwise) Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW else Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW))
                                               else (if (e.roundaboutClockwise) Maneuver.TYPE_ROUNDABOUT_ENTER_CW else Maneuver.TYPE_ROUNDABOUT_ENTER_CCW)
                m == "TURN_LEFT" -> Maneuver.TYPE_TURN_NORMAL_LEFT
                m == "TURN_RIGHT" -> Maneuver.TYPE_TURN_NORMAL_RIGHT
                m == "SHARP_LEFT" -> Maneuver.TYPE_TURN_SHARP_LEFT
                m == "SHARP_RIGHT" -> Maneuver.TYPE_TURN_SHARP_RIGHT
                m == "BEAR_LEFT" -> Maneuver.TYPE_TURN_SLIGHT_LEFT
                m == "BEAR_RIGHT" -> Maneuver.TYPE_TURN_SLIGHT_RIGHT
                m == "KEEP_LEFT" -> Maneuver.TYPE_KEEP_LEFT
                m == "KEEP_RIGHT" -> Maneuver.TYPE_KEEP_RIGHT
                m.contains("UTURN") || m.contains("U_TURN") -> if (left) Maneuver.TYPE_U_TURN_RIGHT else Maneuver.TYPE_U_TURN_LEFT
                m.startsWith("MOTORWAY_EXIT_LEFT") -> Maneuver.TYPE_OFF_RAMP_NORMAL_LEFT
                m.startsWith("MOTORWAY_EXIT_RIGHT") -> Maneuver.TYPE_OFF_RAMP_NORMAL_RIGHT
                m == "ARRIVE_LEFT" -> Maneuver.TYPE_DESTINATION_LEFT
                m == "ARRIVE_RIGHT" -> Maneuver.TYPE_DESTINATION_RIGHT
                m.startsWith("ARRIVE") -> Maneuver.TYPE_DESTINATION
                m == "DEPART" -> Maneuver.TYPE_DEPART
                m.contains("FERRY") -> Maneuver.TYPE_FERRY_BOAT
                m == "STRAIGHT" || m == "FOLLOW" -> Maneuver.TYPE_STRAIGHT
                else -> Maneuver.TYPE_UNKNOWN
            }
            // the arrow: the plain arrow image for each kind of turn. This only goes to the car's own displays (the dashboard and a
            // head-up display), which draw the kind of turn and the roundabout's exit from the rest of the step; the picture of the
            // phone's turn card that was sent here for a few versions made the VW's display drop the turn altogether (desko1973, #240)
            val mb = Maneuver.Builder(type).setIcon(CarIcon.Builder(IconCompat.createWithResource(ctx, maneuverIcon(type))).build())
            val rbExitType = type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW || type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW ||
                type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW_WITH_ANGLE || type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW_WITH_ANGLE
            if (rbExitType) mb.setRoundaboutExitNumber(t.exit.coerceAtLeast(1))
            if (rbAngle != null) mb.setRoundaboutExitAngle(rbAngle)
            val what = when {
                rbExitType -> L.t("Take exit {0}", t.exit)
                m.startsWith("ARRIVE") -> L.t("Arrive")
                t.tag.isNotBlank() -> t.tag
                else -> com.headunit.launcher.Geo.maneuverText(m, t.exit).replaceFirstChar { it.uppercase() }
            }
            // at traffic lights or a stop sign: "At the lights · Smith Street"
            val lead = if (t.landmark.isNotBlank()) t.landmark else what
            val cue = if (t.street.isNotBlank()) "$lead · ${t.street}" else lead
            val sb = Step.Builder(cue).setManeuver(mb.build())
            if (t.street.isNotBlank()) sb.setRoad(t.street)
            // which lanes to be in, for the car's own displays (the dashboard behind the wheel or a head-up display, where the car has
            // one): each lane's arrows with the ones to use marked, plus the picture of them that Android Auto needs alongside
            try {
                val sec = t.lanes
                if (sec != null && sec.lanes.size in 1..10) {
                    val anyFollow = sec.lanes.any { it.follow != null }
                    val recs = ArrayList<List<Pair<String, Boolean>>>()
                    for (ln in sec.lanes) {
                        val dirs = ln.dirs.ifEmpty { listOf("STRAIGHT") }
                        val r = dirs.map { d -> d to (if (anyFollow) d == ln.follow else laneHit(d, t.maneuver)) }
                        recs += r
                        val lb = androidx.car.app.navigation.model.Lane.Builder()
                        for ((d, ok) in r) lb.addDirection(LaneDirection.create(laneShape(d), ok))
                        sb.addLane(lb.build())
                    }
                    val key = recs.toString()
                    val pic = lanesCache?.takeIf { it.first == key }?.second ?: lanesPicture(recs).also { lanesCache = key to it }
                    sb.setLanesImage(CarIcon.Builder(IconCompat.createWithBitmap(pic)).build())
                }
            } catch (x: Exception) { }
            return sb.build()
        }
    }
}

/** Lets the map screen's once-a-second tick also update the speed sign drawn on the map. */
object CarTicks { var tick: (() -> Unit)? = null }

/** A coloured icon for a list row. */
/** A place's logo for a car list row, when it has already been loaded (lists can't wait for one); null otherwise. */
private fun logoIcon(p: Place): CarIcon? = Logos.cached(Logos.domainFor(p))?.let { CarIcon.Builder(IconCompat.createWithBitmap(it)).build() }

/**
 * "See all locations" on the car: the branches as a list, quickest first (drive time and distance), and on the map. Picking one
 * goes there; Back closes it. Waits for the look-up with the list's loading spinner.
 */
private class CarChainScreen(ctx: CarContext, private val holder: CarHolder) : Screen(ctx) {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val poll = object : Runnable { override fun run() { invalidate(); val e = holder.engine; if (e != null && e.chain == null && e.chainBusy) handler.postDelayed(this, 500) } }
    init {
        handler.postDelayed(poll, 500)
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) { handler.removeCallbacks(poll); holder.engine?.let { if (it.chain != null) it.closeChain() } }
        })
    }
    override fun onGetTemplate(): Template {
        val e = holder.engine ?: return ListTemplate.Builder().setTitle(L.t("Searching…")).setHeaderAction(Action.BACK).setLoading(true).build()
        val view = e.chain
        val b = ListTemplate.Builder().setTitle(view?.let { if (it.forStop) L.t("Add a stop: {0}", it.name) else it.name } ?: L.t("Searching…")).setHeaderAction(Action.BACK)
        if (view == null) {
            if (e.chainBusy) return b.setLoading(true).build()
            return b.setSingleList(ItemList.Builder().setNoItemsMessage(L.t("No other branches found nearby")).build()).build()
        }
        val limit = try { carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST) } catch (x: Exception) { 6 }
        // petrol stations with prices: Cheapest and Least detour, in the list's top bar
        if (view.forStop && view.spots.any { it.price != null }) {
            val cheap = e.chainSort == "cheap"
            b.setActionStrip(androidx.car.app.model.ActionStrip.Builder().addAction(
                Action.Builder().setTitle(if (cheap) L.t("Least detour") else L.t("Cheapest"))
                    .setOnClickListener { e.sortChainBy(if (cheap) "route" else "cheap"); invalidate() }.build()).build())
        }
        val list = ItemList.Builder()
        for (s in e.chainSorted().take(limit.coerceIn(1, 25))) {
            // adding a stop: the name, the time it adds, and how far ahead; tapping adds it
            if (view.forStop) {
                // (petrol stations: the price of your fuel there too, as on the phone)
                val pr = s.price?.let { " · " + com.headunit.launcher.fmtFuelPrice(it, s.unit) }.orEmpty()
                val r = Row.Builder().setTitle(s.place.name)
                    .addText(L.t("+{0} min", Math.max(1, Math.round(s.detour / 60.0).toInt())) + " · " + L.t("{0} ahead", Geo.fmtDist(s.ahead, e.imperial)) + pr)
                    .setImage(logoIcon(s.place) ?: rowIcon(carContext, R.drawable.ic_car_pin, 0xFFEA4335.toInt()))
                r.setOnClickListener { e.chainAddStop(s); screenManager.popToRoot() }
                list.addItem(r.build())
                continue
            }
            val parts = s.place.sub.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val road = parts.firstOrNull()?.replace(Regex("^[0-9]+[A-Za-z]?(-[0-9]+)?\\s+"), "") ?: s.place.name
            val time = if (s.secs >= 0) L.t("{0} min", Math.max(1, Math.round(s.secs / 60.0).toInt())) + " · " else ""
            val r = Row.Builder().setTitle(road).addText(time + Geo.fmtDist(if (s.road >= 0) s.road else s.dist, e.imperial) + (parts.getOrNull(1)?.let { " · $it" } ?: ""))
                .setImage(logoIcon(s.place) ?: rowIcon(carContext, R.drawable.ic_car_pin, 0xFFEA4335.toInt()))
            r.setOnClickListener { e.chainGo(s); screenManager.popToRoot() }
            list.addItem(r.build())
        }
        return b.setSingleList(list.build()).build()
    }
}

private fun rowIcon(ctx: CarContext, res: Int, color: Int): CarIcon =
    CarIcon.Builder(IconCompat.createWithResource(ctx, res)).setTint(androidx.car.app.model.CarColor.createCustom(color, color)).build()

/** "1.2 mi" from where you are (or nothing when the position isn't known yet). */
private fun distFrom(e: NavEngine?, p: Place): String {
    if (e == null) return ""
    val here = e.fix?.pos ?: return ""
    return com.headunit.launcher.Geo.fmtDist(com.headunit.launcher.Geo.dist(here, p.pos), e.imperial)
}

/** Home, Work, your saved places, favourites and recent destinations: tap one to go. */
class PlacesScreen(carContext: CarContext, private val holder: CarHolder) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val e = holder.engine
        val limit = try { carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST) } catch (x: Exception) { 6 }
        val list = ItemList.Builder().setNoItemsMessage(L.t(if (e == null) "Starting the map…" else "No saved or recent places yet"))
        // Search first: on cars that don't pass taps on to the map, this is the way in to it (the Search button is Report now)
        var shownRows = 0
        if (e != null) {
            list.addItem(Row.Builder().setTitle(L.t("Search")).addText(L.t("Type or say where to"))
                .setImage(rowIcon(carContext, R.drawable.ic_car_search, 0xFF9CA4BF.toInt()))
                .setOnClickListener { screenManager.push(SearchScreen(carContext, holder)) }.build())
            shownRows = 1
        }
        if (e != null) {
            // (label, place, icon, colour) in the app's colours: Home blue, Work amber, saved teal, favourites gold, recent grey
            val all = ArrayList<Triple<String, Place, Pair<Int, Int>>>()
            e.places.slot("home")?.let { all += Triple(L.t("Home"), it, R.drawable.ic_car_home to 0xFF4C8DFF.toInt()) }
            e.places.slot("work")?.let { all += Triple(L.t("Work"), it, R.drawable.ic_car_work to 0xFFF29900.toInt()) }
            for (c in e.places.customs()) all += Triple(if (c.group.isNotBlank()) "${c.label} · ${c.group}" else c.label, c.place, R.drawable.ic_car_star to 0xFF12A4B4.toInt())
            for (p in e.places.favourites().sortedWith(Comparator { a, b -> java.text.Collator.getInstance(L.locale).compare(a.name, b.name) }))
                all += Triple(p.name, p, R.drawable.ic_car_star to 0xFFF2C230.toInt())
            for (p in e.places.recents()) all += Triple(p.name, p, R.drawable.ic_car_history to 0xFF9CA4BF.toInt())
            // saved routes (planned on the phone with Save route): the same stops and the same way, with today's traffic
            for (sr in e.savedRoutes().take(3)) {
                if (shownRows >= limit.coerceIn(1, 12) - 2) break
                val r = Row.Builder().setTitle(sr.name).setImage(rowIcon(carContext, R.drawable.ic_car_route, 0xFF4C8DFF.toInt()))
                r.addText(when (sr.stops.size) { 0 -> L.t("Saved route"); 1 -> L.t("Saved route · 1 stop"); else -> L.t("Saved route · {0} stops", sr.stops.size) })
                r.setOnClickListener { e.startSavedRoute(sr); screenManager.popToRoot() }
                list.addItem(r.build())
                shownRows++
            }
            val seen = HashSet<String>()
            for ((label, p, ic) in all) {
                if (!seen.add("%.4f,%.4f".format(p.pos.lat, p.pos.lon))) continue
                if (seen.size + shownRows > limit.coerceIn(1, 12)) break
                val r = Row.Builder().setTitle(label).setImage(rowIcon(carContext, ic.first, ic.second))
                val sub = if (label == p.name) p.sub else p.name
                val d = distFrom(e, p)
                val line = listOf(d, sub).filter { it.isNotBlank() }.joinToString(" · ")
                if (line.isNotBlank()) r.addText(line)
                r.setOnClickListener { go(e, p) }
                list.addItem(r.build())
            }
        }
        return ListTemplate.Builder().setTitle(L.t("Places")).setHeaderAction(Action.BACK).setSingleList(list.build()).build()
    }

    private fun go(e: NavEngine, p: Place) {
        e.startRoute(p)
        screenManager.popToRoot()
    }
}

/** "Report here": police, crash, hazard, broken down or mobile camera, saved where you are (the same reports as the phone's). */
/**
 * Other routes while navigating (from Settings): asks for them, then lists them with how they compare, to pick with a touch or the
 * car's knob (the grey routes on the map can only be tapped). Also on the map while this is open.
 */
class AltRoutesScreen(carContext: CarContext, private val holder: CarHolder) : Screen(carContext) {
    private val handler = Handler(android.os.Looper.getMainLooper())
    private var asked = 0L
    private val poll = object : Runnable {
        override fun run() { invalidate(); if (System.currentTimeMillis() - asked < 20_000L) handler.postDelayed(this, 1500) }
    }

    init {
        holder.engine?.let { it.findAlternatives(); asked = System.currentTimeMillis(); handler.postDelayed(poll, 1500) }
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) { handler.removeCallbacks(poll) }
        })
    }

    override fun onGetTemplate(): Template {
        val e = holder.engine
        val alts = e?.liveAlts ?: emptyList()
        val b = ListTemplate.Builder().setTitle(L.t("Other routes")).setHeaderAction(Action.BACK)
        if (alts.isEmpty()) {
            if (System.currentTimeMillis() - asked < 20_000L) return b.setLoading(true).build()
            return b.setSingleList(ItemList.Builder().setNoItemsMessage(L.t("No other routes right now")).build()).build()
        }
        val list = ItemList.Builder()
        for ((i, a) in alts.withIndex()) {
            val diff = when { a.diffMin > 0 -> L.t("{0} min slower", a.diffMin); a.diffMin < 0 -> L.t("{0} min faster", -a.diffMin); else -> L.t("About the same time") }
            // (the first named road after it leaves your route)
            val split = a.route.cum.getOrNull(a.divIdx) ?: 0.0
            val via = a.route.steps.firstOrNull { it.off > split && it.street.isNotBlank() }?.street
            list.addItem(Row.Builder().setTitle(diff).apply { if (via != null) addText(L.t("Via {0}", via)) }
                .setOnClickListener { e?.switchToAlt(i); screenManager.pop() }.build())
        }
        return b.setSingleList(list.build()).build()
    }
}

/** One of your reports, tapped on the car's map: Remove takes just that one away, Keep goes back to the map. */
class RemoveReportScreen(carContext: CarContext, private val engine: NavEngine, private val r: NavEngine.Report) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val remove = Action.Builder().setTitle(L.t("Remove"))
            .setBackgroundColor(androidx.car.app.model.CarColor.RED)
            .setOnClickListener { try { engine.removeReport(r) } catch (e: Exception) { }; try { androidx.car.app.CarToast.makeText(carContext, L.t("Report removed"), androidx.car.app.CarToast.LENGTH_SHORT).show() } catch (e: Exception) { }; screenManager.pop() }
            .build()
        val keep = Action.Builder().setTitle(L.t("Keep")).setOnClickListener { screenManager.pop() }.build()
        return androidx.car.app.model.MessageTemplate.Builder(engine.reportAge(r))
            .setTitle(L.t(engine.reportName(r.kind)))
            .setHeaderAction(Action.BACK)
            .addAction(remove).addAction(keep)
            .build()
    }
}

class ReportScreen(carContext: CarContext, private val holder: CarHolder) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val kinds = listOf(
            Triple("police", L.t("Police"), R.drawable.ic_car_police to 0xFF8AB4F8.toInt()),
            Triple("accident", L.t("Crash"), R.drawable.ic_car_crash to 0xFFF28B82.toInt()),
            Triple("hazard", L.t("Hazard"), R.drawable.ic_car_report to 0xFFFBBC04.toInt()),
            Triple("breakdown", L.t("Broken down"), R.drawable.ic_car_breakdown to 0xFFFCAD70.toInt()),
            Triple("traffic", L.t("Traffic jam"), R.drawable.ic_car_traffic to 0xFFF28B82.toInt()),
            Triple("mobilecam", L.t("Mobile camera"), R.drawable.ic_car_mobilecam to 0xFF8AB4F8.toInt()),
        )
        val list = ItemList.Builder()
        for ((kind, label, ic) in kinds) list.addItem(
            GridItem.Builder().setTitle(label).setImage(rowIcon(carContext, ic.first, ic.second), GridItem.IMAGE_TYPE_LARGE)
                .setOnClickListener { send(kind) }.build()
        )
        // Search here too: Report has the Search button's place, and this is the way to search while navigating (a stop on the way)
        list.addItem(GridItem.Builder().setTitle(L.t("Search")).setImage(rowIcon(carContext, R.drawable.ic_car_search, 0xFFE8EAED.toInt()), GridItem.IMAGE_TYPE_LARGE)
            .setOnClickListener { screenManager.push(SearchScreen(carContext, holder)) }.build())
        return GridTemplate.Builder().setTitle(L.t("Report here")).setHeaderAction(Action.BACK).setSingleList(list.build()).build()
    }

    private fun send(kind: String) {
        val e = holder.engine
        val msg = when {
            e == null -> L.t("The map is still starting")
            e.fix == null -> L.t("Waiting for GPS")
            else -> null
        }
        if (msg != null || e == null) {
            try { androidx.car.app.CarToast.makeText(carContext, msg ?: "", androidx.car.app.CarToast.LENGTH_SHORT).show() } catch (x: Exception) { }
            return
        }
        try { e.report(kind) } catch (x: Exception) { }
        try { androidx.car.app.CarToast.makeText(carContext, "${e.reportName(kind)} report saved here", androidx.car.app.CarToast.LENGTH_SHORT).show() } catch (x: Exception) { }
        screenManager.pop()
    }
}

/** Search with the car's keyboard or voice: results from the same search as the app (TomTom, or offline when there's no signal). */
/**
 * Search on the car screen. [autoGo]: a place asked for by voice ("Hey Google, navigate to …"): the top result's route starts by
 * itself (the route choice shows where to, and Cancel), like other navigation apps, instead of a list to tap while driving.
 * If nothing comes back within about 10 seconds the list stays, to search again.
 */
class SearchScreen(carContext: CarContext, private val holder: CarHolder, initial: String = "", private var autoGo: Boolean = false,
    /** false when opened from the microphone button: the keyboard stays away, so Android Auto's own microphone is right there. */
    private val keyboard: Boolean = true,
    /** While a route is being driven: "stop" adds what's picked as a stop on the way, "ask" asks on the map (Add as a stop /
     *  Go here instead); otherwise the route to it starts, as with no route. */
    private val mode: String = "") : Screen(carContext) {
    private fun go(e: NavEngine?, p: Place) {
        if (e == null) return
        // (the search card this was opened from has done its job)
        if (mode.isNotEmpty()) com.headunit.launcher.ui.CarSearch.open = false
        // (Set home / Set work, then the car's keyboard: the place is saved there, TheDMAl #326)
        if (e.pendingSave != null) { e.pickPlace(p); return }
        when { mode == "stop" && e.driving() -> e.addStop(p); mode == "ask" -> e.goOrAsk(p); else -> e.startRoute(p) }
    }
    private var query = initial
    private var shown: List<Place> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    /** What the search was last asked for, and the results there were then: until new ones come the list shows it's searching. */
    private var asked = ""
    private var askedMs = 0L
    private var stale: List<Place>? = null
    private var waiting = false
    private var typedPending = false
    /** Still typing on the car keyboard (not yet sent with the search key), so the keyboard stays up when results arrive. */
    private var typingNow = false
    /** The results that were there before this search (so old ones aren't taken for the answer). */
    private val before: List<Place>? = holder.engine?.results
    private val askedAt = android.os.SystemClock.uptimeMillis()
    private val poll = object : Runnable {
        override fun run() {
            val e = holder.engine
            val r = e?.results ?: emptyList()
            // the answer to what was asked (or nothing after 8 s: then the list says so instead of searching for ever)
            if (waiting && (r !== stale || android.os.SystemClock.uptimeMillis() - askedMs > 8_000L)) {
                waiting = false
                android.util.Log.i("CarNav", "search: ${r.size} results for \"$asked\"")
                shown = r; invalidate()
            } else if (r !== shown) { shown = r; invalidate() }
            // what's in the search box hasn't been searched for (see [ask]): searched now
            if (e != null && query.length >= 3 && query != asked && !typedPending) ask(query)
            if (autoGo) {
                if (e != null && r !== before && r.isNotEmpty()) {
                    autoGo = false
                    val p = r.first()
                    val asking = mode == "ask" && e.driving()
                    go(e, p)
                    if (!asking) try { androidx.car.app.CarToast.makeText(carContext, L.t("Going to {0}", p.name), androidx.car.app.CarToast.LENGTH_LONG).show() } catch (x: Exception) { }
                    try { screenManager.popToRoot() } catch (x: Exception) { }
                    return
                }
                if (android.os.SystemClock.uptimeMillis() - askedAt > 10_000L) autoGo = false
            }
            handler.postDelayed(this, 500)
        }
    }

    private val typed = Runnable { typedPending = false; if (query.length >= 3 && query != asked) ask(query) }

    /**
     * Searches for [q]. Every search goes through here, so the screen knows what was last searched for: a search that was
     * still waiting for typing to pause when Android Auto stopped the screen (its voice input comes in front of it) used to be
     * dropped and never asked again, leaving the words in the search box with an empty list under them.
     */
    private fun ask(q: String) {
        handler.removeCallbacks(typed); typedPending = false
        val e = holder.engine ?: return
        asked = q; askedMs = android.os.SystemClock.uptimeMillis(); stale = e.results; waiting = true
        android.util.Log.i("CarNav", "search: asking for \"$q\"")
        e.search(q)
        invalidate()
    }

    init {
        if (initial.length >= 2) ask(initial)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { handler.removeCallbacks(poll); handler.post(poll) }
            // (a search waiting for typing to pause is kept: it runs, and the list is brought up to date when the screen is back)
            override fun onStop(owner: LifecycleOwner) { handler.removeCallbacks(poll) }
            override fun onDestroy(owner: LifecycleOwner) { handler.removeCallbacks(poll); handler.removeCallbacks(typed) }
        })
    }

    override fun onGetTemplate(): Template {
        val e = holder.engine
        val limit = try { carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST) } catch (x: Exception) { 6 }
        val list = ItemList.Builder().setNoItemsMessage(L.t(if (query.length < 3) "Type a place, address or postcode" else "No matches. Try adding a town or postcode."))
        // several branches of what was searched nearby: "See all N locations" first
        val chainN = if (query.length >= 3 && e != null && shown.isNotEmpty()) try { e.chainCount(query.trim()) } catch (x: Exception) { 0 } else 0
        // on a route: everything matching along the rest of it, by the time each adds (adding a stop)
        val onRoute = query.trim().length >= 3 && e != null && e.route != null && e.dest != null
        if (onRoute) {
            val q = query.trim()
            list.addItem(Row.Builder().setTitle(L.t("See them along your route")).addText(L.t("On the map, with the time each adds"))
                .setImage(rowIcon(carContext, R.drawable.ic_car_route, 0xFF4C8DFF.toInt()))
                .setOnClickListener { e?.openStopSearch(q); screenManager.push(CarChainScreen(carContext, holder)) }.build())
        }
        if (chainN >= 2 && !onRoute) {
            val first = shown.firstOrNull { Logos.norm(it.name).startsWith(Logos.norm(query)) }
            val r = Row.Builder().setTitle(L.t("See all {0} locations", chainN)).addText(L.t(if (e?.imperial == true) "Within 10 miles" else "Within 15 km"))
                .setImage(first?.let { logoIcon(it) } ?: rowIcon(carContext, R.drawable.ic_car_pin, 0xFF4C8DFF.toInt()))
            val q = query.trim()
            r.setOnClickListener { e?.openChain(q); screenManager.push(CarChainScreen(carContext, holder)) }
            list.addItem(r.build())
        }
        // nothing typed yet: Home, Work, saved and recent places to tap, rather than an empty screen
        if (query.length < 3 && e != null) {
            val all = ArrayList<Triple<String, Place, Pair<Int, Int>>>()
            e.places.slot("home")?.let { all += Triple(L.t("Home"), it, R.drawable.ic_car_home to 0xFF4C8DFF.toInt()) }
            e.places.slot("work")?.let { all += Triple(L.t("Work"), it, R.drawable.ic_car_work to 0xFFF29900.toInt()) }
            for (p in e.places.recents()) all += Triple(p.name, p, R.drawable.ic_car_history to 0xFF9CA4BF.toInt())
            for (cp in e.places.customs()) all += Triple(cp.label, cp.place, R.drawable.ic_car_star to 0xFF12A4B4.toInt())
            for (p in e.places.favourites()) all += Triple(p.name, p, R.drawable.ic_car_star to 0xFFF2C230.toInt())
            val seen = HashSet<String>()
            for ((label, p, ic) in all) {
                if (seen.size >= limit.coerceIn(1, 12)) break
                if (!seen.add("%.4f,%.4f".format(p.pos.lat, p.pos.lon))) continue
                val r = Row.Builder().setTitle(label).setImage(rowIcon(carContext, ic.first, ic.second))
                val line = listOf(distFrom(e, p), if (label == p.name) p.sub else p.name).filter { it.isNotBlank() }.joinToString(" · ")
                if (line.isNotBlank()) r.addText(line)
                r.setOnClickListener { go(e, p); screenManager.popToRoot() }
                list.addItem(r.build())
            }
        }
        if (query.length >= 3) for (p in shown.take((limit - (if (onRoute || chainN >= 2) 1 else 0)).coerceIn(1, 12))) {
            // the shop's logo (once it's been loaded) or a red pin, the name, and how far it is with the address under it
            val r = Row.Builder().setTitle(p.name).setImage(logoIcon(p) ?: rowIcon(carContext, R.drawable.ic_car_pin, 0xFFEA4335.toInt()))
            val line = listOf(distFrom(e, p), p.sub).filter { it.isNotBlank() }.joinToString(" · ")
            if (line.isNotBlank()) r.addText(line)
            r.setOnClickListener { go(e, p); screenManager.popToRoot() }
            list.addItem(r.build())
        }
        val b = SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            // searched once typing pauses (as in the app), not on every letter: each search is a request on your TomTom key
            override fun onSearchTextChanged(searchText: String) {
                android.util.Log.i("CarNav", "search: text \"$searchText\"")
                query = searchText
                handler.removeCallbacks(typed); typedPending = false
                // (a longer pause before searching while you type, and the keyboard kept up when the results arrive: after half a
                // second the results replaced the keyboard on some cars, long before the name was finished, GitHub #293)
                typingNow = true
                if (searchText.length >= 3 && searchText != asked) { typedPending = true; handler.postDelayed(typed, 1300) }
            }
            override fun onSearchSubmitted(searchText: String) {
                android.util.Log.i("CarNav", "search: submitted \"$searchText\"")
                typingNow = false
                query = searchText
                if (searchText.length >= 2) ask(searchText)
            }
        }).setHeaderAction(Action.BACK).setSearchHint(L.t("Search for a place")).setInitialSearchText(query)
            .setShowKeyboardByDefault(keyboard && (query.isEmpty() || typingNow))
        // still searching: Android Auto's own spinner, rather than the last search's places under the new words
        return (if (waiting && query.length >= 3) b.setLoading(true) else b.setItemList(list.build())).build()
    }
}

/** Fuel, charging, food, coffee or parking near you (or along the route): the same search as the app's chips. Tap one to go. */
class NearbyScreen(carContext: CarContext, private val holder: CarHolder, private val kind: String) : Screen(carContext) {
    private val handler = Handler(Looper.getMainLooper())
    private var shown: List<com.headunit.launcher.Nearby>? = null
    private var busyShown = true
    private val poll = object : Runnable {
        override fun run() {
            val e = holder.engine
            val r = e?.nearby ?: emptyList()
            val b = e?.nearbyBusy ?: false
            if (r !== shown || b != busyShown) { shown = r; busyShown = b; invalidate() }
            handler.postDelayed(this, 500)
        }
    }

    init {
        holder.engine?.loadNearby(kind, false)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // back from one of your folders: its list replaced this one, so this one's is loaded again
                holder.engine?.let { if (it.nearbyKind != kind && (kind.startsWith("places") || kind.startsWith("tracks"))) it.loadNearby(kind, false) }
                handler.post(poll)
            }
            override fun onStop(owner: LifecycleOwner) { handler.removeCallbacks(poll) }
            // the places drawn on the map go with the list (picking one already cleared them)
            override fun onDestroy(owner: LifecycleOwner) { holder.engine?.clearNearby() }
        })
    }

    private val title = if (kind.contains(':')) kind.substringAfter(':') else L.t(when (kind) { "fuel" -> "Fuel"; "charging" -> "Charging"; "food" -> "Food"; "coffee" -> "Coffee"; "parking" -> "Parking"; "toilets" -> "Toilets"; "services" -> "Services"; "lorrypark" -> "Lorry parks"; "places" -> "Your places"; "tracks" -> "Your tracks"; else -> "Nearby" })
    private val iconRes = when (kind) { "fuel" -> R.drawable.ic_car_fuel; "charging" -> R.drawable.ic_car_charging; "food" -> R.drawable.ic_car_food; "coffee" -> R.drawable.ic_car_coffee; "parking" -> R.drawable.ic_car_parking; else -> R.drawable.ic_car_pin }
    private val tint = when (kind) { "fuel" -> 0xFFEA4335.toInt(); "charging" -> 0xFF34A853.toInt(); "food" -> 0xFFF29900.toInt(); "coffee" -> 0xFFA56BFF.toInt(); else -> 0xFF4C8DFF.toInt() }

    override fun onGetTemplate(): Template {
        val e = holder.engine
        val items = holder.engine?.nearby ?: emptyList()
        val busy = holder.engine?.nearbyBusy ?: false
        val b = ListTemplate.Builder().setTitle(title).setHeaderAction(Action.BACK)
        if (busy && items.isEmpty()) return b.setLoading(true).build()
        val limit = try { carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST) } catch (x: Exception) { 6 }
        val list = ItemList.Builder().setNoItemsMessage(L.t("Nothing found nearby"))
        for (n in items.take(limit.coerceIn(1, 12))) {
            val r = Row.Builder().setTitle(n.place.name).setImage(rowIcon(carContext, iconRes, tint))
            val bits = ArrayList<String>()
            if (!n.place.web.startsWith("folder:")) bits += com.headunit.launcher.Geo.fmtDist(n.dist, e?.imperial ?: true)
            // (the local way: 139.9p, €1.789, 189.9c; it always said "p" before, even for euro prices)
            n.price?.let { bits += com.headunit.launcher.fmtFuelPrice(it, n.unit) }
            if (n.free.isNotBlank()) bits += n.free
            if (n.detourMin >= 0) bits += "+${n.detourMin} min"
            if (n.extra.isNotBlank()) bits += n.extra
            r.addText(bits.joinToString(" · "))
            if (n.place.sub.isNotBlank()) r.addText(n.place.sub)
            r.setOnClickListener {
                val en = holder.engine ?: return@setOnClickListener
                // one of your folders: its own list (the car's back button comes back here)
                if (n.place.web.startsWith("folder:")) {
                    screenManager.push(NearbyScreen(carContext, holder, kind.substringBefore(':') + ":" + n.place.web.removePrefix("folder:")))
                    return@setOnClickListener
                }
                // on a route it becomes a stop (as in the app); otherwise it's where you're going
                if (en.route != null && !en.previewing) en.addStop(n.place) else en.startRoute(n.place)
                screenManager.popToRoot()
            }
            list.addItem(r.build())
        }
        return b.setSingleList(list.build()).build()
    }
}

/**
 * Quick settings on the car: voice guidance, day / night, what shows on the map and the route options, without picking up the phone.
 * They're the app's own settings (the phone shows the same).
 */
class CarSettingsScreen(carContext: CarContext, private val holder: CarHolder, private val page: Int = 0, private val onTheme: () -> Unit) : Screen(carContext) {
    private val prefs = Prefs(carContext.applicationContext)

    private fun toggleRow(title: String, key: String, def: Boolean, sub: String?, after: (Boolean) -> Unit = {}): Row {
        val r = Row.Builder().setTitle(L.t(title))
        if (sub != null) r.addText(L.t(sub))
        r.setToggle(Toggle.Builder { on -> prefs.put(key, on); after(on); invalidate() }.setChecked(prefs.b(key, def)).build())
        return r.build()
    }

    override fun onGetTemplate(): Template {
        val e = holder.engine
        val limit = try { carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST) } catch (x: Exception) { 6 }
        // most needed first: some cars only allow a few rows in a list, so what doesn't fit goes on a second page ("More settings")
        // rather than being left off (the last rows used to be cut off altogether on those cars)
        val rows = ArrayList<Row>()
        // ending the route (Android Auto's Stop button is left out when the trip card's red button is there; this one can be
        // reached with a rotary knob too)
        if (e != null && e.route != null && !e.previewing) rows += Row.Builder().setTitle(L.t("End route"))
            .setOnClickListener { e.endRoute(); screenManager.popToRoot() }.build()
        // a stop on the way (searched for with the car's keyboard or voice; what's picked is added, keeping where you're going)
        if (e != null && e.driving()) rows += Row.Builder().setTitle(L.t("Add a stop"))
            .addText(if (e.via.isEmpty()) L.t("Search for a place on the way") else e.via.joinToString(" › ") { it.name })
            .setOnClickListener { screenManager.push(SearchScreen(carContext, holder, mode = "stop")) }.build()
        // the whole route at a glance (back to following you after a while)
        if (e != null && e.route != null && !e.previewing) rows += Row.Builder().setTitle(L.t("Route overview"))
            .addText(L.t("See the whole route"))
            .setOnClickListener { e.overview(); screenManager.pop() }.build()
        // other ways to go while navigating: shown on the map in grey with how they compare (tap one, or Switch on the card)
        if (e != null && e.route != null && !e.previewing) rows += Row.Builder().setTitle(L.t("Other routes"))
            .addText(L.t("Show other ways to go on the map"))
            .setOnClickListener { screenManager.push(AltRoutesScreen(carContext, holder)) }.build()
        // 2D / 3D (its button gives way to Settings while navigating)
        // (the title stays the same whichever it is: Android Auto only keeps its place in a list when no row's title changes,
        // and went back to the top of the list after this one was tapped)
        if (e != null) rows += Row.Builder().setTitle(L.t("View"))
            .addText((if (e.tilt3d) "3D · " else "2D · ") + L.t(if (e.tilt3d) "Tap to switch to 2D" else "Tap to switch to 3D"))
            .setOnClickListener { e.toggleTilt(); invalidate() }.build()
        val sm = e?.soundMode ?: prefs.i(PreferenceKeys.SOUND_MODE, 0)
        rows += Row.Builder().setTitle(L.t("Voice guidance"))
            .addText(L.t(listOf("All directions and alerts", "Alerts only (cameras, limits, hazards)", "Off")[sm.coerceIn(0, 2)]))
            .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, when (sm) { 0 -> R.drawable.ic_car_sound_on; 1 -> R.drawable.ic_car_sound_alerts; else -> R.drawable.ic_car_sound_off })).build())
            .setOnClickListener { if (e != null) e.cycleSound() else prefs.put(PreferenceKeys.SOUND_MODE, (sm + 1) % 3); invalidate() }.build()
        // north up or turning with you (GitHub #39)
        val nu = e?.northUp ?: prefs.b(PreferenceKeys.NORTH_UP_CAR, false)
        rows += Row.Builder().setTitle(L.t("Map direction"))
            .addText(L.t(if (nu) "North up · tap to turn with you" else "Turns with you · tap for North up"))
            .setOnClickListener { if (e != null) e.toggleNorthUp() else prefs.put(PreferenceKeys.NORTH_UP_CAR, !nu); invalidate() }.build()
        val th = prefs.s(PreferenceKeys.CARTHEME, "car")
        rows += Row.Builder().setTitle(L.t("Day and night"))
            .addText(L.t(when (th) { "day" -> "Always day"; "night" -> "Always night"; "phone" -> "Same as your phone"; "sun" -> "Sun times"; else -> "Follows the car" }))
            .setOnClickListener {
                prefs.put(PreferenceKeys.CARTHEME, when (th) { "car" -> "sun"; "sun" -> "phone"; "phone" -> "day"; "day" -> "night"; else -> "car" })
                onTheme(); invalidate()
            }.build()
        val reroute: (Boolean) -> Unit = { holder.engine?.rerouteForSettings() }
        rows += toggleRow("Avoid motorways", "avoid_motorway", false, null, reroute)
        rows += toggleRow("Avoid toll roads", "avoid_toll", false, null, reroute)
        rows += toggleRow("Speed cameras", "cameras", true, "Show and warn")
        rows += toggleRow("Traffic lights", "signals", true, "On your route") { on -> try { holder.map?.signalsEnabled(on) } catch (x: Exception) { } }
        rows += toggleRow("Avoid ferries", "avoid_ferry", false, null, reroute)
        // Android Auto's templated UI does not expose a native continuous slider here,
        // so these step through sensible values on each tap (the same settings as the app's Settings > Android Auto)
        val uiScale = prefs.i(PreferenceKeys.CARSCALE, 100).coerceIn(40, 300)
        rows += Row.Builder().setTitle(L.t("Overall size"))
            .addText(L.t("{0}% · tap to cycle from 40% to 300%", uiScale))
            .setOnClickListener {
                val levels = listOf(40, 60, 80, 100, 120, 140, 160, 180, 200, 225, 250, 275, 300)
                val next = levels[(levels.indexOf(uiScale).takeIf { it >= 0 } ?: 3).let { (it + 1) % levels.size }]
                prefs.put(PreferenceKeys.CARSCALE, next)
                invalidate()
            }.build()
        // the car screen's own arrow size (it used to change the phone's arrow size too)
        val arrowSize = prefs.i(PreferenceKeys.CAR_ARROW_SIZE, 100).coerceIn(30, 180)
        rows += Row.Builder().setTitle(L.t("Arrow size"))
            .addText(L.t("{0}% · tap to change (30% to 180%)", arrowSize))
            .setOnClickListener {
                val levels = listOf(30, 45, 60, 80, 100, 120, 140, 160, 180)
                val next = levels[(levels.indexOf(arrowSize).takeIf { it >= 0 } ?: 2).let { (it + 1) % levels.size }]
                prefs.put(PreferenceKeys.CAR_ARROW_SIZE, next)
                try { holder.map?.setArrowScale(prefs.carArrowScale()) } catch (_: Exception) { }
                invalidate()
            }.build()
        // where the speed sign sits on the car screen (the same setting as the app's Settings > Android Auto)
        val spPos = prefs.carSpeedSpot()
        rows += Row.Builder().setTitle(L.t("Speed sign position"))
            .addText(L.t(when (spPos) { "middle" -> "Under the turn card"; "top" -> "Top right"; "bl" -> "Bottom left"; else -> "Bottom right" }) + L.t(" · tap to change"))
            // (a short car screen has no room top right for the ring, between Android Auto's own buttons: it's only offered there
            // with the compact speedometer)
            .setOnClickListener {
                prefs.setCarSpeedSpot(when (spPos) { "bl" -> "br"; "br" -> "middle"; "middle" -> "top"; else -> "bl" }); invalidate()
            }.build()
        // the ring (or block) as in the app, or the small dark pill
        val spStyle = prefs.s(PreferenceKeys.CAR_SPEED_STYLE, "app")
        rows += Row.Builder().setTitle(L.t("Speedometer style"))
            .addText(L.t(if (spStyle == "compact") "Compact" else "Same as the app") + L.t(" · tap to change"))
            .setOnClickListener { prefs.put(PreferenceKeys.CAR_SPEED_STYLE, if (spStyle == "compact") "app" else "compact"); invalidate() }.build()
        // the arrow stays to the side when the cards on the left fold away (GitHub), rather than moving back to the middle
        rows += toggleRow("Keep the arrow to the side", PreferenceKeys.ARROW_SIDE, false, "When the cards fold away, instead of moving to the middle")
        // 12 or 24-hour times (the same setting as the app's Settings > Units)
        val clock = prefs.s(PreferenceKeys.CLOCK, "auto")
        rows += Row.Builder().setTitle(L.t("Clock"))
            .addText(L.t(when (clock) { "24" -> "24-hour"; "12" -> "12-hour"; else -> "Automatic (as the phone)" }) + L.t(" · tap to change"))
            .setOnClickListener { prefs.put(PreferenceKeys.CLOCK, when (clock) { "auto" -> "24"; "24" -> "12"; else -> "auto" }); invalidate() }.build()
        // the map on the screen behind the steering wheel, on cars that offer it (a second map for the phone to draw)
        val cluster = prefs.b(PreferenceKeys.CLUSTER_MAP, true)
        rows += Row.Builder().setTitle(L.t("Map on the driver's display"))
            .addText(L.t(if (cluster) "On (on cars that offer it) · tap to turn off" else "Off · tap to turn on"))
            .setOnClickListener { prefs.put(PreferenceKeys.CLUSTER_MAP, !cluster); invalidate() }.build()
        if (cluster) {
            val cz = prefs.i(PreferenceKeys.CLUSTER_ZOOM, 0).coerceIn(-2, 1)
            rows += Row.Builder().setTitle(L.t("Driver's display zoom"))
                .addText(L.t(when (cz) { 1 -> "Closer"; -1 -> "Further out"; -2 -> "Furthest out"; else -> "Normal" }) + L.t(" · tap to change"))
                .setOnClickListener { prefs.put(PreferenceKeys.CLUSTER_ZOOM, when (cz) { 0 -> -1; -1 -> -2; -2 -> 1; else -> 0 }); invalidate() }.build()
        }
        val max = limit.coerceIn(2, 12)
        val list = ItemList.Builder()
        val start = page * (max - 1)
        val rest = rows.drop(start)
        if (rest.size > max) {
            for (r in rest.take(max - 1)) list.addItem(r)
            list.addItem(Row.Builder().setTitle(L.t("More settings")).addText(L.t("{0} more", rest.size - (max - 1)))
                .setBrowsable(true).setOnClickListener { screenManager.push(CarSettingsScreen(carContext, holder, page + 1, onTheme)) }.build())
        } else for (r in rest) list.addItem(r)
        return ListTemplate.Builder().setTitle(L.t(if (page == 0) "Settings" else "More settings")).setHeaderAction(Action.BACK).setSingleList(list.build()).build()
    }
}
