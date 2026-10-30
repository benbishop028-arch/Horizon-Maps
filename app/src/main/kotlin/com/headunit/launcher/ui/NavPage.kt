package com.headunit.launcher.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
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
    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    val baseMs = if (engine.departAtMs > 0) engine.departAtMs else System.currentTimeMillis()
    Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 4.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val selRoute = rs.getOrNull(engine.active)
    // fuel estimate from the economy and pump price set in Settings > Driving (blank = not shown)
    val ev = engine.prefs.s(PreferenceKeys.FUEL_TYPE, "E10") == "EV"
    val mpg = engine.prefs.s(if (ev) "evmpk" else "fuelmpg", "").toDoubleOrNull()?.takeIf { it > 0 }
    val ppl = engine.prefs.s(if (ev) "evppk" else "fuelppl", "").toDoubleOrNull()?.takeIf { it > 0 }
    val costText = if (mpg != null && ppl != null && selRoute != null)
        (if (ev) "Charging about " else "Fuel about ") + java.text.NumberFormat.getCurrencyInstance(java.util.Locale.UK).format(
            if (ev) selRoute.len / 1609.344 / mpg * ppl / 100.0 else selRoute.len / 1609.344 / mpg * 4.54609 * ppl / 100.0) else null
    ZonesRow(engine)
    if (engine.routeWx.isNotEmpty()) {
        val fahr = isImperial(engine.prefs)
        Row(
            Modifier.floating(50.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Weather on route", color = c.sub, fontSize = 13.sp)
            engine.routeWx.forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(wxIcon(w.code), null, tint = wxTint(w.code), modifier = Modifier.size(24.dp))
                    Column(Modifier.padding(start = 6.dp)) {
                        Text((if (fahr) Math.round(w.temp * 9 / 5 + 32) else Math.round(w.temp)).toString() + "°", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("in ${if (w.minutes >= 60) "${w.minutes / 60} h ${w.minutes % 60} min" else "${w.minutes} min"}" + (if (w.rain >= 40) " · ${w.rain}%" else ""), color = if (w.rain >= 60) Warn else c.sub, fontSize = 11.sp)
                    }
                }
            }
        }
    }
    Row(
        Modifier.floating(50.dp).horizontalScroll(rememberScrollState()).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Stops", color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp))
        Pill("+ Fuel", engine.stopMode && engine.nearbyKind == "fuel", { engine.stopMode = true; engine.loadNearby("fuel", false) })
        Pill("+ Charger", engine.stopMode && engine.nearbyKind == "charging", { engine.stopMode = true; engine.loadNearby("charging", false) })
        Pill(if (engine.via.isEmpty()) "+ Stop" else "Edit stops", false, { engine.stopsEditor = true })
        if (engine.via.isNotEmpty()) Text(engine.via.joinToString(" › ") { it.name }, color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
        if (costText != null) Text(costText, color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
        engine.destRain?.let { Text(it, color = Warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp)) }
    }
    Row(
        Modifier.floating(50.dp).horizontalScroll(rememberScrollState()).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Leave", color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp))
        for ((label, m) in listOf("Now" to 0, "+15 min" to 15, "+30 min" to 30, "+1 h" to 60, "+2 h" to 120)) {
            Pill(label, engine.departMin == m, { if (engine.departMin != m) engine.setDeparture(m) })
        }
        Pill("Arrive by…", hold, { arriveBy = true })
    }
    // electric: the battery now, the least you want to arrive with, and the eco route; a charging stop is then suggested
    // below if the trip needs one
    if (ev) Row(
        Modifier.floating(50.dp).horizontalScroll(rememberScrollState()).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val pct = engine.prefs.i(PreferenceKeys.EV_CHARGE_PCT, 80).coerceIn(5, 100)
        val minArr = engine.prefs.i(PreferenceKeys.EV_MIN_ARRIVE, 20)
        Text("Battery now", color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp))
        Pill("−", false, { engine.prefs.put(PreferenceKeys.EV_CHARGE_PCT, (pct - 10).coerceAtLeast(5)); engine.refreshEvPlan() })
        Text("$pct%", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
        Pill("+", false, { engine.prefs.put(PreferenceKeys.EV_CHARGE_PCT, (pct + 10).coerceAtMost(100)); engine.refreshEvPlan() })
        Text("Arrive with at least", color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(start = 14.dp, end = 4.dp))
        for (m in listOf(10, 20, 30)) Pill("$m%", minArr == m, { engine.prefs.put(PreferenceKeys.EV_MIN_ARRIVE, m); engine.refreshEvPlan() })
        val eco = engine.prefs.b(PreferenceKeys.ROUTE_ECO, false)
        Pill(if (eco) "Eco route ✓" else "Eco route", eco, { engine.prefs.put(PreferenceKeys.ROUTE_ECO, !eco); engine.rerouteForSettings() })
    }
    FuelSuggestionRow(engine)
    // landscape: everything in one row. Portrait (narrow): destination on top, the route choices across, Start underneath.
    val narrowP = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 720
    val destPart: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Text("Destination", color = c.sub, fontSize = 12.sp)
            Text(engine.dest?.name ?: "", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val d = engine.dest
            if (d != null) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val fav = engine.places.isFavourite(d)
                Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favourite", tint = if (fav) Warn else c.sub,
                    modifier = Modifier.size(26.dp).clickable { engine.toast(if (engine.places.toggleFavourite(d)) "Saved to favourites" else "Removed from favourites", 2000) })
                Icon(Icons.Rounded.Home, "Set as home", tint = c.sub,
                    modifier = Modifier.size(26.dp).clickable { engine.places.setSlot("home", d); engine.toast("Home set to ${d.name}", 2500) })
                Icon(Icons.Rounded.Work, "Set as work", tint = c.sub,
                    modifier = Modifier.size(26.dp).clickable { engine.places.setSlot("work", d); engine.toast("Work set to ${d.name}", 2500) })
                var saving by remember { mutableStateOf(false) }
                val mine = engine.places.customAt(d) != null
                Icon(Icons.Rounded.BookmarkAdd, "Save with a name", tint = if (mine) c.accent else c.sub, modifier = Modifier.size(26.dp).clickable { saving = true })
                // paired with your car (Settings > Places & trips): send it there instead of driving it from here
                if (engine.prefs.s(PreferenceKeys.LINK_ROLE, "") == "phone" && engine.prefs.s(PreferenceKeys.LINK_CODE, "").isNotBlank())
                    Icon(Icons.Rounded.DirectionsCar, "Send to car", tint = c.accent, modifier = Modifier.size(26.dp).clickable { engine.sendToCar(d) })
                if (saving) AddPlaceDialog(engine, d) { saving = false }
            }
        }
    }
    val routeCards: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        rs.forEachIndexed { i, r ->
            val sel = i == engine.active
            val shape = RoundedCornerShape(18.dp)
            val label = if (i == fastest) "Fastest" else if (i == shortest) "Shortest" else "Alternative"
            Column(
                Modifier.weight(1f).inset(18.dp, if (sel) c.accent else null)
                                        .clickable { engine.selectRoute(i) }.padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(label.uppercase(), color = if (sel) c.accent else c.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(Geo.fmtDur(r.time), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${Geo.fmtDist(r.len, engine.imperial)} · arrive ${fmt.format(java.util.Date(baseMs + (r.time * 1000).toLong()))}", color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val via = viaOf(r)
                val delayMin = Math.round(r.delay / 60.0).toInt()
                Text(
                    if (delayMin >= 1) "+$delayMin min traffic" else if (via.isNotEmpty()) "via $via" else "No delays",
                    color = if (delayMin >= 10) Bad else if (delayMin >= 1) Warn else c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
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
                Text("Start navigation", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(if (hold) "Planned for later" else if (autoStart) "Starting in ${kotlin.math.ceil((1f - progress.value) * (countdownMs / 1000f)).toInt()} s" else "Tap to start", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
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
        startBtn(Modifier.width(210.dp))
        RoundBtn(Icons.Rounded.Close, { engine.endRoute() }, size = 46.dp)
    } else Column(Modifier.fillMaxWidth().floating(28.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            destPart(Modifier.weight(1f))
            RoundBtn(Icons.Rounded.Close, { engine.endRoute() }, size = 46.dp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { routeCards() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (engine.prefs.b(PreferenceKeys.FLYOVER, true)) FlyOverBtn(engine)
            startBtn(Modifier.weight(1f))
        }
    }
    }
}

/** "Fly over route": a 3D flight along the whole drive before you set off. */
@Composable
private fun FlyOverBtn(engine: NavEngine) {
    val c = LocalVw.current
    Row(
        Modifier.height(72.dp).clip(RoundedCornerShape(36.dp)).background(c.accent.copy(alpha = 0.14f))
            .clickable(enabled = !engine.flyingOver) { engine.flyOver() }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PlayArrow, null, tint = c.accent, modifier = Modifier.size(24.dp))
        Text(if (engine.flyingOver) "Flying…" else "Fly over route", color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
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
internal fun ArrivalCard(engine: NavEngine, a: ArrivalInfo, modifier: Modifier = Modifier) {
    val c = LocalVw.current
    val imp = engine.imperial
    val k = if (imp) 2.23694 else 3.6
    Column(
        modifier.widthIn(min = 380.dp, max = 460.dp).floating(28.dp).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Good.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, null, tint = Good, modifier = Modifier.size(38.dp))
        }
        Text("You've arrived", color = c.sub, fontSize = 14.sp)
        Text(a.name, color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (a.side.isNotEmpty()) Text("On the ${a.side}", color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ArrivalStat(Geo.fmtDist(a.dist, imp), "distance")
            ArrivalStat(Geo.fmtDur(a.secs), "time")
            ArrivalStat("${Math.round(a.avgMs * k)} ${if (imp) "mph" else "km/h"}", "average")
        }
        engine.arrivalParking?.let { pk ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.tile).clickable { engine.dismissArrival(); engine.startRoute(pk.place) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.LocalParking, null, tint = Color(0xFF4C8DFF), modifier = Modifier.size(26.dp))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Nearest parking", color = c.sub, fontSize = 12.sp)
                    Text(pk.place.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(Geo.fmtDist(pk.dist, imp), color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
                Text("Go", color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            a.place?.let { p ->
                var fav by remember { mutableStateOf(engine.places.isFavourite(p)) }
                Pill(if (fav) "Saved" else "Save place", fav, { fav = engine.places.toggleFavourite(p) })
            }
            // the way back to where this trip started (home, if it started there)
            if (engine.tripFrom != null && engine.prefs.b(PreferenceKeys.ROUTEBACK, true)) Pill("Route back", false, { engine.routeBack() })
            Pill("Done", true, { engine.dismissArrival() })
        }
    }
}

private fun nearbyIcon(kind: String?): androidx.compose.ui.graphics.vector.ImageVector = when (kind) {
    "fuel" -> Icons.Rounded.LocalGasStation
    "charging" -> Icons.Rounded.EvStation
    "food" -> Icons.Rounded.Restaurant
    "coffee", "services" -> Icons.Rounded.LocalCafe
    "toilets" -> Icons.Rounded.Wc
    else -> Icons.Rounded.LocalParking
}

private fun nearbyTitle(kind: String?): String = when (kind) {
    "fuel" -> "Fuel stations"; "charging" -> "Charging points"; "food" -> "Food"; "coffee" -> "Coffee"
    "toilets" -> "Toilets"; "services" -> "Services"; else -> "Car parks"
}

@Composable
internal fun NearbyCard(engine: NavEngine) {
    val c = LocalVw.current
    val adding = engine.stopMode || engine.route != null
    val along = engine.nearby.any { it.ahead }
    val title = (if (adding) "Add a stop: " else "") + nearbyTitle(engine.nearbyKind) + (if (along) " ahead" else "")
    val hasPrices = engine.nearby.any { it.price != null }
    Column(Modifier.floating(24.dp).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(nearbyIcon(engine.nearbyKind), c.accent, Modifier.padding(end = 12.dp), 36.dp)
            Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (engine.nearbyBusy) CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
            Icon(Icons.Rounded.Close, "Close", tint = c.sub, modifier = Modifier.padding(start = 12.dp).size(22.dp).clickable { engine.clearNearby() })
        }
        if (hasPrices) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(if (along) "Soonest" else "Nearest", engine.nearbySort != "cheap", { engine.changeNearbySort("near") })
            Pill("Cheapest", engine.nearbySort == "cheap", { engine.changeNearbySort("cheap") })
            Text(com.headunit.launcher.FuelFinder.fuelName(engine.prefs.s(PreferenceKeys.FUEL_TYPE, "E10")), color = c.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
        }
        LazyColumn(Modifier.heightIn(max = 230.dp).padding(top = 6.dp)) {
            items(engine.nearby) { n ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { if (adding) engine.addStop(n.place) else engine.startRoute(n.place) }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(nearbyIcon(engine.nearbyKind), null, tint = c.accent, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(n.place.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(n.extra.takeIf { it.isNotBlank() }, n.place.sub.takeIf { it.isNotBlank() && engine.nearbyKind == "fuel" }).joinToString(" · ")
                        if (sub.isNotBlank()) Text(sub, color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (n.price != null) Text(com.headunit.launcher.fmtFuelPrice(n.price, n.unit), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 10.dp)) {
                        Text(Geo.fmtDist(n.dist, engine.imperial), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp))
                        if (n.detourMin >= 0) Text("+${n.detourMin} min", color = c.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        if (hasPrices) Text("Prices: Fuel Finder (gov.uk)", color = c.sub, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
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
@Composable
internal fun LaneHero(sec: LaneSec, maneuver: String, advice: String?) {
    val anyFollow = sec.lanes.any { it.follow != null }
    val appear by androidx.compose.animation.core.animateFloatAsState(1f, androidx.compose.animation.core.tween(380), label = "heroIn")
    val n = sec.lanes.size
    Column(Modifier.fillMaxWidth().background(Color(0xFF0E2A30)).padding(top = 10.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(118.dp).padding(horizontal = 14.dp)) {
            val a = appear.coerceIn(0f, 1f)
            val w = size.width; val h = size.height
            // the road narrows a little towards the top, as if seen from the driver's seat
            val topInset = w * 0.08f
            fun xAt(frac: Float, y: Float): Float { val t = y / h; val ins = topInset * (1f - t); return ins + frac * (w - 2 * ins) }
            val road = androidx.compose.ui.graphics.Path().apply { moveTo(xAt(0f, 0f), 0f); lineTo(xAt(1f, 0f), 0f); lineTo(w, h); lineTo(0f, h); close() }
            drawPath(road, Color(0xFF233B42).copy(alpha = a))
            val stroke = 6.dp.toPx()
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
                    while (y < h) { drawLine(Color.White.copy(alpha = 0.75f * a), o(xAt(f0, y), y), o(xAt(f0, minOf(h, y + dash)), minOf(h, y + dash)), 2.5.dp.toPx()); y += dash + gap }
                }
                val cx = xAt((f0 + f1) / 2f, h * 0.55f)
                val dirs = lane.dirs.sortedBy { d -> if (if (anyFollow) d == lane.follow else laneMatches(d, maneuver)) 1 else 0 }
                dirs.forEach { d ->
                    val hit = if (anyFollow) d == lane.follow else laneMatches(d, maneuver)
                    val col = if (active && hit) Color.White else Color.White.copy(alpha = if (active) 0.45f else 0.28f)
                    val baseY = h - 12.dp.toPx(); val midY = h * 0.5f
                    val len = 26.dp.toPx()
                    drawLine(col.copy(alpha = col.alpha * a), o(cx, baseY), o(cx, midY), stroke, round)
                    rotate(laneAngle(d), o(cx, midY)) {
                        val tip = o(cx, midY - len)
                        drawLine(col.copy(alpha = col.alpha * a), o(cx, midY), tip, stroke, round)
                        val hd = 10.dp.toPx()
                        drawLine(col.copy(alpha = col.alpha * a), tip, o(cx - hd, tip.y + hd), stroke, round)
                        drawLine(col.copy(alpha = col.alpha * a), tip, o(cx + hd, tip.y + hd), stroke, round)
                    }
                }
            }
            // solid road edges
            drawLine(Color.White.copy(alpha = 0.9f * a), o(xAt(0f, 0f), 0f), o(0f, h), 3.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.9f * a), o(xAt(1f, 0f), 0f), o(w, h), 3.dp.toPx())
        }
        if (advice != null) Text(advice, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 16.dp, end = 16.dp))
    }
}
