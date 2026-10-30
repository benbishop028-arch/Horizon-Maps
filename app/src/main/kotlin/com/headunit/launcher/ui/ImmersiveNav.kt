package com.headunit.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CarCrash
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalPolice
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Satellite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.Geo
import com.headunit.launcher.LL
import com.headunit.launcher.NavEngine
import kotlinx.coroutines.delay
import com.headunit.launcher.PreferenceKeys

private val Teal = GmGreen
private val TealDeep = GmGreenDeep

/** The map screen. It looks the same whichever map colours are chosen; it takes the shared soft palette. */
@Composable
fun ImmersiveNav(engine: NavEngine, onSettings: () -> Unit) {
    val base = LocalVw.current
    val glass = engine.prefs.b(PreferenceKeys.GLASS, true)
    val v = remember(base, glass) { navVw(base).let { if (glass) glassVw(it) else it } }
    CompositionLocalProvider(LocalVw provides v) { MapScreen(engine, onSettings) }
}

// ------------------------------------------------------------------ small building blocks

/** True for buttons inside the grouped column on the right: they sit flat in its one panel instead of each floating on its own. */
private val LocalInRail = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
private fun ImmCircle(onClick: () -> Unit, size: Dp = 52.dp, active: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val c = LocalVw.current
    Box(
        if (LocalInRail.current) Modifier.size(size).pressClick(onClick).padding(4.dp).clip(CircleShape).background(if (active) c.accent else Color.Transparent)
        else Modifier.size(size).panel(6.dp, CircleShape, if (active) c.accent else c.surface).pressClick(onClick),
        contentAlignment = Alignment.Center, content = content,
    )
}

@Composable
private fun ImmIcon(icon: ImageVector, onClick: () -> Unit, active: Boolean = false, size: Dp = 52.dp, desc: String? = null) {
    val c = LocalVw.current
    ImmCircle(onClick, size, active) { Icon(icon, desc, tint = if (active) Color.White else c.text, modifier = Modifier.size(size * 0.46f)) }
}

