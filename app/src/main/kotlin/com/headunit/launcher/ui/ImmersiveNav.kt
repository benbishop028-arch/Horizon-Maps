package com.headunit.launcher.ui

import com.headunit.launcher.L

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.AltRoute
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
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalShipping
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
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.CloudDownload
import com.headunit.launcher.PreferenceKeys

private val Teal = GmGreen
private val TealDeep = GmGreenDeep

/** The map screen. It looks the same whichever map colours are chosen; it takes the shared soft palette. */
@Composable
fun ImmersiveNav(engine: NavEngine, onSettings: () -> Unit) {
    val base = LocalVw.current
    val glass = engine.prefs.b(PreferenceKeys.GLASS, true)
    val v = remember(base, glass) { navVw(base).let { if (glass) glassVw(it) else it } }
    CompositionLocalProvider(LocalVw provides v) { FixedSides { MapScreen(engine, onSettings) } }
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
    var quickReportOpen by remember { mutableStateOf(false) }
    // voice: "take me to…", "share my ETA", "avoid this road", "mute"… anything else is searched for
    val voice = rememberVoiceInput(L.t("Say a destination or a command")) { said ->
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
        if (q.length >= 3) { delay(500); engine.search(q, typed = true) } else if (q.isEmpty()) engine.results = emptyList()
    }
    // text shared from another app goes into the search box
    LaunchedEffect(engine.externalQuery) { engine.externalQuery?.let { q = it; engine.externalQuery = null } }
    BackHandler(enabled = sheet) { sheet = false }
    BackHandler(enabled = quickReportOpen) { quickReportOpen = false }
    val routing = engine.route != null && !engine.previewing
    val previewing = engine.previewing
    val simple by remember { derivedStateOf { engine.driving && engine.prefs.b(PreferenceKeys.DRIVESIMPLE, true) } }
    // the Clean view button (#358): while on a route, 1 hides the extra cards and the media bar, 2 leaves just the map and the buttons
    var cleanView by remember { mutableStateOf(engine.prefs.i(PreferenceKeys.CLEAN_VIEW, 0).coerceIn(0, 2)) }
    val hudMin = routing && !previewing && cleanView >= 1
    val hudOff = routing && !previewing && cleanView == 2

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        // portrait (or any narrow window): cards use the full width, the round buttons stack down the right-hand side,
        // and your speed and the re-centre button sit just above the trip card instead of beside it
        val narrow = maxWidth < 720.dp
        val dens = androidx.compose.ui.platform.LocalDensity.current
        var tripPx by remember { mutableStateOf(0) }
        // the trip card's and speedometer's widths, and the media bar's height (for fitting the media bar between them)
        var tripWPx by remember { mutableStateOf(0) }
        var speedWPx by remember { mutableStateOf(0) }
        var mediaHPx by remember { mutableStateOf(0) }
        // where the speedometer starts (in pixels), so zoom only moves beside the panel when it would really touch it
        var speedTopPx by remember { mutableStateOf(-1) }
        // the map buttons' width down the speed's side (for the speed display set to Middle or Top, which sits just inside them)
        var buttonsWPx by remember { mutableStateOf(0) }
        // Settings > Screen & sizes > Speed position: bottom (as it always was), middle or top of the side away from the cards.
        // Top falls back to middle on narrow screens, where the turn card spans the top
        val speedPos = engine.prefs.s(PreferenceKeys.SPEED_POS, "bottom").let { if (it == "top" && narrow) "middle" else it }
            .let { if (!narrow && (engine.prefs.s(PreferenceKeys.SPEED_SIDE, "auto") != "auto") && ((engine.prefs.s(PreferenceKeys.SPEED_SIDE, "auto") == "left") != engine.layoutMirrored)) "bottom" else it }
        val tripH = with(dens) { tripPx.toDp() }
        val screenH = maxHeight
        // Other routes on a phone held upright (smikkelvriend): the routes come up as a panel along the bottom, the turn card goes
        // to one line, and the speed, Re-centre and trip card step aside, so the map between shows the routes clearly
        val altSheet = narrow && !previewing && engine.altListOpen && engine.route != null
        var altSheetPx by remember { mutableStateOf(0) }
        // the same on a phone held sideways (a short, wide screen, fahadfadi): the routes panel stays beside the buttons but the
        // turn card goes to one line on the other side and the rest steps aside, so the panel no longer covers the turn card
        val shortWide = !narrow && maxHeight < 430.dp
        val altSide = shortWide && !previewing && engine.altListOpen && engine.route != null
        // either way: the map kept clear for the routes
        val altFocus = altSheet || altSide
        var altSideL by remember { mutableStateOf(0f) }
        var altSideR by remember { mutableStateOf(0f) }
        BackHandler(enabled = altFocus && !sheet) { engine.closeAlternatives() }
        // (the routes panel counts like the trip card, which steps aside for it: your arrow is kept clear of it)
        LaunchedEffect(narrow, tripPx, screenH, altSheet, altSheetPx) {
            val coverPx = if (altSheet) altSheetPx else tripPx
            engine.bottomCover = if (narrow && coverPx > 0) (with(dens) { coverPx.toDp() } / screenH).coerceIn(0f, 0.5f).toDouble() else 0.0
        }
        // the routes are fitted into the map above the panel (again once the panel has its full height, as the routes fill it)
        LaunchedEffect(altSheet, altSheetPx, engine.liveAlts.size) {
            engine.altSheetCover = if (altSheet && altSheetPx > 0) (with(dens) { altSheetPx.toDp() } / screenH).toDouble().coerceIn(0.0, 0.6) else 0.0
            if (altSheet && altSheetPx > 0 && engine.liveAlts.isNotEmpty()) { delay(150); engine.refitAlternatives() }
        }
        // sideways: the routes are fitted into the map beside the panel (how much of the width it covers on each side)
        LaunchedEffect(altSide, altSideL, altSideR, engine.liveAlts.size) {
            engine.altSideCover = if (altSide && altSideL + altSideR > 0f) altSideL.toDouble() to altSideR.toDouble() else null
            if (altSide && altSideL + altSideR > 0f && engine.liveAlts.isNotEmpty()) { delay(150); engine.refitAlternatives() }
        }
        // left-hand drive: everything swaps sides (cards on the right, round buttons on the left). Settings > Screen and sizes >
        // Card positions can put the turn card (and with it the buttons, on the other side) on either side whatever you drive
        val mir = engine.layoutMirrored
        // where the trip card and speedometer go (Card positions): "left", "right" (or "centre" for the trip card), on a wide screen
        val tripAt = if (narrow) "" else engine.prefs.s(PreferenceKeys.TRIP_POS, "auto").let { if (it == "auto") (if (mir) "right" else "left") else it }
        val speedAt = engine.prefs.s(PreferenceKeys.SPEED_SIDE, "auto").let { if (narrow || it == "auto") (if (mir) "left" else "right") else it }
        // the speed on the cards' side can only be at the bottom (the turn card has the top)
        val speedOnCardSide = (speedAt == "left") != mir
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
        // the "all locations" panel sits in the same corner and lets the map show through, so the search and its results step aside while it's open
        // Settings › Places & trips › Search as a button (GitHub #154): with no route, just a round search button in the corner, so
        // the map (and your arrow, in the middle of it) has the whole screen; tapping it brings the search bar and its buttons back.
        // They fold away again 20 seconds after you last searched, or when a route starts
        val searchBtn = engine.prefs.b(PreferenceKeys.SEARCH_BUTTON, false)
        var searchOpen by remember { mutableStateOf(false) }
        val searchBusy = q.isNotEmpty() || engine.results.isNotEmpty() || engine.nearbyKind != null || engine.phoneDest != null || engine.shownTrack != null
        val compact = searchBtn && !routing && !previewing && !searchOpen && !searchBusy
        // the portrait layout with no route (Settings › Screen & sizes › New portrait layout): only the search bar at the top, with
        // Settings in it, and your places, music and notices in a sheet along the bottom, instead of a column of cards down from the
        // search bar and the buttons beside it (reports of a cluttered upright screen)
        val sheetMode = narrow && !routing && !previewing && !compact && engine.prefs.b(PreferenceKeys.PORTRAIT_SHEET, true)
        androidx.compose.runtime.SideEffect { if (engine.portraitSheetOn != sheetMode) engine.portraitSheetOn = sheetMode }
        // the portrait layout on a route (the same setting): the trip card is a sheet along the bottom, hazards are lines of the turn
        // card, warnings pop up under it, only sound and other routes stay on the map, and the speed sits bottom left
        val routeSheet = narrow && routing && engine.prefs.b(PreferenceKeys.PORTRAIT_SHEET, true)
        androidx.compose.runtime.SideEffect { if (engine.alertsUnderTurn != routeSheet) engine.alertsUnderTurn = routeSheet }
        // (out of the way while you search, look at a place, or browse places of a kind nearby)
        val homeSheetUp = sheetMode && q.isEmpty() && engine.results.isEmpty() && engine.here == null && engine.nearbyKind == null && engine.chain == null && !engine.chainBusy
        LaunchedEffect(searchOpen, searchBusy) { if (searchOpen && !searchBusy) { delay(20_000); searchOpen = false } }
        LaunchedEffect(routing) { if (routing) { searchOpen = false; engine.homeTucked = false } }
        LaunchedEffect(searchOpen) { if (searchOpen && !typingLocked) { delay(350); try { focus.requestFocus() } catch (_: Exception) {} } }
        // a phone held upright, on a route: the turn card takes the whole width and the map buttons go under it, instead of being
        // squeezed in beside it (the cards that come and go under the turn card still leave room for the buttons)
        var turnBottomPx by remember { mutableStateOf(0f) }
        var turnExtraMax by remember { mutableStateOf(3) }
        var boxTopPx by remember { mutableStateOf(0f) }
        var railHPx by remember { mutableStateOf(0) }
        val railBelow = narrow && routing && !hudOff && engine.turn != null && !engine.itineraryOpen && engine.results.isEmpty() && turnBottomPx > 0f
        Box(Modifier.matchParentSize().onGloballyPositioned { boxTopPx = it.positionInRoot().y })
        // Settings › sizes › Turn card width (ZarulOfficial #317): the card size slider scales what's in the cards, this how wide they are
        val turnWK = engine.prefs.i(PreferenceKeys.TURN_WIDTH, 100).coerceIn(60, 130) / 100f
        if (engine.chain == null && !engine.chainBusy) Column(Modifier.align(side(Alignment.TopStart)).padding(16.dp).then(if (compact) Modifier else if (narrow) Modifier.fillMaxWidth(turnWK.coerceAtMost(1f)).sides(end = if (railBelow) 0.dp else 52.dp * mainButtonScale + 10.dp) else Modifier.width(380.dp * turnWK))
            .onGloballyPositioned { report(0, it.boundsInParent()) }
            // the turn list opens over the trip card below it
            .zIndex(if (engine.itineraryOpen) 2f else 0f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // every block slides and fades in and out instead of popping, so the screen changes feel like one piece
            if (compact) CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density * mainButtonScale, LocalDensity.current.fontScale)) {
                ImmIcon(Icons.Rounded.Search, { searchOpen = true }, desc = L.t("Search"))
            }
            AnimatedVisibility(!routing && !previewing && !compact, enter = fadeIn(tween(220)) + androidx.compose.animation.expandVertically(tween(260)), exit = fadeOut(tween(160)) + androidx.compose.animation.shrinkVertically(tween(220))) {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ImmSearch(engine, q, { q = it }, typingLocked, focus, voice, showFold = !sheetMode)
                // (moving the map tucks everything under the search bar away, apart from search results: [NavEngine.homeTucked])
                val tucked = engine.homeTucked
                if (!tucked && !sheetMode) engine.phoneDest?.let { pd -> PhoneDestCard(engine, pd) }
                // a GPX track you're looking at (Settings › Places & trips › GPX tracks › Show)
                if (!tucked) engine.shownTrack?.let { t -> TrackCard(engine, t) }
                val showResults = engine.results.isNotEmpty()
                AnimatedVisibility(showResults, enter = fadeIn(tween(180)) + androidx.compose.animation.expandVertically(tween(240)), exit = fadeOut(tween(120)) + androidx.compose.animation.shrinkVertically(tween(180))) {
                    ImmResults(engine, q) { q = "" }
                }
                AnimatedVisibility(!sheetMode && !showResults && q.isEmpty() && engine.nearbyKind == null && !tucked, enter = fadeIn(tween(220)) + androidx.compose.animation.expandVertically(tween(260)), exit = fadeOut(tween(140)) + androidx.compose.animation.shrinkVertically(tween(200))) {
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
                        // (your saved places are also in the row with Home and Work, so they're still a tap away once this folds, #143)
                        ImmQuick(engine, showSaved = !cruising)
                    }
                }
              }
            }
            if (routing && !hudOff) {
                if (engine.results.isNotEmpty()) ImmResults(engine, q) { q = "" }
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
                                    // the lines along its bottom (average speed check, services, parking...: they were cards of
                                    // their own under it), as many as keep it clear of the trip card at the bottom of the screen
                                    val freePx = boxTopPx + boxH - tripPx - with(dens) { 26.dp.toPx() } - turnBottomPx
                                    // (a line is given back only when there's room for what it took, as on Android Auto, Dotancom #324)
                                    val extraWhat = "${engine.parkingNear.size}|${engine.avgInfo != null}|${engine.lorryWarn != null}|${engine.servicesAhead.size}|${engine.advice != null}"
                                    val grownBottom = remember(extraWhat) { floatArrayOf(0f) }
                                    androidx.compose.runtime.SideEffect {
                                        val step = with(dens) { 48.dp.toPx() }
                                        if (turnBottomPx <= 0f) return@SideEffect
                                        val need = if (grownBottom[0] > turnBottomPx) grownBottom[0] - turnBottomPx + with(dens) { 8.dp.toPx() } else step
                                        if (freePx < 0f && turnExtraMax > 0) { grownBottom[0] = turnBottomPx; turnExtraMax -= 1 }
                                        else if (freePx > need && turnExtraMax < 3) turnExtraMax += 1
                                    }
                                    // (portrait on a route: one slim line while the turn is far off, about 2 km, and the full card as it
                                    // comes up, a little either side so it can't flicker, as on Android Auto)
                                    val slimHold = remember { booleanArrayOf(false) }
                                    slimHold[0] = if (t.dist > 2100.0) true else if (t.dist < 1900.0) false else slimHold[0]
                                    // (one under the other: inside the animated block they were drawn on top of each other, so a warning
                                    // "under" the turn card covered its top, the turn and the distance)
                                    Column {
                                    Column(Modifier.onGloballyPositioned { turnBottomPx = it.boundsInRoot().bottom }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ImmTurnCard(engine, t, slim = altFocus || (routeSheet && slimHold[0]), footer = {
                                            if (routeSheet && !altFocus) RouteHazardLines(engine)
                                            if (!altFocus && !hudMin) TurnCardExtras(engine, turnExtraMax, parking = false)
                                        })
                                        // the next services: a card of its own under the turn card again, not joined to it
                                        if (!altFocus && !hudMin) ServicesCard(engine)
                                        // car parks near the end: their own card again (the portrait sheet has them in the sheet)
                                        if (!altFocus && !routeSheet && !hudMin) ParkingCard(engine)
                                    }
                                    // warnings (a camera, a limit, rain...) pop up just under the turn card, joined to it
                                    if (routeSheet) {
                                        val curA = engine.alert
                                        val lastA = remember { mutableStateOf(curA) }
                                        if (curA != null) lastA.value = curA
                                        AnimatedVisibility(curA != null, enter = fadeIn(tween(160)) + androidx.compose.animation.expandVertically(tween(200)),
                                            exit = fadeOut(tween(160)) + androidx.compose.animation.shrinkVertically(tween(200))) {
                                            lastA.value?.let { Box(Modifier.padding(top = 6.dp)) { AlertBanner(it) } }
                                        }
                                    }
                                    }
                                }
                            }
                        }
                    }
                    // (not while choosing a route on a phone: the map is kept clear for the routes)
                    if (!altFocus) Column(if (railBelow) Modifier.sides(end = 52.dp * mainButtonScale + 10.dp) else Modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // (the advice, a lorry's limit ahead, the average speed check, services and parking are lines of the
                        // turn card now)
                        // (the portrait route layout: the lanes are in the turn card's own strip, no separate picture under it)
                        if (!routeSheet && !hudMin) JunctionCloseup(engine)
                    }
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
            // (the portrait route layout: compass, report and map options are in the trip sheet, only sound and other routes stay here)
            // Settings > Map buttons: which of these show (Map options always does, it's the way to everything else) (#335)
            val railHidden = engine.prefs.s(PreferenceKeys.RAIL_HIDDEN, "overview").split(",")
            if (!routeSheet) {
            if ("compass" !in railHidden) railItems.add(@Composable { ImmCompass(engine) })
            // #275: keep one-tap reports on the map itself, rather than making drivers open Map options first.
            if ("report" !in railHidden) railItems.add(@Composable { ImmIcon(Icons.Rounded.Campaign, { sheet = false; quickReportOpen = !quickReportOpen }, active = quickReportOpen, desc = L.t("Report")) })
            railItems.add(@Composable { ImmIcon(Icons.Rounded.Layers, { quickReportOpen = false; sheet = !sheet }, active = sheet, desc = L.t("Map options")) })
            }
            // (the portrait route layout: map options stays, above sound)
            if (routeSheet) railItems.add(@Composable { ImmIcon(Icons.Rounded.Layers, { quickReportOpen = false; sheet = !sheet }, active = sheet, desc = L.t("Map options")) })
            // navigating: sound in one big button with the others (voice, alerts only, mute), as in Google Maps. It used to be a small
            // button on the trip card, close to the others and hard to hit while driving (GitHub, magnazan)
            if (routing && "sound" !in railHidden) railItems.add(@Composable {
                ImmCircle({ engine.cycleSound() }, 52.dp) {
                    Icon(soundIcon(engine.soundMode), L.t("Sound"), tint = if (engine.soundMode == 2) Bad else c.text, modifier = Modifier.size(24.dp))
                }
            })
            // navigating: other ways to go, any time (as in Google Maps): shown in grey on the map with how they compare, tap one to switch
            if (routing && "routes" !in railHidden) railItems.add(@Composable { ImmIcon(Icons.Rounded.AltRoute, { engine.findAlternatives() }, desc = L.t("Other routes")) })
            // Clean view (#358): full, then minimal, then the map only; remembered for the next trip
            if (routing && !previewing && "clean" !in railHidden) railItems.add(@Composable {
                val label = when (cleanView) { 0 -> L.t("Full view"); 1 -> L.t("Minimal view"); else -> L.t("Map only") }
                ImmIcon(if (cleanView == 0) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, {
                    cleanView = (cleanView + 1) % 3
                    engine.prefs.put(PreferenceKeys.CLEAN_VIEW, cleanView)
                    engine.toast(when (cleanView) { 0 -> L.t("Full view"); 1 -> L.t("Minimal view"); else -> L.t("Map only") }, 1500)
                }, active = cleanView != 0, desc = label)
            })
            // the whole route on screen for a few seconds, then back to following you (off unless chosen in Settings > Map buttons)
            if (routing && !previewing && "overview" !in railHidden) railItems.add(@Composable { ImmIcon(Icons.Rounded.Route, { engine.overview() }, desc = L.t("Overview")) })
            // the demo drive: play to start it; while it plays, its speed (tap for 1x / 2x / 4x), skip to the next turn, and stop
            if (engine.simMode != 0) {
                railItems.add(@Composable {
                    ImmCircle({ engine.cycleSimSpeed() }, 52.dp, true) { Text("${engine.simMode}×", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                })
                railItems.add(@Composable { ImmIcon(Icons.Rounded.SkipNext, { engine.skipTurn() }, desc = L.t("Skip to the next turn")) })
                railItems.add(@Composable { ImmIcon(Icons.Rounded.Stop, { engine.stopSim() }, desc = L.t("End demo")) })
            } else if (!simple && engine.prefs.b(PreferenceKeys.DEMO, false)) railItems.add(@Composable { ImmIcon(Icons.Rounded.PlayArrow, { engine.cycleSim() }, desc = L.t("Demo drive")) })
            // zoom: hidden while the map is following you on the move (it zooms itself); back as soon as you touch or zoom the map, or
            // whenever you're stopped. (A little hysteresis on the speed, so it doesn't flicker at walking pace.)
            var stopped by remember { mutableStateOf(true) }
            val spNow = engine.fix?.speed ?: 0.0
            androidx.compose.runtime.SideEffect { if (spNow > 3.0) stopped = false else if (spNow < 1.0) stopped = true }
            val showZoom = stopped || !engine.following || kotlin.math.abs(engine.zoomOffset) > 0.01
            val showSettings = !routing && !sheetMode
            val unit = 52.dp * mainButtonScale
            // the top of the speedometer (measured; a fair guess until it has been laid out)
            // (only in the way at the bottom: in the middle or at the top it sits beside the buttons instead)
            val speedTop = if (speedPos != "bottom") maxHeight else if (speedTopPx >= 0) with(dens) { speedTopPx.toDp() } else maxHeight - 190.dp
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
                            Box(Modifier.size(52.dp).pressClick { engine.zoom(1.0) }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, L.t("Zoom in"), tint = c.text, modifier = Modifier.size(26.dp)) }
                            Box(Modifier.width(26.dp).height(1.dp).background(c.line))
                            Box(Modifier.size(52.dp).pressClick { engine.zoom(-1.0) }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Remove, L.t("Zoom out"), tint = c.text, modifier = Modifier.size(26.dp)) }
                        }
                    }
                }
                val settings: @Composable () -> Unit = { if (showSettings) ImmIcon(Icons.Rounded.Settings, { sheet = false; onSettings() }, desc = L.t("Settings")) }
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
                // (under the turn card on an upright phone on a route; kept clear of the trip card at the bottom if the screen is short)
                // (worked out in the buttons' own size, as the padding below is: Settings › sizes › map buttons scales it)
                // (just past halfway down the space between the turn card and the trip card, and never down into the speed sign)
                // (the portrait sheet: under the search bar, which has the whole width)
                // (the portrait sheet: half-way down the clear map between the search bar and the sheet, or the speed above the sheet)
                val railTop = if (sheetMode) with(LocalDensity.current) {
                    // (in real pixels: this density has the buttons' size in it, which the search bar and the sheet don't)
                    val px1 = 1.dp.toPx() / mainButtonScale
                    val gapTop = (16f + 60f + 16f) * px1
                    val sheetTop = boxH - tripPx - 12f * px1
                    val gapBottom = (if (speedTopPx > 0) minOf(sheetTop, speedTopPx.toFloat()) else sheetTop) - 10f * px1
                    val h = railHPx.toFloat()
                    ((gapTop + gapBottom) / 2f - h / 2f).coerceAtMost(gapBottom - h).coerceAtLeast(gapTop).toDp()
                } else if (!railBelow) 16.dp else with(LocalDensity.current) {
                    val gapTop = turnBottomPx - boxTopPx + 10.dp.toPx()
                    val tripTop = boxH - tripPx - 16.dp.toPx()
                    val gapBottom = (if (speedTopPx > 0) minOf(tripTop, speedTopPx.toFloat()) else tripTop) - 10.dp.toPx()
                    val mid = gapTop + (gapBottom - gapTop) * 0.55f
                    (mid - railHPx / 2f).coerceAtMost(gapBottom - railHPx).coerceAtLeast(gapTop).coerceAtLeast(16.dp.toPx()).toDp()
                }
                if (settingsAbove) Column(Modifier.align(side(Alignment.TopEnd)).padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = railTop).onGloballyPositioned { report(2, it.boundsInParent()); buttonsWPx = it.size.width; railHPx = it.size.height }, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = if (mir) Alignment.Start else Alignment.End) {
                    settings(); panelAndZoom()
                }
                // (the same with no Settings button above the panel: the panel itself goes under the turn card, Duddon Close)
                else Row(Modifier.align(side(Alignment.TopEnd)).padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = railTop).onGloballyPositioned { report(2, it.boundsInParent()); buttonsWPx = it.size.width; railHPx = it.size.height }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    if (mir) { panelAndZoom(); settings() } else { settings(); panelAndZoom() }
                }
                // the portrait sheet: Settings on its own at the end of the search bar, level with it
                if (sheetMode) Box(Modifier.align(side(Alignment.TopEnd)).sides(end = 16.dp).padding(top = 16.dp + (60.dp / mainButtonScale - 52.dp) / 2)) {
                    ImmIcon(Icons.Rounded.Settings, { sheet = false; onSettings() }, desc = L.t("Settings"))
                }
                // The compact panel opens inward from the report button, keeping the zoom controls and screen edge clear.
                AnimatedVisibility(
                    visible = quickReportOpen,
                    modifier = Modifier.align(side(Alignment.TopEnd))
                        .padding(top = railTop + 8.dp, start = if (mir) 76.dp else 12.dp, end = if (mir) 12.dp else 76.dp)
                        .zIndex(10f),
                    enter = fadeIn(tween(140)) + androidx.compose.animation.slideInHorizontally(tween(180), initialOffsetX = { if (mir) -it / 5 else it / 5 }),
                    exit = fadeOut(tween(120)) + androidx.compose.animation.slideOutHorizontally(tween(140), targetOffsetX = { if (mir) -it / 5 else it / 5 }),
                ) { QuickReportPanel(engine) { quickReportOpen = false } }
            }
        } else androidx.compose.runtime.SideEffect { engine.setPanel(2, 0f, 0f, 0f, 0f); if (buttonsWPx != 0) buttonsWPx = 0 }

        // ---- "See all locations": the branches in a panel down the driver's side (over the search), the map beside it
        if (engine.chain != null || engine.chainBusy) {
            // (a phone held upright: along the bottom, under the places it lists, which the map fits into the top part of the screen;
            // at the top it covered them)
            ChainPanel(engine, (if (narrow) Modifier.align(Alignment.BottomCenter) else Modifier.align(side(Alignment.TopStart))).zIndex(6f).padding(16.dp)
                .then(if (narrow) Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.45f) else Modifier.width(420.dp).fillMaxHeight()))
            if (engine.chainMoved || (engine.chainBusy && engine.chain != null))
                ChainSearchHere(engine, Modifier.align(Alignment.TopCenter).zIndex(6f)
                    .padding(top = 18.dp, bottom = 110.dp).then(if (narrow) Modifier else Modifier.sides(start = 440.dp)))
        }

        // ---- What's here (holding on the map): the road, its limit and the area, with Go / Add as a stop
        engine.here?.let { h ->
            // where Add a stop's list goes: along the bottom on a phone held upright, down the driver's side otherwise, with the
            // map moved so the place shows in the rest of the screen. Above everything else, so nothing covers its buttons
            // (its outline goes to the map, which centres the place in what's left clear)
            val hereRep = Modifier.onGloballyPositioned { val r = it.boundsInParent(); engine.setHereRect(r.left / boxW, r.top / boxH, r.right / boxW, r.bottom / boxH) }
            if (narrow) Box(Modifier.align(Alignment.BottomCenter).zIndex(8f).padding(16.dp).fillMaxWidth().then(hereRep)) {
                HereCardView(engine, h, maxH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.45f, docked = true)
            } else Box(Modifier.align(side(Alignment.TopStart)).zIndex(8f).padding(16.dp).width(420.dp).fillMaxHeight().then(hereRep)) {
                HereCardView(engine, h, docked = true, tall = true)
            }
        }

        // ---- the routes box, folded out beside the buttons by Other routes (as in Google Maps): your route and the other ways, with
        // the time, arrival, distance, the road each goes by and the traffic; tap one to switch
        if (!previewing && engine.altListOpen && engine.route != null) {
            val besideRail = with(dens) { buttonsWPx.toDp() } + 26.dp
            // (on a narrow, portrait screen there's no room beside the buttons: it's a panel along the bottom instead)
            if (altSheet) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().zIndex(7f).onSizeChanged { altSheetPx = it.height }) { RoutesBox(engine, sheet = true) }
            else Box(Modifier.align(side(Alignment.TopEnd)).sides(end = besideRail, top = if (altSide) 8.dp else 16.dp)
                .then(if (altSide) Modifier.zIndex(7f).onGloballyPositioned { lc ->
                    val b = lc.boundsInParent()
                    altSideL = if (b.left < boxW / 2f) (b.right / boxW).coerceIn(0f, 0.7f) else 0f
                    altSideR = if (b.left >= boxW / 2f) ((boxW - b.left) / boxW).coerceIn(0f, 0.7f) else 0f
                } else Modifier)) { RoutesBox(engine, maxH = if (altSide) screenH - 16.dp else null) }
        }

        // ---- speed limit and speedometer: bottom right, or bottom left when mirrored for left-hand drive (the cards are on the
        // right then; keeping the speed on the right too put it under the turn card on shorter screens)
        val besideButtons = with(dens) { buttonsWPx.toDp() } + 26.dp
        // (Card positions: on the other side if chosen, and lifted above the trip card when they share a corner)
        val speedShareTrip = !narrow && tripPx > 0 && tripAt == speedAt
        if (!previewing && !altFocus && !hudOff) Box(
            when {
                // (the portrait route layout: bottom left, above the trip sheet)
                routeSheet -> Modifier.align(side(Alignment.BottomStart)).sides(start = 16.dp, bottom = if (tripPx > 0) tripH + 8.dp else 16.dp)
                speedOnCardSide -> Modifier.align(if (speedAt == "left") Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(start = 16.dp, end = 16.dp, bottom = if (speedShareTrip) tripH + 8.dp else 16.dp)
                speedPos == "top" -> Modifier.align(side(Alignment.TopEnd)).sides(end = besideButtons, top = 16.dp)
                speedPos == "middle" -> Modifier.align(side(Alignment.CenterEnd)).sides(end = besideButtons)
                else -> Modifier.align(side(Alignment.BottomEnd)).sides(end = 16.dp, bottom = if ((narrow || speedShareTrip) && tripPx > 0) tripH + if (narrow) 4.dp else 8.dp else 16.dp)
            }
                .onGloballyPositioned { val t = it.boundsInParent().top.toInt(); if (it.size.height > 0 && t != speedTopPx) speedTopPx = t; if (it.size.width != speedWPx) speedWPx = it.size.width }
        ) {
            val baseSpeedDensity = LocalDensity.current
            // (sized by Settings > Screen & sizes > Speed limit and speedometer size, inside ImmSpeed)
            val speedSize = 1f
            CompositionLocalProvider(LocalDensity provides Density(baseSpeedDensity.density * speedSize, baseSpeedDensity.fontScale)) { ImmSpeed(engine) }
        }

        // ---- bottom left: hazards and the trip card (not while the "all locations" / add-a-stop panel is open: it's in the same
        // corner and the panel lets the map show through, so the trip card's figures showed through it)
        if (!previewing && !altFocus && !hudOff && engine.arrival == null && engine.chain == null && !engine.chainBusy) {
            val tripBaseDensity = LocalDensity.current
            val tripCardScale = engine.prefs.i(PreferenceKeys.MAIN_CARD_SIZE, 100).coerceIn(70, 150) / 100f
            val tripAlign = when (tripAt) { "centre" -> Alignment.BottomCenter; "right" -> Alignment.BottomEnd; "left" -> Alignment.BottomStart; else -> side(Alignment.BottomStart) }
            if (routeSheet) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { tripPx = it.height; tripWPx = it.width }
                .onGloballyPositioned { report(1, it.boundsInParent()) }) {
                TripSheet(engine, engine.prefs.b(PreferenceKeys.MEDIA_BAR, false) && com.headunit.launcher.MediaNow.showing(), screenH,
                    onOptions = { quickReportOpen = false; sheet = true }, onReport = { sheet = false; quickReportOpen = true })
            } else if (homeSheetUp) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { tripPx = it.height; tripWPx = it.width }
                .onGloballyPositioned { report(1, it.boundsInParent()) }) {
                // (in the trip card's place: everything that keeps clear of the trip card, the speed, re-centre and your arrow, keeps
                // clear of the sheet the same way)
                HomeSheet(engine, engine.prefs.b(PreferenceKeys.MEDIA_BAR, false) && com.headunit.launcher.MediaNow.showing(), screenH, { engine.prefs.put("settingscat", 12); sheet = false; onSettings() }) { try { focus.requestFocus() } catch (_: Exception) {} }
            } else
            Box(Modifier.align(tripAlign).then(if (narrow) Modifier.fillMaxWidth().sides(end = 16.dp) else Modifier).onSizeChanged { tripPx = it.height; tripWPx = it.width }
                .onGloballyPositioned { report(1, it.boundsInParent()) }) {
                CompositionLocalProvider(LocalDensity provides Density(tripBaseDensity.density * tripCardScale, tripBaseDensity.fontScale)) {
                    TripArea(engine, shareEta, narrow, voice, alignEnd = if (narrow || tripAt.isEmpty()) mir else tripAt == "right", centred = tripAt == "centre")
                }
            }
        }
        else androidx.compose.runtime.SideEffect { if (tripPx != 0) tripPx = 0; if (tripWPx != 0) tripWPx = 0; engine.setPanel(1, 0f, 0f, 0f, 0f) }

        // ---- the media bar (Reddit, mrEnmi): back, play / pause and next for whatever is playing on the head unit, with the song
        // when notification access is given. Only while something plays (or for a few minutes after a pause). It goes in the free
        // space at the bottom between the trip card and the speedometer; where that's too narrow (or on a portrait screen, or with
        // the trip card in the middle) it sits just above the trip card instead
        val mediaOn = engine.prefs.b(PreferenceKeys.MEDIA_BAR, false)
        FollowMedia(mediaOn)
        val mediaShow = mediaOn && com.headunit.launcher.MediaNow.showing() && !previewing && engine.arrival == null && engine.here == null &&
            engine.chain == null && !engine.chainBusy && !engine.itineraryOpen && !altFocus && !homeSheetUp && !routeSheet && !hudMin
        val tripW = with(dens) { tripWPx.toDp() }
        val speedW = with(dens) { speedWPx.toDp() }
        val mediaMax = 360.dp
        val mediaMod: Modifier = run {
            // speed in the other bottom corner (it's above the trip card when they share one, or up the side when set to middle / top)
            val speedInCorner = speedPos == "bottom" && !speedShareTrip
            val spare = maxWidth - 16.dp - tripW - 12.dp - 12.dp - (if (speedInCorner) speedW + 16.dp else 16.dp)
            when {
                // no trip card: bottom centre, clear of the speedometer on either side
                tripPx == 0 -> Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).widthIn(max = minOf(mediaMax, maxWidth - (speedW + 28.dp) * 2).coerceAtLeast(200.dp))
                // beside the trip card
                !narrow && tripAt != "centre" && spare >= 240.dp -> {
                    val w = minOf(mediaMax, spare)
                    if (tripAt == "right") Modifier.align(Alignment.BottomEnd).padding(end = 16.dp + tripW + 12.dp, bottom = 16.dp).widthIn(max = w)
                    else Modifier.align(Alignment.BottomStart).padding(start = 16.dp + tripW + 12.dp, bottom = 16.dp).widthIn(max = w)
                }
                // above the trip card, on its side (in portrait the speedometer is above the trip card too, on the other side)
                else -> {
                    val w = minOf(mediaMax, if (narrow) maxWidth - 32.dp - speedW - 12.dp else maxOf(tripW, 260.dp))
                    val a = when { tripAt == "centre" -> Alignment.BottomCenter; tripAt == "right" -> Alignment.BottomEnd; tripAt == "left" -> Alignment.BottomStart; else -> side(Alignment.BottomStart) }
                    Modifier.align(a).padding(start = 16.dp, end = 16.dp, bottom = tripH + 10.dp).widthIn(max = w)
                }
            }
        }
        AnimatedVisibility(mediaShow, modifier = mediaMod.onSizeChanged { mediaHPx = it.height },
            enter = fadeIn(tween(220)) + androidx.compose.animation.expandVertically(tween(240)), exit = fadeOut(tween(200)) + androidx.compose.animation.shrinkVertically(tween(220))) {
            MediaBar()
        }
        if (!mediaShow && mediaHPx != 0) androidx.compose.runtime.SideEffect { mediaHPx = 0 }
        val mediaLift = if (mediaShow && mediaHPx > 0) with(dens) { mediaHPx.toDp() } + 10.dp else 0.dp
        // (the road name pill when cruising, drawn in App, goes above the bar however tall it is: it overlapped on a phone
        // held sideways, where the bar with the song's name is taller than the room it was given, GitHub #277)
        val mediaBottomReach = if (mediaShow && mediaHPx > 0) with(dens) { mediaHPx.toDp() } + 16.dp else 0.dp
        androidx.compose.runtime.SideEffect { if (engine.mediaLiftDp != mediaBottomReach.value) engine.mediaLiftDp = mediaBottomReach.value }

        // ---- bottom centre: re-centre once you have panned away, or zoomed in / out with the buttons (it puts the zoom back to automatic)
        AnimatedVisibility(
            !previewing && !altFocus && (!engine.following || kotlin.math.abs(engine.zoomOffset) > 0.01) && engine.arrival == null, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (if (narrow && tripPx > 0) tripH + 10.dp else 18.dp) + mediaLift),
            enter = fadeIn(tween(200)) + androidx.compose.animation.scaleIn(tween(220), initialScale = 0.85f), exit = fadeOut(tween(150)) + androidx.compose.animation.scaleOut(tween(150), targetScale = 0.9f),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ImmPill(L.t("Re-centre"), { engine.recenter(byUser = true) }, Icons.Rounded.MyLocation, filled = true)
                // zoomed with + and - while following you, with no route: keep this as your normal zoom
                if (!routing && engine.following && kotlin.math.abs(engine.zoomOffset) > 0.01) ImmPill(L.t("Make this my default"), { engine.saveZoomAsDefault() }, Icons.Rounded.ZoomIn)
            }
        }

        // ---- the traffic view while you're stopped (it goes back by itself when you move off, or tap it). It sits at the top,
        // in the gap between the turn card and the map buttons, when it fits there; otherwise just above the trip card
        var trafficPillWPx by remember { mutableStateOf(0) }
        val trafficPillW = with(dens) { trafficPillWPx.toDp() }
        val topLeftEdge = 16.dp + 380.dp + 12.dp
        val topRightEdge = with(dens) { buttonsWPx.toDp() } + 16.dp + 12.dp + (if (speedPos == "top") 110.dp else 0.dp)
        val topGap = maxWidth - topLeftEdge - topRightEdge
        val trafficOnTop = !narrow && trafficPillWPx > 0 && trafficPillW <= topGap
        val trafficMod = when {
            trafficOnTop -> Modifier.align(side(Alignment.TopStart)).sides(start = topLeftEdge + (topGap - trafficPillW) / 2, top = 16.dp)
            tripPx > 0 && !narrow -> Modifier.align(side(Alignment.BottomStart)).sides(start = 16.dp, bottom = tripH + 10.dp)
            else -> Modifier.align(Alignment.BottomCenter).padding(bottom = if (tripPx > 0) tripH + 10.dp else 18.dp)
        }
        AnimatedVisibility(engine.stoppedOverview && !previewing && !altFocus, modifier = trafficMod,
            enter = fadeIn(tween(300)), exit = fadeOut(tween(250))) {
            Row(Modifier.onSizeChanged { if (it.width > 0) trafficPillWPx = it.width }.panel(6.dp, RoundedCornerShape(20.dp), c.surface).pressClick { engine.dismissStoppedOverview() }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Traffic, null, tint = c.accent, modifier = Modifier.size(18.dp))
                Text(L.t("Showing traffic while you’re stopped"), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                Icon(Icons.Rounded.Close, L.t("Close"), tint = c.sub, modifier = Modifier.padding(start = 8.dp).size(16.dp))
            }
        }

        // "Start from here" (#88): where routes are being planned from, until ✕ (or you set off with a GPS fix). Same spot as the
        // traffic pill (never shown together)
        val sf = engine.startFrom
        // (not over the route choice on a phone: there the bottom of the screen is the route card's buttons, and it covered them)
        AnimatedVisibility(sf != null && !altFocus && !engine.stoppedOverview && !(previewing && (narrow || maxHeight < 480.dp)), modifier = trafficMod, enter = fadeIn(tween(300)), exit = fadeOut(tween(250))) {
            Row(Modifier.onSizeChanged { if (it.width > 0) trafficPillWPx = it.width }.panel(6.dp, RoundedCornerShape(20.dp), c.surface).pressClick { engine.clearStartFrom() }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MyLocation, null, tint = c.accent, modifier = Modifier.size(18.dp))
                Text(L.t("Starting from {0}", sf?.name ?: ""), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).widthIn(max = 280.dp))
                Icon(Icons.Rounded.Close, L.t("Close"), tint = c.sub, modifier = Modifier.padding(start = 8.dp).size(16.dp))
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

        // ("Faster route found" is a strip across the top of the trip card: TripArea)

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
        ) { OptionsSheet(engine, shareEta, wide = !narrow && maxWidth >= 860.dp && maxHeight < maxWidth, onSettings = { sheet = false; onSettings() }, voice = voice) { sheet = false } }
        // Add a stop: a panel down the side with the map still showing (where "all locations" goes); while the places of a kind
        // along the route are listed, that list takes its place. The phone's Back button goes back a step
        if (engine.stopsEditor && engine.chain == null && !engine.chainBusy)
            // (a phone held upright: along the bottom like the list it opens, lifted above the keyboard while you type)
            StopsPanel(engine, (if (narrow) Modifier.align(Alignment.BottomCenter).imePadding() else Modifier.align(side(Alignment.TopStart))).zIndex(6f).padding(16.dp)
                .then(if (narrow) Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.6f) else Modifier.width(420.dp).heightIn(max = maxHeight - 32.dp)),
                onClose = { engine.stopsEditor = false })
        BackHandler(enabled = engine.stopsEditor) { if (engine.chain != null || engine.chainBusy) engine.closeChain(back = true) else engine.stopsEditor = false }
    }
}

