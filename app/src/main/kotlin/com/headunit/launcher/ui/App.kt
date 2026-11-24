package com.headunit.launcher.ui

import com.headunit.launcher.L

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.headunit.launcher.MapBinding
import com.headunit.launcher.NavEngine
import com.headunit.launcher.Prefs
import com.headunit.launcher.PreferenceKeys
import com.headunit.launcher.mapSource
import com.headunit.launcher.mapWeather
import com.headunit.launcher.locatorSpec
import com.headunit.launcher.mapDetail
import com.headunit.launcher.setApiKey
import kotlinx.coroutines.delay

internal fun findActivity(c: android.content.Context): android.app.Activity? {
    var x: android.content.Context = c
    while (x is android.content.ContextWrapper) {
        if (x is android.app.Activity) return x
        x = x.baseContext
    }
    return null
}

/**
 * Conservative automatic UI scale for the main app. Keep Android's normal dp sizing (100%) on
 * ordinary screens, like Google Maps, and only shrink slightly when the available short edge is
 * genuinely compact. Large/high-resolution displays must not make controls larger just because
 * they have more pixels or dp available.
 */
internal fun recommendedMainUiScale(widthDp: Int, heightDp: Int): Int {
    if (widthDp <= 0 || heightDp <= 0) return 100
    val shortEdge = minOf(widthDp, heightDp)
    return when {
        shortEdge < 320 -> 92
        shortEdge < 380 -> 96
        shortEdge < 460 -> 98
        else -> 100
    }
}

