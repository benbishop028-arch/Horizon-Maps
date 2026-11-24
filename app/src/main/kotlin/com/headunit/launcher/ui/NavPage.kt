package com.headunit.launcher.ui

import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.headunit.launcher.L

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddRoad
import androidx.compose.foundation.layout.widthIn
import com.headunit.launcher.LaneSec
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.Geo
import com.headunit.launcher.MapBinding
import com.headunit.launcher.Place
import com.headunit.launcher.NavEngine
import com.headunit.launcher.ArrivalInfo
import kotlinx.coroutines.delay
import com.headunit.launcher.PreferenceKeys

/** The map screen: one modern overlay for both map colour schemes. */
@Composable
fun NavOverlays(engine: NavEngine, onSettings: () -> Unit) = ImmersiveNav(engine, onSettings)

internal fun viaOf(r: com.headunit.launcher.Route): String {
    var best = ""
    var bl = 0.0
    for (i in r.steps.indices) {
        val len = (r.steps.getOrNull(i + 1)?.off ?: r.len) - r.steps[i].off
        if (len > bl && r.steps[i].street.isNotBlank()) { bl = len; best = r.steps[i].street }
    }
    return best
}

/** Journey overview: route options plus a Start button that fills up and starts by itself after 5 s. */
@Composable
internal fun BoxScope.RoutePreview(engine: NavEngine) {
    val c = LocalVw.current
    val rs = engine.routes.take(3)
    if (rs.isEmpty()) return
    val fastest = rs.indices.minByOrNull { rs[it].time } ?: 0
    val shortest = rs.indices.minByOrNull { rs[it].len } ?: 0
    val progress = remember { Animatable(0f) }
    // planning an arrive-by trip (or having the picker open) stops the countdown: you are not leaving now
    var arriveBy by remember { mutableStateOf(false) }
    val hold = arriveBy || engine.flyingOver || engine.planned?.dest?.let { p -> engine.dest?.let { d -> Geo.dist(p.pos, d.pos) < 50.0 } } == true
    val autoStart = engine.prefs.b(PreferenceKeys.AUTO_START_ROUTE, true)
    val countdownMs = engine.prefs.i(PreferenceKeys.AUTO_START_SECONDS, 10).coerceIn(3, 30) * 1000
    LaunchedEffect(engine.active, engine.routes, hold, autoStart, countdownMs) {
        progress.snapTo(0f)
        if (hold || !autoStart) return@LaunchedEffect
        progress.animateTo(1f, tween(countdownMs, easing = LinearEasing))
        if (!engine.stopMode) engine.beginNavigation()
    }
    if (arriveBy) ArriveByDialog(engine) { arriveBy = false }
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    // a phone held sideways: too short for the panel across the bottom (it covered nearly all the map, GitHub), so it becomes a
    // column down the left with the route fitted beside it. (Head units are wider than 1000, so they keep the usual layout.)
    val sideways = conf.screenHeightDp < 480 && conf.screenWidthDp < 1000 && conf.screenWidthDp > conf.screenHeightDp
    val sideW = minOf(400, conf.screenWidthDp / 2)
    LaunchedEffect(sideways, sideW, conf.screenWidthDp) {
        val f = if (sideways) sideW.toDouble() / conf.screenWidthDp.coerceAtLeast(1) else 0.0
        if (f != engine.map.previewSide) { engine.map.previewSide = f; engine.refitPreview() }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { engine.map.previewSide = 0.0 } }
    // flying over the route: the panel steps aside so the flight can be seen, leaving one small pill to stop it
    if (engine.flyingOver) {
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).floating(28.dp).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(L.t("Flying…"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Pill(L.t("Stop"), true, { engine.stopFlyOver() })
        }
        return
    }
    val fmt = com.headunit.launcher.Clock.format()
    val baseMs = if (engine.departAtMs > 0) engine.departAtMs else System.currentTimeMillis()
    Column(
        (if (sideways) Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp).width(sideW.dp)
            .verticalScroll(rememberScrollState(), reverseScrolling = true)
        else Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 4.dp).fillMaxWidth()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
    val selRoute = rs.getOrNull(engine.active)
    // fuel estimate from the economy and pump price set in Settings > Driving (blank = not shown)
    val ev = engine.prefs.s(PreferenceKeys.FUEL_TYPE, "E10") == "EV"
    val mpg = engine.prefs.s(if (ev) "evmpk" else "fuelmpg", "").toDoubleOrNull()?.takeIf { it > 0 }
    val ppl = pricePence(engine.prefs.s(if (ev) "evppk" else "fuelppl", ""), ev, engine.prefs)
    val costText = if (mpg != null && ppl != null && selRoute != null)
        (if (ev) L.t("Charging about ") else L.t("Fuel about ")) + formatMoney(engine.prefs, 
            if (ev) selRoute.len / 1609.344 / mpg * ppl / 100.0 else selRoute.len / 1609.344 / mpg * 4.54609 * ppl / 100.0) else null
    ZonesRow(engine)
    if (engine.routeWx.isNotEmpty()) {
        val fahr = isImperial(engine.prefs)
        Row(
            Modifier.floating(50.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(L.t("Weather on route"), color = c.sub, fontSize = 13.sp)
            engine.routeWx.forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(wxIcon(w.code), null, tint = wxTint(w.code), modifier = Modifier.size(24.dp))
                    Column(Modifier.padding(start = 6.dp)) {
                        Text((if (fahr) Math.round(w.temp * 9 / 5 + 32) else Math.round(w.temp)).toString() + "°", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(L.t("in {0}", if (w.minutes >= 60) "${w.minutes / 60} h ${w.minutes % 60} min" else "${w.minutes} min") + (if (w.rain >= 40) " · ${w.rain}%" else ""), color = if (w.rain >= 60) Warn else c.sub, fontSize = 11.sp)
                    }
                }
            }
        }
    }
    val stopsPart: @Composable () -> Unit = {
        Text(L.t("Stops"), color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp))
        Pill(L.t("+ Fuel"), engine.stopMode && engine.nearbyKind == "fuel", { engine.stopMode = true; engine.loadNearby("fuel", false) })
        Pill(L.t("+ Charger"), engine.stopMode && engine.nearbyKind == "charging", { engine.stopMode = true; engine.loadNearby("charging", false) })
        if (engine.prefs.s(PreferenceKeys.VEHICLE, "car") == "lorry") Pill(L.t("+ Lorry park"), engine.stopMode && engine.nearbyKind == "lorrypark", { engine.stopMode = true; engine.loadNearby("lorrypark", false) })
        Pill(if (engine.via.isEmpty()) L.t("+ Stop") else L.t("Edit stops"), false, { engine.stopsEditor = true })
        if (engine.via.isNotEmpty()) Text(engine.via.joinToString(" › ") { it.name }, color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
        engine.destRain?.let { Text(it, color = Warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp)) }
    }
    val leavePart: @Composable () -> Unit = {
        Text(L.t("Leave"), color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp))
        for ((label, m) in listOf(L.t("Now") to 0, L.t("+15 min") to 15, L.t("+30 min") to 30, "+1 h" to 60, "+2 h" to 120)) {
            Pill(label, engine.departMin == m, { if (engine.departMin != m) engine.setDeparture(m) })
        }
        Pill(L.t("Arrive by…"), hold, { arriveBy = true })
    }
    // (sideways on a phone the two share one row that scrolls across, to leave more of the map showing)
    if (sideways) Row(
        Modifier.floating(50.dp).horizontalScroll(rememberScrollState()).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { stopsPart(); Spacer(Modifier.width(8.dp)); leavePart() }
    else {
        Row(
            Modifier.floating(50.dp).horizontalScroll(rememberScrollState()).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) { stopsPart() }
        Row(
            Modifier.floating(50.dp).horizontalScroll(rememberScrollState()).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) { leavePart() }
    }
    // electric: the battery now, the least you want to arrive with, and the eco route; a charging stop is then suggested
    // below if the trip needs one
    if (ev) Row(
        Modifier.floating(50.dp).horizontalScroll(rememberScrollState()).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val pct = engine.prefs.i(PreferenceKeys.EV_CHARGE_PCT, 80).coerceIn(5, 100)
        val minArr = engine.prefs.i(PreferenceKeys.EV_MIN_ARRIVE, 20)
        Text(L.t("Battery now"), color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp))
        // (1% a tap, and held down it runs on: it went in tens, so 32% or 35% couldn't be set, Dry-Secretary2456)
        val setPct = { d: Int -> engine.prefs.put(PreferenceKeys.EV_CHARGE_PCT, (engine.prefs.i(PreferenceKeys.EV_CHARGE_PCT, 80).coerceIn(5, 100) + d).coerceIn(5, 100)) }
        StepPill("−", { setPct(-1) }, { engine.refreshEvPlan() })
        Text("$pct%", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
        StepPill("+", { setPct(1) }, { engine.refreshEvPlan() })
        Text(L.t("Arrive with at least"), color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(start = 14.dp, end = 4.dp))
        for (m in listOf(10, 20, 30)) Pill("$m%", minArr == m, { engine.prefs.put(PreferenceKeys.EV_MIN_ARRIVE, m); engine.refreshEvPlan() })
        val eco = engine.prefs.b(PreferenceKeys.ROUTE_ECO, false)
        Pill(if (eco) L.t("Eco route ✓") else L.t("Eco route"), eco, { engine.prefs.put(PreferenceKeys.ROUTE_ECO, !eco); engine.rerouteForSettings() })
    }
    FuelSuggestionRow(engine)
    // landscape: everything in one row. Portrait (narrow): destination on top, the route choices across, Start underneath.
    val narrowP = conf.screenWidthDp < 720 || sideways
    val destPart: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Text(L.t("Destination"), color = c.sub, fontSize = 12.sp)
            Text(engine.dest?.name ?: "", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val d = engine.dest
            if (d != null) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val fav = engine.places.isFavourite(d)
                Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, L.t("Favourite"), tint = if (fav) Warn else c.sub,
                    modifier = Modifier.size(26.dp).clickable { engine.toast(if (engine.places.toggleFavourite(d)) L.t("Saved to favourites") else L.t("Removed from favourites"), 2000) })
                Icon(Icons.Rounded.Home, L.t("Set as home"), tint = c.sub,
                    modifier = Modifier.size(26.dp).clickable { engine.places.setSlot("home", d); engine.toast(L.t("Home set to {0}", d.name), 2500) })
                Icon(Icons.Rounded.Work, L.t("Set as work"), tint = c.sub,
                    modifier = Modifier.size(26.dp).clickable { engine.places.setSlot("work", d); engine.toast(L.t("Work set to {0}", d.name), 2500) })
                // Save route: the destination, the stops and the road picked, to plan again later (the star already saves the place)
                val rawRoutes = engine.prefs.s(PreferenceKeys.PLACES_ROUTES, "[]")
                val routeSaved = remember(rawRoutes, engine.active, engine.routes, engine.via) { engine.routeIsSaved() }
                Icon(Icons.Rounded.AddRoad, L.t("Save route"), tint = if (routeSaved) c.accent else c.sub, modifier = Modifier.size(26.dp).clickable { engine.toggleSaveRoute() })
                // paired with your car (Settings > Places & trips): send it there instead of driving it from here
                if (engine.prefs.s(PreferenceKeys.LINK_ROLE, "") == "phone" && engine.prefs.s(PreferenceKeys.LINK_CODE, "").isNotBlank())
                    Icon(Icons.Rounded.DirectionsCar, L.t("Send to car"), tint = c.accent, modifier = Modifier.size(26.dp).clickable { engine.sendToCar(d) })
            }
        }
    }
    val routeCards: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        rs.forEachIndexed { i, r ->
            val sel = i == engine.active
            val shape = RoundedCornerShape(18.dp)
            val label = if (r.mine.isNotBlank()) L.t("Your route") else if (i == fastest) L.t("Fastest") else if (rs[i].simpler) L.t("Simplest") else if (i == shortest) L.t("Shortest") else L.t("Alternative")
            Column(
                Modifier.weight(1f).inset(18.dp, if (sel) c.accent else null)
                                        .clickable { engine.selectRoute(i) }.padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                // (one line: three cards across a phone squeezed "ALTERNATIVE" onto two, GitHub)
                Text(label.uppercase(), color = if (sel) c.accent else c.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = if (narrowP) 0.3.sp else 1.sp,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                Text(Geo.fmtDur(r.time), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(L.t("{0} · arrive {1}", Geo.fmtDist(r.len, engine.imperial), fmt.format(java.util.Date(baseMs + (r.time * 1000).toLong()))), color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val via = viaOf(r)
                val delayMin = Math.round(r.delay / 60.0).toInt()
                val mine = mineLine(rs, r)
                Text(
                    mine ?: if (delayMin >= 1) L.t("+{0} min traffic", delayMin) else if (via.isNotEmpty()) "via $via" else L.t("No delays"),
                    color = if (mine != null) Good else if (delayMin >= 10) Bad else if (delayMin >= 1) Warn else c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                // roughly what this route costs in fuel (or charging), from Settings › Fuel (pc4096: it was only at the far end of the
                // Stops row, scrolled out of sight)
                if (mpg != null && ppl != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(if (ev) Icons.Rounded.EvStation else Icons.Rounded.LocalGasStation, null, tint = c.sub, modifier = Modifier.size(13.dp))
                    // (≈ in place of "Fuel about": the pump or plug icon already says what it is, and with two or three cards across a
                    // phone the words pushed the amount off the end, GitHub #272)
                    Text("≈ " + formatMoney(engine.prefs, 
                        if (ev) r.len / 1609.344 / mpg * ppl / 100.0 else r.len / 1609.344 / mpg * 4.54609 * ppl / 100.0),
                        color = c.text.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
                }
                LorryBadge(r)
                if (i == engine.active) LorryCheck(engine, r)
            }
        }
    }
    val startBtn: @Composable (Modifier) -> Unit = { m ->
        Box(
            m.height(72.dp).clip(RoundedCornerShape(36.dp)).background(c.accent.copy(alpha = 0.30f))
                .clickable { engine.beginNavigation() },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress.value).background(c.accent))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // always on one line (in Dutch it wrapped and was cut off, smikkelvriend): a long wording shortens with "…"
                Text(L.t(if (engine.demoNext) "Start demo drive" else "Start navigation"), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp))
                Text(if (hold) L.t("Planned for later") else if (autoStart) L.t("Starting in {0} s", kotlin.math.ceil((1f - progress.value) * (countdownMs / 1000f)).toInt()) else L.t("Tap to start"), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp))
            }
        }
    }
    if (!narrowP) Row(
        Modifier.fillMaxWidth().floating(28.dp).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        destPart(Modifier.width(150.dp))
        routeCards()
        if (engine.prefs.b(PreferenceKeys.FLYOVER, true)) FlyOverBtn(engine)
        if (engine.prefs.b(PreferenceKeys.DEMO, false)) DemoChoiceBtn(engine)
        startBtn(Modifier.width(210.dp))
        RoundBtn(Icons.Rounded.Close, { engine.endRoute() }, size = 46.dp)
    } else Column(Modifier.fillMaxWidth().floating(28.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            destPart(Modifier.weight(1f))
            RoundBtn(Icons.Rounded.Close, { engine.endRoute() }, size = 46.dp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { routeCards() }
        // with both Fly over route and Demo on, they get a row of their own: three across a phone squeezed Start navigation into a
        // narrow column of broken words (GitHub)
        val flyOn = engine.prefs.b(PreferenceKeys.FLYOVER, true)
        val demoOn = engine.prefs.b(PreferenceKeys.DEMO, false)
        if (flyOn && demoOn) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlyOverBtn(engine, Modifier.weight(1f), 56.dp)
                DemoChoiceBtn(engine, Modifier.weight(1f), 56.dp)
            }
            startBtn(Modifier.fillMaxWidth())
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Fly over route takes its share (its words may go on two lines), Start navigation the bigger share, so a long wording
            // like Dutch "Over de route vliegen" no longer squeezes Start into a column of broken words (smikkelvriend, #199)
            if (flyOn) FlyOverBtn(engine, Modifier.weight(1f), twoLines = true)
            if (demoOn) DemoChoiceBtn(engine)
            startBtn(Modifier.weight(if (flyOn) 1.3f else 1f))
        }
    }
    }
}

