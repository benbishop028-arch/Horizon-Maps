
package com.headunit.launcher.ui

import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.BuildConfig
import com.headunit.launcher.FuelFinder
import com.headunit.launcher.Geo
import com.headunit.launcher.L
import com.headunit.launcher.NavEngine
import com.headunit.launcher.OfflinePlaces
import com.headunit.launcher.Place
import com.headunit.launcher.Prefs
import com.headunit.launcher.PreferenceKeys
import com.headunit.launcher.mapSource
import com.headunit.launcher.mapDetail
import com.headunit.launcher.setApiKey
import com.headunit.launcher.Zones
import com.headunit.launcher.Vehicle
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A settings section; [heading] puts a small group heading (Map, Driving, You, Device) above it in the list. */
private class Section(val title: String, val sub: String, val icon: ImageVector, val tint: Color, val heading: String? = null)

// (the order here is the order in the list; SettingsIndex refers to them by number)
// (made each time it's read, so the names follow a change of language straight away)
private val sections: List<Section> get() = listOf(
    Section(L.t("Map style"), L.t("Colours, day and night, map detail"), Icons.Rounded.Map, Color(0xFF4C8DFF), "MAP"),
    Section(L.t("Light & 3D"), L.t("Light, weather, 3D, tilt, sky"), Icons.Rounded.WbTwilight, Color(0xFF4C8DFF)),
    Section(L.t("On the map"), L.t("Traffic, lights, cameras, signs, weather"), Icons.Rounded.Traffic, Color(0xFF4C8DFF)),
    Section(L.t("Your arrow & route line"), L.t("Arrow or car, colours, route look"), Icons.Rounded.Navigation, Color(0xFF4C8DFF)),
    Section(L.t("Routes"), L.t("Vehicle, avoid, choosing, starting"), Icons.Rounded.Route, Color(0xFF12A4B4), "DRIVING"),
    Section(L.t("Turn card & lanes"), L.t("Signs, lanes, trip card, arriving"), Icons.Rounded.TurnRight, Color(0xFF12A4B4)),
    Section(L.t("Alerts"), L.t("Speed, cameras, hazards, reports"), Icons.Rounded.NotificationsActive, Color(0xFFEA4335)),
    Section(L.t("Sound & voice"), L.t("Spoken directions and volume"), Icons.Rounded.VolumeUp, Color(0xFFEA4335)),
    Section(L.t("Places & trips"), L.t("Home, work, favourites, sharing, trip log"), Icons.Rounded.Star, Color(0xFFF29900), "YOU"),
    Section(L.t("Fuel & charging"), L.t("Your fuel, prices, electric car"), Icons.Rounded.LocalGasStation, Color(0xFF34A853)),
    Section(L.t("Offline maps"), L.t("Maps, routes and town names with no signal"), Icons.Rounded.CloudDownload, Color(0xFF8E5CF0)),
    Section(L.t("Screen & sizes"), L.t("Brightness, sizes, glass, units, while driving"), Icons.Rounded.BrightnessMedium, Color(0xFFFFB020), "DEVICE"),
    Section(L.t("Android Auto"), L.t("Car-screen sizes and setting it up"), Icons.Rounded.DirectionsCar, Color(0xFF9CA4BF)),
    Section(L.t("Performance"), L.t("Smoothness on slower head units, demo"), Icons.Rounded.Speed, Color(0xFF9CA4BF)),
    Section(L.t("Keys & accounts"), L.t("Map keys and search"), Icons.Rounded.VpnKey, Color(0xFF9CA4BF)),
    Section(L.t("About & backup"), L.t("Feedback, updates, permissions, backup"), Icons.Rounded.Info, Color(0xFF9CA4BF)),
)

/** Every setting in one screen: sections on the left, the chosen section on the right, in the map screen's soft style. */
@Composable
fun SettingsScreen(prefs: Prefs, engine: NavEngine, onClose: () -> Unit) {
    val base = LocalVw.current
    val v = remember(base) { navVw(base) }
    CompositionLocalProvider(LocalVw provides v) { SettingsBody(prefs, engine, onClose) }
}

@Composable
private fun SettingsBody(prefs: Prefs, engine: NavEngine, onClose: () -> Unit) {
    val c = LocalVw.current
    // opens on the page you were last on (kept between visits)
    var cat by rememberSaveable { mutableIntStateOf(prefs.i("settingscat", 0).coerceIn(0, sections.size - 1)) }
    androidx.compose.runtime.LaunchedEffect(cat) { prefs.put("settingscat", cat) }
    // portrait / narrow: one pane at a time (the section list, or the open section with a back arrow)
    val narrowS = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 720
    var showList by rememberSaveable { mutableStateOf(true) }
    androidx.activity.compose.BackHandler(enabled = narrowS && !showList) { showList = true }
    var query by remember { mutableStateOf("") }
    var highlight by remember { mutableStateOf<String?>(null) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    val matches = remember(query) {
        if (words.isEmpty()) emptyList() else settingsIndex.filter { r ->
            // (English and the chosen language both match, so "Sprache" and "language" find the same setting)
            val hay = (r.title + " " + L.t(r.title) + " " + r.sub + " " + r.group + " " + L.t(r.group) + " " + sections[r.section].title).lowercase()
            words.all { hay.contains(it) }
        }.sortedBy { val t = it.title.lowercase() + " " + L.t(it.title).lowercase(); if (t.startsWith(words[0]) || L.t(it.title).lowercase().startsWith(words[0])) 0 else if (t.contains(words[0])) 1 else 2 }.take(40)
    }
    Row(Modifier.fillMaxSize().background(c.bg).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // the list of sections
        if (!narrowS || showList) Column(
            Modifier.then(if (narrowS) Modifier.fillMaxWidth() else Modifier.width(290.dp)).fillMaxHeight().clip(RoundedCornerShape(28.dp)).background(c.panel).border(1.dp, c.line, RoundedCornerShape(28.dp)).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(c.tile).pressClick(onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowBack, L.t("Back to the map"), tint = c.text, modifier = Modifier.size(24.dp))
                }
                Text(L.t("Settings"), color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp))
            }
            // search every setting by name
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).height(46.dp).clip(RoundedCornerShape(50)).background(c.tile).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.sub, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f).padding(start = 10.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(L.t("Search settings"), color = c.sub, fontSize = 15.sp)
                    androidx.compose.foundation.text.BasicTextField(
                        value = query, onValueChange = { query = it; highlight = null }, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 15.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) Icon(Icons.Rounded.Close, L.t("Clear"), tint = c.sub, modifier = Modifier.size(20.dp).clickable { query = ""; focus.clearFocus() })
            }
            if (words.isNotEmpty()) {
                if (matches.isEmpty()) Text(L.t("Nothing matches \"{0}\"", query.trim()), color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
                matches.forEach { r ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                            cat = r.section; highlight = r.title; query = ""; focus.clearFocus(); showList = false
                        }.padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Text(L.t(r.title), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(sections[r.section].title + (if (r.group.isNotBlank()) " · " + L.t(r.group) else ""), color = c.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else sections.forEachIndexed { i, s ->
                // the small group headings in the list: Map, Driving, You, Device
                if (s.heading != null) Text(L.t(s.heading), color = c.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(start = 14.dp, top = if (i == 0) 2.dp else 12.dp, bottom = 2.dp))
                val on = i == cat
                val rowBg by androidx.compose.animation.animateColorAsState(if (on) c.accent.copy(alpha = 0.16f) else Color.Transparent, androidx.compose.animation.core.tween(200), label = "secBg")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(rowBg).clickable { cat = i; highlight = null; showList = false }.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(s.tint.copy(alpha = if (on) 0.26f else 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(s.icon, null, tint = s.tint, modifier = Modifier.size(21.dp))
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(s.title, color = if (on) c.accent else c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(s.sub, color = c.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        // the chosen section (each opens at the top), sliding up gently into place
        if (!narrowS || !showList) androidx.compose.animation.AnimatedContent(
            targetState = cat,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            transitionSpec = {
                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 60)) +
                    androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(260)) { it / 24 }) togetherWith
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))
            },
            label = "section",
        ) { cat ->
          CompositionLocalProvider(LocalSettingHighlight provides highlight) {
           // the map pages show the real map at the top, so every change can be seen as it's made
           val withPreview = cat in 0..3
           Column(Modifier.fillMaxSize()) {
            if (withPreview && !narrowS) SettingsMapPreview(engine, 210)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp)) {
                if (withPreview && narrowS) SettingsMapPreview(engine, 170)
                if (narrowS) Row(Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(50)).clickable { showList = true }.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(c.tile), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ArrowBack, L.t("All settings"), tint = c.text, modifier = Modifier.size(22.dp))
                    }
                    Text(L.t("Settings"), color = c.sub, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp))
                }
                Text(sections[cat].title, color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
                Text(sections[cat].sub, color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, bottom = 16.dp))
                Column(Modifier.widthIn(max = 820.dp)) {
                    when (cat) {
                        0 -> MapStyleSection(prefs, engine)
                        1 -> LightSection(prefs)
                        2 -> OnTheMapSection(prefs, engine)
                        3 -> ArrowSection(prefs, engine)
                        4 -> RoutesSection(prefs, engine)
                        5 -> GuidanceSection(prefs, engine)
                        6 -> AlertsSection(prefs, engine)
                        7 -> SoundSection(prefs, engine)
                        8 -> { PlacesSection(engine); PhoneLinkGroup(prefs); SharingGroups(prefs) }
                        9 -> FuelSection(prefs, engine)
                        10 -> { OfflineMapsContent(prefs, engine); OfflinePlacesGroup(engine) }
                        11 -> ScreenSection(prefs, engine)
                        12 -> AndroidAutoSection(prefs)
                        13 -> PerformanceSection(prefs)
                        14 -> KeysSection(prefs, engine)
                        else -> AboutSection(prefs, engine)
                    }
                }
            }
           }
          }
        }
    }
}

// ------------------------------------------------------------------ Map look

@Composable
private fun MapStyleSection(prefs: Prefs, engine: NavEngine) {
    SGroup(L.t("Day and night")) {
        SChoice(L.t("Day and night"), listOf("auto" to L.t("Auto"), "lights" to L.t("Headlights"), "sun" to L.t("Sun times"), "times" to L.t("My times"), "day" to L.t("Day"), "night" to L.t("Night")), prefs.s(PreferenceKeys.THEME, "auto"),
            L.t("Auto follows the head unit's own night mode once it has switched to it, and sunrise and sunset until then. Headlights follows the head unit's night mode when it switches with your lights (turn on its headlight or illumination night-mode option), with sun times until then. Sun times switches at sunrise and sunset. My times switches at the times you set")) { prefs.put(PreferenceKeys.THEME, it) }
        // Headlights: whether this head unit has been seen switching with the lights yet (until then sun times stand in), and what the
        // lights signal says right now, so it's easy to check it works (lights on: it should say on within a couple of seconds)
        if (prefs.s(PreferenceKeys.THEME, "auto") == "lights") {
            val lightsOn = rememberSystemNight(useLamps = true)
            val seen = prefs.b(PreferenceKeys.SYSDARKSEEN, false) || lightsOn
            SNote(
                if (seen) L.t("Only your lights decide, in tunnels too. Lights signal now: {0}", if (lightsOn) L.t("on") else L.t("off"))
                else L.t("Your head unit hasn't switched with the lights yet, so sunrise and sunset are used until it does. Turn on its headlight or illumination night-mode option, then switch your lights on to check")
            )
            // units that pass the lights on in their own setting (Teyes, FYT-type): a few have it the other way round
            val ctxL = androidx.compose.ui.platform.LocalContext.current
            val hasLamps = androidx.compose.runtime.remember { readLampsNight(ctxL) != null }
            if (hasLamps) SToggle(prefs, L.t("Lights signal is reversed"), PreferenceKeys.LAMPS_INVERT, false, L.t("Turn on if it says on with your lights off"))
        }
        if (prefs.s(PreferenceKeys.THEME, "auto") == "times") {
            SFieldItem(L.t("Night starts at"), prefs.s(PreferenceKeys.NIGHT_FROM, "19:00"), L.t("24-hour clock, for example 19:30"), placeholder = "19:00") { prefs.put(PreferenceKeys.NIGHT_FROM, it.trim()) }
            SFieldItem(L.t("Day starts at"), prefs.s(PreferenceKeys.DAY_FROM, "07:00"), L.t("24-hour clock, for example 06:45"), placeholder = "07:00") { prefs.put(PreferenceKeys.DAY_FROM, it.trim()) }
        }
    }
    if (prefs.mapSource() == "mapbox") SGroup(L.t("Colours")) {
        MapColoursPicker(prefs.s(PreferenceKeys.MAP_COLOURS, "mapbox")) { picked ->
            val now = prefs.s(PreferenceKeys.MAP_COLOURS, "mapbox")
            // Custom starts as exactly the theme you had (nothing changes until you change something)
            if (picked == "custom" && now != "custom") {
                prefs.put(PreferenceKeys.CUSTOM_BASE, now)
                prefs.put(PreferenceKeys.CUSTOM_DAY, ""); prefs.put(PreferenceKeys.CUSTOM_NIGHT, ""); prefs.put(PreferenceKeys.CUSTOM_ROUTE_W, 100)
            }
            prefs.put(PreferenceKeys.MAP_COLOURS, picked)
        }
        if (prefs.s(PreferenceKeys.MAP_COLOURS, "mapbox") == "car") {
            SItem(L.t("Car's lighting colour"), L.t("The map, mostly at night, takes a hint of it"))
            val cur = prefs.s(PreferenceKeys.CAR_TINT, "#8A5AD0")
            Row(Modifier.padding(start = 4.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (hex in listOf("#E64B4B", "#FF9A3C", "#F2D04A", "#2FC9A0", "#3C8CFF", "#8A5AD0", "#E64BD8", "#FFFFFF")) {
                    val sel = hex.equals(cur, true)
                    Box(Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(android.graphics.Color.parseColor(hex)))
                        .border(if (sel) 3.dp else 1.dp, if (sel) LocalVw.current.accent else Color.White.copy(alpha = 0.25f), androidx.compose.foundation.shape.CircleShape)
                        .pressClick { prefs.put(PreferenceKeys.CAR_TINT, hex) })
                }
            }
            SPercent(L.t("How strong"), L.t("How much of the colour the map takes"), prefs, PreferenceKeys.CAR_TINT_STR, 10, 100, 40)
        }
    }
    if (prefs.mapSource() == "mapbox" && prefs.s(PreferenceKeys.MAP_COLOURS, "mapbox") == "custom") CustomThemeGroup(prefs, engine)
    if (prefs.mapSource() == "mapbox") SavedThemesGroup(prefs)
    SGroup(L.t("The map")) {
        SChoice(L.t("Map source"), listOf("mapbox" to L.t("Mapbox"), "tomtom" to L.t("TomTom"), "osm" to L.t("OpenStreetMap")), prefs.mapSource(),
            L.t("Mapbox: the 3D map. TomTom: TomTom's map in the Horizon colours (needs a TomTom key). All need the Mapbox token, as Mapbox's engine draws them")) { prefs.put(PreferenceKeys.MAP_STYLE, it) }
        if (prefs.mapSource() == "mapbox") {
            SChoice(L.t("Map detail"), listOf("full" to L.t("Full 3D"), "lite" to L.t("Lite"), "lightest" to L.t("Lightest")), prefs.mapDetail(),
                L.t("Full 3D: buildings, landmarks, trees and shadows. Lite: flat buildings, no landmarks or shadows. Lightest: Mapbox's simpler 2D map, for slow head units")) { prefs.put(PreferenceKeys.MAP_DETAIL, it) }
        }
        DefaultZoomRow(prefs)
        SToggle(prefs, L.t("Footpaths and trails"), "paths", true, L.t("Turn off for a cleaner driving map"))
        SChoice(L.t("HD roads"), listOf("auto" to L.t("Automatic"), "on" to L.t("On"), "off" to L.t("Off")), prefs.s(PreferenceKeys.HD_ROADS, "auto"),
            sub = L.t("Lane markings, turn arrows and 3D overpasses where Mapbox has them (Full 3D). Automatic: on in the UK only, as in some countries they hide the ordinary streets")) { prefs.put(PreferenceKeys.HD_ROADS, it) }
    }
}

