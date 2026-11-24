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
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clipToBounds
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
    OnChange(light.nightBright, light.dayBright, light.moon, light.moonStr, light.shadows, light.sunReal, light.overcast, light.weatherLight) { map.setLightOpts(light) }
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
    val satOn = prefs.b(PreferenceKeys.SATELLITE, false) && !com.headunit.launcher.Plus.free
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
    val ksToken = com.headunit.launcher.KeyServer.mapbox
    val plusOn = com.headunit.launcher.Plus.active
    OnChange(style, traffic, key, mbToken, mbLook, ksToken, plusOn) { engine.applyStyle(dark.value) }
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

/**
 * Overall size (Settings > Android Auto) used to make the app think the car screen was smaller: everything is laid out in the
 * enlarged units, so past about 150% a roomy screen fell into the cramped small-screen layouts (cards squashed, labels dropped,
 * the speedometer smaller) and the room kept for Android Auto's own buttons grew with it (magnazan, #234).
 * So the layout is now picked from the screen's size at 100%, and the size is held at the most that layout still fits; the
 * cards then lay themselves out as before, in the larger units. (Making things smaller than 100% is never held back.)
 */
@Composable
private fun CarScreen(engine: NavEngine, a: android.graphics.Rect?, actions: CarActions, onCover: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val user = engine.prefs.i(PreferenceKeys.CARSCALE, 100).coerceIn(40, 300) / 100f
        // the screen in the units it would have at 100%
        val rawW = maxWidth.value * user; val rawH = maxHeight.value * user
        // The layout (side by side or stacked, short or roomy) is now picked from the screen at 100% (in CarScreenBody), so the size
        // no longer has to stay on the roomy side of every layout switch: that held 240% down to about 112-134% on most cars, as the
        // switches that only change the spacing bound first (magnazan, #242). The only limit left is that the cards still have room:
        // the screen, in the enlarged units, stays at least 560 wide and 300 high.
        val cap = minOf(rawW / 560f, rawH / 300f).coerceAtLeast(1f)
        val eff = if (user <= 1f) user else minOf(user, cap).coerceAtLeast(1f)
        // (the most this screen fits, for the setting to say)
        val most = (Math.floor(cap.coerceAtMost(3f) * 20.0) * 5).toInt().coerceAtLeast(100)
        androidx.compose.runtime.SideEffect { if (engine.prefs.i(PreferenceKeys.CARSCALE_MAX, 0) != most) engine.prefs.put(PreferenceKeys.CARSCALE_MAX, most) }
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density * eff / user, d.fontScale)) {
            CarScreenBody(engine, a, actions, onCover, eff)
        }
    }
}

/** The car screen's width, in the units the cards are laid out in (so a card's minimum width can't be wider than the room). */
private val LocalCarWidth = androidx.compose.runtime.compositionLocalOf { 1000.dp }

