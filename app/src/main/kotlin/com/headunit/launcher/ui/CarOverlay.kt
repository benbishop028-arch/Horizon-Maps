package com.headunit.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Route
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.Geo
import com.headunit.launcher.L
import com.headunit.launcher.NavEngine
import com.headunit.launcher.PreferenceKeys
import com.headunit.launcher.carArrowScale
import com.headunit.launcher.hdRoadsWanted
import com.headunit.launcher.lightOptsFrom
import com.headunit.launcher.locatorSpec
import com.headunit.launcher.mapDetail
import com.headunit.launcher.mapSource

/**
 * The app's own screens on the Android Auto display, drawn over the car's map with the app's designs: alerts across the top, the
 * turn card (top left), hazard badges, speed and the trip card (bottom left), the route choice before setting off, and the arrival
 * summary. [scale] makes them the same size on the car screen as in the app (car screens have few pixels per inch for their size).
 * Nothing here can be tapped (Android Auto only lets its own buttons take touches), so the matching buttons are Android Auto's.
 */
@Composable
fun CarOverlay(engine: NavEngine, area: State<android.graphics.Rect?>, dark: State<Boolean>, scale: State<Float>, actions: CarActions, onCover: (Int) -> Unit = {}) {
    val base = LocalDensity.current
    // the car's map follows the settings as soon as they change (on the phone or in the car's own settings)
    CarLiveSettings(engine, dark)
    // [scale] is worked out from the car screen's size (see CarSurface); the Android Auto size setting adjusts it either way
    val user = engine.prefs.i(PreferenceKeys.CARSCALE, 100).coerceIn(40, 300) / 100f
    val textScale = engine.prefs.i(PreferenceKeys.CAR_TEXT_SIZE, 100).coerceIn(75, 150) / 100f
    CompositionLocalProvider(LocalDensity provides Density(base.density * scale.value * user, base.fontScale * textScale)) {
        // dark cards when the car says it's night, or when the map's light has gone dim (dusk) before the car switches
        // (not with "Always day" picked in the car's settings)
        VwTheme(dark.value || (engine.mapLooksDark && engine.prefs.s(PreferenceKeys.CARTHEME, "car") == "car")) {
            // the app's own palette and its see-through "glass" cards (Settings > Glass panels), as the map screen on the phone
            // has them (see ImmersiveNav): the car screen was left on the plain base colours with solid cards, so it looked
            // like a different app
            val plain = LocalVw.current
            val glass = engine.prefs.b(PreferenceKeys.GLASS, true)
            val v = remember(plain, glass) { navVw(plain).let { if (glass) glassVw(it) else it } }
            CompositionLocalProvider(LocalVw provides v) { CarScreen(engine, area.value, actions, onCover) }
        }
    }
}

/**
 * Runs [block] when any of [keys] changes after the first composition. The car's map is set up once with the settings as they
 * are when it starts (CarNavSession.startEngine), so only later changes need applying.
 */
@Composable
private fun OnChange(vararg keys: Any?, block: () -> Unit) {
    val now = keys.toList()
    // what was last applied: compared rather than counted, so a change isn't lost if an effect is cancelled before it runs
    val applied = remember { arrayOf(now) }
    LaunchedEffect(now) {
        if (applied[0] != now) {
            applied[0] = now
            try { block() } catch (e: Exception) { }
        }
    }
}

/**
 * Keeps the Android Auto map in step with the settings while it's connected, as the phone's own map is (see App.kt): the map
 * theme and detail, the locator, sizes and the 3D extras used to be read once when the car connected, so a change only showed
 * after reconnecting. The values mirror CarNavSession.startEngine, which applies them the first time.
 */
@Composable
private fun CarLiveSettings(engine: NavEngine, dark: State<Boolean>) {
    val prefs = engine.prefs
    val map = engine.map
    val (locStyle, locColor) = prefs.locatorSpec()
    OnChange(locStyle, locColor) { map.setLocator(locStyle, locColor) }
    val arrow = prefs.carArrowScale()
    OnChange(arrow) { map.setArrowScale(arrow) }
    val detail = prefs.mapDetail()
    OnChange(detail) { map.setMapDetail(detail) }
    val trafficGreen = prefs.b(PreferenceKeys.TRAFFIC_GREEN, false)
    OnChange(trafficGreen) { map.setTrafficGreen(trafficGreen) }
    val carModel = prefs.s(PreferenceKeys.CAR_MODEL, "saloon")
    OnChange(carModel) { map.setCarModel(carModel) }
    val roadName = prefs.i(PreferenceKeys.CAR_ROAD_NAME_SIZE, 100).coerceIn(70, 200)
    OnChange(roadName) { map.setRoadNameScale(roadName / 100f) }
    // the map theme (Custom: the theme it starts from, with its own colours on top)
    val mapCols = prefs.s(PreferenceKeys.MAP_COLOURS, "mapbox")
    val customOn = mapCols == "custom"
    val baseCols = if (customOn) prefs.s(PreferenceKeys.CUSTOM_BASE, "mapbox") else mapCols
    OnChange(baseCols) { map.setColours(baseCols) }
    val mapFont = prefs.s(PreferenceKeys.MAP_FONT, "")
    OnChange(mapFont) { map.setFont(mapFont) }
    val customDay = prefs.s(PreferenceKeys.CUSTOM_DAY, "")
    val customNight = prefs.s(PreferenceKeys.CUSTOM_NIGHT, "")
    val customW = prefs.i(PreferenceKeys.CUSTOM_ROUTE_W, 100)
    OnChange(customOn, customDay, customNight, customW) { map.setCustom(customOn, customDay, customNight, customW) }
    val camSize = prefs.i(PreferenceKeys.CAM_SIZE, 100)
    OnChange(camSize) { map.setCameraSize(camSize) }
    val flags3d = prefs.b(PreferenceKeys.FLAGS_3D, true)
    val hills = prefs.b(PreferenceKeys.HILLSHADE, false)
    OnChange(flags3d, hills) { map.setFlags(flags3d); map.setHillShading(hills) }
    val carTint = prefs.s(PreferenceKeys.CAR_TINT, "#8A5AD0")
    val carTintStr = prefs.i(PreferenceKeys.CAR_TINT_STR, 40)
    OnChange(carTint, carTintStr) { map.setCarTint(carTint, carTintStr) }
    val light = lightOptsFrom(prefs)
    OnChange(light.nightBright, light.moon, light.moonStr, light.shadows, light.sunReal, light.overcast, light.weatherLight) { map.setLightOpts(light) }
    val logoCorner = prefs.s(PreferenceKeys.MAPBOX_LOGO, "tr")
    OnChange(logoCorner) { map.setLogoCorner(logoCorner) }
    val xFacades = prefs.b(PreferenceKeys.FACADES, true); val xLand = prefs.b(PreferenceKeys.LANDMARKS3D, true); val xPaths = prefs.b(PreferenceKeys.PATHS, true)
    val xTerrain = prefs.b(PreferenceKeys.TERRAIN, false); val xFurn = prefs.b(PreferenceKeys.FURNITURE3D, true); val xClear = prefs.b(PreferenceKeys.CLEARVIEW, true)
    // (tap-to-select stays off on the car, as when the map is made)
    OnChange(xFacades, xLand, xPaths, xTerrain, xFurn, xClear) { map.setExtras(xFacades, xLand, xPaths, xTerrain, false, xFurn, xClear) }
    val xHd = hdRoadsWanted(prefs, prefs.s(PreferenceKeys.COUNTRYDETECTED, ""))
    OnChange(xHd) { map.setHdRoads(xHd) }
    val b3d = prefs.b(PreferenceKeys.BUILDINGS_3D, true)
    // satellite as set on the phone (the car's map always stayed on the drawn map, so with Android Auto it looked as though
    // satellite didn't work)
    val satOn = prefs.b(PreferenceKeys.SATELLITE, false)
    OnChange(b3d, satOn) { map.setView(satOn, b3d) }
    val treesOn = prefs.b(PreferenceKeys.TREES, true)
    OnChange(treesOn) { map.treesEnabled(treesOn) }
    val signalsOn = prefs.b(PreferenceKeys.SIGNALS, true)
    OnChange(signalsOn) { map.signalsEnabled(signalsOn) }
    // the map source, traffic, keys and Mapbox look are read when the style is applied: apply it again in the car's current day / night
    val style = prefs.mapSource()
    val traffic = prefs.b(PreferenceKeys.TRAFFIC, true)
    val key = prefs.s(PreferenceKeys.API_KEY, "")
    val mbToken = prefs.s(PreferenceKeys.MAPBOX_TOKEN, "")
    val mbLook = prefs.s(PreferenceKeys.MBLOOK, "horizon")
    OnChange(style, traffic, key, mbToken, mbLook) { engine.applyStyle(dark.value) }
}

