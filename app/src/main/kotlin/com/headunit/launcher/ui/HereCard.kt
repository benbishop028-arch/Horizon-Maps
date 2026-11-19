package com.headunit.launcher.ui

import com.headunit.launcher.L

import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Factory
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.Geo
import com.headunit.launcher.NavEngine

/**
 * The place preview (like Google Maps'): what the place is and whether it's open, Go (with the drive time) and Add as a stop, a
 * row of round buttons (Save, Call, Website, Share, Start here), then the address, the hours, how far it is, and a petrol
 * station's price or a charger's plugs. A dropped pin shows the road as its own sign and the speed limit instead.
 * Call and Website aren't offered while the car is moving.
 */
@Composable
internal fun HereCardView(engine: NavEngine, h: NavEngine.HereCard, maxH: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
                          docked: Boolean = false, tall: Boolean = false) {
    val c = LocalVw.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val dropped = h.place.name == "Dropped pin" || h.place.name == L.t("Dropped pin")
    val title = if (!dropped) h.place.name else h.road.ifBlank { if (h.loading) L.t("Looking…") else L.t("This spot") }
    val d = h.details
    val kindIcon = when (d?.kind) { "fuel" -> Icons.Rounded.LocalGasStation; "charging" -> Icons.Rounded.EvStation; "cafe" -> Icons.Rounded.LocalCafe
        "food" -> Icons.Rounded.Restaurant; "parking" -> Icons.Rounded.LocalParking; "toilets" -> Icons.Rounded.Wc; else -> null }
    val kindName = when (d?.kind) { "fuel" -> L.t("Fuel"); "charging" -> L.t("Charger"); "cafe" -> L.t("Coffee"); "food" -> L.t("Food")
        "parking" -> L.t("Parking"); "toilets" -> L.t("Toilets"); else -> "" }
    val accent = if (kindIcon != null) c.accent else landColour(h.landClass, h.roadClass)
    val moving = (engine.fix?.speed ?: 0.0) > 3.0
    val driving = engine.route != null && engine.dest != null
    Column(
        // (docked where Add a stop's list goes: the full width given, solid like that list, and full height down the side)
        (if (docked) Modifier.fillMaxWidth() else Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 16.dp))
            .then(if (tall) Modifier.fillMaxHeight() else Modifier)
            .then(if (maxH != androidx.compose.ui.unit.Dp.Unspecified) Modifier.heightIn(max = maxH) else Modifier)
            .panel(12.dp, RoundedCornerShape(if (docked) 28.dp else 24.dp), if (docked) c.surface.copy(alpha = 1f) else c.surface),
    ) {
        Box(Modifier.fillMaxWidth().height(5.dp).background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.25f)))))
        // (scrolls when the screen is too short for all of it, so nothing is cut off)
        Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 14.dp)) {
            // the name, what kind of place, and open or closed
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(kindIcon ?: landIcon(h.landClass, dropped), null, tint = accent, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = listOf(kindName, h.area.ifBlank { h.place.sub.substringBefore(',') }).filter { it.isNotBlank() }.distinct().joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    val st = d?.hours?.let { com.headunit.launcher.OpeningHours.state(it) }
                    if (st != null) Row(Modifier.padding(top = 4.dp)) {
                        Text(if (st.first) L.t("Open now") else L.t("Closed"), color = if (st.first) Good else Bad, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        val more = if (st.first) (if (st.second.isBlank()) L.t("Open 24 hours") else L.t("closes {0}", st.second)) else if (st.second.isNotBlank()) L.t("opens {0}", st.second) else ""
                        if (more.isNotBlank()) Text(" · $more", color = c.sub, fontSize = 13.sp, maxLines = 1)
                    }
                    // a dropped pin: the road as a sign, the area
                    if (dropped) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoadBadge(h)
                        if (h.area.isNotBlank()) Chip(Icons.Rounded.Place, h.area)
                    }
                }
                if (dropped && h.limitMs > 0) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 8.dp)) {
                    MiniLimitSign(Math.round(h.limitMs * if (engine.imperial) 2.23694 else 3.6).toInt())
                    if (h.limitEstimated) Text(L.t("usual"), color = c.sub, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Box(Modifier.size(40.dp).clip(CircleShape).pressClick { engine.here = null }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, L.t("Close"), tint = c.sub, modifier = Modifier.size(22.dp))
                }
            }
            // Go (with the drive time) and Add as a stop
            Row(Modifier.padding(top = 12.dp, end = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1.4f).clip(RoundedCornerShape(16.dp)).background(c.accent).pressClick { engine.hereGo(false) }.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Rounded.Navigation, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    val go = if (driving) L.t("Go here instead") else if (h.etaSecs > 0) L.t("Go · {0}", Geo.fmtDur(h.etaSecs)) else L.t("Go")
                    Text(go, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (driving) Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.tile).pressClick { engine.hereGo(true) }.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Rounded.AddLocationAlt, null, tint = c.text, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(L.t("Add stop"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            // the round buttons
            var saved by remember(h.place) { mutableStateOf(engine.places.favourites().any { Geo.dist(it.pos, h.place.pos) < 15.0 }) }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, end = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundAction(if (saved) Icons.Rounded.Star else Icons.Rounded.StarBorder, L.t("Save"), if (saved) Color(0xFFFFC83D) else c.text) { saved = engine.hereSave() }
                if (!moving && !d?.phone.isNullOrBlank()) RoundAction(Icons.Rounded.Phone, L.t("Call"), c.text) {
                    try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + d!!.phone.replace(" ", ""))).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                }
                if (!moving && !d?.website.isNullOrBlank()) RoundAction(Icons.Rounded.Language, L.t("Website"), c.text) {
                    val w = d!!.website.let { if (it.startsWith("http")) it else "https://$it" }
                    try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(w)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                }
                RoundAction(Icons.Rounded.Share, L.t("Share"), c.text) {
                    val link = String.format(java.util.Locale.US, "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=18/%.6f/%.6f", h.place.pos.lat, h.place.pos.lon, h.place.pos.lat, h.place.pos.lon)
                    val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "$title\n$link")
                    try { ctx.startActivity(android.content.Intent.createChooser(i, L.t("Share")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                }
                if (!driving) RoundAction(Icons.Rounded.MyLocation, L.t("Start here"), c.text) { engine.hereStartFrom() }
            }
            // the details
            Column(Modifier.padding(top = 10.dp, end = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val addr = d?.address?.ifBlank { null } ?: h.place.sub
                if (addr.isNotBlank() && !dropped) DetailLine(Icons.Rounded.Place, addr)
                val hrs = d?.hours?.let { com.headunit.launcher.OpeningHours.pretty(it) }.orEmpty()
                if (hrs.isNotBlank()) DetailLine(Icons.Rounded.Schedule, hrs)
                if (h.distM >= 0) {
                    val arrive = if (h.etaSecs > 0) L.t("{0} · arrive {1}", Geo.fmtDist(h.distM, engine.imperial),
                        com.headunit.launcher.Clock.format().format(java.util.Date(System.currentTimeMillis() + (h.etaSecs * 1000).toLong())))
                        else L.t("{0} away", Geo.fmtDist(h.distM, engine.imperial))
                    DetailLine(Icons.Rounded.NearMe, arrive)
                }
                if (h.fuelPrice.isNotBlank()) HighlightLine(Icons.Rounded.LocalGasStation, Color(0xFFEA4335), L.t("Fuel"), h.fuelPrice)
                if (!d?.plugs.isNullOrEmpty()) HighlightLine(Icons.Rounded.EvStation, Color(0xFF34A853), L.t("Charger"), d!!.plugs.joinToString(" · "))
                if (dropped && h.land.isNotBlank()) DetailLine(landIcon(h.landClass, false), h.land)
            }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    val c = LocalVw.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(14.dp)).pressClick(onClick).padding(4.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.tile), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(21.dp))
        }
        Text(label, color = c.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp).widthIn(max = 72.dp))
    }
}

