package com.headunit.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.Geo
import com.headunit.launcher.L
import com.headunit.launcher.Logos
import com.headunit.launcher.NavEngine
import com.headunit.launcher.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ChainBlue = Color(0xFF4C8DFF)

/**
 * A place's logo in a white circle (the shop's own, from its website), or [fallback] when there isn't one. Looked up once per
 * place off the main thread; nothing is fetched in offline mode.
 */
@Composable
internal fun PlaceLogo(p: Place, size: Dp, fallback: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dom = remember(p) { Logos.domainFor(p) }
    var bmp by remember(dom) { mutableStateOf(Logos.cached(dom)) }
    LaunchedEffect(dom) { if (bmp == null && dom != null) bmp = withContext(Dispatchers.IO) { Logos.load(ctx, dom) } }
    val b = bmp
    if (b != null) {
        Box(Modifier.size(size).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Image(b.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.size(size * 0.7f).clip(CircleShape))
        }
    } else fallback()
}

/** At the top of the search results: "See all 6 locations" when several branches of what was searched are nearby. */
@Composable
internal fun SeeAllRow(engine: NavEngine, q: String, count: Int, onOpen: () -> Unit) {
    val c = LocalVw.current
    val first = engine.results.firstOrNull { Logos.norm(it.name).startsWith(Logos.norm(q)) } ?: engine.results.first()
    val here = engine.fix?.pos
    val nearest = if (here != null) engine.results.filter { Logos.norm(it.name).startsWith(Logos.norm(q)) }.minOfOrNull { Geo.dist(here, it.pos) } else null
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(ChainBlue.copy(alpha = 0.13f))
            .border(2.dp, ChainBlue, RoundedCornerShape(18.dp)).clickable { onOpen(); engine.openChain(q) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaceLogo(first, 44.dp) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(c.tile), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Map, null, tint = ChainBlue, modifier = Modifier.size(22.dp)) }
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(L.t("See all {0} locations", count), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(L.t(if (engine.imperial) "Within 10 miles" else "Within 15 km") + (nearest?.let { " · " + L.t("nearest {0}", Geo.fmtDist(it, engine.imperial)) } ?: ""),
                color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.Map, null, tint = ChainBlue, modifier = Modifier.size(26.dp))
    }
}

private fun mins(secs: Double) = Math.max(1, Math.round(secs / 60.0).toInt())

/** A branch's road and area from its address: "Portswood Road" and "Portswood, Southampton". */
private fun roadOf(p: Place): Pair<String, String> {
    val parts = p.sub.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return p.name to ""
    // (a house number in front goes: "123 Portswood Road" -> "Portswood Road")
    val road = parts[0].replace(Regex("^[0-9]+[A-Za-z]?(-[0-9]+)?\\s+"), "")
    return road to parts.drop(1).take(2).joinToString(", ")
}

/** "All locations": the panel down the driver's side, with the branches sorted, the picked one opened out with Go. */
@Composable
internal fun ChainPanel(engine: NavEngine, modifier: Modifier) {
    val c = LocalVw.current
    val view = engine.chain
    Column(modifier.clip(RoundedCornerShape(28.dp)).background(c.surface).padding(16.dp)) {
        if (view == null) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ChainBlue) }
            return@Column
        }
        val sample = view.spots.first().place
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlaceLogo(sample, 46.dp) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(c.tile), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Place, null, tint = Color(0xFFEA4335)) }
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(view.name, color = c.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(L.t(if (engine.imperial) "{0} within 10 miles" else "{0} within 15 km", view.spots.size), color = c.sub, fontSize = 13.sp)
            }
            Box(Modifier.size(42.dp).clip(CircleShape).background(c.tile).clickable { engine.closeChain(back = true) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, L.t("Close"), tint = c.text)
            }
        }
        // the order: Quickest (drive time now), Nearest, On my route (while driving somewhere)
        val sorts = buildList {
            if (view.spots.any { it.secs >= 0 }) add("quick" to L.t("Quickest"))
            add("near" to L.t("Nearest"))
            if (view.spots.any { it.detour >= 0 }) add("route" to L.t("On my route"))
        }
        if (sorts.size > 1) Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.tile).padding(4.dp)) {
            for ((id, label) in sorts) {
                val on = engine.chainSort == id
                Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) ChainBlue else Color.Transparent).clickable { engine.setChainSort(id) }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center) {
                    Text(label, color = if (on) Color.White else c.sub, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
        val list = engine.chainSorted()
        LazyColumn(Modifier.padding(top = 10.dp).fillMaxWidth().weight(1f, fill = false)) {
            items(list, key = { "${it.place.pos.lat},${it.place.pos.lon}" }) { s ->
                val sel = engine.chainSel === s
                val (road, area) = roadOf(s.place)
                val time = if (s.secs >= 0) L.t("{0} min", mins(s.secs)) else null
                val dist = Geo.fmtDist(if (s.road >= 0) s.road else s.dist, engine.imperial)
                val extra = if (engine.chainSort == "route" && s.detour >= 0) L.t("+{0} min", mins(s.detour)) else null
                if (sel) {
                    Column(Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(ChainBlue.copy(alpha = 0.12f))
                        .border(2.dp, ChainBlue, RoundedCornerShape(20.dp)).padding(14.dp)) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(road, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (area.isNotEmpty()) Text(area, color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(extra ?: time ?: dist, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text(if (extra != null || time != null) dist else "", color = c.sub, fontSize = 13.sp)
                            }
                        }
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(ChainBlue).clickable { engine.chainGo(s) }.padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Navigation, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(L.t("Go"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            if (engine.route != null && engine.dest != null) Row(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.tile).clickable { engine.chainAddStop(s) }.padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Add, null, tint = c.text, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(L.t("Add as a stop"), color = c.text, fontSize = 15.sp)
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().clickable { engine.selectChain(s) }.padding(horizontal = 6.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(road, color = c.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (area.isNotEmpty()) Text(area, color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(extra ?: time ?: dist, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            if (extra != null || time != null) Text(dist, color = c.sub, fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(color = c.tile)
                }
            }
        }
    }
}

/** Over the map once it's been moved: looks for more branches round the middle of the map. */
@Composable
internal fun ChainSearchHere(engine: NavEngine, modifier: Modifier) {
    val c = LocalVw.current
    Row(modifier.clip(RoundedCornerShape(22.dp)).background(c.surface).clickable { engine.openChain(engine.chainQuery, aroundMap = true) }.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (engine.chainBusy) CircularProgressIndicator(color = ChainBlue, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        else Icon(Icons.Rounded.Refresh, null, tint = c.text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(L.t("Search this area"), color = c.text, fontSize = 15.sp)
    }
}