/** The same one-tap reports as Map options, brought onto the map for quick access (#275). */
@Composable
private fun QuickReportPanel(engine: NavEngine, close: () -> Unit) {
    val c = LocalVw.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.width(260.dp).panel(8.dp, shape, c.panel).border(1.dp, c.line, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L.t("Report here"), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            ImmIcon(Icons.Rounded.Close, close, size = 36.dp, desc = L.t("Close"))
        }
        WideTiles(2, listOf(
            TileSpec(Icons.Rounded.LocalPolice, L.t("Police"), Color(0xFF1E66F5), { engine.report("police"); close() }),
            TileSpec(Icons.Rounded.CarCrash, L.t("Crash"), Bad, { engine.report("accident"); close() }),
            TileSpec(Icons.Rounded.PriorityHigh, L.t("Hazard"), Warn, { engine.report("hazard"); close() }),
            TileSpec(Icons.Rounded.Build, L.t("Broken down"), Color(0xFFFB8C00), { engine.report("breakdown"); close() }),
        ), compact = true)
        WideTiles(2, listOf(
            TileSpec(Icons.Rounded.CameraAlt, L.t("Mobile camera"), Color(0xFF1E66F5), { engine.report("mobilecam"); close() }),
            TileSpec(Icons.Rounded.Traffic, L.t("Traffic jam"), Color(0xFFB01C2E), { engine.report("traffic"); close() }),
        ), compact = true)
    }
}