/** What taps on the app's cards do that needs Android Auto's own screens (search, the places list). */
interface CarActions {
    /** The search bar: opens the app's search card over the map (see CarSearch). */
    fun search()
    /** The microphone button: the search card, listening straight away (tapped again, it stops). */
    fun voiceSearch() = search()
    /** The search card's own bar: Android Auto's search screen, which has the car's keyboard. */
    fun typeSearch() = search()
    fun closeSearch() {}
    /** The stops card (see CarStops), and the search card opened from it to add one. */
    fun stops() {}
    fun addStopSearch() {}
    /** The settings card (see CarSettingsCard): closing it, the day / night choice changing, and the other routes' screen. */
    fun closeSettings() {}
    fun themeChanged() {}
    fun otherRoutes() {}
    fun places()
    fun nearby(kind: String)
    fun settings()
}

/**
 * Taps on the car screen: Android Auto passes a tap's position (on newer cars), and it's matched against where the app's cards and
 * buttons are drawn, so the search bar, chips, route cards and Start work by touch as in the app.
 */
object CarTaps {
    private val regions = java.util.LinkedHashMap<String, Pair<androidx.compose.ui.geometry.Rect, () -> Unit>>()
    fun set(id: String, r: androidx.compose.ui.geometry.Rect, a: () -> Unit) { synchronized(regions) { regions.remove(id); regions[id] = r to a } }
    fun remove(id: String) { synchronized(regions) { regions.remove(id) } }
    /** Runs what's under (x, y); true when something was there. */
    fun hit(x: Float, y: Float): Boolean {
        val a = synchronized(regions) { regions.values.toList().asReversed().firstOrNull { it.first.contains(androidx.compose.ui.geometry.Offset(x, y)) }?.second } ?: return false
        a(); return true
    }
}

/**
 * When Android Auto's own buttons were last brought up on the car screen: a touch on the map, or its buttons being laid out
 * again. They fold away by themselves about ten seconds later. Android Auto doesn't always say when they do (the Desktop Head
 * Unit keeps reporting the same visible area with them folded away), so what has to keep out of their way goes by this.
 */
object CarButtons {
    var shownAt by androidx.compose.runtime.mutableLongStateOf(0L)
        private set
    fun poke() { shownAt = android.os.SystemClock.uptimeMillis() }
}

/** Makes this card or button react to taps on the car screen. */
@Composable
internal fun Modifier.carTap(id: String, action: () -> Unit): Modifier {
    val act = androidx.compose.runtime.rememberUpdatedState(action)
    androidx.compose.runtime.DisposableEffect(id) { onDispose { CarTaps.remove(id) } }
    return this.then(Modifier.onGloballyPositioned { CarTaps.set(id, it.boundsInRoot()) { act.value() } })
}

