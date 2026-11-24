package com.headunit.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalConvenienceStore
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.L
import com.headunit.launcher.NavEngine
import com.headunit.launcher.PreferenceKeys

/** One of the round "find nearby" shortcuts under the search bar. */
internal class Shortcut(val id: String, val kind: String, val icon: ImageVector, val color: Color, val name: String, val desc: String)

/** Every shortcut there can be, in the order they're offered. Parking is lorry parks for a lorry. */
internal fun allShortcuts(engine: NavEngine): List<Shortcut> {
    val lorry = engine.prefs.s(PreferenceKeys.VEHICLE, "car") == "lorry"
    return listOf(
        Shortcut("fuel", "fuel", Icons.Rounded.LocalGasStation, Color(0xFFEA4335), "Fuel", "Fuel nearby"),
        Shortcut("charging", "charging", Icons.Rounded.EvStation, Color(0xFF34A853), "Charging", "Charging nearby"),
        Shortcut("food", "food", Icons.Rounded.Restaurant, Color(0xFFF29900), "Food", "Food nearby"),
        Shortcut("coffee", "coffee", Icons.Rounded.LocalCafe, Color(0xFFA56BFF), "Coffee", "Coffee nearby"),
        if (lorry) Shortcut("parking", "lorrypark", Icons.Rounded.LocalShipping, Color(0xFF4C8DFF), "Lorry parks", "Lorry parks nearby")
        else Shortcut("parking", "parking", Icons.Rounded.LocalParking, Color(0xFF4C8DFF), "Parking", "Parking nearby"),
        Shortcut("toilets", "toilets", Icons.Rounded.Wc, Color(0xFF4C8DFF), "Toilets", "Toilets nearby"),
        Shortcut("services", "services", Icons.Rounded.LocalConvenienceStore, Color(0xFF00A3A3), "Services", "Services nearby"),
        // your own saved places, and your GPX tracks (GitHub #164): the list opens nearest first, like the others
        Shortcut("places", "places", Icons.Rounded.Bookmarks, Color(0xFF12A4B4), "Your places", "Your places"),
        Shortcut("tracks", "tracks", Icons.Rounded.Route, Color(0xFFD4537E), "Your tracks", "Your tracks"),
    )
}

/** Saved in place of a list when every shortcut has been taken off. */
internal const val SHORTCUTS_NONE = "none"

/** The ones picked (Settings › Places & trips), or as before when none are picked: Fuel first, or Charging and Toilets for an electric car. */
internal fun chosenShortcuts(engine: NavEngine): List<Shortcut> {
    val all = allShortcuts(engine)
    val raw = engine.prefs.s(PreferenceKeys.SHORTCUTS, "")
    // "none": every one taken off on purpose. (Taking off the last one used to save an empty list, which reads as "never
    // picked" and brought the five standard ones straight back, so they couldn't all be turned off: Reddit)
    if (raw.trim() == SHORTCUTS_NONE) return emptyList()
    val picked = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val ids = picked.ifEmpty {
        if (engine.hideFuel) listOf("charging", "food", "coffee", "parking", "toilets") else listOf("fuel", "charging", "food", "coffee", "parking")
    }
    return ids.mapNotNull { id -> all.firstOrNull { it.id == id } }.take(5)
}