@Composable
private fun LightSection(prefs: Prefs) {
    if (prefs.mapSource() == "mapbox") SGroup(L.t("Light and weather")) {
        SPercent(L.t("Night brightness"), L.t("How bright the whole night map is. 100% is Mapbox's own night light"), prefs, PreferenceKeys.NIGHT_BRIGHT, 30, 200, 100)
        SToggle(prefs, L.t("Moonlight at night"), PreferenceKeys.MOONLIT, false, L.t("Like dusk, but from the moon: a low blue light with long soft shadows, in place of Mapbox's flat night light. The night sky and your colours stay"))
        if (prefs.b(PreferenceKeys.MOONLIT, false))
            SPercent(L.t("Moon strength"), L.t("How strongly the moon lights walls and casts shadows"), prefs, PreferenceKeys.MOON_STR, 0, 200, 100)
        SToggle(prefs, L.t("Shadows"), PreferenceKeys.SHADOWS, true, L.t("Buildings and trees cast shadows. Off is also lighter work for a slow head unit"))
        SToggle(prefs, L.t("Sun in its real place"), PreferenceKeys.SUN_REAL, false, L.t("By day, the light and shadows come from where the sun really is right now"))
        SToggle(prefs, L.t("Overcast light"), PreferenceKeys.OVERCAST, false, L.t("Soft daylight with no hard shadows, calmer in glare"))
        SToggle(prefs, L.t("Weather light"), PreferenceKeys.WEATHER_LIGHT, false, L.t("A greyer, softer daylight when it's raining or snowing where you are. Needs Weather effects on the map switched on (Settings > On the map), which also brings real rain and snow"))
    }
    SGroup(L.t("3D view"), footer = L.t("30° to 65°. Auto is the app's own choice (54°, or 52° with a lighter map). Tilt with speed and flattening at turns still work on top.")) {
        STilt(L.t("Tilt on a wide screen"), L.t("Landscape head units. Higher shows further ahead (more for a slow unit to draw)"), prefs, PreferenceKeys.TILT_WIDE)
        STilt(L.t("Tilt on a tall screen"), L.t("Portrait screens and phones"), prefs, PreferenceKeys.TILT_TALL)
        STilt(L.t("Tilt on Android Auto"), L.t("The car screen"), prefs, PreferenceKeys.TILT_CAR)
    }
    SGroup("3D") {
        SToggle(prefs, L.t("Hill shading"), PreferenceKeys.HILLSHADE, false, L.t("Soft shading on hillsides, so valleys and ridges show even on a flat map. Lighter work than 3D hills"))
        SToggle(prefs, L.t("3D buildings"), "buildings3d", true)
        SToggle(prefs, L.t("Detailed building fronts"), "facades", true, L.t("Windows, doors and shopfronts on buildings, where Mapbox has them (mostly city centres)"))
        SToggle(prefs, L.t("3D landmarks"), "landmarks3d", true, L.t("Detailed models of well-known buildings, bridges and stadiums"))
        SToggle(prefs, L.t("3D hills and valleys"), "terrain", false, L.t("Real terrain. Lovely in hilly areas, but harder work for a slow head unit"))
        SToggle(prefs, L.t("Trees in parks"), "trees", true, L.t("Mapbox's 3D trees in parks and along streets"))
        SToggle(prefs, L.t("Keep the road ahead clear"), "clearview", true, L.t("Mapbox's 3D trees and landmarks are left out round your arrow and, on a route, along the middle of the road just ahead, so they never hide it"))
        SToggle(prefs, L.t("Tap to highlight"), "tapselect", true, L.t("Tap a building to light it up; tap a shop or place, then hold on it to go there"))
    }
    SGroup(L.t("Sky and satellite")) {
        SToggle(prefs, L.t("Satellite"), "satellite", false)
        SToggle(prefs, L.t("Fog and sky"), "skyweather", true, L.t("Haze towards the horizon to match the weather: grey when overcast, thicker in rain, thick in fog"))
        SToggle(prefs, L.t("Night sky in 3D"), "nightsky", true, L.t("Twinkling stars above the horizon after dark"))
    }
}

// ------------------------------------------------------------------ On the map

@Composable
private fun OnTheMapSection(prefs: Prefs, engine: NavEngine) {
    SGroup(L.t("Holding on the map")) {
        SToggle(prefs, L.t("What's here"), PreferenceKeys.WHATS_HERE, true, L.t("Holding on the map shows the road, its speed limit and the area first, with Go and Add as a stop. Off goes straight there, as before"))
    }
    SGroup(L.t("Traffic")) {
        SToggle(prefs, L.t("Live traffic"), "traffic", true)
        SToggle(prefs, L.t("Traffic overview when stopped"), PreferenceKeys.STOPPED_OVERVIEW, true, L.t("After 30 seconds stopped (lights, a queue), the map pulls back to a top-down view of the traffic round you, and back again when you move off"))
        SToggle(prefs, L.t("Only show traffic on your route while navigating"), "routetrafficonly", false, L.t("Off shows traffic on the roads around you too, as Google Maps does"))
        SToggle(prefs, L.t("Light traffic in green"), PreferenceKeys.TRAFFIC_GREEN, false, L.t("Free-flowing main roads shown green, as well as slow traffic in amber and red"))
    }
    SGroup(L.t("On your route")) {
        SToggle(prefs, L.t("Traffic lights"), "signals", true, if (prefs.b(PreferenceKeys.SIGNALS, true)) L.t("Only the ones on your route. Now: ") + engine.map.signalInfo() else null)
        SToggle(prefs, L.t("Speed cameras and roadworks"), "hazardicons", true, L.t("Their icons on the map. The warnings are in Settings > Alerts"))
        if (prefs.b("hazardicons", true)) SPercent(L.t("Speed camera size"), L.t("The red camera badge on the map"), prefs, PreferenceKeys.CAM_SIZE, 50, 350)
        SToggle(prefs, L.t("Zebra crossings"), "zebras", true, L.t("Crossings on your route painted on the road, with Belisha beacons at zebras (close in)"))
        SToggle(prefs, L.t("Stop signs"), PreferenceKeys.STOP_SIGNS, true, L.t("Stop signs on your route shown on the map, with a short chime before each one (where OpenStreetMap has them)"))
        SToggle(prefs, L.t("Number-plate cameras"), PreferenceKeys.ALPR_CAMS, true, L.t("Flock and other automatic number-plate cameras mapped on OpenStreetMap. Shown on the map only, no alerts"))
    }
    SGroup(L.t("3D street furniture")) {
        SToggle(prefs, L.t("3D street lamps, traffic lights and cameras"), "furniture3d", true, L.t("Real-size 3D models with lit lamps and lights (Full 3D). Off: the simpler badges and blocks"))
        SToggle(prefs, L.t("Street lamps"), "lamps", true, if (prefs.b(PreferenceKeys.LAMPS, true)) L.t("Now: ") + engine.map.lampInfo() else null)
        if (prefs.b(PreferenceKeys.LAMPS, true)) SSlider(L.t("Street lamp spacing"), prefs.s(PreferenceKeys.LAMPGAP, "40").toFloatOrNull() ?: 40f, 15f..120f, " m", reset = 40f) { prefs.put(PreferenceKeys.LAMPGAP, it.toString()) }
    }
    SGroup(L.t("Weather")) {
        SToggle(prefs, L.t("Weather effects on the map"), "rainfx", false, L.t("Real rain and snow falling on the map (and fog) to match the weather where you are"))
        SToggle(prefs, L.t("Rain radar layer"), "radar", false, L.t("Live radar from RainViewer, under the route"))
    }
}

// ------------------------------------------------------------------ Your arrow & route line

@Composable
private fun ArrowSection(prefs: Prefs, engine: NavEngine) {
    SGroup(L.t("Your arrow"), footer = L.t("Real 3D model: drawn in the map itself, lit and shadowed like the buildings (Full 3D only; otherwise the picture of the same shape is used). The 3D arrow and car are pictures of 3D shapes. The flat arrow is the lightest to draw on a slow unit.")) {
        val loc = prefs.s(PreferenceKeys.LOCATOR, "arrow")
        val ctx = LocalContext.current
        val hasPic = prefs.s(PreferenceKeys.LOCATOR_IMAGE, "").isNotBlank() && java.io.File(ctx.filesDir, "locator.png").exists()
        val pickPic = rememberLocatorImagePicker { ok ->
            if (ok) { prefs.put(PreferenceKeys.LOCATOR_IMAGE, System.currentTimeMillis().toString()); prefs.put(PreferenceKeys.LOCATOR, "custom") }
            else android.widget.Toast.makeText(ctx, L.t("Couldn't read that picture (try a PNG or JPG)"), android.widget.Toast.LENGTH_SHORT).show()
        }
        fun choosePic() { if (!pickPic()) android.widget.Toast.makeText(ctx, L.t("No app on this unit can pick a picture (try installing a file manager or gallery)"), android.widget.Toast.LENGTH_LONG).show() }
        SPictureChoice(L.t("Locator"), null, listOf("arrow" to L.t("3D arrow"), "car" to L.t("Car"), "model" to L.t("Real 3D model"), "flat" to L.t("Flat arrow"), "custom" to L.t("Your picture")), loc, { id, c -> locatorPicture(id, c) }) {
            if (it == "custom" && !hasPic) choosePic() else prefs.put(PreferenceKeys.LOCATOR, it)
        }
        if (loc == "model") {
            SChoice(L.t("Model shape"), listOf("arrow" to L.t("Arrow"), "car" to L.t("Car")), prefs.s(PreferenceKeys.MODEL_SHAPE, "arrow")) { prefs.put(PreferenceKeys.MODEL_SHAPE, it) }
            SToggle(prefs, L.t("Glow"), PreferenceKeys.MODEL_GLOW, true, L.t("A soft blue glow round the model, and the model lit up brightly"))
            if (prefs.mapDetail() != "full") SNote(L.t("The real 3D model shows with Map detail: Full 3D. On Lite and Lightest the picture of the same shape is used."))
        }
        val shapeCar = loc == "car" || (loc == "model" && prefs.s(PreferenceKeys.MODEL_SHAPE, "arrow") == "car")
        val shapeArrow = loc == "arrow" || (loc == "model" && !shapeCar)
        if (loc == "custom") {
            SItem(L.t("Picture"), L.t("A PNG with a see-through background looks best. Point the top of it the way you drive")) { SButton(if (hasPic) L.t("Change") else L.t("Choose")) { choosePic() } }
            SToggle(prefs, L.t("Turns with you"), PreferenceKeys.LOCATOR_IMAGE_TURNS, true, L.t("Lies flat on the road and turns as you drive, like the arrow. Off keeps it upright"))
            SToggle(prefs, L.t("Round crop"), PreferenceKeys.LOCATOR_IMAGE_CIRCLE, false, L.t("Cuts it to a circle with a white edge (good for photos)"))
        }
        // the car's shape: for the Car picture and the real 3D car alike
        if (shapeCar) {
            val cur = prefs.s(PreferenceKeys.CAR_MODEL, "saloon")
            val colour = prefs.s(PreferenceKeys.CAR_COLOUR, "#F4F5F7")
            val names = listOf("saloon" to L.t("Saloon"), "hatch" to L.t("Hatchback"), "estate" to L.t("Estate"), "suv" to "SUV", "pickup" to L.t("Pickup"), "van" to L.t("Van"), "sports" to L.t("Sports"))
            SItem(L.t("Car type"), names.firstOrNull { it.first == cur }?.second)
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((id, name) in names) {
                    val pic = remember(id, colour) { try { engine.map.carPreview(id, colour).asImageBitmap() } catch (e: Throwable) { null } }
                    val sel = id == cur
                    androidx.compose.foundation.layout.Column(
                        Modifier.width(92.dp).clip(RoundedCornerShape(16.dp)).background(if (sel) LocalVw.current.accent.copy(alpha = 0.22f) else LocalVw.current.tile)
                            .border(if (sel) 2.dp else 0.dp, if (sel) LocalVw.current.accent else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable { prefs.put(PreferenceKeys.CAR_MODEL, id) }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (pic != null) androidx.compose.foundation.Image(pic, name, Modifier.size(80.dp))
                        Text(name, color = LocalVw.current.text, fontSize = 12.sp)
                    }
                }
            }
        }
        if (shapeCar) SColours(L.t("Car colour"), listOf(
            "#F4F5F7" to L.t("White"), "#B9BEC8" to L.t("Silver"), "#7A808C" to L.t("Grey"), "#3E434D" to L.t("Graphite"), "#2A2D34" to L.t("Black"), "#1E2A4A" to L.t("Navy"),
            "#2F6BDF" to L.t("Blue"), "#5FB3E8" to L.t("Sky blue"), "#1FA39A" to L.t("Teal"), "#2E8B57" to L.t("Green"), "#6E8B3D" to L.t("Olive"), "#F2C230" to L.t("Yellow"),
            "#F07C28" to L.t("Orange"), "#D7263D" to L.t("Red"), "#8E1B2E" to L.t("Burgundy"), "#7B4FC9" to L.t("Purple"), "#E86FA8" to L.t("Pink"), "#C9A27A" to L.t("Champagne"), "#6B4A35" to L.t("Brown"),
        ), prefs.s(PreferenceKeys.CAR_COLOUR, "#F4F5F7")) { prefs.put(PreferenceKeys.CAR_COLOUR, it) }
        else if (shapeArrow) SColours(L.t("Arrow colour"), listOf(
            "#F2F2F6" to L.t("White"), "#C2C6D0" to L.t("Silver"), "#8A909C" to L.t("Grey"), "#3E434D" to L.t("Graphite"), "#4C8DFF" to L.t("Blue"), "#6FD0FF" to L.t("Sky blue"),
            "#1FC7A6" to L.t("Teal"), "#3DDC84" to L.t("Green"), "#B5E34A" to L.t("Lime"), "#FFD54A" to L.t("Yellow"), "#FF9A3C" to L.t("Orange"), "#FF4D5E" to L.t("Red"),
            "#FF6FB5" to L.t("Pink"), "#A56BFF" to L.t("Purple"),
        ), prefs.s(PreferenceKeys.ARROW_COLOUR, "#F2F2F6")) { prefs.put(PreferenceKeys.ARROW_COLOUR, it) }
        SSlider(if (shapeCar) L.t("Car size") else if (loc == "custom") L.t("Picture size") else L.t("Arrow size"), prefs.s(PreferenceKeys.ARROW_PERCENT, "240").toFloatOrNull() ?: 240f, 100f..500f, "%", reset = 240f) { prefs.put(PreferenceKeys.ARROW_PERCENT, it.toString()) }
        SToggle(prefs, L.t("Headlight beam at night"), PreferenceKeys.BEAM, true, L.t("A soft cone of light on the road ahead of you after dark, while you're moving"))
    }
    SGroup(L.t("Route line")) {
        SToggle(prefs, L.t("3D flags"), PreferenceKeys.FLAGS_3D, true, L.t("A tall flag at where you're going, and smaller amber ones at your stops, standing above the buildings so you can see them from a distance"))
        SToggle(prefs, L.t("Turn arrow on the route"), "turnarrows", true, L.t("A white arrow through the next junction or roundabout"))
        SToggle(prefs, L.t("Chevrons along the route"), "routechevrons", true, L.t("Small arrows along the line when zoomed in"))
        SToggle(prefs, L.t("Route ribbon"), "ribbon", true, L.t("A soft shadow under the route line, and the route showing faintly through 3D buildings in front of it"))
        SToggle(prefs, L.t("Flowing light along the route"), "routeflow", true)
        SColours(L.t("Alternative route line colour"), listOf(
            "#C4D9E6" to L.t("Pale blue-grey (default)"), "#A9D4F0" to L.t("Light blue"), "#5AB7F5" to L.t("Blue"), "#315CBA" to L.t("Dark blue"),
            "#A9B4C2" to L.t("Silver grey"), "#FFFFFF" to L.t("White"), "#42CFA5" to L.t("Mint"), "#F4C542" to L.t("Yellow"),
            "#FF9A4A" to L.t("Orange"), "#F05D6C" to L.t("Red"), "#C58AF9" to L.t("Purple"), "#F08AC5" to L.t("Pink"),
        ), prefs.s(PreferenceKeys.ALT_ROUTE_COLOUR, "#C4D9E6")) { prefs.put(PreferenceKeys.ALT_ROUTE_COLOUR, it) }
    }
}