@Composable
private fun CarScreen(engine: NavEngine, a: android.graphics.Rect?, actions: CarActions, onCover: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dens = LocalDensity.current
        val cardScale = engine.prefs.i(PreferenceKeys.CAR_CARD_SIZE, 100).coerceIn(70, 150) / 100f
        val buttonScale = engine.prefs.i(PreferenceKeys.CAR_BUTTON_SIZE, 100).coerceIn(70, 150) / 100f
        val requestedSpeedScale = engine.prefs.i(PreferenceKeys.CAR_SPEED_SIZE, 100).coerceIn(40, 180) / 100f
        // Conservative auto-fit for the compact layout; the edge-positioned layout applies a second fit to its exact bounds.
        val speedScale0 = requestedSpeedScale * minOf(1f, (maxWidth.value / 220f).coerceAtLeast(0.25f), (maxHeight.value / 150f).coerceAtLeast(0.25f))
        val hPx = with(dens) { maxHeight.toPx() }
        val wPx = with(dens) { maxWidth.toPx() }
        // tall screens (portrait car displays, or Android Auto's split layouts) get their own layout: cards across the full width,
        // the turn card under Android Auto's buttons, and everything else stacked at the bottom
        // (also any screen too narrow for cards beside each other, so square and small screens get the stacked layout too)
        // Switch to a stacked layout when either the aspect ratio or the actual usable width makes a side-by-side
        // arrangement cramped. Keep this threshold lower than before so medium-width landscape screens retain the map.
        // Infer constrained/split-screen surfaces from the actual available Compose area rather than device model.
        // Android Auto reports its usable surface here, so a split-screen map naturally trips these thresholds.
        val aspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        // Include shorter usable areas common in split-screen Android Auto and ultra-wide dashboards.
        // Use the available content bounds, not physical display dimensions, so insets are respected.
        val lowVertical = maxHeight < 460.dp
        val narrow = maxWidth < 900.dp
        val wideLandscape = aspect > 2.0f
        val compact = lowVertical || narrow || wideLandscape
        // only a really short screen leaves the junction close-up and advice off (a 7-inch 800 x 480 unit counts as compact, but has room)
        val tiny = maxHeight < 330.dp
        // short, wide screens (a 7-inch 800 x 480 unit) are laid out side by side, not stacked: stacked, the cards took the whole
        // width and ran into each other and the speed (#67)
        val shortWide = lowVertical && aspect >= 1.4f
        val portrait = maxHeight > maxWidth * 1.1f || (narrow && !shortWide)
        // the layout for the space Android Auto actually leaves (the same car gets the roomy layout full-screen and a small one in
        // a split-screen tile): "short" keeps the essentials and one row of shortcuts; "tile" (split screen) only the turn, a
        // one-line trip strip and the speed. Text stays its normal size: the layout gets simpler instead of smaller
        val tile = tiny || (maxWidth < 600.dp && lowVertical)
        val short = !tile && lowVertical
        val small = tile || short
        // Android Auto's split screen: the surface stays full size and only the tile's part of it is visible
        val splitView = a != null && a.width() < wPx * 0.75f
        // A short car screen is laid out as the app on a phone held sideways: the trip card bottom left with the route bar (the
        // figures stay on one line under it, without their labels: those were too much on a small screen) and the speed bottom
        // right. Only a tile or split screen, with no room, keeps the bare strip with the speed beside it
        val slim = tile || (short && splitView)
        // Settings > Android Auto > Card positions: the trip card under the cards on the left ("auto"), in the middle or on the right
        // of the bottom; the speedometer bottom right ("auto") or bottom left (it moves left by itself when the trip card takes the right)
        val carTripAt = engine.prefs.s(PreferenceKeys.CAR_TRIP_POS, "auto")
        // (where it goes is worked out below, once it's known whether the turn card is showing)
        val carSpeedSide = engine.prefs.s(PreferenceKeys.CAR_SPEED_SIDE, "auto")
        // ("corner" was a separate choice for a few builds: it's what "top" does now)
        val carSpeedPos = engine.prefs.s(PreferenceKeys.SPEED_POS_CAR, "bottom").let { if (it == "corner") "top" else it }
        val speedScale = speedScale0 * (if (tile) 0.7f else if (short) 0.85f else 1f)
        val insL = with(dens) { (a?.left ?: 0).toDp() }
        val insT = with(dens) { (a?.top ?: 0).toDp() }
        val insR = with(dens) { (wPx - (a?.right ?: wPx.toInt())).coerceAtLeast(0f).toDp() }
        val insB = with(dens) { (hPx - (a?.bottom ?: hPx.toInt())).coerceAtLeast(0f).toDp() }
        val edge = when {
            maxWidth < 600.dp -> 8.dp
            portrait -> 10.dp
            maxWidth < 1050.dp -> 16.dp
            else -> 24.dp
        }
        val left = insL + edge
        // Wide screens: level with Android Auto's buttons (top right); narrow/tall screens: below them.
        val topTarget = if (portrait) insT + edge else insT.coerceAtMost(12.dp) + edge
        // On tall screens Android Auto's buttons hide after a few seconds and the cards move up smoothly.
        val top by androidx.compose.animation.core.animateDpAsState(topTarget, tween(300), label = "carTop")
        val controlsGap = when {
            portrait -> 64.dp
            maxWidth < 900.dp -> 68.dp
            maxWidth < 1250.dp -> 80.dp
            else -> 92.dp
        }
        val rightTop = insR + if (portrait) edge else controlsGap
        // Android Auto's zoom and re-centre buttons, bottom right; reserve less space on smaller displays.
        val rightBottom = insR + if (portrait) 64.dp else controlsGap
        val bottom = insB + edge
        val previewing = engine.previewing
        val guiding = engine.route != null && !previewing
        val arriving = engine.arrival != null
        // the search card (also while navigating, from the trip card's search button: a place picked then is asked about, a
        // stop or where to go instead)
        val searching = CarSearch.open && !previewing && !arriving
        // the stops card, and the question about a place picked while driving
        val stopsOpen = CarStops.open && guiding && !arriving && !searching
        val choosing = guiding && !arriving && engine.choose != null && !searching && !stopsOpen
        // the settings card: over the map, across the part of the screen Android Auto leaves clear (in split screen, the tile)
        val settingsOpen = CarSettingsCard.open && !arriving && !previewing && !searching && !stopsOpen && !choosing
        // Other routes: the routes box is up and the map is zoomed out to the grey routes. The other cards (the turn, the trip,
        // the speed, alerts) step aside so the routes on the map can be seen; they're back when it closes or a route is picked
        val altsOpen = guiding && !arriving && engine.altListOpen && !searching && !settingsOpen && !stopsOpen && !choosing
        val covered = searching || settingsOpen || altsOpen || stopsOpen || choosing
        val c = LocalVw.current
        val topW = if (portrait) {
            (maxWidth - left - rightTop).coerceAtLeast(0.dp).coerceAtMost(600.dp)
        } else {
            minOf(if (maxWidth < 1050.dp) 380.dp else 440.dp, maxWidth * 0.38f)
        }
        // (the turn card's width, which the trip card under it on the same side takes too, so the two line up)
        val turnCardW = if (portrait) topW else if (small) minOf(340.dp, maxWidth * 0.42f) else minOf(420.dp, maxWidth * 0.36f)
        var turnH by remember { mutableStateOf(0) }
        var turnW by remember { mutableStateOf(0) }
        // the turn card and what's under it, without the lane picture, and the cards along the bottom on the same side: the lane
        // picture gets the room left between them
        var aboveH by remember { mutableStateOf(0) }
        var bottomColH by remember { mutableStateOf(0) }
        var topBlockH by remember { mutableStateOf(0) }

        // where the cards are, for placing your arrow the way the app does: on wide screens it moves sideways clear of the cards on
        // the left; on tall ones it stays centred and sits just above the cards along the bottom. Measured within the part of the
        // screen Android Auto leaves visible (the map's camera works within that part too).
        val vL = (a?.left ?: 0).toFloat(); val vT = (a?.top ?: 0).toFloat()
        val vW = (a?.width()?.toFloat() ?: wPx).coerceAtLeast(1f); val vH = (a?.height()?.toFloat() ?: hPx).coerceAtLeast(1f)
        fun report(i: Int, r: androidx.compose.ui.geometry.Rect) =
            engine.setPanel(i, (r.left - vL) / vW, (r.top - vT) / vH, (r.right - vL) / vW, (r.bottom - vT) / vH)
        val topShown = !portrait && !arriving && (engine.route == null || (guiding && engine.turn != null))
        val colShown = !portrait && !previewing && !arriving
        val stackShown = portrait && !previewing && !arriving
        androidx.compose.runtime.SideEffect {
            if (!topShown) engine.setPanel(0, 0f, 0f, 0f, 0f)
            if (!colShown) engine.setPanel(1, 0f, 0f, 0f, 0f)
            if (!stackShown) engine.bottomCover = 0.0
        }

        // the trip card's red button ends the route: with it there (the tile leaves the buttons off), Android Auto's own Stop
        // button is left out (see MapScreen.stopOnCard)
        val endOnCard = guiding && !arriving && !tile && "end" in carTripButtonsOn(engine.prefs)
        androidx.compose.runtime.SideEffect { com.headunit.launcher.car.CarLayout.endOnCard = endOnCard }

        // the turn card, top left (as in the app)
        val t = engine.turn
        val showTurn = guiding && t != null && !arriving
        // hazards ahead (an incident, a speed camera) go along the bottom of the turn card when it's showing, instead of a
        // separate pill: the left side is then just the turn card and the trip card
        val alert = engine.alert
        val inc = engine.incidentAhead; val cam = engine.cameraDist
        val incShown = inc != null && alert?.kind != "incident"
        // (while the average speed card shows under the turn card, it takes the speed camera line's place, so the column
        // doesn't grow too tall on a short car screen: magnazan, #177)
        val camShown = cam != null && alert?.kind != "camera" && engine.avgInfo == null
        // one slim line while the turn is far off (about 2 km), the full card as it comes up (a little either side, so it can't flicker)
        val slimHold = remember { booleanArrayOf(false) }
        if (t != null) slimHold[0] = if (t.dist > 2100.0) true else if (t.dist < 1900.0) false else slimHold[0]
        // Speed position (Settings > Android Auto, and the car's own settings). Every place is anchored to a card or a corner:
        // floating half-way up the right-hand side, beside Android Auto's zoom buttons, it looked lost and sat in from the edge.
        //  "bottom": the bottom corner, on the side picked under Card positions (bottom left on a short screen, #67)
        //  "middle": under the turn card (the stored name is from when it was the middle of the right-hand side); with no turn
        //            card showing it waits bottom left
        //  "top":    top right. Not navigating: under Android Auto's buttons (on a short screen the ring has no room there
        //            between Android Auto's two groups of buttons, so it waits bottom left; the compact pill is small enough).
        val topRightFits = !small || engine.prefs.s(PreferenceKeys.CAR_SPEED_STYLE, "app") == "compact"
        //            It's right in the top right corner itself, where Android Auto's own buttons come up, and makes way while
        //            they're showing: a touch brings them up and they fold away again by themselves about ten seconds later
        //            (see CarButtons). The same with or without a route
        val speedCorner = carSpeedPos == "top" && !portrait
        val speedUnderTurn = !portrait && showTurn && carSpeedPos == "middle"
        // Android Auto's split screen, with Settings › Android Auto › Trip card in split screen on "Column on the left": the trip
        // card is a narrow column under the turn card instead of a bar along the bottom, leaving the road ahead of the car clear
        val tripCol = splitView && guiding && showTurn && !portrait && engine.prefs.s(PreferenceKeys.CAR_SPLIT_TRIP, "bar") == "column"
        val speedTopRight = carSpeedPos == "top" && topRightFits && !speedCorner
        var stripShown by remember { mutableStateOf(true) }
        LaunchedEffect(CarButtons.shownAt) { stripShown = true; kotlinx.coroutines.delay(11_000L); stripShown = false }
        val carSpeedLeft = when {
            speedUnderTurn || speedTopRight || speedCorner -> false
            carSpeedPos == "bottom" -> carSpeedSide == "left" || (carSpeedSide == "auto" && (carTripAt == "right" || slim))
            else -> true
        }
        var speedUnderH by remember { mutableStateOf(0) }
        // (worked out here: the screen's height can't be read from inside the column below)
        val laneRoom = (maxHeight - top - bottom - with(dens) { (aboveH + bottomColH + (if (speedUnderTurn) speedUnderH else 0)).toDp() } - 28.dp - (if (speedUnderTurn) 8.dp else 0.dp)).coerceAtLeast(0.dp)
        if (showTurn && t != null && !settingsOpen && !altsOpen && !searching && !stopsOpen) Box(Modifier.padding(start = left, top = top).width(turnCardW)
            .onSizeChanged { turnH = it.height; turnW = it.width }.onGloballyPositioned { if (topShown) report(0, it.boundsInRoot()) }) {
            // the turn card, with the junction sign or roundabout diagram under it and any advice for the conditions, as on the head unit
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
              Column(Modifier.onSizeChanged { aboveH = it.height }, verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
                // (the car's "Navigation arrow size" is the arrow on the map, as on the phone: CarNav applies it there)
                CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) {
                    ImmTurnCard(engine, t, compact = compact, car = true, slim = slimHold[0],
                        footer = {
                            if (incShown || camShown) Column(Modifier.fillMaxWidth().background(GmGreenDeep).padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (incShown && inc != null) CarHazardLine("incident", L.t(inc.label), Geo.fmtDist(inc.dist, engine.imperial))
                                if (camShown && cam != null) CarHazardLine("camera", L.t("Speed camera"), Geo.fmtDist(cam.toDouble(), engine.imperial))
                            }
                        })
                }
                if (!small) engine.advice?.let { AdvicePill(it) }
                // a low bridge or weight limit the lorry is over: shown even on a small car screen
                LorryWarnCard(engine)
                // an average speed check: your average so far, the limit, how far is left and what keeps you under, as on the
                // phone (it only showed there: magnazan, #177). At the car cards' size, under the turn card
                CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) { AvgSpeedCard(engine) }
              }
                // sized to the gap down to the trip card (it used to run on underneath it on a short car screen), or left out
                // when even a small one wouldn't fit: the turn card's lane strip still shows the lanes then
                // (not on a short screen or a tile: the lanes are in the turn card's own strip there)
                if (!small) JunctionCloseup(engine, maxH = laneRoom)
                // Speed position "Under the turn card": in the left-hand column the app's own cards already keep clear
                if (speedUnderTurn) Box(Modifier.onSizeChanged { speedUnderH = it.height }) {
                    CompositionLocalProvider(LocalDensity provides Density(dens.density * speedScale, dens.fontScale)) { ImmSpeed(engine) }
                }
                if (tripCol) CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) { TripColumn(engine, showEnd = !tile) }
            }
        }

        // not navigating: the app's search bar and quick chips, top left (tap them as in the app)
        // as many saved places as fit above the sound pill / speed (0 to 3), so the list never runs into what's below it
        // (worked out here: the screen's height can't be read from inside the column below)
        val savedRows = ((maxHeight - top - bottom - 242.dp - 96.dp) / 58.dp).toInt().coerceIn(0, 3)
        if (engine.route == null && !arriving && !covered) Column(Modifier.padding(start = left, top = top).width(topW).onSizeChanged { topBlockH = it.height }.onGloballyPositioned { if (topShown) report(0, it.boundsInRoot()) },
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().height(if (small) 48.dp else 60.dp).panel(10.dp, RoundedCornerShape(30.dp), c.surface).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                // the bar opens the search; the arrow folds Home / Work, the round buttons and Saved & recent away and back (as in the app),
                // so it has a tap area of its own
                Row(Modifier.weight(1f).fillMaxHeight().carTap("search") { actions.search() }.padding(start = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Search, null, tint = c.sub)
                    Text(L.t("Where to?"), color = c.sub, fontSize = 18.sp, modifier = Modifier.padding(start = 12.dp))
                }
                val open = engine.prefs.b(PreferenceKeys.QUICKLIST, true)
                Box(Modifier.padding(end = 4.dp).size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).carTap("fold") { engine.prefs.put(PreferenceKeys.QUICKLIST, !open) }, contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(if (open) androidx.compose.material.icons.Icons.Rounded.KeyboardArrowUp else androidx.compose.material.icons.Icons.Rounded.KeyboardArrowDown, null, tint = c.sub, modifier = Modifier.size(28.dp))
                }
                Box(Modifier.size(if (small) 38.dp else 44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.accent).carTap("search-mic") { actions.voiceSearch() }, contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(23.dp))
                }
            }
            // (folded away, with everything in it, by the arrow in the bar)
            if (engine.prefs.b(PreferenceKeys.QUICKLIST, true) && !tile) {
                if (short) CarChipsCompact(engine, actions) else CarChips(engine, actions, savedRows)
            }
        }

        // alerts (cameras, limits, hazards...): beside the turn card on wide screens, under it on tall ones
        val below = if (showTurn && turnH > 0) with(dens) { turnH.toDp() } + 10.dp else if (engine.route == null && topBlockH > 0) with(dens) { topBlockH.toDp() } + 10.dp else 0.dp
        val alertMod = if (portrait) Modifier.align(Alignment.TopStart).padding(start = left, end = rightTop + 64.dp, top = top + below)
            // (beside the turn card, from where it actually ends: it was started 42% of the way across, which a turn card of that
            // width, set in from the edge, ran a little past, so the two overlapped)
            else Modifier.align(Alignment.TopCenter).padding(top = top,
                start = if (showTurn && turnW > 0) left + with(dens) { turnW.toDp() } + 10.dp else if (guiding) maxWidth * 0.42f + left else left, end = maxOf(rightTop, maxWidth * 0.30f))
        AnimatedVisibility(alert != null && !altsOpen, modifier = alertMod, enter = fadeIn(), exit = fadeOut()) { alert?.let { AlertBanner(it) } }

        // the search card: over the map from the left edge to Android Auto's zoom buttons (its other buttons give way to one
        // Close button while it's open, see MapScreen)
        if (searching) CarSearchPanel(engine, actions, small, Modifier.padding(start = left, top = top, end = insR + 8.dp, bottom = bottom).fillMaxSize())

        if (stopsOpen) CarStopsPanel(engine, actions, small, Modifier.padding(start = left, top = top, end = insR + 8.dp, bottom = bottom).fillMaxSize())
        // (in the middle of what's clear of the turn card, which stays up)
        if (choosing) engine.choose?.let { p ->
            Box(Modifier.align(Alignment.BottomCenter).padding(start = left, end = rightBottom, bottom = bottom)) { CarChooseCard(engine, p) }
        }

        if (settingsOpen) {
            CarSettingsPanel(engine, actions, small, Modifier.padding(start = left, top = top, end = insR + 8.dp, bottom = bottom).fillMaxSize())
        }

        if (!previewing && !arriving && !covered) {
            val hazards: @Composable () -> Unit = {
                // (only when there's no turn card to carry them)
                if (!showTurn && (incShown || camShown)) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (incShown && inc != null) HazardBadge("incident", L.t(inc.label), Geo.fmtDist(inc.dist, engine.imperial))
                    if (camShown && cam != null) HazardBadge("camera", L.t("Speed camera"), Geo.fmtDist(cam.toDouble(), engine.imperial))
                }
            }
            if (portrait) {
                // tall: laid out like the app on a phone. The sound button sits on the right edge under the top cards; along the
                // bottom, the trip card spans the width (clear of Android Auto's buttons) with the hazards on the left above it and
                // your speed on the right above it
                var tripPx by remember { mutableStateOf(0) }
                // (while guiding, the sound button is on the trip card)
                if (!guiding && !small) CompositionLocalProvider(LocalDensity provides Density(dens.density * buttonScale, dens.fontScale)) {
                    ImmRound(engine.soundIcon(), "sound", { engine.cycleSound() }, Modifier.align(Alignment.TopEnd).padding(end = rightTop, top = top + below))
                }
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = left, bottom = bottom)
                        .width((maxWidth - left - rightBottom).coerceAtLeast(0.dp).coerceAtMost(620.dp)).onSizeChanged { bottomColH = it.height }
                        .onGloballyPositioned { engine.bottomCover = ((vT + vH - it.boundsInRoot().top) / vH).coerceIn(0f, 0.6f).toDouble() },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (guiding) CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) { FasterCard(engine, Modifier.fillMaxWidth()) }
                    // (the row keeps the speed's height, so the hazards line up with it)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f)) { hazards() }
                        Box(Modifier.padding(start = 30.dp, top = 14.dp)) { CompositionLocalProvider(LocalDensity provides Density(dens.density * speedScale, dens.fontScale)) { ImmSpeed(engine) } }
                    }
                    if (guiding) CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) { Box(Modifier.onSizeChanged { tripPx = it.height }) { TripCard(engine, actions, !small, oneLine = small, buttonsShown = !tile, bar = !slim) } }
                }
            } else {
                var leftColW by remember { mutableStateOf(0) }
                var leftColH by remember { mutableStateOf(0) }
                var speedW by remember { mutableStateOf(0) }
                // Android Auto's split screen (the map in a tile beside another app: the surface stays full size and only the
                // tile's part of it is visible): while navigating, the trip strip is a bar docked along the tile's bottom edge,
                // centred, as Google Maps has it, instead of floating above the edge beside the other cards
                val split = splitView
                val tripDocked = split && small && guiding && !tripCol
                var dockH by remember { mutableStateOf(0) }
                // (right down on the edge: Android Auto leaves a small margin for the tile's rounded corners, which the bar's
                // own rounded top doesn't need. A big bottom inset is something really covering the map, so that's kept clear)
                val dockBottom = if (with(dens) { insB.toPx() } < hPx * 0.12f) 0.dp else insB
                val colBottom = if (tripDocked) dockBottom + with(dens) { dockH.toDp() } + 8.dp else bottom
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = left, bottom = colBottom).onSizeChanged { leftColW = it.width; leftColH = it.height; bottomColH = it.height }
                        .onGloballyPositioned { report(1, it.boundsInRoot()) },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (guiding) CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) { FasterCard(engine, Modifier.widthIn(max = 520.dp)) }
                    // (while guiding, the sound button is on the trip card instead of a pill of its own)
                    if (!guiding && !small) CompositionLocalProvider(LocalDensity provides Density(dens.density * buttonScale, dens.fontScale)) { SoundPill(engine) }
                    hazards()
                    if (carSpeedLeft) {
                        // bottom left (picked, or a short screen (#67)): the speed stays put in the bottom-left corner, with the one-line trip strip beside it
                        // while navigating. Bottom right, it slid in and out with Android Auto's zoom buttons, sat on the Mapbox logo,
                        // and went up the side whenever the trip strip needed the room
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CompositionLocalProvider(LocalDensity provides Density(dens.density * speedScale, dens.fontScale)) { ImmSpeed(engine) }
                            // (allowed to shrink, so on the narrowest tiles it gives way rather than pushing past the edge)
                            if (guiding && carTripAt == "auto" && !tripDocked && !tripCol) Box(Modifier.weight(1f, fill = false)) { CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) { TripCard(engine, actions, false, oneLine = small, buttonsShown = !tile, bar = !slim) } }
                        }
                    } else if (guiding && carTripAt == "auto" && !tripDocked && !tripCol) {
                        // as wide as the turn card above it (with more than a couple of buttons picked for it, it needs its own width)
                        val sameW = carTripButtonsOn(engine.prefs).size <= 2
                        Box(if (sameW) Modifier.width(turnCardW) else Modifier) {
                            CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) { TripCard(engine, actions, sameW, oneLine = small, buttonsShown = !tile, bar = !slim) }
                        }
                    }
                }
                // your speed and the limit, bottom right as in the app (clear of Android Auto's zoom buttons); on a small screen where
                // it would run into the cards on the left, it sits above them instead
                // (with some leeway before it moves back down: right on the edge, the cards' width changes with their text, and the
                // speed sign kept jumping up and down between the two places)
                val needPx = with(dens) { (left + 16.dp + 34.dp + rightBottom).toPx() } + leftColW + speedW
                var crowdedHeld by remember { mutableStateOf(false) }
                val crowdedNow = needPx > wPx
                val roomy = needPx + with(dens) { 48.dp.toPx() } < wPx
                androidx.compose.runtime.SideEffect { if (crowdedNow) crowdedHeld = true else if (roomy) crowdedHeld = false }
                val crowded = crowdedNow || crowdedHeld
                // Down in the bottom corner, level with the trip card and just left of Android Auto's zoom and re-centre buttons
                // (halfway up the side it looked lost in the middle of the screen)
                val speedBottom = if (crowded && leftColH > 0) colBottom + with(dens) { leftColH.toDp() } + 10.dp else colBottom
                // right up against the edge of the clear map: Android Auto reports the part of the screen its own buttons leave free,
                // which shrinks while its zoom and re-centre buttons are showing, so the sign sits just beside them then and slides
                // into the corner when they hide (it used to keep a fixed 92dp gap as well, so it sat well in from either)
                val speedEnd by androidx.compose.animation.core.animateDpAsState(insR + 6.dp, tween(280), label = "speedEnd")
                // (Settings > Android Auto > Speed position on the car screen: bottom as always, or up the side, clear of the cards on
                // the left; at the top it sits below Android Auto's own buttons in the top corner)
                // (right up in the corner of the screen, or of the tile in split screen: the visible area Android Auto reports
                // still leaves room for its buttons there, folded away or not, so it isn't gone by here)
                val cornerGap = with(dens) { 12f.toDp() } + 2.dp
                val speedMod = if (speedCorner) Modifier.align(Alignment.TopEnd).padding(end = if (splitView) insR + 6.dp else cornerGap, top = if (splitView) insT + 6.dp else cornerGap, start = 34.dp, bottom = bottom)
                    else if (speedTopRight) Modifier.align(Alignment.TopEnd).padding(end = speedEnd, top = top + 72.dp, start = 34.dp, bottom = bottom)
                    else Modifier.align(Alignment.BottomEnd).padding(end = speedEnd, bottom = speedBottom, start = 34.dp, top = 20.dp)
                // Card positions (Settings > Android Auto): the trip card in the middle or on the right, along the bottom
                if (tripDocked) Box(
                    Modifier.align(Alignment.BottomStart).padding(start = insL, end = insR, bottom = dockBottom).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) {
                        Box(Modifier.onSizeChanged { dockH = it.height }) { TripCard(engine, actions, false, oneLine = true, buttonsShown = !tile, docked = true) }
                    }
                }
                if (guiding && carTripAt != "auto" && !tripDocked && !tripCol) Box(
                    Modifier.align(if (carTripAt == "centre") Alignment.BottomCenter else Alignment.BottomEnd)
                        .padding(bottom = bottom, end = if (carTripAt == "right") speedEnd else 0.dp)
                ) { CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) { TripCard(engine, actions, false, oneLine = small, buttonsShown = !tile, bar = !slim) } }
                // (in the top corner it makes way for Android Auto's buttons, and for the routes box, which opens there)
                if (!carSpeedLeft && !speedUnderTurn && !(speedCorner && (stripShown || engine.altListOpen))) BoxWithConstraints(speedMod.onSizeChanged { speedW = if (speedTopRight || speedCorner) 0 else it.width }) {
                    val availableFit = minOf(1f, (maxWidth.value / 220f).coerceAtLeast(0.25f), (maxHeight.value / 150f).coerceAtLeast(0.25f))
                    val fittedScale = requestedSpeedScale * availableFit * (if (tile) 0.7f else if (short) 0.85f else 1f)
                    CompositionLocalProvider(LocalDensity provides Density(dens.density * fittedScale, dens.fontScale), LocalSpeedAtTop provides (speedCorner || speedTopRight)) { ImmSpeed(engine) }
                }
            }
        }

        // before setting off: the destination, the route choices and the Start bar that starts by itself after 10 s
        // (on a wide screen that isn't very wide, a slim column down the left, so the routes on the map can be seen beside it:
        // across the bottom, with the routes side by side, it covered nearly the whole of a short screen)
        val previewSide = !portrait && maxWidth < 1120.dp
        if (previewing && previewSide) Box(Modifier.align(Alignment.BottomStart).padding(start = left, top = top, bottom = bottom).width(minOf(300.dp, maxWidth * 0.42f) * cardScale)) {
            CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) { CarRoutePreview(engine, side = true) }
        }
        if (previewing && !previewSide) Box(Modifier.align(Alignment.BottomCenter).padding(start = left, end = if (portrait) rightBottom else rightTop, bottom = bottom)) {
            // ("Card size" covers the route cards too)
            CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) { CarRoutePreview(engine) }
        }

        // working out a route: a small spinner at the top, as in the app
        AnimatedVisibility(engine.busy, modifier = Modifier.align(Alignment.TopCenter).padding(top = top + (if (portrait) below else 0.dp)),
            enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.size(48.dp).panel(6.dp, androidx.compose.foundation.shape.CircleShape, c.surface), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(26.dp), color = c.accent, strokeWidth = 3.dp)
            }
        }

        // Other routes (the round button among Android Auto's): the routes box, as in the app, with each way's time, when it
        // gets there and how it compares; tap one to switch. It was only ever drawn on the phone, so on the car screen the button
        // just zoomed the map out to the grey routes. On the right, under Android Auto's buttons and beside its zoom buttons;
        // drawn smaller when it's taller than the room there (as the arrival card is)
        if (altsOpen) {
            // (its full-size height is worked back from the size it's drawn at, which comes out a pixel or so different at each
            // scale: only a real change counts, or the scale kept chasing its own rounding and the box wiggled)
            var boxH by remember { mutableStateOf(0f) }
            val boxTop = if (portrait) top + below else insT + 6.dp
            val room = (maxHeight - boxTop - bottom).coerceAtLeast(1.dp)
            val fit = if (boxH > 0f) (room.value / boxH).coerceIn(0.5f, 1f) else 1f
            Box(Modifier.align(Alignment.TopEnd).padding(end = insR + 8.dp, top = boxTop, bottom = bottom)) {
                CompositionLocalProvider(LocalDensity provides Density(dens.density * fit, dens.fontScale)) {
                    RoutesBox(engine, Modifier.wrapContentHeight(unbounded = true, align = Alignment.Top).onSizeChanged { val h = it.height / dens.density / fit; if (kotlin.math.abs(h - boxH) > boxH * 0.02f) boxH = h })
                }
            }
        }

        // the arrival summary
        val arr = engine.arrival
        // (drawn smaller when it's taller than the screen: on a short car screen it ran off the bottom, squashing "Nearest
        // parking" and losing the buttons. It's measured at its own height, and the scale that makes that fit is used)
        if (arr != null) {
            var arrH by remember { mutableStateOf(0f) }
            val room = (maxHeight - top - bottom).coerceAtLeast(1.dp)
            val fit = if (arrH > 0f) (room.value / arrH).coerceIn(0.5f, 1f) else 1f
            // (top left, where the turn card was, on a wide screen; along the bottom of a tall one)
            Box(Modifier.align(if (portrait) Alignment.BottomCenter else Alignment.TopStart).padding(start = left, end = rightBottom, top = top, bottom = bottom)) {
                CompositionLocalProvider(LocalDensity provides Density(dens.density * fit, dens.fontScale)) {
                    // (its height in this screen's dp at full size, whatever scale it's drawn at now)
                    ArrivalCard(engine, arr, Modifier.wrapContentHeight(unbounded = true, align = Alignment.Top).onSizeChanged { val h = it.height / dens.density / fit; if (kotlin.math.abs(h - arrH) > arrH * 0.02f) arrH = h }, compact = small)
                }
            }
        }
    }
}

