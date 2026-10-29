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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import com.headunit.launcher.NavEngine
import com.headunit.launcher.PreferenceKeys

/**
 * The app's own screens on the Android Auto display, drawn over the car's map with the app's designs: alerts across the top, the
 * turn card (top left), hazard badges, speed and the trip card (bottom left), the route choice before setting off, and the arrival
 * summary. [scale] makes them the same size on the car screen as in the app (car screens have few pixels per inch for their size).
 * Nothing here can be tapped (Android Auto only lets its own buttons take touches), so the matching buttons are Android Auto's.
 */
@Composable
fun CarOverlay(engine: NavEngine, area: State<android.graphics.Rect?>, dark: State<Boolean>, scale: State<Float>, actions: CarActions, onCover: (Int) -> Unit = {}) {
    val base = LocalDensity.current
    // [scale] is worked out from the car screen's size (see CarSurface); the Android Auto size setting adjusts it either way
    val user = engine.prefs.i(PreferenceKeys.CARSCALE, 100).coerceIn(40, 300) / 100f
    val textScale = engine.prefs.i(PreferenceKeys.CAR_TEXT_SIZE, 100).coerceIn(75, 150) / 100f
    CompositionLocalProvider(LocalDensity provides Density(base.density * scale.value * user, base.fontScale * textScale)) {
        VwTheme(dark.value) { CarScreen(engine, area.value, actions, onCover) }
    }
}

/** What taps on the app's cards do that needs Android Auto's own screens (search, the places list). */
interface CarActions {
    fun search()
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
        val speedScale = requestedSpeedScale * minOf(1f, (maxWidth.value / 220f).coerceAtLeast(0.25f), (maxHeight.value / 150f).coerceAtLeast(0.25f))
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
        val portrait = maxHeight > maxWidth * 1.1f || narrow
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
        val c = LocalVw.current
        val topW = if (portrait) {
            (maxWidth - left - rightTop).coerceAtLeast(0.dp).coerceAtMost(600.dp)
        } else {
            minOf(if (maxWidth < 1050.dp) 380.dp else 440.dp, maxWidth * 0.38f)
        }
        var turnH by remember { mutableStateOf(0) }
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