// ------------------------------------------------------------------ Routes

@Composable
private fun RoutesSection(prefs: Prefs, engine: NavEngine) {
    VehicleGroup(prefs, engine.imperial)
    SGroup(L.t("Avoid"), footer = L.t("Changes apply to the next route you plan. \"Avoid this road\" on the map only lasts for that trip.")) {
        SToggle(prefs, L.t("Avoid toll roads"), "avoid_toll", false, if (prefs.b(PreferenceKeys.AVOID_TOLL, false)) L.t("Everywhere") else null)
        // ...or only in some countries: a route that would use a toll road in one of them is planned without toll roads
        if (!prefs.b(PreferenceKeys.AVOID_TOLL, false)) {
            val chosen = prefs.s(PreferenceKeys.AVOID_TOLL_COUNTRIES, "").split(',').filter { it.isNotBlank() }.toSet()
            SItem(L.t("Avoid tolls only in"), if (chosen.isEmpty()) L.t("Pick countries: a route that would pay a toll in one of them is planned without toll roads") else L.t("A route that would pay a toll in one of these is planned without toll roads"))
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((cc, name) in listOf("FR" to L.t("France"), "IT" to L.t("Italy"), "ES" to L.t("Spain"), "PT" to L.t("Portugal"), "AT" to L.t("Austria"), "CH" to L.t("Switzerland"),
                    "SI" to L.t("Slovenia"), "HR" to L.t("Croatia"), "PL" to L.t("Poland"), "CZ" to L.t("Czechia"), "HU" to L.t("Hungary"), "GR" to L.t("Greece"), "IE" to L.t("Ireland"), "NO" to L.t("Norway"), "US" to "USA")) {
                    Pill(name, cc in chosen, { prefs.put(PreferenceKeys.AVOID_TOLL_COUNTRIES, (if (cc in chosen) chosen - cc else chosen + cc).joinToString(",")) })
                }
            }
        }
        SToggle(prefs, L.t("Avoid motorways"), "avoid_motorway", false)
        SToggle(prefs, L.t("Avoid ferries"), "avoid_ferry", false)
        SToggle(prefs, L.t("Avoid unpaved roads"), "avoid_unpaved", true)
        SToggle(prefs, L.t("Avoid Clean Air Zones and ULEZ"), "avoidzones", false, L.t("A zone you start or finish in can't be avoided"))
        ZonesItem()
    }
    SGroup(L.t("Choosing a route")) {
        SToggle(prefs, L.t("Eco routes"), "route_eco", false, L.t("Fuel-efficient instead of fastest"))
        SToggle(prefs, L.t("Prefer simpler routes"), "prefer_simple", true, L.t("When a route is only a minute or so slower but has far fewer turns, it's picked first (labelled Simplest). Off always picks the fastest"))
        SToggle(prefs, L.t("Offer faster routes"), "fasterroute", true)
        if (prefs.b(PreferenceKeys.FASTER_ROUTE, true)) SToggle(prefs, L.t("Keep other routes on the map"), "livealts", true, L.t("Grey lines with +/− minutes you can tap any time. Off shows a \"Faster route found\" card instead"))
        SToggle(prefs, L.t("Suggest where you're going"), "predict", true, L.t("With the live journey time, including home and work at your usual times"))
        SToggle(prefs, L.t("Weather on the route"), "routewx", true)
        SToggle(prefs, L.t("Arrive on your side of the road"), PreferenceKeys.ARRIVE_SIDE, true, L.t("The route brings you to the kerb side of where you're going, so you don't have to cross the traffic. Mapbox and offline routes"))
    }
    SGroup(L.t("Starting a route")) {
        SToggle(prefs, L.t("Auto-start route countdown"), PreferenceKeys.AUTO_START_ROUTE, true, L.t("Automatically start navigation when the countdown finishes"))
        SSlider(L.t("Auto-start countdown"), prefs.i(PreferenceKeys.AUTO_START_SECONDS, 10).coerceIn(3, 30).toFloat(), 3f..30f, " s", sub = L.t("How long before navigation starts"), steps = 26, reset = 10f) { prefs.put(PreferenceKeys.AUTO_START_SECONDS, it.coerceIn(3, 30)) }
        SToggle(prefs, L.t("Fly along a new route"), "cinematic", true, L.t("A 3-second 3D fly-through before the overview. Tap the map to skip"))
        SToggle(prefs, L.t("Fly over route button"), "flyover", true, L.t("On the route options: a 3D flight along the whole drive before you set off"))
    }
}

/** Car or lorry; for a lorry its size and weight, which every route is then planned for (see Vehicle). */
@Composable
private fun VehicleGroup(prefs: Prefs, imperial: Boolean) {
    val lorry = prefs.s(PreferenceKeys.VEHICLE, "car") == "lorry"
    SGroup(L.t("Vehicle"), footer = if (lorry) L.t("Used by TomTom and offline routes, and by Mapbox for height, width and weight. Limits come from the map data, so some may be missing: always follow the road signs.") else null) {
        SChoice(L.t("Vehicle"), listOf("car" to L.t("Car"), "lorry" to L.t("Lorry")), if (lorry) "lorry" else "car",
            if (lorry) L.t("Routes avoid low bridges, weight limits and lorry bans for your size") else null) { prefs.put(PreferenceKeys.VEHICLE, it) }
        if (lorry) {
            SStep(L.t("Height"), L.t("Including the load. Low bridges are checked against this"), prefs, PreferenceKeys.LORRY_HEIGHT, 400, 200, 500, 5) { Vehicle.heightText(it, imperial) }
            SStep(L.t("Width"), L.t("Without mirrors"), prefs, PreferenceKeys.LORRY_WIDTH, 255, 180, 300, 5) { String.format(java.util.Locale.US, "%.2f m", it / 100.0) }
            SStep(L.t("Length"), L.t("Tractor and trailer"), prefs, PreferenceKeys.LORRY_LENGTH, 1650, 500, 2500, 50) { String.format(java.util.Locale.US, "%.1f m", it / 100.0) }
            SStep(L.t("Weight"), L.t("Fully loaded"), prefs, PreferenceKeys.LORRY_WEIGHT, 440, 35, 600, 5) { tonnes(it) }
            SStep(L.t("Axle weight"), L.t("Heaviest axle"), prefs, PreferenceKeys.LORRY_AXLE, 115, 50, 200, 5) { tonnes(it) }
            SToggle(prefs, L.t("Hazardous goods"), PreferenceKeys.LORRY_HAZMAT, false, L.t("Avoids tunnels and roads closed to hazardous loads"))
        }
    }
}

private fun tonnes(t10: Int): String = if (t10 % 10 == 0) "${t10 / 10} t" else String.format(java.util.Locale.US, "%.1f t", t10 / 10.0)

/** A tilt in degrees with a slider, or Auto (0) for the app's own choice. Saved when you let go. */
@Composable
private fun STilt(title: String, sub: String, prefs: Prefs, key: String) {
    val c = LocalVw.current
    val saved = prefs.i(key, 0)
    var v by remember(key, saved) { mutableStateOf(if (saved in 30..65) saved.toFloat() else 54f) }
    var moving by remember(key, saved) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        SItem(title, sub) {
            Text(if (saved in 30..65 || moving) "${v.roundToInt()}°" else L.t("Auto"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (saved in 30..65) SButton(L.t("Auto")) { prefs.put(key, 0) }
        }
        androidx.compose.material3.Slider(
            value = v, onValueChange = { v = it; moving = true }, onValueChangeFinished = { prefs.put(key, v.roundToInt().coerceIn(30, 65)) },
            valueRange = 30f..65f, steps = 34,
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.tile),
        )
    }
}