@Composable
internal fun ImmPill(text: String, onClick: () -> Unit, icon: ImageVector? = null, filled: Boolean = false) {
    val c = LocalVw.current
    val fg = if (filled) Color.White else c.text
    Row(
        Modifier.height(48.dp).panel(6.dp, RoundedCornerShape(24.dp), if (filled) c.accent else c.surface)
            .pressClick(onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

@Composable
internal fun ImmStat(value: String, label: String, color: Color, compact: Boolean = false) {
    val c = LocalVw.current
    Column {
        Text(value, color = color, fontSize = if (compact) 19.sp else 23.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        Text(label, color = c.sub, fontSize = if (compact) 11.sp else 12.sp, maxLines = 1, softWrap = false)
    }
}

private class TileSpec(val icon: ImageVector, val label: String, val tint: Color, val onClick: () -> Unit)

@Composable
private fun SheetHeading(text: String) {
    Text(text.uppercase(), color = LocalVw.current.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

// ------------------------------------------------------------------ the screen

@Composable
private fun MapScreen(engine: NavEngine, onSettings: () -> Unit) {
    val c = LocalVw.current
    val shareEta = rememberShareEta(engine)
    var q by rememberSaveable { mutableStateOf("") }
    var sheet by remember { mutableStateOf(false) }
    // voice: "take me to…", "share my ETA", "avoid this road", "mute"… anything else is searched for
    val voice = rememberVoiceInput("Say a destination or a command") { said ->
        when (val r = engine.handleVoice(said)) {
            null -> { q = said; engine.search(said) }
            "share" -> shareEta()
            "" -> {}
            else -> if (r.startsWith("search:")) { val w = r.removePrefix("search:"); q = w; engine.search(w) }
        }
    }
    val typingLocked by remember { derivedStateOf { (engine.fix?.speed ?: 0.0) > 3.0 && engine.prefs.b(PreferenceKeys.DRIVELOCK, true) } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(q) {
        if (q.length >= 3) { delay(500); engine.search(q) } else if (q.isEmpty()) engine.results = emptyList()
    }
    // text shared from another app goes into the search box
    LaunchedEffect(engine.externalQuery) { engine.externalQuery?.let { q = it; engine.externalQuery = null } }
    BackHandler(enabled = sheet) { sheet = false }
    val routing = engine.route != null && !engine.previewing
    val previewing = engine.previewing
    val simple by remember { derivedStateOf { engine.driving && engine.prefs.b(PreferenceKeys.DRIVESIMPLE, true) } }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        // portrait (or any narrow window): cards use the full width, the round buttons stack down the right-hand side,
        // and your speed and the re-centre button sit just above the trip card instead of beside it
        val narrow = maxWidth < 720.dp
        val dens = androidx.compose.ui.platform.LocalDensity.current
        var tripPx by remember { mutableStateOf(0) }
        // where the speedometer starts (in pixels), so zoom only moves beside the panel when it would really touch it
        var speedTopPx by remember { mutableStateOf(-1) }
        val tripH = with(dens) { tripPx.toDp() }
        val screenH = maxHeight
        LaunchedEffect(narrow, tripPx, screenH) { engine.bottomCover = if (narrow && tripPx > 0) (tripH / screenH).coerceIn(0f, 0.5f).toDouble() else 0.0 }
        // left-hand drive: everything swaps sides (cards on the right, round buttons on the left)
        val mir = engine.mirrored
        // Settings > sizes > map buttons: scales the button column on the right
        val mainButtonScale = engine.prefs.i(PreferenceKeys.MAIN_BUTTON_SIZE, 100).coerceIn(70, 150) / 100f
        fun side(a: Alignment): Alignment = if (!mir) a else when (a) {
            Alignment.TopStart -> Alignment.TopEnd; Alignment.TopEnd -> Alignment.TopStart
            Alignment.CenterStart -> Alignment.CenterEnd; Alignment.CenterEnd -> Alignment.CenterStart
            Alignment.BottomStart -> Alignment.BottomEnd; Alignment.BottomEnd -> Alignment.BottomStart
            else -> a
        }
        // padding on the "outer" (screen edge) and "inner" sides, swapped when mirrored
        fun Modifier.sides(start: androidx.compose.ui.unit.Dp = 0.dp, end: androidx.compose.ui.unit.Dp = 0.dp, top: androidx.compose.ui.unit.Dp = 0.dp, bottom: androidx.compose.ui.unit.Dp = 0.dp) =
            if (mir) padding(start = end, end = start, top = top, bottom = bottom) else padding(start = start, end = end, top = top, bottom = bottom)
        // ---- top left: search while idle; the turn card and its companions while navigating
        // the panels' outlines (as parts of the screen) go to the camera, so your arrow is moved clear of whatever is beside it
        val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f); val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        fun report(i: Int, r: androidx.compose.ui.geometry.Rect) { engine.setPanel(i, r.left / boxW, r.top / boxH, r.right / boxW, r.bottom / boxH) }
        Column(Modifier.align(side(Alignment.TopStart)).padding(16.dp).then(if (narrow) Modifier.fillMaxWidth().sides(end = 52.dp * mainButtonScale + 10.dp) else Modifier.width(380.dp))
            .onGloballyPositioned { report(0, it.boundsInParent()) }
            // the turn list opens over the trip card below it
            .zIndex(if (engine.itineraryOpen) 2f else 0f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // every block slides and fades in and out instead of popping, so the screen changes feel like one piece
            AnimatedVisibility(!routing && !previewing, enter = fadeIn(tween(220)) + androidx.compose.animation.expandVertically(tween(260)), exit = fadeOut(tween(160)) + androidx.compose.animation.shrinkVertically(tween(220))) {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ImmSearch(engine, q, { q = it }, typingLocked, focus, voice)
                engine.phoneDest?.let { pd -> PhoneDestCard(engine, pd) }
                val showResults = engine.results.isNotEmpty()
                AnimatedVisibility(showResults, enter = fadeIn(tween(180)) + androidx.compose.animation.expandVertically(tween(240)), exit = fadeOut(tween(120)) + androidx.compose.animation.shrinkVertically(tween(180))) {
                    ImmResults(engine) { q = "" }
                }
                AnimatedVisibility(!showResults && q.isEmpty() && engine.nearbyKind == null, enter = fadeIn(tween(220)) + androidx.compose.animation.expandVertically(tween(260)), exit = fadeOut(tween(140)) + androidx.compose.animation.shrinkVertically(tween(200))) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // your places (Home, Work...) always stay under the search bar; while you drive with no route, the suggestion and
                        // resume cards stay too and the rest folds away
                        val cruising = engine.cruising
                        ImmChips(engine) { try { focus.requestFocus() } catch (_: Exception) {} }
                        AnimatedVisibility(!cruising, enter = fadeIn(tween(220)) + androidx.compose.animation.expandVertically(tween(260)), exit = fadeOut(tween(140)) + androidx.compose.animation.shrinkVertically(tween(200))) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                PlannedCard(engine)
                                AppUpdateCard(engine)
                                // newer map data first; the "it's getting old" reminder only when there's nothing newer to offer
                                if (engine.dataUpdates.isNotEmpty()) DataUpdateCard(engine) else PackAgeCard(engine)
                            }
                        }
                        ImmQuick(engine, showSaved = !cruising)
                    }
                }
              }
            }
            if (routing) {
                if (engine.results.isNotEmpty()) ImmResults(engine) { q = "" }
                else {
                    val curTurn = engine.turn
                    val lastTurn = remember { mutableStateOf(curTurn) }
                    if (curTurn != null) lastTurn.value = curTurn
                    AnimatedVisibility(curTurn != null, enter = fadeIn(tween(240)) + androidx.compose.animation.slideInVertically(tween(300)) { -it / 4 }, exit = fadeOut(tween(160))) {
                        lastTurn.value?.let { t ->
                            if (engine.itineraryOpen) {
                                val itineraryDensity = LocalDensity.current
                                val itineraryScale = engine.prefs.i(PreferenceKeys.MAIN_CARD_SIZE, 100).coerceIn(70, 150) / 100f
                                CompositionLocalProvider(LocalDensity provides Density(itineraryDensity.density * itineraryScale, itineraryDensity.fontScale)) { ItineraryCard(engine) }
                            } else {
                                val turnDensity = LocalDensity.current
                                val cardSize = engine.prefs.i(PreferenceKeys.MAIN_CARD_SIZE, 100).coerceIn(70, 150) / 100f
                                CompositionLocalProvider(LocalDensity provides Density(turnDensity.density * cardSize, turnDensity.fontScale)) {
                                    ImmTurnCard(engine, t)
                                }
                            }
                        }
                    }
                    engine.advice?.let { AdvicePill(it) }
                    AvgSpeedCard(engine)
                    ServicesCard(engine)
                    ParkingCard(engine)
                }
            }
            if (engine.nearbyKind != null && (!previewing || engine.stopMode)) NearbyCard(engine)
        }

        // ---- right side: the map buttons grouped in one panel at the top, zoom under it, and Settings beside it
        if (!previewing) {
            val buttonBaseDensity = LocalDensity.current
            // just two buttons on the map: the compass and map options. 3D / 2D and sound are at the top of map options, voice and sound
            // are on the trip card while navigating, Settings is beside these when you're not (and in map options' header when you are)
            val railItems = ArrayList<@Composable () -> Unit>()
            railItems.add(@Composable { ImmCompass(engine) })
            railItems.add(@Composable { ImmIcon(Icons.Rounded.Layers, { sheet = !sheet }, active = sheet, desc = "Map options") })
            if (!simple && (engine.prefs.b(PreferenceKeys.DEMO, false) || engine.simMode != 0)) railItems.add(@Composable { ImmIcon(Icons.Rounded.PlayArrow, { engine.cycleSim() }, active = engine.simMode != 0, desc = "Demo drive") })
            // zoom: hidden while the map is following you on the move (it zooms itself); back as soon as you touch or zoom the map, or
            // whenever you're stopped. (A little hysteresis on the speed, so it doesn't flicker at walking pace.)
            var stopped by remember { mutableStateOf(true) }
            val spNow = engine.fix?.speed ?: 0.0
            androidx.compose.runtime.SideEffect { if (spNow > 3.0) stopped = false else if (spNow < 1.0) stopped = true }
            val showZoom = stopped || !engine.following || kotlin.math.abs(engine.zoomOffset) > 0.01
            val showSettings = !routing
            val unit = 52.dp * mainButtonScale
            // the top of the speedometer (measured; a fair guess until it has been laid out)
            val speedTop = if (speedTopPx >= 0) with(dens) { speedTopPx.toDp() } else maxHeight - 190.dp
            // landscape: Settings sits to the left of the panel (to the right when mirrored) so the panel starts at the very top, with
            // zoom straight under it. Portrait: Settings above the panel, as there's no room beside it next to the search bar.
            val settingsAbove = narrow && showSettings
            val top = 16.dp * mainButtonScale + (if (settingsAbove) unit + 10.dp * mainButtonScale else 0.dp)
            // (with zoom hidden there's nothing to fit under the panel)
            val zoomH = (2 * 52 + 1).dp * mainButtonScale
            // zoom goes under the panel unless that would touch the speedometer; then it moves beside the panel, and on a really short
            // screen the panel splits into two columns rather than run into the speedometer
            val railH = unit * railItems.size
            val zoomUnder = narrow || top + railH + 10.dp * mainButtonScale + zoomH + 8.dp <= speedTop
            val perCol = ((speedTop - top - 8.dp) / unit).toInt().coerceAtLeast(3)
            val chunks = if (narrow || railItems.size <= perCol) listOf(railItems.toList()) else railItems.chunked(perCol)
            CompositionLocalProvider(LocalDensity provides Density(buttonBaseDensity.density * mainButtonScale, buttonBaseDensity.fontScale)) {
                val rail: @Composable (List<@Composable () -> Unit>) -> Unit = { part ->
                    Column(Modifier.panel(6.dp, RoundedCornerShape(26.dp), c.surface), horizontalAlignment = Alignment.CenterHorizontally) {
                        CompositionLocalProvider(LocalInRail provides true) { for (item in part) item() }
                    }
                }
                val zoom: @Composable () -> Unit = {
                    AnimatedVisibility(showZoom, enter = fadeIn(tween(200)) + androidx.compose.animation.expandVertically(tween(220)), exit = fadeOut(tween(250)) + androidx.compose.animation.shrinkVertically(tween(260))) {
                        Column(Modifier.panel(6.dp, RoundedCornerShape(26.dp), c.surface), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(52.dp).pressClick { engine.zoom(1.0) }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, "Zoom in", tint = c.text, modifier = Modifier.size(26.dp)) }
                            Box(Modifier.width(26.dp).height(1.dp).background(c.line))
                            Box(Modifier.size(52.dp).pressClick { engine.zoom(-1.0) }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Remove, "Zoom out", tint = c.text, modifier = Modifier.size(26.dp)) }
                        }
                    }
                }
                val settings: @Composable () -> Unit = { if (showSettings) ImmIcon(Icons.Rounded.Settings, { sheet = false; onSettings() }, desc = "Settings") }
                // the panel against the screen edge with zoom under it (or beside it), in as many columns as it needs
                val panelAndZoom: @Composable () -> Unit = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        val parts = if (mir) chunks else chunks.asReversed()
                        if (!zoomUnder && !mir) zoom()
                        for ((i, part) in parts.withIndex()) {
                            // zoom goes under the column that's against the screen edge
                            val edge = if (mir) i == 0 else i == parts.size - 1
                            if (edge && zoomUnder) Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { rail(part); zoom() }
                            else rail(part)
                        }
                        if (!zoomUnder && mir) zoom()
                    }
                }
                // (its outline goes to the camera too, so with no route your arrow sits in the middle of the clear map)
                if (settingsAbove) Column(Modifier.align(side(Alignment.TopEnd)).padding(16.dp).onGloballyPositioned { report(2, it.boundsInParent()) }, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = if (mir) Alignment.Start else Alignment.End) {
                    settings(); panelAndZoom()
                }
                else Row(Modifier.align(side(Alignment.TopEnd)).padding(16.dp).onGloballyPositioned { report(2, it.boundsInParent()) }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    if (mir) { panelAndZoom(); settings() } else { settings(); panelAndZoom() }
                }
            }
        } else androidx.compose.runtime.SideEffect { engine.setPanel(2, 0f, 0f, 0f, 0f) }

        // ---- speed limit and speedometer: bottom right, or bottom left when mirrored for left-hand drive (the cards are on the
        // right then; keeping the speed on the right too put it under the turn card on shorter screens)
        if (!previewing) Box(
            Modifier.align(side(Alignment.BottomEnd))
                .sides(end = 16.dp, bottom = if (narrow && tripPx > 0) tripH + 4.dp else 16.dp)
                .onGloballyPositioned { val t = it.boundsInParent().top.toInt(); if (it.size.height > 0 && t != speedTopPx) speedTopPx = t }
        ) {
            val baseSpeedDensity = LocalDensity.current
            // (sized by Settings > Screen & sizes > Speed limit and speedometer size, inside ImmSpeed)
            val speedSize = 1f
            CompositionLocalProvider(LocalDensity provides Density(baseSpeedDensity.density * speedSize, baseSpeedDensity.fontScale)) { ImmSpeed(engine) }
        }

        // ---- bottom left: hazards and the trip card
        if (!previewing && engine.arrival == null) {
            val tripBaseDensity = LocalDensity.current
            val tripCardScale = engine.prefs.i(PreferenceKeys.MAIN_CARD_SIZE, 100).coerceIn(70, 150) / 100f
            Box(Modifier.align(side(Alignment.BottomStart)).then(if (narrow) Modifier.fillMaxWidth().sides(end = 16.dp) else Modifier).onSizeChanged { tripPx = it.height }
                .onGloballyPositioned { report(1, it.boundsInParent()) }) {
                CompositionLocalProvider(LocalDensity provides Density(tripBaseDensity.density * tripCardScale, tripBaseDensity.fontScale)) {
                    TripArea(engine, shareEta, narrow, voice)
                }
            }
        }
        else androidx.compose.runtime.SideEffect { if (tripPx != 0) tripPx = 0; engine.setPanel(1, 0f, 0f, 0f, 0f) }

        // ---- bottom centre: re-centre once you have panned away, or zoomed in / out with the buttons (it puts the zoom back to automatic)
        AnimatedVisibility(
            !previewing && (!engine.following || kotlin.math.abs(engine.zoomOffset) > 0.01) && engine.arrival == null, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (narrow && tripPx > 0) tripH + 10.dp else 18.dp),
            enter = fadeIn(tween(200)) + androidx.compose.animation.scaleIn(tween(220), initialScale = 0.85f), exit = fadeOut(tween(150)) + androidx.compose.animation.scaleOut(tween(150), targetScale = 0.9f),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ImmPill("Re-centre", { engine.recenter() }, Icons.Rounded.MyLocation, filled = true)
                // zoomed with + and - while following you, with no route: keep this as your normal zoom
                if (!routing && engine.following && kotlin.math.abs(engine.zoomOffset) > 0.01) ImmPill("Make this my default", { engine.saveZoomAsDefault() }, Icons.Rounded.ZoomIn)
            }
        }

        // ---- top centre: the traffic view while you're stopped (it goes back by itself when you move off)
        AnimatedVisibility(engine.stoppedOverview && !previewing, modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            enter = fadeIn(tween(300)), exit = fadeOut(tween(250))) {
            Row(Modifier.panel(6.dp, RoundedCornerShape(20.dp), c.surface).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Traffic, null, tint = c.accent, modifier = Modifier.size(18.dp))
                Text("Traffic round you · back when you move", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
            }
        }

        if (previewing) RoutePreview(engine)
        // the arrival moment: the summary slides up from the bottom while the camera swoops down to the destination
        val curArr = engine.arrival
        val lastArr = remember { mutableStateOf(curArr) }
        if (curArr != null) lastArr.value = curArr
        AnimatedVisibility(
            visible = curArr != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(520)) { it } + fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + fadeOut(),
        ) { lastArr.value?.let { a -> ArrivalCard(engine, a) } }

        engine.faster?.let { fr ->
            Row(
                Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = if (narrow) tripH + 10.dp else 84.dp).widthIn(max = 540.dp).panel(10.dp, RoundedCornerShape(24.dp), c.surface).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Good.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Route, null, tint = Good, modifier = Modifier.size(24.dp)) }
                Column {
                    Text("Faster route found", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Saves about ${fr.savedMin} min", color = Good, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                ImmPill("Switch", { engine.acceptFaster() }, filled = true)
                ImmPill("Keep", { engine.dismissFaster() })
            }
        }

        AnimatedVisibility(engine.busy, modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp), enter = fadeIn(tween(160)) + androidx.compose.animation.scaleIn(tween(200), initialScale = 0.7f), exit = fadeOut(tween(160))) {
            Box(Modifier.size(44.dp).panel(6.dp, CircleShape, c.surface), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 3.dp)
            }
        }

        // ---- the options sheet slides in from the right; tapping the map closes it
        if (sheet && !previewing) Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { sheet = false },
        )
        AnimatedVisibility(
            visible = sheet && !previewing,
            modifier = Modifier.align(side(Alignment.CenterEnd)),
            enter = slideInHorizontally(initialOffsetX = { if (mir) -it else it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { if (mir) -it else it }) + fadeOut(),
        ) { OptionsSheet(engine, shareEta, wide = !narrow && maxWidth >= 860.dp && maxHeight < maxWidth, onSettings = { sheet = false; onSettings() }) { sheet = false } }
        if (engine.stopsEditor) StopsDialog(engine, onClose = { engine.stopsEditor = false })
    }
}

