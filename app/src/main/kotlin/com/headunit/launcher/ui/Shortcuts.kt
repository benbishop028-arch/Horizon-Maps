package com.headunit.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Close
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
    )
}

/** The ones picked (Settings › Places & trips), or as before when none are picked: Fuel first, or Charging and Toilets for an electric car. */
internal fun chosenShortcuts(engine: NavEngine): List<Shortcut> {
    val all = allShortcuts(engine)
    val picked = engine.prefs.s(PreferenceKeys.SHORTCUTS, "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val ids = picked.ifEmpty {
        if (engine.hideFuel) listOf("charging", "food", "coffee", "parking", "toilets") else listOf("fuel", "charging", "food", "coffee", "parking")
    }
    return ids.mapNotNull { id -> all.firstOrNull { it.id == id } }.take(5)
}

/** Settings › Places & trips: pick up to 5 shortcuts; they show in the order they were picked. */
@Composable
internal fun ShortcutsGroup(engine: NavEngine) {
    val c = LocalVw.current
    val prefs = engine.prefs
    val order = chosenShortcuts(engine).map { it.id }
    fun save(ids: List<String>) = prefs.put(PreferenceKeys.SHORTCUTS, ids.joinToString(","))
    SGroup(L.t("Shortcuts under the search bar"), footer = L.t("Pick up to 5. They show in this order. Tap one to add or take it off")) {
        Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (line in allShortcuts(engine).chunked(4)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (s in line) {
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
                            Text(L.t(s.name), color = if (on) c.text else c.sub, fontSize = 15.sp)
                            if (on) { Spacer(Modifier.width(8.dp)); Text("${at + 1}", color = c.sub, fontSize = 13.sp) }
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
    Triple("end", "End", Icons.Rounded.Close to Color(0xFFE5484D)),
)

/** Which trip card buttons are switched on (all of them unless changed). */
internal fun carTripButtonsOn(prefs: com.headunit.launcher.Prefs): Set<String> {
    val raw = prefs.s(PreferenceKeys.CAR_TRIP_BTNS, "*")
    return if (raw == "*") carTripButtons.map { it.first }.toSet() else raw.split(',').filter { it.isNotBlank() }.toSet()
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
