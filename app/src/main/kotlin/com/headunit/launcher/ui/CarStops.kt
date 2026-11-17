package com.headunit.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.Geo
import com.headunit.launcher.L
import com.headunit.launcher.NavEngine
import com.headunit.launcher.Place

/**
 * The stops card on the car screen (tap the trip card's figures while navigating): the stops on the way in order, each with
 * when you get there, then where you're going. Stops can be moved earlier or later and taken off; the route is worked out again
 * with Update route, as in the app's stops editor, rather than after every tap.
 */
object CarStops {
    /** The card is showing. */
    var open by mutableStateOf(false)
    /** The stops as changed on the card, until Update route (null: as they are on the route). */
    var draft by mutableStateOf<List<Place>?>(null)

    fun show() { draft = null; open = true }
    /** Scrolls the list (set while the card is showing): drags on the car screen come through Android Auto, not as touches. */
    internal var scroll: ((Float) -> Unit)? = null

    /** A drag on the car screen while the card is open; true when the card took it (so the map under it stays put). */
    fun scrollBy(dy: Float): Boolean {
        if (!open) return false
        scroll?.invoke(dy)
        return true
    }
}

@Composable
internal fun CarStopsPanel(engine: NavEngine, actions: CarActions, small: Boolean, modifier: Modifier = Modifier) {
    val c = LocalVw.current
    val listState = rememberLazyListState()
    DisposableEffect(Unit) {
        CarStops.scroll = { dy -> listState.dispatchRawDelta(dy) }
        onDispose { CarStops.scroll = null }
    }
    // (a stop reached, or one added by voice, while the card is open: what was being changed here is out of date)
    val via = engine.via
    LaunchedEffect(via) { CarStops.draft = null }
    val draft = CarStops.draft
    val stops = draft ?: via
    val etas = engine.stopEtas
    val rowH = if (small) 54.dp else 62.dp
    val btn = if (small) 40.dp else 46.dp
    fun change(l: List<Place>) { CarStops.draft = l }
    // (solid even with glass panels on: it's a list to read, over most of the map)
    Column(modifier.panel(12.dp, RoundedCornerShape(24.dp), c.surface.copy(alpha = 1f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(if (small) 44.dp else 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(btn).clip(CircleShape).carTap("stp-back") { actions.closeSearch() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, L.t("Back"), tint = c.text, modifier = Modifier.size(26.dp))
            }
            Text(L.t("Stops on the way"), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
            if (stops.isEmpty()) item {
                Box(Modifier.fillMaxWidth().height(rowH), contentAlignment = Alignment.CenterStart) {
                    Text(L.t("No stops yet. Add one and the route goes through it on the way."), color = c.sub, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
            itemsIndexed(stops) { i, p ->
                // (the times are for the route as it is: once the order is changed here they'd be wrong until it's worked out again)
                val eta = if (draft == null) etas.firstOrNull { it.place === p } else null
                val line = if (eta != null) eta.arrive + " · " + Geo.fmtDur(eta.secs) + " · " + Geo.fmtDist(eta.dist, engine.imperial) else p.sub
                Row(Modifier.fillMaxWidth().height(rowH).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp, end = 6.dp)) {
                        Text(p.name, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (line.isNotBlank()) Text(line, color = if (eta != null) Good else c.sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    StopButton("stp-up$i", Icons.Rounded.KeyboardArrowUp, L.t("Earlier"), btn, c.text, i > 0) {
                        change(stops.toMutableList().also { l -> val t = l[i]; l[i] = l[i - 1]; l[i - 1] = t })
                    }
                    StopButton("stp-dn$i", Icons.Rounded.KeyboardArrowDown, L.t("Later"), btn, c.text, i < stops.size - 1) {
                        change(stops.toMutableList().also { l -> val t = l[i]; l[i] = l[i + 1]; l[i + 1] = t })
                    }
                    StopButton("stp-rm$i", Icons.Rounded.Close, L.t("Remove"), btn, Bad, true) { change(stops.filterIndexed { n, _ -> n != i }) }
                }
            }
            // where you're going, last (with the time for the whole trip)
            item {
                val e = engine.eta
                Row(Modifier.fillMaxWidth().height(rowH).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFEA4335)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Flag, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(engine.dest?.name ?: L.t("Destination"), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (e != null && draft == null) Text(e.arrive + " · " + Geo.fmtDur(e.secs) + " · " + Geo.fmtDist(e.dist, engine.imperial), color = Good, fontSize = 14.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // (a search for the stop: what's picked there is added, without asking whether to go there instead)
            StopPill("stp-add", L.t("Add a stop"), if (draft == null) c.accent else c.tile, if (draft == null) Color.White else c.text, Modifier.weight(1f)) {
                CarStops.draft?.let { engine.setStops(it) }
                actions.addStopSearch()
            }
            if (draft != null) StopPill("stp-go", L.t("Update route"), c.accent, Color.White, Modifier.weight(1f)) {
                engine.setStops(draft); CarStops.draft = null
            }
        }
    }
}

@Composable
private fun StopButton(id: String, icon: ImageVector, what: String, size: Dp, tint: Color, enabled: Boolean, act: () -> Unit) {
    // (one that can't be used is still there, greyed, so the others don't move about under your finger)
    Box(Modifier.size(size).clip(CircleShape).then(if (enabled) Modifier.carTap(id, act) else Modifier), contentAlignment = Alignment.Center) {
        Icon(icon, what, tint = if (enabled) tint else tint.copy(alpha = 0.25f), modifier = Modifier.size(size * 0.62f))
    }
}

@Composable
private fun StopPill(id: String, text: String, fill: Color, fg: Color, modifier: Modifier, act: () -> Unit) {
    Box(modifier.height(46.dp).clip(RoundedCornerShape(23.dp)).background(fill).carTap(id, act).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A place picked while a route is being driven: a stop on the way, or where to go instead? (Picked from the search card, a
 * typed search or a saved place: before, any of them just replaced where you were going.)
 */
@Composable
internal fun CarChooseCard(engine: NavEngine, p: Place, modifier: Modifier = Modifier) {
    val c = LocalVw.current
    Column(modifier.widthIn(max = 460.dp).panel(12.dp, RoundedCornerShape(24.dp), c.surface.copy(alpha = 1f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(p.name, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (p.sub.isNotBlank()) Text(p.sub, color = c.sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        StopPill("ch-stop", L.t("Add as a stop"), c.accent, Color.White, Modifier.fillMaxWidth()) { engine.chooseStop() }
        StopPill("ch-go", L.t("Go here instead"), c.tile, c.text, Modifier.fillMaxWidth()) { engine.chooseGo() }
        StopPill("ch-no", L.t("Cancel"), c.tile, c.sub, Modifier.fillMaxWidth()) { engine.choose = null }
    }
}