/**
 * Map options: the map's look first (Standard or Satellite, Auto / Day / Night), then what's shown on it as tiles that light up when
 * on, then the trip tools, reports and things to find. In landscape on a wide screen it's twice as wide, in two columns, so nothing
 * needs scrolling; it still scrolls if a small screen needs it.
 */
@Composable
private fun OptionsSheet(engine: NavEngine, shareEta: () -> Unit, wide: Boolean = false, onSettings: () -> Unit = {}, close: () -> Unit) {
    val c = LocalVw.current
    val p = engine.prefs
    val routing = engine.route != null && !engine.previewing
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxHeight().widthIn(max = if (wide) 760.dp else 410.dp).fillMaxWidth().padding(10.dp).shadow(16.dp, shape).clip(shape).background(c.panel)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Map options", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            ImmIcon(Icons.Rounded.Settings, onSettings, size = 42.dp, desc = "Settings")
            Spacer(Modifier.width(8.dp))
            ImmIcon(Icons.Rounded.Close, close, size = 42.dp, desc = "Close")
        }
        val mapPart: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // quick rows: the view and the sound (they were round buttons on the map)
                SheetHeading("View")
                Segments(listOf("2D", "3D"), if (engine.tilt3d) 1 else 0) { i -> if ((i == 1) != engine.tilt3d) engine.toggleTilt() }
                SheetHeading("Sound")
                Segments(listOf("Voice", "Alerts only", "Mute"), engine.soundMode) { i -> p.put(PreferenceKeys.SOUND_MODE, i) }
                SheetHeading("Map")
                // the map's look: two picture cards, then day and night
                val sat = p.b(PreferenceKeys.SATELLITE, false)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LookCard(Modifier.weight(1f), "Standard", false, !sat) { p.put(PreferenceKeys.SATELLITE, false) }
                    LookCard(Modifier.weight(1f), "Satellite", true, sat) { p.put(PreferenceKeys.SATELLITE, true) }
                }
                // Auto covers every automatic choice in Settings (headlights, sun times, your own times): tapping it while one of those
                // is on leaves it as it is
                val theme = p.s(PreferenceKeys.THEME, "auto")
                val seg = when (theme) { "day" -> 1; "night" -> 2; else -> 0 }
                Segments(listOf("Auto", "Day", "Night"), seg) { i -> if (i != seg) p.put(PreferenceKeys.THEME, listOf("auto", "day", "night")[i]) }
                SheetHeading("On the map")
                fun tog(key: String, def: Boolean): Pair<Boolean, () -> Unit> { val on = p.b(key, def); return on to { p.put(key, !on) } }
                val layers = listOf(
                    Triple(Icons.Rounded.Traffic, "Traffic", tog(PreferenceKeys.TRAFFIC, true)),
                    Triple(Icons.Rounded.Apartment, "3D buildings", tog(PreferenceKeys.BUILDINGS_3D, true)),
                    Triple(Icons.Rounded.Traffic, "Traffic lights", tog(PreferenceKeys.SIGNALS, true)),
                    Triple(Icons.Rounded.CameraAlt, "Cameras", tog(PreferenceKeys.HAZARD_ICONS, true)),
                    Triple(Icons.Rounded.Lightbulb, "Street lamps", tog(PreferenceKeys.LAMPS, true)),
                    Triple(Icons.Rounded.Park, "Trees", tog(PreferenceKeys.TREES, true)),
                    Triple(Icons.Rounded.DirectionsWalk, "Footpaths", tog(PreferenceKeys.PATHS, true)),
                    Triple(Icons.Rounded.WaterDrop, "Rain radar", tog(PreferenceKeys.RAIN_RADAR, false)),
                    Triple(Icons.Rounded.AcUnit, "Weather", tog(PreferenceKeys.RAIN_EFFECTS, false)),
                )
                for (row in layers.chunked(3)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((icon, label, st) in row) ToggleTile(Modifier.weight(1f), icon, label, st.first, st.second)
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        val tripPart: @Composable () -> Unit = {
            if (routing) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetHeading("This trip")
                WideTiles(4, listOf(
                    TileSpec(Icons.Rounded.Add, if (engine.via.isEmpty()) "Add stop" else "Stops", c.accent, { engine.stopsEditor = true; close() }),
                    TileSpec(Icons.Rounded.DoNotDisturbOn, "Avoid road", Warn, { engine.avoidRoadAhead(); close() }),
                    TileSpec(Icons.Rounded.Route, "Overview", c.accent, { engine.overview(); close() }),
                    TileSpec(Icons.Rounded.Share, "Share ETA", Good, { shareEta(); close() }),
                ))
            }
        }
        val restPart: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetHeading("Report here")
                WideTiles(5, listOf(
                    TileSpec(Icons.Rounded.LocalPolice, "Police", Color(0xFF1E66F5), { engine.report("police"); close() }),
                    TileSpec(Icons.Rounded.CarCrash, "Crash", Bad, { engine.report("accident"); close() }),
                    TileSpec(Icons.Rounded.PriorityHigh, "Hazard", Warn, { engine.report("hazard"); close() }),
                    TileSpec(Icons.Rounded.Build, "Broken down", Color(0xFFFB8C00), { engine.report("breakdown"); close() }),
                    TileSpec(Icons.Rounded.CameraAlt, "Mobile cam", Color(0xFF1E66F5), { engine.report("mobilecam"); close() }),
                ), compact = true)
                // with no route the map page already has fuel, charging, food, coffee and parking, so only the rest are here
                fun find(kind: String): () -> Unit = { engine.loadNearby(kind, false); close() }
                val finds = listOfNotNull(
                    if (engine.route != null) TileSpec(Icons.Rounded.LocalGasStation, "Fuel", Color(0xFFEA4335), find("fuel")) else null,
                    if (engine.route != null) TileSpec(Icons.Rounded.EvStation, "Charging", Color(0xFF34A853), find("charging")) else null,
                    if (engine.route != null) TileSpec(Icons.Rounded.Restaurant, "Food", Color(0xFFF29900), find("food")) else null,
                    if (engine.route != null) TileSpec(Icons.Rounded.LocalCafe, "Coffee", Color(0xFFA56BFF), find("coffee")) else null,
                    TileSpec(Icons.Rounded.Wc, "Toilets", Color(0xFF4C8DFF), find("toilets")),
                    if (engine.route != null) TileSpec(Icons.Rounded.LocalParking, "Parking", Color(0xFF4C8DFF), find("parking")) else null,
                    if (engine.route != null) TileSpec(Icons.Rounded.Apartment, "Services", Color(0xFF12A4B4), find("services")) else null,
                    if (engine.dest != null) TileSpec(Icons.Rounded.Place, "Park at end", Color(0xFFEA4335), { engine.loadNearby("parking", true); close() }) else null,
                )
                SheetHeading(if (engine.route != null) "Find along the route" else "Find nearby")
                WideTiles(4, finds)
            }
        }
        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.weight(1f)) { mapPart() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { tripPart(); restPart() }
        } else {
            // while navigating the trip tools come first; otherwise the map
            tripPart(); mapPart(); restPart()
        }
    }
}