/** A faster route turned up while driving: switch or keep, as in the app. */
@Composable
private fun FasterCard(engine: NavEngine, modifier: Modifier) {
    val fr = engine.faster ?: return
    val c = LocalVw.current
    Row(
        modifier.panel(10.dp, RoundedCornerShape(24.dp), c.surface).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Good.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Route, null, tint = Good, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(L.t("Faster route found"), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(L.t("Saves about {0} min", fr.savedMin), color = Good, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Box(Modifier.carTap("faster-yes") { engine.acceptFaster() }) { ImmPill(L.t("Switch"), {}, filled = true) }
        Box(Modifier.carTap("faster-no") { engine.dismissFaster() }) { ImmPill(L.t("Keep"), {}) }
    }
}

/** Voice guidance: tap for all / alerts only / off (as the app's sound button). */
@Composable
private fun SoundPill(engine: NavEngine) {
    val c = LocalVw.current
    val sm = engine.prefs.i(PreferenceKeys.SOUND_MODE, if (engine.prefs.b(PreferenceKeys.VOICE, true)) 0 else 2).coerceIn(0, 2)
    Row(
        Modifier.height(46.dp).panel(6.dp, RoundedCornerShape(23.dp), c.surface).carTap("sound") { engine.cycleSound() }.padding(start = 10.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = when (sm) { 0 -> c.accent; 1 -> Warn; else -> Bad }
        Box(Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(
                when (sm) { 0 -> androidx.compose.material.icons.Icons.Rounded.VolumeUp; 1 -> androidx.compose.material.icons.Icons.Rounded.NotificationsActive; else -> androidx.compose.material.icons.Icons.Rounded.VolumeOff },
                null, tint = tint, modifier = Modifier.size(18.dp),
            )
        }
        Text(L.t(when (sm) { 0 -> "Sound on"; 1 -> "Alerts only"; else -> "Muted" }), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp))
    }
}

/** The trip card standing up: time left, arrival and distance one under the other, then the button that ends the route (Android
 *  Auto's split screen with Trip card in split screen on "Column on the left"). */
@Composable
private fun TripColumn(engine: NavEngine, showEnd: Boolean = true) {
    val e = engine.eta ?: return
    val c = LocalVw.current
    val dc = delayTint(e.delayMin, c.surface)
    Column(Modifier.panel(10.dp, RoundedCornerShape(18.dp), c.surface).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(Geo.fmtDur(e.secs), color = dc, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        Text(e.arrive, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        Text(Geo.fmtDist(e.dist, engine.imperial), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        if (showEnd && "end" in carTripButtonsOn(engine.prefs)) {
            val sz = 34.dp * carButtonScale(engine)
            Box(Modifier.padding(top = 6.dp).size(sz).clip(androidx.compose.foundation.shape.CircleShape).background(Bad).carTap("end") { engine.endRoute() }, contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(sz * 0.52f))
            }
        }
    }
}

/** Time left, arrival, distance and the route bar, with the sound button and the red button that ends the route.
 *  [fill]: across the width, compact (tall screens). [docked]: a bar on the bottom edge (Android Auto's split screen).
 *  [bar]: the route bar over the one-line strip too (a short car screen: the progress along the route as in the app, without
 *  the labels under the figures, which were too much on a small screen). */
@Composable
private fun TripCard(engine: NavEngine, actions: CarActions, fill: Boolean, oneLine: Boolean = false, buttonsShown: Boolean = true, docked: Boolean = false, bar: Boolean = false) {
    val e = engine.eta ?: return
    val c = LocalVw.current
    val dc = delayTint(e.delayMin, c.surface)
    // the card is as wide as its figures and buttons (at least 400dp), never the whole screen: the figures used to share a row that
    // was told to fill the space, so on a very wide car screen the card stretched right across and its sound and end buttons ended
    // up under Android Auto's own buttons on the right. The route bar is set to the row's measured width (it can't be asked for
    // its natural width).
    var rowW by remember { mutableStateOf(0) }
    val dens = LocalDensity.current
    val w = if (fill) Modifier.fillMaxWidth() else Modifier.width(if (rowW > 0) with(dens) { rowW.toDp() } else 400.dp)
    // ([docked]: sitting on the bottom edge of the screen, so only its top corners are rounded)
    Column((if (fill) Modifier.fillMaxWidth() else Modifier).panel(10.dp, if (docked) RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp) else RoundedCornerShape(22.dp),
        // (docked on the bottom edge it sits over the road's name on the map: solid there even with glass panels on, or the
        // name showed through its figures)
        if (docked) c.surface.copy(alpha = 1f) else c.surface)) {
        // a slim bar: the full-height one took up nearly half the card
        if (engine.showRouteBar && (!oneLine || bar)) RouteBar(engine, w.padding(start = 16.dp, end = 16.dp, top = 6.dp), thin = true)
        // stops on the way: the next one and when you get there, over the figures for the whole trip (tap for the stops card)
        val nextStop = engine.via.firstOrNull()
        if (nextStop != null) Row(w.carTap("trip-stop") { actions.stops() }.padding(start = 16.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val n = engine.via.size
            Box(Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                Text(if (n > 1) "$n" else "1", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text(nextStop.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp, end = 8.dp))
            engine.stopEtas.firstOrNull { it.place === nextStop }?.let { s ->
                Text(s.arrive + " · " + Geo.fmtDur(s.secs), color = Good, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
        val end: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { sz ->
            // end the route, as in the app
            Box(Modifier.size(sz).clip(androidx.compose.foundation.shape.CircleShape).background(Bad).carTap("end") { engine.endRoute() }, contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(sz * 0.52f))
            }
        }
        // sound: tap for all / alerts only / off, as the app's sound button; lit up in its colour unless it's fully on
        val sound: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { sz ->
            val sm = engine.prefs.i(PreferenceKeys.SOUND_MODE, if (engine.prefs.b(PreferenceKeys.VOICE, true)) 0 else 2).coerceIn(0, 2)
            val tint = when (sm) { 0 -> c.sub; 1 -> Warn; else -> Bad }
            Box(Modifier.size(sz).clip(androidx.compose.foundation.shape.CircleShape).background(if (sm == 0) c.line else tint.copy(alpha = 0.22f))
                .carTap("sound") { engine.cycleSound() }, contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(engine.soundIcon(), null, tint = if (sm == 0) c.text else tint, modifier = Modifier.size(sz * 0.52f))
            }
        }
        val stats: @Composable () -> Unit = {
            CarStat(Geo.fmtDur(e.secs), if (e.delayMin >= 1) "+${e.delayMin} min" else L.t("left"), dc)
            CarStat(e.arrive, L.t("arrive"), c.text)
            CarStat(Geo.fmtDist(e.dist, engine.imperial), L.t("to go"), c.text)
            engine.evCardPct()?.let { p -> CarStat("$p%", L.t("battery"), if (p < 10) Bad else if (p < 20) Warn else Good) }
        }
        // the whole route, and other routes (as in the app): Android Auto has no room left for them among its own buttons
        val small: @Composable (androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit, androidx.compose.ui.unit.Dp) -> Unit = { icon, id, act, sz ->
            Box(Modifier.size(sz).clip(androidx.compose.foundation.shape.CircleShape).background(c.line).carTap(id, act), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(icon, null, tint = c.text, modifier = Modifier.size(sz * 0.52f))
            }
        }
        // only the buttons chosen (Settings > Android Auto > Buttons on the trip card), at the size chosen there; with fewer of them
        // the card is narrower and leaves more of the map clear
        val on = if (buttonsShown) carTripButtonsOn(engine.prefs) else emptySet()
        // a short screen or a split-screen tile: one line, the figures without their labels, then the buttons kept
        if (oneLine) {
            val grow1 = (when (engine.prefs.s(PreferenceKeys.CAR_TRIP_BTN_SIZE, "normal")) { "large" -> 1.25f; "xl" -> 1.5f; else -> 1f }) * carButtonScale(engine)
            // (measured, so the route bar over it can be made the same width)
            Row((if (fill) Modifier.fillMaxWidth() else Modifier).onSizeChanged { if (it.width != rowW) rowW = it.width }.padding(start = 14.dp, end = if (on.isEmpty()) 14.dp else 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val sz = 36.dp * grow1
                // (the figures open the stops card)
                Row(Modifier.carTap("trip-figs") { actions.stops() }, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Geo.fmtDur(e.secs), color = dc, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    Text(e.arrive, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    Text(Geo.fmtDist(e.dist, engine.imperial), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
                // (across a set width, the buttons go to the far end)
                if (fill) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                // (just the one that ends the route, unless others are picked in Settings > Android Auto > Buttons on the trip card)
                if (on.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if ("overview" in on) small(androidx.compose.material.icons.Icons.Rounded.ZoomOutMap, "overview", { engine.overview() }, sz)
                    if ("routes" in on) small(androidx.compose.material.icons.Icons.Rounded.AltRoute, "altroutes", { engine.findAlternatives() }, sz)
                    if ("sound" in on) sound(sz)
                    if ("search" in on) small(androidx.compose.material.icons.Icons.Rounded.Search, "tripsearch", { actions.search() }, sz)
                    if ("end" in on) end(sz)
                }
            }
            return@Column
        }
        val grow = (when (engine.prefs.s(PreferenceKeys.CAR_TRIP_BTN_SIZE, "normal")) { "large" -> 1.25f; "xl" -> 1.5f; else -> 1f }) * carButtonScale(engine)
        val buttons: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { base ->
            val sz = base * grow
            if ("overview" in on) small(androidx.compose.material.icons.Icons.Rounded.ZoomOutMap, "overview", { engine.overview() }, sz)
            if ("routes" in on) small(androidx.compose.material.icons.Icons.Rounded.AltRoute, "altroutes", { engine.findAlternatives() }, sz)
            if ("sound" in on) sound(sz)
            // search while navigating: a stop on the way, or somewhere else to go
            if ("search" in on) small(androidx.compose.material.icons.Icons.Rounded.Search, "tripsearch", { actions.search() }, sz)
            if ("end" in on) end(sz)
        }
        if (fill) Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).carTap("trip-figs") { actions.stops() }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { stats() }
            if (on.isNotEmpty()) Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { buttons(36.dp) }
        } else Row(Modifier.widthIn(min = if (on.size >= 3) 400.dp else 0.dp).onSizeChanged { if (it.width != rowW) rowW = it.width }.padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.carTap("trip-figs") { actions.stops() }, horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) { stats() }
            if (on.isNotEmpty()) Row(Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { buttons(40.dp) }
        }
    }
}

/** Settings > Android Auto > Button size, for the round buttons the app draws on the car screen. */
private fun carButtonScale(engine: NavEngine): Float = engine.prefs.i(PreferenceKeys.CAR_BUTTON_SIZE, 100).coerceIn(70, 150) / 100f

/** A figure on the car trip card, with a label big enough to read at a glance (the app's own labels were tiny on a car screen). */
@Composable
private fun CarStat(value: String, label: String, color: Color) {
    val c = LocalVw.current
    Column {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        Text(label, color = c.sub, fontSize = 14.sp, maxLines = 1, softWrap = false)
    }
}

/** One hazard along the bottom of the car turn card: its road sign, what it is and how far. */
@Composable
private fun CarHazardLine(kind: String, label: String, dist: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AlertSign(com.headunit.launcher.AlertInfo(kind, label), 22.dp)
        Text("$label in $dist", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
    }
}

/** A round button like the app's (sound and so on), tappable on the car screen. */
@Composable
private fun ImmRound(icon: androidx.compose.ui.graphics.vector.ImageVector, id: String, action: () -> Unit, modifier: Modifier) {
    val c = LocalVw.current
    Box(modifier.size(52.dp).panel(6.dp, androidx.compose.foundation.shape.CircleShape, c.surface).carTap(id, action), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(icon, null, tint = c.text, modifier = Modifier.size(24.dp))
    }
}

private fun NavEngine.soundIcon(): androidx.compose.ui.graphics.vector.ImageVector = when (prefs.i(PreferenceKeys.SOUND_MODE, if (prefs.b(PreferenceKeys.VOICE, true)) 0 else 2).coerceIn(0, 2)) {
    0 -> androidx.compose.material.icons.Icons.Rounded.VolumeUp
    1 -> androidx.compose.material.icons.Icons.Rounded.NotificationsActive
    else -> androidx.compose.material.icons.Icons.Rounded.VolumeOff
}

/** The app's route choice, as on its own screen: pick a route, start with Start (or wait 10 s). Stacks up on narrow screens.
 *  [side]: a slim column down the left with the routes one under the other (a car screen that isn't very wide: laid out across
 *  the bottom it covered nearly the whole map, with the routes on it out of sight). */
@Composable
private fun CarRoutePreview(engine: NavEngine, side: Boolean = false) {
    val btnH = if (side) 50.dp else 72.dp
    val c = LocalVw.current
    val rs = engine.routes.take(3)
    if (rs.isEmpty()) return
    val fastest = rs.indices.minByOrNull { rs[it].time } ?: 0
    val shortest = rs.indices.minByOrNull { rs[it].len } ?: 0
    val progress = remember { Animatable(0f) }
    val autoStart = engine.prefs.b(PreferenceKeys.AUTO_START_ROUTE, true)
    val countdownSeconds = engine.prefs.i(PreferenceKeys.AUTO_START_SECONDS, 10).coerceIn(3, 30)
    LaunchedEffect(engine.active, engine.routes, autoStart, countdownSeconds) {
        progress.snapTo(0f)
        if (!autoStart) return@LaunchedEffect
        progress.animateTo(1f, tween(countdownSeconds * 1000, easing = LinearEasing))
        if (engine.previewing) engine.beginNavigation()
    }
    val fmt = com.headunit.launcher.Clock.format()
    val baseMs = if (engine.departAtMs > 0) engine.departAtMs else System.currentTimeMillis()
    val dest: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Text(L.t("Destination"), color = c.sub, fontSize = 12.sp)
            Text(engine.dest?.name ?: "", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    val cards: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        rs.forEachIndexed { i, r ->
            val sel = i == engine.active
            val label = L.t(if (i == fastest) "Fastest" else if (rs[i].simpler) "Simplest" else if (i == shortest) "Shortest" else "Alternative")
            Column(Modifier.weight(1f).inset(18.dp, if (sel) c.accent else null).carTap("route-$i") { engine.selectRoute(i) }.padding(horizontal = 12.dp, vertical = 9.dp)) {
                Text(label.uppercase(), color = if (sel) c.accent else c.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
                Text(Geo.fmtDur(r.time), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${Geo.fmtDist(r.len, engine.imperial)} · arrive ${fmt.format(java.util.Date(baseMs + (r.time * 1000).toLong()))}", color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val via = viaOf(r)
                val delayMin = Math.round(r.delay / 60.0).toInt()
                Text(if (delayMin >= 1) L.t("+{0} min traffic", delayMin) else if (via.isNotEmpty()) L.t("via {0}", via) else L.t("No delays"),
                    color = if (delayMin >= 10) Bad else if (delayMin >= 1) Warn else c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LorryBadge(r)
                if (i == engine.active) LorryCheck(engine, r)
            }
        }
    }
    val start: @Composable (Modifier) -> Unit = { m ->
        Box(m.height(btnH).clip(RoundedCornerShape(36.dp)).background(c.accent.copy(alpha = 0.30f)).carTap("start") { engine.beginNavigation() }, contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress.value).background(c.accent))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(L.t(if (engine.demoNext) "Start demo drive" else if (side) "Start" else "Start navigation"), color = Color.White, fontSize = if (side) 15.sp else 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (autoStart) Text(L.t("Starting in {0} s", kotlin.math.ceil((1f - progress.value) * countdownSeconds).toInt()), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp) else Text(L.t("Tap to start"), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
        }
    }
    // "Demo" (with the demo drive switched on in Settings): the countdown or Start then begins this trip as a demo drive
    val demoOn = engine.prefs.b(PreferenceKeys.DEMO, false)
    val demo: @Composable () -> Unit = {
        val on = engine.demoNext
        Row(Modifier.height(btnH).clip(RoundedCornerShape(36.dp)).background(if (on) c.accent else c.accent.copy(alpha = 0.14f)).carTap("demo") { engine.toggleDemoNext() }
            .padding(horizontal = if (side) 12.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.DirectionsCar, null, tint = if (on) Color.White else c.accent, modifier = Modifier.size(24.dp))
            Text(L.t("Demo"), color = if (on) Color.White else c.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
        }
    }
    val cancel: @Composable () -> Unit = {
        Box(Modifier.size(46.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.tile).carTap("cancel") { engine.endRoute() }, contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Close, null, tint = c.text, modifier = Modifier.size(22.dp))
        }
    }
    if (side) {
        Column(Modifier.fillMaxWidth().floating(24.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(engine.dest?.name ?: "", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 6.dp))
                Box(Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.tile).carTap("cancel") { engine.endRoute() }, contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Close, null, tint = c.text, modifier = Modifier.size(20.dp))
                }
            }
            // each route on two lines: its time with the distance and arrival beside it, then what it is and the way it goes
            rs.forEachIndexed { i, r ->
                val sel = i == engine.active
                val label = L.t(if (i == fastest) "Fastest" else if (rs[i].simpler) "Simplest" else if (i == shortest) "Shortest" else "Alternative")
                val via = viaOf(r)
                val delayMin = Math.round(r.delay / 60.0).toInt()
                Column(Modifier.fillMaxWidth().inset(14.dp, if (sel) c.accent else null).carTap("route-$i") { engine.selectRoute(i) }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(Geo.fmtDur(r.time), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${Geo.fmtDist(r.len, engine.imperial)} · ${fmt.format(java.util.Date(baseMs + (r.time * 1000).toLong()))}", color = c.sub, fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
                    }
                    Text(label + " · " + (if (delayMin >= 1) L.t("+{0} min traffic", delayMin) else if (via.isNotEmpty()) L.t("via {0}", via) else L.t("No delays")),
                        color = if (delayMin >= 10) Bad else if (delayMin >= 1) Warn else if (sel) c.accent else c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LorryBadge(r)
                    if (sel) LorryCheck(engine, r)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (demoOn) demo()
                start(Modifier.weight(1f))
            }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // The horizontal arrangement needs room for the destination, three route cards, Start and Close.
        // A 760dp breakpoint left the route cards squeezed on medium displays, so stack until there is real room.
        if (maxWidth >= 1120.dp) Row(
            Modifier.fillMaxWidth().floating(28.dp).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            dest(Modifier.width(170.dp))
            cards()
            if (demoOn) demo()
            start(Modifier.width(220.dp))
            cancel()
        } else Column(
            // narrow: the destination and close button, the routes side by side, then Start across the width
            Modifier.fillMaxWidth().floating(28.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) { dest(Modifier.weight(1f).padding(start = 6.dp)); cancel() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { cards() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (demoOn) demo()
                start(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Not navigating, under the search bar, as in the app: Home and Work as two tiles with the drive time, five round buttons to find
 * things nearby (they open a list to pick from), then Saved & recent with the first few places ([rows], as many as fit) and "All" for
 * the rest. Every part works by touch on cars that pass taps on.
 */
/**
 * The short-screen version of the chips under the search bar (#67): Home and Work as slim pills, then one row of four shortcuts
 * and ⋯ (the rest, and Saved & recent, are in Places).
 */
@Composable
private fun CarChipsCompact(engine: NavEngine, actions: CarActions) {
    val c = LocalVw.current
    val home = engine.places.slot("home"); val work = engine.places.slot("work")
    // (capped: five of them share one row of a short screen)
    val bs = carButtonScale(engine).coerceAtMost(1.25f)
    LaunchedEffect(Unit) { while (true) { engine.refreshSlotTimes(); kotlinx.coroutines.delay(60_000) } }
    @Composable fun pill(id: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, label: String, place: com.headunit.launcher.Place?, mins: Int?) {
        Row(Modifier.height(38.dp).panel(6.dp, RoundedCornerShape(19.dp), c.surface).carTap(id) { if (place != null) engine.startRoute(place) else actions.search() }
            .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Text(if (place == null) L.t(if (id == "work") "Set work" else "Set home") else label, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 6.dp))
            if (place != null && mins != null && mins > 0) Text(Geo.fmtDur(mins * 60.0), color = c.sub, fontSize = 14.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 6.dp))
        }
    }
    @Composable fun round(id: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, act: () -> Unit) {
        Box(Modifier.size(40.dp * bs).panel(6.dp, androidx.compose.foundation.shape.CircleShape, c.surface).carTap(id, act), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp * bs))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // (each can be left off: Settings > Android Auto, or the car's own settings)
        val showHome = engine.prefs.b(PreferenceKeys.HOME_BTN, true); val showWork = engine.prefs.b(PreferenceKeys.WORK_BTN, true)
        if (showHome || showWork) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showHome) pill("home", androidx.compose.material.icons.Icons.Rounded.Home, Color(0xFF4C8DFF), L.t("Home"), home, engine.slotMins["home"])
            if (showWork) pill("work", androidx.compose.material.icons.Icons.Rounded.Work, Color(0xFFF29900), L.t("Work"), work, engine.slotMins["work"])
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // the shortcuts picked in Settings > Places & trips, in that order (the first four: the row is short), then the rest
            for (sc in chosenShortcuts(engine).take(4)) round("n-${sc.kind}", sc.icon, sc.color) { actions.nearby(sc.kind) }
            round("more", androidx.compose.material.icons.Icons.Rounded.MoreHoriz, c.sub) { actions.places() }
        }
    }
}

@Composable
private fun CarChips(engine: NavEngine, actions: CarActions, rows: Int) {
    val c = LocalVw.current
    val home = engine.places.slot("home"); val work = engine.places.slot("work")
    // the drive times (the engine throttles this itself: online only, every 10 minutes or after moving 3 km)
    LaunchedEffect(Unit) { while (true) { engine.refreshSlotTimes(); kotlinx.coroutines.delay(60_000) } }
    val here = engine.fix?.pos
    @Composable fun tile(id: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, label: String, place: com.headunit.launcher.Place?, mins: Int?, m: Modifier) {
        val sub = when {
            place == null -> L.t("Tap to set")
            mins == 0 -> L.t("You're here")
            mins != null && mins > 0 -> Geo.fmtDur(mins * 60.0)
            here != null -> Geo.fmtDist(Geo.dist(here, place.pos), engine.imperial)
            else -> place.sub
        }
        Row(m.height(58.dp).panel(6.dp, RoundedCornerShape(20.dp), c.surface).carTap(id) { if (place != null) engine.startRoute(place) else actions.search() }
            .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(if (place == null) L.t(if (id == "work") "Set work" else "Set home") else label, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    @Composable fun find(kind: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, sz: androidx.compose.ui.unit.Dp) {
        Box(Modifier.size(sz).panel(6.dp, androidx.compose.foundation.shape.CircleShape, c.surface).carTap("n-$kind") { actions.nearby(kind) }, contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(icon, null, tint = tint, modifier = Modifier.size(sz * 0.48f))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // (each can be left off, here and in the app: Settings > Places & trips, or the car's own settings)
        val showHome = engine.prefs.b(PreferenceKeys.HOME_BTN, true); val showWork = engine.prefs.b(PreferenceKeys.WORK_BTN, true)
        if (showHome || showWork) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showHome) tile("home", androidx.compose.material.icons.Icons.Rounded.Home, Color(0xFF4C8DFF), L.t("Home"), home, engine.slotMins["home"], Modifier.weight(1f))
            if (showWork) tile("work", androidx.compose.material.icons.Icons.Rounded.Work, Color(0xFFF29900), L.t("Work"), work, engine.slotMins["work"], Modifier.weight(1f))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Settings > Android Auto > Button size, kept to what five across the row have room for
            val sz = minOf(50.dp * carButtonScale(engine), (maxWidth - 16.dp) / 5)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // the shortcuts picked in Settings > Places & trips > Shortcuts under the search bar, in that order, as in the
                // app (they used to be a fixed five here, whatever was picked)
                for (sc in chosenShortcuts(engine)) find(sc.kind, sc.icon, sc.color, sz)
            }
        }
        // your own places first, then favourites, then recent ones (as in the app)
        val rawC = engine.prefs.s(PreferenceKeys.PLACES_CUSTOM, ""); val rawF = engine.prefs.s(PreferenceKeys.PLACES_FAVOURITES, ""); val rawR = engine.prefs.s(PreferenceKeys.PLACES_RECENT, "")
        val saved = remember(rawC, rawF, rawR) {
            val mine = engine.places.customs()
            val favs = engine.places.favourites().filter { f -> mine.none { Geo.dist(it.place.pos, f.pos) < 60 } }
            val recents = engine.places.recents().filter { r -> favs.none { Geo.dist(it.pos, r.pos) < 60 } && mine.none { Geo.dist(it.place.pos, r.pos) < 60 } }
            mine.map { Triple(it.place, true, if (it.group.isNotBlank()) "${it.label} · ${it.group}" else it.label) } + favs.map { Triple(it, true, it.name) } + recents.take(4).map { Triple(it, false, it.name) }
        }
        // (folded away with the arrow in the search bar)
        if (engine.prefs.b(PreferenceKeys.QUICKLIST, true)) Column(Modifier.fillMaxWidth().panel(10.dp, RoundedCornerShape(24.dp), c.surface).padding(vertical = 2.dp)) {
            Row(Modifier.fillMaxWidth().carTap("saved") { actions.places() }.padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(L.t("SAVED & RECENT"), color = c.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
                Text(if (saved.size > rows) L.t("All {0}", saved.size) else L.t("Open"), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            for ((i, tr) in saved.take(rows).withIndex()) {
                val (p, fav, label) = tr
                Row(Modifier.fillMaxWidth().carTap("saved-$i") { engine.startRoute(p) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.tile), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(if (fav) androidx.compose.material.icons.Icons.Rounded.Star else androidx.compose.material.icons.Icons.Rounded.History, null,
                            tint = if (fav) Warn else c.sub, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(label, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (p.sub.isNotBlank()) Text(p.sub, color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (here != null) Text(Geo.fmtDist(Geo.dist(here, p.pos), engine.imperial), color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