/** Settings › Places & trips: pick up to 5 shortcuts; they show in the order they were picked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShortcutsGroup(engine: NavEngine) {
    val c = LocalVw.current
    val prefs = engine.prefs
    val order = chosenShortcuts(engine).map { it.id }
    fun save(ids: List<String>) = prefs.put(PreferenceKeys.SHORTCUTS, if (ids.isEmpty()) SHORTCUTS_NONE else ids.joinToString(","))
    SGroup(L.t("Shortcuts under the search bar"), footer = L.t("Pick up to 5. They show in this order. Tap one to add or take it off")) {
        // (they wrap onto as many lines as the screen needs: in fixed rows of four, a phone squeezed the last one into a
        // column of single letters, GitHub #121)
        FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (s in allShortcuts(engine)) {
                    val at = order.indexOf(s.id)
                    val on = at >= 0
                    Row(
                        Modifier.clip(RoundedCornerShape(22.dp))
                            .background(if (on) s.color.copy(alpha = 0.2f) else c.tile)
                            .border(2.dp, if (on) s.color else Color.Transparent, RoundedCornerShape(22.dp))
                            .pressClick {
                                if (on) save(order - s.id)
                                else if (order.size < 5) save(order + s.id)
                                else engine.toast(L.t("Up to 5: take one off first"), 2500)
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(s.icon, null, tint = if (on) s.color else c.sub, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(L.t(s.name), color = if (on) c.text else c.sub, fontSize = 15.sp, maxLines = 1, softWrap = false)
                        if (on) { Spacer(Modifier.width(8.dp)); Text("${at + 1}", color = c.sub, fontSize = 13.sp, maxLines = 1) }
                    }
                }
        }
        // the picked ones in their order, each with arrows to move it (GitHub #121): arrows rather than dragging, as they're easy
        // to hit on a head unit
        if (order.size >= 2) {
            val all = allShortcuts(engine)
            Text(L.t("Your order").uppercase(L.locale), color = c.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                order.forEachIndexed { i, id ->
                    val s = all.firstOrNull { it.id == id } ?: return@forEachIndexed
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.tile).padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}", color = c.sub, fontSize = 14.sp, modifier = Modifier.width(22.dp))
                        Icon(s.icon, null, tint = s.color, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(L.t(s.name), color = c.text, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        for ((up, icon) in listOf(true to Icons.Rounded.KeyboardArrowUp, false to Icons.Rounded.KeyboardArrowDown)) {
                            val to = if (up) i - 1 else i + 1
                            val can = to in order.indices
                            Box(
                                Modifier.padding(start = 6.dp).size(44.dp).clip(CircleShape).alpha(if (can) 1f else 0.3f)
                                    .then(if (can) Modifier.background(c.line).pressClick {
                                        val m = order.toMutableList(); m[i] = order[to]; m[to] = order[i]; save(m)
                                    } else Modifier),
                                contentAlignment = Alignment.Center,
                            ) { Icon(icon, L.t(if (up) "Earlier" else "Later"), tint = c.text, modifier = Modifier.size(26.dp)) }
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(top = 4.dp)) {
            Spacer(Modifier.weight(1f))
            SButton(L.t("Reset")) { prefs.put(PreferenceKeys.SHORTCUTS, "") }
        }
    }
}

/** The buttons on the Android Auto trip card, in their order: id, name, icon, colour. */
internal val carTripButtons: List<Triple<String, String, Pair<ImageVector, Color>>> = listOf<Triple<String, String, Pair<ImageVector, Color>>>(
    Triple("overview", "Overview", Icons.Rounded.ZoomOutMap to Color(0xFF4C8DFF)),
    Triple("routes", "Other routes", Icons.Rounded.AltRoute to Color(0xFF4C8DFF)),
    Triple("sound", "Sound", Icons.Rounded.VolumeUp to Color(0xFF4C8DFF)),
    Triple("stop", "Add stop", Icons.Rounded.AddLocationAlt to Color(0xFF4C8DFF)),
    Triple("search", "Search", Icons.Rounded.Search to Color(0xFF4C8DFF)),
    Triple("report", "Report", Icons.Rounded.Campaign to Color(0xFF4C8DFF)),
    Triple("end", "End", Icons.Rounded.Close to Color(0xFFE5484D)),
)

/** Which trip card buttons are switched on (all of them unless changed). */
internal fun carTripButtonsOn(prefs: com.headunit.launcher.Prefs): Set<String> {
    var raw = prefs.s(PreferenceKeys.CAR_TRIP_BTNS, "*")
    // once: a trip card whose buttons were picked by hand before Add stop existed gets it too (it's the only quick way to a stop
    // on the car screen); taking it off afterwards is kept
    // (unchanged buttons already include it: only marked as done, so taking it off later isn't undone)
    if (!prefs.b(PreferenceKeys.CAR_TRIP_STOP_ADDED, false)) {
        if (raw != "*") {
            val next = raw.split(',').filter { it.isNotBlank() }.toSet() + "stop"
            raw = carTripButtons.map { it.first }.filter { it in next }.joinToString(",")
            prefs.put(PreferenceKeys.CAR_TRIP_BTNS, raw)
        }
        prefs.put(PreferenceKeys.CAR_TRIP_STOP_ADDED, true)
    }
    // (unless picked otherwise, just the button that ends the route: Overview and Other routes are among Android Auto's own round
    // buttons in the top corner while navigating, which fold away, so the trip card stays small)
    // (and Search: a stop on the way, or somewhere else to go, while navigating)
    // (and Report: police, a crash or a hazard with one tap, GitHub #290)
    // (and Add stop: the stops card, with fuel, charging, food and coffee along the route a tap away, as the phone's pin button)
    // (the default is just Add stop and End, so the card stays small; the rest can be added in Settings, GitHub #350)
    return if (raw == "*") setOf("stop", "end") else raw.split(',').filter { it.isNotBlank() }.toSet()
}

/** Settings > Android Auto: which buttons the trip card has, and how big they are (a narrower card leaves more of the map clear). */
@Composable
internal fun CarTripButtonsGroup(prefs: com.headunit.launcher.Prefs) {
    val c = LocalVw.current
    val on = carTripButtonsOn(prefs)
    SGroup(L.t("Buttons on the trip card"), footer = L.t("Tap to show or hide. Fewer buttons make the card narrower")) {
        Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (line in carTripButtons.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((id, name, look) in line) {
                        val sel = id in on
                        val col = look.second
                        Row(
                            Modifier.clip(RoundedCornerShape(22.dp))
                                .background(if (sel) col.copy(alpha = 0.2f) else c.tile)
                                .border(2.dp, if (sel) col else Color.Transparent, RoundedCornerShape(22.dp))
                                .pressClick {
                                    val next = if (sel) on - id else on + id
                                    prefs.put(PreferenceKeys.CAR_TRIP_BTNS, carTripButtons.map { it.first }.filter { it in next }.joinToString(","))
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(look.first, null, tint = if (sel) col else c.sub, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(L.t(name), color = if (sel) c.text else c.sub, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
        SChoice(L.t("Button size"), listOf("normal" to L.t("Normal"), "large" to L.t("Large"), "xl" to L.t("Extra large")), prefs.s(PreferenceKeys.CAR_TRIP_BTN_SIZE, "normal")) {
            prefs.put(PreferenceKeys.CAR_TRIP_BTN_SIZE, it)
        }
    }
}