/** A row of choices with the chosen one filled in blue (2D / 3D, sound, day and night). */
@Composable
private fun Segments(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val c = LocalVw.current
    Row(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(14.dp)).background(c.tile).padding(3.dp)) {
        for ((i, label) in labels.withIndex()) {
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(11.dp)).background(if (i == selected) c.accent else Color.Transparent)
                .clickable { onPick(i) }, contentAlignment = Alignment.Center) {
                Text(label, color = if (i == selected) Color.White else c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

private fun soundIcon(mode: Int): ImageVector = when (mode) { 0 -> Icons.Rounded.VolumeUp; 1 -> Icons.Rounded.NotificationsActive; else -> Icons.Rounded.VolumeOff }

/** Standard or Satellite, as a little picture with its name under it; outlined when it's the one in use. */
@Composable
private fun LookCard(modifier: Modifier, label: String, satellite: Boolean, selected: Boolean, onClick: () -> Unit) {
    val c = LocalVw.current
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.clip(shape).background(if (selected) c.accent.copy(alpha = 0.18f) else c.tile)
        .border(if (selected) 2.dp else 0.dp, if (selected) c.accent else Color.Transparent, shape).pressClick(onClick)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(54.dp)) {
            val w = size.width; val h = size.height
            if (satellite) {
                drawRect(Color(0xFF3E5A35))
                drawRect(Color(0xFF6B7F4A), topLeft = Offset(w * 0.08f, h * 0.12f), size = androidx.compose.ui.geometry.Size(w * 0.3f, h * 0.38f))
                drawRect(Color(0xFF7C6A4E), topLeft = Offset(w * 0.55f, h * 0.55f), size = androidx.compose.ui.geometry.Size(w * 0.35f, h * 0.3f))
                drawLine(Color(0xFFBDB6A6), Offset(0f, h * 0.72f), Offset(w, h * 0.38f), strokeWidth = h * 0.08f)
            } else {
                drawRect(if (c.dark) Color(0xFF2A3348) else Color(0xFFE9EDF3))
                val road = if (c.dark) Color(0xFF55607A) else Color.White
                drawLine(road, Offset(0f, h * 0.65f), Offset(w, h * 0.35f), strokeWidth = h * 0.1f)
                drawLine(road, Offset(w * 0.35f, 0f), Offset(w * 0.5f, h), strokeWidth = h * 0.07f)
                drawRect(if (c.dark) Color(0xFF2E4A3A) else Color(0xFFCDE8D2), topLeft = Offset(w * 0.62f, h * 0.6f), size = androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.3f))
            }
        }
        Text(label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

/** A switch as a tile: lit up in blue when it's on. */
@Composable
private fun ToggleTile(modifier: Modifier, icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalVw.current
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.clip(shape).background(if (on) c.accent.copy(alpha = 0.2f) else c.tile)
        .border(if (on) 1.5.dp else 0.dp, if (on) c.accent else Color.Transparent, shape).pressClick(onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, if (on) "$label on" else "$label off", tint = if (on) c.accent else c.sub, modifier = Modifier.size(24.dp))
        Text(label, color = if (on) c.text else c.sub, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
    }
}

/** Tiles in rows of [cols] that share the width evenly. [compact]: smaller, for a row of five. */
@Composable
private fun WideTiles(cols: Int, tiles: List<TileSpec>, compact: Boolean = false) {
    val c = LocalVw.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in tiles.chunked(cols)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            for (t in row) Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.tile).pressClick(t.onClick).padding(vertical = if (compact) 9.dp else 11.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(if (compact) 32.dp else 38.dp).clip(CircleShape).background(t.tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(t.icon, null, tint = t.tint, modifier = Modifier.size(if (compact) 19.dp else 22.dp))
                }
                Text(t.label, color = c.text, fontSize = if (compact) 11.sp else 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            }
            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Bottom left: hazard badges, then the trip card with the route bar, arrival, time and distance left, and the trip buttons. */
@Composable
private fun TripArea(engine: NavEngine, shareEta: () -> Unit, narrow: Boolean = false, voice: () -> Unit = {}) {
    val c = LocalVw.current
    val inc = engine.incidentAhead
    val cam = engine.cameraDist
    val eta = engine.eta
    val radarOn = engine.prefs.b(PreferenceKeys.HAZARDRADAR, false)
    if (inc == null && cam == null && eta == null && (!radarOn || engine.radar.isEmpty())) return
    val mir = engine.mirrored
    Column(Modifier.padding(start = if (mir) 0.dp else 16.dp, end = if (mir) 16.dp else 0.dp, bottom = 16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = if (mir) Alignment.End else Alignment.Start) {
        // hazard radar: the next two miles at a glance (replaces the separate camera and incident badges)
        if (radarOn) HazardRadar(engine)
        else if ((inc != null && engine.alert?.kind != "incident") || (cam != null && engine.alert?.kind != "camera")) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (inc != null && engine.alert?.kind != "incident") HazardBadge("incident", inc.label, Geo.fmtDist(inc.dist, engine.imperial))
            if (cam != null && engine.alert?.kind != "camera") HazardBadge("camera", "Speed camera", Geo.fmtDist(cam.toDouble(), engine.imperial))
        }
        eta?.let { e ->
            val dc = if (e.delayMin >= 10) Bad else if (e.delayMin >= 1) Warn else Good
            val shape = RoundedCornerShape(22.dp)
            // landscape: the card is only as wide as its contents (figures, then the buttons close by), and the route bar matches that width.
            // portrait: full width, with the buttons under the figures.
            val dens = androidx.compose.ui.platform.LocalDensity.current
            var rowPx by remember { mutableStateOf(0) }
            val rowW = with(dens) { rowPx.toDp() }
            Column(Modifier.then(if (narrow) Modifier.fillMaxWidth() else Modifier).panel(10.dp, shape, c.surface).animateContentSize()) {
                val barMod = if (narrow) Modifier.fillMaxWidth() else Modifier.width(rowW)
                // the route bar across the top of the card, with room to breathe
                if (engine.showRouteBar && (narrow || rowPx > 0)) {
                    RouteBar(engine, barMod.padding(start = if (narrow) 16.dp else 18.dp, end = if (narrow) 16.dp else 18.dp, top = if (narrow) 7.dp else 9.dp, bottom = if (narrow) 2.dp else 4.dp))
                    Box(barMod.padding(horizontal = 18.dp).height(1.dp).background(c.line))
                }
                // electric: roughly how much battery you'll arrive with (amber under 20%, red under 10%)
                val evPct = engine.evArrivalPct()
                val evCol = if (evPct == null) c.text else if (evPct < 10) Bad else if (evPct < 20) Warn else Good
                val stats: @Composable () -> Unit = {
                    ImmStat(Geo.fmtDur(e.secs), if (e.delayMin >= 1) "+${e.delayMin} min traffic" else "time left", dc)
                    ImmStat(e.arrive, "arrival", c.text)
                    ImmStat(Geo.fmtDist(e.dist, engine.imperial), "to go", c.text)
                    if (evPct != null) ImmStat("$evPct%", "battery at arrival", evCol)
                }
                val buttons: @Composable () -> Unit = {
                    // electric: chargers along the route from here
                    if (evPct != null) ImmCircle({ engine.loadNearby("charging", false) }, 40.dp) { Icon(Icons.Rounded.EvStation, "Chargers ahead", tint = Good, modifier = Modifier.size(20.dp)) }
                    ImmCircle({ voice() }, 40.dp) { Icon(Icons.Rounded.Mic, "Voice", tint = c.text, modifier = Modifier.size(20.dp)) }
                    ImmCircle({ engine.cycleSound() }, 40.dp) { Icon(soundIcon(engine.soundMode), "Sound", tint = if (engine.soundMode == 2) Bad else c.text, modifier = Modifier.size(20.dp)) }
                    ImmCircle({ engine.stopsEditor = true }, 40.dp) { Icon(Icons.Rounded.Add, "Stops", tint = c.text, modifier = Modifier.size(21.dp)) }
                    Box(Modifier.size(40.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Bad).pressClick { engine.endRoute() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, "End route", tint = Color.White, modifier = Modifier.size(21.dp))
                    }
                }
                if (!narrow) Row(
                    Modifier.widthIn(min = 360.dp).onSizeChanged { rowPx = it.width }.padding(start = 18.dp, end = 12.dp, top = 9.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    stats()
                    Row(Modifier.padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { buttons() }
                } else Row(
                    // portrait: one compact line (figures, then the buttons), so the card stays low and clear of your arrow
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        ImmStat(Geo.fmtDur(e.secs), if (e.delayMin >= 1) "+${e.delayMin} min" else "time left", dc, compact = true)
                        ImmStat(e.arrive, "arrival", c.text, compact = true)
                        ImmStat(Geo.fmtDist(e.dist, engine.imperial), "to go", c.text, compact = true)
                        if (evPct != null) ImmStat("$evPct%", "battery", evCol, compact = true)
                    }
                    Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (evPct != null) ImmCircle({ engine.loadNearby("charging", false) }, 36.dp) { Icon(Icons.Rounded.EvStation, "Chargers ahead", tint = Good, modifier = Modifier.size(19.dp)) }
                        ImmCircle({ voice() }, 36.dp) { Icon(Icons.Rounded.Mic, "Voice", tint = c.text, modifier = Modifier.size(19.dp)) }
                        ImmCircle({ engine.cycleSound() }, 36.dp) { Icon(soundIcon(engine.soundMode), "Sound", tint = if (engine.soundMode == 2) Bad else c.text, modifier = Modifier.size(19.dp)) }
                        ImmCircle({ engine.stopsEditor = true }, 36.dp) { Icon(Icons.Rounded.Add, "Stops", tint = c.text, modifier = Modifier.size(20.dp)) }
                        Box(Modifier.size(36.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Bad).pressClick { engine.endRoute() }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, "End route", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImmCompass(engine: NavEngine) {
    val c = LocalVw.current
    Box(
        (if (LocalInRail.current) Modifier.size(52.dp) else Modifier.size(52.dp).panel(6.dp, CircleShape, c.surface)).pressClick { engine.toggleNorthUp() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(width = 4.dp, height = 8.dp).clip(RoundedCornerShape(2.dp)).background(Bad))
            Text("N", color = if (engine.northUp) c.accent else c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Speed limit sign beside a large readout of your current speed. */
@Composable
internal fun ImmSpeed(engine: NavEngine) {
    // the size from Settings (100% = the standard size): it's laid out at that size, so it keeps clear of the edges and other cards
    val pct = engine.prefs.i(PreferenceKeys.SPEED_SIZE, 100).coerceIn(50, 200)
    if (pct == 100) { SpeedBlock(engine); return }
    val base = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density * pct / 100f, base.fontScale),
    ) { SpeedBlock(engine) }
}

@Composable
private fun SpeedBlock(engine: NavEngine) {
    val c = LocalVw.current
    val show = engine.prefs.b(PreferenceKeys.MAPSPEED, true)
    val lim = engine.speedLimit
    if (lim == null && !show) return
    val k = if (engine.imperial) 2.23694 else 3.6
    if (engine.prefs.b(PreferenceKeys.SPEEDRING, true)) { SpeedRing(engine, show, k); return }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Keep the speed cluster in the same screen position, but mirror the sign's
        // relationship to the speed readout for left-hand-drive layouts.
        if (show && engine.mirrored && !engine.isCar && lim != null) SpeedLimitSign(engine, 70.dp)
        if (show) Column(
            Modifier.width(78.dp).panel(8.dp, RoundedCornerShape(24.dp), c.surface).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${Math.round((engine.fix?.speed ?: 0.0) * k)}", color = speedTint(engine) ?: c.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(if (engine.imperial) "mph" else "km/h", color = c.sub, fontSize = 12.sp)
        }
        if (lim != null && !(show && engine.mirrored && !engine.isCar)) SpeedLimitSign(engine, 70.dp)
    }
}

/**
 * Speedometer ring: a glowing arc that fills towards the limit and warms to amber, then red, as you near or pass it,
 * with your speed in the middle and the limit sign tucked against it.
 */
@Composable
private fun SpeedRing(engine: NavEngine, show: Boolean, k: Double) {
    val c = LocalVw.current
    val lim = engine.speedLimit
    val sp = engine.fix?.speed ?: 0.0
    val full = if (lim != null && lim > 0) lim * 1.2 else 31.3          // the ring is full at 20% over the limit (or 70 mph with no limit)
    val target = (sp / full).toFloat().coerceIn(0f, 1f)
    val frac by androidx.compose.animation.core.animateFloatAsState(target, androidx.compose.animation.core.tween(350), label = "ring")
    val ratio = if (lim != null && lim > 0 && !engine.limitEstimated) (sp / lim).toFloat() else 0f
    val col = when {
        engine.overLimit || ratio > 1.04f -> Bad
        ratio >= 0.9f -> androidx.compose.ui.graphics.lerp(Warn, Bad, ((ratio - 0.9f) / 0.14f).coerceIn(0f, 1f))
        else -> c.accent
    }
    val arcCol by androidx.compose.animation.animateColorAsState(col, androidx.compose.animation.core.tween(400), label = "ringCol")
    Box(contentAlignment = Alignment.Center) {
        if (show) Box(Modifier.size(104.dp).panel(10.dp, CircleShape, c.surface), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                val sw = 7.dp.toPx()
                val inset = sw / 2 + 4.dp.toPx()
                val tl = Offset(inset, inset)
                val sz = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                val cap = androidx.compose.ui.graphics.drawscope.Stroke(width = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawArc(c.tile, 135f, 270f, false, tl, sz, style = cap)
                if (frac > 0.005f) {
                    // a soft glow under the arc, then the arc itself
                    drawArc(arcCol.copy(alpha = 0.28f), 135f, 270f * frac, false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw * 2.4f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    drawArc(arcCol, 135f, 270f * frac, false, tl, sz, style = cap)
                }
                // a tick where the limit sits on the ring
                if (lim != null && lim > 0) {
                    val ang = Math.toRadians(135.0 + 270.0 * (lim / full).coerceIn(0.0, 1.0))
                    val r0 = sz.width / 2 - sw; val r1 = sz.width / 2 + sw
                    val cx = size.width / 2; val cy = size.height / 2
                    drawLine(c.text.copy(alpha = 0.7f), Offset(cx + (kotlin.math.cos(ang) * r0).toFloat(), cy + (kotlin.math.sin(ang) * r0).toFloat()),
                        Offset(cx + (kotlin.math.cos(ang) * r1).toFloat(), cy + (kotlin.math.sin(ang) * r1).toFloat()), 2.5.dp.toPx())
                }
            }
            // the number sits exactly in the middle of the ring; the unit goes in the gap at the bottom of the arc
            val num = "${Math.round(sp * k)}"
            Text(num, color = speedTint(engine) ?: c.text, fontSize = if (num.length >= 3) 27.sp else 33.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false), lineHeight = 33.sp,
                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both)))
            Text(if (engine.imperial) "mph" else "km/h", color = c.sub, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp))
        }
        // The limit sign goes on the speed's inner side, so it never runs off the screen: the left normally, the right when the
        // app is mirrored for left-hand drive (the speed is then on the left edge). Android Auto never mirrors: its speed is
        // always on the right.
        val signRight = engine.mirrored && !engine.isCar
        if (lim != null) Box(
            Modifier.align(if (!show) Alignment.Center else if (signRight) Alignment.TopEnd else Alignment.TopStart)
                .offset(x = if (!show) 0.dp else if (signRight) 30.dp else (-30).dp, y = if (show) (-18).dp else 0.dp)
        ) {
            SpeedLimitSign(engine, if (show) 50.dp else 70.dp)
        }
    }
}

@Composable
internal fun ImmTurnCard(
    engine: NavEngine, t: com.headunit.launcher.TurnInfo, compact: Boolean = false,
    /** Android Auto: no written lane advice under the lane arrows (the arrows already say it). */
    car: Boolean = false,
    /** Android Auto with the turn still far off: just one line (arrow, distance, road), so more of the map shows. */
    slim: Boolean = false,
    /** Extra row along the bottom of the card (Android Auto puts the hazards ahead there). */
    footer: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(28.dp)
    val frac = ((450.0 - t.dist) / 450.0).coerceIn(0.0, 1.0).toFloat()
    // living card: it grows a touch and starts to glow as the turn comes up, and pulses in the last 80 m
    val alive = engine.prefs.b(PreferenceKeys.LIVECARD, true)
    val near = alive && t.dist < 300.0
    val grow by androidx.compose.animation.core.animateFloatAsState(if (near) 1f + 0.035f * frac else 1f, androidx.compose.animation.core.tween(400), label = "grow")
    val glow by androidx.compose.animation.core.animateFloatAsState(if (near) (0.25f + 0.55f * frac) else 0f, androidx.compose.animation.core.tween(500), label = "glow")
    // only runs in the last 80 m, so the screen isn't redrawn constantly the rest of the time
    // read only while drawing, so the pulse repaints the card without recomposing it 60 times a second
    val pulse = rememberPulse(alive && t.dist < 80.0, 650)
    val glowCol = Color(0xFF7FFFE6)
    Column(
        Modifier.fillMaxWidth()
            .graphicsLayer { scaleX = grow; scaleY = grow; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f) }
            .shadow((if (compact) 5f else 12f + 14f * glow).dp, shape, ambientColor = glowCol, spotColor = glowCol)
            .clip(shape)
            .background(androidx.compose.ui.graphics.lerp(Teal, Color(0xFF14C9A6), 0.35f * glow))
            .border(1.5.dp, Color.White.copy(alpha = 0.18f + 0.25f * glow), shape)
            .drawWithContent {
                drawContent()
                val pv = pulse.value
                if (pv > 0.01f) {
                    drawRect(Color.White.copy(alpha = 0.10f * pv))
                    val sw = (1.5f + 1.5f * pv) * density
                    drawRoundRect(Color.White.copy(alpha = 0.45f * glow * pv), topLeft = Offset(sw / 2, sw / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                }
            }
            .animateContentSize()
            // tap for the list of every turn on the route
            .clickable { engine.itineraryOpen = true },
    ) {
        if (slim) Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            ManeuverArrow(t.maneuver, Geo.maneuverAngle(t.maneuver), Modifier.size(34.dp), roundaboutExit = t.exit, roundaboutClockwise = !engine.mirrored, exitAngle = roundaboutTurn(t.rbIn, t.rbOut))
            RollText(Geo.fmtDist(t.dist, engine.imperial), Color.White, 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
            Text(t.street, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
        } else {
        val thenIcon = t.thenManeuver != null && engine.prefs.s(PreferenceKeys.THEN_STYLE, "row") == "icon"
        Row(Modifier.padding(start = if (compact) 12.dp else 18.dp, end = if (compact) 12.dp else 16.dp, top = if (compact) 9.dp else 16.dp, bottom = if (compact) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ManeuverArrow(t.maneuver, Geo.maneuverAngle(t.maneuver), Modifier.size(if (compact) 42.dp else 60.dp), roundaboutExit = t.exit, roundaboutClockwise = !engine.mirrored, exitAngle = roundaboutTurn(t.rbIn, t.rbOut))
            Column(Modifier.padding(start = if (compact) 9.dp else 16.dp).weight(1f)) {
                RollText(Geo.fmtDist(t.dist, engine.imperial), Color.White, if (compact) 28.sp else 38.sp, fontWeight = FontWeight.Bold)
                val ukSign = engine.prefs.b(PreferenceKeys.UKSIGNS, true) && (t.sign.isNotBlank() && t.sign != t.street || t.tag.startsWith("Exit"))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (t.tag.isNotBlank() && !(ukSign && t.tag.startsWith("Exit"))) Text(t.tag, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 7.dp, vertical = 2.dp))
                    if (!ukSign && t.sign.isNotBlank() && t.sign != t.street) Text(t.sign, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 130.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1E8E55)).padding(horizontal = 7.dp, vertical = 2.dp))
                    Text(t.street, color = Color.White, fontSize = if (compact) 16.sp else 19.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // the turn straight after, as a small arrow (the "Then" line below is used instead when that style is chosen)
            if (thenIcon && t.thenManeuver != null) Column(Modifier.padding(start = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(TealDeep), contentAlignment = Alignment.Center) {
                    ManeuverArrow(t.thenManeuver, Geo.maneuverAngle(t.thenManeuver), Modifier.size(28.dp), roundaboutExit = t.thenExit, roundaboutClockwise = !engine.mirrored)
                }
                Text("then", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
            }
        }
        // Explicit slip-road / motorway-exit cue. Lane guidance alone can be ambiguous on
        // fast roads, so make the exit manoeuvre visible in the turn card as well.
        val maneuverUpper = t.maneuver.uppercase()
        val exitLike = t.exitNo.isNotBlank() ||
            t.tag.startsWith("Exit", ignoreCase = true) ||
            maneuverUpper.contains("EXIT") ||
            maneuverUpper.contains("OFF_RAMP") ||
            maneuverUpper == "TAKE_EXIT" ||
            maneuverUpper.startsWith("MOTORWAY_EXIT")
        // Only when the UK road sign below isn't shown: it already carries the exit, and the same thing twice on the card is clutter.
        val signShown = engine.prefs.b(PreferenceKeys.UKSIGNS, true) && (t.sign.isNotBlank() && t.sign != t.street || t.tag.startsWith("Exit"))
        if (exitLike && !signShown && !maneuverUpper.startsWith("ROUNDABOUT")) {
            val exitLabel = t.exitNo.ifBlank {
                t.tag.replaceFirst(Regex("^Exit\\s*", RegexOption.IGNORE_CASE), "").trim().takeIf { it.isNotBlank() }.orEmpty()
            }
            Row(
                Modifier.fillMaxWidth()
                    .padding(start = if (compact) 12.dp else 16.dp, end = if (compact) 12.dp else 16.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1554A8))
                    .padding(horizontal = 10.dp, vertical = if (compact) 5.dp else 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("↗", color = Color.White, fontSize = if (compact) 19.sp else 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (exitLabel.isNotBlank()) "EXIT  $exitLabel" else "EXIT / SLIP ROAD",
                    color = Color.White,
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 7.dp)
                )
                Spacer(Modifier.weight(1f))
                Text("↗", color = Color.White.copy(alpha = 0.9f), fontSize = if (compact) 18.sp else 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        // A proper UK direction sign: blue for motorways, green for primary roads, white for local roads.
        if (engine.prefs.b(PreferenceKeys.UKSIGNS, true) && (t.sign.isNotBlank() && t.sign != t.street || t.tag.startsWith("Exit")))
            UkSign(t.sign, t.tag.removePrefix("Exit ").takeIf { t.tag.startsWith("Exit") }.orEmpty(), t.street, Modifier.padding(start = if (compact) 12.dp else 16.dp, end = if (compact) 12.dp else 16.dp, bottom = if (compact) 5.dp else 12.dp))
        if (!compact && engine.prefs.b(PreferenceKeys.LANE_STRIP, true)) t.lanes?.let { ls ->
            // lane hero view: before a complex junction the lanes grow into a big picture with yours lit; simple ones keep the strip
            // (not in Android Auto: the big lane picture made the card grow to fill much of a car screen as soon as a junction came up)
            val hero = !car && engine.prefs.s(PreferenceKeys.LANE_STYLE, "hero") == "hero" && ls.lanes.size >= 2 && t.dist < 900.0
            if (hero) LaneHero(ls, t.maneuver, if (car) null else t.laneAdvice)
            else Box(Modifier.fillMaxWidth().background(TealDeep).padding(vertical = 4.dp), contentAlignment = Alignment.Center) { LaneRow(ls, t.maneuver, t.dist < 400.0) }
        }
        val heroShown = !car && engine.prefs.b(PreferenceKeys.LANE_STRIP, true) && engine.prefs.s(PreferenceKeys.LANE_STYLE, "hero") == "hero" && (t.lanes?.lanes?.size ?: 0) >= 2 && t.dist < 900.0
        if (!compact && !heroShown && !(car && t.lanes != null && engine.prefs.b(PreferenceKeys.LANE_STRIP, true))) t.laneAdvice?.let { Text(it, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().background(TealDeep).padding(horizontal = 18.dp, vertical = 6.dp)) }
        if (!compact && t.thenManeuver != null && !thenIcon) {
            Row(Modifier.fillMaxWidth().background(TealDeep).padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ManeuverArrow(t.thenManeuver, Geo.maneuverAngle(t.thenManeuver), Modifier.size(22.dp), roundaboutExit = t.thenExit, roundaboutClockwise = !engine.mirrored)
                Text(t.thenText, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
            }
        }
        } // (end of the full card)
        footer?.invoke()
        Box(Modifier.fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.18f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color.White.copy(alpha = 0.9f)))
        }
    }
}

@Composable
private fun ImmSearch(engine: NavEngine, q: String, onQ: (String) -> Unit, locked: Boolean, focus: FocusRequester, voice: () -> Unit) {
    val c = LocalVw.current
    val shape = RoundedCornerShape(30.dp)
    Box(Modifier.fillMaxWidth().height(60.dp).panel(10.dp, shape, c.surface)) {
        TextField(
            value = q, onValueChange = onQ, enabled = !locked,
            placeholder = { Text(if (locked) "Tap the mic to search while driving" else "Where to?", fontSize = if (locked) 15.sp else 18.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = c.sub) },
            trailingIcon = {
                if (q.isNotEmpty()) Icon(Icons.Rounded.Close, "Clear", tint = c.sub, modifier = Modifier.padding(end = 8.dp).clickable { onQ(""); engine.results = emptyList() })
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    // folds Saved & recent up under the bar, and back down again
                    val open = engine.prefs.b(PreferenceKeys.QUICKLIST, true)
                    Box(Modifier.padding(end = 4.dp).size(40.dp).clip(CircleShape).clickable { engine.prefs.put(PreferenceKeys.QUICKLIST, !open) }, contentAlignment = Alignment.Center) {
                        Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, if (open) "Hide saved and recent" else "Show saved and recent", tint = c.sub, modifier = Modifier.size(26.dp))
                    }
                    Box(Modifier.padding(end = 8.dp).size(44.dp).clip(CircleShape).background(c.accent).pressClick { voice() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Mic, "Voice search", tint = Color.White, modifier = Modifier.size(23.dp))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { engine.search(q) }),
            shape = shape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, disabledContainerColor = c.surface,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                focusedTextColor = c.text, unfocusedTextColor = c.text, disabledTextColor = c.sub,
                focusedPlaceholderColor = c.sub, unfocusedPlaceholderColor = c.sub, disabledPlaceholderColor = c.sub,
                cursorColor = c.accent,
            ),
            modifier = Modifier.fillMaxSize().focusRequester(focus),
        )
    }
}

@Composable
private fun ImmResults(engine: NavEngine, onPicked: () -> Unit) {
    val c = LocalVw.current
    val shape = RoundedCornerShape(26.dp)
    val here = engine.fix?.pos
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).panel(10.dp, shape, c.surface).padding(vertical = 6.dp)) {
        items(engine.results) { p ->
            Row(Modifier.fillMaxWidth().clickable { onPicked(); engine.pickPlace(p) }.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(c.tile), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Place, null, tint = Color(0xFFEA4335), modifier = Modifier.size(22.dp))
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(p.name, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p.sub, color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (here != null) Text(Geo.fmtDist(Geo.dist(here, p.pos), engine.imperial), color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
internal fun ImmChip(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    val c = LocalVw.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.height(46.dp).panel(6.dp, shape, c.surface).pressClick(onClick).padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) }
        Text(label, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Under the search bar: Home and Work as two tiles with the drive time, then five round buttons to find things nearby. */
@Composable
private fun ImmChips(engine: NavEngine, focusSearch: () -> Unit) {
    val home = engine.places.slot("home")
    val work = engine.places.slot("work")
    // the drive times: the engine throttles this itself (online only, every 10 minutes or after moving 3 km), so a minute's tick is cheap
    LaunchedEffect(Unit) { while (true) { engine.refreshSlotTimes(); delay(60_000) } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaceTile(engine, Modifier.weight(1f), Icons.Rounded.Home, Color(0xFF4C8DFF), "Home", home, engine.slotMins["home"]) {
                if (home != null) engine.startRoute(home) else { engine.pendingSave = "home"; engine.toast("Search for your home address", 3500); focusSearch() }
            }
            PlaceTile(engine, Modifier.weight(1f), Icons.Rounded.Work, Color(0xFFF29900), "Work", work, engine.slotMins["work"]) {
                if (work != null) engine.startRoute(work) else { engine.pendingSave = "work"; engine.toast("Search for your work address", 3500); focusSearch() }
            }
        }
        // things to find nearby: always all five on show, no scrolling sideways to find them
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FindButton(Icons.Rounded.LocalGasStation, Color(0xFFEA4335), "Fuel nearby") { engine.loadNearby("fuel", false) }
            FindButton(Icons.Rounded.EvStation, Color(0xFF34A853), "Charging nearby") { engine.loadNearby("charging", false) }
            FindButton(Icons.Rounded.Restaurant, Color(0xFFF29900), "Food nearby") { engine.loadNearby("food", false) }
            FindButton(Icons.Rounded.LocalCafe, Color(0xFFA56BFF), "Coffee nearby") { engine.loadNearby("coffee", false) }
            FindButton(Icons.Rounded.LocalParking, Color(0xFF4C8DFF), "Parking nearby") { engine.loadNearby("parking", false) }
        }
    }
}

@Composable
private fun PlaceTile(engine: NavEngine, modifier: Modifier, icon: ImageVector, tint: Color, label: String, place: com.headunit.launcher.Place?, mins: Int?, onClick: () -> Unit) {
    val c = LocalVw.current
    val here = engine.fix?.pos
    val sub = when {
        place == null -> "Tap to set"
        mins == 0 -> "You're here"
        mins != null && mins > 0 -> Geo.fmtDur(mins * 60.0)
        here != null -> Geo.fmtDist(Geo.dist(here, place.pos), engine.imperial)
        else -> place.sub
    }
    Row(modifier.height(58.dp).panel(6.dp, RoundedCornerShape(20.dp), c.surface).pressClick(onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(if (place == null) "Set ${label.lowercase()}" else label, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FindButton(icon: ImageVector, tint: Color, desc: String, onClick: () -> Unit) {
    val c = LocalVw.current
    Box(Modifier.size(50.dp).panel(6.dp, CircleShape, c.surface).pressClick(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** A place sent from your phone: starts by itself after 8 seconds unless you tap "Not now". */
@Composable
private fun PhoneDestCard(engine: NavEngine, p: com.headunit.launcher.Place) {
    val c = LocalVw.current
    var left by remember(p) { mutableStateOf(8) }
    LaunchedEffect(p) { while (left > 0) { delay(1000); left-- }; engine.takePhoneDest(true) }
    Row(Modifier.fillMaxWidth().panel(10.dp, RoundedCornerShape(22.dp), c.surface).padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PhoneAndroid, null, tint = c.accent, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("From your phone: ${p.name}", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Starting in $left s", color = c.sub, fontSize = 13.sp)
        }
        ImmPill("Go", { engine.takePhoneDest(true) }, filled = true)
        ImmPill("Not now", { engine.takePhoneDest(false) })
    }
}

/** Where you parked (saved on the phone when Android Auto disconnects): walk there, or clear it. Shown for 2 days. */
@Composable
private fun ParkedCard(engine: NavEngine) {
    val c = LocalVw.current
    val raw = engine.prefs.s(PreferenceKeys.PARKED_SPOT, "")
    val parts = raw.split(',')
    val spot = parts.getOrNull(0)?.toDoubleOrNull()?.let { la -> parts.getOrNull(1)?.toDoubleOrNull()?.let { lo -> LL(la, lo) } } ?: return
    val at = parts.getOrNull(2)?.toLongOrNull() ?: return
    val ageMin = ((System.currentTimeMillis() - at) / 60_000L).coerceAtLeast(0L)
    if (ageMin > 48 * 60) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val here = engine.fix?.pos
    val ago = when { ageMin < 1 -> "just now"; ageMin < 60 -> "$ageMin min ago"; ageMin < 24 * 60 -> "${ageMin / 60} h ago"; else -> "yesterday" }
    Row(Modifier.fillMaxWidth().panel(10.dp, RoundedCornerShape(22.dp), c.surface).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.DirectionsCar, null, tint = c.accent, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Your car", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Parked $ago" + (if (here != null) " · " + Geo.fmtDist(Geo.dist(here, spot), engine.imperial) else ""), color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // walking directions in the phone's maps app (Horizon Nav plans driving routes); just the spot if there's none
        ImmPill("Walk", {
            val walk = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(String.format(java.util.Locale.US, "google.navigation:q=%.6f,%.6f&mode=w", spot.lat, spot.lon)))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            val geo = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(String.format(java.util.Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f(Your car)", spot.lat, spot.lon, spot.lat, spot.lon)))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            try { ctx.startActivity(walk) } catch (e: Exception) { try { ctx.startActivity(geo) } catch (x: Exception) { engine.toast("No walking maps app on this phone", 3000) } }
        }, filled = true)
        Box(Modifier.size(40.dp).clip(CircleShape).clickable { engine.prefs.put(PreferenceKeys.PARKED_SPOT, "") }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, "Forget where you parked", tint = c.sub, modifier = Modifier.size(22.dp))
        }
    }
}

/** Resume a trip, a suggested trip, and saved & recent places. */
@Composable
private fun ImmQuick(engine: NavEngine, showSaved: Boolean = true) {
    val c = LocalVw.current
    val rawCustom = engine.prefs.s(PreferenceKeys.PLACES_CUSTOM, "")
    val rawFav = engine.prefs.s(PreferenceKeys.PLACES_FAVOURITES, "")
    val rawRecent = engine.prefs.s(PreferenceKeys.PLACES_RECENT, "")
    // (kind 0 = one of your own labelled places, 1 = a favourite, 2 = recent; the label is the name shown)
    val saved: List<Triple<com.headunit.launcher.Place, Int, String>> = remember(rawCustom, rawFav, rawRecent) {
        val mine = engine.places.customs()
        val favs = engine.places.favourites().filter { f -> mine.none { Geo.dist(it.place.pos, f.pos) < 60 } }
        val recents = engine.places.recents().filter { r -> favs.none { Geo.dist(it.pos, r.pos) < 60 } && mine.none { Geo.dist(it.place.pos, r.pos) < 60 } }
        mine.map { Triple(it.place, 0, it.label) } + favs.map { Triple(it, 1, it.name) } + recents.take(4).map { Triple(it, 2, it.name) }
    }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    if (adding) AddPlaceDialog(engine, null) { adding = false }
    val here = engine.fix?.pos
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ParkedCard(engine)
        engine.resume?.let { rp ->
            Row(
                Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(TealDeep).clickable { engine.startRoute(rp) }.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Route, null, tint = Color.White, modifier = Modifier.size(26.dp))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Resume trip to ${rp.name}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("You hadn't arrived when the app closed", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                Icon(Icons.Rounded.Close, "Dismiss", tint = Color.White, modifier = Modifier.padding(start = 8.dp).size(24.dp).clickable { engine.resume = null; engine.prefs.put(PreferenceKeys.RESUME, "{}") })
            }
        }
        // predictive destination with the live journey time (checked again every few minutes while the map is idle)
        LaunchedEffect(engine.fix != null) { while (true) { engine.refreshPrediction(); delay(60_000) } }
        val pr = engine.prediction?.takeIf { engine.prefs.b(PreferenceKeys.PREDICT, true) }
        if (pr != null) Row(
            Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Teal).clickable { engine.startRoute(pr.place) }.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(when (pr.title) { "Home" -> Icons.Rounded.Home; "Work" -> Icons.Rounded.Work; else -> Icons.Rounded.History }, null, tint = Color.White, modifier = Modifier.size(26.dp))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(listOfNotNull(pr.title, if (pr.mins >= 0) Geo.fmtDur(pr.mins * 60.0) else null, pr.note.ifBlank { null }).joinToString(" · "),
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val arrive = if (pr.mins >= 0) "Arrive about " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(java.util.Date(System.currentTimeMillis() + pr.mins * 60_000L)) else pr.place.sub
                Text(if (pr.delayMin >= 3) "$arrive · +${pr.delayMin} min delays" else arrive, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Go", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        val suggestion = remember { engine.habits.suggest(System.currentTimeMillis()) }
        if (pr == null && suggestion != null) Row(
            Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Teal).clickable { engine.startRoute(suggestion) }.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.History, null, tint = Color.White, modifier = Modifier.size(26.dp))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("Going to ${suggestion.name}?", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (suggestion.sub.isNotBlank()) Text(suggestion.sub, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Go", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        // three places on show to keep the map clear; "All" opens the rest (it used to be a list up to five tall)
        AnimatedVisibility(showSaved && engine.prefs.b(PreferenceKeys.QUICKLIST, true),
            enter = fadeIn(tween(200)) + androidx.compose.animation.expandVertically(tween(240)), exit = fadeOut(tween(140)) + androidx.compose.animation.shrinkVertically(tween(200))) {
        Column(Modifier.fillMaxWidth().panel(10.dp, RoundedCornerShape(26.dp), c.surface).padding(vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("SAVED & RECENT", color = c.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
                if (saved.size > 3) Text(if (showAll) "Less" else "All ${saved.size}", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { showAll = !showAll }.padding(horizontal = 10.dp, vertical = 8.dp))
                Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable { adding = true }.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, null, tint = c.accent, modifier = Modifier.size(18.dp))
                    Text("Add", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp))
                }
            }
            if (saved.isEmpty()) Text("Places you save or drive to show up here", color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 8.dp))
            LazyColumn(Modifier.heightIn(max = 250.dp)) {
                items(if (showAll) saved else saved.take(3)) { (p, kind, label) ->
                    val fav = kind != 2
                    // swipe a place sideways to take it off the list (a favourite is unstarred, a recent one forgotten, your own place removed)
                    var dx by remember { mutableStateOf(0f) }
                    var w by remember { mutableStateOf(1) }
                    val shown by androidx.compose.animation.core.animateFloatAsState(dx, label = "swipe")
                    Row(Modifier.fillMaxWidth().onSizeChanged { w = it.width }
                        .pointerInput(p, kind) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (kotlin.math.abs(dx) > w * 0.35f) when (kind) {
                                        0 -> { engine.places.removeCustom(label); engine.toast("Removed $label", 2000) }
                                        1 -> { engine.places.toggleFavourite(p); engine.toast("Removed from favourites", 2000) }
                                        else -> { engine.places.removeRecent(p); engine.toast("Removed from recent places", 2000) }
                                    }
                                    dx = 0f
                                },
                                onDragCancel = { dx = 0f },
                            ) { change, amount -> change.consume(); dx += amount }
                        }
                        .graphicsLayer { translationX = shown; alpha = 1f - (kotlin.math.abs(shown) / w.toFloat()).coerceIn(0f, 0.7f) }
                        .clickable { engine.startRoute(p) }.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(c.tile), contentAlignment = Alignment.Center) {
                            Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.History, null, tint = if (fav) Warn else c.sub, modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(label, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (p.sub.isNotBlank()) Text(p.sub, color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (here != null) Text(Geo.fmtDist(Geo.dist(here, p.pos), engine.imperial), color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        }
    }
}

/**
 * Every turn still ahead, opened by tapping the turn card: the arrow, what to do and onto which road, how far away it is and
 * roughly when you'll get there. Tapping a turn shows it on the map and offers to avoid that road. It opens over the trip card,
 * closes by itself 5 seconds after the last touch, and the map goes back to following you.
 */
@Composable
private fun ItineraryCard(engine: NavEngine) {
    @Suppress("UNUSED_VARIABLE") val tick = engine.turn   // read so the list moves along as you drive
    val rows = engine.itinerary()
    val shape = RoundedCornerShape(28.dp)
    var picked by remember { mutableStateOf(-1) }
    var touched by remember { mutableStateOf(0) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // folds away 5 seconds after the last touch (scrolling the list counts)
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) touched++ }
    LaunchedEffect(touched, listState.isScrollInProgress) { if (!listState.isScrollInProgress) { delay(5_000); engine.closeItinerary() } }
    LaunchedEffect(rows.isEmpty()) { if (rows.isEmpty()) engine.closeItinerary() }
    Column(Modifier.fillMaxWidth().shadow(12.dp, shape).clip(shape).background(Teal).border(1.5.dp, Color.White.copy(alpha = 0.18f), shape)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your route", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                val e = engine.eta
                Text("${rows.size} ${if (rows.size == 1) "step" else "steps"}" + (if (e != null) " · " + Geo.fmtDist(e.dist, engine.imperial) + " · arrive " + e.arrive else ""),
                    color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(TealDeep).clickable { engine.closeItinerary() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "Close", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).background(TealDeep), state = listState) {
            items(rows, key = { it.index }) { row ->
                val sel = picked == row.index
                Column(Modifier.fillMaxWidth().background(if (sel) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable { touched++; picked = row.index; engine.previewTurn(row) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ManeuverArrow(row.maneuver, Geo.maneuverAngle(row.maneuver), Modifier.size(30.dp), roundaboutExit = row.exit, roundaboutClockwise = !engine.mirrored)
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(row.text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(row.tag.ifBlank { null }, "about " + row.arrive).joinToString(" · "), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1)
                        }
                        Text(Geo.fmtDist(row.dist, engine.imperial), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
                    }
                    if (sel && row.road.isNotBlank()) Row(Modifier.padding(start = 44.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Avoid ${row.road}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f)).clickable { engine.avoidTurnRoad(row) }.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
            }
        }
    }
}