/** A percentage with a slider ([min] to [max], in steps of 10), and Reset back to [def]. Saved when you let go. */
@Composable
internal fun SPercent(title: String, sub: String, prefs: Prefs, key: String, min: Int, max: Int, def: Int = 100) {
    val c = LocalVw.current
    val saved = prefs.i(key, def).coerceIn(min, max)
    var v by remember(key, saved) { mutableStateOf(saved.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        SItem(title, sub) {
            Text("${v.roundToInt()}%", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (saved != def) SButton(L.t("Reset")) { prefs.put(key, def) }
        }
        androidx.compose.material3.Slider(
            value = v, onValueChange = { v = (it / 10f).roundToInt() * 10f }, onValueChangeFinished = { prefs.put(key, v.roundToInt().coerceIn(min, max)) },
            valueRange = min.toFloat()..max.toFloat(), steps = (max - min) / 10 - 1,
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.tile),
        )
    }
}

/** A number set with − and + (a lorry's measurements): [show] turns the stored value into what's shown. */
@Composable
private fun SStep(title: String, sub: String, prefs: Prefs, key: String, def: Int, min: Int, max: Int, by: Int, show: (Int) -> String) {
    val c = LocalVw.current
    val v = prefs.i(key, def).coerceIn(min, max)
    SItem(title, sub) {
        SButton("−") { prefs.put(key, (v - by).coerceAtLeast(min)) }
        Text(show(v), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 92.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        SButton("+") { prefs.put(key, (v + by).coerceAtMost(max)) }
    }
}

// ------------------------------------------------------------------ Turn card & lanes

@Composable
private fun GuidanceSection(prefs: Prefs, engine: NavEngine) {
    val mi = engine.imperial
    SGroup(L.t("Turn card")) {
        SToggle(prefs, L.t("UK road signs on the turn card"), "uksigns", true)
        SToggle(prefs, L.t("Living turn card"), "livecard", true, L.t("Grows and glows as the turn gets close"))
        SToggle(prefs, L.t("Lane guidance on the turn card"), "lanestrip", true)
        if (prefs.b(PreferenceKeys.LANE_STRIP, true)) SChoice(L.t("Lane guidance style"), listOf("hero" to L.t("Lane hero view"), "classic" to L.t("Classic strip")), prefs.s(PreferenceKeys.LANE_STYLE, "hero"),
            if (prefs.s(PreferenceKeys.LANE_STYLE, "hero") == "hero") L.t("Before junctions with two or more lanes the turn card shows a big lane picture with yours lit") else L.t("The small lane strip at every junction")) { prefs.put(PreferenceKeys.LANE_STYLE, it) }
        SChoice(L.t("Next turn on the turn card"), listOf("close" to L.t("When close"), "always" to L.t("Always"), "off" to L.t("Off")), prefs.s(PreferenceKeys.THEN_MODE, "close"),
            when (prefs.s(PreferenceKeys.THEN_MODE, "close")) { "close" -> L.t("Only when the turn after comes straight after (within about 15 seconds)"); "always" -> L.t("The turn after the next one, all the time"); else -> null }) { prefs.put(PreferenceKeys.THEN_MODE, it) }
        if (prefs.s(PreferenceKeys.THEN_MODE, "close") != "off") SChoice(L.t("Shown as"), listOf("row" to L.t("A line under the card"), "icon" to L.t("A small arrow")), prefs.s(PreferenceKeys.THEN_STYLE, "row")) { prefs.put(PreferenceKeys.THEN_STYLE, it) }
    }
    SGroup(L.t("Lanes and junctions")) {
        SToggle(prefs, L.t("Junction pictures"), PreferenceKeys.JUNCTION_PICS, false, L.t("Mapbox's pictures of motorway junctions, with the lanes to use, in the last kilometre. Only where Mapbox has them, and only for Mapbox accounts it offers them to: if none ever show, yours doesn't have them"))
        SToggle(prefs, L.t("Lanes on the map"), "lanemap", true)
        if (prefs.b(PreferenceKeys.LANEMAP, true)) SPictureChoice(
            L.t("Lanes shown as"),
            when (prefs.s(PreferenceKeys.LANE_MAP_STYLE, "lit")) {
                "closeup" -> L.t("A drawn picture of the junction's lanes under the turn card for the last 400 m. Nothing drawn on the map")
                "sign" -> L.t("A sign over the road at the junction, with the lanes to use lit up")
                else -> L.t("The lane to use lit up on the road. On motorways, where the real lanes are known, the route line moves into it")
            },
            listOf("lit" to L.t("Lit lane"), "closeup" to L.t("Close-up picture"), "sign" to L.t("Sign")), prefs.s(PreferenceKeys.LANE_MAP_STYLE, "lit"), { id, c -> lanePicture(id, c) },
        ) { prefs.put(PreferenceKeys.LANE_MAP_STYLE, it) }
        SToggle(prefs, L.t("Zoom in on lanes before a junction"), "laneview", true)
        SToggle(prefs, L.t("Junction close-up"), "junctionview", true, L.t("At slip roads, motorway exits, forks and lane splits the view moves closer and a little flatter for the last 200 m"))
        SToggle(prefs, L.t("Flatten the map to 2D at turns"), "turn2d", true)
        SToggle(prefs, L.t("Tilt with speed"), "speedtilt", true, L.t("Flatter when slow, a longer view at speed"))
    }
    SGroup(L.t("Trip card")) {
        SToggle(prefs, L.t("Route bar on the trip card"), "routebar", true, L.t("Traffic, cameras, services and stops, with your progress"))
        SToggle(prefs, L.t("Next services card"), "servicescard", true)
        SToggle(prefs, L.t("Hazard radar"), "hazardradar", false, (if (mi) L.t("The next 2 miles") else L.t("The next 3 km")) + L.t(": bends, crossings, schools, bumps, cameras and checks"))
    }
    SGroup(L.t("Arriving")) {
        SToggle(prefs, L.t("Arrival moment"), "arrivalfx", true, L.t("The camera swoops to your destination and shows the nearest parking"))
        SToggle(prefs, L.t("Light up your destination"), PreferenceKeys.ARRIVAL_PLACE, true, L.t("On arrival the building glows, its entrance is marked and a dotted line shows the walk from the parking"))
        SToggle(prefs, L.t("Parking near your destination"), "parkprompt", true, (if (mi) L.t("About a mile") else L.t("About 1.5 km")) + L.t(" before you arrive, car parks close by with the walk from each"))
        SToggle(prefs, L.t("Route back"), "routeback", true, L.t("On the arrival card: one tap for the way back to where you started"))
    }
    SGroup(L.t("Other screens")) {
        SToggle(prefs, L.t("Next turn in the notification"), PreferenceKeys.TURN_NOTIF, true, L.t("The arrow, distance and road in Horizon Nav's notification while you navigate, for car launchers, watches and other screens that show navigation notifications"))
    }
}


/** What the app knows about clean air zones, with a button to download them again. */
@Composable
private fun ZonesItem() {
    val ctx = LocalContext.current
    val sc = rememberCoroutineScope()
    var st by remember { mutableStateOf(Zones.all(ctx).let { z -> if (z.isEmpty()) L.t("Not downloaded yet") else "${z.size} zones: " + z.joinToString(", ") { it.name }.take(140) }) }
    var busy by remember { mutableStateOf(false) }
    SItem(L.t("Clean air zones on this unit"), st) {
        SButton(if (busy) L.t("Updating…") else L.t("Update")) {
            if (!busy) { busy = true; sc.launch { withContext(Dispatchers.IO) { Zones.fetch(ctx) }; st = Zones.status; busy = false } }
        }
    }
}

// ------------------------------------------------------------------ Alerts

@Composable
private fun AlertsSection(prefs: Prefs, engine: NavEngine) {
    SGroup(L.t("Speed")) {
        SToggle(prefs, L.t("Warn when over the limit"), "speedwarn", true)
        if (prefs.b(PreferenceKeys.SPEEDWARN, true)) SChoice(L.t("Warn when over by"), listOf("0", "5", "10").map { it to it + if (engine.imperial) " mph" else " km/h" }, prefs.s(PreferenceKeys.TRAFFIC_WARNING, "5")) { prefs.put(PreferenceKeys.TRAFFIC_WARNING, it) }
        SToggle(prefs, L.t("Say when the limit changes"), "limitchange", true)
        if (prefs.b(PreferenceKeys.LIMITCHANGE, true)) SChoice(L.t("How"), listOf("both" to L.t("Chime and voice"), "voice" to L.t("Voice only"), "chime" to L.t("Chime only")),
            prefs.s(PreferenceKeys.LIMITCHANGE_HOW, "both")) { prefs.put(PreferenceKeys.LIMITCHANGE_HOW, it) }
        SToggle(prefs, L.t("Next speed limit ahead"), PreferenceKeys.NEXT_LIMIT, true, L.t("A small sign above the limit with the next one on your route and how far it is, from about half a mile away"))
        SToggle(prefs, L.t("Show estimated limits"), "limitest", false, L.t("Where no limit is mapped, show the usual one for that kind of road (greyed out). Off shows no sign instead of a guess"))
    }
    SGroup(L.t("Cameras")) {
        SToggle(prefs, L.t("Speed cameras"), "cameras", true, L.t("Spoken and on-screen warnings as you come up to one"))
        if (prefs.b(PreferenceKeys.CAMERA_WARNINGS, true)) SToggle(prefs, L.t("Off where they're banned"), "cambanobey", true, L.t("France, Germany and Switzerland don't allow camera warnings, so they switch off there by themselves"))
        SToggle(prefs, L.t("Average speed checks"), "avgzones", true, L.t("Shows your average against the limit"))
    }
    SGroup(L.t("Hazards"), footer = L.t("Speed bumps, schools, level crossings and fords come from OpenStreetMap, so some may be missing.")) {
        SToggle(prefs, L.t("Roadworks and incidents"), "incidents", true)
        SToggle(prefs, L.t("Wrong-way warning"), "wrongway", true, L.t("If you turn the wrong way into a one-way street"))
        SToggle(prefs, L.t("Speed bumps"), "bumps", true)
        SToggle(prefs, L.t("Sharp bends"), PreferenceKeys.BEND_WARN, true, L.t("Bends that need slowing for are striped yellow on the route, with a warning and a comfortable speed before each"))
        SToggle(prefs, L.t("Schools at school-run times"), "schools", true)
        SToggle(prefs, L.t("Level crossings and fords"), "railford", true, L.t("A warning sign and a spoken warning for level crossings and fords on your route"))
    }
    SGroup(L.t("Zones and reminders")) {
        SToggle(prefs, L.t("Clean Air Zones and ULEZ"), "zonewarn", true)
        SToggle(prefs, L.t("Break reminder after 2 hours"), "breakremind", true)
        SToggle(prefs, L.t("Weather advice"), "wxadvice", true, L.t("A quiet note in heavy rain, snow, fog or ice when you're near the limit"))
    }
    SGroup(L.t("Your reports"), footer = L.t("Make a report from the map options button. Reports stay on this unit and fade after a few hours.")) {
        SToggle(prefs, L.t("Warn about reports ahead"), "policealerts", true)
        SItem(L.t("Reports still active"), "${engine.reportsActive().size}") { SButton(L.t("Clear"), danger = true) { engine.clearReports() } }
    }
}

// ------------------------------------------------------------------ Sound & voice

@Composable
private fun SoundSection(prefs: Prefs, engine: NavEngine) {
    val ctx = LocalContext.current
    SGroup(L.t("Directions")) {
        // the language of the spoken directions and the turn card (the rest of the app is still English for now)
        SChoice(L.t("Language"), listOf("auto" to L.t("Phone's language")) + L.supported, prefs.s(PreferenceKeys.APP_LANG, "auto"),
            L.t("The whole app: directions, screens and Android Auto. Phone's language uses English when the phone's isn't one of these")) {
            prefs.put(PreferenceKeys.APP_LANG, it); engine.setLanguage(it)
        }
        // a language that isn't there yet: asked for on GitHub (an issue, filled in, sent from the browser)
        var langReq by remember { mutableStateOf(false) }
        SItem(L.t("Missing your language?"), L.t("Ask for it: the request goes to GitHub, where you can see when it's added")) {
            SButton(L.t("Request a language")) { langReq = true }
        }
        if (langReq) LanguageRequestDialog(engine) { langReq = false }
        SChoice(L.t("Spoken guidance"), listOf("0" to L.t("All"), "1" to L.t("Alerts only"), "2" to L.t("Off")), engine.soundMode.toString(),
            L.t("Alerts only skips turn-by-turn but still speaks cameras, limits and hazards")) { prefs.put(PreferenceKeys.SOUND_MODE, it.toInt()) }
        SToggle(prefs, L.t("Say road names"), PreferenceKeys.VOICE_ROADS, true, L.t("Off gives just the direction: \"In 200 metres, turn left\", without the road or the places on the signs"))
        SToggle(prefs, L.t("Smarter directions"), "smartvoice", true, L.t("Junction numbers and sign destinations, the next move when it comes straight after, and fewer early prompts on roads you drive a lot"))
    }
    SGroup(L.t("Voice and volume")) {
        SChoice(L.t("Voice channel"), listOf("nav" to L.t("Navigation"), "media" to L.t("Media")), prefs.s(PreferenceKeys.VOICE_CHANNEL, "nav"),
            L.t("If you hear the beeps but not the spoken directions, choose Media: some head units don't play the navigation channel")) { prefs.put(PreferenceKeys.VOICE_CHANNEL, it) }
        SItem(L.t("Voice and language"), L.t("Uses the system text-to-speech voice")) { SButton(L.t("Open")) { openSystemSettings(ctx, "com.android.settings.TTS_SETTINGS") } }
        SItem(L.t("Volume")) { SButton(L.t("Open")) { openSystemSettings(ctx, Settings.ACTION_SOUND_SETTINGS) } }
        SSlider(L.t("Chime volume"), prefs.i(PreferenceKeys.CHIME_VOL, 70).coerceIn(5, 100).toFloat(), 5f..100f, "%",
            sub = L.t("The beeps for speeding, limit changes and cameras, compared with the voice. Your head unit's volume, turned while it speaks, changes both"),
            reset = 70f) { prefs.put(PreferenceKeys.CHIME_VOL, it.toInt()); engine.testChime() }
    }
}


// ------------------------------------------------------------------ Places & trips

@Composable
private fun PlaceItem(label: String, p: Place?, onChange: () -> Unit, onClear: () -> Unit) {
    SItem(label, p?.let { it.name + (if (it.sub.isNotBlank()) ", " + it.sub else "") } ?: L.t("Not set")) {
        SButton(if (p != null) L.t("Change") else L.t("Set"), primary = true, onClick = onChange)
        if (p != null) SButton(L.t("Clear"), danger = true, onClick = onClear)
    }
}

@Composable
private fun PlacesSection(engine: NavEngine) {
    val pl = engine.places
    var settingSlot by remember { mutableStateOf<String?>(null) }
    // (read again whenever the saved places change, so a change shows straight away)
    val slotsRaw = engine.prefs.s(PreferenceKeys.PLACE_HOME, "") + engine.prefs.s("place_work", "")
    SGroup(L.t("Saved places"), footer = L.t("Home and Work can also be set from the pills under the search bar, or the house or briefcase on the route options.")) {
        PlaceItem(L.t("Home"), remember(slotsRaw) { pl.slot("home") }, { settingSlot = "home" }) { pl.clearSlot("home") }
        PlaceItem(L.t("Work"), remember(slotsRaw) { pl.slot("work") }, { settingSlot = "work" }) { pl.clearSlot("work") }
    }
    settingSlot?.let { s -> AddPlaceDialog(engine, null, slot = s) { settingSlot = null } }
    // your own labelled places: rename, reorder or remove
    val raw = engine.prefs.s(PreferenceKeys.PLACES_CUSTOM, "")
    val mine = remember(raw) { pl.customs() }
    var renaming by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    SGroup(L.t("Your places"), footer = L.t("Shown as pills under the search bar, after Home and Work. Add one with the + pill there.")) {
        if (mine.isEmpty()) SNote(L.t("No places of your own yet."))
        mine.forEachIndexed { i, lp ->
            if (renaming == lp.label) {
                var t by remember(lp.label) { mutableStateOf(lp.label) }
                SItem(L.t("Rename {0}", lp.label)) {
                    SField(t, { t = it.take(24) }, width = 220.dp)
                    SButton(L.t("Save"), primary = true) { pl.renameCustom(lp.label, t); renaming = null }
                }
            } else SItem(lp.label, lp.place.name + if (lp.place.sub.isNotBlank()) ", " + lp.place.sub else "") {
                if (i > 0) SButton("↑") { pl.moveCustom(lp.label, -1) }
                SButton(L.t("Rename")) { renaming = lp.label }
                SButton(L.t("Remove"), danger = true) { pl.removeCustom(lp.label) }
            }
        }
        SItem(L.t("Add a place")) { SButton(L.t("Add"), primary = true) { adding = true } }
    }
    if (adding) AddPlaceDialog(engine, null) { adding = false }
    val favRaw = engine.prefs.s(PreferenceKeys.PLACES_FAVOURITES, "")
    val favs = remember(favRaw) { pl.favourites() }
    var renamingFav by remember { mutableStateOf<Place?>(null) }
    var slotFor by remember { mutableStateOf<Place?>(null) }
    SGroup(L.t("Favourites")) {
        if (favs.isEmpty()) SNote(L.t("No favourites yet. Tap the star on a destination to add one."))
        favs.forEach { f ->
            when {
                renamingFav == f -> {
                    var t by remember(f) { mutableStateOf(f.name) }
                    SItem(L.t("Rename {0}", f.name)) {
                        SField(t, { t = it.take(40) }, width = 220.dp)
                        SButton(L.t("Save"), primary = true) { pl.renameFavourite(f, t); renamingFav = null }
                    }
                }
                slotFor == f -> SItem(L.t("Make {0}…", f.name)) {
                    SButton(L.t("Home"), primary = true) { pl.setSlot("home", f); slotFor = null; engine.toast(L.t("{0} is now Home", f.name), 2500) }
                    SButton(L.t("Work"), primary = true) { pl.setSlot("work", f); slotFor = null; engine.toast(L.t("{0} is now Work", f.name), 2500) }
                    SButton(L.t("Cancel")) { slotFor = null }
                }
                else -> SItem(f.name, f.sub) {
                    SButton(L.t("Rename")) { renamingFav = f }
                    SButton(L.t("Make Home / Work")) { slotFor = f }
                    SButton(L.t("Remove"), danger = true) { pl.toggleFavourite(f) }
                }
            }
        }
        if (favs.size > 1) SItem(L.t("Remove every favourite")) { SButton(L.t("Remove all"), danger = true) { pl.clearFavourites() } }
    }
    ShortcutsGroup(engine)
    SGroup(L.t("History"), footer = L.t("Swipe a place sideways on the Saved & recent list to take it off.")) {
        SToggle(engine.prefs, L.t("Saved & recent under the search bar"), "quicklist", true, L.t("The list of favourites and recent places on the map"))
        SItem(L.t("Recent destinations"), L.t("{0} recent, plus places remembered for searching without signal", pl.recents().size)) {
            SButton(L.t("Clear"), danger = true) { pl.clearHistory(); engine.toast(L.t("History cleared"), 2000) }
        }
    }
    TripsGroup(engine)
}

@Composable
private fun TripsGroup(engine: NavEngine) {
    var shown by remember { mutableStateOf(engine.trips.all()) }
    val fmt = com.headunit.launcher.Clock.format("EEE d MMM, ")
    val k = if (engine.imperial) 2.23694 else 3.6
    val unit = if (engine.imperial) "mph" else "km/h"
    SGroup(L.t("Trip log")) {
        if (shown.isEmpty()) SNote(L.t("No trips yet. Every drive is recorded here when you park."))
        else SItem("${shown.size} trips", Geo.fmtDist(shown.sumOf { it.dist }, engine.imperial) + L.t(" in all")) { SButton(L.t("Clear"), danger = true) { engine.trips.clear(); shown = emptyList() } }
        shown.take(50).forEach { t ->
            val mins = ((t.end - t.start) / 60000L).toInt()
            SItem(
                fmt.format(java.util.Date(t.start)) + (if (t.to.isNotBlank()) " → ${t.to}" else ""),
                L.t("{0} · moving {1} · top {2} {3}", Geo.fmtDur(mins * 60.0), Geo.fmtDur(t.moving.toDouble()), Math.round(t.maxSpeed * k), unit),
            ) { Text(Geo.fmtDist(t.dist, engine.imperial), color = LocalVw.current.text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}


/** Share your ETA and places sent from other apps (part of Places & trips). */
/** Your own normal zoom when not on a route: a slider from further out to closer, and Reset. */
@Composable
private fun DefaultZoomRow(prefs: Prefs) {
    val c = LocalVw.current
    val saved = (prefs.s(PreferenceKeys.DEFAULT_ZOOM, "0").toFloatOrNull() ?: 0f).coerceIn(-2f, 2f)
    var v by remember(saved) { mutableStateOf(saved) }
    fun save(x: Float) = prefs.put(PreferenceKeys.DEFAULT_ZOOM, String.format(java.util.Locale.US, "%.2f", x))
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        SItem(L.t("Default zoom"), L.t("When you're not on a route. Auto zoom still pulls out at speed and moves in at junctions, round your setting. Or zoom with + and − on the map and tap \"Make this my default\"")) {
            if (kotlin.math.abs(v) > 0.01f) SButton(L.t("Reset")) { v = 0f; save(0f) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(L.t("Further out"), color = c.sub, fontSize = 13.sp)
            androidx.compose.material3.Slider(value = v, onValueChange = { v = (Math.round(it * 4f) / 4f) }, onValueChangeFinished = { save(v) },
                valueRange = -2f..2f, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            Text(L.t("Closer"), color = c.sub, fontSize = 13.sp)
        }
    }
}

/**
 * Sending places from the phone to the head unit (through a free relay, encrypted with the pairing code). The head unit makes the
 * code; you type it on the phone.
 */
@Composable
private fun PhoneLinkGroup(prefs: Prefs) {
    val code = prefs.s(PreferenceKeys.LINK_CODE, "")
    val role = prefs.s(PreferenceKeys.LINK_ROLE, "")
    var typing by remember { mutableStateOf(false) }
    SGroup(L.t("Send places from your phone"), footer = L.t("Pair once: on the head unit, tap Pair a phone and type its code into Horizon Nav on your phone (in this same place). Then \"Send to car\" on the phone's route screen sends the place to the head unit, which starts it the next time it's on (within 12 hours). Places go through ntfy.sh, a free message relay, scrambled with your code so only your phone and car can read them.")) {
        when {
            code.isNotBlank() && role == "car" -> {
                SItem(L.t("This is the car"), L.t("Paired. Code {0}: type it on another phone to add it too", com.headunit.launcher.CarLink.pretty(code))) {
                    SButton(L.t("Unpair"), danger = true) { prefs.put(PreferenceKeys.LINK_CODE, ""); prefs.put(PreferenceKeys.LINK_ROLE, ""); prefs.put(PreferenceKeys.LINK_LAST, "") }
                }
                val qr = remember(code) { donateQr(com.headunit.launcher.CarLink.pretty(code), 300) }
                if (qr != null) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(110.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(6.dp)) {
                        androidx.compose.foundation.Image(qr, L.t("The pairing code as a QR code"), Modifier.size(98.dp), filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
                    }
                    Text(com.headunit.launcher.CarLink.pretty(code), color = LocalVw.current.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
            code.isNotBlank() && role == "phone" -> SItem(L.t("This is your phone"), L.t("Paired with your car ({0}). \"Send to car\" is on the route screen, next to Save", com.headunit.launcher.CarLink.pretty(code))) {
                SButton(L.t("Unpair"), danger = true) { prefs.put(PreferenceKeys.LINK_CODE, ""); prefs.put(PreferenceKeys.LINK_ROLE, "") }
            }
            else -> {
                SItem(L.t("On the head unit"), L.t("Makes a code to type on your phone")) {
                    SButton(L.t("Pair a phone"), primary = true) { prefs.put(PreferenceKeys.LINK_CODE, com.headunit.launcher.CarLink.newCode()); prefs.put(PreferenceKeys.LINK_ROLE, "car"); prefs.put(PreferenceKeys.LINK_LAST, "") }
                }
                SItem(L.t("On your phone"), L.t("Type the code the head unit shows")) { SButton(L.t("Enter code")) { typing = true } }
                if (typing) SFieldItem(L.t("Car's code"), "", L.t("Like K7P-4QX-M2D"), placeholder = L.t("K7P-4QX-M2D")) { t ->
                    val ok = com.headunit.launcher.CarLink.clean(t)
                    if (ok != null) { prefs.put(PreferenceKeys.LINK_CODE, ok); prefs.put(PreferenceKeys.LINK_ROLE, "phone"); typing = false }
                }
            }
        }
    }
}

@Composable
private fun SharingGroups(prefs: Prefs) {
    val ctx = LocalContext.current
    val pkg = android.net.Uri.parse("package:" + ctx.packageName)
    SGroup(L.t("Share your ETA"), footer = L.t("The share button on the trip card texts your arrival time and a map link. On a head unit without a SIM it opens your messaging app instead.")) {
        val pickEta = rememberEtaContactPicker(prefs)
        SItem(L.t("Send it to"), prefs.s(PreferenceKeys.ETA_NAME, "").ifBlank { L.t("No one chosen yet") }) {
            SButton(if (prefs.s(PreferenceKeys.ETA_NUMBER, "").isBlank()) L.t("Choose") else L.t("Change")) { pickEta() }
            if (prefs.s(PreferenceKeys.ETA_NUMBER, "").isNotBlank()) SButton(L.t("Clear"), danger = true) { prefs.put(PreferenceKeys.ETA_NUMBER, ""); prefs.put(PreferenceKeys.ETA_NAME, "") }
        }
    }
    SGroup(L.t("Places from other apps"), footer = L.t("Share a place to Horizon Nav from Google Maps, WhatsApp, Contacts or a web page and it plans the route. To open Google Maps links here straight away, tap Open by default and add the links.")) {
        SItem(L.t("Google Maps links")) {
            SButton(L.t("Open by default")) {
                if (android.os.Build.VERSION.SDK_INT >= 31) openSystemSettings(ctx, Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg)
                else openSystemSettings(ctx, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            }
        }
    }
}

// ------------------------------------------------------------------ Fuel & charging

@Composable
private fun FuelSection(prefs: Prefs, engine: NavEngine) {
    // (kept in the same units inside whichever are shown, miles per kWh and UK miles per gallon, so saved figures don't change)
    val mi = engine.imperial
    fun num(t: String) = t.filter { it.isDigit() || it == '.' }
    fun fmt1(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v)
    val ctx = LocalContext.current
    val sc = rememberCoroutineScope()
    SGroup(L.t("Fuel prices"), footer = L.t("Live prices come from the government's Fuel Finder scheme. It's free: on gov.uk search \"Fuel Finder\", register as an information recipient with a GOV.UK One Login, then paste the client ID and secret here. Without them the fuel list still works, just without prices.")) {
        SChoice(L.t("Your fuel"), listOf("E10" to L.t("Unleaded"), "E5" to L.t("Super"), "B7" to L.t("Diesel"), "E85" to L.t("Bioethanol (E85)"), "EV" to L.t("Electric")), prefs.s(PreferenceKeys.FUEL_TYPE, "E10")) { prefs.put(PreferenceKeys.FUEL_TYPE, it) }
        SToggle(prefs, L.t("Suggest a fuel stop on long trips"), "smartfuel", true, if (prefs.s(PreferenceKeys.FUEL_TYPE, "E10") == "EV") (if (mi) L.t("Over about 95 miles") else L.t("Over about 150 km")) + L.t(", a charging point along the way with the shortest detour") else (if (mi) L.t("Over about 95 miles") else L.t("Over about 150 km")) + L.t(", the best-value station along the way"))
        SFieldItem(L.t("Client ID"), prefs.s(PreferenceKeys.FFID, "")) { prefs.put(PreferenceKeys.FFID, it.trim()) }
        SFieldItem(L.t("Client secret"), prefs.s(PreferenceKeys.FFSECRET, ""), password = true) { prefs.put(PreferenceKeys.FFSECRET, it.trim()) }
        var st by remember { mutableStateOf(FuelFinder.status) }
        var busy by remember { mutableStateOf(false) }
        SItem(L.t("Prices"), st.ifBlank { if (FuelFinder.configured(prefs)) L.t("Not downloaded yet") else L.t("Add your details first") }) {
            SButton(if (busy) L.t("Updating…") else L.t("Update now"), primary = true) {
                if (!busy && FuelFinder.configured(prefs)) {
                    busy = true; st = L.t("Downloading stations and prices (the first time takes a minute)…")
                    sc.launch {
                        try { withContext(Dispatchers.IO) { FuelFinder.refresh(ctx, prefs, force = true) } } catch (e: Exception) { }
                        st = FuelFinder.status; busy = false
                    }
                }
            }
        }
        SItem(L.t("Spotted a wrong price?")) { SButton(L.t("Report it")) { openSystemSettings(ctx, android.content.Intent.ACTION_VIEW, android.net.Uri.parse(FuelFinder.REPORT_URL)) } }
    }
    SGroup(L.t("Fuel prices outside the UK"), footer = L.t("France and Austria work by themselves, from their governments' own price services. Germany needs a free key from creativecommons.tankerkoenig.de (Tankerkönig). Australia (NSW and Tasmania) needs a free API key and secret from api.nsw.gov.au (Fuel API). Prices show in cents a litre, e.g. 189.9c. Elsewhere the fuel list still works, without prices.")) {
        SFieldItem(L.t("Tankerkönig key (Germany)"), prefs.s(PreferenceKeys.TANKERKOENIG_KEY, ""), placeholder = L.t("Paste the key"), password = true) { prefs.put(PreferenceKeys.TANKERKOENIG_KEY, it.trim()) }
        SFieldItem(L.t("FuelCheck API key (Australia)"), prefs.s(PreferenceKeys.NSW_FUEL_KEY, ""), sub = L.t("New South Wales and Tasmania"), placeholder = L.t("Paste the API key"), password = true) { prefs.put(PreferenceKeys.NSW_FUEL_KEY, it.trim()) }
        SFieldItem(L.t("FuelCheck API secret"), prefs.s(PreferenceKeys.NSW_FUEL_SECRET, ""), placeholder = L.t("Paste the API secret"), password = true) { prefs.put(PreferenceKeys.NSW_FUEL_SECRET, it.trim()) }
    }
    if (prefs.s(PreferenceKeys.FUEL_TYPE, "E10") == "EV") SGroup(L.t("Your electric car"), footer = L.t("With the efficiency and price filled in, each route shows roughly what it will cost to charge for. The efficiency and battery size also give the battery estimate on the trip card and the charging stop suggestions (set your battery level on the route options).")) {
        SToggle(prefs, L.t("Battery estimate on the trip card"), PreferenceKeys.EV_PCT_CARD, true, L.t("Roughly how much battery you'll arrive with, on the phone and Android Auto. It's worked out from your settings, not read from the car"))
        SToggle(prefs, L.t("Show petrol stations too"), PreferenceKeys.EV_SHOW_FUEL, false, L.t("The Fuel button on the map page (on the phone and Android Auto). Off: Charging takes its place"))
        if (mi) SFieldItem(L.t("Efficiency (miles per kWh)"), prefs.s(PreferenceKeys.EVMPK, ""), placeholder = L.t("e.g. 3.5"), number = true) { s -> prefs.put(PreferenceKeys.EVMPK, num(s).take(5)) }
        else SFieldItem(L.t("Efficiency (km per kWh)"), prefs.s(PreferenceKeys.EVMPK, "").toDoubleOrNull()?.let { fmt1(it * 1.609344) } ?: "", placeholder = L.t("e.g. 5.6"), number = true) { s ->
            prefs.put(PreferenceKeys.EVMPK, num(s).toDoubleOrNull()?.takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "%.3f", it / 1.609344) } ?: "")
        }
        SFieldItem(L.t("Usable battery (kWh)"), prefs.s(PreferenceKeys.EV_BATTERY_KWH, ""), L.t("For the battery estimate and charging stops (60 if left blank)"), placeholder = L.t("e.g. 64"), number = true) { s -> prefs.put(PreferenceKeys.EV_BATTERY_KWH, s.filter { it.isDigit() || it == '.' }.take(5)) }
        SFieldItem(if (mi) L.t("Electricity price (pence per kWh)") else L.t("Electricity price (cents per kWh)"), prefs.s(PreferenceKeys.EVPPK, ""), placeholder = L.t("e.g. 28"), number = true) { s -> prefs.put(PreferenceKeys.EVPPK, s.filter { it.isDigit() || it == '.' }.take(6)) }
    } else SGroup(L.t("Fuel cost on the route options"), footer = L.t("With both filled in, each route shows roughly what it will cost in fuel.")) {
        if (mi) SFieldItem(L.t("Fuel economy (miles per gallon)"), prefs.s(PreferenceKeys.FUELMPG, ""), placeholder = L.t("e.g. 45"), number = true) { s -> prefs.put(PreferenceKeys.FUELMPG, num(s).take(5)) }
        // litres per 100 km: 282.48 / UK miles per gallon
        else SFieldItem(L.t("Fuel use (litres per 100 km)"), prefs.s(PreferenceKeys.FUELMPG, "").toDoubleOrNull()?.takeIf { it > 0 }?.let { fmt1(282.481 / it) } ?: "", placeholder = L.t("e.g. 6.3"), number = true) { s ->
            prefs.put(PreferenceKeys.FUELMPG, num(s).toDoubleOrNull()?.takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "%.2f", 282.481 / it) } ?: "")
        }
        SFieldItem(if (mi) L.t("Fuel price (pence per litre)") else L.t("Fuel price (cents per litre)"), prefs.s(PreferenceKeys.FUELPPL, ""), placeholder = L.t("e.g. 139.9"), number = true) { s -> prefs.put(PreferenceKeys.FUELPPL, s.filter { it.isDigit() || it == '.' }.take(6)) }
    }
}


/** Town and village names for your country, so search works with no signal even without the full pack. */
@Composable
private fun OfflinePlacesGroup(engine: NavEngine) {
    val ctx = LocalContext.current
    val sc = rememberCoroutineScope()
    // the country of the chosen pack, else the one you're in
    val cc = engine.chosenPack().country.ifBlank { engine.detectedCountry }.ifBlank { "GB" }
    val where = countryName(cc)
    var state by remember { mutableStateOf(if (OfflinePlaces.installed(ctx)) L.t("Installed (%.1f MB)").format(OfflinePlaces.sizeMb(ctx)) else L.t("Not downloaded")) }
    var busy by remember { mutableStateOf(false) }
    SGroup(L.t("Place names"), footer = L.t("A small download of towns and villages in {0}, so you can search for them without signal even without the full pack.", where)) {
        SItem(L.t("Towns and villages"), state) {
            SButton(if (busy) L.t("Downloading…") else if (OfflinePlaces.installed(ctx)) L.t("Update") else L.t("Download"), primary = !OfflinePlaces.installed(ctx)) {
                if (!busy) {
                    busy = true; state = L.t("Downloading (needs signal)…")
                    sc.launch {
                        try {
                            val n = withContext(Dispatchers.IO) { OfflinePlaces.download(ctx, cc) }
                            state = L.t("Installed: {0} places", n); engine.toast(L.t("Offline places ready"), 3000)
                        } catch (e: Exception) { state = L.t("Failed: {0}", e.message) } finally { busy = false }
                    }
                }
            }
            if (OfflinePlaces.installed(ctx) && !busy) SButton(L.t("Remove"), danger = true) { OfflinePlaces.delete(ctx); state = L.t("Not downloaded") }
        }
    }
}

// ------------------------------------------------------------------ Screen & sizes

@Composable
private fun ScreenSection(prefs: Prefs, engine: NavEngine) {
    SGroup(L.t("Screen")) {
        SToggle(prefs, L.t("Full screen"), PreferenceKeys.FULLSCREEN, true, L.t("Hides the status and navigation bars. Turn off if your head unit has its own bar (climate, home button) that the map covers"))
        SSlider(L.t("Brightness"), prefs.i(PreferenceKeys.BRIGHT, 100).toFloat(), 10f..100f, "%", sub = L.t("100% leaves it to the system"), live = true) { prefs.put(PreferenceKeys.BRIGHT, it) }
        SToggle(prefs, L.t("Dim the screen at night"), "dimnight", false)
        SToggle(prefs, L.t("Keep the screen on"), "keepon", true)
        SToggle(prefs, L.t("Glass panels"), "glass", true, L.t("See-through cards tinted by the map. Off gives solid cards"))
        SChoice(L.t("Mapbox logo position"), listOf("tl" to L.t("Top left"), "tr" to L.t("Top right"), "bl" to L.t("Bottom left"), "br" to L.t("Bottom right")), prefs.s(PreferenceKeys.MAPBOX_LOGO, "tr"),
            L.t("Mapbox requires their logo on the map; pick the corner that suits your layout")) { prefs.put(PreferenceKeys.MAPBOX_LOGO, it) }
    }
    SGroup(L.t("Sizes")) {
        val screenConfig = androidx.compose.ui.platform.LocalConfiguration.current
        val autoPercent = recommendedMainUiScale(screenConfig.screenWidthDp, screenConfig.screenHeightDp)
        val autoScale = prefs.b(PreferenceKeys.UI_AUTO_SCALE, prefs.s(PreferenceKeys.UI_SCALE, "").isBlank())
        SItem(
            L.t("Automatically scale UI"),
            if (autoScale) L.t("Uses screen size and shape (currently {0}%)", autoPercent) else L.t("Manual text and button size is enabled")
        ) {
            Switch(checked = autoScale, onCheckedChange = { enabled ->
                if (!enabled) prefs.put(PreferenceKeys.UI_SCALE, autoPercent)
                prefs.put(PreferenceKeys.UI_AUTO_SCALE, enabled)
            })
        }
        SSlider(
            L.t("Text and button size"),
            (if (autoScale) autoPercent else prefs.i(PreferenceKeys.UI_SCALE, autoPercent)).toFloat(),
            60f..140f, "%", sub = if (autoScale) L.t("Move the slider to switch to a manual size") else L.t("Adjust manually, or turn automatic scaling back on"), steps = 15, reset = 100f
        ) {
            prefs.put(PreferenceKeys.UI_AUTO_SCALE, false)
            prefs.put(PreferenceKeys.UI_SCALE, it)
        }
        var mainAdvanced by rememberSaveable { mutableStateOf(false) }
        SItem(if (mainAdvanced) L.t("Advanced display sizes") else L.t("Advanced display sizes"), L.t("Adjust individual elements without changing the overall scale")) {
            SButton(if (mainAdvanced) L.t("Hide") else L.t("Show")) { mainAdvanced = !mainAdvanced }
        }
        if (mainAdvanced) {
            SSlider(L.t("Card size"), prefs.i(PreferenceKeys.MAIN_CARD_SIZE, 100).toFloat(), 70f..150f, "%", sub = L.t("Turn, trip and route cards"), steps = 15, reset = 100f) { prefs.put(PreferenceKeys.MAIN_CARD_SIZE, it) }
            SSlider(L.t("Button size"), prefs.i(PreferenceKeys.MAIN_BUTTON_SIZE, 100).toFloat(), 70f..150f, "%", sub = L.t("On-screen controls"), steps = 15, reset = 100f) { prefs.put(PreferenceKeys.MAIN_BUTTON_SIZE, it) }
            SSlider(L.t("Text size"), prefs.i(PreferenceKeys.MAIN_TEXT_SIZE, 100).toFloat(), 70f..150f, "%", sub = L.t("Independent text scaling"), steps = 15, reset = 100f) { prefs.put(PreferenceKeys.MAIN_TEXT_SIZE, it) }
            SSlider(L.t("Navigation arrow size"), prefs.i(PreferenceKeys.MAIN_ARROW_SIZE, 100).toFloat(), 60f..180f, "%", sub = L.t("Map position arrow"), steps = 11, reset = 100f) { prefs.put(PreferenceKeys.MAIN_ARROW_SIZE, it) }
            SSlider(L.t("Road name size"), prefs.i(PreferenceKeys.ROAD_NAME_SIZE, 100).toFloat(), 70f..200f, "%", sub = L.t("The name of the road you're on, under your arrow"), steps = 12, reset = 100f) { prefs.put(PreferenceKeys.ROAD_NAME_SIZE, it) }
        }

    }
    SGroup(L.t("Speed display")) {
        SToggle(prefs, L.t("Your speed next to the limit"), "mapspeed", true)
        SToggle(prefs, L.t("Speedometer ring"), "speedring", true, L.t("Fills towards the limit, turning amber then red"))
        SSlider(L.t("Speed limit and speedometer size"), prefs.i(PreferenceKeys.SPEED_SIZE, 100).toFloat(), 50f..200f, "%", sub = L.t("On this screen (Android Auto has its own size, under Android Auto)"), steps = 29, reset = 100f) { prefs.put(PreferenceKeys.SPEED_SIZE, it) }
        SPictureChoice(L.t("Speed position"), L.t("On the side away from the cards, so it never covers them. Move it up if your hand covers it"),
            listOf("bottom" to L.t("Bottom"), "middle" to L.t("Middle"), "top" to L.t("Top")), prefs.s(PreferenceKeys.SPEED_POS, "bottom"), { id, c -> speedPicture(id, c) },
        ) { prefs.put(PreferenceKeys.SPEED_POS, it) }
    }
    SGroup(L.t("Card positions"), L.t("Where the turn card, trip card and speedometer sit on a wide screen (portrait screens keep the cards across the top and bottom)")) {
        CardPositionsPreview(prefs, car = false)
        SChoice(L.t("Turn card"), listOf("auto" to L.t("By steering wheel"), "left" to L.t("Top left"), "right" to L.t("Top right")), prefs.s(PreferenceKeys.CARDS_SIDE, "auto")) { prefs.put(PreferenceKeys.CARDS_SIDE, it) }
        SChoice(L.t("Trip card"), listOf("auto" to L.t("Under the turn card"), "left" to L.t("Bottom left"), "centre" to L.t("Bottom centre"), "right" to L.t("Bottom right")), prefs.s(PreferenceKeys.TRIP_POS, "auto")) { prefs.put(PreferenceKeys.TRIP_POS, it) }
        SChoice(L.t("Speedometer"), listOf("auto" to L.t("Away from the cards"), "left" to L.t("Bottom left"), "right" to L.t("Bottom right")), prefs.s(PreferenceKeys.SPEED_SIDE, "auto")) { prefs.put(PreferenceKeys.SPEED_SIDE, it) }
    }
    SGroup(L.t("While driving")) {
        SToggle(prefs, L.t("Road and heading when driving without a route"), "cruise", true)
        SToggle(prefs, L.t("Keep the arrow on the road with no route"), "snaproad", true)
        SToggle(prefs, L.t("Keep the arrow to the side"), PreferenceKeys.ARROW_SIDE, false, L.t("When the cards fold away, instead of moving to the middle"))
        SToggle(prefs, L.t("Dim the map in tunnels"), "tunneldim", true)
        SToggle(prefs, L.t("Fewer buttons while driving"), "drivesimple", true)
        SToggle(prefs, L.t("Lock typing while driving"), "drivelock", true, L.t("Voice search still works"))
    }
    SGroup(L.t("Units")) {
        SChoice(L.t("Distance and speed"), listOf("mi" to L.t("Miles"), "km" to L.t("Kilometres")), if (engine.imperial) "mi" else "km") { prefs.put(PreferenceKeys.DISTANCE_UNIT, it) }
        SChoice(L.t("Clock"), listOf("auto" to L.t("Automatic"), "24" to "24-hour", "12" to "12-hour"), prefs.s(PreferenceKeys.CLOCK, "auto"),
            L.t("Automatic follows the phone's or head unit's own 12 / 24-hour setting")) { prefs.put(PreferenceKeys.CLOCK, it) }
        SChoice(L.t("Temperature"), listOf("C" to "°C", "F" to "°F"), if (isImperial(prefs)) "F" else "C") { prefs.put(PreferenceKeys.TEMPERATURE_UNIT, it) }
        SChoice(L.t("Driving side"), listOf("" to L.t("Automatic"), "rhd" to L.t("Right-hand drive"), "lhd" to L.t("Left-hand drive")), prefs.s(PreferenceKeys.DRIVE, ""),
            when (prefs.s(PreferenceKeys.DRIVE, "")) {
                "rhd" -> L.t("Driver on the right (UK): cards on the left, buttons on the right")
                "lhd" -> L.t("Driver on the left (US, Europe): the screen is mirrored, cards on the right")
                else -> L.t("Follows the country you're in") + (if (engine.mirrored) L.t(": left-hand drive, screen mirrored") else L.t(": right-hand drive"))
            }) { prefs.put(PreferenceKeys.DRIVE, it) }
    }
}

// ------------------------------------------------------------------ Android Auto

@Composable
private fun AndroidAutoSection(prefs: Prefs) {
    val ctx = LocalContext.current
    // Android Auto only lists apps that weren't installed from Play when its own "Unknown sources" is on, and some phones hide them
    // anyway: the steps that get it showing, with shortcuts
    val gh = "com.google.android.projection.gearhead"
    val ghUri = android.net.Uri.parse("package:$gh")
    val hasAa = remember { try { ctx.packageManager.getPackageInfo(gh, 0); true } catch (e: Exception) { false } }
    val source = remember {
        try {
            val inst = if (android.os.Build.VERSION.SDK_INT >= 30) ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName
                else @Suppress("DEPRECATION") ctx.packageManager.getInstallerPackageName(ctx.packageName)
            when (inst) {
                "com.android.vending" -> L.t("Google Play (Android Auto lists it without any extra steps)")
                null -> L.t("Unknown (installed from a file): needs Unknown sources in Android Auto")
                ctx.packageName -> L.t("Horizon Nav's own updater: needs Unknown sources in Android Auto")
                else -> L.t("{0}: needs Unknown sources in Android Auto", inst)
            }
        } catch (e: Exception) { L.t("Unknown") }
    }
    CarTripButtonsGroup(prefs)
    SGroup(L.t("Card positions on the car screen"), L.t("The turn card stays top left on Android Auto, where its own buttons leave room")) {
        CardPositionsPreview(prefs, car = true)
        SChoice(L.t("Trip card"), listOf("auto" to L.t("Under the turn card"), "centre" to L.t("Bottom centre"), "right" to L.t("Bottom right")), prefs.s(PreferenceKeys.CAR_TRIP_POS, "auto")) { prefs.put(PreferenceKeys.CAR_TRIP_POS, it) }
        SChoice(L.t("Speedometer"), listOf("auto" to L.t("Away from the cards"), "left" to L.t("Bottom left"), "right" to L.t("Bottom right")), prefs.s(PreferenceKeys.CAR_SPEED_SIDE, "auto")) { prefs.put(PreferenceKeys.CAR_SPEED_SIDE, it) }
    }
    SGroup(L.t("Sizes on the car screen")) {
        SSlider(L.t("Overall size on the car screen"), prefs.i(PreferenceKeys.CARSCALE, 100).toFloat(), 40f..300f, "%", sub = L.t("Base scale for the whole overlay"), steps = 25, reset = 100f) { prefs.put(PreferenceKeys.CARSCALE, it) }
        SSlider(L.t("Speedometer and speed-limit sign size"), prefs.i(PreferenceKeys.CAR_SPEED_SIZE, 100).toFloat(), 40f..180f, "%", sub = L.t("Automatically shrinks further if the available screen area is too small"), reset = 100f) { prefs.put(PreferenceKeys.CAR_SPEED_SIZE, it) }
        SChoice(L.t("Speed position on the car screen"), listOf("bottom" to L.t("Bottom"), "middle" to L.t("Middle"), "top" to L.t("Top")), prefs.s(PreferenceKeys.SPEED_POS_CAR, "bottom"),
            L.t("On the side away from the cards. Separate from this screen's own setting")) { prefs.put(PreferenceKeys.SPEED_POS_CAR, it) }
        var carAdvanced by rememberSaveable { mutableStateOf(false) }
        SItem(L.t("Advanced car-screen sizes"), L.t("Fine-tune individual elements independently")) {
            SButton(if (carAdvanced) L.t("Hide") else L.t("Show")) { carAdvanced = !carAdvanced }
        }
        if (carAdvanced) {
            SSlider(L.t("Card size"), prefs.i(PreferenceKeys.CAR_CARD_SIZE, 100).toFloat(), 70f..150f, "%", sub = L.t("Turn, trip and route cards"), steps = 16, reset = 100f) { prefs.put(PreferenceKeys.CAR_CARD_SIZE, it) }
            SSlider(L.t("Button size"), prefs.i(PreferenceKeys.CAR_BUTTON_SIZE, 100).toFloat(), 70f..150f, "%", sub = L.t("Round controls and tappable buttons"), steps = 16, reset = 100f) { prefs.put(PreferenceKeys.CAR_BUTTON_SIZE, it) }
            SSlider(L.t("Text size"), prefs.i(PreferenceKeys.CAR_TEXT_SIZE, 100).toFloat(), 70f..150f, "%", sub = L.t("Independent of card and button sizing"), steps = 16, reset = 100f) { prefs.put(PreferenceKeys.CAR_TEXT_SIZE, it) }
            SSlider(L.t("Navigation arrow size"), prefs.i(PreferenceKeys.CAR_ARROW_SIZE, 100).toFloat(), 30f..180f, "%", reset = 100f) { prefs.put(PreferenceKeys.CAR_ARROW_SIZE, it) }
            SSlider(L.t("Road name size"), prefs.i(PreferenceKeys.CAR_ROAD_NAME_SIZE, 100).toFloat(), 70f..200f, "%", sub = L.t("The name of the road you're on, under your arrow"), steps = 12, reset = 100f) { prefs.put(PreferenceKeys.CAR_ROAD_NAME_SIZE, it) }
        }
    }
    SGroup(L.t("Setting it up"), footer = L.t("If Horizon Nav doesn't appear in Android Auto: 1. Open Android Auto's settings, tap the version number at the bottom about 10 times, then in the top-right menu open Developer settings and turn on Unknown sources. 2. Back in Android Auto's settings, open Customise launcher and make sure Horizon Nav is ticked. 3. Force stop Android Auto (App info below), unplug and plug back in. If it still doesn't show, your phone's Android Auto only lists apps installed by the Play Store: install the APK through an installer that sets Play as the source (for example \"adb install -i com.android.vending\", or an app like AAAD or Install with Options).")) {
        SItem(L.t("Installed by"), source)
        SItem(L.t("Android Auto settings"), if (hasAa) null else L.t("Android Auto isn't installed on this phone")) {
            if (hasAa) SButton(L.t("Open")) {
                val i = ctx.packageManager.getLaunchIntentForPackage(gh)
                try {
                    if (i != null) ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) else openSystemSettings(ctx, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ghUri)
                } catch (e: Exception) { openSystemSettings(ctx, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ghUri) }
            }
        }
        if (hasAa) SItem(L.t("Android Auto app info"), L.t("To force stop it after changing its settings")) {
            SButton(L.t("App info")) { openSystemSettings(ctx, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ghUri) }
        }
        // the most asked question: Android Auto only lists apps from Google Play unless told otherwise
        if (hasAa) SItem(L.t("Not showing in Android Auto?"), L.t("1. In Android Auto's settings, tap Version repeatedly to unlock Developer settings, then turn on Unknown sources. ") +
            L.t("2. Force stop Android Auto (App info above) and reconnect. 3. If it still isn't listed, install Horizon Nav with the free AAEnabler app, which most people need. ") +
            L.t("Voice requests through Android Auto's own microphone may not reach apps from outside Google Play: use the app's search button instead.")) {
            SButton(L.t("AAEnabler")) {
                try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/malebuffy/AAEnabler")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (e: Exception) { android.widget.Toast.makeText(ctx, L.t("No browser on this device: search for AAEnabler on GitHub"), android.widget.Toast.LENGTH_LONG).show() }
            }
        }
    }
}

// ------------------------------------------------------------------ Performance

@Composable
private fun PerformanceSection(prefs: Prefs) {
    SGroup(L.t("Smoothness"), footer = L.t("Lower these if the map feels slow or the head unit gets warm. Sharpness and fast drawing apply after restarting the app.")) {
        SChoice(L.t("Map sharpness"), listOf("100" to L.t("Full"), "80" to L.t("Balanced"), "65" to L.t("Light"), "50" to L.t("Lowest")), prefs.s(PreferenceKeys.MAP_RESOLUTION, "80")) { prefs.put(PreferenceKeys.MAP_RESOLUTION, it) }
        SChoice(L.t("Frame rate"), listOf("auto" to L.t("Auto"), "20" to "20", "30" to "30", "45" to "45", "60" to "60"), prefs.s(PreferenceKeys.MAP_FPS, "auto"),
            if (prefs.s(PreferenceKeys.MAP_FPS, "auto") == "auto") L.t("Starts at 30 and steps down when the head unit can't keep up, so the map moves evenly") else L.t("Lower is lighter on the head unit")) { prefs.put(PreferenceKeys.MAP_FPS, it) }
        SToggle(prefs, L.t("Fast map drawing"), "mapfast", true)
        // (the old "Light map" switch is now Settings > Map > Map detail)
        SToggle(prefs, L.t("Performance meter"), "perfmeter", false, L.t("A line at the top of the map: frames drawn, camera moves and pauses. Handy for finding what slows the map"))
    }
    SGroup(L.t("Demo")) {
        SToggle(prefs, L.t("Demo drive button"), "demo", false, L.t("Plays a route back so you can try the guidance while parked"))
    }
}

// ------------------------------------------------------------------ Keys & accounts

@Composable
private fun KeysSection(prefs: Prefs, engine: NavEngine) {
    SGroup(L.t("Search")) {
        SChoice(L.t("Search results"), searchCountryOptions(engine), searchCountryValue(prefs)) { prefs.put(PreferenceKeys.COUNTRY, it) }
        if (engine.hasKey && engine.hasMapbox) SChoice(L.t("Search with"), listOf("auto" to L.t("TomTom"), "mapbox" to L.t("Mapbox")), if (prefs.s(PreferenceKeys.SEARCHWITH, "auto") == "mapbox") "mapbox" else "auto",
            L.t("TomTom's search is the one Horizon Nav uses: best for UK addresses and house numbers")) { prefs.put(PreferenceKeys.SEARCHWITH, it) }
        else if (!engine.hasKey) SNote(L.t("For the most accurate address search, add a free TomTom key below."))
    }
    SGroup(L.t("Map accounts"), footer = L.t("Search also finds places you saved or went to before. With no signal, routes, the map and search come from the offline pack.")) {
        SFieldItem(L.t("TomTom API key"), prefs.s(PreferenceKeys.API_KEY, ""), if (BuildConfig.TOMTOM_API_KEY.isNotBlank() && prefs.s(PreferenceKeys.API_KEY, "").isBlank()) L.t("Using the key built into this app unless you paste another") else L.t("Optional: gives the most accurate address search, and a backup for routes and traffic. Free from developer.tomtom.com"),
            placeholder = L.t("Paste a key")) { prefs.setApiKey(it) }
        SFieldItem(L.t("Mapbox access token"), prefs.s(PreferenceKeys.MAPBOX_TOKEN, ""), L.t("Use an active public token starting pk. (never sk.). If the map reports 401, open account.mapbox.com > Tokens, copy an active token, and check it has styles:read and fonts:read permissions."),
            placeholder = L.t("Paste a token")) { prefs.put(PreferenceKeys.MAPBOX_TOKEN, com.headunit.launcher.MapboxRest.clean(it)) }
    }
    if (engine.hasKey && engine.hasMapbox) SGroup(L.t("Routes, search and traffic"), footer = L.t("Auto uses Mapbox while the Mapbox map is showing, and TomTom otherwise. If the one in use stops working (its daily limit, a refused key, an outage), the other takes over by itself for a while.")) {
        SChoice(L.t("Use"), listOf("auto" to L.t("Auto"), "tomtom" to L.t("TomTom"), "mapbox" to L.t("Mapbox")), prefs.s(PreferenceKeys.DATAPROV, "auto")) { prefs.put(PreferenceKeys.DATAPROV, it) }
    }
}

// ------------------------------------------------------------------ About & backup

@Composable
private fun AboutSection(prefs: Prefs, engine: NavEngine) {
    val ctx = LocalContext.current
    val pkg = android.net.Uri.parse("package:" + ctx.packageName)
    var donating by remember { mutableStateOf(false) }
    if (donating) DonateDialog(LocalVw.current.dark) { donating = false }
    // (here so the crash report further down can open it too)
    var feedback by remember { mutableStateOf(false) }
    if (feedback) FeedbackDialog(engine) { feedback = false }
    SGroup(L.t("Feedback"), footer = L.t("Ideas and bug reports are very welcome, here or on GitHub and Reddit.")) {
        SItem(L.t("Feedback or a problem"), L.t("Sends a report with the app version, your unit and the last crash, so it's quick to fix")) { SButton(L.t("Report"), primary = true) { feedback = true } }
    }
    SGroup(L.t("Support Horizon Nav")) {
        SItem(L.t("Donate"), L.t("Free, no ads. Donations help keep it going (PayPal)")) { SButton(L.t("Donate"), primary = true) { donating = true } }
    }
    SGroup(L.t("Horizon Nav")) {
        SItem(L.t("Version"), BuildConfig.VERSION_NAME)
        if (com.headunit.launcher.Updater.enabled) {
            SItem(L.t("Check for updates"), engine.updateStatus.ifBlank { null }) {
                val u = engine.appUpdate
                if (u != null && !engine.updateBusy) SButton(L.t("Update to {0}", u.version), primary = true) { engine.installAppUpdate() }
                else SButton(if (engine.updateBusy) L.t("Updating…") else L.t("Check now")) { engine.checkForUpdate(manual = true) }
            }
            SToggle(prefs, L.t("Look for updates by itself"), "updcheck", true, L.t("When the app opens and there's a connection (at most every half hour)"))
        }
        SItem(L.t("Setup"), L.t("Go through the first-run steps again: map key, home and work, offline maps, permissions")) { SButton(L.t("Run setup")) { prefs.put(PreferenceKeys.SETUP_DONE, false); prefs.put(PreferenceKeys.FORCESETUP, true) } }
    }
    SGroup(L.t("Permissions"), footer = L.t("While a route is running, guidance carries on when you switch apps and a notification shows. Allow notifications so it can appear.")) {
        val hasLoc = ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        SItem(L.t("Location"), if (hasLoc) L.t("Allowed") else L.t("Not allowed: navigation needs it")) {
            SButton(L.t("App permissions"), primary = !hasLoc) { openSystemSettings(ctx, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg) }
        }
        SItem(L.t("Location services")) { SButton(L.t("Open")) { openSystemSettings(ctx, Settings.ACTION_LOCATION_SOURCE_SETTINGS) } }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(prefs.exportJson().toByteArray()) }
                true
            } catch (e: Exception) { false }
            android.widget.Toast.makeText(ctx, if (ok) L.t("Backup saved") else L.t("Could not save the backup"), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val msg = try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                L.t("Restored {0} settings", prefs.importJson(text))
            } catch (e: Exception) { L.t("That file is not a valid backup") }
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    SGroup(L.t("Backup"), footer = L.t("Includes settings and saved places, not the API key or the offline pack. A backup from Horizon Launcher can be restored here too.")) {
        SItem(L.t("Settings and saved places")) {
            SButton(L.t("Back up")) { exporter.launch("horizon-nav-backup.json") }
            SButton(L.t("Restore")) { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }
        }
    }
    val cf = java.io.File(ctx.filesDir, "lastcrash.txt")
    var rep by remember { mutableStateOf(if (cf.exists()) cf.readText() else "") }
    // a report from before the last update (older reports have no version line, or another version's): it may well be fixed already
    val oldRep = rep.isNotBlank() && !rep.startsWith("Horizon Nav ${BuildConfig.VERSION_NAME} ")
    if (rep.isNotBlank()) SGroup(L.t("Problem report"), footer = if (oldRep) L.t("This is from an older version of the app, before your last update, so it may already be fixed. Clear it, and send a photo if it comes back.") else L.t("The app closed unexpectedly last time. Send a photo of this so it can be fixed.")) {
        Text(rep.take(900), color = LocalVw.current.text, fontSize = 10.sp, modifier = Modifier.padding(vertical = 6.dp))
        // sent the same way as Feedback (the report includes this crash and the unit's details)
        SItem(L.t("This crash"), L.t("Sends it with your unit's details, the same as Feedback")) {
            SButton(L.t("Send report"), primary = true) { feedback = true }
            SButton(L.t("Clear")) { cf.delete(); rep = "" }
        }
    }
}

/** Readable name of a country code ("UK", "Norway"...). */
internal fun countryName(cc: String): String = when (cc.uppercase()) {
    "GB" -> "UK"; "US" -> "US"; "" -> ""
    else -> java.util.Locale("", cc).getDisplayCountry(java.util.Locale.UK).ifBlank { cc }
}

/** Search limited to the country you're in (followed as you travel) or anywhere. */
internal fun searchCountryOptions(engine: NavEngine): List<Pair<String, String>> {
    val here = engine.detectedCountry
    return listOf("auto" to (if (here.isNotBlank()) "${countryName(here)} only" else L.t("My country only")), "" to L.t("Anywhere"))
}

internal fun searchCountryValue(prefs: Prefs): String = if (prefs.s(PreferenceKeys.COUNTRY, "auto").isBlank()) "" else "auto"

/**
 * Feedback: say what happened in a line or two, then send it as a GitHub issue (the browser opens with it filled in) or copy it to
 * paste anywhere (Reddit, a message). Either way the app version, unit, main settings and the last crash go with it.
 */
@Composable
private fun FeedbackDialog(engine: NavEngine, onClose: () -> Unit) {
    val c = LocalVw.current
    val ctx = LocalContext.current
    var note by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.panel).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(L.t("Feedback or a problem"), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(L.t("What happened, or what would you like? Where you were and what you tapped helps."), color = c.sub, fontSize = 14.sp)
            SField(note, { note = it.take(600) }, placeholder = L.t("e.g. Rerouted the wrong way near Southampton"), width = 516.dp, fill = true)
            Text(L.t("Sent with it: the app version, your head unit, the main settings, your downloaded areas and the last crash (no places or API keys)."), color = c.sub, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                SButton(L.t("Close")) { onClose() }
                SButton(L.t("Copy")) {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText(L.t("Horizon Nav report"), engine.feedbackReport(note)))
                    engine.toast(L.t("Report copied: paste it into a message or a Reddit comment"), 3500)
                }
                SButton(L.t("Send on GitHub"), primary = true) {
                    val title = note.lineSequence().firstOrNull()?.take(80)?.ifBlank { null } ?: L.t("Report from Horizon Nav {0}", com.headunit.launcher.BuildConfig.VERSION_NAME)
                    // a link has a length limit, so a very long report is cut down (Copy always has all of it)
                    val body = engine.feedbackReport(note).take(5000)
                    val url = "https://github.com/${BuildConfig.UPDATE_REPO}/issues/new?title=" + android.net.Uri.encode(title) + "&body=" + android.net.Uri.encode(body)
                    try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (e: Exception) { engine.toast(L.t("No browser on this unit: use Copy instead"), 3500) }
                    onClose()
                }
            }
        }
    }
}

/**
 * "Request a language": the language wanted (and anything else) goes to GitHub as an issue, filled in, from the browser. The
 * title and the details stay in English so they can be read there; only what the person typed is theirs.
 */
@Composable
private fun LanguageRequestDialog(engine: NavEngine, onClose: () -> Unit) {
    val c = LocalVw.current
    val ctx = LocalContext.current
    var lang by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var helper by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.panel).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(L.t("Request a language"), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(L.t("Which language would you like Horizon Nav in? If you can speak it, say so: you could help check the wording."), color = c.sub, fontSize = 14.sp)
            SField(lang, { lang = it.take(40) }, placeholder = L.t("e.g. Swedish"), width = 516.dp, fill = true)
            SField(note, { note = it.take(400) }, placeholder = L.t("Anything else (optional)"), width = 516.dp, fill = true)
            Row(Modifier.fillMaxWidth().pressClick { helper = !helper }, verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(helper, { helper = it })
                Text(L.t("I can help check the wording"), color = c.text, fontSize = 15.sp)
            }
            Text(L.t("Opens GitHub with the request filled in: tap Submit there to send it."), color = c.sub, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                SButton(L.t("Close")) { onClose() }
                SButton(L.t("Send on GitHub"), primary = true) {
                    val want = lang.trim()
                    if (want.isEmpty()) { engine.toast(L.t("Type the language first"), 2500); return@SButton }
                    val phone = java.util.Locale.getDefault()
                    val body = buildString {
                        append("**Language requested:** ").append(want).append("\n\n")
                        append("**Can help check the wording:** ").append(if (helper) "yes" else "no").append("\n\n")
                        if (note.isNotBlank()) append("**Notes:** ").append(note.trim()).append("\n\n")
                        append("---\n")
                        append("Phone language: ").append(phone.getDisplayName(java.util.Locale.ENGLISH)).append(" (").append(phone.toLanguageTag()).append(")\n")
                        append("App language: ").append(L.lang).append("\n")
                        append("Horizon Nav ").append(BuildConfig.VERSION_NAME).append("\n")
                        append("_Sent from Settings › Sound & voice › Request a language_")
                    }
                    val title = "Language request: " + want
                    val url = "https://github.com/${BuildConfig.UPDATE_REPO}/issues/new?title=" + android.net.Uri.encode(title) + "&body=" + android.net.Uri.encode(body)
                    try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (e: Exception) { engine.toast(L.t("No browser on this unit: use Copy instead"), 3500) }
                    onClose()
                }
            }
        }
    }
}

