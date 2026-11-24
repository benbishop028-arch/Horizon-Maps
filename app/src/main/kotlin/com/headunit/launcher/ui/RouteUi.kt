package com.headunit.launcher.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material.icons.rounded.LocalConvenienceStore
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import com.headunit.launcher.L
import com.headunit.launcher.NavigationPolicy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.TurnSlightRight
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.headunit.launcher.Geo
import com.headunit.launcher.NavEngine
import com.headunit.launcher.Place
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import com.headunit.launcher.PreferenceKeys

/** True while a route is being followed (not just previewed). */
private val NavEngine.guiding: Boolean get() = route != null && !previewing

/** The route bar is shown on the right while navigating (it can be switched off in Settings). */
val NavEngine.showRouteBar: Boolean get() = guiding && prefs.b(PreferenceKeys.ROUTEBAR, true)

/** Driving with no route: just the road, your speed and the alerts. */
val NavEngine.cruising: Boolean get() = route == null && !previewing && (fix?.speed ?: 0.0) > 4.0

private fun barIcon(kind: String): ImageVector = when (kind) {
    "camera" -> Icons.Rounded.CameraAlt
    "works" -> Icons.Rounded.Construction
    "incident" -> Icons.Rounded.PriorityHigh
    "services" -> Icons.Rounded.LocalCafe
    "avg" -> Icons.Rounded.Speed
    "stop" -> Icons.Rounded.Place
    "zone" -> Icons.Rounded.Eco
    else -> Icons.Rounded.Flag
}

private fun barColor(kind: String): Color = when (kind) {
    "camera", "avg" -> Bad
    "works", "incident" -> Warn
    "services" -> Color(0xFF4C8DFF)
    "zone" -> Good
    else -> Color(0xFF8E5CF0)
}

/**
 * The whole route as a strip above the trip card: traffic in amber and red, markers for cameras, incidents, services,
 * average speed checks, stops and zones, and an arrow that moves along it as you drive (start on the left, destination on the right).
 */
@Composable
fun RouteBar(engine: NavEngine, modifier: Modifier = Modifier, thin: Boolean = false) {
    val r = engine.route ?: return
    val c = LocalVw.current
    val len = max(1.0, r.len)
    val target = (engine.barProgress / len).coerceIn(0.0, 1.0).toFloat()
    // progress arrives about once a second; the arrow glides between updates (only the drawing is redone, nothing is recomposed)
    val frac by androidx.compose.animation.core.animateFloatAsState(target, androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing), label = "barArrow")
    val bins = 120
    val levels = remember(r, r.sev.size, engine.barEvents) {
        val out = IntArray(bins)
        for (i in r.sev.indices) {
            if (i + 1 >= r.cum.size) break
            // (every bin the stretch reaches into, not just the one it starts in)
            val b0 = ((r.cum[i] / len) * bins).toInt().coerceIn(0, bins - 1)
            val b1 = ((r.cum[i + 1] / len) * bins).toInt().coerceIn(b0, bins - 1)
            for (b in b0..b1) if (r.sev[i] > out[b]) out[b] = r.sev[i]
        }
        out
    }
    // (blended: each bin's level is averaged with the bins a little either side, so the colours run into each other where the
    // traffic changes instead of stepping, #334; the same colours as before)
    val blend = engine.prefs.b(com.headunit.launcher.PreferenceKeys.BLEND_TRAFFIC, true)
    // (each patch of traffic is first made a little wider, so a short one on a long trip, under a bin's width of the bar, still shows
    // in its own colour and isn't averaged away into the blue, GitHub #336; the blending is then only at its edges)
    val smooth = remember(levels, blend) {
        val wide = IntArray(bins) { b -> var m = 0; for (k in -2..2) { val i = b + k; if (i in 0 until bins && levels[i] > m) m = levels[i] }; m }
        FloatArray(bins) { b -> if (!blend) wide[b].toFloat() else {
            var sum = 0f; var wsum = 0f
            for (k in -1..1) { val i = b + k; if (i !in 0 until bins) continue; val w = 2f - kotlin.math.abs(k); sum += wide[i] * w; wsum += w }
            sum / wsum } }
    }
    val accent = c.accent
    val theme = engine.map.theme
    // [thin]: the slimmer bar on the car screen (smaller markers, a thinner track and a smaller arrow)
    // (the locator is drawn large for the map; shrunk to the arrow's size in halves here, once, so its edges stay smooth: shrunk in
    // one go while drawing, from 384 px to about 60, it came out jagged)
    val arrowPx = with(androidx.compose.ui.platform.LocalDensity.current) { (if (thin) 22.dp else 32.dp).roundToPx() }
    val locator = remember(theme, arrowPx) { shrinkSmooth(engine.map.locatorBitmap(), arrowPx).asImageBitmap() }
    val dot = if (thin) 16.dp else 20.dp
    BoxWithConstraints(modifier.height(if (thin) 34.dp else 48.dp)) {
        val w = maxWidth
        // the track, with the part already driven greyed out, and the arrow
        Canvas(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(if (thin) 18.dp else 26.dp)) {
            // (thicker, so the traffic colours can be told apart: the thin bar was hard to read, GitHub #336)
            val th = (if (thin) 6.dp else 11.dp).toPx()
            val y0 = (size.height - th) / 2f
            val bw = size.width / bins
            drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(0f, y0 - 1.5f), Size(size.width, th + 3f), CornerRadius(th))
            // the coloured bar has round ends (it used to be square inside the rounded dark track)
            val ends = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, y0, size.width, y0 + th, CornerRadius(th / 2f)))
            }
            clipPath(ends) {
                for (b in 0 until bins) {
                    val done = (b + 1f) / bins <= frac
                    val v = smooth[b]
                    val col = if (v <= 1f) androidx.compose.ui.graphics.lerp(accent, Warn, v) else androidx.compose.ui.graphics.lerp(Warn, Bad, (v - 1f).coerceAtMost(1f))
                    drawRect(if (done) Color(0xFF8A8F99) else col, Offset(b * bw, y0), Size(bw + 0.6f, th))
                }
            }
            // the arrow: the map's own locator, shrunk and turned to point along the bar
            val sz = arrowPx.toFloat()
            val ax = (frac * size.width).coerceIn(sz / 2f, size.width - sz / 2f)
            val cy = size.height / 2f
            rotate(90f, Offset(ax, cy)) {
                drawImage(
                    locator, dstOffset = androidx.compose.ui.unit.IntOffset((ax - sz / 2f).toInt(), (cy - sz / 2f).toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(arrowPx, arrowPx),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                )
            }
        }
        // markers above the track (ones that would sit on top of each other are left out)
        var lastX = -100f
        for (e in engine.barEvents.sortedBy { it.off }) {
            val f = (e.off / len).coerceIn(0.0, 1.0).toFloat()
            val x = w.value * f
            if (x - lastX < 22f) continue
            lastX = x
            Box(
                Modifier.offset(x = (w * f - dot / 2).coerceIn(0.dp, w - dot), y = 0.dp).size(dot).shadow(3.dp, CircleShape).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) { Icon(barIcon(e.kind), null, tint = barColor(e.kind), modifier = Modifier.size(dot * 0.65f)) }
        }
        Box(
            Modifier.offset(x = w - dot, y = 0.dp).size(dot).shadow(3.dp, CircleShape).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Flag, null, tint = Color(0xFFEA4335), modifier = Modifier.size(dot * 0.65f)) }
    }
}