/**
 * One of your saved routes in the route choice (#321): "Home → Work (morning) · +2 min" against the quickest of the others
 * ("2 min faster", "same time"), or null for a normal route.
 */
internal fun mineLine(rs: List<com.headunit.launcher.Route>, r: com.headunit.launcher.Route): String? {
    if (r.mine.isBlank()) return null
    val best = rs.filter { it.mine.isBlank() }.minOfOrNull { it.time } ?: return r.mine
    val d = Math.round((r.time - best) / 60.0).toInt()
    return r.mine + " · " + when { d > 0 -> L.t("+{0} min", d); d < 0 -> L.t("{0} min faster", -d); else -> L.t("same time") }
}

/**
 * Planned for a lorry: a blue "Lorry · 4.2 m · 44 t" when every measurement was used (TomTom or offline), or an amber
 * "Height & weight only" when Mapbox planned it (it can't check length or hazardous goods). Nothing for a car.
 */
@Composable
internal fun LorryBadge(r: com.headunit.launcher.Route) {
    val kind = r.lorry
    if (kind.isEmpty()) return
    val full = kind == "full"
    val fg = if (full) Color(0xFFAECBFA) else Color(0xFFFBD38D)
    val bg = if (full) Color(0xFF1E3A5C) else Color(0xFF3A3020)
    val text = if (full) L.t("Lorry · ") + String.format(java.util.Locale.US, "%.1f m", com.headunit.launcher.Vehicle.heightCm / 100.0) + " · " +
        (com.headunit.launcher.Vehicle.weightT10 / 10.0).let { if (it % 1.0 == 0.0) "${it.toInt()} t" else String.format(java.util.Locale.US, "%.1f t", it) }
        else L.t("Height & weight only")
    Row(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.LocalShipping, null, tint = fg, modifier = Modifier.size(14.dp))
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
    }
}