/**
 * Settings > Map > Colours: a small picture of each colour set (land, water, park, roads, a building and the route), tap to pick.
 * Day colours are shown; each one has its night version.
 */
@Composable
private fun MapColoursPicker(value: String, onPick: (String) -> Unit) {
    val c = LocalVw.current
    // land, water, park, road, building
    val sets = listOf(
        Triple("mapbox", L.t("Mapbox"), listOf(0xFFEDEFE9, 0xFF9CCBF0, 0xFFC6E4B4, 0xFFFFFFFF, 0xFFDCDCE0)),
        Triple("horizon", L.t("Horizon"), listOf(0xFFF4F2EE, 0xFFAADAF5, 0xFFCDEBC4, 0xFFFFFFFF, 0xFFE7E5E1)),
        Triple("twilight", L.t("Twilight"), listOf(0xFFF1F0F5, 0xFFAAD4F5, 0xFFCDEBC4, 0xFFFFFFFF, 0xFFE5E3EA)),
        Triple("faded", L.t("Faded"), listOf(0xFFF1F1F1, 0xFFD9E2EA, 0xFFE2E8DE, 0xFFFFFFFF, 0xFFE6E6E6)),
        Triple("grey", L.t("Grey"), listOf(0xFFE8E8E8, 0xFFCFCFCF, 0xFFDADADA, 0xFFFFFFFF, 0xFFD4D4D4)),
        Triple("ember", L.t("Ember"), listOf(0xFFF3EEE6, 0xFFB8D0DC, 0xFFD6E4C4, 0xFFFFFFFF, 0xFFE6DDD0)),
        Triple("mono", L.t("Mono Night"), listOf(0xFFEFEFEF, 0xFFD4D4D4, 0xFFDEDEDE, 0xFFFFFFFF, 0xFFE2E2E2)),
        Triple("forest", L.t("Forest"), listOf(0xFFEEF0E6, 0xFFA8CFC8, 0xFFBCDCAE, 0xFFFFFFFF, 0xFFE0E2D4)),
        Triple("arctic", L.t("Arctic"), listOf(0xFFF2F6FA, 0xFFBFE0F5, 0xFFD8ECEA, 0xFFFFFFFF, 0xFFE4ECF4)),
        Triple("contrast", L.t("High Contrast"), listOf(0xFFFFFFFF, 0xFF7CC4F0, 0xFFB4E0A4, 0xFF222222, 0xFFD8D8D8)),
        Triple("sepia", L.t("Sepia"), listOf(0xFFEFE4CF, 0xFFC8D2C0, 0xFFD8DCB4, 0xFFFAF4E6, 0xFFE2D4B8)),
        Triple("neon", L.t("Neon"), listOf(0xFFECEBF4, 0xFFA8E4F4, 0xFFC8ECD4, 0xFFFFFFFF, 0xFFE2E0EE)),
        Triple("pastel", L.t("Pastel"), listOf(0xFFF6F3F0, 0xFFCFE4F2, 0xFFDCECD6, 0xFFFFFFFF, 0xFFECE8E6)),
        Triple("car", L.t("Match my car"), listOf(0xFF1A1424, 0xFF14182C, 0xFF232C30, 0xFF5A4A6E, 0xFF2A2240)),
        Triple("custom", L.t("Custom"), listOf(0xFF26304C, 0xFF2F6FB0, 0xFF3F7A5A, 0xFF8A93AE, 0xFFE0A84A)),
    )
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SItem(L.t("Colours"), L.t("Horizon: Mapbox's map in the app's own soft colours, slate blue at night. Twilight: the same with a deeper purple night. Mono Night: greys, with only the route and warnings in colour. Match my car: a hint of your car's interior lighting colour. Custom: your own colours. Day colours shown (Match my car at night); each has a night version"))
        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((id, name, cols) in sets) {
                val sel = id == value
                Column(
                    Modifier.width(124.dp).clip(RoundedCornerShape(14.dp)).background(c.tile)
                        .border(2.dp, if (sel) c.accent else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable { onPick(id) },
                ) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(76.dp)) {
                        val w = size.width; val h = size.height
                        fun col(i: Int) = androidx.compose.ui.graphics.Color(cols[i])
                        drawRect(col(0))
                        drawRect(col(1), topLeft = androidx.compose.ui.geometry.Offset(w * 0.68f, 0f), size = androidx.compose.ui.geometry.Size(w * 0.32f, h * 0.3f))
                        drawRect(col(2), topLeft = androidx.compose.ui.geometry.Offset(w * 0.06f, h * 0.62f), size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.26f))
                        drawRect(col(4), topLeft = androidx.compose.ui.geometry.Offset(w * 0.47f, h * 0.1f), size = androidx.compose.ui.geometry.Size(w * 0.18f, h * 0.2f))
                        drawRect(col(3), topLeft = androidx.compose.ui.geometry.Offset(0f, h * 0.44f), size = androidx.compose.ui.geometry.Size(w, h * 0.12f))
                        drawRect(col(3), topLeft = androidx.compose.ui.geometry.Offset(w * 0.36f, 0f), size = androidx.compose.ui.geometry.Size(w * 0.06f, h))
                        drawRect(androidx.compose.ui.graphics.Color(0xFF2A7BE4), topLeft = androidx.compose.ui.geometry.Offset(w * 0.68f, 0f), size = androidx.compose.ui.geometry.Size(w * 0.05f, h))
                    }
                    Text(name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                }
            }
        }
    }
}


