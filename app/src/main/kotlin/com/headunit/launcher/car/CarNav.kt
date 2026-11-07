package com.headunit.launcher.car

import com.headunit.launcher.PreferenceKeys
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
                when {
                    p != null -> e.startRoute(Place(t?.name ?: "Destination", "From Google Assistant", p))
                    q.equals("home", true) && e.places.slot("home") != null -> e.startRoute(e.places.slot("home")!!)
                    q.equals("work", true) && e.places.slot("work") != null -> e.startRoute(e.places.slot("work")!!)
                    q.isNotEmpty() -> ms.screenManager.push(SearchScreen(carContext, holder, q, autoGo = true))
                    else -> try { androidx.car.app.CarToast.makeText(carContext, "Couldn't read a place from that", androidx.car.app.CarToast.LENGTH_LONG).show() } catch (x: Exception) { }
                }
            }
        }.apply { isDaemon = true; name = "car-nav-intent" }
        pendingNavWait = worker
        worker.start()
    }

    /** Night colours on the car screen: follow the car, or always day / always night (quick settings on the car). */
    private fun carDark(): Boolean = when (Prefs(carContext.applicationContext).s(PreferenceKeys.CARTHEME, "car")) {
        "day" -> false
        "night" -> true
        else -> carContext.isDarkMode
    }

    private fun applyTheme() {
        val d = carDark()
        holder.engine?.applyStyle(d)
        if (::surface.isInitialized) surface.dark.value = d
    }

    override fun onCreateScreen(intent: Intent): Screen {
        holder = CarHolder(carContext)
        val screen = MapScreen(carContext, holder)
        mapScreen = screen
        surface = CarSurface(carContext) { map -> startEngine(map) }
        surface.actions = object : com.headunit.launcher.ui.CarActions {
            override fun search() { screen.screenManager.push(SearchScreen(carContext, holder)) }
            override fun places() { screen.screenManager.push(PlacesScreen(carContext, holder)) }
            override fun nearby(kind: String) { screen.screenManager.push(NearbyScreen(carContext, holder, kind)) }
            override fun settings() { screen.screenManager.push(CarSettingsScreen(carContext, holder) { applyTheme() }) }
        }
        screen.openSettings = { screen.screenManager.push(CarSettingsScreen(carContext, holder) { applyTheme() }) }
        try { carContext.getCarService(AppManager::class.java).setSurfaceCallback(surface) } catch (e: Exception) { }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                screen.stop()
                pendingNavWaitToken++
                pendingNavWait?.interrupt()
                pendingNavWait = null
                // unplugged: the car's engine stops, but a trip the phone is also following carries on there (with its voice back)
                if (ClusterLink.engine === holder.engine) ClusterLink.engine = null
                try { holder.engine?.shutdown() } catch (e: Exception) { }
                try { if (!com.headunit.launcher.RouteSync.anyGuiding()) NavService.stop(carContext.applicationContext) } catch (e: Exception) { }
                CarTicks.tick = null
                surface.release()
                holder.engine = null; holder.map = null
            }
        })
        pendingNav = intent
        return screen
    }

    /** The map is on the car screen: start an engine for it, with the same settings as the app on the phone. */
    private fun startEngine(map: MapBinding) {
        val app = carContext.applicationContext
        val prefs = Prefs(app)
        map.setTheme("immersive")
        map.setArrowScale((prefs.s(PreferenceKeys.ARROW_PERCENT, "240").toIntOrNull() ?: 240) / 100f)
        // the same map look and locator as on the phone (your own picture isn't used on the car screen: the 3D arrow instead)
        val (loc, locCol) = prefs.locatorSpec()
        if (loc == "custom") map.setLocator("arrow", prefs.s(PreferenceKeys.ARROW_COLOUR, "#F2F2F6")) else map.setLocator(loc, locCol)
        // Keep the locator proportionate to the more compact Android Auto overlay sizing.
        // Android Auto uses a smaller map surface than the main head-unit app. Keep the
        // user preference, but normalise it so the 240% main-app default doesn't create an enormous car-screen locator.
        map.setArrowScale(((prefs.s(PreferenceKeys.ARROW_PERCENT, "240").toIntOrNull() ?: 240) / 100f).coerceIn(1f, 5f) * 0.9f * (prefs.i(PreferenceKeys.CAR_ARROW_SIZE, 100).coerceIn(30, 180) / 100f))
        map.setMapDetail(prefs.mapDetail())
        map.setTrafficGreen(prefs.b(PreferenceKeys.TRAFFIC_GREEN, false))
        map.setCarModel(prefs.s(PreferenceKeys.CAR_MODEL, "saloon"))
        map.setRoadNameScale(prefs.i(PreferenceKeys.CAR_ROAD_NAME_SIZE, 100).coerceIn(70, 200) / 100f)
        val mapCols = prefs.s(PreferenceKeys.MAP_COLOURS, "mapbox")
        map.setColours(if (mapCols == "custom") prefs.s(PreferenceKeys.CUSTOM_BASE, "mapbox") else mapCols)
        map.setCustom(mapCols == "custom", prefs.s(PreferenceKeys.CUSTOM_DAY, ""), prefs.s(PreferenceKeys.CUSTOM_NIGHT, ""), prefs.i(PreferenceKeys.CUSTOM_ROUTE_W, 100))
        map.setCameraSize(prefs.i(PreferenceKeys.CAM_SIZE, 100))
        map.setFlags(prefs.b(PreferenceKeys.FLAGS_3D, true))
        map.setHillShading(prefs.b(PreferenceKeys.HILLSHADE, false))
        map.setCarTint(prefs.s(PreferenceKeys.CAR_TINT, "#8A5AD0"), prefs.i(PreferenceKeys.CAR_TINT_STR, 40))
        map.setLightOpts(com.headunit.launcher.lightOptsFrom(prefs))
        map.setLogoCorner(prefs.s(PreferenceKeys.MAPBOX_LOGO, "tr"))
        map.setExtras(prefs.b(PreferenceKeys.FACADES, true), prefs.b(PreferenceKeys.LANDMARKS3D, true), prefs.b(PreferenceKeys.PATHS, true), prefs.b(PreferenceKeys.TERRAIN, false),
            false, prefs.b(PreferenceKeys.FURNITURE3D, true), prefs.b(PreferenceKeys.CLEARVIEW, true))
        val engine = NavEngine(app, prefs, map)
        // the car screen does the talking while it's connected, and shares the trip with the phone
        engine.isCar = true
        ClusterLink.engine = engine
        // (the app's own short messages don't show on the car screen: a tapped number-plate camera says what it is with Android Auto's)
        map.onAlprTap = { who -> try { androidx.car.app.CarToast.makeText(carContext, "Number-plate camera" + (if (who.isNotBlank()) " · $who" else ""), androidx.car.app.CarToast.LENGTH_SHORT).show() } catch (e: Exception) { } }
        // back in the car: the "Your car" spot on the phone isn't needed any more
        try { prefs.put(PreferenceKeys.PARKED_SPOT, "") } catch (e: Exception) { }
        holder.map = map; holder.engine = engine
        surface.engine = engine
        CarTicks.tick = { surface.tick(); try { holder.map?.setWeatherCode(holder.engine?.weather?.code) } catch (e: Exception) { } }
        applyTheme()
        try { engine.startLocation() } catch (e: Exception) { }
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
        val perUnit = (short / 1050f).coerceIn(dens * 0.68f, dens * 0.98f)
        uiScale.value = perUnit / dens
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
            // Android Auto: keep street-lamp models and their light pools off to reduce visual clutter
            // and rendering work on the car display. This is scoped to the car map; the phone app's
            // Street lamps preference remains unchanged.
            m.lampsEnabled(false)
            // a touch thicker route line on the car screen, where it looked thin against the buildings
            m.routeBoost = 1.2f
            val root = FrameLayout(p.context)
            root.addView(m.mapView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
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

    override fun onStableAreaChanged(stableArea: Rect) {}

    override fun onScroll(distanceX: Float, distanceY: Float) {
        val m = map ?: return
        m.onUserGesture?.invoke()
        try { m.panBy(distanceX, distanceY) } catch (e: Exception) { }
    }

    override fun onFling(velocityX: Float, velocityY: Float) {}

    // a tap on the car screen (newer cars): the app's own cards and buttons react, as in the app
    override fun onClick(x: Float, y: Float) {
        try {
            // the app's cards and buttons first, then the map itself (alternative route cards and lines, skipping the fly-through)
            if (!com.headunit.launcher.ui.CarTaps.hit(x, y)) map?.tapAt(android.graphics.PointF(x, y))
        } catch (e: Exception) { }
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        val m = map ?: return
        m.onUserGesture?.invoke()
        // a double tap comes as a negative factor: zoom in one step
        val dz = if (scaleFactor <= 0f) 1.0 else Math.log(scaleFactor.toDouble()) / Math.log(2.0)
        try { m.zoomAround(dz, focusX, focusY) } catch (e: Exception) { }
    }

    /** Called about once a second (the cards update themselves). */
    fun tick() {}

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
                    try { NavService.start(holder.carContext.applicationContext, "Navigating to ${e.dest?.name ?: "your destination"}") } catch (x: Exception) { }
                } else {
                    nav?.navigationEnded()
                    // (the phone may still be guiding a trip of its own: then its background GPS service stays on)
                    try { if (!com.headunit.launcher.RouteSync.anyGuiding()) NavService.stop(holder.carContext.applicationContext) } catch (x: Exception) { }
                }
            } catch (x: Exception) { }
        }
        if (autoDrive && guiding && e.simMode == 0) e.cycleSim()
        val t = e.turn; val eta = e.eta
        // Android Auto's buttons only change with the state (rebuilding them every second while the distance counts down made the
        // host redo its layout for nothing); the turn and distance go to the car's displays through updateTrip
        val sig = "${guiding}|${e.previewing}|${e.active}|${e.routes.size}|${e.busy}|${e.simMode}|${e.faster?.savedMin}|${e.soundMode}"
        if (sig != lastSig) { lastSig = sig; invalidate() }
        val tsig = "${t?.maneuver}|${t?.street}|${t?.exit}|${t?.dist?.let { Math.round(it / 10) }}|${eta?.secs?.let { Math.round(it / 30) }}|${eta?.dist?.let { Math.round(it / 100) }}|${System.identityHashCode(t?.lanes)}"
        if (guiding && t != null && eta != null && tsig != lastTrip) {
            lastTrip = tsig
            try { nav?.updateTrip(trip(e, t, eta)) } catch (x: Exception) { }
            try { NavService.update(carContext.applicationContext, t, eta, e.imperial, !e.mirrored, e.prefs.b(PreferenceKeys.TURN_NOTIF, true)) } catch (x: Exception) { }
        }
        CarTicks.tick?.invoke()
    }

    private fun icon(res: Int) = CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build()

    /** Report (police, crash, hazard...): opens the "Report here" screen. It has the Search button's place. */
    private fun reportAction(): Action = Action.Builder().setTitle("Report")
        .setOnClickListener { screenManager.push(ReportScreen(carContext, holder)) }.build()

    override fun onGetTemplate(): Template {
        val e = holder.engine
        val guiding = e != null && e.route != null && !e.previewing
        val strip = ActionStrip.Builder()
        val previewing = e != null && e.route != null && e.previewing
        if (previewing) {
            // the route choice (drawn by the app on the map): start, try the next route, or cancel
            strip.addAction(Action.Builder().setTitle("Start").setOnClickListener { e?.beginNavigation(); invalidate() }.build())
            if ((e?.routes?.size ?: 0) > 1) strip.addAction(Action.Builder().setTitle("Next route").setOnClickListener {
                e?.let { en -> en.selectRoute((en.active + 1) % en.routes.size.coerceAtMost(3)) }; invalidate()
            }.build())
            strip.addAction(Action.Builder().setTitle("Cancel").setOnClickListener { e?.endRoute(); invalidate() }.build())
            return NavigationTemplate.Builder().setActionStrip(strip.build()).build()
        }
        // voice guidance: all / alerts only / off (the same as the app's sound button)
        val soundIcon = when (e?.soundMode ?: 0) { 0 -> R.drawable.ic_car_sound_on; 1 -> R.drawable.ic_car_sound_alerts; else -> R.drawable.ic_car_sound_off }
        val sound = Action.Builder().setIcon(icon(soundIcon)).setOnClickListener { e?.cycleSound(); invalidate() }.build()
        // 2D / 3D (as the app's own button): the title says which view a tap switches to
        val view = Action.Builder().setTitle(if (e?.tilt3d != false) "2D" else "3D").setOnClickListener { e?.toggleTilt(); invalidate() }.build()
        val demoOn = e != null && (e.prefs.b(PreferenceKeys.DEMO, false) || e.simMode != 0)
        val fr = e?.faster
        if (guiding && fr != null) {
            // a faster route turned up: switch or keep (also on the map card, for cars that pass taps on)
            strip.addAction(Action.Builder().setTitle("Switch −${fr.savedMin} min").setOnClickListener { e?.acceptFaster(); invalidate() }.build())
            strip.addAction(Action.Builder().setTitle("Keep").setOnClickListener { e?.dismissFaster(); invalidate() }.build())
            strip.addAction(sound)
        } else if (guiding) {
            // only four fit: Settings stays while navigating (2D / 3D is the first row in it)
            strip.addAction(Action.Builder().setTitle("Stop").setOnClickListener { e?.endRoute(); invalidate() }.build())
            // the demo drive (for trying it out parked; only with Demo switched on in Settings, as in the app): starts it, speeds it
            // up, stops it. It has Report's place while it's on
            if (demoOn) strip.addAction(Action.Builder().setTitle(when (e?.simMode ?: 0) { 0 -> "Demo"; 1 -> "Faster"; else -> "End demo" })
                .setOnClickListener { e?.cycleSim(); invalidate() }.build())
            strip.addAction(sound)
            if (!demoOn) strip.addAction(reportAction())
            strip.addAction(Action.Builder().setIcon(icon(R.drawable.ic_car_settings)).setOnClickListener { openSettings() }.build())
        } else {
            // Report has the Search button's place, and Search is the first row in Places. Every car gets Places: a rotary knob or
            // the car's buttons can only reach Android Auto's own buttons, never the search bar and chips the app draws on the map
            // (cars that pass taps on still have those too)
            strip.addAction(reportAction())
            strip.addAction(Action.Builder().setTitle("Places").setOnClickListener { screenManager.push(PlacesScreen(carContext, holder)) }.build())
            strip.addAction(view)
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
        tb.addDestination(Destination.Builder().setName(d?.name ?: "Destination").setAddress(d?.sub ?: "").build(), est)
        if (e.roadNow.isNotBlank()) tb.setCurrentRoad(e.roadNow)
        return tb.build()
    }

    companion object {
        fun distance(m: Double, imperial: Boolean): Distance = if (imperial) {
            val mi = m / 1609.344
            if (mi >= 0.25) Distance.create(Math.round(mi * 10) / 10.0, Distance.UNIT_MILES)
            else Distance.create((Math.round(m * 1.09361 / 10.0) * 10).toDouble(), Distance.UNIT_YARDS)
        } else {
            if (m >= 1000) Distance.create(Math.round(m / 100.0) / 10.0, Distance.UNIT_KILOMETERS)
            else Distance.create((Math.round(m / 10.0) * 10).toDouble(), Distance.UNIT_METERS)
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
            Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW, Maneuver.TYPE_ROUNDABOUT_ENTER_CW -> R.drawable.ic_man_roundabout_cw
            Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW, Maneuver.TYPE_ROUNDABOUT_ENTER_CCW -> R.drawable.ic_man_roundabout_ccw
            Maneuver.TYPE_DESTINATION, Maneuver.TYPE_DESTINATION_LEFT, Maneuver.TYPE_DESTINATION_RIGHT -> R.drawable.ic_man_destination
            else -> R.drawable.ic_man_straight
        }

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

        fun step(e: NavEngine, t: TurnInfo, ctx: CarContext): Step {
            val left = !e.mirrored
            val m = t.maneuver.uppercase()
            val type = when {
                m.startsWith("ROUNDABOUT") -> if (t.exit > 0) (if (left) Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW else Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW)
                                               else (if (left) Maneuver.TYPE_ROUNDABOUT_ENTER_CW else Maneuver.TYPE_ROUNDABOUT_ENTER_CCW)
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
            val mb = Maneuver.Builder(type).setIcon(CarIcon.Builder(IconCompat.createWithResource(ctx, maneuverIcon(type))).build())
            if (type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW || type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW) mb.setRoundaboutExitNumber(t.exit.coerceAtLeast(1))
            val what = when {
                type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW || type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW -> "Take exit ${t.exit}"
                m.startsWith("ARRIVE") -> "Arrive"
                t.tag.isNotBlank() -> t.tag
                else -> m.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
            }
            val cue = if (t.street.isNotBlank()) "$what · ${t.street}" else what
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
        val list = ItemList.Builder().setNoItemsMessage(if (e == null) "Starting the map…" else "No saved or recent places yet")
        // Search first: on cars that don't pass taps on to the map, this is the way in to it (the Search button is Report now)
        var shownRows = 0
        if (e != null) {
            list.addItem(Row.Builder().setTitle("Search").addText("Type or say where to")
                .setImage(rowIcon(carContext, R.drawable.ic_car_search, 0xFF9CA4BF.toInt()))
                .setOnClickListener { screenManager.push(SearchScreen(carContext, holder)) }.build())
            shownRows = 1
        }
        if (e != null) {
            // (label, place, icon, colour) in the app's colours: Home blue, Work amber, saved teal, favourites gold, recent grey
            val all = ArrayList<Triple<String, Place, Pair<Int, Int>>>()
            e.places.slot("home")?.let { all += Triple("Home", it, R.drawable.ic_car_home to 0xFF4C8DFF.toInt()) }
            e.places.slot("work")?.let { all += Triple("Work", it, R.drawable.ic_car_work to 0xFFF29900.toInt()) }
            for (c in e.places.customs()) all += Triple(c.label, c.place, R.drawable.ic_car_star to 0xFF12A4B4.toInt())
            for (p in e.places.favourites()) all += Triple(p.name, p, R.drawable.ic_car_star to 0xFFF2C230.toInt())
            for (p in e.places.recents()) all += Triple(p.name, p, R.drawable.ic_car_history to 0xFF9CA4BF.toInt())
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
        return ListTemplate.Builder().setTitle("Places").setHeaderAction(Action.BACK).setSingleList(list.build()).build()
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
        val b = ListTemplate.Builder().setTitle("Other routes").setHeaderAction(Action.BACK)
        if (alts.isEmpty()) {
            if (System.currentTimeMillis() - asked < 20_000L) return b.setLoading(true).build()
            return b.setSingleList(ItemList.Builder().setNoItemsMessage("No other routes right now").build()).build()
        }
        val list = ItemList.Builder()
        for ((i, a) in alts.withIndex()) {
            val diff = when { a.diffMin > 0 -> "${a.diffMin} min slower"; a.diffMin < 0 -> "${-a.diffMin} min faster"; else -> "About the same time" }
            // (the first named road after it leaves your route)
            val split = a.route.cum.getOrNull(a.divIdx) ?: 0.0
            val via = a.route.steps.firstOrNull { it.off > split && it.street.isNotBlank() }?.street
            list.addItem(Row.Builder().setTitle(diff).apply { if (via != null) addText("Via $via") }
                .setOnClickListener { e?.switchToAlt(i); screenManager.pop() }.build())
        }
        return b.setSingleList(list.build()).build()
    }
}

class ReportScreen(carContext: CarContext, private val holder: CarHolder) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val kinds = listOf(
            Triple("police", "Police", R.drawable.ic_car_police to 0xFF8AB4F8.toInt()),
            Triple("accident", "Crash", R.drawable.ic_car_crash to 0xFFF28B82.toInt()),
            Triple("hazard", "Hazard", R.drawable.ic_car_report to 0xFFFBBC04.toInt()),
            Triple("breakdown", "Broken down", R.drawable.ic_car_breakdown to 0xFFFCAD70.toInt()),
            Triple("mobilecam", "Mobile camera", R.drawable.ic_car_mobilecam to 0xFF8AB4F8.toInt()),
        )
        val list = ItemList.Builder()
        for ((kind, label, ic) in kinds) list.addItem(
            GridItem.Builder().setTitle(label).setImage(rowIcon(carContext, ic.first, ic.second), GridItem.IMAGE_TYPE_LARGE)
                .setOnClickListener { send(kind) }.build()
        )
        // Search here too: Report has the Search button's place, and this is the way to search while navigating (a stop on the way)
        list.addItem(GridItem.Builder().setTitle("Search").setImage(rowIcon(carContext, R.drawable.ic_car_search, 0xFFE8EAED.toInt()), GridItem.IMAGE_TYPE_LARGE)
            .setOnClickListener { screenManager.push(SearchScreen(carContext, holder)) }.build())
        return GridTemplate.Builder().setTitle("Report here").setHeaderAction(Action.BACK).setSingleList(list.build()).build()
    }

    private fun send(kind: String) {
        val e = holder.engine
        val msg = when {
            e == null -> "The map is still starting"
            e.fix == null -> "Waiting for GPS"
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
class SearchScreen(carContext: CarContext, private val holder: CarHolder, initial: String = "", private var autoGo: Boolean = false) : Screen(carContext) {
    private var query = initial
    private var shown: List<Place> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    /** The results that were there before this search (so old ones aren't taken for the answer). */
    private val before: List<Place>? = holder.engine?.results
    private val askedAt = android.os.SystemClock.uptimeMillis()
    private val poll = object : Runnable {
        override fun run() {
            val e = holder.engine
            val r = e?.results ?: emptyList()
            if (r !== shown) { shown = r; invalidate() }
            if (autoGo) {
                if (e != null && r !== before && r.isNotEmpty()) {
                    autoGo = false
                    val p = r.first()
                    e.startRoute(p)
                    try { androidx.car.app.CarToast.makeText(carContext, "Going to ${p.name}", androidx.car.app.CarToast.LENGTH_LONG).show() } catch (x: Exception) { }
                    try { screenManager.popToRoot() } catch (x: Exception) { }
                    return
                }
                if (android.os.SystemClock.uptimeMillis() - askedAt > 10_000L) autoGo = false
            }
            handler.postDelayed(this, 500)
        }
    }

    private val typed = Runnable { if (query.length >= 3) holder.engine?.search(query) }

    init {
        if (initial.length >= 2) holder.engine?.search(initial)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { handler.post(poll) }
            override fun onStop(owner: LifecycleOwner) { handler.removeCallbacks(poll); handler.removeCallbacks(typed) }
        })
    }

    override fun onGetTemplate(): Template {
        val e = holder.engine
        val limit = try { carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST) } catch (x: Exception) { 6 }
        val list = ItemList.Builder().setNoItemsMessage(if (query.length < 3) "Type a place, address or postcode" else "Searching…")
        if (query.length >= 3) for (p in shown.take(limit.coerceIn(1, 12))) {
            // a red pin, the name, and how far it is with the address under it (as in the app's results)
            val r = Row.Builder().setTitle(p.name).setImage(rowIcon(carContext, R.drawable.ic_car_pin, 0xFFEA4335.toInt()))
            val line = listOf(distFrom(e, p), p.sub).filter { it.isNotBlank() }.joinToString(" · ")
            if (line.isNotBlank()) r.addText(line)
            r.setOnClickListener { e?.startRoute(p); screenManager.popToRoot() }
            list.addItem(r.build())
        }
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            // searched once typing pauses (as in the app), not on every letter: each search is a request on your TomTom key
            override fun onSearchTextChanged(searchText: String) {
                query = searchText
                handler.removeCallbacks(typed)
                if (searchText.length >= 3) handler.postDelayed(typed, 500)
            }
            override fun onSearchSubmitted(searchText: String) { query = searchText; handler.removeCallbacks(typed); e?.search(searchText) }
        }).setHeaderAction(Action.BACK).setSearchHint("Search for a place").setInitialSearchText(query)
            .setShowKeyboardByDefault(query.isEmpty()).setItemList(list.build()).build()
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
            override fun onStart(owner: LifecycleOwner) { handler.post(poll) }
            override fun onStop(owner: LifecycleOwner) { handler.removeCallbacks(poll) }
            // the places drawn on the map go with the list (picking one already cleared them)
            override fun onDestroy(owner: LifecycleOwner) { holder.engine?.clearNearby() }
        })
    }

    private val title = when (kind) { "fuel" -> "Fuel"; "charging" -> "Charging"; "food" -> "Food"; "coffee" -> "Coffee"; "parking" -> "Parking"; "toilets" -> "Toilets"; else -> "Nearby" }
    private val iconRes = when (kind) { "fuel" -> R.drawable.ic_car_fuel; "charging" -> R.drawable.ic_car_charging; "food" -> R.drawable.ic_car_food; "coffee" -> R.drawable.ic_car_coffee; "parking" -> R.drawable.ic_car_parking; else -> R.drawable.ic_car_pin }
    private val tint = when (kind) { "fuel" -> 0xFFEA4335.toInt(); "charging" -> 0xFF34A853.toInt(); "food" -> 0xFFF29900.toInt(); "coffee" -> 0xFFA56BFF.toInt(); else -> 0xFF4C8DFF.toInt() }

    override fun onGetTemplate(): Template {
        val e = holder.engine
        val items = holder.engine?.nearby ?: emptyList()
        val busy = holder.engine?.nearbyBusy ?: false
        val b = ListTemplate.Builder().setTitle(title).setHeaderAction(Action.BACK)
        if (busy && items.isEmpty()) return b.setLoading(true).build()
        val limit = try { carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST) } catch (x: Exception) { 6 }
        val list = ItemList.Builder().setNoItemsMessage("Nothing found nearby")
        for (n in items.take(limit.coerceIn(1, 12))) {
            val r = Row.Builder().setTitle(n.place.name).setImage(rowIcon(carContext, iconRes, tint))
            val bits = ArrayList<String>()
            bits += com.headunit.launcher.Geo.fmtDist(n.dist, e?.imperial ?: true)
            // (the local way: 139.9p, €1.789, 189.9c; it always said "p" before, even for euro prices)
            n.price?.let { bits += com.headunit.launcher.fmtFuelPrice(it, n.unit) }
            if (n.free.isNotBlank()) bits += n.free
            if (n.detourMin >= 0) bits += "+${n.detourMin} min"
            if (n.extra.isNotBlank()) bits += n.extra
            r.addText(bits.joinToString(" · "))
            if (n.place.sub.isNotBlank()) r.addText(n.place.sub)
            r.setOnClickListener {
                val en = holder.engine ?: return@setOnClickListener
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
        val r = Row.Builder().setTitle(title)
        if (sub != null) r.addText(sub)
        r.setToggle(Toggle.Builder { on -> prefs.put(key, on); after(on); invalidate() }.setChecked(prefs.b(key, def)).build())
        return r.build()
    }

    override fun onGetTemplate(): Template {
        val e = holder.engine
        val limit = try { carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST) } catch (x: Exception) { 6 }
        // most needed first: some cars only allow a few rows in a list, so what doesn't fit goes on a second page ("More settings")
        // rather than being left off (the last rows used to be cut off altogether on those cars)
        val rows = ArrayList<Row>()
        // the whole route at a glance (back to following you after a while)
        if (e != null && e.route != null && !e.previewing) rows += Row.Builder().setTitle("Route overview")
            .addText("See the whole route")
            .setOnClickListener { e.overview(); screenManager.pop() }.build()
        // other ways to go while navigating: shown on the map in grey with how they compare (tap one, or Switch on the card)
        if (e != null && e.route != null && !e.previewing) rows += Row.Builder().setTitle("Other routes")
            .addText("Show other ways to go on the map")
            .setOnClickListener { screenManager.push(AltRoutesScreen(carContext, holder)) }.build()
        // 2D / 3D (its button gives way to Settings while navigating)
        if (e != null) rows += Row.Builder().setTitle(if (e.tilt3d) "View: 3D" else "View: 2D")
            .addText(if (e.tilt3d) "Tap to switch to 2D" else "Tap to switch to 3D")
            .setOnClickListener { e.toggleTilt(); invalidate() }.build()
        val sm = e?.soundMode ?: prefs.i(PreferenceKeys.SOUND_MODE, 0)
        rows += Row.Builder().setTitle("Voice guidance")
            .addText(listOf("All directions and alerts", "Alerts only (cameras, limits, hazards)", "Off")[sm.coerceIn(0, 2)])
            .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, when (sm) { 0 -> R.drawable.ic_car_sound_on; 1 -> R.drawable.ic_car_sound_alerts; else -> R.drawable.ic_car_sound_off })).build())
            .setOnClickListener { if (e != null) e.cycleSound() else prefs.put(PreferenceKeys.SOUND_MODE, (sm + 1) % 3); invalidate() }.build()
        // north up or turning with you (GitHub #39)
        val nu = e?.northUp ?: prefs.b(PreferenceKeys.NORTH_UP_CAR, false)
        rows += Row.Builder().setTitle("Map direction")
            .addText(if (nu) "North up · tap to turn with you" else "Turns with you · tap for North up")
            .setOnClickListener { if (e != null) e.toggleNorthUp() else prefs.put(PreferenceKeys.NORTH_UP_CAR, !nu); invalidate() }.build()
        val th = prefs.s(PreferenceKeys.CARTHEME, "car")
        rows += Row.Builder().setTitle("Day and night")
            .addText(when (th) { "day" -> "Always day"; "night" -> "Always night"; else -> "Follows the car" })
            .setOnClickListener {
                prefs.put(PreferenceKeys.CARTHEME, when (th) { "car" -> "day"; "day" -> "night"; else -> "car" })
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
        rows += Row.Builder().setTitle("Overall size")
            .addText("${uiScale}% · tap to cycle from 40% to 300%")
            .setOnClickListener {
                val levels = listOf(40, 60, 80, 100, 120, 140, 160, 180, 200, 225, 250, 275, 300)
                val next = levels[(levels.indexOf(uiScale).takeIf { it >= 0 } ?: 3).let { (it + 1) % levels.size }]
                prefs.put(PreferenceKeys.CARSCALE, next)
                invalidate()
            }.build()
        // the car screen's own arrow size (it used to change the phone's arrow size too)
        val arrowSize = prefs.i(PreferenceKeys.CAR_ARROW_SIZE, 100).coerceIn(30, 180)
        rows += Row.Builder().setTitle("Arrow size")
            .addText("${arrowSize}% · tap to change (30% to 180%)")
            .setOnClickListener {
                val levels = listOf(30, 45, 60, 80, 100, 120, 140, 160, 180)
                val next = levels[(levels.indexOf(arrowSize).takeIf { it >= 0 } ?: 2).let { (it + 1) % levels.size }]
                prefs.put(PreferenceKeys.CAR_ARROW_SIZE, next)
                try { holder.map?.setArrowScale(((prefs.s(PreferenceKeys.ARROW_PERCENT, "240").toIntOrNull() ?: 240) / 100f).coerceIn(1f, 5f) * 0.9f * (next / 100f)) } catch (_: Exception) { }
                invalidate()
            }.build()
        // where the speed sign sits on the car screen (the same setting as the app's Settings > Android Auto)
        val spPos = prefs.s(PreferenceKeys.SPEED_POS_CAR, "bottom")
        rows += Row.Builder().setTitle("Speed sign position")
            .addText(when (spPos) { "middle" -> "Middle"; "top" -> "Top"; else -> "Bottom" } + " · tap to change")
            .setOnClickListener { prefs.put(PreferenceKeys.SPEED_POS_CAR, when (spPos) { "bottom" -> "middle"; "middle" -> "top"; else -> "bottom" }); invalidate() }.build()
        // 12 or 24-hour times (the same setting as the app's Settings > Units)
        val clock = prefs.s(PreferenceKeys.CLOCK, "auto")
        rows += Row.Builder().setTitle("Clock")
            .addText(when (clock) { "24" -> "24-hour"; "12" -> "12-hour"; else -> "Automatic (as the phone)" } + " · tap to change")
            .setOnClickListener { prefs.put(PreferenceKeys.CLOCK, when (clock) { "auto" -> "24"; "24" -> "12"; else -> "auto" }); invalidate() }.build()
        // the map on the screen behind the steering wheel, on cars that offer it (a second map for the phone to draw)
        val cluster = prefs.b(PreferenceKeys.CLUSTER_MAP, true)
        rows += Row.Builder().setTitle("Map on the driver's display")
            .addText(if (cluster) "On (on cars that offer it) · tap to turn off" else "Off · tap to turn on")
            .setOnClickListener { prefs.put(PreferenceKeys.CLUSTER_MAP, !cluster); invalidate() }.build()
        val max = limit.coerceIn(2, 12)
        val list = ItemList.Builder()
        val start = page * (max - 1)
        val rest = rows.drop(start)
        if (rest.size > max) {
            for (r in rest.take(max - 1)) list.addItem(r)
            list.addItem(Row.Builder().setTitle("More settings").addText("${rest.size - (max - 1)} more")
                .setBrowsable(true).setOnClickListener { screenManager.push(CarSettingsScreen(carContext, holder, page + 1, onTheme)) }.build())
        } else for (r in rest) list.addItem(r)
        return ListTemplate.Builder().setTitle(if (page == 0) "Settings" else "More settings").setHeaderAction(Action.BACK).setSingleList(list.build()).build()
    }
}