/** The lorry check for the chosen route: green with no limits under your lorry, red with the ones it found, grey while checking. */
@Composable
internal fun LorryCheck(engine: NavEngine, r: com.headunit.launcher.Route) {
    if (r.lorry.isEmpty()) return
    androidx.compose.runtime.LaunchedEffect(r) { engine.lorryRouteCheck(r) }
    val res = engine.lorryCheck?.takeIf { it.first === r }?.second ?: ""
    val text = res.ifBlank { L.t("Lorry check…") }
    val ok = res.contains("no limits"); val bad = res.contains(" on this route")
    val fg = if (ok) Color(0xFF9BE3B0) else if (bad) Color(0xFFF2C2BF) else Color(0xFFBDC1C6)
    val bg = if (ok) Color(0xFF1E3A28) else if (bad) Color(0xFF3A2224) else Color(0xFF2C313A)
    Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp))
}

/** "Fly over route": a 3D flight along the whole drive before you set off. */
@Composable
private fun FlyOverBtn(engine: NavEngine, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 72.dp, twoLines: Boolean = false) {
    val c = LocalVw.current
    Row(
        modifier.height(height).clip(RoundedCornerShape(height / 2)).background(c.accent.copy(alpha = 0.14f))
            .clickable(enabled = !engine.flyingOver) { engine.flyOver() }.padding(horizontal = if (twoLines) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.PlayArrow, null, tint = c.accent, modifier = Modifier.size(24.dp))
        Text(if (engine.flyingOver) L.t("Flying…") else L.t("Fly over route"), color = c.accent, fontSize = if (twoLines) 14.sp else 15.sp, fontWeight = FontWeight.Bold,
            maxLines = if (twoLines) 2 else 1, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp, textAlign = if (twoLines) androidx.compose.ui.text.style.TextAlign.Center else null,
            modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun ArrivalStat(value: String, label: String) {
    val c = LocalVw.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = c.sub, fontSize = 12.sp)
    }
}

/** Shown for a few seconds after arriving, with how the trip went and a button to save the place. */
@Composable
internal fun ArrivalCard(engine: NavEngine, a: ArrivalInfo, modifier: Modifier = Modifier, compact: Boolean = false) {
    val c = LocalVw.current
    val imp = engine.imperial
    val k = if (imp) 2.23694 else 3.6
    // on the car screen taps come through Android Auto (see CarTaps), not as touches on the card
    val car = engine.isCar
    // ([compact]: a short car screen. The tick sits beside "You've arrived" and the gaps are tighter, so the card is lower)
    Column(
        modifier.widthIn(min = 380.dp, max = 460.dp).floating(28.dp).padding(if (compact) 14.dp else 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
    ) {
        if (compact) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Good.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = Good, modifier = Modifier.size(20.dp))
            }
            Text(L.t("You've arrived"), color = c.sub, fontSize = 14.sp)
        } else {
            Box(Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Good.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = Good, modifier = Modifier.size(38.dp))
            }
            Text(L.t("You've arrived"), color = c.sub, fontSize = 14.sp)
        }
        Text(a.name, color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (a.side.isNotEmpty()) Text(L.t(if (a.side == "left") "On the left" else "On the right"), color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 0.dp else 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ArrivalStat(Geo.fmtDist(a.dist, imp), "distance")
            ArrivalStat(Geo.fmtDur(a.secs), "time")
            ArrivalStat("${Math.round(a.avgMs * k)} ${if (imp) "mph" else "km/h"}", "average")
            // what the drive really cost, from the distance driven (Settings › Places and trips › Live fuel cost, #316)
            a.fuelCost?.let { ArrivalStat(formatMoney(engine.prefs, it), L.t(if (engine.prefs.s(PreferenceKeys.FUEL_TYPE, "E10") == "EV") "charging" else "fuel")) }
        }
        engine.arrivalParking?.let { pk ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.tile).clickable { engine.dismissArrival(); engine.startRoute(pk.place) }
                    .then(if (car) Modifier.carTap("arr-park") { engine.dismissArrival(); engine.startRoute(pk.place) } else Modifier)
                    .padding(horizontal = 14.dp, vertical = if (compact) 6.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.LocalParking, null, tint = Color(0xFF4C8DFF), modifier = Modifier.size(26.dp))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(L.t("Nearest parking"), color = c.sub, fontSize = 12.sp)
                    Text(pk.place.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(Geo.fmtDist(pk.dist, imp), color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
                Text(L.t("Go"), color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        // the way you drove, kept as one of your routes (#321): named on the phone; on the car screen (no typing) with the name it suggests
        engine.finishedDrive?.let { d ->
            var naming by remember { mutableStateOf(false) }
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).background(c.accent).clickable { if (car) engine.saveMyRoute(d.suggested) else naming = true }
                    .then(if (car) Modifier.carTap("arr-mine") { engine.saveMyRoute(d.suggested) } else Modifier)
                    .padding(horizontal = 16.dp, vertical = if (compact) 7.dp else 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Route, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(L.t("Save as my route"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
            }
            if (naming) SaveMyRouteDialog(d.suggested, onSave = { n -> naming = false; engine.saveMyRoute(n) }, onClose = { naming = false })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            a.place?.let { p ->
                var fav by remember { mutableStateOf(engine.places.isFavourite(p)) }
                Pill(if (fav) L.t("Saved") else L.t("Save place"), fav, { fav = engine.places.toggleFavourite(p) },
                    if (car) Modifier.carTap("arr-save") { fav = engine.places.toggleFavourite(p) } else Modifier)
            }
            // the way back to where this trip started (home, if it started there)
            if (engine.tripFrom != null && engine.prefs.b(PreferenceKeys.ROUTEBACK, true)) Pill(L.t("Route back"), false, { engine.routeBack() },
                if (car) Modifier.carTap("arr-back") { engine.routeBack() } else Modifier)
            Pill(L.t("Done"), true, { engine.dismissArrival() }, if (car) Modifier.carTap("arr-done") { engine.dismissArrival() } else Modifier)
        }
    }
}

/** Names a route before it's saved to My routes (#321), with the name it suggests filled in. */
@Composable
private fun SaveMyRouteDialog(suggested: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    val c = LocalVw.current
    var name by remember { mutableStateOf(suggested) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.panel)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(L.t("Save this route"), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            SField(name, { name = it.take(60) }, placeholder = L.t("e.g. School run"), width = 436.dp, fill = true)
            Text(L.t("Offered next time you go from near here to the same place. Settings › Places & trips › My routes to rename or delete it."), color = c.sub, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                SButton(L.t("Cancel")) { onClose() }
                SButton(L.t("Save"), primary = true) { onSave(name.ifBlank { suggested }) }
            }
        }
    }
}

private fun nearbyIcon(kind: String?): androidx.compose.ui.graphics.vector.ImageVector = when (kind?.substringBefore(':')) {
    "fuel" -> Icons.Rounded.LocalGasStation
    "charging" -> Icons.Rounded.EvStation
    "food" -> Icons.Rounded.Restaurant
    "coffee", "services" -> Icons.Rounded.LocalCafe
    "toilets" -> Icons.Rounded.Wc
    "lorrypark" -> Icons.Rounded.LocalShipping
    "places" -> Icons.Rounded.Bookmarks
    "tracks" -> Icons.Rounded.Route
    else -> Icons.Rounded.LocalParking
}

private fun nearbyTitle(kind: String?): String = if (kind != null && kind.contains(':')) kind.substringAfter(':') else when (kind) {
    "fuel" -> L.t("Fuel stations"); "charging" -> L.t("Charging points"); "food" -> L.t("Food"); "coffee" -> L.t("Coffee")
    "toilets" -> L.t("Toilets"); "services" -> L.t("Services"); "lorrypark" -> L.t("Lorry parks")
    "places" -> L.t("Your places"); "tracks" -> L.t("Your tracks"); else -> L.t("Car parks")
}

@Composable
internal fun NearbyCard(engine: NavEngine) {
    val c = LocalVw.current
    val adding = engine.stopMode || engine.route != null
    val along = engine.nearby.any { it.ahead }
    val title = (if (adding) L.t("Add a stop: ") else "") + nearbyTitle(engine.nearbyKind) + (if (along) " ahead" else "")
    val hasPrices = engine.nearby.any { it.price != null }
    Column(Modifier.floating(24.dp).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // in one of your folders: ‹ goes back to the folders
            val nk = engine.nearbyKind
            if (nk != null && nk.contains(':')) Icon(Icons.AutoMirrored.Rounded.ArrowBack, L.t("Back"), tint = c.accent,
                modifier = Modifier.padding(end = 8.dp).size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable { engine.loadNearby(nk.substringBefore(':'), false) }.padding(6.dp))
            IconBadge(if (nk != null && nk.contains(':')) Icons.Rounded.FolderOpen else nearbyIcon(nk), c.accent, Modifier.padding(end = 12.dp), 36.dp)
            Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (engine.nearbyBusy) CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
            Icon(Icons.Rounded.Close, L.t("Close"), tint = c.sub, modifier = Modifier.padding(start = 12.dp).size(22.dp).clickable { engine.clearNearby() })
        }
        if (engine.nearbyKind == "charging") engine.chargerFilterText().takeIf { it.isNotEmpty() }?.let { ft ->
            Text(L.t("Filter: {0}", ft), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 6.dp))
        }
        if (hasPrices) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(if (along) L.t("Soonest") else L.t("Nearest"), engine.nearbySort != "cheap", { engine.changeNearbySort("near") })
            Pill(L.t("Cheapest"), engine.nearbySort == "cheap", { engine.changeNearbySort("cheap") })
            Text(com.headunit.launcher.FuelFinder.fuelName(engine.prefs.s(PreferenceKeys.FUEL_TYPE, "E10")), color = c.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
        }
        LazyColumn(Modifier.heightIn(max = 230.dp).padding(top = 6.dp)) {
            items(engine.nearby) { n ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                    // (a GPX track is shown on the map with its card, rather than added as a stop or driven straight away; a folder opens)
                    if (n.place.web.startsWith("folder:")) engine.loadNearby((engine.nearbyKind ?: "places").substringBefore(':') + ":" + n.place.web.removePrefix("folder:"), false)
                    else if (n.place.web.startsWith("gpx:")) { engine.nearbyKind = null; engine.pickPlace(n.place) }
                    else if (adding) engine.addStop(n.place) else engine.startRoute(n.place)
                }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(if (n.place.web.startsWith("folder:")) Icons.Rounded.Folder else nearbyIcon(engine.nearbyKind), null, tint = c.accent, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(n.place.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(n.extra.takeIf { it.isNotBlank() }, n.place.sub.takeIf { it.isNotBlank() && engine.nearbyKind == "fuel" }).joinToString(" · ")
                        if (sub.isNotBlank()) Text(sub, color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (n.price != null) Text(com.headunit.launcher.fmtFuelPrice(n.price, n.unit), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    // a charger's live availability: green with some free, red with none, grey without live data
                    if (n.free.isNotBlank()) {
                        val none = n.free.startsWith("0 of"); val unknown = n.free.startsWith("No")
                        val fg = if (unknown) c.sub else if (none) Color(0xFFF2C2BF) else Color(0xFF9BE3B0)
                        val bg = if (unknown) c.tile else if (none) Color(0xFF3A2224) else Color(0xFF1E3A28)
                        Text(n.free, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    if (n.place.web.startsWith("folder:")) Icon(Icons.Rounded.ChevronRight, null, tint = c.sub, modifier = Modifier.padding(start = 10.dp).size(22.dp))
                    else Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 10.dp)) {
                        Text(Geo.fmtDist(n.dist, engine.imperial), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp))
                        if (n.detourMin >= 0) Text(L.t("+{0} min", n.detourMin), color = c.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        if (hasPrices) Text(L.t("Prices: Fuel Finder (gov.uk)"), color = c.sub, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}


private fun laneAngle(d: String): Float = when (d) {
    "SLIGHT_LEFT" -> -45f; "LEFT" -> -90f; "SHARP_LEFT" -> -135f; "LEFT_U_TURN" -> -180f
    "SLIGHT_RIGHT" -> 45f; "RIGHT" -> 90f; "SHARP_RIGHT" -> 135f; "RIGHT_U_TURN" -> 180f
    else -> 0f
}

private fun laneMatches(dir: String, maneuver: String): Boolean {
    val m = maneuver.uppercase()
    return when {
        m.contains("U_TURN") -> dir.endsWith("U_TURN")
        m.contains("LEFT") -> dir.contains("LEFT")
        m.contains("RIGHT") -> dir.contains("RIGHT")
        else -> dir == "STRAIGHT"
    }
}

/** Lane guidance in the style of Google Maps: one arrow per lane, the lanes you should use in solid white and the rest dimmed, with thin dividers. */
@Composable
internal fun LaneRow(sec: LaneSec, maneuver: String, near: Boolean = false) {
    val anyFollow = sec.lanes.any { it.follow != null }
    // fades and settles in once when the strip appears; nothing pulses or bounces afterwards
    val appear by androidx.compose.animation.core.animateFloatAsState(1f, androidx.compose.animation.core.tween(320), label = "laneIn")
    val grow by androidx.compose.animation.core.animateFloatAsState(if (near) 1f else 0f, androidx.compose.animation.core.tween(350), label = "laneBig")
    val cellW = (42 + 6 * grow).dp
    val n = sec.lanes.size
    androidx.compose.foundation.Canvas(Modifier.width(cellW * n + 16.dp).height((58 + 8 * grow).dp)) {
        val pad = 8.dp.toPx()
        val gap = 5.dp.toPx()
        val cw = (size.width - 2 * pad) / n
        val stroke = 4.2.dp.toPx() + 0.8.dp.toPx() * grow
        val round = androidx.compose.ui.graphics.StrokeCap.Round
        fun o(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x, y)
        val a = appear.coerceIn(0f, 1f)
        val ink = Color(0xFF12303A)
        sec.lanes.forEachIndexed { idx, lane ->
            val x0 = pad + idx * cw
            val cx = x0 + cw / 2
            val active = if (anyFollow) lane.follow != null else lane.dirs.any { laneMatches(it, maneuver) }
            // each lane is its own rounded chip: solid white for the lane you need, a faint wash for the others
            drawRoundRect(
                Color.White.copy(alpha = a * (if (active) 0.96f else 0.12f)), o(x0 + gap / 2, 3.dp.toPx()),
                androidx.compose.ui.geometry.Size(cw - gap, size.height - 6.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(13.dp.toPx()),
            )
            // the arrows that are not the one you need are drawn first, so the highlighted one sits on top
            val dirs = lane.dirs.sortedBy { d -> if (if (anyFollow) d == lane.follow else laneMatches(d, maneuver)) 1 else 0 }
            dirs.forEach { d ->
                val hit = if (anyFollow) d == lane.follow else laneMatches(d, maneuver)
                val col = if (active) ink.copy(alpha = a * (if (hit) 1f else 0.35f)) else Color.White.copy(alpha = a * 0.5f)
                val baseY = size.height - 13.dp.toPx()
                val midY = size.height * 0.52f
                val len = (14.dp.toPx() + 2.dp.toPx() * grow)
                drawLine(col, o(cx, baseY), o(cx, midY), stroke, round)
                rotate(laneAngle(d), o(cx, midY)) {
                    val tip = o(cx, midY - len)
                    drawLine(col, o(cx, midY), tip, stroke, round)
                    val hd = 6.5.dp.toPx()
                    drawLine(col, tip, o(cx - hd, tip.y + hd), stroke, round)
                    drawLine(col, tip, o(cx + hd, tip.y + hd), stroke, round)
                }
            }
        }
    }
}


/**
 * Lane hero view: the lanes drawn big across the turn card, like a stylised gantry picture: dark road, dashed lane lines,
 * the lane(s) you need lit with a glow and a large arrow, the others dimmed. Used before junctions with two or more lanes.
 */
/**
 * The junction close-up picture (Settings > Turn card & lanes > Lanes on the map > Close-up picture): for the last 400 m before a
 * junction with lane data, a drawn picture of its lanes with the ones to use lit and the distance, under the turn card. Nothing is
 * drawn on the map itself in this style, so it can never sit in the wrong place.
 */
@Composable
internal fun JunctionCloseup(engine: NavEngine, modifier: Modifier = Modifier, maxH: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified) {
    // [maxH]: the room there is for it (on the car screen, the gap between the turn card and the trip card): the picture is made
    // smaller to fit, and left out when even a small one wouldn't (the turn card's own lane strip still shows the lanes)
    val limited = maxH != androidx.compose.ui.unit.Dp.Unspecified
    // Mapbox's own picture of a motorway junction, when it has one (Settings > Turn card & lanes > Junction pictures)
    run {
        val t = engine.turn
        if (t != null && t.junctionPic.isNotEmpty() && engine.route != null && !engine.previewing && t.dist in 5.0..1000.0 && engine.prefs.b(PreferenceKeys.JUNCTION_PICS, false)) {
            var bmp by remember(t.junctionPic) { mutableStateOf(JunctionPics.cached(t.junctionPic)) }
            if (bmp == null) LaunchedEffect(t.junctionPic) { bmp = JunctionPics.load(t.junctionPic, engine.mbToken) }
            val b = bmp
            if (b != null && (!limited || maxH >= 90.dp)) {
                val shape = RoundedCornerShape(18.dp)
                Box(modifier.widthIn(max = 420.dp).fillMaxWidth().shadow(8.dp, shape).clip(shape).background(Color.Black)) {
                    androidx.compose.foundation.Image(b.asImageBitmap(), L.t("Junction"), if (limited) Modifier.fillMaxWidth().heightIn(max = maxH) else Modifier.fillMaxWidth(),
                        contentScale = if (limited) androidx.compose.ui.layout.ContentScale.Fit else androidx.compose.ui.layout.ContentScale.FillWidth)
                    Text(Geo.fmtDist(t.dist, engine.imperial), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                return
            }
        }
    }
    if (!engine.prefs.b(PreferenceKeys.LANEMAP, true) || engine.prefs.s(PreferenceKeys.LANE_MAP_STYLE, "lit") != "closeup") return
    if (engine.route == null || engine.previewing) return
    val t = engine.turn ?: return
    val sec = t.lanes ?: return
    if (t.dist > 400.0 || t.dist < 5.0 || sec.lanes.size < 2) return
    // (the distance line and padding take about 56 dp; the lanes drawing is 118 dp when there's room, down to 50)
    val canvasH = if (limited) (maxH - 56.dp).coerceAtMost(118.dp) else 118.dp
    if (canvasH < 50.dp) return
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.widthIn(max = 420.dp).fillMaxWidth().shadow(8.dp, shape).clip(shape).background(Color(0xFF0E2A30))) {
        Text(Geo.fmtDist(t.dist, engine.imperial), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, top = 10.dp))
        LaneHero(sec, t.maneuver, null, canvasH)
    }
}

@Composable
internal fun LaneHero(sec: LaneSec, maneuver: String, advice: String?, canvasH: androidx.compose.ui.unit.Dp = 118.dp) {
    val anyFollow = sec.lanes.any { it.follow != null }
    val appear by androidx.compose.animation.core.animateFloatAsState(1f, androidx.compose.animation.core.tween(380), label = "heroIn")
    val n = sec.lanes.size
    Column(Modifier.fillMaxWidth().background(Color(0xFF0E2A30)).padding(top = 10.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(canvasH).padding(horizontal = 14.dp)) {
            val a = appear.coerceIn(0f, 1f)
            val w = size.width; val h = size.height
            // the road narrows a little towards the top, as if seen from the driver's seat
            val topInset = w * 0.08f
            fun xAt(frac: Float, y: Float): Float { val t = y / h; val ins = topInset * (1f - t); return ins + frac * (w - 2 * ins) }
            val road = androidx.compose.ui.graphics.Path().apply { moveTo(xAt(0f, 0f), 0f); lineTo(xAt(1f, 0f), 0f); lineTo(w, h); lineTo(0f, h); close() }
            drawPath(road, Color(0xFF233B42).copy(alpha = a))
            // (bolder than before, karthikeyansr-lgtm #188: the thin lines were hard to read at a glance)
            // (thinner again on a road with many lanes, so the arrows in neighbouring lanes don't run into each other)
            val laneW = w / maxOf(1, n)
            val stroke = minOf(9.dp.toPx(), laneW * 0.14f).coerceAtLeast(5.dp.toPx())
            val round = androidx.compose.ui.graphics.StrokeCap.Round
            fun o(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x, y)
            sec.lanes.forEachIndexed { idx, lane ->
                val f0 = idx / n.toFloat(); val f1 = (idx + 1) / n.toFloat()
                val active = if (anyFollow) lane.follow != null else lane.dirs.any { laneMatches(it, maneuver) }
                if (active) {
                    val glow = androidx.compose.ui.graphics.Path().apply { moveTo(xAt(f0, 0f), 0f); lineTo(xAt(f1, 0f), 0f); lineTo(xAt(f1, h), h); lineTo(xAt(f0, h), h); close() }
                    drawPath(glow, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x5534F5D0), Color(0x9934F5D0))), alpha = a)
                }
                // dashed lane line on the left of each lane (solid edges at the sides)
                if (idx > 0) {
                    var y = 0f
                    val dash = 14.dp.toPx(); val gap = 10.dp.toPx()
                    while (y < h) { drawLine(Color.White.copy(alpha = 0.85f * a), o(xAt(f0, y), y), o(xAt(f0, minOf(h, y + dash)), minOf(h, y + dash)), 4.dp.toPx()); y += dash + gap }
                }
                val cx = xAt((f0 + f1) / 2f, h * 0.55f)
                val dirs = lane.dirs.sortedBy { d -> if (if (anyFollow) d == lane.follow else laneMatches(d, maneuver)) 1 else 0 }
                // Each colour's arrows go into one outline, stroked once: drawn as separate see-through lines, the faded arrows were
                // brighter where the lines overlapped, at the joints and where two arrows share a stem (mjdshibly #231)
                val outlines = LinkedHashMap<Float, androidx.compose.ui.graphics.Path>()
                dirs.forEach { d ->
                    val hit = if (anyFollow) d == lane.follow else laneMatches(d, maneuver)
                    val alphaOf = if (active && hit) 1f else if (active) 0.5f else 0.35f
                    val path = outlines.getOrPut(alphaOf) { androidx.compose.ui.graphics.Path() }
                    val baseY = h - 12.dp.toPx(); val midY = h * 0.5f
                    val len = 26.dp.toPx()
                    val hd = minOf(12.dp.toPx(), laneW * 0.2f).coerceAtLeast(8.dp.toPx())
                    // (the head turned by the lane's angle round the middle of the stem, as the canvas rotation did before)
                    val rad = Math.toRadians(laneAngle(d).toDouble())
                    val cs = kotlin.math.cos(rad).toFloat(); val sn = kotlin.math.sin(rad).toFloat()
                    fun rot(x: Float, y: Float): androidx.compose.ui.geometry.Offset {
                        val dx = x - cx; val dy = y - midY
                        return o(cx + dx * cs - dy * sn, midY + dx * sn + dy * cs)
                    }
                    val tip = rot(cx, midY - len); val l = rot(cx - hd, midY - len + hd); val r = rot(cx + hd, midY - len + hd)
                    path.moveTo(cx, baseY); path.lineTo(cx, midY); path.lineTo(tip.x, tip.y)
                    path.moveTo(l.x, l.y); path.lineTo(tip.x, tip.y); path.lineTo(r.x, r.y)
                }
                // (the faded ones first, the lit one on top)
                for ((al, path) in outlines.entries.sortedBy { it.key }) drawPath(path, Color.White.copy(alpha = al * a),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            // solid road edges
            drawLine(Color.White.copy(alpha = 0.95f * a), o(xAt(0f, 0f), 0f), o(0f, h), 5.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.95f * a), o(xAt(1f, 0f), 0f), o(w, h), 5.dp.toPx())
        }
        if (advice != null) Text(advice, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 16.dp, end = 16.dp))
    }
}

/** Mapbox's junction pictures, fetched once each and kept (a few) for the drive. */
internal object JunctionPics {
    private val cache = object : LinkedHashMap<String, android.graphics.Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, android.graphics.Bitmap>?) = size > 6
    }
    private val failed = HashSet<String>()

    fun cached(url: String): android.graphics.Bitmap? = synchronized(cache) { cache[url] }

    suspend fun load(url: String, token: String): android.graphics.Bitmap? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        synchronized(cache) { cache[url] }?.let { return@withContext it }
        if (url in failed || token.isBlank()) return@withContext null
        try {
            val full = url + (if ('?' in url) "&" else "?") + "access_token=" + java.net.URLEncoder.encode(token, "UTF-8")
            val c = java.net.URL(full).also { com.headunit.launcher.Net.check() }.openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 10000
            if (c.responseCode !in 200..299) { failed += url; return@withContext null }
            val b = c.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) } ?: run { failed += url; return@withContext null }
            synchronized(cache) { cache[url] = b }
            b
        } catch (e: Exception) { null }
    }
}


/**
 * "Demo" on the route choice (with Settings > Performance > Demo drive button on): lit, the countdown or Start begins this trip as
 * a demo drive, played along the route, instead of following the GPS.
 */
@Composable
internal fun DemoChoiceBtn(engine: NavEngine, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 72.dp) {
    val c = LocalVw.current
    val on = engine.demoNext
    // (styled like Fly over route beside it; filled once chosen)
    Row(
        modifier.height(height).clip(RoundedCornerShape(height / 2)).background(if (on) c.accent else c.accent.copy(alpha = 0.14f))
            .clickable { engine.toggleDemoNext() }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.DirectionsCar, null, tint = if (on) Color.White else c.accent, modifier = Modifier.size(24.dp))
        Text(L.t("Demo"), color = if (on) Color.White else c.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
    }
}

/**
 * The price set in Settings > Fuel, in cents or pence (per litre, or per kWh for electric), or null when blank. The box asks for
 * cents, but a price per litre typed in euros or pounds ("2.15" or "2,15") is understood too: no fuel costs under 10 cents a
 * litre, so anything under 10 is taken as euros or pounds (smikkelvriend: 2.15 gave 3 cents for a 30-minute drive). For
 * electricity, anything under 2 (no one pays 2 cents a kWh).
 */
/**
 * The fuel the trip has cost so far and how far you've driven ("£1.84 so far · 23.4 km driven"), under the trip card's figures
 * (Settings › Places and trips › Live fuel cost, ZarulOfficial #316). Nothing when it's off or no economy and price are set.
 */
@Composable
internal fun LiveFuelLine(engine: NavEngine, modifier: Modifier = Modifier, column: Boolean = false) {
    if (!engine.liveFuelOn()) return
    // (to the nearest 100 m: the line doesn't need redrawing for every metre)
    val driven = Math.round(engine.drivenThisRoute / 100.0) * 100.0
    val cost = engine.fuelCostOf(driven) ?: return
    val c = LocalVw.current
    val ev = engine.prefs.s(PreferenceKeys.FUEL_TYPE, "E10") == "EV"
    val costText = L.t("{0} so far", formatMoney(engine.prefs, cost))
    val distanceText = L.t("{0} driven", Geo.fmtDist(driven, engine.imperial))
    if (column) {
        Column(modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.width(100.dp).height(1.dp).background(c.line))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (ev) Icons.Rounded.EvStation else Icons.Rounded.LocalGasStation, null, tint = c.sub, modifier = Modifier.size(14.dp))
                Text(costText, color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DirectionsCar, null, tint = c.sub, modifier = Modifier.size(14.dp))
                Text(distanceText, color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp))
            }
        }
    } else Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ev) Icons.Rounded.EvStation else Icons.Rounded.LocalGasStation, null, tint = c.sub, modifier = Modifier.size(14.dp))
        Text("$costText · $distanceText", color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp))
    }
}