// ------------------------------------------------------------------ pictures for the choices, and the map preview

private fun androidx.compose.ui.graphics.drawscope.DrawScope.lanePicture(id: String, c: Vw) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF2A3042))
    when (id) {
        "lit" -> {
            // three lanes seen ahead, the middle one lit green
            val road = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.2f, h); lineTo(w * 0.8f, h); lineTo(w * 0.62f, 0f); lineTo(w * 0.38f, 0f); close() }
            drawPath(road, Color(0xFF596274))
            val lit = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.4f, h); lineTo(w * 0.6f, h); lineTo(w * 0.54f, 0f); lineTo(w * 0.46f, 0f); close() }
            drawPath(lit, Color(0xFF34C759).copy(alpha = 0.75f))
            for (f in listOf(0.4f, 0.6f)) drawLine(Color.White, androidx.compose.ui.geometry.Offset(w * f, h), androidx.compose.ui.geometry.Offset(w * (0.5f + (f - 0.5f) * 0.4f), 0f), 2f)
        }
        "closeup" -> {
            drawRoundRect(Color(0xFF1C2130), androidx.compose.ui.geometry.Offset(w * 0.08f, h * 0.12f), androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.76f), androidx.compose.ui.geometry.CornerRadius(10f))
            for ((i, on) in listOf(false, true, true).withIndex()) {
                val x = w * (0.28f + i * 0.22f)
                drawLine(if (on) Color.White else Color(0xFF6A7184), androidx.compose.ui.geometry.Offset(x, h * 0.72f), androidx.compose.ui.geometry.Offset(x, h * 0.32f), 5f)
                drawLine(if (on) Color.White else Color(0xFF6A7184), androidx.compose.ui.geometry.Offset(x - 7f, h * 0.42f), androidx.compose.ui.geometry.Offset(x, h * 0.3f), 5f)
                drawLine(if (on) Color.White else Color(0xFF6A7184), androidx.compose.ui.geometry.Offset(x + 7f, h * 0.42f), androidx.compose.ui.geometry.Offset(x, h * 0.3f), 5f)
            }
        }
        else -> {
            // a blue gantry sign over the road
            drawRect(Color(0xFF596274), androidx.compose.ui.geometry.Offset(w * 0.3f, h * 0.55f), androidx.compose.ui.geometry.Size(w * 0.4f, h * 0.45f))
            drawRoundRect(Color(0xFF1E5BC6), androidx.compose.ui.geometry.Offset(w * 0.12f, h * 0.1f), androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.38f), androidx.compose.ui.geometry.CornerRadius(6f))
            for (i in 0..2) {
                val x = w * (0.3f + i * 0.2f)
                drawLine(if (i == 0) Color(0xFF8FB0E8) else Color.White, androidx.compose.ui.geometry.Offset(x, h * 0.42f), androidx.compose.ui.geometry.Offset(x, h * 0.18f), 4f)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.speedPicture(id: String, c: Vw) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF2A3042))
    // the cards on the left, the speed sign on the right at the chosen height
    drawRoundRect(Color(0xFF3A4256), androidx.compose.ui.geometry.Offset(w * 0.06f, h * 0.1f), androidx.compose.ui.geometry.Size(w * 0.36f, h * 0.3f), androidx.compose.ui.geometry.CornerRadius(6f))
    drawRoundRect(Color(0xFF3A4256), androidx.compose.ui.geometry.Offset(w * 0.06f, h * 0.7f), androidx.compose.ui.geometry.Size(w * 0.5f, h * 0.22f), androidx.compose.ui.geometry.CornerRadius(6f))
    val cy = when (id) { "top" -> h * 0.24f; "middle" -> h * 0.5f; else -> h * 0.76f }
    val ctr = androidx.compose.ui.geometry.Offset(w * 0.82f, cy)
    drawCircle(Color.White, h * 0.17f, ctr)
    drawCircle(Color(0xFFE0202A), h * 0.17f, ctr, style = androidx.compose.ui.graphics.drawscope.Stroke(h * 0.05f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.locatorPicture(id: String, c: Vw) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF2A3042))
    drawRect(Color(0xFF596274), androidx.compose.ui.geometry.Offset(w * 0.38f, 0f), androidx.compose.ui.geometry.Size(w * 0.24f, h))
    val cx = w / 2; val cy = h * 0.55f
    when (id) {
        "car" -> {
            drawRoundRect(Color(0xFF3C8CFF), androidx.compose.ui.geometry.Offset(cx - 10f, cy - 18f), androidx.compose.ui.geometry.Size(20f, 36f), androidx.compose.ui.geometry.CornerRadius(7f))
            drawRoundRect(Color(0xFF1B2A44), androidx.compose.ui.geometry.Offset(cx - 7f, cy - 10f), androidx.compose.ui.geometry.Size(14f, 9f), androidx.compose.ui.geometry.CornerRadius(3f))
        }
        "custom" -> {
            drawCircle(Color(0xFF9CA4BF), 16f, androidx.compose.ui.geometry.Offset(cx, cy))
            drawCircle(Color(0xFF2A3042), 6f, androidx.compose.ui.geometry.Offset(cx, cy - 3f))
        }
        else -> {
            val p = androidx.compose.ui.graphics.Path().apply { moveTo(cx, cy - 20f); lineTo(cx + 15f, cy + 16f); lineTo(cx, cy + 8f); lineTo(cx - 15f, cy + 16f); close() }
            if (id == "flat") drawPath(p, Color.White)
            else {
                drawPath(p, Color(0xFF3C8CFF))
                drawPath(p, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                if (id == "model") drawCircle(Color(0x553C8CFF), 26f, androidx.compose.ui.geometry.Offset(cx, cy))
            }
        }
    }
}

