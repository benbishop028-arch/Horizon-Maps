package com.headunit.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.L
import com.headunit.launcher.NavEngine
import com.headunit.launcher.PreferenceKeys
import com.headunit.launcher.carSpeedSpot
import com.headunit.launcher.setCarSpeedSpot
import com.headunit.launcher.car.CarLayout

/**
 * The car's quick settings as a card over the map (on cars that pass taps on to the app): three pages down the side, with
 * switches, choices to tap and - / + steppers.
 * Android Auto's own list (CarSettingsScreen) could only be one long list of rows to tap through, which went back to the top
 * after each tap; it's still what a car without taps gets. Both change the same settings.
 */
object CarSettingsCard {
    var open by mutableStateOf(false)
    /** The page showing (kept while the car is connected, so it reopens where it was left). */
    internal var page by mutableIntStateOf(0)
    internal var scroll: ((Float) -> Unit)? = null

    /** A drag on the car screen while the card is open; true when the card took it. */
    fun scrollBy(dy: Float): Boolean {
        if (!open) return false
        scroll?.invoke(dy)
        return true
    }
}

@Composable
internal fun CarSettingsPanel(engine: NavEngine, actions: CarActions, small: Boolean, modifier: Modifier = Modifier) {
    val c = LocalVw.current
    val prefs = engine.prefs
    val page = CarSettingsCard.page
    val listState = rememberLazyListState()
    DisposableEffect(Unit) {
        CarSettingsCard.scroll = { dy -> listState.dispatchRawDelta(dy) }
        onDispose { CarSettingsCard.scroll = null }
    }
    LaunchedEffect(page) { listState.scrollToItem(0) }
    val guiding = engine.route != null && !engine.previewing
    val reroute = { engine.rerouteForSettings() }
    // (solid even with glass panels on: it's a list to read, over most of the map)
    Column(modifier.panel(12.dp, RoundedCornerShape(24.dp), c.surface.copy(alpha = 1f)).padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(if (small) 44.dp else 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(if (small) 42.dp else 48.dp).clip(CircleShape).carTap("st-back") { actions.closeSettings() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, L.t("Back"), tint = c.text, modifier = Modifier.size(26.dp))
            }
            Text(L.t("Settings"), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
        }
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(if (small) 112.dp else 140.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(L.t("Driving"), L.t("Map"), L.t("Display")).forEachIndexed { i, name ->
                    Row(Modifier.fillMaxWidth().height(if (small) 44.dp else 52.dp).clip(RoundedCornerShape(12.dp)).carTap("st-tab$i") { CarSettingsCard.page = i },
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(4.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(if (page == i) c.accent else Color.Transparent))
                        Text(name, color = if (page == i) c.accent else c.text, fontSize = 17.sp, fontWeight = if (page == i) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxHeight().padding(start = 6.dp), state = listState, verticalArrangement = Arrangement.spacedBy(if (small) 4.dp else 8.dp)) {
                when (page) {
                    // ---- Driving: the route, what it avoids and the voice
                    0 -> {
                        if (guiding) item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SetButton("st-end", L.t("End route"), Bad, Color.White) { engine.endRoute(); actions.closeSettings() }
                                SetButton("st-over", L.t("Route overview"), c.tile, c.text) { engine.overview(); actions.closeSettings() }
                                SetButton("st-alts", L.t("Other routes"), c.tile, c.text) { actions.closeSettings(); actions.otherRoutes() }
                                SetButton("st-stops", L.t("Stops"), c.tile, c.text) { actions.closeSettings(); actions.stops() }
                            }
                        }
                        item {
                            val sm = prefs.i(PreferenceKeys.SOUND_MODE, if (prefs.b(PreferenceKeys.VOICE, true)) 0 else 2).coerceIn(0, 2)
                            SetChoice("st-snd", L.t("Voice guidance"), listOf("0" to L.t("All"), "1" to L.t("Alerts only"), "2" to L.t("Off")), sm.toString()) { prefs.put(PreferenceKeys.SOUND_MODE, it.toInt()) }
                        }
                        item { SetSwitch("st-mw", L.t("Avoid motorways"), "", prefs.b("avoid_motorway", false)) { prefs.put("avoid_motorway", it); reroute() } }
                        item { SetSwitch("st-toll", L.t("Avoid toll roads"), "", prefs.b("avoid_toll", false)) { prefs.put("avoid_toll", it); reroute() } }
                        item { SetSwitch("st-ferry", L.t("Avoid ferries"), "", prefs.b("avoid_ferry", false)) { prefs.put("avoid_ferry", it); reroute() } }
                        item { SetSwitch("st-train", L.t("Avoid car trains"), "", prefs.b(PreferenceKeys.AVOID_TRAIN, false)) { prefs.put(PreferenceKeys.AVOID_TRAIN, it); reroute() } }
                    }
                    // ---- Map: how it's drawn and what's on it
                    1 -> {
                        item { SetChoice("st-view", L.t("View"), listOf("3d" to "3D", "2d" to "2D"), if (engine.tilt3d) "3d" else "2d") { if ((it == "3d") != engine.tilt3d) engine.toggleTilt() } }
                        item {
                            SetChoice("st-north", L.t("Map direction"), listOf("turn" to L.t("Turns with you"), "north" to L.t("North up")), if (engine.northUp) "north" else "turn") {
                                if ((it == "north") != engine.northUp) engine.toggleNorthUp()
                            }
                        }
                        item {
                            SetChoice("st-theme", L.t("Day and night"), listOf("car" to L.t("Car"), "sun" to L.t("Sun"), "phone" to L.t("Phone"), "day" to L.t("Day"), "night" to L.t("Night")),
                                prefs.s(PreferenceKeys.CARTHEME, "car")) { prefs.put(PreferenceKeys.CARTHEME, it); actions.themeChanged() }
                        }
                        item { SetSwitch("st-cams", L.t("Speed cameras"), L.t("Show and warn"), prefs.b("cameras", true)) { prefs.put("cameras", it) } }
                        item { SetSwitch("st-sig", L.t("Traffic lights"), L.t("On your route"), prefs.b("signals", true)) { prefs.put("signals", it) } }
                        item {
                            val v = prefs.i(PreferenceKeys.CAR_ARROW_SIZE, 100).coerceIn(30, 180)
                            SetStepper("st-arrow", L.t("Arrow size"), "$v%", { prefs.put(PreferenceKeys.CAR_ARROW_SIZE, (v - 10).coerceAtLeast(30)) }, { prefs.put(PreferenceKeys.CAR_ARROW_SIZE, (v + 10).coerceAtMost(180)) })
                        }
                        item {
                            SetSwitch("st-side", L.t("Keep the arrow to the side"), L.t("When the cards fold away, instead of moving to the middle"), prefs.b(PreferenceKeys.ARROW_SIDE, false)) { prefs.put(PreferenceKeys.ARROW_SIDE, it) }
                        }
                    }
                    // ---- Display: sizes and where things sit on the car screen
                    else -> {
                        item {
                            val v = prefs.i(PreferenceKeys.CARSCALE, 100).coerceIn(40, 300)
                            SetStepper("st-scale", L.t("Overall size"), "$v%" + (prefs.i(PreferenceKeys.CARSCALE_MAX, 0).takeIf { it in 100..299 && v > it }?.let { " (" + L.t("Up to {0}% fits this car's screen", it) + ")" } ?: ""), { prefs.put(PreferenceKeys.CARSCALE, (v - 10).coerceAtLeast(40)) }, { prefs.put(PreferenceKeys.CARSCALE, (v + 10).coerceAtMost(300)) })
                        }
                        item {
                            val opts = listOf("bl" to L.t("Bottom left"), "br" to L.t("Bottom right"), "middle" to L.t("Under turn"), "top" to L.t("Top right"))
                            SetChoice("st-sppos", L.t("Speedometer"), opts, prefs.carSpeedSpot()) { prefs.setCarSpeedSpot(it) }
                        }
                        item {
                            SetChoice("st-spst", L.t("Speedometer style"), listOf("app" to L.t("Same as the app"), "compact" to L.t("Compact")), prefs.s(PreferenceKeys.CAR_SPEED_STYLE, "app")) {
                                prefs.put(PreferenceKeys.CAR_SPEED_STYLE, it)
                            }
                        }
                        item { SetSwitch("st-swap", L.t("Big speed limit sign"), L.t("Swaps them round: the limit sign big, your speed small beside it"), prefs.b(PreferenceKeys.SPEED_SWAP, false)) { prefs.put(PreferenceKeys.SPEED_SWAP, it) } }
                        item {
                            SetChoice("st-clock", L.t("Clock"), listOf("auto" to L.t("Automatic"), "24" to L.t("24-hour"), "12" to L.t("12-hour")), prefs.s(PreferenceKeys.CLOCK, "auto")) { prefs.put(PreferenceKeys.CLOCK, it) }
                        }
                        item { SetSwitch("st-mapui", L.t("Search bar and places on the map"), L.t("Off for a car with a rotary knob: the full map, with Search, Home and Work under Places (★)"), prefs.b(PreferenceKeys.CAR_MAPUI, true)) { prefs.put(PreferenceKeys.CAR_MAPUI, it) } }
                        item { SetSwitch("st-soundbtn", L.t("Sound button on the map"), L.t("Off leaves more room for Recent places"), prefs.b(PreferenceKeys.CAR_SOUND_BTN, true)) { prefs.put(PreferenceKeys.CAR_SOUND_BTN, it) } }
                        item { SetSwitch("st-home", L.t("Home button"), L.t("Under the search bar"), prefs.b(PreferenceKeys.HOME_BTN, true)) { prefs.put(PreferenceKeys.HOME_BTN, it) } }
                        item { SetSwitch("st-work", L.t("Work button"), L.t("Under the search bar"), prefs.b(PreferenceKeys.WORK_BTN, true)) { prefs.put(PreferenceKeys.WORK_BTN, it) } }
                        item { SetSwitch("st-cargps", L.t("Use the car's GPS"), L.t("Off uses only the phone's GPS for your position and direction. Try off if the arrow turns sideways or lags behind"), prefs.b(PreferenceKeys.CAR_GPS, true)) { prefs.put(PreferenceKeys.CAR_GPS, it) } }
                        val cluster = prefs.b(PreferenceKeys.CLUSTER_MAP, true)
                        item { SetSwitch("st-clu", L.t("Map on the driver's display"), L.t("On cars that offer it"), cluster) { prefs.put(PreferenceKeys.CLUSTER_MAP, it) } }
                        if (cluster) item {
                            SetChoice("st-clut", L.t("Driver's display text size"), listOf("-1" to L.t("Smaller"), "0" to L.t("Normal"), "1" to L.t("Larger"), "2" to L.t("Largest")),
                                prefs.i(PreferenceKeys.CLUSTER_TEXT, 0).coerceIn(-1, 2).toString()) { prefs.put(PreferenceKeys.CLUSTER_TEXT, it.toInt()) }
                        }
                        // how close the map zooms in for turns and junctions (also in the phone's Settings)
                        item {
                            SetChoice("st-jz", L.t("Junction zoom"), listOf("-1" to L.t("Further"), "0" to L.t("Usual"), "1" to L.t("Closer"), "2" to L.t("Closest")),
                                prefs.s(PreferenceKeys.JUNCTION_ZOOM, "0")) { prefs.put(PreferenceKeys.JUNCTION_ZOOM, it) }
                        }
                        if (cluster) item {
                            SetChoice("st-cluz", L.t("Driver's display zoom"), listOf("1" to L.t("Closer"), "0" to L.t("Normal"), "-1" to L.t("Further out"), "-2" to L.t("Furthest out")),
                                prefs.i(PreferenceKeys.CLUSTER_ZOOM, 0).coerceIn(-2, 1).toString()) { prefs.put(PreferenceKeys.CLUSTER_ZOOM, it.toInt()) }
                        }
                    }
                }
            }
        }
    }
}