internal fun pricePence(raw: String, ev: Boolean, prefs: com.headunit.launcher.Prefs? = null): Double? {
    val v = raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: return null
    // (rupees, kroner, forints, yen...: the price is typed as normally written, in the main unit; bijindas: ₹107.40 came out as paise)
    if (prefs != null && !smallUnitPrices(moneyCurrency(prefs))) return v * 100.0
    return if (v < (if (ev) 2.0 else 10.0)) v * 100.0 else v
}

/**
 * The money fuel and charging costs are shown in: the currency of the country you're driving in (India: rupees), else the phone's.
 * (It used to be the phone's language alone, so an Indian driver with English (UK) set saw pounds.)
 */
internal fun moneyCurrency(prefs: com.headunit.launcher.Prefs): java.util.Currency {
    val cc = prefs.s(com.headunit.launcher.PreferenceKeys.COUNTRYDETECTED, "").trim().uppercase()
    if (cc.length == 2) try { return java.util.Currency.getInstance(java.util.Locale("", cc)) } catch (e: Exception) { }
    return try { java.util.Currency.getInstance(java.util.Locale.getDefault()) } catch (e: Exception) { java.util.Currency.getInstance("GBP") }
}

/** Currencies where a litre costs under 10 of the main unit, so the price box takes pence or cents (as it always has). */
internal fun smallUnitPrices(c: java.util.Currency): Boolean = c.currencyCode in setOf("GBP", "EUR", "USD", "CHF", "AUD", "CAD", "NZD", "GIP", "IMP", "JEP")

/** An amount of money in [moneyCurrency], written the phone's way ("£2.10", "₹225.40"). */
internal fun formatMoney(prefs: com.headunit.launcher.Prefs, amount: Double): String {
    val cur = moneyCurrency(prefs)
    val loc = if (cur.currencyCode == "GBP") java.util.Locale.UK else java.util.Locale.getDefault()
    return java.text.NumberFormat.getCurrencyInstance(loc).apply { currency = cur }.format(amount)
}


/**
 * A round − or + button like [Pill]: a tap is one step, and held down it keeps stepping, faster the longer it's held. [done]
 * runs once, when it's let go (so the charging plan is worked out again once, not at every step).
 */
@Composable
private fun StepPill(text: String, step: () -> Unit, done: () -> Unit) {
    val c = LocalVw.current
    val scope = rememberCoroutineScope()
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(c.tile)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    step()
                    val job = scope.launch {
                        kotlinx.coroutines.delay(450)
                        var gap = 120L
                        while (true) { step(); kotlinx.coroutines.delay(gap); gap = (gap - 8).coerceAtLeast(45) }
                    }
                    tryAwaitRelease()
                    job.cancel()
                    done()
                })
            }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp) }
}