@Composable
private fun DetailLine(icon: ImageVector, text: String) {
    val c = LocalVw.current
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = c.sub, modifier = Modifier.padding(top = 1.dp).size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = c.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HighlightLine(icon: ImageVector, tint: Color, label: String, value: String) {
    val c = LocalVw.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.tile).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = c.text, fontSize = 13.sp, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Text(value, color = Good, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

/** The road as a little sign: motorway blue, A road green with yellow lettering, B road white, others a plain grey chip. */
@Composable
private fun RoadBadge(h: NavEngine.HereCard) {
    val c = LocalVw.current
    val label = h.ref.ifBlank { h.roadKind }
    if (label.isBlank()) return
    val (bg, fg, border) = when (h.roadClass) {
        "motorway", "motorway_link" -> Triple(Color(0xFF0B5FBF), Color.White, Color.White)
        "trunk", "trunk_link", "primary", "primary_link" -> Triple(Color(0xFF0B6B3A), Color(0xFFFFD200), Color.White)
        "secondary" -> Triple(Color.White, Color.Black, Color.Black)
        else -> Triple(c.tile, c.text, Color.Transparent)
    }
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).border(1.5.dp, border, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
    // the kind of road beside a numbered badge
    if (h.ref.isNotBlank() && h.roadKind.isNotBlank()) Text(h.roadKind, color = c.sub, fontSize = 13.sp, maxLines = 1)
}

@Composable
private fun Chip(icon: ImageVector, text: String) {
    val c = LocalVw.current
    Row(Modifier.clip(RoundedCornerShape(50)).background(c.tile).padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.sub, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Fact(icon: ImageVector, text: String, tint: Color) {
    val c = LocalVw.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = c.text, fontSize = 14.sp, maxLines = 1)
    }
}

/** A small round speed limit sign (red ring, black number). */
@Composable
private fun MiniLimitSign(v: Int) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White).border(5.dp, Color(0xFFE0202A), CircleShape), contentAlignment = Alignment.Center) {
        Text("$v", color = Color.Black, fontSize = if (v >= 100) 15.sp else 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

private fun landIcon(cls: String, dropped: Boolean): ImageVector = when (cls) {
    "residential" -> Icons.Rounded.Home
    "park", "grass", "wood", "scrub", "pitch", "cemetery", "agriculture", "national_park" -> Icons.Rounded.Park
    "commercial_area", "commercial", "retail" -> Icons.Rounded.Storefront
    "industrial" -> Icons.Rounded.Factory
    "school" -> Icons.Rounded.School
    "hospital" -> Icons.Rounded.LocalHospital
    "building" -> Icons.Rounded.Business
    else -> Icons.Rounded.Place
}

/** The card's colour, from what the land is (green for parks, amber for shops...) or the road (blue on a motorway). */
private fun landColour(cls: String, road: String): Color = when (cls) {
    "park", "grass", "wood", "scrub", "pitch", "cemetery", "agriculture", "national_park" -> Color(0xFF3DBE6E)
    "commercial_area", "commercial", "retail" -> Color(0xFFFFA53D)
    "industrial" -> Color(0xFF9A8CFF)
    "school" -> Color(0xFFFFC83D)
    "hospital" -> Color(0xFFFF5A5A)
    "residential", "building" -> Color(0xFF6FA8FF)
    else -> when (road) { "motorway", "motorway_link" -> Color(0xFF3C8CFF); "trunk", "primary" -> Color(0xFF3DBE6E); else -> Color(0xFF6FA8FF) }
}
