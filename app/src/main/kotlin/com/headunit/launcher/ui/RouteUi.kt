package com.headunit.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Place
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
            val b = ((r.cum[i] / len) * bins).toInt().coerceIn(0, bins - 1)
            if (r.sev[i] > out[b]) out[b] = r.sev[i]
        }
        out
    }
    val accent = c.accent
    val theme = engine.map.theme
    val locator = remember(theme) { engine.map.locatorBitmap().asImageBitmap() }
    // [thin]: the slimmer bar on the car screen (smaller markers, a thinner track and a smaller arrow)
    val dot = if (thin) 16.dp else 20.dp
    BoxWithConstraints(modifier.height(if (thin) 34.dp else 48.dp)) {
        val w = maxWidth
        // the track, with the part already driven greyed out, and the arrow
        Canvas(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(if (thin) 18.dp else 26.dp)) {
            val th = (if (thin) 4.dp else 8.dp).toPx()
            val y0 = (size.height - th) / 2f
            val bw = size.width / bins
            drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(0f, y0 - 1.5f), Size(size.width, th + 3f), CornerRadius(th))
            for (b in 0 until bins) {
                val done = (b + 1f) / bins <= frac
                val col = when (levels[b]) { 2 -> Bad; 1 -> Warn; else -> accent }
                drawRect(if (done) Color(0xFF8A8F99) else col, Offset(b * bw, y0), Size(bw + 0.6f, th))
            }
            // the arrow: the map's own locator, shrunk and turned to point along the bar
            val sz = (if (thin) 22.dp else 32.dp).toPx()
            val ax = (frac * size.width).coerceIn(sz / 2f, size.width - sz / 2f)
            val cy = size.height / 2f
            rotate(90f, Offset(ax, cy)) {
                drawImage(
                    locator, dstOffset = androidx.compose.ui.unit.IntOffset((ax - sz / 2f).toInt(), (cy - sz / 2f).toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(sz.toInt(), sz.toInt()),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
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
    Row(
        modifier.fillMaxWidth().panel(8.dp, shape, c.surface).border(2.dp, if (a.over) Bad else Color.Transparent, shape).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val lim = a.limitMs?.let { (Math.round(it * k / 5.0) * 5).toInt() }
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White).border(5.dp, Bad, CircleShape), contentAlignment = Alignment.Center) {
            Text(lim?.toString() ?: "?", color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(if (a.ahead) "AVERAGE SPEED CHECK AHEAD" else "AVERAGE SPEED CHECK", color = Bad, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            if (a.ahead) Text("Starts in ${Geo.fmtDist(a.toStartM, engine.imperial)} · ${Geo.fmtDist(a.leftM, engine.imperial)} long", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            else {
                Text("${Geo.fmtDist(a.leftM, engine.imperial)} left", color = c.sub, fontSize = 13.sp)
                // what you can do from here and still come out under the limit (only worth saying once it differs from the limit)
                val safe = a.safeMs
                val lm = a.limitMs
                if (safe != null && lm != null) {
                    val sv = Math.floor(safe * k).toInt()
                    val lv = Math.round(lm * k).toInt()
                    when {
                        safe <= 0.5 || sv < lv - 25 -> Text("Slow down to bring your average back under", color = Bad, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        sv < lv -> Text("Keep under $sv $unit for the rest", color = Warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        sv > lv -> Text("Up to ${minOf(sv, lv + 15)} $unit keeps you under", color = c.sub, fontSize = 13.sp)
                        else -> {}
                    }
                }
            }
        }
        if (!a.ahead) Column(horizontalAlignment = Alignment.End) {
            Text("${Math.round(a.avgMs * k)}", color = if (a.over) Bad else Good, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("avg $unit", color = c.sub, fontSize = 11.sp)
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
            Text("Parking near ${engine.dest?.name ?: "your destination"}", color = c.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(CircleShape).clickable { engine.parkingNear = emptyList() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "Close", tint = c.sub, modifier = Modifier.size(16.dp))
            }
        }
        list.forEach { n ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { engine.parkAt(n) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF2E6FD8)), contentAlignment = Alignment.Center) {
                    Text("P", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
                Text(n.place.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 10.dp))
                // a walk at about 80 m a minute
                Text("${maxOf(1, Math.round(n.dist / 80.0).toInt())} min walk", color = c.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, end = 8.dp))
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
        Text("No GPS · estimating your position", color = c.text, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
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
        Text((if (road.isNotBlank()) "$road · " else "") + "Heading $dir", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Edit the stops on the way: add by searching, move them up or down, or remove them. */
@Composable
fun StopsDialog(engine: NavEngine, onClose: () -> Unit) {
    val c = LocalVw.current
    var stops by remember { mutableStateOf(engine.via) }
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
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(horizontal = 16.dp).widthIn(max = 620.dp).fillMaxWidth().heightIn(max = 560.dp).clip(RoundedCornerShape(26.dp)).background(c.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Stops on the way", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Close, "Close", tint = c.sub, modifier = Modifier.size(28.dp).clickable { onClose() })
            }
            // one tap, no typing: the best one along the route ahead is added straight away
            if (engine.dest != null) {
                Text("ONE TAP: THE BEST ONE ALONG YOUR ROUTE", color = c.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val ev = engine.prefs.s(PreferenceKeys.FUEL_TYPE, "E10") == "EV"
                    for ((kind, name) in listOfNotNull(if (ev) null else "fuel" to "Fuel", "charging" to "Charger", "coffee" to "Coffee", "food" to "Food", "services" to "Services", "toilets" to "Toilets"))
                        Pill(name, false, { engine.addBestStop(kind); onClose() })
                }
            }
            // your own places, added to the list below (then Update route)
            val mine = remember { listOfNotNull(engine.places.slot("home")?.let { "Home" to it }, engine.places.slot("work")?.let { "Work" to it }) +
                engine.places.favourites().take(4).map { "★ " + it.name to it } }
            if (mine.isNotEmpty()) {
                Text("YOUR PLACES", color = c.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((name, p) in mine) Pill(name, false, { stops = (stops + p).take(8) })
                }
            }
            TextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                placeholder = { Text("Search for a stop to add") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = c.sub) },
                trailingIcon = { if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = c.accent, strokeWidth = 2.dp) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = c.tile, unfocusedContainerColor = c.tile, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = c.text, unfocusedTextColor = c.text, cursorColor = c.accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (results.isNotEmpty()) LazyColumn(Modifier.heightIn(max = 200.dp).clip(RoundedCornerShape(16.dp)).background(c.tile)) {
                itemsIndexed(results) { _, p ->
                    Row(Modifier.fillMaxWidth().clickable { stops = (stops + p).take(8); q = ""; results = emptyList() }.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, null, tint = c.accent, modifier = Modifier.size(22.dp))
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(p.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (p.sub.isNotBlank()) Text(p.sub, color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 260.dp)) {
                itemsIndexed(stops) { i, p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(p.name, color = c.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        Icon(Icons.Rounded.KeyboardArrowUp, "Earlier", tint = if (i > 0) c.text else c.sub.copy(alpha = 0.3f),
                            modifier = Modifier.size(36.dp).clickable(enabled = i > 0) { stops = stops.toMutableList().also { l -> val t = l[i]; l[i] = l[i - 1]; l[i - 1] = t } })
                        Icon(Icons.Rounded.KeyboardArrowDown, "Later", tint = if (i < stops.size - 1) c.text else c.sub.copy(alpha = 0.3f),
                            modifier = Modifier.size(36.dp).clickable(enabled = i < stops.size - 1) { stops = stops.toMutableList().also { l -> val t = l[i]; l[i] = l[i + 1]; l[i + 1] = t } })
                        Icon(Icons.Rounded.Close, "Remove", tint = Bad, modifier = Modifier.padding(start = 6.dp).size(30.dp).clickable { stops = stops.filterIndexed { n, _ -> n != i } })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0xFFEA4335)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Flag, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Text(engine.dest?.name ?: "Destination", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
            }
            if (stops.isEmpty()) Text("No stops yet. Search above to add one; the route goes through them in this order.", color = c.sub, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                Pill("Update route", true, { engine.setStops(stops); onClose() })
                Pill("Cancel", false, onClose)
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
        Text("Goes into " + z.joinToString(", ") + ". A charge may apply.", color = c.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (!engine.prefs.b(PreferenceKeys.AVOIDZONES, false)) Pill("Avoid zones", false, { engine.prefs.put(PreferenceKeys.AVOIDZONES, true); engine.dest?.let { engine.startRoute(it, engine.departMin, true) } })
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
    val f = java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.UK)
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.panel).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Arrive by", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(engine.dest?.name ?: "", color = c.sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Pill("−15", false, { ms -= 15 * 60_000L })
                Text(f.format(java.util.Date(ms)), color = c.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Pill("+15", false, { ms += 15 * 60_000L })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("+1 h", false, { ms += 3_600_000L })
                Pill("−1 h", false, { ms -= 3_600_000L })
                Pill("Tomorrow 9:00", false, {
                    ms = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1); set(java.util.Calendar.HOUR_OF_DAY, 9); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0) }.timeInMillis
                })
            }
            Text("Horizon Nav works out when to leave with the traffic expected then, checks again half an hour before, and reminds you 10 minutes before and when it's time to go (while the app is open).", color = c.sub, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Plan it", true, { if (ms > System.currentTimeMillis()) { engine.planArriveBy(ms); onClose() } })
                Pill("Cancel", false, onClose)
            }
        }
    }
}

/** On the map while idle: the planned trip with its leave time, to start it or cancel it. */
@Composable
fun PlannedCard(engine: NavEngine) {
    val p = engine.planned ?: return
    val c = LocalVw.current
    val f = java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK)
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
            Text(if (mins <= 0) "Time to leave for ${p.dest.name}" else "Leave by ${f.format(java.util.Date(p.leaveBy))} for ${p.dest.name}", color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Arrive by ${f.format(java.util.Date(p.arriveBy))}" + (if (mins in 1..120) " · in $mins min" else ""), color = if (mins <= 10) Color.White.copy(alpha = 0.85f) else c.sub, fontSize = 12.sp)
        }
        Text("Go", color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
        Icon(Icons.Rounded.Close, "Cancel", tint = fg, modifier = Modifier.size(24.dp).clickable { engine.cancelPlanned() })
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
        Text(if (engine.prefs.s(PreferenceKeys.FUEL_TYPE, "E10") == "EV") "Charging stop" else "Fuel stop", color = Good, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            n.place.name + " · " + Geo.fmtDist(n.dist, engine.imperial) + " in" + (if (n.price != null) " · " + com.headunit.launcher.fmtFuelPrice(n.price, n.unit) else "") + (if (n.detourMin >= 0) " · +${n.detourMin} min" else "") + (if (n.extra.startsWith("arrive")) " · " + n.extra else ""),
            color = c.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 420.dp),
        )
        Pill("Add stop", false, { engine.addStop(n.place); engine.fuelSuggestion = null })
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
            Text(near.label, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(" · " + (if (near.dist < 20) "now" else "in " + Geo.fmtDist(near.dist, engine.imperial)), color = c.sub, fontSize = 14.sp, maxLines = 1)
            Box(Modifier.weight(1f))
            Text(if (engine.imperial) "next 2 mi" else "next 3 km", color = c.sub, fontSize = 11.sp)
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
            Text("New map data for $name" + if (more > 0) " (+$more more)" else "", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (wifi) "Only what's changed downloads" else "Only what's changed downloads · best on Wi-Fi", color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Pill("Update", true, { engine.updateData(id) })
        Pill("Later", false, { engine.snoozeDataUpdates() })
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
            Text("Offline map is $months months old", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(if (wifi) "Update it now for new roads and junctions" else "Update on Wi-Fi for new roads and junctions", color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (wifi) Pill("Update", true, { engine.updatePack(); hidden = true })
        Pill("Later", false, { engine.snoozePackUpdate(); hidden = true })
    }
}

/** A newer version of the app is out (shareable build only): Update downloads it and opens the installer; Later hides it for a few days. */
@Composable
fun AppUpdateCard(engine: NavEngine) {
    val u = engine.appUpdate ?: return
    val c = LocalVw.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().panel(8.dp, shape, c.surface).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Horizon Nav update available", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Installed ${com.headunit.launcher.BuildConfig.VERSION_NAME}  →  Available ${u.version}", color = c.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (engine.updateBusy || engine.updateStatus.isNotBlank()) engine.updateStatus else "Update now: your settings and places are kept",
                color = c.sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (u.releaseNotes.isNotBlank() && !engine.updateBusy) {
                Text(u.releaseNotes, color = c.sub, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            // Horizon Nav isn't on Google Play, so "installed by Google Play" means a tool like AAEnabler installed it to get it into
            // Android Auto. Android Auto drops apps that update themselves, so say so before they find it missing in the car.
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val viaAaTool = remember { com.headunit.launcher.Updater.installedViaAaTool(ctx) }
            if (viaAaTool && !engine.updateBusy && engine.updateStatus.isBlank()) Text(
                if (android.os.Build.VERSION.SDK_INT >= 29) "Installed with AAEnabler: Update saves the new version to your Downloads, so you can install it with AAEnabler and keep it in Android Auto."
                else "Installed with AAEnabler (or similar)? Updating here takes it out of Android Auto: install the new version with AAEnabler again afterwards.",
                color = Warn, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (!engine.updateBusy) Pill("Update", true, { engine.installAppUpdate() })
        if (!engine.updateBusy) Pill("Later", false, { engine.snoozeAppUpdate() })
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
            Text(if (slot == "home") "Set Home" else if (slot == "work") "Set Work" else if (preset != null) "Save this place" else "Add a place", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (slot == null) SField(label, { label = it.take(24) }, placeholder = "Name, e.g. Mum's, Gym, Office 2", width = 476.dp, fill = true)
            val pk = picked
            if (pk != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.tile).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Place, null, tint = c.accent, modifier = Modifier.size(22.dp))
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(pk.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (pk.sub.isNotBlank()) Text(pk.sub, color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (preset == null) Text("Change", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { picked = null; q = "" })
            } else {
                SField(q, { q = it }, placeholder = "Search for the address or postcode", width = 476.dp, fill = true)
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
                Pill("Save", pk != null, {
                    val p = picked
                    if (p != null && slot != null) { engine.places.setSlot(slot, p); engine.toast(if (slot == "home") "Home saved" else "Work saved", 2500); close() }
                    else if (p != null) { engine.places.addCustom(label, p); engine.toast("Saved as ${label.trim().ifBlank { p.name }}", 2500); close() }
                })
                Pill("Cancel", false, { close() })
            }
        }
    }
}

/**
 * Shown once after updating from a Horizon Nav from before the Mapbox map: what the Mapbox key adds, and a box to paste it in
 * (filled in already if one was saved before). Skipping it is fine: it can be added later in Settings › Keys & accounts.
 */
@Composable
fun MapboxKeyDialog(prefs: com.headunit.launcher.Prefs, refused: Boolean = false, onClose: () -> Unit) {
    val c = LocalVw.current
    var k by remember { mutableStateOf(prefs.s(PreferenceKeys.MAPBOX_TOKEN, "")) }
    Dialog(onDismissRequest = {}) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.panel).padding(22.dp)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (refused) "Mapbox turned down your key" else "Add your Mapbox key", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (refused) Text("Mapbox answered \"401\" for the key you saved, so the map can't load. Make a fresh one at account.mapbox.com › Tokens › " +
                "Create a token: name it Horizon Nav, leave the public scopes ticked and the secret ones unticked, then paste it below.", color = Warn, fontSize = 14.sp)
            Text("Horizon Nav now draws its map with Mapbox. Your free Mapbox key adds the 3D map with buildings, trees and lane markings, " +
                "live traffic on your route, and better search and speed limits.", color = c.text, fontSize = 15.sp)
            Text("Get it at account.mapbox.com › Tokens: copy the Default public token (it starts pk.).", color = c.sub, fontSize = 13.sp)
            SField(k, { k = it }, placeholder = "Mapbox access token (pk.…)", width = 500.dp, fill = true)
            val t = com.headunit.launcher.MapboxRest.clean(k)
            when {
                t.startsWith("sk.") -> Text("That's a secret token: use the public one (pk.) here.", color = Warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                t.isNotEmpty() && !com.headunit.launcher.MapboxRest.looksValid(t) -> Text("That doesn't look like a whole token yet: it's a long code starting pk.", color = Warn, fontSize = 13.sp)
                t.isEmpty() -> Text("Without it the map stays blank, but routes and search still work from your offline pack.", color = c.sub, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Save", true, { if (t.isNotEmpty() && !t.startsWith("sk.")) { prefs.put(PreferenceKeys.MAPBOX_TOKEN, t); onClose() } })
                Pill("Later", false, onClose)
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
    val clockwise = !engine.mirrored
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
            val startCanvas = 90f   // drawArc angles: 0 = right, 90 = bottom (clockwise on screen)
            val exitCanvas = ((exitA - 90.0) % 360.0 + 360.0).toFloat() % 360f
            // (on screen a positive sweep goes clockwise: bottom, then left, then top, then right)
            val sweep = if (clockwise) (exitCanvas - startCanvas + 360f) % 360f else -((startCanvas - exitCanvas + 360f) % 360f)
            drawArc(blue, startCanvas, sweep, false, topLeft = Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(sw * 0.75f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
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