/** Card for an average speed check: your average so far against the limit, and how much is left. */
@Composable
fun AvgSpeedCard(engine: NavEngine, modifier: Modifier = Modifier) {
    val a = engine.avgInfo ?: return
    val c = LocalVw.current
    val k = if (engine.imperial) 2.23694 else 3.6
    val unit = if (engine.imperial) "mph" else "km/h"
    val shape = RoundedCornerShape(22.dp)
    // (deeper red, amber and green on a light card, so they read in daylight: magnazan, #177)
    val red = delayTint(10, c.surface); val amber = delayTint(5, c.surface); val green = delayTint(0, c.surface)
    Row(
        modifier.fillMaxWidth().panel(8.dp, shape, c.surface).border(2.dp, if (a.over) red else Color.Transparent, shape).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val lim = a.limitMs?.let { (Math.round(it * k / 5.0) * 5).toInt() }
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White).border(5.dp, Bad, CircleShape), contentAlignment = Alignment.Center) {
            Text(lim?.toString() ?: "?", color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(if (a.ahead) L.t("AVERAGE SPEED CHECK AHEAD") else L.t("AVERAGE SPEED CHECK"), color = red, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            if (a.ahead) Text(L.t("Starts in {0} · {1} long", Geo.fmtDist(a.toStartM, engine.imperial), Geo.fmtDist(a.leftM, engine.imperial)), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            else {
                Text("${Geo.fmtDist(a.leftM, engine.imperial)} left", color = c.sub, fontSize = 13.sp)
                // how far through the check you are, as a bar (GitHub #268): green, amber once you need to ease off for the rest,
                // red over the limit, like the card's border
                if (a.lenM > 50.0) {
                    val done = (1.0 - a.leftM / a.lenM).coerceIn(0.0, 1.0).toFloat()
                    val lmB = a.limitMs; val sfB = a.safeMs
                    val barCol = when {
                        a.over -> red
                        lmB != null && sfB != null && Math.floor(sfB * k) < Math.round(lmB * k) -> amber
                        else -> green
                    }
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.line)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(done).clip(RoundedCornerShape(5.dp)).background(barCol))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                        Text(L.t("Start"), color = c.sub, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text(L.t("End camera"), color = c.sub, fontSize = 11.sp)
                    }
                }
                // what you can do from here and still come out under the limit (only worth saying once it differs from the limit)
                val safe = a.safeMs
                val lm = a.limitMs
                if (safe != null && lm != null) {
                    val sv = Math.floor(safe * k).toInt()
                    val lv = Math.round(lm * k).toInt()
                    when {
                        safe <= 0.5 || sv < lv - 25 -> Text(L.t("Slow down to bring your average back under"), color = red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        sv < lv -> Text(L.t("Keep under {0} {1} for the rest", sv, unit), color = amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        sv > lv -> Text(L.t("Up to {0} {1} keeps you under", minOf(sv, lv + 15), unit), color = c.sub, fontSize = 13.sp)
                        else -> {}
                    }
                }
            }
        }
        if (!a.ahead) Column(horizontalAlignment = Alignment.End) {
            Text("${Math.round(a.avgMs * k)}", color = if (a.over) red else green, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(L.t("avg {0}", unit), color = c.sub, fontSize = 11.sp)
        }
    }
}

/** The next motorway services on the route, with the fuel price there when Fuel Finder is set up. */
@Composable
fun ServicesCard(engine: NavEngine, modifier: Modifier = Modifier) {
    val list = engine.servicesAhead
    if (list.isEmpty() || engine.avgInfo != null || !engine.prefs.b(PreferenceKeys.SERVICESCARD, true)) return
    val c = LocalVw.current
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().panel(6.dp, shape, c.surface).padding(horizontal = 14.dp, vertical = 8.dp)) {
        // only the next services (the one after it is shown once you've passed this one)
        list.take(1).forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF4C8DFF)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.LocalCafe, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(s.name, color = c.text, fontSize = if (i == 0) 15.sp else 13.sp, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (s.fuel.isNotBlank()) Text(s.fuel, color = c.sub, fontSize = 11.sp)
                }
                Text(Geo.fmtDist(s.dist, engine.imperial), color = c.text, fontSize = if (i == 0) 15.sp else 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** About a mile from the end: car parks close to your destination, with how far each is to walk. Tap one to drive there instead. */
@Composable
fun ParkingCard(engine: NavEngine, modifier: Modifier = Modifier) {
    val list = engine.parkingNear
    if (list.isEmpty()) return
    val c = LocalVw.current
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().panel(6.dp, shape, c.surface).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L.t("Parking near {0}", engine.dest?.name ?: "your destination"), color = c.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(CircleShape).tapOn(engine, "park-x") { engine.parkingNear = emptyList() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, L.t("Close"), tint = c.sub, modifier = Modifier.size(16.dp))
            }
        }
        list.forEachIndexed { pi, n ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).tapOn(engine, "park-$pi") { engine.parkAt(n) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF2E6FD8)), contentAlignment = Alignment.Center) {
                    Text("P", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
                // the price, opening hours and spaces under the name, where they're known
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(n.place.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (n.extra.isNotBlank()) Text(n.extra, color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // a walk at about 80 m a minute
                Text(L.t("{0} min walk", maxOf(1, Math.round(n.dist / 80.0).toInt())), color = c.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, end = 8.dp))
            }
        }
    }
}