        // the turn card, top left (as in the app)
        val t = engine.turn
        val showTurn = guiding && t != null && !arriving
        if (showTurn && t != null) Box(Modifier.padding(start = left, top = top).width(if (portrait) topW else minOf(420.dp, maxWidth * 0.36f))
            .onSizeChanged { turnH = it.height }.onGloballyPositioned { if (topShown) report(0, it.boundsInRoot()) }) {
            // the turn card, with the junction sign or roundabout diagram under it and any advice for the conditions, as on the head unit
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
                // (the car's "Navigation arrow size" is the arrow on the map, as on the phone: CarNav applies it there)
                CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) {
                    ImmTurnCard(engine, t, compact = compact)
                }
                if (!compact) engine.advice?.let { AdvicePill(it) }
            }
        }

        // not navigating: the app's search bar and quick chips, top left (tap them as in the app)
        if (engine.route == null && !arriving) Column(Modifier.padding(start = left, top = top).width(topW).onSizeChanged { topBlockH = it.height }.onGloballyPositioned { if (topShown) report(0, it.boundsInRoot()) },
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().height(60.dp).panel(10.dp, RoundedCornerShape(30.dp), c.surface).carTap("search") { actions.search() }.padding(start = 18.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Search, null, tint = c.sub)
                Text("Where to?", color = c.sub, fontSize = 18.sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
                Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(23.dp))
                }
            }
            CarChips(engine, actions)
        }

        // alerts (cameras, limits, hazards...): beside the turn card on wide screens, under it on tall ones
        val alert = engine.alert
        val below = if (showTurn && turnH > 0) with(dens) { turnH.toDp() } + 10.dp else if (engine.route == null && topBlockH > 0) with(dens) { topBlockH.toDp() } + 10.dp else 0.dp
        val alertMod = if (portrait) Modifier.align(Alignment.TopStart).padding(start = left, end = rightTop + 64.dp, top = top + below)
            else Modifier.align(Alignment.TopCenter).padding(top = top, start = if (guiding) maxWidth * 0.42f else left, end = maxOf(rightTop, maxWidth * 0.30f))
        AnimatedVisibility(alert != null, modifier = alertMod, enter = fadeIn(), exit = fadeOut()) { alert?.let { AlertBanner(it) } }

        if (!previewing && !arriving) {
            val inc = engine.incidentAhead; val cam = engine.cameraDist
            val hazards: @Composable () -> Unit = {
                if ((inc != null && alert?.kind != "incident") || (cam != null && alert?.kind != "camera")) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (inc != null && alert?.kind != "incident") HazardBadge("incident", inc.label, Geo.fmtDist(inc.dist, engine.imperial))
                    if (cam != null && alert?.kind != "camera") HazardBadge("camera", "Speed camera", Geo.fmtDist(cam.toDouble(), engine.imperial))
                }
            }
            if (portrait) {
                // tall: laid out like the app on a phone. The sound button sits on the right edge under the top cards; along the
                // bottom, the trip card spans the width (clear of Android Auto's buttons) with the hazards on the left above it and
                // your speed on the right above it
                var tripPx by remember { mutableStateOf(0) }
                CompositionLocalProvider(LocalDensity provides Density(dens.density * buttonScale, dens.fontScale)) {
                    ImmRound(engine.soundIcon(), "sound", { engine.cycleSound() }, Modifier.align(Alignment.TopEnd).padding(end = rightTop, top = top + below))
                }
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = left, bottom = bottom)
                        .width((maxWidth - left - rightBottom).coerceAtLeast(0.dp).coerceAtMost(620.dp))
                        .onGloballyPositioned { engine.bottomCover = ((vT + vH - it.boundsInRoot().top) / vH).coerceIn(0f, 0.6f).toDouble() },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (guiding) CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) { FasterCard(engine, Modifier.fillMaxWidth()) }
                    // (the row keeps the speed's height, so the hazards line up with it)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f)) { hazards() }
                        Box(Modifier.padding(start = 30.dp, top = 14.dp)) { CompositionLocalProvider(LocalDensity provides Density(dens.density * speedScale, dens.fontScale)) { ImmSpeed(engine) } }
                    }
                    if (guiding) CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) { Box(Modifier.onSizeChanged { tripPx = it.height }) { TripCard(engine, true) } }
                }
            } else {
                var leftColW by remember { mutableStateOf(0) }
                var leftColH by remember { mutableStateOf(0) }
                var speedW by remember { mutableStateOf(0) }
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = left, bottom = bottom).onSizeChanged { leftColW = it.width; leftColH = it.height }
                        .onGloballyPositioned { report(1, it.boundsInRoot()) },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (guiding) CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) { FasterCard(engine, Modifier.widthIn(max = 520.dp)) }
                    CompositionLocalProvider(LocalDensity provides Density(dens.density * buttonScale, dens.fontScale)) { SoundPill(engine) }
                    hazards()
                    if (guiding) CompositionLocalProvider(LocalDensity provides Density(dens.density * cardScale, dens.fontScale)) { TripCard(engine, false) }
                }
                // your speed and the limit, bottom right as in the app (clear of Android Auto's zoom buttons); on a small screen where
                // it would run into the cards on the left, it sits above them instead
                val crowded = with(dens) { (left + 16.dp + 34.dp + rightBottom).toPx() } + leftColW + speedW > wPx
                // Keep the speedometer against the screen edge, above Android Auto's native zoom/re-centre controls.
                val speedBottom = if (crowded && leftColH > 0) bottom + with(dens) { leftColH.toDp() } + 10.dp else bottom + 154.dp
                BoxWithConstraints(Modifier.align(Alignment.BottomEnd).padding(end = insR + 12.dp, bottom = speedBottom, start = 34.dp, top = 20.dp)
                    .onSizeChanged { speedW = it.width }) {
                    val availableFit = minOf(1f, (maxWidth.value / 220f).coerceAtLeast(0.25f), (maxHeight.value / 150f).coerceAtLeast(0.25f))
                    val fittedScale = requestedSpeedScale * availableFit
                    CompositionLocalProvider(LocalDensity provides Density(dens.density * fittedScale, dens.fontScale)) { ImmSpeed(engine) }
                }
            }
        }

        // before setting off: the destination, the route choices and the Start bar that starts by itself after 10 s
        if (previewing) Box(Modifier.align(Alignment.BottomCenter).padding(start = left, end = if (portrait) rightBottom else rightTop, bottom = bottom)) { CarRoutePreview(engine) }

        // working out a route: a small spinner at the top, as in the app
        AnimatedVisibility(engine.busy, modifier = Modifier.align(Alignment.TopCenter).padding(top = top + (if (portrait) below else 0.dp)),
            enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.size(48.dp).panel(6.dp, androidx.compose.foundation.shape.CircleShape, c.surface), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(26.dp), color = c.accent, strokeWidth = 3.dp)
            }
        }

        // the arrival summary
        val arr = engine.arrival
        if (arr != null) Box(Modifier.align(Alignment.BottomCenter).padding(start = left, end = rightBottom, bottom = bottom)) { ArrivalCard(engine, arr) }
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
            Text("Faster route found", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Saves about ${fr.savedMin} min", color = Good, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Box(Modifier.carTap("faster-yes") { engine.acceptFaster() }) { ImmPill("Switch", {}, filled = true) }
        Box(Modifier.carTap("faster-no") { engine.dismissFaster() }) { ImmPill("Keep", {}) }
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
        Text(when (sm) { 0 -> "Sound on"; 1 -> "Alerts only"; else -> "Muted" }, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Time left, arrival, distance and the route bar, with the red button that ends the route. [fill]: across the width, compact (tall screens). */
@Composable
private fun TripCard(engine: NavEngine, fill: Boolean) {
    val e = engine.eta ?: return
    val c = LocalVw.current
    val dc = if (e.delayMin >= 10) Bad else if (e.delayMin >= 1) Warn else Good
    val w = if (fill) Modifier.fillMaxWidth() else Modifier.width(380.dp)
    Column((if (fill) Modifier.fillMaxWidth() else Modifier).panel(10.dp, RoundedCornerShape(22.dp), c.surface)) {
        if (engine.showRouteBar) {
            RouteBar(engine, w.padding(start = 16.dp, end = 16.dp, top = if (fill) 7.dp else 9.dp, bottom = if (fill) 2.dp else 4.dp))
            Box(w.padding(horizontal = 18.dp).height(1.dp).background(c.line))
        }
        val end: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { sz ->
            // end the route, as in the app
            Box(Modifier.size(sz).clip(androidx.compose.foundation.shape.CircleShape).background(Bad).carTap("end") { engine.endRoute() }, contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(sz * 0.52f))
            }
        }
        if (fill) Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ImmStat(Geo.fmtDur(e.secs), if (e.delayMin >= 1) "+${e.delayMin} min" else "time left", dc, compact = true)
                ImmStat(e.arrive, "arrival", c.text, compact = true)
                ImmStat(Geo.fmtDist(e.dist, engine.imperial), "to go", c.text, compact = true)
            }
            Box(Modifier.padding(start = 12.dp)) { end(36.dp) }
        } else Row(Modifier.widthIn(min = 380.dp).padding(start = 18.dp, end = 12.dp, top = 9.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            ImmStat(Geo.fmtDur(e.secs), if (e.delayMin >= 1) "+${e.delayMin} min traffic" else "time left", dc)
            ImmStat(e.arrive, "arrival", c.text)
            ImmStat(Geo.fmtDist(e.dist, engine.imperial), "to go", c.text)
            end(40.dp)
        }
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

/** The app's route choice, as on its own screen: pick a route, start with Start (or wait 10 s). Stacks up on narrow screens. */
@Composable
private fun CarRoutePreview(engine: NavEngine) {
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
    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    val baseMs = if (engine.departAtMs > 0) engine.departAtMs else System.currentTimeMillis()
    val dest: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Text("Destination", color = c.sub, fontSize = 12.sp)
            Text(engine.dest?.name ?: "", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    val cards: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        rs.forEachIndexed { i, r ->
            val sel = i == engine.active
            val label = if (i == fastest) "Fastest" else if (i == shortest) "Shortest" else "Alternative"
            Column(Modifier.weight(1f).inset(18.dp, if (sel) c.accent else null).carTap("route-$i") { engine.selectRoute(i) }.padding(horizontal = 12.dp, vertical = 9.dp)) {
                Text(label.uppercase(), color = if (sel) c.accent else c.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
                Text(Geo.fmtDur(r.time), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${Geo.fmtDist(r.len, engine.imperial)} · arrive ${fmt.format(java.util.Date(baseMs + (r.time * 1000).toLong()))}", color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val via = viaOf(r)
                val delayMin = Math.round(r.delay / 60.0).toInt()
                Text(if (delayMin >= 1) "+$delayMin min traffic" else if (via.isNotEmpty()) "via $via" else "No delays",
                    color = if (delayMin >= 10) Bad else if (delayMin >= 1) Warn else c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    val start: @Composable (Modifier) -> Unit = { m ->
        Box(m.height(72.dp).clip(RoundedCornerShape(36.dp)).background(c.accent.copy(alpha = 0.30f)).carTap("start") { engine.beginNavigation() }, contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress.value).background(c.accent))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Start navigation", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (autoStart) Text("Starting in ${kotlin.math.ceil((1f - progress.value) * countdownSeconds).toInt()} s", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp) else Text("Tap to start", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
        }
    }
    val cancel: @Composable () -> Unit = {
        Box(Modifier.size(46.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.tile).carTap("cancel") { engine.endRoute() }, contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Close, null, tint = c.text, modifier = Modifier.size(22.dp))
        }
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
            start(Modifier.width(220.dp))
            cancel()
        } else Column(
            // narrow: the destination and close button, the routes side by side, then Start across the width
            Modifier.fillMaxWidth().floating(28.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) { dest(Modifier.weight(1f).padding(start = 6.dp)); cancel() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { cards() }
            start(Modifier.fillMaxWidth())
        }
    }
}

/**
 * Home, Work, your saved places and the nearby searches in one row that scrolls sideways. Android Auto doesn't say where a swipe
 * starts (a swipe always moves the map), so the row moves with the arrow buttons at its ends.
 */
@Composable
private fun CarChips(engine: NavEngine, actions: CarActions) {
    val c = LocalVw.current
    val home = engine.places.slot("home"); val work = engine.places.slot("work")
    val mine = engine.places.customs()
    val state = androidx.compose.foundation.rememberScrollState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { 240.dp.toPx() }
    @Composable fun arrow(id: String, back: Boolean) {
        Box(Modifier.size(44.dp).panel(8.dp, androidx.compose.foundation.shape.CircleShape, c.surface).carTap(id) {
            scope.launch { state.animateScrollBy(if (back) -stepPx else stepPx) }
        }, contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(
                if (back) androidx.compose.material.icons.Icons.Rounded.ChevronLeft else androidx.compose.material.icons.Icons.Rounded.ChevronRight,
                null, tint = c.text, modifier = Modifier.size(26.dp),
            )
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (state.value > 0) arrow("chips-back", true)
        Row(Modifier.weight(1f).horizontalScroll(state).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.carTap("home") { if (home != null) engine.startRoute(home) else actions.search() }) {
                ImmChip(androidx.compose.material.icons.Icons.Rounded.Home, Color(0xFF4C8DFF), if (home != null) "Home" else "Set home") {}
            }
            Box(Modifier.carTap("work") { if (work != null) engine.startRoute(work) else actions.search() }) {
                ImmChip(androidx.compose.material.icons.Icons.Rounded.Work, Color(0xFFF29900), if (work != null) "Work" else "Set work") {}
            }
            for (lp in mine.take(12)) Box(Modifier.carTap("place-" + lp.label) { engine.startRoute(lp.place) }) {
                ImmChip(androidx.compose.material.icons.Icons.Rounded.Star, Color(0xFF12A4B4), lp.label) {}
            }
            Box(Modifier.carTap("saved") { actions.places() }) {
                ImmChip(androidx.compose.material.icons.Icons.Rounded.History, Color(0xFF9CA4BF), "Saved & recent") {}
            }
            // things to find nearby, as in the app: they open a list to pick from
            Box(Modifier.carTap("n-fuel") { actions.nearby("fuel") }) { ImmChip(androidx.compose.material.icons.Icons.Rounded.LocalGasStation, Color(0xFFEA4335), "Fuel") {} }
            Box(Modifier.carTap("n-charging") { actions.nearby("charging") }) { ImmChip(androidx.compose.material.icons.Icons.Rounded.EvStation, Color(0xFF34A853), "Charging") {} }
            Box(Modifier.carTap("n-food") { actions.nearby("food") }) { ImmChip(androidx.compose.material.icons.Icons.Rounded.Restaurant, Color(0xFFF29900), "Food") {} }
            Box(Modifier.carTap("n-coffee") { actions.nearby("coffee") }) { ImmChip(androidx.compose.material.icons.Icons.Rounded.LocalCafe, Color(0xFFA56BFF), "Coffee") {} }
            Box(Modifier.carTap("n-parking") { actions.nearby("parking") }) { ImmChip(androidx.compose.material.icons.Icons.Rounded.LocalParking, Color(0xFF4C8DFF), "Parking") {} }
            Box(Modifier.carTap("settings") { actions.settings() }) { ImmChip(androidx.compose.material.icons.Icons.Rounded.Settings, Color(0xFF9CA4BF), "Settings") {} }
        }
        if (state.value < state.maxValue) arrow("chips-more", false)
    }
}
