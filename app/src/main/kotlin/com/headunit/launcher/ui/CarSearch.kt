package com.headunit.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.Geo
import com.headunit.launcher.L
import com.headunit.launcher.NavEngine
import com.headunit.launcher.Place

/**
 * The search card on the car screen: drawn by the app over the map (Android Auto's own search screen takes the whole screen and
 * can only show one list), with Recent, Categories and Saved down the side, as Google Maps has it. Typing goes to Android
 * Auto's search screen: the car's keyboard only comes with that screen (the library gives an app no way to bring it up over
 * the map). The microphone listens straight away (see CarVoice), and what was said is searched for in the card.
 */
object CarSearch {
    /** The card is showing. */
    var open by mutableStateOf(false)
    /** The microphone is listening, and what it has made out so far. */
    var listening by mutableStateOf(false)
    var heard by mutableStateOf("")
    /** A line under the search bar when listening didn't work out ("Didn't catch that"). */
    var note by mutableStateOf("")
    /** What was said, being searched for in the card. */
    var query by mutableStateOf("")

    /** Opened from the stops card's "Add a stop": what's picked is added as a stop, without asking whether to go there instead. */
    var forStop by mutableStateOf(false)

    /** The card as it opens: nothing searched for. */
    fun reset() { query = ""; note = ""; forStop = false }
    /** Scrolls the list (set while the card is showing): drags on the car screen come through Android Auto, not as touches. */
    internal var scroll: ((Float) -> Unit)? = null

    /** A drag on the car screen while the card is open; true when the card took it (so the map under it stays put). */
    fun scrollBy(dy: Float): Boolean {
        if (!open) return false
        scroll?.invoke(dy)
        return true
    }
}

private class SearchRow(val title: String, val sub: String, val place: Place, val icon: ImageVector, val tint: Color)