/**
 * Map options: the map's look first (Standard or Satellite, Auto / Day / Night), then what's shown on it as tiles that light up when
 * on, then the trip tools, reports and things to find. In landscape on a wide screen it's twice as wide, in two columns, so nothing
 * needs scrolling; it still scrolls if a small screen needs it.
 */
@Composable
private fun OptionsSheet(engine: NavEngine, shareEta: () -> Unit, wide: Boolean = false, onSettings: () -> Unit = {}, voice: () -> Unit = {}, close: () -> Unit) { TextSides {
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
            Text(L.t("Map options"), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            ImmIcon(Icons.Rounded.Settings, onSettings, size = 42.dp, desc = L.t("Settings"))
            Spacer(Modifier.width(8.dp))
            ImmIcon(Icons.Rounded.Close, close, size = 42.dp, desc = L.t("Close"))
        }
        val mapPart: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // quick rows: the view and the sound (they were round buttons on the map)
                SheetHeading(L.t("View"))
                Segments(listOf("2D", "3D"), if (engine.tilt3d) 1 else 0) { i -> if ((i == 1) != engine.tilt3d) engine.toggleTilt() }
                SheetHeading(L.t("Sound"))
                Segments(listOf(L.t("Voice"), L.t("Alerts only"), L.t("Mute")), engine.soundMode) { i -> p.put(PreferenceKeys.SOUND_MODE, i) }
                SheetHeading(L.t("Map"))
                // the map's look: two picture cards, then day and night
                val sat = p.b(PreferenceKeys.SATELLITE, false)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LookCard(Modifier.weight(1f), L.t("Standard"), false, !sat) { p.put(PreferenceKeys.SATELLITE, false) }
                    LookCard(Modifier.weight(1f), L.t("Satellite"), true, sat) { p.put(PreferenceKeys.SATELLITE, true) }
                }
                // Auto covers every automatic choice in Settings (headlights, sun times, your own times): tapping it while one of those
                // is on leaves it as it is
                val theme = p.s(PreferenceKeys.THEME, "auto")
                val seg = when (theme) { "day" -> 1; "night" -> 2; else -> 0 }
                Segments(listOf(L.t("Auto"), L.t("Day"), L.t("Night")), seg) { i -> if (i != seg) p.put(PreferenceKeys.THEME, listOf("auto", "day", "night")[i]) }
                // the map's brightness, for whichever of day and night is showing (the same settings as in Light & 3D: set each once and
                // the map changes with the time of day, mjdshibly #236). Only on Mapbox's own map, whose light the app sets
                if (!sat && engine.map.lightsAdjustable) MapBrightRow(p, c.dark)
                SheetHeading(L.t("On the map"))
                fun tog(key: String, def: Boolean): Pair<Boolean, () -> Unit> { val on = p.b(key, def); return on to { p.put(key, !on) } }
                val layers = listOf(
                    Triple(Icons.Rounded.Traffic, L.t("Traffic"), tog(PreferenceKeys.TRAFFIC, true)),
                    Triple(Icons.Rounded.Apartment, L.t("3D buildings"), tog(PreferenceKeys.BUILDINGS_3D, true)),
                    Triple(Icons.Rounded.Traffic, L.t("Traffic lights"), tog(PreferenceKeys.SIGNALS, true)),
                    Triple(Icons.Rounded.CameraAlt, L.t("Cameras"), tog(PreferenceKeys.HAZARD_ICONS, true)),
                    Triple(Icons.Rounded.Lightbulb, L.t("Street lamps"), tog(PreferenceKeys.LAMPS, true)),
                    Triple(Icons.Rounded.Park, L.t("Trees"), tog(PreferenceKeys.TREES, true)),
                    Triple(Icons.Rounded.DirectionsWalk, L.t("Footpaths"), tog(PreferenceKeys.PATHS, true)),
                    Triple(Icons.Rounded.WaterDrop, L.t("Rain radar"), tog(PreferenceKeys.RAIN_RADAR, false)),
                    Triple(Icons.Rounded.AcUnit, L.t("Weather"), tog(PreferenceKeys.RAIN_EFFECTS, false)),
                )
                for (row in layers.chunked(3)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((icon, label, st) in row) ToggleTile(Modifier.weight(1f), icon, label, st.first, st.second)
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        val tripPart: @Composable () -> Unit = {
            if (routing) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetHeading(L.t("This trip"))
                WideTiles(5, listOf(
                    TileSpec(Icons.Rounded.Mic, L.t("Voice"), c.accent, { close(); voice() }),
                    TileSpec(Icons.Rounded.Add, if (engine.via.isEmpty()) L.t("Add stop") else L.t("Stops"), c.accent, { engine.stopsEditor = true; close() }),
                    TileSpec(Icons.Rounded.DoNotDisturbOn, L.t("Avoid road"), Warn, { engine.avoidRoadAhead(); close() }),
                    TileSpec(Icons.Rounded.Route, L.t("Overview"), c.accent, { engine.overview(); close() }),
                    TileSpec(Icons.Rounded.Share, L.t("Share ETA"), Good, { shareEta(); close() }),
                ))
            }
        }
        val restPart: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetHeading(L.t("Report here"))
                WideTiles(3, listOf(
                    TileSpec(Icons.Rounded.LocalPolice, L.t("Police"), Color(0xFF1E66F5), { engine.report("police"); close() }),
                    TileSpec(Icons.Rounded.CarCrash, L.t("Crash"), Bad, { engine.report("accident"); close() }),
                    TileSpec(Icons.Rounded.PriorityHigh, L.t("Hazard"), Warn, { engine.report("hazard"); close() }),
                    TileSpec(Icons.Rounded.Build, L.t("Broken down"), Color(0xFFFB8C00), { engine.report("breakdown"); close() }),
                    TileSpec(Icons.Rounded.CameraAlt, L.t("Mobile camera"), Color(0xFF1E66F5), { engine.report("mobilecam"); close() }),
                    TileSpec(Icons.Rounded.Traffic, L.t("Traffic jam"), Color(0xFFB01C2E), { engine.report("traffic"); close() }),
                ), compact = true)
                // with no route the map page already has fuel, charging, food, coffee and parking, so only the rest are here
                fun find(kind: String): () -> Unit = { engine.loadNearby(kind, false); close() }
                val finds = listOfNotNull(
                    if (engine.route != null && !engine.hideFuel) TileSpec(Icons.Rounded.LocalGasStation, L.t("Fuel"), Color(0xFFEA4335), find("fuel")) else null,
                    if (engine.route != null) TileSpec(Icons.Rounded.EvStation, L.t("Charging"), Color(0xFF34A853), find("charging")) else null,
                    if (engine.route != null) TileSpec(Icons.Rounded.Restaurant, L.t("Food"), Color(0xFFF29900), find("food")) else null,
                    if (engine.route != null) TileSpec(Icons.Rounded.LocalCafe, L.t("Coffee"), Color(0xFFA56BFF), find("coffee")) else null,
                    TileSpec(Icons.Rounded.Wc, L.t("Toilets"), Color(0xFF4C8DFF), find("toilets")),
                    if (engine.route != null) TileSpec(Icons.Rounded.LocalParking, L.t("Parking"), Color(0xFF4C8DFF), find("parking")) else null,
                    if (engine.route != null) TileSpec(Icons.Rounded.Apartment, L.t("Services"), Color(0xFF12A4B4), find("services")) else null,
                    if (engine.dest != null) TileSpec(Icons.Rounded.Place, L.t("Park at end"), Color(0xFFEA4335), { engine.loadNearby("parking", true); close() }) else null,
                )
                SheetHeading(if (engine.route != null) L.t("Find along the route") else L.t("Find nearby"))
                WideTiles(4, finds)
                // closes the app properly: ends any route and stops the GPS, so nothing keeps running in the background (pc4096: swiping
                // it away left a route, and the GPS with it, going for hours)
                val act = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
                Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).border(1.5.dp, Bad.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                    .clickable {
                        close()
                        try { if (engine.route != null || engine.previewing) engine.endRoute() } catch (e: Exception) { }
                        try { engine.stopLocation() } catch (e: Exception) { }
                        try { if (!com.headunit.launcher.RouteSync.carConnected()) act?.let { com.headunit.launcher.NavService.stop(it.applicationContext) } } catch (e: Exception) { }
                        try { act?.finishAndRemoveTask() } catch (e: Exception) { }
                    }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PowerSettingsNew, null, tint = Bad, modifier = Modifier.size(18.dp))
                    Text(L.t("Close Horizon Nav"), color = Bad, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                }
                Text(L.t("Ends the route and stops GPS"), color = c.sub, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
} }

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
private fun TripArea(engine: NavEngine, shareEta: () -> Unit, narrow: Boolean = false, voice: () -> Unit = {}, alignEnd: Boolean = engine.layoutMirrored, centred: Boolean = false) {
    val c = LocalVw.current
    val inc = engine.incidentAhead
    val cam = engine.cameraDist
    val eta = engine.eta
    val radarOn = engine.prefs.b(PreferenceKeys.HAZARDRADAR, false)
    if (inc == null && cam == null && eta == null && (!radarOn || engine.radar.isEmpty())) return
    val mir = alignEnd
    Column(Modifier.padding(start = if (mir || centred) 0.dp else 16.dp, end = if (mir && !centred) 16.dp else 0.dp, bottom = 16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = if (centred) Alignment.CenterHorizontally else if (mir) Alignment.End else Alignment.Start) {
        // hazard radar: the next two miles at a glance (replaces the separate camera and incident badges)
        if (radarOn) HazardRadar(engine)
        else if ((inc != null && engine.alert?.kind != "incident") || (cam != null && engine.alert?.kind != "camera")) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (inc != null && engine.alert?.kind != "incident") HazardBadge("incident", L.t(inc.label), Geo.fmtDist(inc.dist, engine.imperial))
            if (cam != null && engine.alert?.kind != "camera") HazardBadge("camera", L.t("Speed camera"), Geo.fmtDist(cam.toDouble(), engine.imperial))
        }
        eta?.let { e ->
            val dc = delayTint(e.delayMin, c.surface)
            val shape = RoundedCornerShape(22.dp)
            // landscape: the card is only as wide as its contents (figures, then the buttons close by), and the route bar matches that width.
            // portrait: full width, with the buttons under the figures.
            val dens = androidx.compose.ui.platform.LocalDensity.current
            var rowPx by remember { mutableStateOf(0) }
            val rowW = with(dens) { rowPx.toDp() }
            TextSides { Column(Modifier.then(if (narrow) Modifier.fillMaxWidth() else Modifier).panel(10.dp, shape, c.surface).animateContentSize()) {
                val barMod = if (narrow) Modifier.fillMaxWidth() else Modifier.width(rowW)
                // "Faster route found" across the top of the card (it was a box of its own floating above it)
                if (narrow || rowPx > 0) FasterStrip(engine, barMod)
                // the route bar across the top of the card, with room to breathe
                if (engine.showRouteBar && (narrow || rowPx > 0)) {
                    RouteBar(engine, barMod.padding(start = if (narrow) 16.dp else 18.dp, end = if (narrow) 16.dp else 18.dp, top = if (narrow) 7.dp else 9.dp, bottom = if (narrow) 2.dp else 4.dp))
                    Box(barMod.padding(horizontal = 18.dp).height(1.dp).background(c.line))
                }
                // electric: roughly how much battery you'll arrive with (amber under 20%, red under 10%)
                val evPct = engine.evArrivalPct()
                val evStat = evPct != null && engine.prefs.b(PreferenceKeys.EV_PCT_CARD, true)
                val evCol = if (evPct == null) c.text else if (evPct < 10) Bad else if (evPct < 20) Warn else Good
                val stats: @Composable () -> Unit = {
                    ImmStat(Geo.fmtDur(e.secs), if (e.delayMin >= 1) L.t("+{0} min traffic", e.delayMin) else L.t("time left"), dc)
                    ImmStat(e.arrive, "arrival", c.text)
                    ImmStat(Geo.fmtDist(e.dist, engine.imperial), L.t("to go"), c.text)
                    if (evStat) ImmStat("$evPct%", L.t("battery at arrival"), evCol)
                }
                // send your arrival time (WhatsApp, a text...): only while stopped, so nobody picks a contact while driving (#213)
                val shareCtx = androidx.compose.ui.platform.LocalContext.current
                val canShare = (engine.fix?.speed ?: 0.0) < 2.0
                val share = { engine.etaShareText()?.let { t ->
                    val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, t)
                    try { shareCtx.startActivity(android.content.Intent.createChooser(i, L.t("Send arrival time")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (x: Exception) { }
                } }
                val buttons: @Composable () -> Unit = {
                    if (canShare) ImmCircle({ share() }, 44.dp) { Icon(Icons.Rounded.Send, L.t("Send arrival time"), tint = c.accent, modifier = Modifier.size(21.dp)) }
                    // electric: chargers along the route from here
                    // (voice and sound moved off the card: sound is a big button at the top right, voice is in Map options > This trip)
                    // add a stop (or see the stops) on the way
                    ImmCircle({ engine.stopsEditor = true }, 44.dp) { Icon(Icons.Rounded.AddLocationAlt, if (engine.via.isEmpty()) L.t("Add stop") else L.t("Stops"), tint = c.accent, modifier = Modifier.size(22.dp)) }
                    if (evPct != null) ImmCircle({ engine.loadNearby("charging", false) }, 44.dp) { Icon(Icons.Rounded.EvStation, L.t("Chargers ahead"), tint = Good, modifier = Modifier.size(22.dp)) }
                    Box(Modifier.size(44.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Bad).pressClick { engine.endRoute() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, L.t("End route"), tint = Color.White, modifier = Modifier.size(23.dp))
                    }
                }
                if (!narrow) Row(
                    Modifier.widthIn(min = 360.dp).onSizeChanged { rowPx = it.width }.padding(start = 18.dp, end = 12.dp, top = 9.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    stats()
                    Row(Modifier.padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { buttons() }
                } else androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // portrait: one compact line (figures, then the buttons), so the card stays low and clear of your arrow. When it can't
                    // all fit (an electric car adds a fourth figure and a fifth button), the buttons go on a line under the figures:
                    // squeezed onto one line the figures ran into each other ("21 min14:1216 km75")
                    val nStats = if (evStat) 4 else 3
                    val nButtons = (if (evPct != null) 3 else 2) + (if (canShare) 1 else 0)
                    val oneLine = maxWidth >= (nStats * 74 + nButtons * 42 + 30).dp
                    val figures: @Composable (Modifier) -> Unit = { m ->
                        // (grouped together on the left: spread across the card they looked stretched out)
                        Row(m, horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            ImmStat(Geo.fmtDur(e.secs), if (e.delayMin >= 1) L.t("+{0} min", e.delayMin) else L.t("time left"), dc, compact = true)
                            ImmStat(e.arrive, "arrival", c.text, compact = true)
                            ImmStat(Geo.fmtDist(e.dist, engine.imperial), L.t("to go"), c.text, compact = true)
                            if (evStat) ImmStat("$evPct%", "battery", evCol, compact = true)
                        }
                    }
                    val smallButtons: @Composable (Modifier) -> Unit = { m ->
                        Row(m, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (canShare) ImmCircle({ share() }, 40.dp) { Icon(Icons.Rounded.Send, L.t("Send arrival time"), tint = c.accent, modifier = Modifier.size(19.dp)) }
                            ImmCircle({ engine.stopsEditor = true }, 40.dp) { Icon(Icons.Rounded.AddLocationAlt, if (engine.via.isEmpty()) L.t("Add stop") else L.t("Stops"), tint = c.accent, modifier = Modifier.size(20.dp)) }
                            if (evPct != null) ImmCircle({ engine.loadNearby("charging", false) }, 40.dp) { Icon(Icons.Rounded.EvStation, L.t("Chargers ahead"), tint = Good, modifier = Modifier.size(20.dp)) }
                            Box(Modifier.size(40.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Bad).pressClick { engine.endRoute() }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Close, L.t("End route"), tint = Color.White, modifier = Modifier.size(21.dp))
                            }
                        }
                    }
                    if (oneLine) Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        figures(Modifier.weight(1f))
                        smallButtons(Modifier.padding(start = 12.dp))
                    } else Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        figures(Modifier.fillMaxWidth().padding(end = 4.dp))
                        smallButtons(Modifier.align(Alignment.End))
                    }
                }
                // what the trip has cost so far (#316)
                LiveFuelLine(engine, Modifier.padding(start = if (narrow) 14.dp else 18.dp, end = 12.dp, bottom = 8.dp))
            } }
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
        Text("N", color = if (engine.northUp) c.accent else c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        // the red mark rides round the edge and always points at real north
        val a = Math.toRadians(-engine.compassDeg)
        Box(Modifier.offset(x = (20 * kotlin.math.sin(a)).dp, y = (-20 * kotlin.math.cos(a)).dp).size(8.dp).clip(CircleShape).background(Bad))
    }
}

/** Speed limit sign beside a large readout of your current speed. */
@Composable
internal fun ImmSpeed(engine: NavEngine) {
    // a clock with the speedometer (Settings › While driving › Clock on the map): on the side away from the screen edge it sits against, so
    // it never pushes the speedometer off the screen. Not on the car screen, which has its own clock (bijindas)
    if (!engine.isCar && engine.prefs.b(PreferenceKeys.CLOCK_MAP, false)) {
        val atTop = LocalSpeedAtTop.current
        val ringSign = engine.prefs.b(PreferenceKeys.SPEEDRING, true) && engine.speedLimit != null && engine.prefs.b(PreferenceKeys.MAPSPEED, true)
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(if (ringSign && !atTop) 26.dp else 6.dp)) {
            if (!atTop) MapClock()
            ImmSpeedOnly(engine)
            if (atTop) MapClock()
        }
        return
    }
    ImmSpeedOnly(engine)
}

/** The time now, in a small pill (12 or 24-hour as Settings › Units › Clock). Ticks over on the minute. */
@Composable
private fun MapClock() {
    val c = LocalVw.current
    var now by remember { mutableStateOf(com.headunit.launcher.Clock.hm(System.currentTimeMillis())) }
    LaunchedEffect(Unit) {
        while (true) {
            val ms = System.currentTimeMillis()
            now = com.headunit.launcher.Clock.hm(ms)
            kotlinx.coroutines.delay(60_000L - ms % 60_000L + 50L)
        }
    }
    Box(Modifier.panel(6.dp, RoundedCornerShape(50), c.surface).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(now, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ImmSpeedOnly(engine: NavEngine) {
    // the size from Settings (100% = the standard size): it's laid out at that size, so it keeps clear of the edges and other cards
    val pct = engine.prefs.i(PreferenceKeys.SPEED_SIZE, 100).coerceIn(50, 200)
    if (pct == 100) { SpeedWithNext(engine); return }
    val base = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density * pct / 100f, base.fontScale),
    ) { SpeedWithNext(engine) }
}

/**
 * True where the speed sits at the top of the screen (Android Auto's Top right): what goes with it then hangs below it instead
 * of standing on top, the next limit under the speedometer and the limit sign on the ring's bottom corner, so the speedometer
 * itself stays right up in the corner.
 */
internal val LocalSpeedAtTop = androidx.compose.runtime.staticCompositionLocalOf { false }

/** The speed, with the next speed limit on the route (when it changes within about half a mile) small above it. */
@Composable
private fun SpeedWithNext(engine: NavEngine) {
    val atTop = LocalSpeedAtTop.current
    val nx = engine.nextLimit
    // (the compact pill on the car carries the next limit inside it: a second chip stacked on the pill looked like two odd bits)
    if (nx == null || !engine.prefs.b(PreferenceKeys.NEXT_LIMIT, true) || carCompactSpeed(engine)) { SpeedBlock(engine); return }
    val c = LocalVw.current
    val k = if (engine.imperial) 2.23694 else 3.6
    // with the speedometer ring, the limit sign pokes up above the ring's top corner: the gap leaves room for it, so the two
    // don't overlap (on the car screen they did)
    val ringSign = engine.prefs.b(PreferenceKeys.SPEEDRING, true) && engine.speedLimit != null && engine.prefs.b(PreferenceKeys.MAPSPEED, true) && !carCompactSpeed(engine)
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(if (ringSign) 26.dp else 6.dp)) {
        if (atTop) SpeedBlock(engine)
        Row(
            Modifier.panel(6.dp, RoundedCornerShape(20.dp), c.surface).padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val num = "${(Math.round(nx.first * k / 5.0) * 5)}"
            // the same kind of sign as the main one: the red ring, or the black-bordered board in the US and Canada
            // (drawn exactly like the main sign, just smaller)
            LimitSignFace(num.toInt(), engine.limitSignStyle, if (engine.limitSignStyle != "round") 40.dp else 36.dp)
            Text(L.t("in {0}", Geo.fmtDist(nx.second, engine.imperial)), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        if (!atTop) SpeedBlock(engine)
    }
}

/** The compact speedometer is picked for the Android Auto screen (Settings > Android Auto > Speedometer style). */
private fun carCompactSpeed(engine: NavEngine): Boolean = engine.isCar && engine.prefs.s(PreferenceKeys.CAR_SPEED_STYLE, "app") == "compact"

/**
 * The compact speedometer for the car screen: one small dark pill with the limit sign (when the limit is known) and your speed
 * beside it, or just a circle with your speed. Much smaller than the ring, so it tucks into a corner. It follows day and night
 * like the other cards (pc4096: the always-black pill was jarring and the amber and red speeds didn't suit it).
 */
@Composable
private fun SpeedPill(engine: NavEngine, show: Boolean, k: Double) {
    val c = LocalVw.current
    val lim = engine.speedLimit
    Row(
        Modifier.height(60.dp).widthIn(min = 60.dp).panel(6.dp, RoundedCornerShape(30.dp), c.surface).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        // (Big speed limit sign: the sign fills the pill's height and your speed is smaller beside it)
        val swap = lim != null && show && engine.prefs.b(PreferenceKeys.SPEED_SWAP, false)
        if (lim != null) SpeedLimitSign(engine, if (swap) 54.dp else 48.dp)
        // (the same width with or without the sign, so the pill doesn't change size as the figures do)
        if (show) Column(Modifier.widthIn(min = if (swap) 38.dp else 48.dp).padding(horizontal = if (lim != null) 6.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${Math.round((engine.fix?.speed ?: 0.0) * k)}", color = speedTint(engine) ?: c.text, fontSize = if (swap) 16.sp else 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Text(if (engine.imperial) "mph" else "km/h", color = c.sub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
        // the next limit on the route, in the same pill after a thin line: a smaller sign with how far it is under it
        val nx = engine.nextLimit
        if (nx != null && engine.prefs.b(PreferenceKeys.NEXT_LIMIT, true)) {
            Box(Modifier.padding(start = if (lim != null || show) 4.dp else 0.dp, end = 8.dp).width(1.dp).height(34.dp).background(c.sub.copy(alpha = 0.35f)))
            Column(Modifier.padding(end = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                val num = "${(Math.round(nx.first * k / 5.0) * 5)}"
                LimitSignFace(num.toInt(), engine.limitSignStyle, if (engine.limitSignStyle != "round") 34.dp else 30.dp)
                Text(Geo.fmtDist(nx.second, engine.imperial), color = c.sub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun SpeedBlock(engine: NavEngine) {
    val c = LocalVw.current
    val show = engine.prefs.b(PreferenceKeys.MAPSPEED, true)
    val lim = engine.speedLimit
    if (lim == null && !show) return
    val k = if (engine.imperial) 2.23694 else 3.6
    if (carCompactSpeed(engine)) { SpeedPill(engine, show, k); return }
    // swapped round (Settings > Speed display > Big speed limit sign): only with a limit known, else the speedometer as usual
    if (show && lim != null && engine.prefs.b(PreferenceKeys.SPEED_SWAP, false)) { SwappedSpeed(engine, k); return }
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
 * The speed limit sign big, where the speedometer usually is, and your speed small on its inner top corner, where the sign
 * usually is (ole1993: the limit matters more to him than his own speed). The speed still turns amber and red near and over
 * the limit.
 */
@Composable
private fun SwappedSpeed(engine: NavEngine, k: Double) {
    val c = LocalVw.current
    Box(contentAlignment = Alignment.Center) {
        // (the ring's space, so the layout is the same size as usual; the sign itself is drawn last, below, so it's always in front)
        Box(Modifier.size(104.dp))
        // (on the side towards the middle of the screen, worked out as for the usual layout, so it never runs off the edge)
        var onLeftHalf by remember { mutableStateOf(engine.mirrored && !engine.isCar) }
        Box(Modifier.matchParentSize().onGloballyPositioned { lc ->
            val rootW = lc.findRootCoordinates().size.width
            if (rootW > 0) {
                val left = lc.positionInRoot().x + lc.size.width / 2f < rootW / 2f
                if (left != onLeftHalf) onLeftHalf = left
            }
        })
        val signRight = onLeftHalf
        val atTop = LocalSpeedAtTop.current
        Column(
            Modifier.align(if (atTop) (if (signRight) Alignment.BottomEnd else Alignment.BottomStart) else if (signRight) Alignment.TopEnd else Alignment.TopStart)
                .offset(x = if (signRight) 26.dp else (-26).dp, y = if (atTop) 14.dp else (-14).dp)
                .size(56.dp).panel(8.dp, CircleShape, c.surface),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            val num = "${Math.round((engine.fix?.speed ?: 0.0) * k)}"
            Text(num, color = speedTint(engine) ?: c.text, fontSize = if (num.length >= 3) 17.sp else 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, lineHeight = 20.sp)
            Text(if (engine.imperial) "mph" else "km/h", color = c.sub, fontSize = 9.sp, maxLines = 1, lineHeight = 10.sp)
        }
        // the speed limit sign last, so it's always in front of the speed circle where the two meet
        Box(Modifier.size(104.dp).zIndex(1f), contentAlignment = Alignment.Center) { SpeedLimitSign(engine, 100.dp) }
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
        // (decided by where the speed really is on screen: with the speed set to the right on a left-hand-drive layout, the
        // sign went off the right edge, GitHub)
        var onLeftHalf by remember { mutableStateOf(engine.mirrored && !engine.isCar) }
        Box(Modifier.matchParentSize().onGloballyPositioned { lc ->
            val rootW = lc.findRootCoordinates().size.width
            if (rootW > 0) {
                val cx = lc.positionInRoot().x + lc.size.width / 2f
                val left = cx < rootW / 2f
                if (left != onLeftHalf) onLeftHalf = left
            }
        })
        val signRight = onLeftHalf
        // (on the ring's top corner; its bottom corner where the speed is at the top of the screen)
        val atTop = LocalSpeedAtTop.current
        // (the car screen's own sign size, 80 to 170%; the sign grows outwards from the ring's corner, so it moves out a little too)
        val ls = if (engine.isCar) engine.prefs.i(PreferenceKeys.CAR_LIMIT_SIZE, 100).coerceIn(80, 170) / 100f else 1f
        val extra = ((ls - 1f) * 25f).dp
        if (lim != null) Box(
            Modifier.align(if (!show) Alignment.Center else if (atTop) (if (signRight) Alignment.BottomEnd else Alignment.BottomStart) else if (signRight) Alignment.TopEnd else Alignment.TopStart)
                .offset(x = if (!show) 0.dp else if (signRight) 30.dp + extra else (-30).dp - extra, y = if (!show) 0.dp else if (atTop) 18.dp + extra else (-18).dp - extra)
        ) {
            // (the upright North American board is narrower than the round sign, so it gets a little more height to stay readable)
            SpeedLimitSign(engine, (if (!show) 70.dp else if (engine.limitSignStyle != "round") 58.dp else 50.dp) * ls)
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
) { TextSides {
    // (24: rounder again; 20 looked a little square)
    val shape = RoundedCornerShape(24.dp)
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
    // Settings › sizes › Turn arrow size and Turn card height (ZarulOfficial #317): the arrow on its own, and how much room there
    // is above and below the writing (70% is a tight card, 130% a roomy one)
    val arrowK = engine.prefs.i(if (car) PreferenceKeys.CAR_TURN_ARROW else PreferenceKeys.TURN_ARROW, 100).coerceIn(80, 200) / 100f
    val padK = (1f + (engine.prefs.i(if (car) PreferenceKeys.CAR_TURN_HEIGHT else PreferenceKeys.TURN_HEIGHT, 100).coerceIn(70, 130) / 100f - 1f) * 2.5f).coerceIn(0.25f, 1.75f)
    Column(
        Modifier.fillMaxWidth()
            // (grows from its top middle, evenly both ways: grown from its left edge, a card the full width of an upright phone went off
            // the right of the screen as the turn came up)
            .graphicsLayer { scaleX = grow; scaleY = grow; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f) }
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
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                }
            }
            .animateContentSize()
            // tap for the list of every turn on the route
            .clickable { engine.itineraryOpen = true },
    ) {
        if (slim) Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp * padK), verticalAlignment = Alignment.CenterVertically) {
            ManeuverArrow(t.maneuver, Geo.maneuverAngle(t.maneuver), Modifier.size(34.dp * arrowK), roundaboutExit = t.exit, roundaboutClockwise = engine.roundaboutClockwise, exitAngle = roundaboutTurn(t.rbIn, t.rbOut), sides = t.sides)
            RollText(Geo.fmtDist(t.dist, engine.imperial), Color.White, 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
            RoadLine(if (t.landmark.isNotBlank()) t.landmark + (if (t.street.isNotBlank()) " · " + t.street else "") else t.street, engine.ukRoadBadges, 16.sp, Modifier.padding(start = 12.dp), ref = if (t.landmark.isBlank()) t.ref else "")
        } else {
        val thenIcon = t.thenManeuver != null && engine.prefs.s(PreferenceKeys.THEN_STYLE, "row") == "icon"
        Row(Modifier.padding(start = if (compact) 12.dp else 18.dp, end = if (compact) 12.dp else 16.dp, top = (if (compact) 9.dp else 16.dp) * padK, bottom = (if (compact) 8.dp else 12.dp) * padK), verticalAlignment = Alignment.CenterVertically) {
            ManeuverArrow(t.maneuver, Geo.maneuverAngle(t.maneuver), Modifier.size((if (compact) 42.dp else 60.dp) * arrowK), roundaboutExit = t.exit, roundaboutClockwise = engine.roundaboutClockwise, exitAngle = roundaboutTurn(t.rbIn, t.rbOut), exitNumber = t.exit, sides = t.sides)
            Column(Modifier.padding(start = if (compact) 9.dp else 16.dp).weight(1f)) {
                // at traffic lights or a stop sign: that in big ("At the lights"), and the distance moves down beside the road name
                val lmk = t.landmark.isNotBlank()
                if (lmk) Text(t.landmark, color = Color.White, fontSize = if (compact) 24.sp else 31.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                else RollText(Geo.fmtDist(t.dist, engine.imperial), Color.White, if (compact) 28.sp else 38.sp, fontWeight = FontWeight.Bold)
                val ukSign = engine.prefs.b(PreferenceKeys.UKSIGNS, true) && (t.sign.isNotBlank() && t.sign != t.street || t.tag.startsWith("Exit"))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (t.tag.isNotBlank() && !(ukSign && t.tag.startsWith("Exit"))) Text(t.tag, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 7.dp, vertical = 2.dp))
                    if (!ukSign && t.sign.isNotBlank() && t.sign != t.street) Text(t.sign, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 130.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1E8E55)).padding(horizontal = 7.dp, vertical = 2.dp))
                    RoadLine(if (lmk) Geo.fmtDist(t.dist, engine.imperial) + (if (t.street.isNotBlank()) " · " + t.street else "") else t.street, engine.ukRoadBadges, if (compact) 16.sp else 19.sp, ref = if (!lmk) t.ref else "")
                }
            }
            // the turn straight after, as a small arrow (the "Then" line below is used instead when that style is chosen)
            if (thenIcon && t.thenManeuver != null) Column(Modifier.padding(start = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(TealDeep), contentAlignment = Alignment.Center) {
                    ManeuverArrow(t.thenManeuver, Geo.maneuverAngle(t.thenManeuver), Modifier.size(28.dp), roundaboutExit = t.thenExit, roundaboutClockwise = engine.roundaboutClockwise)
                }
                Text(L.t("then"), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
            }
        }
        // (the blue "EXIT" strip that used to sit here is gone: the exit's number badge beside the road name already says it, and its
        // arrows always pointed right, even for an exit on the left: mjdshibly #232)
        // A proper UK direction sign: blue for motorways, green for primary roads, white for local roads.
        if (engine.prefs.b(PreferenceKeys.UKSIGNS, true) && (t.sign.isNotBlank() && t.sign != t.street || t.tag.startsWith("Exit")))
            UkSign(t.sign, t.tag.removePrefix("Exit ").takeIf { t.tag.startsWith("Exit") }.orEmpty(), t.street, Modifier.padding(start = if (compact) 12.dp else 16.dp, end = if (compact) 12.dp else 16.dp, bottom = if (compact) 5.dp else 12.dp))
        // (the lanes stay on a small car screen too: they were left off with everything else, so small screens had no lane guidance)
        if ((!compact || car) && engine.prefs.b(PreferenceKeys.LANE_STRIP, true)) t.lanes?.let { ls ->
            // lane hero view: before a complex junction the lanes grow into a big picture with yours lit; simple ones keep the strip
            // (not in Android Auto: the big lane picture made the card grow to fill much of a car screen as soon as a junction came up)
            val hero = !car && engine.prefs.s(PreferenceKeys.LANE_STYLE, "hero") == "hero" && ls.lanes.size >= 2 && t.dist < 900.0
            // (a short, wide screen, like a phone held sideways: a lower lane picture, so the turn card no longer reaches down behind
            // the trip card and hides the "Then" line, fahadfadi)
            val shortScreen = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenHeightDp < 450 && it.screenWidthDp > it.screenHeightDp }
            if (hero) FixedSides { LaneHero(ls, t.maneuver, if (car) null else t.laneAdvice, canvasH = if (shortScreen) 60.dp else 118.dp) }
            else Box(Modifier.fillMaxWidth().background(TealDeep).padding(vertical = 4.dp), contentAlignment = Alignment.Center) { FixedSides { LaneRow(ls, t.maneuver, t.dist < 400.0) } }
        }
        val heroShown = !car && engine.prefs.b(PreferenceKeys.LANE_STRIP, true) && engine.prefs.s(PreferenceKeys.LANE_STYLE, "hero") == "hero" && (t.lanes?.lanes?.size ?: 0) >= 2 && t.dist < 900.0
        if (!compact && !heroShown && !(car && t.lanes != null && engine.prefs.b(PreferenceKeys.LANE_STRIP, true))) t.laneAdvice?.let { Text(it, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().background(TealDeep).padding(horizontal = 18.dp, vertical = 6.dp)) }
        // (the "then" line stays on a small car screen too)
        if ((!compact || car) && t.thenManeuver != null && !thenIcon) {
            Row(Modifier.fillMaxWidth().background(TealDeep).padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // a roundabout next: its exit number goes in the icon, so the line only needs the road ("Then A3057 Romsey Road");
                // "Then take the 3rd exit onto …" ran out of room on long names (karthikeyansr)
                val rbShort = t.thenExit > 0 && t.thenStreet.isNotBlank() && t.thenManeuver.uppercase().contains("ROUNDABOUT")
                if (rbShort) ManeuverArrow(t.thenManeuver, Geo.maneuverAngle(t.thenManeuver), Modifier.size(38.dp), roundaboutExit = t.thenExit, roundaboutClockwise = engine.roundaboutClockwise, exitNumber = t.thenExit)
                else ManeuverArrow(t.thenManeuver, Geo.maneuverAngle(t.thenManeuver), Modifier.size(22.dp), roundaboutExit = t.thenExit, roundaboutClockwise = engine.roundaboutClockwise)
                BadgedLine(if (rbShort) L.t("Then {0}", t.thenStreet) else t.thenText, engine.ukRoadBadges, 14.sp, Color.White.copy(alpha = 0.92f), Modifier.padding(start = 10.dp),
                    // on the car screen a long street name wraps onto a second line before it's cut short (fjunakovic, #248)
                    maxLines = if (car) 2 else 1)
            }
        }
        } // (end of the full card)
        footer?.invoke()
        // the progress bar sits a little way inside the card, clear of its round corners (which hid the last few seconds of it
        // when it ran along the very bottom edge, karthikeyansr)
        // (only once it starts to fill, about 450 m out: an empty bar for kilometres before looked like nothing was happening,
        // mjdshibly. Its space stays, so the card doesn't change height when it appears)
        Box(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 5.dp * padK.coerceAtMost(1f), bottom = 9.dp * padK.coerceIn(0.5f, 1.2f)).height(4.dp).clip(RoundedCornerShape(2.dp))
            .background(if (frac > 0f) Color.Black.copy(alpha = 0.18f) else Color.Transparent)) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color.White.copy(alpha = 0.9f)))
        }
    }
} }

/**
 * The portrait route layout's trip sheet. Folded: the route bar, time left, arrival (larger, as it's what's looked at most) and
 * distance, with End; "Faster route found" across it; in the last stretch, which side the destination is on and car parks near
 * it. Pulled up (or the handle tapped): add a stop, send your arrival time, overview, report and map options, the next turns,
 * and the music.
 */
@Composable
private fun TripSheet(engine: NavEngine, media: Boolean, screenH: Dp, onOptions: () -> Unit, onReport: () -> Unit) {
    val c = LocalVw.current
    val e = engine.eta ?: return
    var open by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val dc = delayTint(e.delayMin, c.surface)
    val shareCtx = androidx.compose.ui.platform.LocalContext.current
    val canShare = (engine.fix?.speed ?: 0.0) < 2.0
    val arriving = e.dist < 800.0
    // (folds again by itself after a while, so the map isn't left covered while you drive)
    var touched by remember { mutableStateOf(0) }
    LaunchedEffect(open, touched) { if (open) { delay(15_000L); open = false } }
    Column(Modifier.fillMaxWidth().panel(12.dp, shape, c.surface)
        .pointerInput(Unit) {
            var dragged = 0f
            detectVerticalDragGestures(onDragStart = { dragged = 0f }, onDragEnd = {
                if (dragged < -40f) { open = true; touched++ } else if (dragged > 40f) open = false
            }) { ch, dy: Float -> ch.consume(); dragged += dy }
        }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { touched++ }
        .navigationBarsPadding()
        .animateContentSize()) {
        Box(Modifier.fillMaxWidth().height(20.dp).clickable { open = !open; touched++ }, contentAlignment = Alignment.Center) {
            Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.line))
        }
        // (well in from the screen's edges: 22 dp each side)
        FasterStrip(engine, Modifier.fillMaxWidth(), edge = 22.dp)
        if (engine.showRouteBar) RouteBar(engine, Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 2.dp))
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // (the figures take what room is left and are clipped if the screen is too narrow or its text too large, so the buttons
            // after them are never pushed off the edge: End went missing that way on a wide, large-text screen, GitHub #341)
            Row(Modifier.weight(1f).clipToBounds(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ImmStat(Geo.fmtDur(e.secs), if (e.delayMin >= 1) L.t("+{0} min", e.delayMin) else L.t("time left"), dc, compact = true)
                ImmStat(e.arrive, "arrival", c.text)
                ImmStat(Geo.fmtDist(e.dist, engine.imperial), L.t("to go"), c.text, compact = true)
            }
            // add a stop, then End: both big enough to hit while driving
            Box(Modifier.size(50.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(c.tile).pressClick { engine.stopsEditor = true; open = false },
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AddLocationAlt, if (engine.via.isEmpty()) L.t("Add stop") else L.t("Stops"), tint = c.accent, modifier = Modifier.size(25.dp))
            }
            // (a round red X, as everywhere else End is: the wide "End" button was missed, Dotancom #318)
            Box(Modifier.size(50.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Bad).pressClick { engine.endRoute() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, L.t("End route"), tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        // what the trip has cost so far (#316)
        LiveFuelLine(engine, Modifier.padding(start = 22.dp, end = 18.dp, bottom = 10.dp))
        // arriving: the side of the road, and car parks near the destination (tap one to go there instead)
        if (arriving) {
            val side = remember(engine.route) { engine.destSide() }
            val parks = engine.parkingNear
            if (side.isNotEmpty() || parks.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (side.isNotEmpty()) Row(Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(c.accent.copy(alpha = 0.16f)).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Flag, null, tint = c.accent, modifier = Modifier.size(18.dp))
                    Text(L.t(if (side == "left") "On the left" else "On the right"), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                }
                parks.take(3).forEach { n ->
                    Row(Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(c.tile).clickable { engine.parkAt(n) }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2E6FD8)), contentAlignment = Alignment.Center) {
                            Text("P", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                        }
                        Text(n.place.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 6.dp).widthIn(max = 160.dp))
                        Text(" · " + L.t("{0} min walk", maxOf(1, Math.round(n.dist / 80.0).toInt())), color = c.sub, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
        AnimatedVisibility(open, enter = fadeIn(tween(200)) + androidx.compose.animation.expandVertically(tween(240)),
            exit = fadeOut(tween(140)) + androidx.compose.animation.shrinkVertically(tween(200))) {
            Column(Modifier.fillMaxWidth().heightIn(max = screenH * 0.45f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // the trip's actions, as tiles
                val evPct = engine.evArrivalPct()
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val act: @Composable (ImageVector, String, Color, () -> Unit) -> Unit = { icon, label, tint, go ->
                        Column(Modifier.width(76.dp).clip(RoundedCornerShape(16.dp)).background(c.tile).clickable { touched++; go() }.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
                            Text(label, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    if (canShare) act(Icons.Rounded.Send, L.t("Share ETA"), c.accent) {
                        engine.etaShareText()?.let { t ->
                            val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, t)
                            try { shareCtx.startActivity(android.content.Intent.createChooser(i, L.t("Send arrival time")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (x: Exception) { }
                        }
                    }
                    act(Icons.Rounded.Route, L.t("Overview"), c.accent) { engine.overview(); open = false }
                    act(Icons.Rounded.Campaign, L.t("Report"), Warn) { open = false; onReport() }
                    act(Icons.Rounded.Layers, L.t("Map options"), c.text) { open = false; onOptions() }
                    if (evPct != null) act(Icons.Rounded.EvStation, L.t("Chargers ahead"), Good) { engine.loadNearby("charging", false); open = false }
                }
                // the next few turns
                val rows = engine.itinerary().take(4)
                if (rows.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.tile)) {
                    rows.forEachIndexed { i, row ->
                        if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(c.line))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            ManeuverArrow(row.maneuver, Geo.maneuverAngle(row.maneuver), Modifier.size(24.dp), roundaboutExit = row.exit, roundaboutClockwise = engine.roundaboutClockwise, color = c.text)
                            Text(row.text, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 12.dp).weight(1f))
                            Text(Geo.fmtDist(row.dist, engine.imperial), color = c.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (media) MediaBar()
            }
        }
    }
}

/** The weather where you are and the cheapest of your fuel nearby, as two small chips in a row (each only when it's known). */
@Composable
private fun SheetGlance(engine: NavEngine) {
    val c = LocalVw.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val wx = engine.weather
    val fuel = engine.prefs.s(PreferenceKeys.FUEL_TYPE, "E10")
    val here = engine.fix?.pos
    // (worked out away from the screen thread, again only after moving about half a kilometre)
    val cell = here?.let { "${Math.round(it.lat * 200)},${Math.round(it.lon * 200)}" }
    val cheapest by androidx.compose.runtime.produceState<Pair<com.headunit.launcher.FuelFinder.Station, Double>?>(null, cell, fuel) {
        value = if (here == null || fuel == "EV" || !com.headunit.launcher.FuelFinder.configured(engine.prefs)) null
        else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.headunit.launcher.FuelFinder.stations(ctx).asSequence().filter { !it.motorway && Geo.dist(here, it.pos) < 5000.0 }
                    .mapNotNull { s -> com.headunit.launcher.FuelFinder.priceOf(ctx, s.id, fuel)?.let { s to it.pence } }
                    .minByOrNull { it.second }
            } catch (e: Exception) { null }
        }
    }
    if (wx == null && cheapest == null) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        wx?.let { w ->
            val f = isImperial(engine.prefs)
            val t = if (f) Math.round(w.tempC * 9 / 5 + 32) else Math.round(w.tempC)
            Row(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(c.tile).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(wxIcon(w.code), null, tint = wxTint(w.code), modifier = Modifier.size(20.dp))
                Text("$t°" + (if (f) "F" else "C"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
            }
        }
        cheapest?.let { (s, p) ->
            Row(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(c.tile)
                .clickable { engine.startRoute(com.headunit.launcher.Place(s.brand.ifBlank { s.name }, s.postcode, s.pos)) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocalGasStation, null, tint = Color(0xFFEA4335), modifier = Modifier.size(20.dp))
                Text(String.format(java.util.Locale.UK, "%.1fp", p), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                Text(" · " + s.brand.ifBlank { s.name } + " · " + Geo.fmtDist(Geo.dist(here ?: s.pos, s.pos), engine.imperial), color = c.sub, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

/**
 * "Leave by 08:04 for Work": from 45 minutes before the time you usually set off for Home or Work (learnt from your trips there)
 * until 15 minutes after, while you're not already there; earlier by any traffic on the way just now.
 */
@Composable
private fun DepartureNudge(engine: NavEngine) {
    val c = LocalVw.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val here = engine.fix?.pos ?: return
    // (looked at again each minute)
    val minute by androidx.compose.runtime.produceState(System.currentTimeMillis() / 60_000L) { while (true) { delay(30_000L); value = System.currentTimeMillis() / 60_000L } }
    val pick = remember(minute, engine.slotMins["home"], engine.slotMins["work"]) {
        val cal = java.util.Calendar.getInstance()
        val now = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        listOf("work", "home").firstNotNullOfOrNull { k ->
            val p = engine.places.slot(k) ?: return@firstNotNullOfOrNull null
            if (Geo.dist(here, p.pos) < 800.0) return@firstNotNullOfOrNull null
            val usual = com.headunit.launcher.JourneyTimes.usualStart(ctx, p.pos) ?: return@firstNotNullOfOrNull null
            val delayMin = engine.slotDelay[k] ?: 0
            val leave = usual - delayMin
            if (now in (leave - 45)..(leave + 15)) Triple(k, p, leave) else null
        }
    } ?: return
    val (k, p, leave) = pick
    val mins = engine.slotMins[k]
    val d = engine.slotDelay[k] ?: 0
    val (icon, tint) = if (k == "home") Icons.Rounded.Home to Color(0xFF4C8DFF) else Icons.Rounded.Work to Color(0xFFF29900)
    val at = String.format(java.util.Locale.UK, "%02d:%02d", ((leave % 1440) + 1440) % 1440 / 60, ((leave % 60) + 60) % 60)
    Row(Modifier.fillMaxWidth().panel(6.dp, RoundedCornerShape(22.dp), c.surface).clickable { engine.startRoute(p) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(L.t("Leave by {0} for {1}", at, L.t(if (k == "home") "Home" else "Work")), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text((if (mins != null && mins > 0) Geo.fmtDur(mins * 60.0) + " · " else "") + (if (d >= 1) L.t("+{0} min traffic", d) else L.t("No delays")),
                color = delayTint(d, c.surface), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Icon(Icons.Rounded.Navigation, null, tint = c.accent, modifier = Modifier.size(22.dp))
    }
}

/** You're outside every map you've downloaded: a line to get this area (only when you have downloaded some). */
@Composable
private fun OfflineHealth(engine: NavEngine, onOffline: () -> Unit) {
    val c = LocalVw.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val here = engine.fix?.pos ?: return
    val cell = "${Math.round(here.lat * 20)},${Math.round(here.lon * 20)}"
    val outside by androidx.compose.runtime.produceState(false, cell) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try { com.headunit.launcher.OfflineRouter.installedIds(ctx).isNotEmpty() && com.headunit.launcher.OfflineRouter.areaFor(ctx, here) == null } catch (e: Exception) { false }
        }
    }
    if (!outside) return
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Warn.copy(alpha = 0.14f)).clickable { onOffline() }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.CloudDownload, null, tint = Warn, modifier = Modifier.size(20.dp))
        Text(L.t("You're outside your downloaded maps"), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp).weight(1f))
        Text(L.t("Get this area"), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** The drive to Home and Work just now, with any traffic on the way, a line each (tap to go). */
@Composable
private fun SlotTraffic(engine: NavEngine) {
    val c = LocalVw.current
    val rows = listOf("home" to Pair(Icons.Rounded.Home, Color(0xFF4C8DFF)), "work" to Pair(Icons.Rounded.Work, Color(0xFFF29900)))
        .mapNotNull { (k, look) -> val m = engine.slotMins[k] ?: return@mapNotNull null; val p = engine.places.slot(k) ?: return@mapNotNull null; if (m <= 0) null else Triple(k, look, Pair(m, p)) }
    if (rows.isEmpty()) return
    Column(Modifier.fillMaxWidth().panel(6.dp, RoundedCornerShape(22.dp), c.surface).padding(vertical = 4.dp)) {
        Text(L.t("Traffic now"), color = c.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 2.dp))
        for ((k, look, mp) in rows) {
            val (m, p) = mp
            val d = engine.slotDelay[k] ?: 0
            Row(Modifier.fillMaxWidth().clickable { engine.startRoute(p) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(look.second.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(look.first, null, tint = look.second, modifier = Modifier.size(18.dp)) }
                Text(L.t(if (k == "home") "Home" else "Work"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp).weight(1f))
                Text(Geo.fmtDur(m * 60.0), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(if (d >= 1) "  " + L.t("+{0} min traffic", d) else "  " + L.t("No delays"), color = delayTint(d, c.surface), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * The portrait sheet with no route: a handle, the road you're on while driving, the music, Home, Work and your places (the same
 * tiles and buttons as under the search bar, in their own colours), any notices (a planned trip, updates), and pulled up (or the
 * handle tapped) your recent places. While you drive it stays folded.
 */
@Composable
private fun HomeSheet(engine: NavEngine, media: Boolean, screenH: Dp, onOffline: () -> Unit, focusSearch: () -> Unit) {
    val c = LocalVw.current
    var open by rememberSaveable { mutableStateOf(false) }
    val cruising = engine.cruising
    // moving the map or driving folds it; a route you can carry on with opens it, so that card is seen
    LaunchedEffect(engine.homeTucked, cruising) { if (engine.homeTucked || cruising) open = false }
    LaunchedEffect(engine.resume != null) { if (engine.resume != null && !cruising) open = true }
    // a place sent from your phone opens it too, so it's seen straight away
    LaunchedEffect(engine.phoneDest) { if (engine.phoneDest != null && !cruising) open = true }
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    // a swipe up or down anywhere on the sheet opens or folds it (only on the handle before, and a swipe elsewhere went through to
    // the map, which moved it and so folded the sheet straight back); the sheet also keeps taps and drags off the map under it
    Column(Modifier.fillMaxWidth().panel(12.dp, shape, c.surface)
        .pointerInput(cruising) {
            var dragged = 0f
            detectVerticalDragGestures(onDragStart = { dragged = 0f }, onDragEnd = {
                if (dragged < -40f && !cruising) { open = true; engine.homeTucked = false } else if (dragged > 40f) open = false
            }) { ch, dy: Float -> ch.consume(); dragged += dy }
        }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
        .navigationBarsPadding()
        .padding(start = 20.dp, end = 20.dp, bottom = 16.dp).animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // the handle: tap to open or fold
        Box(Modifier.fillMaxWidth().height(24.dp).clickable { if (!cruising) { open = !open; if (open) engine.homeTucked = false } }, contentAlignment = Alignment.Center) {
            Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.line))
        }
        if (engine.cruisePillWanted) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CruisePill(engine) }
        if (media) MediaBar()
        // around the time you usually set off for Home or Work: when to leave today, with the traffic
        DepartureNudge(engine)
        // at a glance: the weather now and the cheapest fuel near you
        SheetGlance(engine)
        ImmChips(engine, focusSearch)
        // where you left the car, while you're away from it
        if (!cruising) ParkedCard(engine)
        // outside every map you've downloaded (only when you have some): a line to get this area
        if (!cruising) OfflineHealth(engine, onOffline)
        if (!cruising) {
            PlannedCard(engine)
            AppUpdateCard(engine)
            if (engine.dataUpdates.isNotEmpty()) DataUpdateCard(engine) else PackAgeCard(engine)
        }
        AnimatedVisibility(open && !cruising, enter = fadeIn(tween(200)) + androidx.compose.animation.expandVertically(tween(240)),
            exit = fadeOut(tween(140)) + androidx.compose.animation.shrinkVertically(tween(200))) {
            Column(Modifier.fillMaxWidth().heightIn(max = screenH * 0.42f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // a place sent from your phone, then the traffic to Home and Work now, then your recent places
                engine.phoneDest?.let { pd -> PhoneDestCard(engine, pd) }
                SlotTraffic(engine)
                ImmQuick(engine, showSaved = true, listAlways = true, parked = false) }
        }
    }
}

@Composable
private fun ImmSearch(engine: NavEngine, q: String, onQ: (String) -> Unit, locked: Boolean, focus: FocusRequester, voice: () -> Unit, onSettings: (() -> Unit)? = null, showFold: Boolean = true) { TextSides {
    val c = LocalVw.current
    val shape = RoundedCornerShape(30.dp)
    Box(Modifier.fillMaxWidth().height(60.dp).panel(10.dp, shape, c.surface)) {
        TextField(
            value = q, onValueChange = onQ, enabled = !locked,
            placeholder = { Text(if (locked) L.t("Tap the mic to search while driving") else L.t("Where to?"), fontSize = if (locked) 15.sp else 18.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = c.sub) },
            trailingIcon = {
                if (q.isNotEmpty()) Icon(Icons.Rounded.Close, L.t("Clear"), tint = c.sub, modifier = Modifier.padding(end = 8.dp).clickable { onQ(""); engine.results = emptyList() })
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    // folds Saved & recent up under the bar, and back down again
                    // (tucked away by moving the map: the arrow points down, and brings it all back)
                    val open = engine.prefs.b(PreferenceKeys.QUICKLIST, true) && !engine.homeTucked
                    // (the portrait sheet: Settings here instead, the places being in the sheet at the bottom)
                    if (onSettings != null) Box(Modifier.padding(end = 4.dp).size(40.dp).clip(CircleShape).clickable { onSettings() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Settings, L.t("Settings"), tint = c.sub, modifier = Modifier.size(24.dp))
                    } else if (showFold) Box(Modifier.padding(end = 4.dp).size(40.dp).clip(CircleShape).clickable {
                        if (engine.homeTucked) { engine.homeTucked = false; engine.prefs.put(PreferenceKeys.QUICKLIST, true) }
                        else engine.prefs.put(PreferenceKeys.QUICKLIST, !open)
                    }, contentAlignment = Alignment.Center) {
                        Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, if (open) L.t("Hide saved and recent") else L.t("Show saved and recent"), tint = c.sub, modifier = Modifier.size(26.dp))
                    }
                    Box(Modifier.padding(end = 8.dp).size(44.dp).clip(CircleShape).background(c.accent).pressClick { voice() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Mic, L.t("Voice search"), tint = Color.White, modifier = Modifier.size(23.dp))
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
            modifier = Modifier.fillMaxSize().focusRequester(focus).onFocusChanged { if (it.isFocused) engine.homeTucked = false },
        )
    }
} }

@Composable
private fun ImmResults(engine: NavEngine, q: String, onPicked: () -> Unit) {
    // (a place picked: the keyboard goes, so the place's card and the map have the screen)
    val kb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val c = LocalVw.current
    val shape = RoundedCornerShape(26.dp)
    val here = engine.fix?.pos
    // several branches of what was searched nearby (Lidl, Sainsbury's…): "See all N locations" at the top
    val chainN = remember(engine.results, q) { if (q.trim().length >= 2) engine.chainCount(q.trim()) else 0 }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).panel(10.dp, shape, c.surface).padding(vertical = 6.dp)) {
        if (chainN >= 2 && engine.results.isNotEmpty()) item { SeeAllRow(engine, q.trim(), chainN) { } }
        items(engine.results) { p ->
            Row(Modifier.fillMaxWidth().clickable { kb?.hide(); focus.clearFocus(); onPicked(); engine.openPlace(p) }.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                // a GPX track (GitHub #162): its pink route mark; otherwise the shop's own logo where there is one
                if (p.web.startsWith("gpx:")) Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFD4537E).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Route, null, tint = Color(0xFFD4537E), modifier = Modifier.size(22.dp))
                }
                else PlaceLogo(p, 42.dp) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(c.tile), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Place, null, tint = Color(0xFFEA4335), modifier = Modifier.size(22.dp))
                    }
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
        // Home and Work, then your own places and favourites in the same row, which scrolls sideways: so a saved place is still one
        // tap away while you drive, when the Saved & recent list has folded away (GitHub #143). With more than Home and Work, the
        // next tile peeks in at the edge so it's clear there's more. (Home and Work can each be left off: Settings › Places & trips)
        val showHome = engine.prefs.b(PreferenceKeys.HOME_BTN, true); val showWork = engine.prefs.b(PreferenceKeys.WORK_BTN, true)
        val rawCustom = engine.prefs.s(PreferenceKeys.PLACES_CUSTOM, "")
        val rawFav = engine.prefs.s(PreferenceKeys.PLACES_FAVOURITES, "")
        // every saved place lives in this row now (your own places, saved routes and favourites), and the list below is only
        // recent places; the row always ends with "Add a place"
        val rawRoutes = engine.prefs.s(PreferenceKeys.PLACES_ROUTES, "[]")
        val routes = remember(rawRoutes) { engine.savedRoutes() }
        val rowPlaces = engine.prefs.b(PreferenceKeys.ROW_PLACES, true)
        val extra: List<Pair<String, com.headunit.launcher.Place>> = remember(rawCustom, rawFav, rowPlaces) {
            val all = engine.places.customs()
            // (only the starred ones, and none with "Your places next to Home and Work" off: GitHub bijindas)
            val mine = if (rowPlaces) all.filter { it.pinned } else emptyList()
            val favs = engine.places.favourites().filter { f -> all.none { Geo.dist(it.place.pos, f.pos) < 60 } &&
                listOfNotNull(home, work).none { Geo.dist(it.pos, f.pos) < 60 } }
            (mine.map { it.label to it.place } + favs.map { it.name to it }).take(40)
        }
        var adding by remember { mutableStateOf(false) }
        if (adding) AddPlaceDialog(engine, null) { adding = false }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val half = (maxWidth - 8.dp) / 2
            val fixed = (if (showHome) 1 else 0) + (if (showWork) 1 else 0)
            // (with more than two tiles, the next one peeks in at the edge so it's clear the row scrolls)
            val tileW = if (fixed + extra.size + routes.size + 1 <= 2) half else half - 22.dp
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showHome) PlaceTile(engine, Modifier.width(tileW), Icons.Rounded.Home, Color(0xFF4C8DFF), L.t("Home"), home, engine.slotMins["home"]) {
                    if (home != null) engine.startRoute(home) else { engine.pendingSave = "home"; engine.toast(L.t("Search for your home address"), 3500); focusSearch() }
                }
                if (showWork) PlaceTile(engine, Modifier.width(tileW), Icons.Rounded.Work, Color(0xFFF29900), L.t("Work"), work, engine.slotMins["work"]) {
                    if (work != null) engine.startRoute(work) else { engine.pendingSave = "work"; engine.toast(L.t("Search for your work address"), 3500); focusSearch() }
                }
                for ((label, pl) in extra) PlaceTile(engine, Modifier.width(tileW), Icons.Rounded.Star, Color(0xFF12A4B4), label, pl, null) { engine.startRoute(pl) }
                for (sr in routes) RouteTile(engine, Modifier.width(tileW), sr)
                AddPlaceTile(Modifier.width(tileW)) { adding = true }
            }
        }
        // things to find nearby: the ones picked in Settings › Places & trips (up to five, all on show, no scrolling sideways);
        // as before when none are picked (Charging and Toilets in place of Fuel for an electric car, lorry parks for a lorry)
        val picks = chosenShortcuts(engine)
        if (picks.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = if (picks.size >= 4) Arrangement.SpaceBetween else Arrangement.spacedBy(14.dp)) {
            for (s in picks) FindButton(s.icon, s.color, L.t(s.desc)) { engine.loadNearby(s.kind, false) }
        }
    }
}

/** A saved route in the row under the search bar: its name, and "Saved route" (with its stops). */
@Composable
private fun RouteTile(engine: NavEngine, modifier: Modifier, sr: NavEngine.SavedRoute) {
    val c = LocalVw.current
    Row(modifier.height(58.dp).panel(6.dp, RoundedCornerShape(20.dp), c.surface).pressClick { engine.startSavedRoute(sr) }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Route, null, tint = c.accent, modifier = Modifier.size(20.dp)) }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(sr.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(when (sr.stops.size) { 0 -> L.t("Saved route"); 1 -> L.t("Saved route · 1 stop"); else -> L.t("Saved route · {0} stops", sr.stops.size) },
                color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Always last in the row under the search bar: save a new place. */
@Composable
private fun AddPlaceTile(modifier: Modifier, onClick: () -> Unit) {
    val c = LocalVw.current
    Row(modifier.height(58.dp).panel(6.dp, RoundedCornerShape(20.dp), c.surface).border(1.5.dp, c.accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp)).pressClick(onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = c.accent, modifier = Modifier.size(22.dp)) }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(L.t("Add a place"), color = c.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(L.t("Save somewhere"), color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PlaceTile(engine: NavEngine, modifier: Modifier, icon: ImageVector, tint: Color, label: String, place: com.headunit.launcher.Place?, mins: Int?, onClick: () -> Unit) {
    val c = LocalVw.current
    val here = engine.fix?.pos
    val sub = when {
        place == null -> L.t("Tap to set")
        mins == 0 -> L.t("You're here")
        mins != null && mins > 0 -> Geo.fmtDur(mins * 60.0)
        here != null -> Geo.fmtDist(Geo.dist(here, place.pos), engine.imperial)
        else -> place.sub
    }
    Row(modifier.height(58.dp).panel(6.dp, RoundedCornerShape(20.dp), c.surface).pressClick(onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(if (place == null) L.t("Set {0}", if (L.en) label.lowercase() else label) else label, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Text(L.t("From your phone: {0}", p.name), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // the whole trip came along: say how many stops, so it's clear they're coming too
            val n = engine.phoneStops.size
            Text(L.t("Starting in {0} s", left) + (if (n == 1) " · " + L.t("1 stop") else if (n > 1) " · " + L.t("{0} stops", n) else ""), color = c.sub, fontSize = 13.sp)
        }
        ImmPill(L.t("Go"), { engine.takePhoneDest(true) }, filled = true)
        ImmPill(L.t("Not now"), { engine.takePhoneDest(false) })
    }
}

/** Where you parked (saved on the phone when Android Auto disconnects): walk there, or clear it. Shown for 2 days. */
@Composable
private fun ParkedCard(engine: NavEngine) {
    val c = LocalVw.current
    val raw = engine.prefs.s(PreferenceKeys.PARKED_SPOT, "")
    val parts = raw.split(',')
    val spot = parts.getOrNull(0)?.toDoubleOrNull()?.let { la -> parts.getOrNull(1)?.toDoubleOrNull()?.let { lo -> LL(la, lo) } }
    val at = parts.getOrNull(2)?.toLongOrNull()
    val ageMin = if (at != null) ((System.currentTimeMillis() - at) / 60_000L).coerceAtLeast(0L) else 0L
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val here = engine.fix?.pos
    if (spot == null || at == null || ageMin > 48 * 60) {
        // nothing saved (or too old): a button to save where you are now (joel941703-hash, #359)
        if (here == null) return
        Row(Modifier.fillMaxWidth().panel(10.dp, RoundedCornerShape(22.dp), c.surface).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.DirectionsCar, null, tint = c.accent, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(L.t("Your car"), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(L.t("Not saved yet"), color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ImmPill(L.t("Park here"), {
                engine.fix?.pos?.let { p ->
                    engine.prefs.put(PreferenceKeys.PARKED_SPOT, String.format(java.util.Locale.US, "%.6f,%.6f,%d", p.lat, p.lon, System.currentTimeMillis()))
                    engine.toast(L.t("Saved where you parked"), 2500)
                }
            }, filled = true)
        }
        return
    }
    // (not while you're still at the car)
    if (here != null && Geo.dist(here, spot) < 80.0) return
    val ago = when { ageMin < 1 -> L.t("just now"); ageMin < 60 -> L.t("{0} min ago", ageMin); ageMin < 24 * 60 -> L.t("{0} h ago", ageMin / 60); else -> "yesterday" }
    Row(Modifier.fillMaxWidth().panel(10.dp, RoundedCornerShape(22.dp), c.surface).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.DirectionsCar, null, tint = c.accent, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(L.t("Your car"), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(L.t("Parked {0}", ago) + (if (here != null) " · " + Geo.fmtDist(Geo.dist(here, spot), engine.imperial) else ""), color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // walking directions in the phone's maps app (Horizon Nav plans driving routes); just the spot if there's none
        ImmPill(L.t("Walk back"), {
            val walk = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(String.format(java.util.Locale.US, "google.navigation:q=%.6f,%.6f&mode=w", spot.lat, spot.lon)))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            val geo = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(String.format(java.util.Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f(Your car)", spot.lat, spot.lon, spot.lat, spot.lon)))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            // (Google Maps by name: on some phones the request went to a driving app, which drove you round to the car, Dotancom
            // #320; then Google's walking directions on the web, then just the spot)
            val web = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(String.format(java.util.Locale.US, "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=walking", spot.lat, spot.lon)))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            val tries = listOf(android.content.Intent(walk).setPackage("com.google.android.apps.maps"), web.setPackage("com.google.android.apps.maps"),
                android.content.Intent(web).setPackage(null), geo)
            if (tries.none { t -> try { ctx.startActivity(t); true } catch (e: Exception) { false } }) engine.toast(L.t("No walking maps app on this phone"), 3000)
        }, filled = true)
        Box(Modifier.size(40.dp).clip(CircleShape).clickable { engine.prefs.put(PreferenceKeys.PARKED_SPOT, "") }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, L.t("Forget where you parked"), tint = c.sub, modifier = Modifier.size(22.dp))
        }
    }
}

/** Resume a trip, a suggested trip, and saved & recent places. */
@Composable
private fun ImmQuick(engine: NavEngine, showSaved: Boolean = true, listAlways: Boolean = false, parked: Boolean = true) {
    val c = LocalVw.current
    val rawCustom = engine.prefs.s(PreferenceKeys.PLACES_CUSTOM, "")
    val rawFav = engine.prefs.s(PreferenceKeys.PLACES_FAVOURITES, "")
    val rawRecent = engine.prefs.s(PreferenceKeys.PLACES_RECENT, "")
    val rawRoutes = engine.prefs.s(PreferenceKeys.PLACES_ROUTES, "[]")
    val routes = remember(rawRoutes) { engine.savedRoutes() }
    val rawJourneyHistory = engine.prefs.s("journey_history", "[]")
    val journeyHistory = remember(rawJourneyHistory) { engine.journeyHistory() }
    // (kind 0 = one of your own labelled places, 1 = a favourite, 2 = recent, 3 = a saved route, 4 = a group of your places; the
    // label is the name shown). A group is one row that opens to its places (GitHub #138).
    var openGroup by rememberSaveable { mutableStateOf("") }
    val groupCount = remember(rawCustom) { engine.places.customs().filter { it.group.isNotBlank() }.groupingBy { it.group }.eachCount() }
    val inOpen = remember(rawCustom, openGroup) { engine.places.customs().filter { it.group == openGroup && openGroup.isNotEmpty() }.map { it.label }.toSet() }
    val saved: List<Triple<com.headunit.launcher.Place, Int, String>> = remember(rawCustom, rawFav, rawRecent, routes, rawJourneyHistory, journeyHistory, openGroup) {
        val mine = engine.places.customs()
        val favs = engine.places.favourites().filter { f -> mine.none { Geo.dist(it.place.pos, f.pos) < 60 } }
        val recents = engine.places.recents().filter { r -> favs.none { Geo.dist(it.pos, r.pos) < 60 } && mine.none { Geo.dist(it.place.pos, r.pos) < 60 } }
        val grouped = mine.map { it.group }.filter { it.isNotBlank() }.distinct().flatMap { g ->
            val members = mine.filter { it.group == g }
            listOf(Triple(members.first().place, 4, g)) + (if (g == openGroup) members.map { Triple(it.place, 0, it.label) } else emptyList())
        }
        // (only recent places: everything saved is in the row of tiles above, which ends with "Add a place")
        @Suppress("UNUSED_VARIABLE") val unusedGroups = grouped
        journeyHistory.map { Triple(it.dest, 5, it.id.toString()) } + recents.map { Triple(it, 2, it.name) }
    }
    var adding by remember { mutableStateOf(false) }
    if (adding) AddPlaceDialog(engine, null) { adding = false }
    val here = engine.fix?.pos
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (parked) ParkedCard(engine)
        engine.resume?.let { rp ->
            Row(
                Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(TealDeep).clickable { engine.startRoute(rp) }.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Route, null, tint = Color.White, modifier = Modifier.size(26.dp))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(L.t("Resume trip to {0}", rp.name), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(L.t("You hadn't arrived when the app closed"), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                Icon(Icons.Rounded.Close, L.t("Dismiss"), tint = Color.White, modifier = Modifier.padding(start = 8.dp).size(24.dp).clickable { engine.resume = null; engine.prefs.put(PreferenceKeys.RESUME, "{}") })
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
                Text(listOfNotNull(L.t(pr.title), if (pr.mins >= 0) Geo.fmtDur(pr.mins * 60.0) else null, L.t(pr.note).ifBlank { null }).joinToString(" · "),
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val arrive = if (pr.mins >= 0) L.t("Arrive about ") + com.headunit.launcher.Clock.hm(System.currentTimeMillis() + pr.mins * 60_000L) else pr.place.sub
                Text(if (pr.delayMin >= 3) L.t("{0} · +{1} min delays", arrive, pr.delayMin) else arrive, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(L.t("Go"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        // (Settings "Suggest where you're going" switches this one off too: it used to stay on regardless, GitHub #73)
        val suggestion = remember { engine.habits.suggest(System.currentTimeMillis()) }?.takeIf { engine.prefs.b(PreferenceKeys.PREDICT, true) }
        if (pr == null && suggestion != null) Row(
            Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Teal).clickable { engine.startRoute(suggestion) }.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.History, null, tint = Color.White, modifier = Modifier.size(26.dp))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(L.t("Going to {0}?", suggestion.name), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (suggestion.sub.isNotBlank()) Text(suggestion.sub, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(L.t("Go"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        // three places on show to keep the map clear; "All" opens the rest (it used to be a list up to five tall)
        AnimatedVisibility(showSaved && (listAlways || engine.prefs.b(PreferenceKeys.QUICKLIST, true)),
            enter = fadeIn(tween(200)) + androidx.compose.animation.expandVertically(tween(240)), exit = fadeOut(tween(140)) + androidx.compose.animation.shrinkVertically(tween(200))) {
        Column(Modifier.fillMaxWidth().panel(10.dp, RoundedCornerShape(26.dp), c.surface).padding(vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(L.t("Recent").uppercase(L.locale), color = c.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
            }
            if (saved.isEmpty()) Text(L.t("Places you drive to show up here"), color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 8.dp))
            LazyColumn(Modifier.heightIn(max = 250.dp)) {
                // (all of them, scrolling within the panel: the "All 6" / "Less" switch is gone)
                items(saved) { (p, kind, label) ->
                    if (kind == 4) {
                        val open = label == openGroup
                        Row(Modifier.fillMaxWidth().clickable { openGroup = if (open) "" else label }.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(c.tile), contentAlignment = Alignment.Center) {
                                Icon(if (open) Icons.Rounded.FolderOpen else Icons.Rounded.Folder, null, tint = c.accent, modifier = Modifier.size(22.dp))
                            }
                            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                Text(label, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val n = groupCount[label] ?: 0
                                Text(if (n == 1) L.t("1 place") else L.t("{0} places", n), color = c.sub, fontSize = 13.sp, maxLines = 1)
                            }
                            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = c.sub, modifier = Modifier.size(24.dp))
                        }
                        return@items
                    }
                    val fav = kind == 0 || kind == 1
                    val sr = if (kind == 3) routes.firstOrNull { it.dest === p && it.name == label } else null
                    val jr = if (kind == 5) journeyHistory.firstOrNull { it.id.toString() == label } else null
                    // swipe a place sideways to take it off the list (a favourite is unstarred, a recent one forgotten, your own place removed)
                    var dx by remember { mutableStateOf(0f) }
                    var w by remember { mutableStateOf(1) }
                    val shown by androidx.compose.animation.core.animateFloatAsState(dx, label = "swipe")
                    Row(Modifier.fillMaxWidth().onSizeChanged { w = it.width }
                        .pointerInput(p, kind) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (kotlin.math.abs(dx) > w * 0.35f) when (kind) {
                                        0 -> { engine.places.removeCustom(label); engine.toast(L.t("Removed {0}", label), 2000) }
                                        3 -> { sr?.let { engine.deleteSavedRoute(it) }; engine.toast(L.t("Removed from saved routes"), 2000) }
                                        5 -> { jr?.let { engine.removeJourneyHistory(it) }; engine.toast(L.t("Removed from recent places"), 2000) }
                                        1 -> { engine.places.toggleFavourite(p); engine.toast(L.t("Removed from favourites"), 2000) }
                                        else -> { engine.places.removeRecent(p); engine.toast(L.t("Removed from recent places"), 2000) }
                                    }
                                    dx = 0f
                                },
                                onDragCancel = { dx = 0f },
                            ) { change, amount -> change.consume(); dx += amount }
                        }
                        .graphicsLayer { translationX = shown; alpha = 1f - (kotlin.math.abs(shown) / w.toFloat()).coerceIn(0f, 0.7f) }
                        .clickable { when { sr != null -> engine.startSavedRoute(sr); jr != null -> engine.startJourneyHistory(jr); else -> engine.startRoute(p) } }
                        .padding(start = if (kind == 0 && label in inOpen) 34.dp else 16.dp, end = 16.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(c.tile), contentAlignment = Alignment.Center) {
                            if (kind == 3 || kind == 5) Icon(Icons.Rounded.Route, null, tint = c.accent, modifier = Modifier.size(22.dp))
                            else Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.History, null, tint = if (fav) Warn else c.sub, modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(if (jr != null) p.name else label, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (sr != null) Text(when (sr.stops.size) { 0 -> L.t("Saved route"); 1 -> L.t("Saved route · 1 stop"); else -> L.t("Saved route · {0} stops", sr.stops.size) },
                                color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            else if (jr != null) Text(L.t(if (jr.stops.size == 1) "1 stop" else "{0} stops", jr.stops.size),
                                color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            else if (p.sub.isNotBlank()) Text(p.sub, color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                Text(L.t("Your route"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                val e = engine.eta
                Text("${rows.size} ${if (rows.size == 1) "step" else "steps"}" + (if (e != null) " · " + Geo.fmtDist(e.dist, engine.imperial) + L.t(" · arrive ") + e.arrive else ""),
                    color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(TealDeep).clickable { engine.closeItinerary() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, L.t("Close"), tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).background(TealDeep), state = listState) {
            items(rows, key = { it.index }) { row ->
                val sel = picked == row.index
                Column(Modifier.fillMaxWidth().background(if (sel) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable { touched++; picked = row.index; engine.previewTurn(row) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ManeuverArrow(row.maneuver, Geo.maneuverAngle(row.maneuver), Modifier.size(30.dp), roundaboutExit = row.exit, roundaboutClockwise = engine.roundaboutClockwise)
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(row.text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(row.tag.ifBlank { null }, "about " + row.arrive).joinToString(" · "), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1)
                        }
                        Text(Geo.fmtDist(row.dist, engine.imperial), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
                    }
                    if (sel && row.road.isNotBlank()) Row(Modifier.padding(start = 44.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(L.t("Avoid {0}", row.road), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f)).clickable { engine.avoidTurnRoad(row) }.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
            }
        }
    }
}

/** The routes box (Other routes): your route first, then each other way, with how it compares. */
@Composable
internal fun RoutesBox(engine: NavEngine, modifier: Modifier = Modifier, sheet: Boolean = false, maxH: androidx.compose.ui.unit.Dp? = null) {
    val c = LocalVw.current
    val eta = engine.eta
    val alts = engine.liveAlts
    // on the car screen taps come through Android Auto (see CarTaps), not as touches on the box
    val car = engine.isCar
    // sheet: a phone held upright, where it's a solid panel along the bottom (the see-through one let the map's names show through it)
    val boxMod = if (sheet) modifier.fillMaxWidth().panel(12.dp, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), c.surface.copy(alpha = 1f))
        .pointerInput(Unit) { detectTapGestures { } }.padding(top = 6.dp, bottom = 10.dp)
        // maxH: a phone held sideways, where it's no taller than the screen (the routes scroll inside it) and a little narrower
        else modifier.width(if (maxH != null) 300.dp else 340.dp).then(if (maxH != null) Modifier.heightIn(max = maxH) else Modifier)
            .panel(10.dp, RoundedCornerShape(22.dp), c.surface).padding(vertical = 8.dp)
    val picked = engine.altPicked
    Column(boxMod) {
        if (sheet) Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 2.dp).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.line))
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(L.t("Routes"), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).pressClick { engine.closeAlternatives() }
                .then(if (car) Modifier.carTap("alt-close") { engine.closeAlternatives() } else Modifier), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, L.t("Close"), tint = c.sub, modifier = Modifier.size(22.dp))
            }
        }
        // the routes scroll when there isn't room for them all (a phone held sideways); the title and close button stay put.
        // (Only then: the car screen measures the box with no height limit, where a scrolling list would crash)
        Column(if (maxH != null) Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()) else Modifier) {
        // yours
        if (eta != null) RouteRow(
            title = Geo.fmtDur(eta.secs), titleCol = c.text,
            line2 = L.t("Arrive {0} · {1}", eta.arrive, Geo.fmtDist(eta.dist, engine.imperial)),
            line3 = L.t("Your route") + (if (eta.delayMin >= 2) L.t(" · {0} min in traffic", eta.delayMin) else ""), selected = true,
            tap = if (car) "alt-mine" else null,
        ) { engine.closeAlternatives() }
        for ((i, a) in alts.withIndex()) {
            val secs = (eta?.secs ?: 0.0) + a.diffMin * 60.0
            val arrive = com.headunit.launcher.Clock.hm(System.currentTimeMillis() + (secs * 1000).toLong())
            // distance: what's left of yours up to where it turns off, then the rest of its own
            val split = a.route.cum.getOrNull(a.divIdx) ?: 0.0
            val toSplit = (eta?.dist ?: 0.0) - ((engine.route?.len ?: 0.0) - a.divergeOff)
            val dist = (toSplit.coerceAtLeast(0.0) + (a.route.len - split)).coerceAtLeast(0.0)
            val via = a.route.steps.firstOrNull { it.off > split && it.street.isNotBlank() }?.street
            val diff = when { a.diffMin < 0 -> L.t("{0} min faster", -a.diffMin); a.diffMin > 0 -> L.t("{0} min slower", a.diffMin); else -> L.t("Same time") }
            val traffic = when (a.traffic) { -1 -> L.t("Less traffic"); 1 -> L.t("More traffic"); else -> L.t("Similar traffic") }
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(c.line))
            RouteRow(
                title = "${Geo.fmtDur(secs)}  ·  $diff",
                titleCol = if (a.diffMin < 0) Good else if (a.diffMin > 0) Warn else c.text,
                line2 = L.t("Arrive {0} · {1}", arrive, Geo.fmtDist(dist, engine.imperial)),
                line3 = (if (via != null) L.t("Via {0} · ", via) else "") + traffic, selected = false,
                tap = if (car) "alt-$i" else null, picked = picked != null && picked === a,
            ) { engine.tapAlt(i) }
        }
        if (alts.isEmpty()) Text(
            if (engine.altSearching) L.t("Looking for other routes…") else L.t("No other routes right now"),
            color = c.sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        )
        else Text(L.t("Tap a route to see it, then tap it again to switch"), color = c.sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun RouteRow(title: String, titleCol: Color, line2: String, line3: String, selected: Boolean, tap: String? = null, picked: Boolean = false, onClick: () -> Unit) {
    val c = LocalVw.current
    Row(
        Modifier.fillMaxWidth().pressClick(onClick).then(if (tap != null) Modifier.carTap(tap, onClick) else Modifier)
            // tapped once: a yellow tint, as its line on the map, until it's tapped again to switch
            .then(if (picked) Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(12.dp)).background(PickedAlt.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 10.dp)
                  else Modifier.padding(horizontal = 18.dp, vertical = 10.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(46.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) c.accent else if (picked) PickedAlt else c.line))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = titleCol, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(line2, color = c.text, fontSize = 13.sp, maxLines = 1)
            Text(line3, color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (picked) Text(L.t("Tap again to switch"), color = PickedAlt, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** The other route tapped once: the same yellow as its line on the map. */
private val PickedAlt = Color(0xFFF0B429)


/** Arabic, Hebrew and Urdu: the inside of a card reads right to left (arrow on the right, text right-aligned). */
@Composable
internal fun TextSides(content: @Composable () -> Unit) {
    if (!L.rtl) { content(); return }
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) { content() }
}

/** The map screen's own layout: where the cards and buttons sit follows the driving side setting, not the language. */
@Composable
internal fun FixedSides(content: @Composable () -> Unit) {
    if (!L.rtl) { content(); return }
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) { content() }
}

/**
 * A road name on the turn card, with UK road numbers drawn as small signs (karthikeyansr): motorways blue with white, A roads green
 * with yellow, B roads white with black, as on the real signs. Only in the UK ([badges]); elsewhere, and for roads with no number,
 * it's the plain white text it always was.
 */
@Composable
internal fun RoadLine(text: String, badges: Boolean, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier, ref: String = "") {
    if (ref.isNotBlank() && !badges && text.isNotBlank()) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PlainRefChip(ref, size)
            Text(text, color = Color.White, fontSize = size, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        return
    }
    val refs = if (badges) roadRefRx.findAll(text).toList().take(2) else emptyList()
    if (refs.isEmpty()) { Text(text, color = Color.White, fontSize = size, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier); return }
    // what's left once the numbers are taken out (and the brackets and dots that went with them)
    val rest = roadRefRx.replace(text, "").replace("()", "").replace(Regex("\\s*[·/,]\\s*(?=[·/,]|$)"), "").replace(Regex("^\\s*[·/,]\\s*"), "").trim()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (m in refs) RoadRefChip(roadRefOf(m), size)
        if (rest.isNotBlank()) Text(rest, color = Color.White, fontSize = size, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// UK road numbers: M25, A303, B3065, A1(M), and "M 25" written with a space (it showed with no badge). Not part of a longer word
// or number (the old \b after "(M)" never matched, so A1(M) came out as a green A1 with "(M)" trailing after it)
private val roadRefRx = Regex("(?<![A-Za-z0-9])((?:M ?|[AB])[0-9]{1,4})(\\s?\\(M\\))?(?![A-Za-z0-9])")
private fun roadRefOf(m: MatchResult): String = m.groupValues[1].replace(" ", "") + (if (m.groupValues[2].isNotBlank()) "(M)" else "")

/** A road number from outside the UK (169, E39): a plain white badge, as the UK ones are for B roads. */
@Composable
private fun PlainRefChip(r: String, size: androidx.compose.ui.unit.TextUnit) {
    Text(r, color = Color(0xFF111111), fontSize = size * 0.85f, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White).border(1.5.dp, Color(0xFF111111), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
}

/** One UK road-number badge: blue for motorways (M or (M)), green with yellow for A roads, white for B roads. */
@Composable
private fun RoadRefChip(r: String, size: androidx.compose.ui.unit.TextUnit) {
    val mway = r.startsWith("M") || r.endsWith("(M)")
    val (bg, fg, edge) = when {
        mway -> Triple(Color(0xFF0065B3), Color.White, Color.White)
        r.startsWith("A") -> Triple(Color(0xFF00703C), Color(0xFFFFD200), Color.White)
        else -> Triple(Color.White, Color(0xFF111111), Color(0xFF111111))
    }
    Text(r, color = fg, fontSize = size * 0.85f, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(bg).border(1.5.dp, edge, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
}

/**
 * A line of words with the UK road numbers in it shown as badges where they stand ("Then turn left onto [A36]"), for the
 * turn card's "Then" line (karthikeyansr). The words shorten with "…" when space is short; the badges always show in full.
 */
@Composable
internal fun BadgedLine(text: String, badges: Boolean, size: androidx.compose.ui.unit.TextUnit, color: Color, modifier: Modifier = Modifier, maxLines: Int = 1) {
    val refs = if (badges) roadRefRx.findAll(text).toList().take(2) else emptyList()
    if (refs.isEmpty()) { Text(text, color = color, fontSize = size, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier); return }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        var at = 0
        for (m in refs) {
            val before = text.substring(at, m.range.first).trim()
            if (before.isNotEmpty()) Text(before, color = color, fontSize = size, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            RoadRefChip(roadRefOf(m), size)
            at = m.range.last + 1
        }
        val after = text.substring(at).trim()
        if (after.isNotEmpty()) Text(after, color = color, fontSize = size, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}


/**
 * The map options panel's brightness slider: the night map's brightness at night, the day map's by day. It's saved when the slider
 * is let go rather than at every step, as each change relights the map (and going back to exactly 100% reloads it).
 */
@Composable
private fun MapBrightRow(p: com.headunit.launcher.Prefs, night: Boolean) {
    val c = LocalVw.current
    val key = if (night) PreferenceKeys.NIGHT_BRIGHT else PreferenceKeys.DAY_BRIGHT
    val lo = if (night) 30f else 50f; val hi = if (night) 200f else 150f
    var v by remember(key) { mutableStateOf(p.i(key, 100).toFloat().coerceIn(lo, hi)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.BrightnessLow, null, tint = c.sub, modifier = Modifier.size(22.dp))
        androidx.compose.material3.Slider(
            value = v, onValueChange = { v = it }, valueRange = lo..hi,
            onValueChangeFinished = { p.put(key, (Math.round(v / 5f) * 5).coerceIn(lo.toInt(), hi.toInt())) },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = c.accent, inactiveTrackColor = c.tile),
        )
        Icon(Icons.Rounded.BrightnessHigh, null, tint = c.text, modifier = Modifier.size(22.dp))
        Text((if (night) L.t("Night") else L.t("Day")) + " · " + (Math.round(v / 5f) * 5) + "%", color = c.sub, fontSize = 14.sp, maxLines = 1,
            modifier = Modifier.padding(start = 10.dp).widthIn(min = 92.dp))
    }
}
