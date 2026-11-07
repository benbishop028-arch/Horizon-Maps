package com.headunit.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
 * The What's here card: the road as its own sign (a motorway badge in blue, an A road in green with yellow lettering, a B road
 * in white), a mini speed limit sign, what the land is and the area with their own icons, how far it is from you, and Go / Add as a
 * stop / Save.
 */
@Composable
internal fun HereCardView(engine: NavEngine, h: NavEngine.HereCard) {
    val c = LocalVw.current
    val dropped = h.place.name == "Dropped pin"
    val title = if (!dropped) h.place.name else h.road.ifBlank { if (h.loading) "Looking…" else "This spot" }
    val accent = landColour(h.landClass, h.roadClass)
    Column(
        Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 16.dp).panel(12.dp, RoundedCornerShape(24.dp), c.surface),
    ) {
        // a thin band in the colour of what's here, along the top
        Box(Modifier.fillMaxWidth().height(5.dp).background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.25f)))))
        Column(Modifier.padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // what kind of place, as a coloured tile
                Box(Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(landIcon(h.landClass, dropped), null, tint = accent, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoadBadge(h)
                        if (h.area.isNotBlank()) Chip(Icons.Rounded.Place, h.area)
                    }
                }
                // the speed limit, as a little sign
                if (h.limitMs > 0) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 8.dp)) {
                    MiniLimitSign(Math.round(h.limitMs * if (engine.imperial) 2.23694 else 3.6).toInt())
                    if (h.limitEstimated) Text("usual", color = c.sub, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Box(Modifier.size(40.dp).clip(CircleShape).pressClick { engine.here = null }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, "Close", tint = c.sub, modifier = Modifier.size(22.dp))
                }
            }
            // what the land is, and how far it is
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (h.land.isNotBlank()) Fact(landIcon(h.landClass, false), h.land, accent)
                if (h.distM >= 0) Fact(Icons.Rounded.NearMe, Geo.fmtDist(h.distM, engine.imperial) + " away", c.accent)
            }
            Text(String.format(java.util.Locale.US, "%.5f, %.5f", h.place.pos.lat, h.place.pos.lon), color = c.sub.copy(alpha = 0.8f), fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 8.dp))
            // Go, Add as a stop, Save
            Row(Modifier.padding(top = 12.dp, end = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val driving = engine.route != null && !engine.previewing
                Row(
                    Modifier.clip(RoundedCornerShape(24.dp)).background(c.accent).pressClick { engine.hereGo(false) }.padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Navigation, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (driving) "Go here instead" else "Go", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                if (driving) Row(
                    Modifier.clip(RoundedCornerShape(24.dp)).background(c.tile).pressClick { engine.hereGo(true) }.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AddLocationAlt, null, tint = c.text, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add as a stop", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                var saved by remember(h.place) { mutableStateOf(engine.places.favourites().any { Geo.dist(it.pos, h.place.pos) < 15.0 }) }
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(if (saved) Color(0x33FFC83D) else c.tile).pressClick { saved = engine.hereSave() },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (saved) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Save", tint = if (saved) Color(0xFFFFC83D) else c.text, modifier = Modifier.size(24.dp)) }
            }
        }
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