/** The whole app: the map full screen with its overlays, and the settings screen on top when it is open. */
@Composable
fun App(prefs: Prefs, engine: NavEngine, map: MapBinding) { SafeArea(prefs) { TextSides {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var whatsNewVisible by remember { mutableStateOf(false) }
    var whatsNewNotes by remember { mutableStateOf("") }
    val installedVersion = com.headunit.launcher.BuildConfig.VERSION_NAME
    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    LaunchedEffect(installedVersion) {
        if (com.headunit.launcher.BuildConfig.UPDATE_REPO.isNotBlank() && prefs.s(PreferenceKeys.WHATS_NEW_SEEN_VERSION, "") != installedVersion) {
            val previousVersion = prefs.s(PreferenceKeys.WHATS_NEW_SEEN_VERSION, "")
            val notes0 = try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.headunit.launcher.Updater.installedReleaseNotes(installedVersion, previousVersion, appCtx) } } catch (_: Exception) { "" }
            // (only what this unit hasn't been shown before: a build's notes are everything since the last published version)
            val notes = com.headunit.launcher.Updater.unseenNotes(notes0, prefs.s(PreferenceKeys.WHATS_NEW_SHOWN, ""))
            // (only shown when there's something to say: an update with no new notes showed a card saying just that no notes
            // were found. It counts as seen, so it isn't looked up again on every start)
            if (notes.isNotBlank()) {
                whatsNewNotes = notes
                prefs.put(PreferenceKeys.WHATS_NEW_LAST_NOTES, notes)
                whatsNewVisible = true
            } else prefs.put(PreferenceKeys.WHATS_NEW_SEEN_VERSION, installedVersion)
        }
    }
    // Settings › Horizon Nav › Version › What's new: the card again, with the notes it showed last (every version since the one before,
    // after a jump of several), or this version's own notes when it hasn't shown any yet (GitHub #149)
    LaunchedEffect(WhatsNewCard.reopen) {
        if (!WhatsNewCard.reopen) return@LaunchedEffect
        WhatsNewCard.reopen = false
        val saved = prefs.s(PreferenceKeys.WHATS_NEW_LAST_NOTES, "")
        whatsNewNotes = saved
        whatsNewVisible = true
        if (saved.isBlank()) {
            val prev = installedVersion.substringAfterLast('.').toIntOrNull()?.let { "1.0.${it - 1}" } ?: ""
            whatsNewNotes = try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.headunit.launcher.Updater.installedReleaseNotes(installedVersion, prev, appCtx) } } catch (_: Exception) { "" }
        }
    }
    LaunchedEffect(Unit) { while (true) { delay(60_000); nowMs = System.currentTimeMillis() } }
    // the donate card: once, the first time the app is open a day or more after it was installed. Never while you're moving (it
    // waits until you've stopped), and not over the What's new card. (The car screen never shows it.)
    var donateVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // (never in the Google Play edition: Play doesn't allow donations through PayPal)
        if (prefs.b(PreferenceKeys.DONATE_SHOWN, false) || com.headunit.launcher.BuildConfig.STORE == "play") return@LaunchedEffect
        val installed = try { appCtx.packageManager.getPackageInfo(appCtx.packageName, 0).firstInstallTime } catch (e: Exception) { 0L }
        if (installed <= 0L || System.currentTimeMillis() - installed < 24L * 3600_000L) return@LaunchedEffect
        delay(20_000)   // let the map settle first
        while (true) {
            if (!whatsNewVisible && (engine.fix?.speed ?: 0.0) < 2.0) { donateVisible = true; break }
            delay(15_000)
        }
    }
    // the invitation to test Horizon Maps on Google Play (GitHub edition only): a while after the app opens, only when you're
    // stopped with no route, never over the What's new or donate cards. "Not now" brings it back in 3 days
    var playInviteVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (com.headunit.launcher.BuildConfig.STORE == "play") return@LaunchedEffect
        val st = prefs.s(PreferenceKeys.PLAY_INVITE, "")
        if (st == "never" || (st.toLongOrNull() ?: 0L) > System.currentTimeMillis()) return@LaunchedEffect
        delay(40_000)   // let the map settle first
        while (true) {
            if (!whatsNewVisible && !donateVisible && engine.route == null && !engine.previewing && (engine.fix?.speed ?: 0.0) < 2.0) { playInviteVisible = true; break }
            delay(15_000)
        }
    }
    // the head unit has switched to its night mode at least once (so it links night mode to the headlights): noted for "Headlights"
    val sysDarkRaw = rememberSystemNight(useLamps = prefs.s(PreferenceKeys.THEME, "auto") == "lights")
    // On a phone, Auto goes by sunrise and sunset ("Phone's dark mode" follows the phone's own light / dark setting). Head units
    // still follow their own night mode once it's been seen switching, with sunrise and sunset until then.
    val phoneLike = remember {
        try { appCtx.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY) && appCtx.resources.configuration.smallestScreenWidthDp < 600 } catch (e: Exception) { false }
    }
    val sysDark = sysDarkRaw
    LaunchedEffect(sysDark) { if (sysDark && !prefs.b(PreferenceKeys.SYSDARKSEEN, false)) prefs.put(PreferenceKeys.SYSDARKSEEN, true) }
    // (lukaszsakul, #181: with the phone's dark mode on all the time, Auto kept the map dark all day. On a phone, Auto now goes by
    // sunrise and sunset; "Phone's dark mode" is the choice for matching the phone's own light / dark setting)
    val themeNow = prefs.s(PreferenceKeys.THEME, "auto")
    val dark = if (themeNow == "phone") sysDark
    else resolveDark(if (phoneLike && themeNow == "auto") "sun" else themeNow, prefs.s(PreferenceKeys.LASTLAT, "").toDoubleOrNull(), prefs.s(PreferenceKeys.LASTLON, "").toDoubleOrNull(), nowMs,
        prefs.b(PreferenceKeys.SYSDARKSEEN, false), sysDark,
        prefs.s(PreferenceKeys.NIGHT_FROM, "19:00"), prefs.s(PreferenceKeys.DAY_FROM, "07:00"))
    val hostCtx = androidx.compose.ui.platform.LocalContext.current

    // screen brightness (per window, no permission needed) incl. dimming at night, and keeping the screen on
    val bright = prefs.i(PreferenceKeys.BRIGHT, 100)
    val dimNight = prefs.b(PreferenceKeys.DIMNIGHT, false)
    // On a phone the screen stays on only while a route is being followed, as in other navigation apps; browsing the map it
    // goes off as usual (it was kept on whenever the app was open, a big battery drain on a phone left with the app open).
    // A head unit keeps it on all the time
    val onPhone = remember { engine.onPhone() }
    val keepOn = prefs.b(PreferenceKeys.KEEPON, true) && (!onPhone || (engine.route != null && !engine.previewing) || prefs.b(PreferenceKeys.KEEPON_NOROUTE, false))
    LaunchedEffect(bright, dimNight, dark, keepOn) {
        val win = findActivity(hostCtx)?.window ?: return@LaunchedEffect
        val dim = dimNight && dark
        // dimmed at night: half of what the screen is at now. It used to be half of full brightness, a fixed 50%, which is brighter
        // than most screens are set at night, so "dim" made it brighter (testers). With Brightness at 100% (the system's own
        // level), that's half the system's level; never brighter than it
        val sys = try { (android.provider.Settings.System.getInt(hostCtx.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128) / 255f).coerceIn(0.02f, 1f) } catch (e: Exception) { 0.5f }
        val base = if (bright >= 100) sys else bright / 100f
        val v = if (bright >= 100 && !dim) -1f else (base * (if (dim) 0.5f else 1f)).coerceIn(0.02f, 1f)
        val lp = win.attributes
        lp.screenBrightness = v
        win.attributes = lp
        if (keepOn) win.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // map look and behaviour follow the settings as soon as they change
    val style = prefs.mapSource()
    val traffic = prefs.b(PreferenceKeys.TRAFFIC, true)
    val key = prefs.s(PreferenceKeys.API_KEY, "")
    // the Classic look was retired: everyone gets the Modern map
    LaunchedEffect(Unit) { if (prefs.s(PreferenceKeys.MAP_THEME, "immersive") != "immersive") prefs.put(PreferenceKeys.MAP_THEME, "immersive") }
    LaunchedEffect(Unit) { map.setTheme("immersive") }
    // your locator: 3D arrow, 3D car or the flat arrow, in the colour you picked
    val (locStyle, locColor) = prefs.locatorSpec()
    LaunchedEffect(locStyle, locColor) { map.setLocator(locStyle, locColor) }
    val routeArrows = prefs.b(PreferenceKeys.ROUTECHEVRONS, true)
    LaunchedEffect(routeArrows) { map.setRouteArrows(routeArrows) }
    val mapFps = prefs.s(PreferenceKeys.MAP_FPS, "30").toIntOrNull() ?: 30
    LaunchedEffect(mapFps) { map.setMaxFps(mapFps) }
    // Settings > Map: how much detail, which colours, and where Mapbox's logo goes
    val mapDetail = prefs.mapDetail()
    LaunchedEffect(mapDetail) { map.setMapDetail(mapDetail) }
    val mapColours = prefs.s(PreferenceKeys.MAP_COLOURS, "mapbox")
    // (anyone who'd picked the short-lived Custom colours keeps the theme it started from)
    // Custom: the theme it starts from, with its own colours on top
    val customOn = mapColours == "custom"
    val baseColours = if (customOn) prefs.s(PreferenceKeys.CUSTOM_BASE, "mapbox") else mapColours
    LaunchedEffect(baseColours) { map.setColours(baseColours) }
    val mapFont = prefs.s(PreferenceKeys.MAP_FONT, "")
    LaunchedEffect(mapFont) { map.setFont(mapFont) }
    val customDay = prefs.s(PreferenceKeys.CUSTOM_DAY, "")
    val customNight = prefs.s(PreferenceKeys.CUSTOM_NIGHT, "")
    val customW = prefs.i(PreferenceKeys.CUSTOM_ROUTE_W, 100)
    LaunchedEffect(customOn, customDay, customNight, customW) { map.setCustom(customOn, customDay, customNight, customW) }
    val camSize = prefs.i(PreferenceKeys.CAM_SIZE, 100)
    LaunchedEffect(camSize) { map.setCameraSize(camSize) }
    val flags3d = prefs.b(PreferenceKeys.FLAGS_3D, true)
    val hills = prefs.b(PreferenceKeys.HILLSHADE, false)
    LaunchedEffect(flags3d, hills) { map.setFlags(flags3d); map.setHillShading(hills) }
    // Light and weather, and the "Match my car" colour
    val lightOpts = com.headunit.launcher.lightOptsFrom(prefs)
    LaunchedEffect(lightOpts.nightBright, lightOpts.dayBright, lightOpts.moon, lightOpts.moonStr, lightOpts.shadows, lightOpts.sunReal, lightOpts.overcast, lightOpts.weatherLight) {
        map.setLightOpts(lightOpts)
    }
    val wxCode = engine.weather?.code
    LaunchedEffect(wxCode) { map.setWeatherCode(wxCode) }
    val carTint = prefs.s(PreferenceKeys.CAR_TINT, "#8A5AD0")
    val carTintStr = prefs.i(PreferenceKeys.CAR_TINT_STR, 40)
    LaunchedEffect(carTint, carTintStr) { map.setCarTint(carTint, carTintStr) }
    val logoCorner = prefs.s(PreferenceKeys.MAPBOX_LOGO, "tr")
    LaunchedEffect(logoCorner) { map.setLogoCorner(logoCorner) }
    // (off unless chosen: with it on, traffic on the roads round you vanished as soon as a route started, which looked like a bug)
    val hideTraffic = prefs.b(PreferenceKeys.ROUTETRAFFICONLY, false)
    LaunchedEffect(hideTraffic) { map.setHideOtherTraffic(hideTraffic) }
    val altColour = prefs.s(PreferenceKeys.ALT_ROUTE_COLOUR, "#C4D9E6")
    LaunchedEffect(altColour) { map.setAltColour(altColour) }
    val arrowPct = prefs.s(PreferenceKeys.ARROW_PERCENT, "240").toIntOrNull() ?: 240
    // the old second slider (Screen & sizes > Navigation arrow size) multiplied this one: it's folded into Arrow size once (also
    // after restoring an older backup), so the arrow stays the same size, and the car screen's arrow is evened back out
    val oldMainArrow = prefs.i(PreferenceKeys.MAIN_ARROW_SIZE, 100).coerceIn(60, 180)
    LaunchedEffect(oldMainArrow) {
        if (oldMainArrow != 100) {
            prefs.put(PreferenceKeys.ARROW_PERCENT, (arrowPct * oldMainArrow / 100).coerceIn(60, 500).toString())
            prefs.put(PreferenceKeys.CAR_ARROW_SIZE, (prefs.i(PreferenceKeys.CAR_ARROW_SIZE, 100) * 100 / oldMainArrow).coerceIn(30, 180))
            prefs.put(PreferenceKeys.MAIN_ARROW_SIZE, 100)
        }
    }
    LaunchedEffect(arrowPct, oldMainArrow) { map.setArrowScale(arrowPct / 100f * oldMainArrow / 100f) }
    val carModel = prefs.s(PreferenceKeys.CAR_MODEL, "saloon")
    LaunchedEffect(carModel) { map.setCarModel(carModel) }
    val trafficGreen = prefs.b(PreferenceKeys.TRAFFIC_GREEN, false)
    LaunchedEffect(trafficGreen) { map.setTrafficGreen(trafficGreen) }
    val blendTraffic = prefs.b(PreferenceKeys.BLEND_TRAFFIC, true)
    LaunchedEffect(blendTraffic) { map.setBlendTraffic(blendTraffic) }
    val roadNamePct = prefs.i(PreferenceKeys.ROAD_NAME_SIZE, 100).coerceIn(70, 200)
    LaunchedEffect(roadNamePct) { map.setRoadNameScale(roadNamePct / 100f) }
    val mbToken = prefs.s(PreferenceKeys.MAPBOX_TOKEN, "")
    val mbLook = prefs.s(PreferenceKeys.MBLOOK, "horizon")
    // (Horizon Maps Plus: the key server's token arriving, or Plus starting or ending, loads the right map)
    val ksToken = com.headunit.launcher.KeyServer.mapbox
    val plusOn = com.headunit.launcher.Plus.active
    LaunchedEffect(dark, style, traffic, key, mbToken, mbLook, ksToken, plusOn) { engine.applyStyle(dark) }
    // (satellite is Plus's: the free OpenStreetMap version has none)
    val sat = prefs.b(PreferenceKeys.SATELLITE, false) && !com.headunit.launcher.Plus.free
    val b3d = prefs.b(PreferenceKeys.BUILDINGS_3D, true)
    LaunchedEffect(sat, b3d) { map.setView(sat, b3d) }
    val lampGap = prefs.s(PreferenceKeys.LAMPGAP, "40").toDoubleOrNull() ?: 40.0
    val lampsOn = prefs.b(PreferenceKeys.LAMPS, true)
    LaunchedEffect(lampsOn) { map.lampsEnabled(lampsOn) }
    LaunchedEffect(lampGap) { delay(1200); map.applyLampGap(lampGap) }
    val treesOn = prefs.b(PreferenceKeys.TREES, true)
    LaunchedEffect(treesOn) { map.treesEnabled(treesOn) }
    // where you parked, on the map (for 2 days)
    val parkedRaw = prefs.s(PreferenceKeys.PARKED_SPOT, "")
    LaunchedEffect(parkedRaw) {
        val ps = parkedRaw.split(',')
        val la = ps.getOrNull(0)?.toDoubleOrNull(); val lo = ps.getOrNull(1)?.toDoubleOrNull(); val at = ps.getOrNull(2)?.toLongOrNull() ?: 0L
        map.setParked(if (la != null && lo != null && System.currentTimeMillis() - at < 48 * 3600_000L) com.headunit.launcher.LL(la, lo) else null)
    }
    // your favourites as stars on the map (Settings > On the map; Xundey, #249): smaller and without names while navigating,
    // and a tap on one opens its place card, with Go
    // ...and Home, Work and the starred places in Your places too, with their own icons, all one size from Settings › On the map ›
    // Saved places icons (starred places in Your places never showed, so a list moved there looked lost)
    val favsOn = prefs.b(PreferenceKeys.FAVS_ON_MAP, true)
    val favsRaw = prefs.s(PreferenceKeys.PLACES_FAVOURITES, "")
    val customsRaw = prefs.s(PreferenceKeys.PLACES_CUSTOM, "")
    val homeRaw = prefs.s(PreferenceKeys.PLACE_HOME, ""); val workRaw = prefs.s("place_work", "")
    val savedScale = prefs.i(PreferenceKeys.SAVED_ICON_SIZE, 100).coerceIn(50, 250) / 100f
    val favsSmall = engine.route != null && !engine.previewing
    val savedOnMap = remember(favsOn, favsRaw, customsRaw, homeRaw, workRaw) {
        if (!favsOn) emptyList() else {
            val out = ArrayList<Triple<com.headunit.launcher.Place, String, String>>()
            fun near(p: com.headunit.launcher.Place) = out.any { com.headunit.launcher.Geo.dist(it.first.pos, p.pos) < 30.0 }
            engine.places.slot("home")?.let { out += Triple(it, com.headunit.launcher.L.t("Home"), "home") }
            engine.places.slot("work")?.let { if (!near(it)) out += Triple(it, com.headunit.launcher.L.t("Work"), "work") }
            for (f in engine.places.favourites()) if (!near(f)) out += Triple(f, f.name, "fav")
            for (c in engine.places.customs()) if (c.pinned && !near(c.place)) out += Triple(c.place, c.label, "fav")
            out
        }
    }
    LaunchedEffect(savedOnMap, favsSmall, savedScale) {
        map.setFavourites(savedOnMap.map { it.first.pos to it.second }, favsSmall, savedOnMap.map { it.third }, savedScale)
    }
    val savedNow = androidx.compose.runtime.rememberUpdatedState(savedOnMap)
    androidx.compose.runtime.DisposableEffect(map) {
        map.onFavTap = { i -> savedNow.value.getOrNull(i)?.let { engine.showHere(it.first) } }
        onDispose { map.onFavTap = null }
    }
    // the extra 3D features (Settings > On the map)
    val xFacades = prefs.b(PreferenceKeys.FACADES, true); val xLand = prefs.b(PreferenceKeys.LANDMARKS3D, true); val xPaths = prefs.b(PreferenceKeys.PATHS, true)
    val xTerrain = prefs.b(PreferenceKeys.TERRAIN, false); val xTap = prefs.b(PreferenceKeys.TAPSELECT, true); val xFurn = prefs.b(PreferenceKeys.FURNITURE3D, true); val xClear = prefs.b(PreferenceKeys.CLEARVIEW, true)
    LaunchedEffect(xFacades, xLand, xPaths, xTerrain, xTap, xFurn, xClear) { map.setExtras(xFacades, xLand, xPaths, xTerrain, xTap, xFurn, xClear) }
    // Mapbox's HD roads (on by itself in the UK only: elsewhere they could leave the streets missing)
    val xHd = com.headunit.launcher.hdRoadsWanted(prefs, engine.detectedCountry)
    LaunchedEffect(xHd) { map.setHdRoads(xHd) }
    // real rain and snow on the map when it's raining or snowing where you are (Weather effects)
    val wxOn = prefs.b(PreferenceKeys.RAIN_EFFECTS, false)
    // (with the kinds switched off in Settings > Weather shown as overcast)
    val wxNow = prefs.mapWeather(engine.weather?.code)
    LaunchedEffect(wxOn, wxNow) { map.setWeather(if (wxOn) wxNow else null) }
    // the haze towards the horizon follows the weather, and the route's ribbon look
    val skyOn = prefs.b(PreferenceKeys.SKYWEATHER, true)
    LaunchedEffect(skyOn, wxNow) { map.setSky(if (skyOn) wxNow else null) }
    val ribbon = prefs.b(PreferenceKeys.RIBBON, true)
    LaunchedEffect(ribbon) { map.setRibbon(ribbon) }
    val signalsOn = prefs.b(PreferenceKeys.SIGNALS, true)
    LaunchedEffect(signalsOn) { map.signalsEnabled(signalsOn) }
    // light flowing along the route line while guiding (off while the route is only being previewed)
    val flowOn = prefs.b(PreferenceKeys.ROUTEFLOW, true) && engine.route != null && !engine.previewing
    LaunchedEffect(flowOn) { map.setFlow(flowOn) }
    // live rain radar layer (map options)
    val radarOn = prefs.b(PreferenceKeys.RAIN_RADAR, false)
    LaunchedEffect(radarOn) { engine.setRadarOn(radarOn) }

    // Automatically choose a comfortable UI scale for the main app based on the current
    // window's dp dimensions. A saved legacy uiscale is treated as a deliberate manual choice;
    // fresh installs default to automatic scaling.
    val configuration = LocalConfiguration.current
    val hasManualScale = prefs.s(PreferenceKeys.UI_SCALE, "").isNotBlank()
    val autoScaleEnabled = prefs.b(PreferenceKeys.UI_AUTO_SCALE, !hasManualScale)
    val autoScalePercent = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        recommendedMainUiScale(configuration.screenWidthDp, configuration.screenHeightDp)
    }
    val scale = (if (autoScaleEnabled) autoScalePercent else prefs.i(PreferenceKeys.UI_SCALE, autoScalePercent))
        .coerceIn(60, 140) / 100f
    val baseDensity = androidx.compose.ui.platform.LocalDensity.current
    val mainTextScale = prefs.i(PreferenceKeys.MAIN_TEXT_SIZE, 100).coerceIn(70, 150) / 100f
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(baseDensity.density * scale, baseDensity.fontScale * mainTextScale),
    ) {
        VwTheme(dark) {
            val c = LocalVw.current
            var settings by rememberSaveable { mutableStateOf(false) }
            // a destination shared from another app brings the map back
            val tick = engine.externalTick
            LaunchedEffect(tick) { if (tick > 0) settings = false }
            // Back closes the settings; on the map it sends the app to the background instead of closing it,
            // so a route in progress keeps going (and the map is ready straight away when you come back).
            BackHandler(enabled = settings) { settings = false }
            BackHandler(enabled = !settings) { findActivity(hostCtx)?.moveTaskToBack(true) }

            // while the car screen is connected the phone shows a brief trip screen instead of a second map (unless asked for)
            val carOn = com.headunit.launcher.RouteSync.carOn
            var phoneMap by remember { mutableStateOf(false) }
            LaunchedEffect(carOn) { if (!carOn) phoneMap = false }
            // the phone's engine leaves speed limit, camera and traffic lookups to the car while the phone isn't showing its map
            val mapShown = !carOn || phoneMap || settings
            androidx.compose.runtime.SideEffect { engine.showingMap = mapShown }
            Box(Modifier.fillMaxSize().background(c.bg)) {
                // first run: a short setup before the map (existing installs that are already set up skip it)
                LaunchedEffect(Unit) { if (prefs.s(PreferenceKeys.PLACE_HOME, "").isNotBlank() || prefs.s(PreferenceKeys.HABITS, "").isNotBlank()) prefs.put(PreferenceKeys.SETUP_DONE, true) }
                val setupDone = !prefs.b(PreferenceKeys.FORCESETUP, false) && (prefs.b(PreferenceKeys.SETUP_DONE, false) || prefs.s(PreferenceKeys.PLACE_HOME, "").isNotBlank() || prefs.s(PreferenceKeys.HABITS, "").isNotBlank())
                if (!setupDone) {
                    // (setup asks for the key itself)
                    LaunchedEffect(Unit) { prefs.put(PreferenceKeys.MAPBOX_PROMPT, false) }
                    SetupScreen(prefs, engine) { settings = false }
                } else if (!settings && carOn && !phoneMap) {
                    // Android Auto is connected: the car screen has the map, so the phone shows the trip in brief
                    CarConnectedScreen(engine, onShowMap = { phoneMap = true }, onSettings = { settings = true })
                } else if (settings && !PipUi.on) {
                    // the map is taken off screen while the settings are open, so it is not drawing underneath them.
                    // The settings rise and fade in over a moment, so opening them doesn't feel like a jump.
                    val inA = remember { androidx.compose.animation.core.Animatable(0f) }
                    LaunchedEffect(Unit) { inA.animateTo(1f, androidx.compose.animation.core.tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = inA.value; val s = 0.97f + 0.03f * inA.value; scaleX = s; scaleY = s }) {
                        SettingsScreen(prefs, engine, onClose = { settings = false })
                    }
                } else {
                    // (in full screen the map reaches right up round the camera, while the cards and buttons stay clear of it, GitHub)
                    val bleed = prefs.b(PreferenceKeys.FULLSCREEN, true)
                    MapHost(map, Modifier.fillMaxSize().bleedCutout(bleed))
                    // at night in 3D, stars and the real moon phase in the sky above the horizon
                    if (c.dark && engine.tilt3d && !sat && !engine.daylightOutside && prefs.b(PreferenceKeys.NIGHTSKY, true))
                        // (stretched up behind the camera notch with the map: it used to start under it, a hard edge across the top)
                        Box(Modifier.fillMaxSize().bleedCutout(bleed)) { NightSky(Modifier.fillMaxWidth().fillMaxHeight(0.2f)) }
                    // in a tunnel (no GPS) the map is dimmed a little, as the car's own screen would be
                    if (engine.deadReckoning && !c.dark && prefs.b(PreferenceKeys.TUNNELDIM, true)) Box(Modifier.fillMaxSize().bleedCutout(bleed).background(Color.Black.copy(alpha = 0.35f)))
                    val wxCode = prefs.mapWeather(engine.weather?.code)
                    // rain, snow or fog over the map to match the real weather where you are
                    // (the map draws rain and snow itself now; the screen overlay is kept for fog, and for when the map can't)
                    if (prefs.b(PreferenceKeys.RAIN_EFFECTS, false) && wxCode != null && (wxCode == 45 || wxCode == 48 || !map.drawsWeather)) WeatherFx(wxCode, Modifier.fillMaxSize())
                    // (in the small picture-in-picture window, #301: just the turn and the time left over the map)
                    if (PipUi.on) PipOverlay(engine) else NavOverlays(engine, onSettings = { settings = true })
                    if (!PipUi.on && prefs.b(PreferenceKeys.PERFORMANCE_METER, false) && engine.perfLine.isNotEmpty()) Text(engine.perfLine, color = Color.White, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
                    if (engine.deadReckoning && !PipUi.on) DeadReckoningChip(Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp))
                    // (with the heading switched off and no road name known, there's nothing to show)
                    // it stays up while you slow down or wait at lights (a minute below walking pace), instead of swapping back and
                    // forth with the bubble under your arrow every time you dropped under 9 mph (liamstears)
                    val fastNow = engine.cruising
                    val noRoute = engine.route == null && !engine.previewing
                    var cruiseHeld by remember { mutableStateOf(false) }
                    LaunchedEffect(fastNow, noRoute) {
                        if (fastNow) cruiseHeld = true
                        else if (!noRoute) cruiseHeld = false
                        else { kotlinx.coroutines.delay(60_000L); cruiseHeld = false }
                    }
                    val cruiseShows = (fastNow || (cruiseHeld && noRoute)) && engine.following && prefs.b(PreferenceKeys.CRUISE, true) &&
                        (prefs.b(PreferenceKeys.CRUISE_HEADING, true) || engine.roadNow.isNotBlank())
                    // (above the media bar when that's showing at the bottom)
                    val mediaUp = prefs.b(PreferenceKeys.MEDIA_BAR, false) && com.headunit.launcher.MediaNow.showing()
                    androidx.compose.runtime.SideEffect { val w = !engine.deadReckoning && cruiseShows; if (engine.cruisePillWanted != w) engine.cruisePillWanted = w }
                    // (with the portrait sheet up, the pill is the top line of the sheet instead)
                    if (!engine.deadReckoning && cruiseShows && !engine.portraitSheetOn && !PipUi.on) CruisePill(engine, Modifier.align(Alignment.BottomCenter).padding(bottom = if (mediaUp) maxOf(84f, engine.mediaLiftDp + 10f).dp else 18.dp))
                    // that pill has the road name, so the one under your arrow steps aside while it's up (they overlapped)
                    val cruisePillUp = !engine.deadReckoning && cruiseShows
                    LaunchedEffect(cruisePillUp) { map.setRoadNameHidden(cruisePillUp) }
                    // coming back from the settings, the map fades up from the background instead of flashing in
                    val back = remember { androidx.compose.animation.core.Animatable(1f) }
                    LaunchedEffect(Unit) { back.animateTo(0f, androidx.compose.animation.core.tween(320)) }
                    if (back.value > 0.01f) Box(Modifier.fillMaxSize().bleedCutout(prefs.b(PreferenceKeys.FULLSCREEN, true)).graphicsLayer { alpha = back.value }.background(c.bg))
                }
                // once, after updating from the old map: what the Mapbox key adds, and where to paste it
                // ...and any time the map can't show for want of a working key (missing, half pasted, or turned down by Mapbox): the blank
                // map with only stars on it looked broken, and the short message about it was easy to miss. "Later" puts it off until the
                // app is next opened. (Not while the downloaded map is showing: that one needs no key.)
                var mbLater by remember { mutableStateOf(false) }
                val noMapKey = !engine.hasMapbox && !mbLater && !engine.useOfflineMap() && !(carOn && !phoneMap)
                if (setupDone && !settings && !PipUi.on && (prefs.b(PreferenceKeys.MAPBOX_PROMPT, false) || noMapKey))
                    MapboxKeyDialog(prefs, refused = engine.mbKeyRefused, why = engine.mbRefusedWhy) { prefs.put(PreferenceKeys.MAPBOX_PROMPT, false); mbLater = true }
                // short messages (saved, sent, no signal...) show over everything
                val curMsg = engine.message
                val lastMsg = remember { mutableStateOf(curMsg) }
                if (curMsg != null) lastMsg.value = curMsg
                androidx.compose.animation.AnimatedVisibility(
                    visible = curMsg != null && !PipUi.on,
                    modifier = Modifier.align(if (settings) Alignment.BottomCenter else Alignment.TopCenter).padding(top = 12.dp, bottom = 18.dp),
                    enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(240)) { if (settings) it / 2 else -it / 2 },
                    exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)),
                ) {
                    val act = engine.msgAction
                    Row(
                        Modifier.shadow(10.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(navVw(c).surface).border(1.dp, navVw(c).line, RoundedCornerShape(50))
                            .padding(start = 22.dp, end = if (act != null) 8.dp else 22.dp, top = if (act != null) 7.dp else 11.dp, bottom = if (act != null) 7.dp else 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(lastMsg.value ?: "", color = navVw(c).text, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                        // a button on the message (Undo straight after a report)
                        if (act != null) Box(
                            Modifier.padding(start = 14.dp).clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = 0.16f))
                                .clickable { act.second() }.padding(horizontal = 16.dp, vertical = 7.dp),
                        ) { Text(act.first, color = c.accent, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
                    }
                }
                // one of your reports tapped on the map: what it is and when you made it, with Remove / Keep
                val rc = if (settings || PipUi.on) null else engine.reportCard
                // (above the trip card on a tall screen, where that card fills the bottom)
                val tallScreen = LocalConfiguration.current.screenHeightDp > LocalConfiguration.current.screenWidthDp
                if (rc != null) Column(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = if (tallScreen) 170.dp else 18.dp).width(300.dp)
                        .shadow(10.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(navVw(c).surface).border(1.dp, navVw(c).line, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(L.t(engine.reportName(rc.kind)), color = navVw(c).text, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(engine.reportAge(rc), color = navVw(c).text.copy(alpha = 0.65f), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                    Row {
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(50)).background(Color(0xFFE24B4A).copy(alpha = 0.18f)).clickable { engine.removeReport(rc) }.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(L.t("Remove"), color = if (c.dark) Color(0xFFFF8A8A) else Color(0xFFC62828), fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
                        Box(Modifier.width(8.dp))
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(50)).background(navVw(c).line).clickable { engine.reportCard = null }.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(L.t("Keep"), color = navVw(c).text, fontSize = 15.sp) }
                    }
                }
                // alerts (camera, speed limit, incident, rain, break) get a large banner that is hard to miss
                if (!settings) {
                    val curAlert = engine.alert
                    val lastAlert = remember { mutableStateOf(curAlert) }
                    if (curAlert != null) lastAlert.value = curAlert
                    // landscape: centred in the gap between the turn card (left) and the round buttons (right), so it never covers them.
                    // Portrait: just above the trip card at the bottom.
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                        val narrow = maxWidth < 720.dp
                        Box(
                            if (narrow) Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 250.dp)
                            else Modifier.align(Alignment.TopStart).fillMaxWidth().padding(start = if (engine.mirrored) 290.dp else 412.dp, end = if (engine.mirrored) 412.dp else 290.dp, top = 16.dp),
                            contentAlignment = if (narrow) Alignment.BottomCenter else Alignment.TopCenter,
                        ) {
                            // (the portrait route layout draws them under its turn card instead)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = curAlert != null && !engine.alertsUnderTurn,
                                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { if (narrow) it else -it }) + androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { if (narrow) it else -it }) + androidx.compose.animation.fadeOut(),
                            ) { lastAlert.value?.let { AlertBanner(it) } }
                        }
                    }
                }
            }
        }
    }
    if (donateVisible) DonateDialog(dark) { prefs.put(PreferenceKeys.DONATE_SHOWN, true); donateVisible = false }
    if (playInviteVisible) PlayInviteDialog(dark) { how ->
        prefs.put(PreferenceKeys.PLAY_INVITE, if (how == InviteClose.LATER) (System.currentTimeMillis() + 3L * 24 * 3600_000L).toString() else "never")
        playInviteVisible = false
    }
    if (whatsNewVisible) {
        // Follow the app's active day/night palette so the update card stays readable in both modes.
        val updatePalette = if (dark) DarkVw else LightVw
        val updateHeader = if (updatePalette.dark) Color(0xFF203B62) else Color(0xFFDCEBFF)
        val updateHeaderTitle = if (updatePalette.dark) Color.White else Color(0xFF172B46)
        val updateHeaderSub = if (updatePalette.dark) Color(0xFFD8E7FA) else Color(0xFF405875)
        val updateIconBg = if (updatePalette.dark) Color(0xFF234C43) else Color(0xFFDDF5E9)
        val updateIcon = if (updatePalette.dark) Color(0xFF9AF0C9) else Color(0xFF16734D)
        val updateNotesBg = if (updatePalette.dark) Color(0xFF192638) else Color(0xFFF4F7FC)
        val updateLabel = updatePalette.accent
        val conf = LocalConfiguration.current
        // landscape (wide and short, like most head units): a wide card with the title and button on the left and the changes in
        // two columns on the right, so more of them show at once; portrait keeps the tall single-column card
        val wideCard = conf.screenWidthDp > conf.screenHeightDp && conf.screenWidthDp >= 640
        val dismissWhatsNew = {
            prefs.put(PreferenceKeys.WHATS_NEW_SEEN_VERSION, installedVersion)
            prefs.put(PreferenceKeys.WHATS_NEW_SHOWN, com.headunit.launcher.Updater.markNotesShown(whatsNewNotes, prefs.s(PreferenceKeys.WHATS_NEW_SHOWN, "")))
            whatsNewVisible = false
        }
        androidx.compose.ui.window.Dialog(onDismissRequest = dismissWhatsNew,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = !wideCard)) {
            val shape = RoundedCornerShape(28.dp)
            val maxH = (conf.screenHeightDp.dp - 32.dp).coerceAtLeast(240.dp)
            val header: @Composable () -> Unit = {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(updateHeader).padding(18.dp)) {
                    androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                        Text(L.t("HORIZON NAV  /  UPDATE"), color = updatePalette.accent, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text(L.t("What's new in {0}", installedVersion), color = updateHeaderTitle, fontSize = 25.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, lineHeight = 30.sp)
                        val summary = remember(whatsNewNotes) { notesSummary(whatsNewNotes) }
                        Text(summary.ifBlank { L.t("Your settings and saved places stay as they were") }, color = updateHeaderSub, fontSize = 14.sp)
                    }
                }
            }
            val goButton: @Composable () -> Unit = {
                androidx.compose.material3.Button(
                    onClick = dismissWhatsNew,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = updatePalette.accent, contentColor = if (updatePalette.dark) Color(0xFF102039) else Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) { Text(L.t("Let's go"), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp)) }
            }
            if (wideCard) {
                val cardW = (conf.screenWidthDp.dp * 0.92f).coerceAtMost(1100.dp)
                // the list's share of the width, less the padding and gap: two columns once there's room for two readable cards
                val listW = cardW * 0.66f - 58.dp
                androidx.compose.foundation.layout.Row(
                    Modifier.width(cardW).heightIn(max = maxH).clip(shape).background(updatePalette.surface).border(1.dp, updatePalette.line, shape).padding(18.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp)
                ) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(0.34f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                        header(); goButton()
                    }
                    Box(Modifier.weight(0.66f)) { WhatsNewList(whatsNewNotes, updatePalette.dark, columns = if (listW >= 480.dp) 2 else 1, maxHeight = maxH - 36.dp) }
                }
            } else {
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxWidth().heightIn(max = maxH).clip(shape).background(updatePalette.surface).border(1.dp, updatePalette.line, shape).padding(22.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)
                ) {
                    header()
                    // each change on its own card, under New, Improved and Fixed (it used to be one block of text)
                    WhatsNewList(whatsNewNotes, updatePalette.dark)
                    goButton()
                }
            }
        }
    }

} } }