/** Shown while the GPS has dropped out (a tunnel): the arrow is being kept moving along the route. */
@Composable
fun DeadReckoningChip(modifier: Modifier = Modifier) {
    // the same day/night colours as the map's other cards
    val c = navVw(LocalVw.current)
    Row(
        modifier.panel(6.dp, RoundedCornerShape(50), c.surface).border(1.dp, c.line, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.GpsOff, null, tint = Warn, modifier = Modifier.size(18.dp))
        Text(L.t("No GPS · estimating your position"), color = c.text, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Driving with no route: the road you are on and which way you are heading. */
@Composable
fun CruisePill(engine: NavEngine, modifier: Modifier = Modifier) {
    val road = engine.roadNow
    val dir = com.headunit.launcher.RouteExtras.compass(engine.fix?.heading ?: 0.0)
    // light in the day, dark at night, like the map's other cards
    val c = navVw(LocalVw.current)
    Row(
        modifier.panel(6.dp, RoundedCornerShape(50), c.surface).border(1.dp, c.line, RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Navigation, null, tint = c.accent, modifier = Modifier.size(18.dp))
        // (the heading can be switched off on its own, liamstears)
        val withHeading = engine.prefs.b(com.headunit.launcher.PreferenceKeys.CRUISE_HEADING, true)
        Text(if (!withHeading) road else (if (road.isNotBlank()) "$road · " else "") + L.t("Heading {0}", dir), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * Edit the stops on the way, in a panel down the side with the map still showing (it was a box over the whole map): a category
 * shows the places of that kind along your route in the same panel, with the time each adds (as "See them along your route"),
 * rather than adding one unseen; or search, add one of your places, and move stops up or down or remove them. Opening it doesn't
 * move the map: only the list of places along the route zooms out to show them.
 */
@Composable
fun StopsPanel(engine: NavEngine, modifier: Modifier, onClose: () -> Unit) {
    val c = LocalVw.current
    // (picked up again when a stop is added from the list of places along the route)
    var stops by remember(engine.via) { mutableStateOf(engine.via) }
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(q) {
        if (q.trim().length < 3) { results = emptyList(); return@LaunchedEffect }
        delay(500)
        busy = true
        results = try { engine.findPlaces(q) } catch (e: Exception) { emptyList() }
        busy = false
    }
    // (the same look as the list of places along the route it opens: the same solid panel, round buttons, tiles and blue)
    val blue = ChainBlue
    Column(modifier.clip(RoundedCornerShape(28.dp)).background(c.surface.copy(alpha = 1f)).pointerInput(Unit) { detectTapGestures { } }
        .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // header: as the list's, with a round icon, the title and how many stops there are, and a round ✕
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(c.tile), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AddLocationAlt, null, tint = Color(0xFFEA4335), modifier = Modifier.size(24.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(if (stops.isEmpty() && engine.via.isEmpty()) L.t("Add a stop") else L.t("Stops on the way"), color = c.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(engine.dest?.name ?: L.t("Destination"), color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(42.dp).clip(CircleShape).background(c.tile).clickable { onClose() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, L.t("Close"), tint = c.text)
            }
        }
        // search first: it's what the keyboard comes up for, so it stays in view above it
        TextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text(L.t("Search for a stop to add")) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = c.sub) },
            trailingIcon = { if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = blue, strokeWidth = 2.dp) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // the keyboard's search key: everything matching along the route, on the map
            keyboardActions = KeyboardActions(onSearch = { if (q.trim().length >= 2 && engine.route != null && engine.dest != null) engine.openStopSearch(q.trim()) }),
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = c.tile, unfocusedContainerColor = c.tile, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = c.text, unfocusedTextColor = c.text, cursorColor = blue,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        // see where they all are on the route, with the time each adds
        if (q.trim().length >= 3 && engine.route != null && engine.dest != null) Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(blue.copy(alpha = 0.12f)).border(2.dp, blue, RoundedCornerShape(20.dp))
                .clickable { engine.openStopSearch(q.trim()) }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Map, null, tint = blue, modifier = Modifier.size(22.dp))
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(L.t("See them along your route"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(L.t("On the map, with the time each adds"), color = c.sub, fontSize = 12.sp)
            }
        }
        if (results.isNotEmpty()) Column(Modifier.clip(RoundedCornerShape(16.dp)).background(c.tile)) {
            results.forEach { p ->
                Row(Modifier.fillMaxWidth().clickable { stops = (stops + p).take(8); q = ""; results = emptyList() }.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, null, tint = blue, modifier = Modifier.size(22.dp))
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(p.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (p.sub.isNotBlank()) Text(p.sub, color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        // a kind of place: the ones along the route ahead, listed in this same spot with the map zoomed out to show them. Tiles in
        // the same block as the list's Least detour / Soonest / Nearest
        if (engine.dest != null && engine.route != null) {
            Text(L.t("Find along the route"), color = c.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            val ev = engine.prefs.s(PreferenceKeys.FUEL_TYPE, "E10") == "EV"
            // (searched for along the route by these words: the search services know them in any country)
            // the same round icons as under the search bar (each named for TalkBack)
            class Kind(val term: String, val name: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tint: Color)
            val kinds = listOfNotNull(if (ev) null else Kind("petrol station", L.t("Fuel"), Icons.Rounded.LocalGasStation, Color(0xFFEA4335)),
                Kind("EV charging station", L.t("Charger"), Icons.Rounded.EvStation, Color(0xFF34A853)), Kind("coffee", L.t("Coffee"), Icons.Rounded.LocalCafe, Color(0xFFA56BFF)),
                Kind("restaurant", L.t("Food"), Icons.Rounded.Restaurant, Color(0xFFF29900)), Kind("rest area", L.t("Services"), Icons.Rounded.LocalConvenienceStore, Color(0xFF00A3A3)),
                Kind("public toilet", L.t("Toilets"), Icons.Rounded.Wc, Color(0xFF4C8DFF)))
            // (each gets an equal share of the width, up to 56 dp, so six still fit on a small phone)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (k in kinds) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth().widthIn(max = 56.dp).aspectRatio(1f).clip(CircleShape).background(c.tile)
                        .clickable { engine.openStopSearch(k.term, title = k.name, category = true) }, contentAlignment = Alignment.Center) {
                        Icon(k.icon, k.name, tint = k.tint, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
        // your own places, added to the list below (then Update route)
        val mine = remember { listOfNotNull(engine.places.slot("home")?.let { L.t("Home") to it }, engine.places.slot("work")?.let { L.t("Work") to it }) +
            engine.places.favourites().take(4).map { "★ " + it.name to it } }
        if (mine.isNotEmpty()) {
            Text(L.t("Your places"), color = c.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((name, p) in mine) Box(Modifier.clip(RoundedCornerShape(12.dp)).background(c.tile).clickable { stops = (stops + p).take(8) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)) { Text(name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
            }
        }
        // the stops, then where you're going, as the list's rows
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            stops.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(blue), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(p.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // when you get there, on the route as it is (not once the list here has been changed)
                        if (stops === engine.via) engine.stopEtas.firstOrNull { it.place === p }?.let { s ->
                            Text(s.arrive + " · " + Geo.fmtDur(s.secs) + " · " + Geo.fmtDist(s.dist, engine.imperial), color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(Icons.Rounded.KeyboardArrowUp, L.t("Earlier"), tint = if (i > 0) c.text else c.sub.copy(alpha = 0.3f),
                        modifier = Modifier.size(36.dp).clickable(enabled = i > 0) { stops = stops.toMutableList().also { l -> val t = l[i]; l[i] = l[i - 1]; l[i - 1] = t } })
                    Icon(Icons.Rounded.KeyboardArrowDown, L.t("Later"), tint = if (i < stops.size - 1) c.text else c.sub.copy(alpha = 0.3f),
                        modifier = Modifier.size(36.dp).clickable(enabled = i < stops.size - 1) { stops = stops.toMutableList().also { l -> val t = l[i]; l[i] = l[i + 1]; l[i + 1] = t } })
                    Icon(Icons.Rounded.Close, L.t("Remove"), tint = Bad, modifier = Modifier.padding(start = 6.dp).size(30.dp).clickable { stops = stops.filterIndexed { n, _ -> n != i } })
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFEA4335)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Flag, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Text(engine.dest?.name ?: L.t("Destination"), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
            }
        }
        if (stops.isEmpty()) Text(L.t("No stops yet. Add one and the route goes through it on the way."), color = c.sub, fontSize = 13.sp)
        // (only once something's been changed here: a full-width blue button, as the list's Add as a stop)
        if (stops != engine.via) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(blue).clickable { engine.setStops(stops); onClose() }.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(L.t("Update route"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(c.tile).clickable { stops = engine.via }.padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(L.t("Undo changes"), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Route options row: the clean air / low emission zones this route goes into. */
@Composable
fun ZonesRow(engine: NavEngine) {
    val z = engine.routeZones
    if (z.isEmpty()) return
    val c = LocalVw.current
    Row(
        Modifier.floating(50.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Eco, null, tint = Warn, modifier = Modifier.size(20.dp))
        Text(L.t("Goes into ") + z.joinToString(", ") + L.t(". A charge may apply."), color = c.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (!engine.prefs.b(PreferenceKeys.AVOIDZONES, false)) Pill(L.t("Avoid zones"), false, { engine.prefs.put(PreferenceKeys.AVOIDZONES, true); engine.dest?.let { engine.startRoute(it, engine.departMin, true) } })
    }
}

// ------------------------------------------------------------------ night sky

/**
 * At night in 3D, a soft sky along the top of the map: a deep blue fade with stars that twinkle slowly. It only redraws twice a second.
 */
@Composable
fun NightSky(modifier: Modifier = Modifier) {
    val stars = remember { List(46) { floatArrayOf(Math.random().toFloat(), (Math.random() * Math.random()).toFloat(), 0.4f + Math.random().toFloat() * 0.6f, Math.random().toFloat() * 6.28f) } }
    var tick by remember { mutableStateOf(0) }
    // Two updates a second are enough for a subtle twinkle and keep extra map redraws low on slower head units.
    LaunchedEffect(Unit) { while (true) { delay(500); tick += 2 } }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xE6070B1F), Color(0x990B1433), Color.Transparent)), Offset.Zero, size)
        val t = tick * 0.125f
        for (s in stars) {
            val a = (0.35f + 0.45f * s[2] * (0.6f + 0.4f * kotlin.math.sin(t * 0.9f + s[3]))).coerceIn(0f, 1f) * (1f - s[1])
            drawCircle(Color.White.copy(alpha = a), (0.8f + 1.4f * s[2]) * density, Offset(s[0] * w, s[1] * h * 0.8f))
        }
    }
}

// ------------------------------------------------------------------ arrive by

/** Pick the time to arrive by; the app works out when to leave and reminds you. */
@Composable
fun ArriveByDialog(engine: NavEngine, onClose: () -> Unit) {
    val c = LocalVw.current
    val cal = remember {
        java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.MINUTE, 60 + (engine.route?.time ?: 0.0).toInt() / 60)
            set(java.util.Calendar.MINUTE, (get(java.util.Calendar.MINUTE) / 15) * 15)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
    }
    var ms by remember { mutableStateOf(cal.timeInMillis) }
    val f = com.headunit.launcher.Clock.format("EEE ")
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.panel).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(L.t("Arrive by"), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(engine.dest?.name ?: "", color = c.sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Pill("−15", false, { ms -= 15 * 60_000L })
                Text(f.format(java.util.Date(ms)), color = c.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Pill("+15", false, { ms += 15 * 60_000L })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("+1 h", false, { ms += 3_600_000L })
                Pill("−1 h", false, { ms -= 3_600_000L })
                Pill(L.t("Tomorrow 9:00"), false, {
                    ms = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1); set(java.util.Calendar.HOUR_OF_DAY, 9); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0) }.timeInMillis
                })
            }
            Text(L.t("Horizon Nav works out when to leave with the traffic expected then, checks again half an hour before, and reminds you 10 minutes before and when it's time to go (while the app is open)."), color = c.sub, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(L.t("Plan it"), true, { if (ms > System.currentTimeMillis()) { engine.planArriveBy(ms); onClose() } })
                Pill(L.t("Cancel"), false, onClose)
            }
        }
    }
}

/** On the map while idle: the planned trip with its leave time, to start it or cancel it. */
@Composable
fun PlannedCard(engine: NavEngine) {
    val p = engine.planned ?: return
    val c = LocalVw.current
    val f = com.headunit.launcher.Clock.format()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(p) { while (true) { now = System.currentTimeMillis(); delay(30_000) } }
    val mins = ((p.leaveBy - now) / 60_000L).toInt()
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().panel(8.dp, shape, if (mins <= 10) c.accent else c.surface).clickable { engine.startPlanned() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (mins <= 10) Color.White else c.text
        Column(Modifier.weight(1f)) {
            Text(if (mins <= 0) L.t("Time to leave for {0}", p.dest.name) else L.t("Leave by {0} for {1}", f.format(java.util.Date(p.leaveBy)), p.dest.name), color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(L.t("Arrive by {0}", f.format(java.util.Date(p.arriveBy))) + (if (mins in 1..120) L.t(" · in {0} min", mins) else ""), color = if (mins <= 10) Color.White.copy(alpha = 0.85f) else c.sub, fontSize = 12.sp)
        }
        Text(L.t("Go"), color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
        Icon(Icons.Rounded.Close, L.t("Cancel"), tint = fg, modifier = Modifier.size(24.dp).clickable { engine.cancelPlanned() })
    }
}

/** Route options row: a good place to fill up on a long trip. */
@Composable
fun FuelSuggestionRow(engine: NavEngine) {
    val n = engine.fuelSuggestion ?: return
    val c = LocalVw.current
    Row(
        Modifier.floating(50.dp).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(if (engine.prefs.s(PreferenceKeys.FUEL_TYPE, "E10") == "EV") L.t("Charging stop") else L.t("Fuel stop"), color = Good, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            n.place.name + " · " + Geo.fmtDist(n.dist, engine.imperial) + " in" + (if (n.price != null) " · " + com.headunit.launcher.fmtFuelPrice(n.price, n.unit) else "") + (if (n.detourMin >= 0) L.t(" · +{0} min", n.detourMin) else "") + (if (n.extra.startsWith("arrive")) " · " + n.extra else ""),
            color = c.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 420.dp),
        )
        Pill(L.t("Add stop"), false, { engine.addStop(n.place); engine.fuelSuggestion = null })
    }
}

// ------------------------------------------------------------------ UK road signs

private val ukRefRx = Regex("\\b([MAB][0-9]{1,4})(\\s?\\(M\\))?\\b")

/**
 * The direction sign for the junction, drawn the way UK gantries look: blue with white text for motorways,
 * green with yellow road numbers for primary roads, white with black text for local roads. The junction number
 * sits in its own black box, and road numbers of a different class get their own coloured panel.
 */
@Composable
fun UkSign(sign: String, junction: String, street: String, modifier: Modifier = Modifier) {
    val text = sign.ifBlank { street }
    val refs = ukRefRx.findAll(text).map { it.groupValues[1] + (if (it.groupValues[2].isNotBlank()) " (M)" else "") }.distinct().toList()
    // (Mapbox writes "M27: Salisbury, Ringwood": the colon after the road number is split off too, so it never shows as ": Salisbury")
    val dests = text.split(',', '/', ';', '|', ':').map { ukRefRx.replace(it, "").replace(Regex("[()]"), "").trim().trim(':', '-', '–', ' ') }.filter { it.length >= 3 && it.any { ch -> ch.isLetter() } }.distinct().take(3)
    val motorway = refs.any { it.startsWith("M") || it.endsWith("(M)") } || (junction.isNotBlank() && refs.none { it.startsWith("A") || it.startsWith("B") })
    val primary = !motorway && refs.any { it.startsWith("A") }
    val bg = when { motorway -> Color(0xFF0065B3); primary -> Color(0xFF00703C); else -> Color.White }
    val fg = if (motorway || primary) Color.White else Color(0xFF111111)
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier.clip(shape).background(bg).border(2.dp, if (motorway || primary) Color.White else Color(0xFF111111), shape).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (junction.isNotBlank()) Text(junction, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(Color.Black).padding(horizontal = 8.dp, vertical = 2.dp))
        Column(Modifier.weight(1f, fill = false)) {
            if (dests.isNotEmpty()) dests.forEach { Text(it, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            else if (refs.isEmpty()) Text(text, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        refs.take(2).forEach { r ->
            val isM = r.startsWith("M") || r.endsWith("(M)")
            val isA = r.startsWith("A") && !isM
            when {
                // a road of the sign's own class: plain (yellow numbers on green signs)
                isM && motorway -> Text(r, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                isA && primary -> Text(r, color = Color(0xFFFFD200), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                // a different class gets its own panel
                isM -> Text(r, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFF0065B3)).padding(horizontal = 6.dp, vertical = 1.dp))
                isA -> Text(r, color = Color(0xFFFFD200), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFF00703C)).padding(horizontal = 6.dp, vertical = 1.dp))
                else -> Text("($r)", color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

// ------------------------------------------------------------------ hazard radar

private fun radarColor(kind: String): Color = when (kind) {
    "camera", "avg" -> Bad
    "incident" -> Color(0xFFFF7A45)
    "works" -> Warn
    "school", "crossing" -> Color(0xFF4DA3FF)
    "bend" -> Color(0xFFB4BCD0)
    else -> Warn
}

private fun radarIcon(kind: String): ImageVector = when (kind) {
    "camera" -> Icons.Rounded.CameraAlt
    "avg" -> Icons.Rounded.Speed
    "works" -> Icons.Rounded.Construction
    "incident" -> Icons.Rounded.PriorityHigh
    "school" -> Icons.Rounded.School
    "crossing" -> Icons.Rounded.DirectionsWalk
    "bend" -> Icons.Rounded.TurnSlightRight
    else -> Icons.Rounded.Warning
}

/**
 * Hazard radar: the next two miles as a strip, you on the left, with bends, crossings, schools, bumps, cameras and checks
 * placed where they are, and the nearest one spelled out.
 */
@Composable
fun HazardRadar(engine: NavEngine, modifier: Modifier = Modifier) {
    val items = engine.radar
    if (items.isEmpty()) return
    val c = LocalVw.current
    val reach = 3219.0
    val near = items.first()
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.widthIn(max = 540.dp).fillMaxWidth().panel(8.dp, shape, c.surface).padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(radarIcon(near.kind), null, tint = radarColor(near.kind), modifier = Modifier.size(20.dp).graphicsFlip(near.kind == "bend" && near.label.endsWith("left")))
            // (one line, whatever the language: with a long name, "Próg zwalniający · za 150 m", there was no room left for
            // "następne 3 km", which then wrapped a letter or two per line and made the card fill half the screen, changing size
            // as the distance counted down, GitHub #245. The name now gives way and is cut short instead)
            Row(Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(L.t(near.label), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(" · " + (if (near.dist < 20) L.t("now") else L.t("in {0}", Geo.fmtDist(near.dist, engine.imperial))), color = c.sub, fontSize = 14.sp, maxLines = 1, softWrap = false)
            }
            Text(if (engine.imperial) L.t("next 2 mi") else L.t("next 3 km"), color = c.sub, fontSize = 11.sp, maxLines = 1, softWrap = false)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp).height(30.dp)) {
            val w = maxWidth
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.tile))
            // you
            Box(Modifier.align(Alignment.CenterStart).size(12.dp).clip(CircleShape).background(c.accent))
            // furthest first so the nearest sit on top
            for (it in items.asReversed()) {
                val f = (it.dist / reach).toFloat().coerceIn(0f, 1f)
                val x = (w - 26.dp) * f + 4.dp
                Box(Modifier.offset(x = x).align(Alignment.CenterStart).size(26.dp).clip(CircleShape).background(c.surface2).border(2.dp, radarColor(it.kind), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(radarIcon(it.kind), null, tint = radarColor(it.kind), modifier = Modifier.size(15.dp).graphicsFlip(it.kind == "bend" && it.label.endsWith("left")))
                }
            }
        }
    }
}

/** Mirrors an icon left-to-right (used for left-hand bends). */
private fun Modifier.graphicsFlip(on: Boolean): Modifier = if (on) this.then(Modifier.scale(-1f, 1f)) else this


// ------------------------------------------------------------------ offline pack age

/** Newer map data is out for a downloaded area (a rebuilt pack, or new speed limit / camera files): Update or Later. */
@Composable
fun DataUpdateCard(engine: NavEngine) {
    val id = engine.dataUpdates.firstOrNull() ?: return
    if (engine.packBusy) return
    val c = LocalVw.current
    val name = com.headunit.launcher.PackRegions.byId(id).name
    val wifi = remember(id) { engine.onWifi() }
    val more = engine.dataUpdates.size - 1
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().panel(8.dp, shape, c.surface).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(L.t("New map data for {0}", name) + if (more > 0) L.t(" (+{0} more)", more) else "", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (wifi) L.t("Only what's changed downloads") else L.t("Only what's changed downloads · best on Wi-Fi"), color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Pill(L.t("Update"), true, { engine.updateData(id) })
        Pill(L.t("Later"), false, { engine.snoozeDataUpdates() })
    }
}

/** A gentle note when the offline pack is about four months old or more, with Update (best on Wi-Fi) and Later. */
@Composable
fun PackAgeCard(engine: NavEngine) {
    val rev = engine.packRev
    var hidden by remember { mutableStateOf(false) }
    // file checks happen once (and again after a download), not on every redraw
    val age = remember(rev) { if (engine.packNeedsUpdate()) engine.packAgeDays() else null }
    if (age == null || hidden || engine.packBusy) return
    val c = LocalVw.current
    val wifi = remember(rev) { engine.onWifi() }
    val months = (age / 30).coerceAtLeast(4)
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().panel(8.dp, shape, c.surface).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(L.t("Offline map is {0} months old", months), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(if (wifi) L.t("Update it now for new roads and junctions") else L.t("Update on Wi-Fi for new roads and junctions"), color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (wifi) Pill(L.t("Update"), true, { engine.updatePack(); hidden = true })
        Pill(L.t("Later"), false, { engine.snoozePackUpdate(); hidden = true })
    }
}

/** The changes in an available update (every version from this one up to it), fetched once per version and kept for the run. */
private object UpdateNotesCache { val notes = HashMap<String, String>() }

/**
 * A newer version of the app is out (shareable build only), styled like the What's new card: the blue header with your version and
 * the new one, how many changes are new, improved and fixed, and "What's in it" folding open to just their headlines. Update
 * downloads it and opens the installer; Later hides it for a few days. On a short landscape screen it's more compact, and the
 * folded-open list scrolls within a set height, so it never runs off the screen.
 */
@Composable
fun AppUpdateCard(engine: NavEngine) {
    val u = engine.appUpdate ?: return
    val c = LocalVw.current
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    val short = conf.screenHeightDp < 520
    val headBg = if (c.dark) Color(0xFF203B62) else Color(0xFFDCEBFF)
    val headTitle = if (c.dark) Color.White else Color(0xFF172B46)
    val headSub = if (c.dark) Color(0xFFD8E7FA) else Color(0xFF405875)
    var notes by remember(u.version) { mutableStateOf(UpdateNotesCache.notes[u.version] ?: u.releaseNotes) }
    LaunchedEffect(u.version) {
        if (UpdateNotesCache.notes.containsKey(u.version)) return@LaunchedEffect
        val n = try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.headunit.launcher.Updater.installedReleaseNotes(u.version, com.headunit.launcher.BuildConfig.VERSION_NAME, null)
            }
        } catch (e: Exception) { "" }
        if (n.isNotBlank()) { UpdateNotesCache.notes[u.version] = n; notes = n }
    }
    val items = remember(notes) { try { parseNotes(notes) } catch (e: Exception) { emptyList() } }
    val summary = remember(items) { if (items.isEmpty()) "" else try { notesSummary(notes) } catch (e: Exception) { "" } }
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().panel(8.dp, shape, c.surface).padding(if (short) 10.dp else 12.dp)) {
        // the header, as on the What's new card
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(headBg).padding(horizontal = 14.dp, vertical = if (short) 9.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (short) 3.dp else 5.dp)) {
            if (!short) Text(L.t("HORIZON NAV  /  UPDATE"), color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(L.t("Update available"), color = headTitle, fontSize = if (short) 17.sp else 20.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (short) VersionChips(u.version, c, headTitle)
            }
            if (!short) VersionChips(u.version, c, headTitle)
            if (summary.isNotBlank()) Text(summary, color = headSub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val busy = engine.updateBusy || engine.updateStatus.isNotBlank()
        // "What's in it": just the headlines, up to three of each kind
        if (items.isNotEmpty() && !busy) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = !open }.padding(horizontal = 6.dp, vertical = if (short) 7.dp else 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(L.t("What's in it"), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = c.accent, modifier = Modifier.size(22.dp))
            }
            androidx.compose.animation.AnimatedVisibility(open, enter = androidx.compose.animation.expandVertically(tween(220)) + fadeIn(tween(200)),
                exit = androidx.compose.animation.shrinkVertically(tween(180)) + fadeOut(tween(140))) {
                val kindTitles = listOf(L.t("New"), L.t("Improved"), L.t("Fixed"))
                val kindCols = listOf(if (c.dark) Color(0xFF9AF0C9) else Color(0xFF16734D), c.accent, if (c.dark) Color(0xFFFFB27A) else Color(0xFFB3541E))
                Column(Modifier.fillMaxWidth().heightIn(max = (conf.screenHeightDp * (if (short) 0.34f else 0.38f)).dp).verticalScroll(rememberScrollState())
                    .padding(start = 6.dp, end = 6.dp, bottom = 4.dp)) {
                    for (k in 0..2) {
                        val group = items.filter { it.kind == k }
                        if (group.isEmpty()) continue
                        Text(kindTitles[k].uppercase(L.locale), color = kindCols[k], fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                        for (it in group.take(3)) Text("• " + it.title.ifBlank { it.desc }, color = c.text, fontSize = 13.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))
                        if (group.size > 3) Text(L.t("and {0} more", group.size - 3), color = c.sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
        Text(if (busy) engine.updateStatus else L.t("Your settings and places are kept"), color = c.sub, fontSize = 12.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = if (items.isNotEmpty() && !busy) 0.dp else 8.dp, bottom = 8.dp))
        // Horizon Nav isn't on Google Play, so "installed by Google Play" means a tool like AAEnabler installed it to get it into
        // Android Auto. Android Auto drops apps that update themselves, so say so before they find it missing in the car.
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val viaAaTool = remember { com.headunit.launcher.Updater.installedViaAaTool(ctx) }
        if (viaAaTool && !busy) Text(
            if (android.os.Build.VERSION.SDK_INT >= 29) L.t("Installed with AAEnabler: Update saves the new version to your Downloads, so you can install it with AAEnabler and keep it in Android Auto.")
            else L.t("Installed with AAEnabler (or similar)? Updating here takes it out of Android Auto: install the new version with AAEnabler again afterwards."),
            color = Warn, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp))
        if (!engine.updateBusy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Button(onClick = { engine.installAppUpdate() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = if (c.dark) Color(0xFF102039) else Color.White)) {
                Text(L.t("Update"), fontWeight = FontWeight.Bold)
            }
            androidx.compose.material3.Button(onClick = { engine.snoozeAppUpdate() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c.tile, contentColor = c.text)) {
                Text(L.t("Later"), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Your version, an arrow, and the new one picked out in the accent colour. */
@Composable
private fun VersionChips(newVersion: String, c: Vw, text: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(com.headunit.launcher.BuildConfig.VERSION_NAME, color = text, fontSize = 13.sp, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(text.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = c.accent, modifier = Modifier.size(16.dp))
        Text(newVersion, color = if (c.dark) Color(0xFF0F1D33) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.accent).padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

// ------------------------------------------------------------------ your own saved places

/**
 * Save a place under your own name. With [preset] (e.g. the destination on the route options) only the name is asked for;
 * otherwise you search for the address too. Saved places show as pills under the search bar, after Home and Work.
 */
@Composable
fun AddPlaceDialog(engine: NavEngine, preset: Place?, slot: String? = null, onClose: () -> Unit) {
    val c = LocalVw.current
    var label by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(preset) }
    // its own results: the main search's results opened the main search panel behind it, which closed this dialog
    var found by remember { mutableStateOf<List<Place>>(emptyList()) }
    LaunchedEffect(q) {
        if (preset == null && q.length >= 3) { delay(450); found = try { engine.findPlaces(q) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { emptyList() } }
        else if (q.isEmpty()) found = emptyList()
    }
    fun close() { onClose() }
    Dialog(onDismissRequest = { close() }) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.panel).border(1.dp, c.line, RoundedCornerShape(26.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // [slot] "home" or "work": setting Home or Work from Settings (no name to type, it's saved straight into that slot)
            Text(if (slot == "home") L.t("Set Home") else if (slot == "work") L.t("Set Work") else if (preset != null) L.t("Save this place") else L.t("Add a place"), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (slot == null) SField(label, { label = it.take(24) }, placeholder = L.t("Name, e.g. Mum's, Gym, Office 2"), width = 476.dp, fill = true)
            val pk = picked
            if (pk != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.tile).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Place, null, tint = c.accent, modifier = Modifier.size(22.dp))
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(pk.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (pk.sub.isNotBlank()) Text(pk.sub, color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (preset == null) Text(L.t("Change"), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { picked = null; q = "" })
            } else {
                // Home or Work: one tap to use where you are now (GitHub #263), looked up as the dialog opens so the address shows
                if (slot != null && preset == null) {
                    val fixNow = engine.fix
                    var hereNow by remember { mutableStateOf<Place?>(null) }
                    LaunchedEffect(fixNow != null) { if (fixNow != null && hereNow == null) hereNow = try { engine.placeHereNow() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null } }
                    val acc = fixNow?.acc ?: 0.0
                    val subLine = when {
                        fixNow == null -> L.t("Waiting for your location")
                        else -> listOfNotNull(hereNow?.let { h -> listOf(h.name, h.sub).filter { it.isNotBlank() }.joinToString(", ") }?.ifBlank { null },
                            if (acc > 0.0) "±" + Geo.fmtDist(acc, engine.imperial) else null).joinToString(" · ")
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.accent.copy(alpha = 0.14f))
                        .border(1.5.dp, c.accent, RoundedCornerShape(16.dp))
                        .clickable(enabled = fixNow != null) {
                            val f = engine.fix ?: return@clickable
                            picked = hereNow?.takeIf { Geo.dist(it.pos, f.pos) < 100.0 } ?: Place(L.t("Your location"), "", f.pos)
                        }.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MyLocation, null, tint = c.accent, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(L.t("Use where I am now"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (subLine.isNotBlank()) Text(subLine, color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(L.t("or"), color = c.sub, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                SField(q, { q = it }, placeholder = L.t("Search for the address or postcode"), width = 476.dp, fill = true)
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    itemsIndexed(found.take(6)) { _, p ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                            picked = p; found = emptyList(); if (label.isBlank()) label = p.name.take(24)
                        }.padding(horizontal = 12.dp, vertical = 9.dp)) {
                            Text(p.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (p.sub.isNotBlank()) Text(p.sub, color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(L.t("Save"), pk != null, {
                    val p = picked
                    if (p != null && slot != null) { engine.places.setSlot(slot, p); engine.toast(if (slot == "home") L.t("Home saved") else L.t("Work saved"), 2500); close() }
                    else if (p != null) { engine.places.addCustom(label, p); engine.toast(L.t("Saved as {0}", label.trim().ifBlank { p.name }), 2500); close() }
                })
                Pill(L.t("Cancel"), false, { close() })
            }
        }
    }
}

/**
 * Shown once after updating from a Horizon Nav from before the Mapbox map: what the Mapbox key adds, and a box to paste it in
 * (filled in already if one was saved before). Skipping it is fine: it can be added later in Settings › Keys & accounts.
 */
@Composable
fun MapboxKeyDialog(prefs: com.headunit.launcher.Prefs, refused: Boolean = false, why: String = "", onClose: () -> Unit) {
    val c = LocalVw.current
    var k by remember { mutableStateOf(prefs.s(PreferenceKeys.MAPBOX_TOKEN, "")) }
    Dialog(onDismissRequest = {}) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.panel).padding(22.dp)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (refused) L.t("Mapbox turned down your key") else L.t("Add your Mapbox key"), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (refused) Text(why.ifBlank { L.t("Mapbox answered \"401\" for the key you saved, so the map can't load. Make a fresh one at account.mapbox.com › Tokens › ") +
                L.t("Create a token: name it Horizon Nav, leave the public scopes ticked and the secret ones unticked, then paste it below.") }, color = Warn, fontSize = 14.sp)
            Text(L.t("Horizon Nav now draws its map with Mapbox. Your free Mapbox key adds the 3D map with buildings, trees and lane markings, ") +
                L.t("live traffic on your route, and better search and speed limits."), color = c.text, fontSize = 15.sp)
            Text(L.t("Get it at account.mapbox.com › Tokens: copy the Default public token (it starts pk.)."), color = c.sub, fontSize = 13.sp)
            SField(k, { k = it }, placeholder = L.t("Mapbox access token (pk.…)"), width = 500.dp, fill = true)
            val t = com.headunit.launcher.MapboxRest.clean(k)
            when {
                t.startsWith("sk.") -> Text(L.t("That's a secret token: use the public one (pk.) here."), color = Warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                t.isNotEmpty() && !com.headunit.launcher.MapboxRest.looksValid(t) -> Text(L.t("That doesn't look like a whole token yet: it's a long code starting pk."), color = Warn, fontSize = 13.sp)
                t.isNotEmpty() && !com.headunit.launcher.MapboxRest.looksWhole(t) -> Text(L.t("Part of that token looks missing or mistyped. Copy it again with the copy button at account.mapbox.com › Tokens."), color = Warn, fontSize = 13.sp)
                t.isEmpty() -> Text(L.t("Without it the map stays blank, but routes and search still work from your offline pack."), color = c.sub, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(L.t("Save"), true, { if (t.isNotEmpty() && !t.startsWith("sk.")) { prefs.put(PreferenceKeys.MAPBOX_TOKEN, t); onClose() } })
                Pill(L.t("Later"), false, onClose)
            }
        }
    }
}

/**
 * A UK-style road sign for the junction coming up, from the route's sign data: blue for motorways (with the black junction-number
 * box), green for main roads (yellow road numbers), the roads it leads to and the places on the sign. Only when the route has them.
 */
@Composable
fun JunctionSign(t: com.headunit.launcher.TurnInfo, modifier: Modifier = Modifier) {
    if (t.sign.isBlank() || t.dist > 3200.0) return
    val refRx = Regex("\\b([MAB][0-9]{1,4}(?:\\(M\\))?)\\b")
    val refs = refRx.findAll(t.sign).map { it.groupValues[1] }.distinct().take(2).toList()
    val places = refRx.replace(t.sign, " ").split(',', ';', ':', '/').map { it.trim().trim('-', '–').trim() }
        .filter { it.length > 1 && !it.equals("J", true) && !Regex("^J?[0-9]+[A-Z]?$").matches(it) }.distinct().take(3)
    if (refs.isEmpty() && places.isEmpty()) return
    val motorway = t.exitNo.isNotBlank() || refs.any { it.startsWith("M") || it.endsWith("(M)") }
    val bg = if (motorway) Color(0xFF1554A8) else Color(0xFF0D6B3A)
    val shape = RoundedCornerShape(14.dp)
    val left = t.maneuver.uppercase().contains("LEFT")
    Column(modifier.widthIn(max = 380.dp).shadow(8.dp, shape).clip(shape).background(Color.White).padding(3.dp).clip(RoundedCornerShape(11.dp)).background(bg)
        .padding(horizontal = 12.dp, vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (t.exitNo.isNotBlank()) {
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White).padding(1.5.dp).clip(RoundedCornerShape(3.dp)).background(Color.Black)
                    .padding(horizontal = 7.dp, vertical = 1.dp)) { Text(t.exitNo, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.width(6.dp))
            }
            refs.forEach { r ->
                if (motorway) Box(Modifier.padding(end = 6.dp).border(1.5.dp, Color.White, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    Text(r, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                } else Text(r, color = Color(0xFFFFD200), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(end = 8.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(if (left) "↖" else "↗", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        places.forEach { p -> Text(p, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/**
 * A small diagram of the roundabout ahead: the ring, the roads off it (from the route's own junction data) and your way round it
 * lit in blue, from the road you come in on (at the bottom) to your exit. Clockwise where traffic keeps left, anticlockwise elsewhere.
 */
@Composable
fun RoundaboutView(engine: NavEngine, t: com.headunit.launcher.TurnInfo, modifier: Modifier = Modifier) {
    if (!t.maneuver.uppercase().startsWith("ROUNDABOUT") || t.rbIn < 0 || t.rbOut < 0 || t.dist > 800.0) return
    val c = navVw(LocalVw.current)
    // The steering-wheel layout can be manually changed; roundabout direction must still follow local traffic rules.
    val clockwise = engine.roundaboutClockwise
    // angles on screen: the way you come in points up, so the road you enter from is at the bottom
    fun rel(b: Double) = ((b - t.rbIn) % 360.0 + 360.0) % 360.0
    val exitA = rel(t.rbOut)
    val others = t.rbExits.map { rel(it) }.filter { a -> Math.abs(((a - exitA + 540.0) % 360.0) - 180.0) > 20.0 && Math.abs(((a - 180.0 + 540.0) % 360.0) - 180.0) > 20.0 }
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.size(132.dp).panel(6.dp, shape, c.surface).border(1.dp, c.line, shape).padding(8.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val cx = w / 2f; val cy = h / 2f
            val r = w * 0.24f
            val road = c.sub.copy(alpha = 0.35f)
            val sw = w * 0.085f
            fun leg(angleDeg: Double, col: Color, width: Float) {
                val a = Math.toRadians(angleDeg)
                val sx = cx + (r * Math.sin(a)).toFloat(); val sy = cy - (r * Math.cos(a)).toFloat()
                val ex = cx + (w * 0.5f * Math.sin(a)).toFloat(); val ey = cy - (h * 0.5f * Math.cos(a)).toFloat()
                drawLine(col, Offset(sx, sy), Offset(ex, ey), width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            drawCircle(road, r, Offset(cx, cy), style = Stroke(sw))
            for (a in others) leg(a, road, sw)
            leg(180.0, road, sw); leg(exitA, road, sw)
            // your way round: in from the bottom, round the ring, out at your exit
            val blue = Color(0xFF2F7CF6)
            leg(180.0, blue, sw * 0.75f)
            leg(exitA, blue, sw * 0.75f)
            // the ring from the entry (bottom) to the exit, the way traffic goes
            val sweep = NavigationPolicy.roundaboutSweep(exitA, clockwise)
            // A positive canvas sweep follows the clockwise roundabout used where traffic keeps left.
            drawArc(blue, 90f, sweep, false, topLeft = Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(sw * 0.75f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
}

/** A short, quiet note about the conditions ("Heavy rain · take it steady, 50 limit"). */
@Composable
fun AdvicePill(text: String, modifier: Modifier = Modifier) {
    val c = navVw(LocalVw.current)
    val shape = RoundedCornerShape(50)
    Row(modifier.panel(6.dp, shape, c.surface).border(1.dp, c.line, shape).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.WaterDrop, null, tint = Color(0xFF4C8DFF), modifier = Modifier.size(18.dp))
        Text(text, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A lorry's limit ahead that it's over (a low bridge, a weight limit): red, under the turn card, with the limit's sign,
 * how far it is and the lorry's own size (see NavEngine.checkLorry).
 */
@Composable
internal fun LorryWarnCard(engine: NavEngine, modifier: Modifier = Modifier) {
    val w = engine.lorryWarn ?: return
    val red = Color(0xFFD93025)
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.widthIn(max = 420.dp).fillMaxWidth().shadow(8.dp, shape).clip(shape).background(Color(0xF23A2224))
            .border(2.dp, red, shape).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(red).padding(5.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Text(w.sign, color = Color(0xFF202124), fontSize = if (w.sign.length <= 4) 14.sp else 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text("${w.title} · ${Geo.fmtDist(w.dist, engine.imperial)}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(w.detail, color = Color(0xFFF2C2BF), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}


/** A tap on the phone, or through Android Auto's tap list on the car screen (where ordinary clicks don't reach). */
@Composable
internal fun Modifier.tapOn(engine: NavEngine, id: String, action: () -> Unit): Modifier =
    if (engine.isCar) carTap(id, action) else clickable(onClick = action)

/**
 * [b] shrunk to [px] square for drawing small: halved with filtering until it's within twice the size, then scaled the rest of the
 * way. Bilinear filtering only looks at neighbouring pixels, so one big step skips most of the picture and leaves jagged edges.
 */
internal fun shrinkSmooth(b: android.graphics.Bitmap, px: Int): android.graphics.Bitmap {
    val target = px.coerceAtLeast(1)
    var cur = b
    while (cur.width / 2 >= target && cur.height / 2 >= target) {
        cur = android.graphics.Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
    }
    return if (cur.width == target && cur.height == target) cur else android.graphics.Bitmap.createScaledBitmap(cur, target, target, true)
}