/** A button in a row of them (End route, Route overview, Other routes). */
@Composable
private fun RowScope.SetButton(id: String, text: String, fill: Color, fg: Color, act: () -> Unit) {
    Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(fill).carTap(id, act).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A setting that's on or off: the whole row is the switch. */
@Composable
private fun SetSwitch(id: String, title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
    val c = LocalVw.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.tile).carTap(id) { set(!on) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.padding(start = 10.dp).width(50.dp).height(28.dp).clip(RoundedCornerShape(50)).background(if (on) c.accent else c.line).padding(3.dp),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White))
        }
    }
}

/** A setting with a few values: all of them showing, the one picked lit up; tap another to change. */
@Composable
private fun SetChoice(id: String, title: String, options: List<Pair<String, String>>, current: String, pick: (String) -> Unit) {
    val c = LocalVw.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.tile).padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((key, label) in options) {
                val sel = key == current
                Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(50)).background(if (sel) c.accent else c.line).carTap("$id-$key") { pick(key) }.padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center) {
                    Text(label, color = if (sel) Color.White else c.text, fontSize = 14.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A size: - and + either side of the value. */
@Composable
private fun SetStepper(id: String, title: String, value: String, minus: () -> Unit, plus: () -> Unit) {
    val c = LocalVw.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.tile).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.line).carTap("$id-m", minus), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Remove, L.t("Smaller"), tint = c.text, modifier = Modifier.size(22.dp)) }
        Text(value, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.width(64.dp).padding(horizontal = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.line).carTap("$id-p", plus), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, L.t("Larger"), tint = c.text, modifier = Modifier.size(22.dp)) }
    }
}