/**
 * The real map at the top of the map settings pages, so every change shows as it's made. Day / Night show it in that light for a
 * moment (to check the night colours in daylight); Now, or leaving, goes back. It can't be dragged, so the map stays where it was.
 */
@Composable
private fun SettingsMapPreview(engine: NavEngine, heightDp: Int) {
    val c = LocalVw.current
    // (shared with the Custom theme's Day / Night tabs, so the preview shows the one being edited)
    var mode by PreviewLight::mode
    androidx.compose.runtime.DisposableEffect(mode) {
        engine.map.forceLight(when (mode) { "day" -> "day"; "night" -> "night"; else -> null })
        onDispose { }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { engine.map.forceLight(null); PreviewLight.mode = "now" } }
    Box(Modifier.fillMaxWidth().widthIn(max = 820.dp).height(heightDp.dp).padding(bottom = 10.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, c.line, RoundedCornerShape(20.dp))) {
        MapHost(engine.map, Modifier.fillMaxSize(), cornerDp = 20)
        // (touches stop here: the preview only shows)
        Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } })
        Text(L.t("Live preview"), color = Color.White, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp))
        Row(Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x99000000))) {
            for ((id, label) in listOf("day" to L.t("Day"), "night" to L.t("Night"), "now" to L.t("Now"))) {
                val sel = id == mode
                Box(Modifier.background(if (sel) c.accent else Color.Transparent).pressClick { mode = id }.padding(horizontal = 14.dp, vertical = 7.dp)) {
                    Text(label, color = Color.White, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}


/**
 * Card positions: a little picture of a wide screen showing where the turn card, trip card and speedometer will sit, redrawn as the
 * choices change (resolved the same way the map screens do it).
 */
@Composable
private fun CardPositionsPreview(prefs: com.headunit.launcher.Prefs, car: Boolean) {
    val vw = LocalVw.current
    val engineMir = prefs.s(PreferenceKeys.DRIVE, "") == "lhd" ||
        (prefs.s(PreferenceKeys.DRIVE, "") == "" && prefs.s(PreferenceKeys.COUNTRYDETECTED, "").let { it.isNotBlank() && it.uppercase() !in com.headunit.launcher.KEEP_LEFT })
    val mir = if (car) false else when (prefs.s(PreferenceKeys.CARDS_SIDE, "auto")) { "left" -> false; "right" -> true; else -> engineMir }
    val trip = if (car) prefs.s(PreferenceKeys.CAR_TRIP_POS, "auto") else prefs.s(PreferenceKeys.TRIP_POS, "auto")
    val tripAt = if (trip == "auto") (if (mir) "right" else "left") else trip
    val sp = if (car) prefs.s(PreferenceKeys.CAR_SPEED_SIDE, "auto") else prefs.s(PreferenceKeys.SPEED_SIDE, "auto")
    val speedAt = if (sp == "auto") (if (car && trip == "right") "left" else if (mir) "left" else "right") else sp
    val green = Color(0xFF1F7A57)
    val dark = Color(0xFF1B2029)
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(130.dp).clip(RoundedCornerShape(12.dp))) {
        val w = size.width; val h = size.height
        drawRect(Color(0xFF2A3242))
        drawLine(Color(0xFF3A4558), androidx.compose.ui.geometry.Offset(0f, h * 0.7f), androidx.compose.ui.geometry.Offset(w, h * 0.45f), strokeWidth = h * 0.06f)
        val pad = w * 0.03f
        // the turn card
        val tw = w * 0.32f; val th = h * 0.26f
        val tx = if (mir) w - pad - tw else pad
        drawRoundRect(green, androidx.compose.ui.geometry.Offset(tx, pad), androidx.compose.ui.geometry.Size(tw, th), androidx.compose.ui.geometry.CornerRadius(10f))
        // the speedometer
        val r = h * 0.12f
        val sx = if (speedAt == "left") pad + r else w - pad - r
        // the trip strip
        val sw = w * 0.36f; val sh = h * 0.13f
        val sharing = speedAt == tripAt
        val sy = h - pad - r - (if (sharing) sh + pad else 0f)
        drawCircle(dark, r, androidx.compose.ui.geometry.Offset(sx, sy))
        drawCircle(Color(0xFF5A9BFF), r, androidx.compose.ui.geometry.Offset(sx, sy), style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.18f))
        val stx = when (tripAt) { "centre" -> (w - sw) / 2; "right" -> w - pad - sw; else -> pad }
        drawRoundRect(dark, androidx.compose.ui.geometry.Offset(stx, h - pad - sh), androidx.compose.ui.geometry.Size(sw, sh), androidx.compose.ui.geometry.CornerRadius(sh / 2))
        drawRoundRect(vw.accent, androidx.compose.ui.geometry.Offset(stx + sw * 0.08f, h - pad - sh * 0.62f), androidx.compose.ui.geometry.Size(sw * 0.3f, sh * 0.24f), androidx.compose.ui.geometry.CornerRadius(4f))
        // your arrow
        val ax = w * 0.5f; val ay = h * 0.55f
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(ax, ay - h * 0.07f); lineTo(ax + h * 0.05f, ay + h * 0.05f); lineTo(ax, ay + h * 0.025f); lineTo(ax - h * 0.05f, ay + h * 0.05f); close() }
        drawPath(path, Color(0xFF9CC7FF))
    }
}