@Composable
private fun CarScreenBody(engine: NavEngine, a: android.graphics.Rect?, actions: CarActions, onCover: (Int) -> Unit, sizeScale: Float) {
    BoxWithConstraints(Modifier.fillMaxSize()) { CompositionLocalProvider(LocalCarWidth provides maxWidth) {
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
        // the screen's size at 100% (or as made smaller): which layout to use is picked from this, not from the enlarged units, so a
        // bigger Overall size makes everything bigger in the same layout instead of switching to the cramped small-screen one (#242)
        val layoutW = maxWidth * sizeScale.coerceAtLeast(1f)
        val layoutH = maxHeight * sizeScale.coerceAtLeast(1f)
        // Include shorter usable areas common in split-screen Android Auto and ultra-wide dashboards.
        // Use the available content bounds, not physical display dimensions, so insets are respected.
        val lowVertical = layoutH < 460.dp
        val narrow = layoutW < 900.dp
        val wideLandscape = aspect > 2.0f
        val compact = lowVertical || narrow || wideLandscape
        // only a really short screen leaves the junction close-up and advice off (a 7-inch 800 x 480 unit counts as compact, but has room)
        val tiny = layoutH < 330.dp
        // short, wide screens (a 7-inch 800 x 480 unit) are laid out side by side, not stacked: stacked, the cards took the whole
        // width and ran into each other and the speed (#67)
        val shortWide = lowVertical && aspect >= 1.4f
        val portrait = maxHeight > maxWidth * 1.1f || (narrow && !shortWide)
        // the layout for the space Android Auto actually leaves (the same car gets the roomy layout full-screen and a small one in
        // a split-screen tile): "short" keeps the essentials and one row of shortcuts; "tile" (split screen) only the turn, a
        // one-line trip strip and the speed. Text stays its normal size: the layout gets simpler instead of smaller
        val tile = tiny || (layoutW < 600.dp && lowVertical)
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
        // the space round the edge: from 8 on a small screen to 24 on a wide one, smoothly (it used to jump at set widths, and those
        // jumps were among the limits that held the Overall size back)
        val edge = if (portrait && layoutW >= 600.dp) 10.dp else (8f + 16f * ((layoutW.value - 600f) / 450f).coerceIn(0f, 1f)).dp
        val left = insL + edge
        // Wide screens: level with Android Auto's buttons (top right); narrow/tall screens: below them.
        val topTarget = if (portrait) insT + edge else insT.coerceAtMost(12.dp) + edge
        // On tall screens Android Auto's buttons hide after a few seconds and the cards move up smoothly.
        val top by androidx.compose.animation.core.animateDpAsState(topTarget, tween(300), label = "carTop")
        // (Android Auto's own buttons don't grow with Overall size, so the room kept for them is taken back out of it)
        val aaUnit = 1f / sizeScale.coerceAtLeast(1f)
        val controlsGap = when {
            portrait -> 64.dp
            maxWidth * sizeScale < 900.dp -> 68.dp
            maxWidth * sizeScale < 1250.dp -> 80.dp
            else -> 92.dp
        } * aaUnit
        val rightTop = insR + if (portrait) edge else controlsGap
        // Android Auto's zoom and re-centre buttons, bottom right; reserve less space on smaller displays.
        val rightBottom = insR + if (portrait) 64.dp * aaUnit else controlsGap
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
            minOf((380f + 60f * ((layoutW.value - 1000f) / 100f).coerceIn(0f, 1f)).dp, maxWidth * 0.38f)
        }
        // (the turn card's width, which the trip card under it on the same side takes too, so the two line up)
        // (Settings › Android Auto › sizes › Turn card width, ZarulOfficial #317; never more than about half the screen)
        val turnWK = engine.prefs.i(PreferenceKeys.CAR_TURN_WIDTH, 100).coerceIn(60, 130) / 100f
        val turnCardW = (if (portrait) topW else if (small) minOf(340.dp, maxWidth * 0.42f) else minOf(420.dp, maxWidth * 0.36f)).let { (it * turnWK).coerceAtMost(maxWidth * (if (portrait) 1f else 0.55f)) }
        var turnH by remember { mutableStateOf(0) }
        var turnW by remember { mutableStateOf(0) }
        // the turn card and what's under it, without the lane picture, and the cards along the bottom on the same side: the lane
        // picture gets the room left between them
        var aboveH by remember { mutableStateOf(0) }
        var bottomColH by remember { mutableStateOf(0) }
        var tripColH by remember { mutableStateOf(0) }
        var optionalColH by remember { mutableStateOf(0) }
        var topBlockH by remember { mutableStateOf(0) }

        // where the cards are, for placing your arrow the way the app does: on wide screens it moves sideways clear of the cards on
        // the left; on tall ones it stays centred and sits just above the cards along the bottom. Measured within the part of the
        // screen Android Auto leaves visible (the map's camera works within that part too).
        val vL = (a?.left ?: 0).toFloat(); val vT = (a?.top ?: 0).toFloat()
        val vW = (a?.width()?.toFloat() ?: wPx).coerceAtLeast(1f); val vH = (a?.height()?.toFloat() ?: hPx).coerceAtLeast(1f)
        fun report(i: Int, r: androidx.compose.ui.geometry.Rect) =
            engine.setPanel(i, (r.left - vL) / vW, (r.top - vT) / vH, (r.right - vL) / vW, (r.bottom - vT) / vH)
        // (Search bar and places on the map switched off: nothing drawn top left with no route, so the map has the whole screen)
        // ...and with Hide them after set, they fold away once the screen hasn't been touched for that long (a touch anywhere brings
        // them back: CarButtons is poked by every tap, drag and pinch), for a clean map with no route (ZarulOfficial #315)
        val hideAfter = engine.prefs.s(PreferenceKeys.CAR_MAPUI_HIDE, "0").toIntOrNull() ?: 0
        var uiAsleep by remember { mutableStateOf(false) }
        LaunchedEffect(CarButtons.shownAt, hideAfter, engine.route == null) {
            uiAsleep = false
            if (hideAfter > 0 && engine.route == null) { kotlinx.coroutines.delay(hideAfter * 1000L); uiAsleep = true }
        }
        val mapUi = engine.prefs.b(PreferenceKeys.CAR_MAPUI, true) && !(uiAsleep && !CarSearch.open)
        // (the sound button when not navigating can be left off: Settings > Android Auto)
        val soundBtn = engine.prefs.b(PreferenceKeys.CAR_SOUND_BTN, true)
        val topShown = !portrait && !arriving && ((engine.route == null && mapUi) || (guiding && engine.turn != null))
        val colShown = !portrait && !previewing && !arriving
        val stackShown = portrait && !previewing && !arriving
        androidx.compose.runtime.SideEffect {
            if (!topShown) engine.setPanel(0, 0f, 0f, 0f, 0f)
            if (!colShown) engine.setPanel(1, 0f, 0f, 0f, 0f)
            if (!stackShown) engine.bottomCover = 0.0
        }

        // the turn card, top left (as in the app)
        val t = engine.turn
        val showTurn = guiding && t != null && !arriving
        // hazards ahead (an incident, a speed camera) go along the bottom of the turn card when it's showing, instead of a
        // separate pill: the left side is then just the turn card and the trip card
        val alert = engine.alert
        val inc = engine.incidentAhead; val cam = engine.cameraDist
        // roadworks on the turn card show once, as the line on the card: the pop-up for them is left out while the card is
        // there (desko1973, #309). Accidents and closures still get their pop-up.
        val worksOnCard = showTurn && inc != null && inc.label == "Roadworks"
        val incShown = inc != null && (alert?.kind != "incident" || worksOnCard)
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
        // Keep Android Auto's Stop available for the column, which can be clipped on a short split screen.
        val endOnCard = guiding && !arriving && !tile && !tripCol && "end" in carTripButtonsOn(engine.prefs)
        androidx.compose.runtime.SideEffect { com.headunit.launcher.car.CarLayout.endOnCard = endOnCard }
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
        val optionalRoom = (maxHeight - top - bottom - with(dens) { (aboveH + bottomColH + (if (tripCol) tripColH else 0) + (if (speedUnderTurn) speedUnderH else 0)).toDp() } - 28.dp - (if (speedUnderTurn) 8.dp else 0.dp)).coerceAtLeast(0.dp)
        val laneRoom = (optionalRoom - with(dens) { (if (tripCol) optionalColH else 0).toDp() }).coerceAtLeast(0.dp)
        // the lines along the bottom of the turn card (average speed check, services, parking...: they were cards of their own
        // under it): as many as leave the turn card clear of what's at the bottom of the screen (the trip card, the speed), one
        // taken away when it would reach them and one given back when there's room again (with a margin, so it can't flicker)
        val extraCap = if (tile) 1 else if (small) 2 else 4
        var extraMax by remember { mutableStateOf(extraCap) }
        // the card's height when it last had one line more: a line is only given back when there's room for that much again. A car
        // park line is taller than the 48 dp margin, so near the end it was taken away and given back over and over (Dotancom
        // #324). Forgotten when the lines themselves change
        val extraWhat = "${engine.parkingNear.size}|${engine.avgInfo != null}|${engine.lorryWarn != null}|${engine.servicesAhead.size}|${engine.advice != null}"
        val grownH = remember(extraWhat) { intArrayOf(0) }
        val extraFreePx = with(dens) { (maxHeight - top - bottom - 12.dp).toPx() } - bottomColH - aboveH - (if (tripCol) tripColH else 0) - (if (speedUnderTurn) speedUnderH else 0)
        // (judged a moment after each change, once the card has been laid out again: judged straight away, the card's height was
        // still the old, taller one, so every line was taken away in one go and the car parks never came back, Dotancom #324)
        androidx.compose.runtime.LaunchedEffect(extraMax, extraCap, extraWhat, extraFreePx.toInt()) {
            kotlinx.coroutines.delay(150)
            val step = with(dens) { 48.dp.toPx() }
            val need = if (grownH[0] > aboveH) (grownH[0] - aboveH) + with(dens) { 8.dp.toPx() } else step
            if (extraMax > extraCap) extraMax = extraCap
            else if (extraFreePx < 0f && extraMax > 0) { grownH[0] = maxOf(grownH[0], aboveH); extraMax -= 1 }
            else if (extraFreePx > need && extraMax < extraCap) extraMax += 1
        }
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
                            // a lorry's limit ahead, an average speed check (magnazan, #177), the next services, car parks near the
                            // end and advice for the conditions, as lines of the card instead of cards stacked under it
                            // (car parks are their own card again, under it: liked better that way)
                            TurnCardExtras(engine, extraMax, advice = !small, parking = false)
                        })
                }
                // the next services: a card of its own under the turn card again, not joined to it (not on a tile: no room)
                // ...and car parks near the end, a card of their own too, as they were before they joined the turn card
                if (!tile && !tripCol) CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { ServicesCard(engine); ParkingCard(engine) }
                }
              }
                // Speed position "Under the turn card": in the left-hand column the app's own cards already keep clear
                if (speedUnderTurn) Box(Modifier.onSizeChanged { speedUnderH = it.height }) {
                    CompositionLocalProvider(LocalDensity provides Density(dens.density * speedScale, dens.fontScale)) { ImmSpeed(engine) }
                }
                if (tripCol) Box(Modifier.onSizeChanged { tripColH = it.height }) {
                    CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) { TripColumn(engine, showEnd = !tile) }
                }
                if (!tile && tripCol) Box(Modifier.heightIn(max = optionalRoom).clipToBounds().onSizeChanged { optionalColH = it.height }) {
                    CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { ServicesCard(engine); ParkingCard(engine) }
                    }
                }
                // Leave the remaining space to the close-up, after the trip card and its controls.
                if (!small) JunctionCloseup(engine, maxH = laneRoom)
            }
        }

        // not navigating: the app's search bar and quick chips, top left (tap them as in the app)
        // as many saved places as fit above the sound pill / speed (0 to 3), so the list never runs into what's below it
        // (worked out here: the screen's height can't be read from inside the column below)
        // The height the chips under the bar may take: down to the column at the bottom left (the sound pill at its button size,
        // the speed, hazards), all measured. Fixed guesses (242 + 96 dp) used to leave out the green suggestion card and the button
        // size, so on a short screen Recent ran under "Alerts only". CarChips fits the suggestion and Recent rows into it.
        var chipsPx by remember { mutableStateOf(0) }
        val chipsBudget = with(dens) {
            if (topBlockH == 0 || chipsPx == 0) 0.dp
            else (maxHeight - top - bottom - bottomColH.toDp() - 10.dp - (topBlockH - chipsPx).toDp()).coerceAtLeast(0.dp)
        }
        // (a car with a rotary knob and no touchscreen can't reach any of this: Settings › Android Auto can leave it off, and
        // Places (★) in Android Auto's own buttons has Search, Home, Work and the rest, wass0035 #222)
        // a trip paused by a stop on the way (or stopped a few minutes ago): "Carry on to …", which starts by itself after 10 seconds
        // unless "Not now" is tapped, as Waze does (pc4096). It takes the search bar's place while it's up
        val resumeTo = if (engine.route == null && !arriving && !covered && !engine.busy) engine.resume else null
        if (resumeTo != null) Column(Modifier.padding(start = left, top = top).width(topW).onGloballyPositioned { if (topShown) report(0, it.boundsInRoot()) }
            .panel(10.dp, RoundedCornerShape(22.dp), c.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
            val left10 = remember(resumeTo) { androidx.compose.animation.core.Animatable(1f) }
            LaunchedEffect(resumeTo) {
                left10.animateTo(0f, androidx.compose.animation.core.tween(10_000, easing = androidx.compose.animation.core.LinearEasing))
                if (engine.resume === resumeTo && engine.route == null) engine.startRoute(resumeTo, startNow = true)
            }
            Text(L.t("Carry on to"), color = c.sub, fontSize = 13.sp)
            Text(resumeTo.name, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (resumeTo.sub.isNotBlank()) Text(resumeTo.sub, color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.line)) {
                Box(Modifier.fillMaxWidth(left10.value).fillMaxHeight().background(Good))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(c.accent).carTap("resume-go") { engine.startRoute(resumeTo, startNow = true) }
                    .padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text(L.t("Go") + " (" + kotlin.math.ceil(left10.value * 10f).toInt() + ")", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(c.line).carTap("resume-no") { engine.forgetResume() }
                    .padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text(L.t("Not now"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        else if (engine.route == null && !arriving && !covered && mapUi) Column(Modifier.padding(start = left, top = top).width(topW).onSizeChanged { topBlockH = it.height }.onGloballyPositioned { if (topShown) report(0, it.boundsInRoot()) },
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
                if (short) CarChipsCompact(engine, actions) else Box(Modifier.onSizeChanged { chipsPx = it.height }) { CarChips(engine, actions, chipsBudget) }
            }
        }

        // alerts (cameras, limits, hazards...): beside the turn card on wide screens, under it on tall ones
        val below = if (showTurn && turnH > 0) with(dens) { turnH.toDp() } + 10.dp else if (engine.route == null && topBlockH > 0) with(dens) { topBlockH.toDp() } + 10.dp else 0.dp
        val alertMod = if (portrait) Modifier.align(Alignment.TopStart).padding(start = left, end = rightTop + 64.dp, top = top + below)
            // (beside the turn card, from where it actually ends: it was started 42% of the way across, which a turn card of that
            // width, set in from the edge, ran a little past, so the two overlapped)
            else Modifier.align(Alignment.TopCenter).padding(top = top,
                start = if (showTurn && turnW > 0) left + with(dens) { turnW.toDp() } + 10.dp else if (guiding) maxWidth * 0.42f + left else left, end = maxOf(rightTop, maxWidth * 0.30f))
        // (only that roadworks pop-up: another one, "Road closed ahead", still shows)
        val worksPopup = worksOnCard && alert?.kind == "incident" && alert?.text?.startsWith(L.t("Roadworks")) == true
        AnimatedVisibility(alert != null && !altsOpen && !worksPopup, modifier = alertMod, enter = fadeIn(), exit = fadeOut()) { alert?.let { AlertBanner(it) } }

        // the search card: over the map from the left edge to Android Auto's zoom buttons (its other buttons give way to one
        // Close button while it's open, see MapScreen)
        if (searching) CarSearchPanel(engine, actions, small, Modifier.padding(start = left, top = top, end = insR + 8.dp, bottom = bottom).fillMaxSize())

        if (stopsOpen) CarStopsPanel(engine, actions, small, Modifier.padding(start = left, top = top, end = insR + 8.dp, bottom = bottom).fillMaxSize())
        // a place's card (tapped on the map, or picked from the search): Go, or Add stop on a route
        if (!searching && !arriving && !stopsOpen && !settingsOpen) engine.here?.let { h ->
            Box(Modifier.align(Alignment.BottomCenter).padding(start = left, end = rightBottom, bottom = bottom)) { CarHereCard(engine, h) }
        }
        // (in the middle of what's clear of the turn card, which stays up)
        if (choosing) engine.choose?.let { p ->
            Box(Modifier.align(Alignment.BottomCenter).padding(start = left, end = rightBottom, bottom = bottom)) { CarChooseCard(engine, p) }
        }

        if (settingsOpen) {
            CarSettingsPanel(engine, actions, small, Modifier.padding(start = left, top = top, end = insR + 8.dp, bottom = bottom).fillMaxSize())
        }
        // quick reports (the flag on the trip card): top right, beside Android Auto's own buttons (GitHub #290)
        if (CarReportCard.open && guiding && !covered && !arriving) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = top, end = rightTop + 8.dp)) { CarReportPanel(engine) }
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
                if (!guiding && !small && soundBtn) CompositionLocalProvider(LocalDensity provides Density(dens.density * buttonScale, dens.fontScale)) {
                    ImmRound(engine.soundIcon(), "sound", { engine.cycleSound() }, Modifier.align(Alignment.TopEnd).padding(end = rightTop, top = top + below))
                }
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = left, bottom = bottom)
                        .width((maxWidth - left - rightBottom).coerceAtLeast(0.dp).coerceAtMost(620.dp)).onSizeChanged { bottomColH = it.height }
                        .onGloballyPositioned { engine.bottomCover = ((vT + vH - it.boundsInRoot().top) / vH).coerceIn(0f, 0.6f).toDouble() },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (guiding && tripCol) CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) { FasterCard(engine, Modifier.fillMaxWidth()) }
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
                    if (guiding && tripCol) CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) { FasterCard(engine, Modifier.widthIn(max = 520.dp)) }
                    // (while guiding, the sound button is on the trip card instead of a pill of its own)
                    if (!guiding && !small && soundBtn) CompositionLocalProvider(LocalDensity provides Density(dens.density * buttonScale, dens.fontScale)) { SoundPill(engine) }
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
                        // (Add stop doesn't count: it sits at the end of the route bar, not among the buttons)
                        val sameW = (carTripButtonsOn(engine.prefs) - "stop").size <= 2
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
            // Keep the route text at its normal size on short car screens; the list scrolls within the room available.
            val boxTop = if (portrait) top + below else insT + 6.dp
            val room = (maxHeight - boxTop - bottom).coerceAtLeast(1.dp)
            Box(Modifier.align(Alignment.TopEnd).padding(end = insR + 8.dp, top = boxTop, bottom = bottom)) {
                RoutesBox(engine, Modifier.heightIn(max = room), maxH = room)
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
    } }
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
        Text(L.t(when (sm) { 0 -> "Sound on"; 1 -> "Alerts only"; else -> "Muted" }), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
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
        Text(Geo.fmtDur(e.secs), color = dc, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Text(e.arrive, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Text(Geo.fmtDist(e.dist, engine.imperial), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        LiveFuelLine(engine, column = true)
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
        // Add stop sits at its far end, in the height the bar already takes, so the card is no wider for it (among the buttons it
        // pushed the card out over the road ahead); with no bar it goes with the other buttons
        val onTrip = if (buttonsShown) carTripButtonsOn(engine.prefs) else emptySet()
        val barShown = engine.showRouteBar && (!oneLine || bar)
        val pinOnBar = barShown && "stop" in onTrip
        // "Faster route found" across the top of the card (it was a box of its own floating above it)
        FasterStrip(engine, w)
        if (pinOnBar) Row(w.padding(start = 16.dp, end = 10.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RouteBar(engine, Modifier.weight(1f), thin = true)
            Box(Modifier.padding(start = 8.dp).size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.line).carTap("tripstop") { actions.stops() },
                contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.AddLocationAlt, L.t("Add stop"), tint = c.text, modifier = Modifier.size(19.dp))
            }
        } else if (barShown) RouteBar(engine, w.padding(start = 16.dp, end = 16.dp, top = 6.dp), thin = true)
        // stops on the way: the next one and when you get there, over the figures for the whole trip (tap for the stops card)
        val nextStop = engine.via.firstOrNull()
        if (nextStop != null) Row(w.carTap("trip-stop") { actions.stops() }.padding(start = 16.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val n = engine.via.size
            // (sized from the text, so it grows with Text size instead of the number spilling out of it)
            Box(Modifier.size(with(LocalDensity.current) { 22.sp.toDp() }).clip(androidx.compose.foundation.shape.CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                Text(if (n > 1) "$n" else "1", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text(nextStop.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp, end = 8.dp))
            engine.stopEtas.firstOrNull { it.place === nextStop }?.let { s ->
                Text(s.arrive + " · " + Geo.fmtDur(s.secs), color = Good, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
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
        val stats: @Composable androidx.compose.foundation.layout.RowScope.(Boolean) -> Unit = { share ->
            // ([share]: a set width (as wide as the turn card), split evenly, each figure's text shrinking to fit its part: spread out
            // with nothing between them, they ran into each other when the card was narrow, "19:206.2 …")
            val m = if (share) Modifier.weight(1f) else Modifier
            CarStat(Geo.fmtDur(e.secs), if (e.delayMin >= 1) "+${e.delayMin} min" else L.t("left"), dc, m, share)
            CarStat(e.arrive, L.t("arrive"), c.text, m, share)
            CarStat(Geo.fmtDist(e.dist, engine.imperial), L.t("to go"), c.text, m, share)
            engine.evCardPct()?.let { p -> CarStat("$p%", L.t("battery"), if (p < 10) Bad else if (p < 20) Warn else Good, m, share) }
        }
        // the whole route, and other routes (as in the app): Android Auto has no room left for them among its own buttons
        val small: @Composable (androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit, androidx.compose.ui.unit.Dp) -> Unit = { icon, id, act, sz ->
            Box(Modifier.size(sz).clip(androidx.compose.foundation.shape.CircleShape).background(c.line).carTap(id, act), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(icon, null, tint = c.text, modifier = Modifier.size(sz * 0.52f))
            }
        }
        // only the buttons chosen (Settings > Android Auto > Buttons on the trip card), at the size chosen there; with fewer of them
        // the card is narrower and leaves more of the map clear
        val on = if (pinOnBar) onTrip - "stop" else onTrip
        // a short screen or a split-screen tile: one line, the figures without their labels, then the buttons kept
        if (oneLine) {
            val grow1 = (when (engine.prefs.s(PreferenceKeys.CAR_TRIP_BTN_SIZE, "normal")) { "large" -> 1.25f; "xl" -> 1.5f; else -> 1f }) * carButtonScale(engine)
            // (measured, so the route bar over it can be made the same width)
            Row((if (fill) Modifier.fillMaxWidth() else Modifier).onSizeChanged { if (it.width != rowW) rowW = it.width }.padding(start = 14.dp, end = if (on.isEmpty()) 14.dp else 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val sz = 36.dp * grow1
                // (the figures open the stops card)
                Row(Modifier.carTap("trip-figs") { actions.stops() }, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Geo.fmtDur(e.secs), color = dc, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    Text(e.arrive, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    Text(Geo.fmtDist(e.dist, engine.imperial), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                // (across a set width, the buttons go to the far end)
                if (fill) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                // (just the one that ends the route, unless others are picked in Settings > Android Auto > Buttons on the trip card)
                if (on.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if ("overview" in on) small(androidx.compose.material.icons.Icons.Rounded.ZoomOutMap, "overview", { engine.overview() }, sz)
                    if ("routes" in on) small(androidx.compose.material.icons.Icons.Rounded.AltRoute, "altroutes", { engine.findAlternatives() }, sz)
                    if ("sound" in on) sound(sz)
                    if ("stop" in on) small(androidx.compose.material.icons.Icons.Rounded.AddLocationAlt, "tripstop", { actions.stops() }, sz)
                    if ("search" in on) small(androidx.compose.material.icons.Icons.Rounded.Search, "tripsearch", { actions.search() }, sz)
                    if ("report" in on) small(androidx.compose.material.icons.Icons.Rounded.Campaign, "tripreport", { CarReportCard.open = !CarReportCard.open }, sz)
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
            // add a stop: the stops card (the phone's pin button), with fuel and the like along the route one tap away
            if ("stop" in on) small(androidx.compose.material.icons.Icons.Rounded.AddLocationAlt, "tripstop", { actions.stops() }, sz)
            // search while navigating: a stop on the way, or somewhere else to go
            if ("search" in on) small(androidx.compose.material.icons.Icons.Rounded.Search, "tripsearch", { actions.search() }, sz)
            if ("report" in on) small(androidx.compose.material.icons.Icons.Rounded.Campaign, "tripreport", { CarReportCard.open = !CarReportCard.open }, sz)
            if ("end" in on) end(sz)
        }
        if (fill) Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).carTap("trip-figs") { actions.stops() }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { stats(true) }
            if (on.isNotEmpty()) Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { buttons(36.dp) }
        } else Row(Modifier.widthIn(min = if ((on - "stop").size >= 3) minOf(400.dp, LocalCarWidth.current * 0.45f) else 0.dp).onSizeChanged { if (it.width != rowW) rowW = it.width }.padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.carTap("trip-figs") { actions.stops() }, horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) { stats(false) }
            if (on.isNotEmpty()) Row(Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { buttons(40.dp) }
        }
        // what the trip has cost so far (#316; not on the one-line card of a short screen)
        if (!oneLine) LiveFuelLine(engine, Modifier.padding(start = 16.dp, end = 12.dp, bottom = 8.dp))
    }
}

/** Settings > Android Auto > Button size, for the round buttons the app draws on the car screen. */
private fun carButtonScale(engine: NavEngine): Float = engine.prefs.i(PreferenceKeys.CAR_BUTTON_SIZE, 100).coerceIn(70, 150) / 100f

/** A figure on the car trip card, with a label big enough to read at a glance (the app's own labels were tiny on a car screen). */
@Composable
private fun CarStat(value: String, label: String, color: Color, modifier: Modifier = Modifier, fit: Boolean = false) {
    val c = LocalVw.current
    Column(modifier) {
        // ([fit]: shrinks a little, down to 15sp, to fit a narrow share of the card, before it would ever be cut short)
        if (fit) androidx.compose.foundation.text.BasicText(value, style = androidx.compose.ui.text.TextStyle(color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 15.sp, maxFontSize = 22.sp, stepSize = 1.sp))
        else Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Text(label, color = c.sub, fontSize = 14.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** One hazard along the bottom of the car turn card: its road sign, what it is and how far. */
@Composable
private fun CarHazardLine(kind: String, label: String, dist: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AlertSign(com.headunit.launcher.AlertInfo(kind, label), 22.dp)
        Text("$label " + L.t("in {0}", dist), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
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
    // roughly what each route costs in fuel (or charging), from Settings › Fuel, as on the phone (pc4096: it wasn't on Android Auto)
    val ev = engine.prefs.s(PreferenceKeys.FUEL_TYPE, "E10") == "EV"
    val mpg = engine.prefs.s(if (ev) "evmpk" else "fuelmpg", "").toDoubleOrNull()?.takeIf { it > 0 }
    val ppl = pricePence(engine.prefs.s(if (ev) "evppk" else "fuelppl", ""), ev, engine.prefs)
    val cost: @Composable (com.headunit.launcher.Route) -> Unit = { r ->
        if (mpg != null && ppl != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            androidx.compose.material3.Icon(if (ev) androidx.compose.material.icons.Icons.Rounded.EvStation else androidx.compose.material.icons.Icons.Rounded.LocalGasStation, null, tint = c.sub, modifier = Modifier.size(13.dp))
            Text((if (ev) L.t("Charging about ") else L.t("Fuel about ")) + formatMoney(engine.prefs, 
                if (ev) r.len / 1609.344 / mpg * ppl / 100.0 else r.len / 1609.344 / mpg * 4.54609 * ppl / 100.0),
                color = c.text.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
        }
    }
    val dest: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Text(L.t("Destination"), color = c.sub, fontSize = 12.sp)
            Text(engine.dest?.name ?: "", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    val cards: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        rs.forEachIndexed { i, r ->
            val sel = i == engine.active
            val label = L.t(if (r.mine.isNotBlank()) "Your route" else if (i == fastest) "Fastest" else if (rs[i].simpler) "Simplest" else if (i == shortest) "Shortest" else "Alternative")
            Column(Modifier.weight(1f).inset(18.dp, if (sel) c.accent else null).carTap("route-$i") { engine.selectRoute(i) }.padding(horizontal = 12.dp, vertical = 9.dp)) {
                Text(label.uppercase(), color = if (sel) c.accent else c.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
                Text(Geo.fmtDur(r.time), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${Geo.fmtDist(r.len, engine.imperial)} · arrive ${fmt.format(java.util.Date(baseMs + (r.time * 1000).toLong()))}", color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val via = viaOf(r)
                val delayMin = Math.round(r.delay / 60.0).toInt()
                val mine = mineLine(rs, r)
                Text(mine ?: if (delayMin >= 1) L.t("+{0} min traffic", delayMin) else if (via.isNotEmpty()) L.t("via {0}", via) else L.t("No delays"),
                    color = if (mine != null) Good else if (delayMin >= 10) Bad else if (delayMin >= 1) Warn else c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                cost(r)
                LorryBadge(r)
                if (i == engine.active) LorryCheck(engine, r)
            }
        }
    }
    val start: @Composable (Modifier) -> Unit = { m ->
        Box(m.height(btnH).clip(RoundedCornerShape(36.dp)).background(c.accent.copy(alpha = 0.30f)).carTap("start") { engine.beginNavigation() }, contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress.value).background(c.accent))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(L.t(if (engine.demoNext) "Start demo drive" else if (side) "Start" else "Start navigation"), color = Color.White, fontSize = if (side) 15.sp else 17.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp))
                Text(if (autoStart) L.t("Starting in {0} s", kotlin.math.ceil((1f - progress.value) * countdownSeconds).toInt()) else L.t("Tap to start"), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp))
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
    // a clean air or low emission zone on the way (a charge may apply), with Avoid zones, as in the app; then the weather along
    // the route ([withWeather]: left off on a small screen)
    val extras: @Composable (Boolean) -> Unit = { withWeather ->
        val z = engine.routeZones
        if (z.isNotEmpty()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Warn.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Eco, null, tint = Warn, modifier = Modifier.size(18.dp))
            Text(L.t("Goes into ") + z.joinToString(", ") + L.t(". A charge may apply."), color = c.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp))
            if (!engine.prefs.b(PreferenceKeys.AVOIDZONES, false)) Box(Modifier.clip(RoundedCornerShape(16.dp)).background(c.tile)
                .carTap("avoid-zones") { engine.prefs.put(PreferenceKeys.AVOIDZONES, true); engine.dest?.let { engine.startRoute(it, engine.departMin, true) } }
                .padding(horizontal = 12.dp, vertical = 7.dp)) { Text(L.t("Avoid zones"), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
        if (withWeather && engine.routeWx.isNotEmpty()) {
            val fahr = isImperial(engine.prefs)
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(L.t("Weather on route"), color = c.sub, fontSize = 12.sp, maxLines = 1)
                engine.routeWx.take(3).forEach { w ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(wxIcon(w.code), null, tint = wxTint(w.code), modifier = Modifier.size(18.dp))
                        Text((if (fahr) Math.round(w.temp * 9 / 5 + 32) else Math.round(w.temp)).toString() + "°" + (if (w.rain >= 40) " · ${w.rain}%" else ""),
                            color = if (w.rain >= 60) Warn else c.text, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
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
            // (on a short screen the routes scroll rather than pushing the Start button out of the bottom of the card, where it was
            // cut in half)
            Column(Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rs.forEachIndexed { i, r ->
                val sel = i == engine.active
                val label = L.t(if (r.mine.isNotBlank()) "Your route" else if (i == fastest) "Fastest" else if (rs[i].simpler) "Simplest" else if (i == shortest) "Shortest" else "Alternative")
                val via = viaOf(r)
                val delayMin = Math.round(r.delay / 60.0).toInt()
                Column(Modifier.fillMaxWidth().inset(14.dp, if (sel) c.accent else null).carTap("route-$i") { engine.selectRoute(i) }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(Geo.fmtDur(r.time), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${Geo.fmtDist(r.len, engine.imperial)} · ${fmt.format(java.util.Date(baseMs + (r.time * 1000).toLong()))}", color = c.sub, fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
                    }
                    val mine = mineLine(rs, r)
                    Text(mine ?: (label + " · " + (if (delayMin >= 1) L.t("+{0} min traffic", delayMin) else if (via.isNotEmpty()) L.t("via {0}", via) else L.t("No delays"))),
                        color = if (mine != null) Good else if (delayMin >= 10) Bad else if (delayMin >= 1) Warn else if (sel) c.accent else c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // (just the chosen route's, so the list doesn't grow past a short screen)
                    if (sel) cost(r)
                    LorryBadge(r)
                    if (sel) LorryCheck(engine, r)
                }
            }
            }
            extras(false)
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
        if (maxWidth >= 1120.dp) Column(Modifier.fillMaxWidth().floating(28.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                dest(Modifier.width(170.dp))
                cards()
                if (demoOn) demo()
                start(Modifier.width(220.dp))
                cancel()
            }
            extras(true)
        } else Column(
            // narrow: the destination and close button, the routes side by side, then Start across the width
            Modifier.fillMaxWidth().floating(28.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) { dest(Modifier.weight(1f).padding(start = 6.dp)); cancel() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { cards() }
            extras(true)
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
        Row(Modifier.height(38.dp).panel(6.dp, RoundedCornerShape(19.dp), c.surface).carTap(id) { if (place != null) engine.startRoute(place) else { engine.pendingSave = id; actions.search() } }
            .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Text(if (place == null) L.t(if (id == "work") "Set work" else "Set home") else label, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
            if (place != null && mins != null && mins > 0) Text(Geo.fmtDur(mins * 60.0), color = c.sub, fontSize = 14.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
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
private fun CarChips(engine: NavEngine, actions: CarActions, budget: androidx.compose.ui.unit.Dp) {
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
        Row(m.height(58.dp).panel(6.dp, RoundedCornerShape(20.dp), c.surface).carTap(id) { if (place != null) engine.startRoute(place) else { engine.pendingSave = id; actions.search() } }
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
        // Home and Work, then your starred places and favourites in the same row, as many as fit, and All for the rest (as in the
        // app, where the row scrolls sideways; Android Auto passes on taps only, so there's All instead). The list below is then
        // recent places only, so nothing is shown twice. (Home and Work can each be left off: Settings > Places & trips)
        val showHome = engine.prefs.b(PreferenceKeys.HOME_BTN, true); val showWork = engine.prefs.b(PreferenceKeys.WORK_BTN, true)
        val rawC = engine.prefs.s(PreferenceKeys.PLACES_CUSTOM, ""); val rawF = engine.prefs.s(PreferenceKeys.PLACES_FAVOURITES, ""); val rawR = engine.prefs.s(PreferenceKeys.PLACES_RECENT, "")
        val rowPlaces = engine.prefs.b(PreferenceKeys.ROW_PLACES, true)
        val savedRow: List<Pair<String, com.headunit.launcher.Place>> = remember(rawC, rawF, rowPlaces) {
            val all = engine.places.customs()
            val mine = if (rowPlaces) all.filter { it.pinned } else emptyList()
            val favs = engine.places.favourites().filter { f -> all.none { Geo.dist(it.place.pos, f.pos) < 60 } &&
                listOfNotNull(home, work).none { Geo.dist(it.pos, f.pos) < 60 } }
            (mine.map { it.label to it.place } + favs.map { it.name to it }).take(20)
        }
        // (Home / Work / saved places and the round shortcut buttons are always shown: measured, so what's left of [budget] is known)
        val dens = LocalDensity.current
        var fixedPx by remember { mutableStateOf(0) }
        var predPx by remember { mutableStateOf(0) }
        Column(Modifier.fillMaxWidth().onSizeChanged { fixedPx = it.height }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fixed = (if (showHome) 1 else 0) + (if (showWork) 1 else 0)
            val allW = 50.dp
            // tiles at least 104 dp wide; what's left after Home, Work and the All button goes to saved places
            val fits = ((maxWidth - allW + 8.dp) / 112.dp).toInt().coerceAtLeast(fixed)
            val shownSaved = savedRow.take((fits - fixed).coerceAtLeast(0))
            if (fixed + shownSaved.size > 0 || savedRow.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showHome) tile("home", androidx.compose.material.icons.Icons.Rounded.Home, Color(0xFF4C8DFF), L.t("Home"), home, engine.slotMins["home"], Modifier.weight(1f))
                if (showWork) tile("work", androidx.compose.material.icons.Icons.Rounded.Work, Color(0xFFF29900), L.t("Work"), work, engine.slotMins["work"], Modifier.weight(1f))
                for ((i, sp) in shownSaved.withIndex()) tile("row-$i", androidx.compose.material.icons.Icons.Rounded.Star, Warn, sp.first, sp.second, null, Modifier.weight(1f))
                if (savedRow.size > shownSaved.size) Box(Modifier.width(allW).height(58.dp).panel(6.dp, RoundedCornerShape(20.dp), c.surface).carTap("row-all") { actions.places() },
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.MoreHoriz, null, tint = c.accent, modifier = Modifier.size(22.dp))
                        Text(L.t("All"), color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
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
        }
        // where you're probably heading now, with the live drive time (as in the app; Settings "Suggest where you're going")
        LaunchedEffect(engine.fix != null) { while (true) { engine.refreshPrediction(); kotlinx.coroutines.delay(60_000) } }
        val pr0 = engine.prediction?.takeIf { engine.prefs.b(PreferenceKeys.PREDICT, true) }
        // what fits under the always-shown part: the suggestion card (its measured height; about 62 dp before it's been shown),
        // then Recent with as many rows as fit (a row 54 dp: 38 dp icon + 8 dp above and below; its header and padding about 46 dp),
        // each after a 10 dp gap. Nothing until the fixed part has been measured.
        var left = with(dens) { if (fixedPx == 0) 0.dp else budget - fixedPx.toDp() }
        val predH = with(dens) { if (predPx > 0) predPx.toDp() else 62.dp }
        val pr = pr0?.takeIf { left >= predH + 10.dp }
        if (pr != null) left -= predH + 10.dp
        // (a row less when the suggestion is showing, so the map keeps its room)
        // (the text is in sp, so with a bigger Text size the rows grow: two lines of 16 + 13 sp are about 38 sp, the header line 19 sp)
        val rowH = with(dens) { maxOf(38.dp, 38.sp.toDp()) } + 16.dp
        val headH = with(dens) { 24.dp + maxOf(22.dp, 19.sp.toDp()) }
        val fitRows = ((left - 10.dp - headH) / rowH).toInt().coerceIn(0, if (pr != null) 2 else 3)
        if (pr != null) Row(
            Modifier.fillMaxWidth().onSizeChanged { predPx = it.height }.clip(RoundedCornerShape(22.dp)).background(GmGreen).carTap("predict") { engine.startRoute(pr.place) }.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(when (pr.title) { "Home" -> androidx.compose.material.icons.Icons.Rounded.Home; "Work" -> androidx.compose.material.icons.Icons.Rounded.Work
                else -> androidx.compose.material.icons.Icons.Rounded.History }, null, tint = Color.White, modifier = Modifier.size(24.dp))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(listOfNotNull(L.t(pr.title), if (pr.mins >= 0) Geo.fmtDur(pr.mins * 60.0) else null, L.t(pr.note).ifBlank { null }).joinToString(" · "),
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val arrive = if (pr.mins >= 0) L.t("Arrive about ") + com.headunit.launcher.Clock.hm(System.currentTimeMillis() + pr.mins * 60_000L) else pr.place.sub
                Text(arrive, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(L.t("Go"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        // recent places only (the saved ones are in the row above)
        val recents = remember(rawC, rawF, rawR) {
            val all = engine.places.customs(); val favs = engine.places.favourites()
            engine.places.recents().filter { r -> favs.none { Geo.dist(it.pos, r.pos) < 60 } && all.none { Geo.dist(it.place.pos, r.pos) < 60 } }
        }
        val listRows = fitRows
        // (folded away with the arrow in the search bar)
        if (engine.prefs.b(PreferenceKeys.QUICKLIST, true) && recents.isNotEmpty() && listRows > 0) Column(Modifier.fillMaxWidth().panel(10.dp, RoundedCornerShape(24.dp), c.surface).padding(vertical = 2.dp)) {
            Row(Modifier.fillMaxWidth().carTap("saved") { actions.places() }.padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(L.t("Recent").uppercase(), color = c.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
                Text(L.t("Open"), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            for ((i, p) in recents.take(listRows).withIndex()) {
                Row(Modifier.fillMaxWidth().carTap("saved-$i") { engine.startRoute(p) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.tile), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.History, null, tint = c.sub, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(p.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (p.sub.isNotBlank()) Text(p.sub, color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (here != null) Text(Geo.fmtDist(Geo.dist(here, p.pos), engine.imperial), color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}


/**
 * The place card on the car, kept simple for driving: the name, open or closed, how far and when you'd arrive, a petrol station's
 * price or a charger's plugs, then Go and (on a route) Add stop.
 */
@Composable
private fun CarHereCard(engine: NavEngine, h: NavEngine.HereCard) {
    val c = LocalVw.current
    val dropped = h.place.name == "Dropped pin" || h.place.name == L.t("Dropped pin")
    val title = if (!dropped) h.place.name else h.road.ifBlank { if (h.loading) L.t("Looking…") else L.t("This spot") }
    val driving = engine.route != null && engine.dest != null
    val d = h.details
    Column(Modifier.widthIn(min = 300.dp, max = 440.dp).panel(10.dp, RoundedCornerShape(24.dp), c.surface).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val st = d?.hours?.let { com.headunit.launcher.OpeningHours.state(it) }
                if (st != null) Row {
                    Text(if (st.first) L.t("Open now") else L.t("Closed"), color = if (st.first) Good else Bad, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    val more = if (st.first) (if (st.second.isBlank()) L.t("Open 24 hours") else L.t("closes {0}", st.second)) else if (st.second.isNotBlank()) L.t("opens {0}", st.second) else ""
                    if (more.isNotBlank()) Text(" · $more", color = c.sub, fontSize = 14.sp, maxLines = 1)
                }
                if (h.distM >= 0) Text(if (h.etaSecs > 0) L.t("{0} · arrive {1}", Geo.fmtDist(h.distM, engine.imperial),
                        com.headunit.launcher.Clock.format().format(java.util.Date(System.currentTimeMillis() + (h.etaSecs * 1000).toLong())))
                    else L.t("{0} away", Geo.fmtDist(h.distM, engine.imperial)), color = c.sub, fontSize = 14.sp, maxLines = 1)
            }
            Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).carTap("here-x") { engine.here = null }, contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Close, null, tint = c.sub, modifier = Modifier.size(22.dp))
            }
        }
        val extra = if (h.fuelPrice.isNotBlank()) h.fuelPrice else d?.plugs?.takeIf { it.isNotEmpty() }?.joinToString(" · ").orEmpty()
        if (extra.isNotBlank()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.tile).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(if (h.fuelPrice.isNotBlank()) androidx.compose.material.icons.Icons.Rounded.LocalGasStation else androidx.compose.material.icons.Icons.Rounded.EvStation, null,
                tint = if (h.fuelPrice.isNotBlank()) Color(0xFFEA4335) else Color(0xFF34A853), modifier = Modifier.size(18.dp))
            Text(extra, color = Good, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1.3f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.accent).carTap("here-go") { engine.hereGo(false) }, contentAlignment = Alignment.Center) {
                Text(if (driving) L.t("Go here instead") else if (h.etaSecs > 0) L.t("Go · {0}", Geo.fmtDur(h.etaSecs)) else L.t("Go"),
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (driving) Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.tile).carTap("here-stop") { engine.hereGo(true) }, contentAlignment = Alignment.Center) {
                Text(L.t("Add stop"), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}