@Composable
internal fun CarSearchPanel(engine: NavEngine, actions: CarActions, small: Boolean, modifier: Modifier = Modifier) {
    val c = LocalVw.current
    var tab by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    DisposableEffect(Unit) {
        CarSearch.scroll = { dy -> listState.dispatchRawDelta(dy) }
        onDispose { CarSearch.scroll = null }
    }
    // (read each time the card opens or the tab changes: the lists are small)
    val places = engine.places
    val rows: List<SearchRow> = remember(tab, CarSearch.open) {
        val out = ArrayList<SearchRow>()
        val seen = HashSet<String>()
        fun add(title: String, p: Place, icon: ImageVector, tint: Color) {
            if (seen.add("%.4f,%.4f".format(p.pos.lat, p.pos.lon))) out += SearchRow(title, if (title == p.name) p.sub else p.name, p, icon, tint)
        }
        // (the app's colours: Home blue, Work amber, saved teal, favourites gold, recent grey)
        places.slot("home")?.let { add(L.t("Home"), it, Icons.Rounded.Home, Color(0xFF4C8DFF)) }
        places.slot("work")?.let { add(L.t("Work"), it, Icons.Rounded.Work, Color(0xFFF29900)) }
        if (tab == 0) for (p in places.recents()) add(p.name, p, Icons.Rounded.History, Color(0xFF9CA4BF))
        if (tab == 2) {
            for (cp in places.customs()) add(cp.label, cp.place, Icons.Rounded.Star, Color(0xFF12A4B4))
            for (p in places.favourites().sortedWith(Comparator { a, b -> java.text.Collator.getInstance(L.locale).compare(a.name, b.name) }))
                add(p.name, p, Icons.Rounded.Star, Color(0xFFF2C230))
        }
        out
    }
    val here = engine.fix?.pos
    val rowH = if (small) 56.dp else 64.dp
    // a place picked: with no route, the route to it. While driving it's a stop (from "Add a stop"), or asked about
    // (Add as a stop / Go here instead): it used to replace where you were going without a word
    val pick: (Place) -> Unit = { p ->
        val stop = CarSearch.forStop
        actions.closeSearch()
        // (no route yet: the place's card first, with Go, as on the phone)
        if (stop && engine.driving()) engine.addStop(p)
        else if (!engine.driving() && engine.route == null && engine.prefs.b(com.headunit.launcher.PreferenceKeys.WHATS_HERE, true) && !p.web.startsWith("gpx:")) engine.showHere(p)
        else engine.goOrAsk(p)
    }
    // what was said is searched for, and the list shows it's searching until the answer comes (or 8 s pass)
    val query = CarSearch.query
    val asking = query.trim().length >= 3
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (!asking) { busy = false; return@LaunchedEffect }
        busy = true
        val stale = engine.results
        engine.search(query.trim())
        withTimeoutOrNull(8_000L) { snapshotFlow { engine.results }.first { it !== stale } }
        busy = false
    }
    LaunchedEffect(query) { listState.scrollToItem(0) }
    // (solid even with glass panels on: it's a list to read, over most of the map)
    Column(modifier.panel(12.dp, RoundedCornerShape(24.dp), c.surface.copy(alpha = 1f)).padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // back, the search bar (opens Android Auto's keyboard search) and the microphone
        Row(Modifier.fillMaxWidth().height(if (small) 46.dp else 54.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // back: from the results to the lists, then out of the card
            Box(Modifier.size(if (small) 42.dp else 48.dp).clip(CircleShape).carTap("sp-back") {
                if (CarSearch.query.isNotEmpty()) CarSearch.query = "" else actions.closeSearch()
            }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, L.t("Back"), tint = c.text, modifier = Modifier.size(26.dp))
            }
            Row(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50)).background(c.tile).carTap("sp-type") { actions.typeSearch() }.padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = c.sub, modifier = Modifier.size(24.dp))
                val listening = CarSearch.listening
                val line = when {
                    listening && CarSearch.heard.isNotBlank() -> CarSearch.heard
                    listening -> L.t("Listening…")
                    query.isNotEmpty() -> query
                    CarSearch.note.isNotBlank() -> CarSearch.note
                    else -> L.t(if (CarSearch.forStop) "Add a stop" else "Where to?")
                }
                Text(line, color = if (listening) c.accent else if (query.isNotEmpty()) c.text else c.sub, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 12.dp))
                // clear what's typed
                if (query.isNotEmpty() && !listening) Box(Modifier.size(38.dp).clip(CircleShape).carTap("sp-clear") { CarSearch.query = "" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, L.t("Clear"), tint = c.sub, modifier = Modifier.size(22.dp))
                }
            }
            // (red while it's listening: tap again to stop)
            Box(Modifier.size(if (small) 42.dp else 48.dp).clip(CircleShape).background(if (CarSearch.listening) Bad else c.accent).carTap("sp-mic") { actions.voiceSearch() },
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Mic, L.t("Voice search"), tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        // searching for what was said: the results take the tabs' place
        if (query.isNotEmpty()) {
            val found = engine.results
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (!asking || busy || found.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (asking && busy) androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), color = c.accent, strokeWidth = 3.dp)
                    else Text(L.t(if (!asking) "Type a place, address or postcode" else "No matches. Try adding a town or postcode."), color = c.sub, fontSize = 16.sp)
                } else LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    itemsIndexed(found) { i, p ->
                        val d = if (here != null) Geo.fmtDist(Geo.dist(here, p.pos), engine.imperial) else ""
                        CarSearchLine(p.name, p.sub, d, Icons.Rounded.Place, Color(0xFFEA4335), rowH, Modifier.carTap("sp-f$i") { pick(p) })
                    }
                }
            }
        } else Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(if (small) 124.dp else 150.dp).fillMaxHeight().padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(L.t("Recent"), L.t("Categories"), L.t("Saved")).forEachIndexed { i, name ->
                    Row(Modifier.fillMaxWidth().height(if (small) 44.dp else 52.dp).clip(RoundedCornerShape(12.dp)).carTap("sp-tab$i") { tab = i },
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(4.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(if (tab == i) c.accent else Color.Transparent))
                        Text(name, color = if (tab == i) c.accent else c.text, fontSize = 17.sp, fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
            if (tab == 1) {
                val kinds = listOf(
                    Triple("fuel", L.t("Fuel"), Icons.Rounded.LocalGasStation to Color(0xFFEA4335)), Triple("charging", L.t("Charging"), Icons.Rounded.EvStation to Color(0xFF34A853)),
                    Triple("food", L.t("Food"), Icons.Rounded.Restaurant to Color(0xFFF29900)), Triple("coffee", L.t("Coffee"), Icons.Rounded.LocalCafe to Color(0xFFA56BFF)),
                    Triple("parking", L.t("Parking"), Icons.Rounded.LocalParking to Color(0xFF4C8DFF)), Triple("toilets", L.t("Toilets"), Icons.Rounded.Wc to Color(0xFF4C8DFF)),
                )
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = listState) {
                    itemsIndexed(kinds) { i, k ->
                        CarSearchLine(k.second, "", "", k.third.first, k.third.second, rowH, Modifier.carTap("sp-k$i") { actions.closeSearch(); actions.nearby(k.first) })
                    }
                }
            } else if (rows.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(L.t("No saved or recent places yet"), color = c.sub, fontSize = 16.sp)
                }
            } else LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = listState) {
                itemsIndexed(rows) { i, r ->
                    val d = if (here != null) Geo.fmtDist(Geo.dist(here, r.place.pos), engine.imperial) else ""
                    CarSearchLine(r.title, r.sub, d, r.icon, r.tint, rowH, Modifier.carTap("sp-r$tab-$i") { pick(r.place) })
                }
            }
        }
    }
}

@Composable
private fun CarSearchLine(title: String, sub: String, trail: String, icon: ImageVector, tint: Color, h: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    val c = LocalVw.current
    Row(modifier.fillMaxWidth().height(h).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, color = c.sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trail.isNotBlank()) Text(trail, color = Good, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
    }
}