/**
 * Keeps the app clear of the camera notch, and of the status and navigation bars when Full screen is off. Android 15 and newer
 * draw every app behind them (for apps targeting Android 15 and up); before, Android kept the app clear by itself. This keeps
 * it looking exactly as it did, with the gap filled black. (On older Android these gaps already come back as nothing.)
 */
@Composable
internal fun SafeArea(prefs: Prefs, content: @Composable () -> Unit) {
    // (the cards, buttons and screens stay clear of the camera; the map itself reaches round it in full screen: see bleedCutout)
    val full = prefs.b(PreferenceKeys.FULLSCREEN, true)
    val ins = if (full) WindowInsets.displayCutout else WindowInsets.displayCutout.union(WindowInsets.systemBars)
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).windowInsetsPadding(ins),
    ) { content() }
}


/**
 * Lets the map run under the camera notch in full screen: the screen's contents are kept clear of the notch, and this stretches the
 * map back out over that strip, so the camera sits on the map rather than in a black band (Samsung S24 Ultra, GitHub).
 */
@Composable
internal fun Modifier.bleedCutout(on: Boolean): Modifier {
    if (!on) return this
    val ins = WindowInsets.displayCutout
    val d = androidx.compose.ui.platform.LocalDensity.current
    val dir = androidx.compose.ui.platform.LocalLayoutDirection.current
    val l = ins.getLeft(d, dir); val t = ins.getTop(d); val rr = ins.getRight(d, dir); val b = ins.getBottom(d)
    if (l == 0 && t == 0 && rr == 0 && b == 0) return this
    return this.layout { m, c ->
        if (!c.hasBoundedWidth || !c.hasBoundedHeight) {
            val p = m.measure(c); layout(p.width, p.height) { p.place(0, 0) }
        } else {
            val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(c.maxWidth + l + rr, c.maxHeight + t + b))
            layout(c.maxWidth, c.maxHeight) { p.place(-l, -t) }
        }
    }
}
