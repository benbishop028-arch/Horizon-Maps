
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
import com.headunit.launcher.NavEngine
import com.headunit.launcher.OfflinePlaces
import com.headunit.launcher.Place
import com.headunit.launcher.Prefs
import com.headunit.launcher.PreferenceKeys
import com.headunit.launcher.mapSource
import com.headunit.launcher.mapDetail
import com.headunit.launcher.setApiKey
import com.headunit.launcher.Zones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A settings section; [heading] puts a small group heading (Map, Driving, You, Device) above it in the list. */
private class Section(val title: String, val sub: String, val icon: ImageVector, val tint: Color, val heading: String? = null)

// (the order here is the order in the list; SettingsIndex refers to them by number)
private val sections = listOf(
    Section("Map look", "Day and night, detail, 3D, satellite", Icons.Rounded.Map, Color(0xFF4C8DFF), "MAP"),
    Section("On the map", "Traffic, lights, cameras, signs, weather", Icons.Rounded.Traffic, Color(0xFF4C8DFF)),
    Section("Your arrow & route line", "Arrow or car, colours, route look", Icons.Rounded.Navigation, Color(0xFF4C8DFF)),
    Section("Routes", "Avoid, eco, faster routes, starting", Icons.Rounded.Route, Color(0xFF12A4B4), "DRIVING"),
    Section("Turn card & lanes", "Signs, lanes, trip card, arriving", Icons.Rounded.TurnRight, Color(0xFF12A4B4)),
    Section("Alerts", "Speed, cameras, hazards, reports", Icons.Rounded.NotificationsActive, Color(0xFFEA4335)),
    Section("Sound & voice", "Spoken directions and volume", Icons.Rounded.VolumeUp, Color(0xFFEA4335)),
    Section("Places & trips", "Home, work, favourites, sharing, trip log", Icons.Rounded.Star, Color(0xFFF29900), "YOU"),
    Section("Fuel & charging", "Your fuel, prices, electric car", Icons.Rounded.LocalGasStation, Color(0xFF34A853)),
    Section("Offline maps", "Maps, routes and town names with no signal", Icons.Rounded.CloudDownload, Color(0xFF8E5CF0)),
    Section("Screen & sizes", "Brightness, sizes, units, while driving", Icons.Rounded.BrightnessMedium, Color(0xFFFFB020), "DEVICE"),
    Section("Android Auto", "Car-screen sizes and setting it up", Icons.Rounded.DirectionsCar, Color(0xFF9CA4BF)),
    Section("Performance", "Smoothness on slower head units, demo", Icons.Rounded.Speed, Color(0xFF9CA4BF)),
    Section("Keys & accounts", "Map keys and search", Icons.Rounded.VpnKey, Color(0xFF9CA4BF)),
    Section("About & backup", "Updates, permissions, backup", Icons.Rounded.Info, Color(0xFF9CA4BF)),
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
    var cat by rememberSaveable { mutableIntStateOf(0) }
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
            val hay = (r.title + " " + r.sub + " " + r.group + " " + sections[r.section].title).lowercase()
            words.all { hay.contains(it) }
        }.sortedBy { if (it.title.lowercase().startsWith(words[0])) 0 else if (it.title.lowercase().contains(words[0])) 1 else 2 }.take(40)
    }
    Row(Modifier.fillMaxSize().background(c.bg).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // the list of sections
        if (!narrowS || showList) Column(
            Modifier.then(if (narrowS) Modifier.fillMaxWidth() else Modifier.width(290.dp)).fillMaxHeight().clip(RoundedCornerShape(28.dp)).background(c.panel).border(1.dp, c.line, RoundedCornerShape(28.dp)).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(c.tile).pressClick(onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowBack, "Back to the map", tint = c.text, modifier = Modifier.size(24.dp))
                }
                Text("Settings", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp))
            }
            // search every setting by name
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).height(46.dp).clip(RoundedCornerShape(50)).background(c.tile).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.sub, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f).padding(start = 10.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search settings", color = c.sub, fontSize = 15.sp)
                    androidx.compose.foundation.text.BasicTextField(
                        value = query, onValueChange = { query = it; highlight = null }, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 15.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) Icon(Icons.Rounded.Close, "Clear", tint = c.sub, modifier = Modifier.size(20.dp).clickable { query = ""; focus.clearFocus() })
            }
            if (words.isNotEmpty()) {
                if (matches.isEmpty()) Text("Nothing matches \"${query.trim()}\"", color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
                matches.forEach { r ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                            cat = r.section; highlight = r.title; query = ""; focus.clearFocus(); showList = false
                        }.padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Text(r.title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(sections[r.section].title + (if (r.group.isNotBlank()) " · " + r.group else ""), color = c.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else sections.forEachIndexed { i, s ->
                // the small group headings in the list: Map, Driving, You, Device
                if (s.heading != null) Text(s.heading, color = c.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp,
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
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp)) {
                if (narrowS) Row(Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(50)).clickable { showList = true }.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(c.tile), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ArrowBack, "All settings", tint = c.text, modifier = Modifier.size(22.dp))
                    }
                    Text("Settings", color = c.sub, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp))
                }
                Text(sections[cat].title, color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
                Text(sections[cat].sub, color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, bottom = 16.dp))
                Column(Modifier.widthIn(max = 820.dp)) {
                    when (cat) {
                        0 -> MapLookSection(prefs, engine)
                        1 -> OnTheMapSection(prefs, engine)
                        2 -> ArrowSection(prefs, engine)
                        3 -> RoutesSection(prefs)
                        4 -> GuidanceSection(prefs)
                        5 -> AlertsSection(prefs, engine)
                        6 -> SoundSection(prefs, engine)
                        7 -> { PlacesSection(engine); PhoneLinkGroup(prefs); SharingGroups(prefs) }
                        8 -> FuelSection(prefs)
                        9 -> { OfflineMapsContent(prefs, engine); OfflinePlacesGroup(engine) }
                        10 -> ScreenSection(prefs, engine)
                        11 -> AndroidAutoSection(prefs)
                        12 -> PerformanceSection(prefs)
                        13 -> KeysSection(prefs, engine)
                        else -> AboutSection(prefs, engine)
                    }
                }
            }
          }
        }
    }
}

// ------------------------------------------------------------------ Map look

@Composable
private fun MapLookSection(prefs: Prefs, engine: NavEngine) {
    SGroup("Day and night") {
        SChoice("Day and night", listOf("auto" to "Auto", "lights" to "Headlights", "sun" to "Sun times", "times" to "My times", "day" to "Day", "night" to "Night"), prefs.s(PreferenceKeys.THEME, "auto"),
            "Auto follows the head unit's own night mode once it has switched to it, and sunrise and sunset until then. Headlights follows the head unit's night mode when it switches with your lights (turn on its headlight or illumination night-mode option), with sun times until then. Sun times switches at sunrise and sunset. My times switches at the times you set") { prefs.put(PreferenceKeys.THEME, it) }
        if (prefs.s(PreferenceKeys.THEME, "auto") == "times") {
            SFieldItem("Night starts at", prefs.s(PreferenceKeys.NIGHT_FROM, "19:00"), "24-hour clock, for example 19:30", placeholder = "19:00") { prefs.put(PreferenceKeys.NIGHT_FROM, it.trim()) }
            SFieldItem("Day starts at", prefs.s(PreferenceKeys.DAY_FROM, "07:00"), "24-hour clock, for example 06:45", placeholder = "07:00") { prefs.put(PreferenceKeys.DAY_FROM, it.trim()) }
        }
    }
    SGroup("The map") {
        SChoice("Map source", listOf("mapbox" to "Mapbox", "tomtom" to "TomTom", "osm" to "OpenStreetMap"), prefs.mapSource(),
            "Mapbox: the 3D map. TomTom: TomTom's map in the Horizon colours (needs a TomTom key). All need the Mapbox token, as Mapbox's engine draws them") { prefs.put(PreferenceKeys.MAP_STYLE, it) }
        if (prefs.mapSource() == "mapbox") {
            SChoice("Map detail", listOf("full" to "Full 3D", "lite" to "Lite", "lightest" to "Lightest"), prefs.mapDetail(),
                "Full 3D: buildings, landmarks, trees and shadows. Lite: flat buildings, no landmarks or shadows. Lightest: Mapbox's simpler 2D map, for slow head units") { prefs.put(PreferenceKeys.MAP_DETAIL, it) }
            MapColoursPicker(prefs.s(PreferenceKeys.MAP_COLOURS, "mapbox")) { prefs.put(PreferenceKeys.MAP_COLOURS, it) }
        }
        DefaultZoomRow(prefs)
        SToggle(prefs, "Footpaths and trails", "paths", true, "Turn off for a cleaner driving map")
        SToggle(prefs, "Glass panels", "glass", true, "See-through cards tinted by the map. Off gives solid cards")
        SChoice("Mapbox logo position", listOf("tl" to "Top left", "tr" to "Top right", "bl" to "Bottom left", "br" to "Bottom right"), prefs.s(PreferenceKeys.MAPBOX_LOGO, "tr"),
            "Mapbox requires their logo on the map; pick the corner that suits your layout") { prefs.put(PreferenceKeys.MAPBOX_LOGO, it) }
    }
    SGroup("3D and satellite") {
        SToggle(prefs, "Satellite", "satellite", false)
        SToggle(prefs, "3D buildings", "buildings3d", true)
        SToggle(prefs, "Detailed building fronts", "facades", true, "Windows, doors and shopfronts on buildings, where Mapbox has them (mostly city centres)")
        SToggle(prefs, "3D landmarks", "landmarks3d", true, "Detailed models of well-known buildings, bridges and stadiums")
        SToggle(prefs, "3D hills and valleys", "terrain", false, "Real terrain. Lovely in hilly areas, but harder work for a slow head unit")
        SToggle(prefs, "Fog and sky", "skyweather", true, "Haze towards the horizon to match the weather: grey when overcast, thicker in rain, thick in fog")
        SToggle(prefs, "Night sky in 3D", "nightsky", true, "Twinkling stars above the horizon after dark")
        SToggle(prefs, "Trees in parks", "trees", true, "Mapbox's 3D trees in parks and along streets")
        SToggle(prefs, "Keep the road ahead clear", "clearview", true, "Mapbox's 3D trees and landmarks are left out round your arrow and, on a route, along the middle of the road just ahead, so they never hide it")
        SToggle(prefs, "Tap to highlight", "tapselect", true, "Tap a building to light it up; tap a shop or place, then hold on it to go there")
    }
}

// ------------------------------------------------------------------ On the map

@Composable
private fun OnTheMapSection(prefs: Prefs, engine: NavEngine) {
    SGroup("Traffic") {
        SToggle(prefs, "Live traffic", "traffic", true)
        SToggle(prefs, "Traffic overview when stopped", PreferenceKeys.STOPPED_OVERVIEW, true, "After 30 seconds stopped (lights, a queue), the map pulls back to a top-down view of the traffic round you, and back again when you move off")
        SToggle(prefs, "Only show traffic on your route while navigating", "routetrafficonly", false, "Off shows traffic on the roads around you too, as Google Maps does")
        SToggle(prefs, "Light traffic in green", PreferenceKeys.TRAFFIC_GREEN, false, "Free-flowing main roads shown green, as well as slow traffic in amber and red")
    }
    SGroup("On your route") {
        SToggle(prefs, "Traffic lights", "signals", true, if (prefs.b(PreferenceKeys.SIGNALS, true)) "Only the ones on your route. Now: " + engine.map.signalInfo() else null)
        SToggle(prefs, "Speed cameras and roadworks", "hazardicons", true)
        SToggle(prefs, "Zebra crossings", "zebras", true, "Crossings on your route painted on the road, with Belisha beacons at zebras (close in)")
        SToggle(prefs, "Stop signs", PreferenceKeys.STOP_SIGNS, true, "Stop signs on your route shown on the map, with a short chime before each one (where OpenStreetMap has them)")
    }
    SGroup("3D street furniture") {
        SToggle(prefs, "3D street lamps, traffic lights and cameras", "furniture3d", true, "Real-size 3D models with lit lamps and lights (Full 3D). Off: the simpler badges and blocks")
        SToggle(prefs, "Street lamps", "lamps", true, if (prefs.b(PreferenceKeys.LAMPS, true)) "Now: " + engine.map.lampInfo() else null)
        if (prefs.b(PreferenceKeys.LAMPS, true)) SSlider("Street lamp spacing", prefs.s(PreferenceKeys.LAMPGAP, "40").toFloatOrNull() ?: 40f, 15f..120f, " m", reset = 40f) { prefs.put(PreferenceKeys.LAMPGAP, it.toString()) }
    }
    SGroup("Weather") {
        SToggle(prefs, "Weather effects on the map", "rainfx", false, "Real rain and snow falling on the map (and fog) to match the weather where you are")
        SToggle(prefs, "Rain radar layer", "radar", false, "Live radar from RainViewer, under the route")
    }
}

// ------------------------------------------------------------------ Your arrow & route line

@Composable
private fun ArrowSection(prefs: Prefs, engine: NavEngine) {
    SGroup("Your arrow", footer = "Real 3D model: drawn in the map itself, lit and shadowed like the buildings (Full 3D only; otherwise the picture of the same shape is used). The 3D arrow and car are pictures of 3D shapes. The flat arrow is the lightest to draw on a slow unit.") {
        val loc = prefs.s(PreferenceKeys.LOCATOR, "arrow")
        val ctx = LocalContext.current
        val hasPic = prefs.s(PreferenceKeys.LOCATOR_IMAGE, "").isNotBlank() && java.io.File(ctx.filesDir, "locator.png").exists()
        val pickPic = rememberLocatorImagePicker { ok ->
            if (ok) { prefs.put(PreferenceKeys.LOCATOR_IMAGE, System.currentTimeMillis().toString()); prefs.put(PreferenceKeys.LOCATOR, "custom") }
            else android.widget.Toast.makeText(ctx, "Couldn't read that picture (try a PNG or JPG)", android.widget.Toast.LENGTH_SHORT).show()
        }
        fun choosePic() { if (!pickPic()) android.widget.Toast.makeText(ctx, "No app on this unit can pick a picture (try installing a file manager or gallery)", android.widget.Toast.LENGTH_LONG).show() }
        SChoice("Locator", listOf("arrow" to "3D arrow", "car" to "Car", "model" to "Real 3D model", "flat" to "Flat arrow", "custom" to "Your picture"), loc) {
            if (it == "custom" && !hasPic) choosePic() else prefs.put(PreferenceKeys.LOCATOR, it)
        }
        if (loc == "model") {
            SChoice("Model shape", listOf("arrow" to "Arrow", "car" to "Car"), prefs.s(PreferenceKeys.MODEL_SHAPE, "arrow")) { prefs.put(PreferenceKeys.MODEL_SHAPE, it) }
            SToggle(prefs, "Glow", PreferenceKeys.MODEL_GLOW, true, "A soft blue glow round the model, and the model lit up brightly")
            if (prefs.mapDetail() != "full") SNote("The real 3D model shows with Map detail: Full 3D. On Lite and Lightest the picture of the same shape is used.")
        }
        val shapeCar = loc == "car" || (loc == "model" && prefs.s(PreferenceKeys.MODEL_SHAPE, "arrow") == "car")
        val shapeArrow = loc == "arrow" || (loc == "model" && !shapeCar)
        if (loc == "custom") {
            SItem("Picture", "A PNG with a see-through background looks best. Point the top of it the way you drive") { SButton(if (hasPic) "Change" else "Choose") { choosePic() } }
            SToggle(prefs, "Turns with you", PreferenceKeys.LOCATOR_IMAGE_TURNS, true, "Lies flat on the road and turns as you drive, like the arrow. Off keeps it upright")
            SToggle(prefs, "Round crop", PreferenceKeys.LOCATOR_IMAGE_CIRCLE, false, "Cuts it to a circle with a white edge (good for photos)")
        }
        // the car's shape: for the Car picture and the real 3D car alike
        if (shapeCar) {
            val cur = prefs.s(PreferenceKeys.CAR_MODEL, "saloon")
            val colour = prefs.s(PreferenceKeys.CAR_COLOUR, "#F4F5F7")
            val names = listOf("saloon" to "Saloon", "hatch" to "Hatchback", "estate" to "Estate", "suv" to "SUV", "pickup" to "Pickup", "van" to "Van", "sports" to "Sports")
            SItem("Car type", names.firstOrNull { it.first == cur }?.second)
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
        if (shapeCar) SColours("Car colour", listOf(
            "#F4F5F7" to "White", "#B9BEC8" to "Silver", "#7A808C" to "Grey", "#3E434D" to "Graphite", "#2A2D34" to "Black", "#1E2A4A" to "Navy",
            "#2F6BDF" to "Blue", "#5FB3E8" to "Sky blue", "#1FA39A" to "Teal", "#2E8B57" to "Green", "#6E8B3D" to "Olive", "#F2C230" to "Yellow",
            "#F07C28" to "Orange", "#D7263D" to "Red", "#8E1B2E" to "Burgundy", "#7B4FC9" to "Purple", "#E86FA8" to "Pink", "#C9A27A" to "Champagne", "#6B4A35" to "Brown",
        ), prefs.s(PreferenceKeys.CAR_COLOUR, "#F4F5F7")) { prefs.put(PreferenceKeys.CAR_COLOUR, it) }
        else if (shapeArrow) SColours("Arrow colour", listOf(
            "#F2F2F6" to "White", "#C2C6D0" to "Silver", "#8A909C" to "Grey", "#3E434D" to "Graphite", "#4C8DFF" to "Blue", "#6FD0FF" to "Sky blue",
            "#1FC7A6" to "Teal", "#3DDC84" to "Green", "#B5E34A" to "Lime", "#FFD54A" to "Yellow", "#FF9A3C" to "Orange", "#FF4D5E" to "Red",
            "#FF6FB5" to "Pink", "#A56BFF" to "Purple",
        ), prefs.s(PreferenceKeys.ARROW_COLOUR, "#F2F2F6")) { prefs.put(PreferenceKeys.ARROW_COLOUR, it) }
        SSlider(if (shapeCar) "Car size" else if (loc == "custom") "Picture size" else "Arrow size", prefs.s(PreferenceKeys.ARROW_PERCENT, "240").toFloatOrNull() ?: 240f, 100f..500f, "%", reset = 240f) { prefs.put(PreferenceKeys.ARROW_PERCENT, it.toString()) }
    }
    SGroup("Route line") {
        SToggle(prefs, "Turn arrow on the route", "turnarrows", true, "A white arrow through the next junction or roundabout")
        SToggle(prefs, "Chevrons along the route", "routechevrons", true, "Small arrows along the line when zoomed in")
        SToggle(prefs, "Route ribbon", "ribbon", true, "A soft shadow under the route line, and the route showing faintly through 3D buildings in front of it")
        SToggle(prefs, "Flowing light along the route", "routeflow", true)
        SColours("Alternative route line colour", listOf(
            "#C4D9E6" to "Pale blue-grey (default)", "#A9D4F0" to "Light blue", "#5AB7F5" to "Blue", "#315CBA" to "Dark blue",
            "#A9B4C2" to "Silver grey", "#FFFFFF" to "White", "#42CFA5" to "Mint", "#F4C542" to "Yellow",
            "#FF9A4A" to "Orange", "#F05D6C" to "Red", "#C58AF9" to "Purple", "#F08AC5" to "Pink",
        ), prefs.s(PreferenceKeys.ALT_ROUTE_COLOUR, "#C4D9E6")) { prefs.put(PreferenceKeys.ALT_ROUTE_COLOUR, it) }
    }
}

// ------------------------------------------------------------------ Routes

@Composable
private fun RoutesSection(prefs: Prefs) {
    SGroup("Avoid", footer = "Changes apply to the next route you plan. \"Avoid this road\" on the map only lasts for that trip.") {
        SToggle(prefs, "Avoid toll roads", "avoid_toll", false, if (prefs.b(PreferenceKeys.AVOID_TOLL, false)) "Everywhere" else null)
        // ...or only in some countries: a route that would use a toll road in one of them is planned without toll roads
        if (!prefs.b(PreferenceKeys.AVOID_TOLL, false)) {
            val chosen = prefs.s(PreferenceKeys.AVOID_TOLL_COUNTRIES, "").split(',').filter { it.isNotBlank() }.toSet()
            SItem("Avoid tolls only in", if (chosen.isEmpty()) "Pick countries: a route that would pay a toll in one of them is planned without toll roads" else "A route that would pay a toll in one of these is planned without toll roads")
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((cc, name) in listOf("FR" to "France", "IT" to "Italy", "ES" to "Spain", "PT" to "Portugal", "AT" to "Austria", "CH" to "Switzerland",
                    "SI" to "Slovenia", "HR" to "Croatia", "PL" to "Poland", "CZ" to "Czechia", "HU" to "Hungary", "GR" to "Greece", "IE" to "Ireland", "NO" to "Norway", "US" to "USA")) {
                    Pill(name, cc in chosen, { prefs.put(PreferenceKeys.AVOID_TOLL_COUNTRIES, (if (cc in chosen) chosen - cc else chosen + cc).joinToString(",")) })
                }
            }
        }
        SToggle(prefs, "Avoid motorways", "avoid_motorway", false)
        SToggle(prefs, "Avoid ferries", "avoid_ferry", false)
        SToggle(prefs, "Avoid unpaved roads", "avoid_unpaved", true)
        SToggle(prefs, "Avoid Clean Air Zones and ULEZ", "avoidzones", false, "A zone you start or finish in can't be avoided")
        ZonesItem()
    }
    SGroup("Choosing a route") {
        SToggle(prefs, "Eco routes", "route_eco", false, "Fuel-efficient instead of fastest")
        SToggle(prefs, "Offer faster routes", "fasterroute", true)
        if (prefs.b(PreferenceKeys.FASTER_ROUTE, true)) SToggle(prefs, "Keep other routes on the map", "livealts", true, "Grey lines with +/− minutes you can tap any time. Off shows a \"Faster route found\" card instead")
        SToggle(prefs, "Suggest where you're going", "predict", true, "With the live journey time, including home and work at your usual times")
        SToggle(prefs, "Weather on the route", "routewx", true)
    }
    SGroup("Starting a route") {
        SToggle(prefs, "Auto-start route countdown", PreferenceKeys.AUTO_START_ROUTE, true, "Automatically start navigation when the countdown finishes")
        SSlider("Auto-start countdown", prefs.i(PreferenceKeys.AUTO_START_SECONDS, 10).coerceIn(3, 30).toFloat(), 3f..30f, " s", sub = "How long before navigation starts", steps = 26, reset = 10f) { prefs.put(PreferenceKeys.AUTO_START_SECONDS, it.coerceIn(3, 30)) }
        SToggle(prefs, "Fly along a new route", "cinematic", true, "A 3-second 3D fly-through before the overview. Tap the map to skip")
        SToggle(prefs, "Fly over route button", "flyover", true, "On the route options: a 3D flight along the whole drive before you set off")
    }
}

// ------------------------------------------------------------------ Turn card & lanes

@Composable
private fun GuidanceSection(prefs: Prefs) {
    SGroup("Turn card") {
        SToggle(prefs, "UK road signs on the turn card", "uksigns", true)
        SToggle(prefs, "Living turn card", "livecard", true, "Grows and glows as the turn gets close")
        SToggle(prefs, "Lane guidance on the turn card", "lanestrip", true)
        if (prefs.b(PreferenceKeys.LANE_STRIP, true)) SChoice("Lane guidance style", listOf("hero" to "Lane hero view", "classic" to "Classic strip"), prefs.s(PreferenceKeys.LANE_STYLE, "hero"),
            if (prefs.s(PreferenceKeys.LANE_STYLE, "hero") == "hero") "Before junctions with two or more lanes the turn card shows a big lane picture with yours lit" else "The small lane strip at every junction") { prefs.put(PreferenceKeys.LANE_STYLE, it) }
        SChoice("Next turn on the turn card", listOf("close" to "When close", "always" to "Always", "off" to "Off"), prefs.s(PreferenceKeys.THEN_MODE, "close"),
            when (prefs.s(PreferenceKeys.THEN_MODE, "close")) { "close" -> "Only when the turn after comes straight after (within about 15 seconds)"; "always" -> "The turn after the next one, all the time"; else -> null }) { prefs.put(PreferenceKeys.THEN_MODE, it) }
        if (prefs.s(PreferenceKeys.THEN_MODE, "close") != "off") SChoice("Shown as", listOf("row" to "A line under the card", "icon" to "A small arrow"), prefs.s(PreferenceKeys.THEN_STYLE, "row")) { prefs.put(PreferenceKeys.THEN_STYLE, it) }
    }
    SGroup("Lanes and junctions") {
        SToggle(prefs, "Lanes painted on the map", "lanemap", true)
        SToggle(prefs, "Use Mapbox's lane data on TomTom routes", PreferenceKeys.LANES_MAPBOX, true, "Needs a Mapbox token. Mapbox draws the lanes on the map, so its lane list suits them best. Uses one extra Mapbox request per route")
        SToggle(prefs, "Zoom in on lanes before a junction", "laneview", true)
        SToggle(prefs, "Junction close-up", "junctionview", true, "At slip roads, motorway exits, forks and lane splits the view moves closer and a little flatter for the last 200 m")
        SToggle(prefs, "Flatten the map to 2D at turns", "turn2d", true)
        SToggle(prefs, "Tilt with speed", "speedtilt", true, "Flatter when slow, a longer view at speed")
    }
    SGroup("Trip card") {
        SToggle(prefs, "Route bar on the trip card", "routebar", true, "Traffic, cameras, services and stops, with your progress")
        SToggle(prefs, "Next services card", "servicescard", true)
        SToggle(prefs, "Hazard radar", "hazardradar", false, "The next 2 miles: bends, crossings, schools, bumps, cameras and checks")
    }
    SGroup("Arriving") {
        SToggle(prefs, "Arrival moment", "arrivalfx", true, "The camera swoops to your destination and shows the nearest parking")
        SToggle(prefs, "Parking near your destination", "parkprompt", true, "About a mile before you arrive, car parks close by with the walk from each")
        SToggle(prefs, "Route back", "routeback", true, "On the arrival card: one tap for the way back to where you started")
    }
}


/** What the app knows about clean air zones, with a button to download them again. */
@Composable
private fun ZonesItem() {
    val ctx = LocalContext.current
    val sc = rememberCoroutineScope()
    var st by remember { mutableStateOf(Zones.all(ctx).let { z -> if (z.isEmpty()) "Not downloaded yet" else "${z.size} zones: " + z.joinToString(", ") { it.name }.take(140) }) }
    var busy by remember { mutableStateOf(false) }
    SItem("Clean air zones on this unit", st) {
        SButton(if (busy) "Updating…" else "Update") {
            if (!busy) { busy = true; sc.launch { withContext(Dispatchers.IO) { Zones.fetch(ctx) }; st = Zones.status; busy = false } }
        }
    }
}

// ------------------------------------------------------------------ Alerts

@Composable
private fun AlertsSection(prefs: Prefs, engine: NavEngine) {
    SGroup("Speed") {
        SToggle(prefs, "Warn when over the limit", "speedwarn", true)
        if (prefs.b(PreferenceKeys.SPEEDWARN, true)) SChoice("Warn when over by", listOf("0", "5", "10").map { it to it + if (engine.imperial) " mph" else " km/h" }, prefs.s(PreferenceKeys.TRAFFIC_WARNING, "5")) { prefs.put(PreferenceKeys.TRAFFIC_WARNING, it) }
        SToggle(prefs, "Say when the limit changes", "limitchange", true)
        SToggle(prefs, "Show estimated limits", "limitest", false, "Where no limit is mapped, show the usual one for that kind of road (greyed out). Off shows no sign instead of a guess")
        SToggle(prefs, "Speed cameras", "cameras", true)
        if (prefs.b(PreferenceKeys.CAMERA_WARNINGS, true)) SToggle(prefs, "Off where they're banned", "cambanobey", true, "France, Germany and Switzerland don't allow camera warnings, so they switch off there by themselves")
        SToggle(prefs, "Average speed checks", "avgzones", true, "Shows your average against the limit")
    }
    SGroup("On the road", footer = "Average speed checks, speed bumps, schools and clean air zones come from OpenStreetMap, so some may be missing.") {
        SToggle(prefs, "Roadworks and incidents", "incidents", true)
        SToggle(prefs, "Wrong-way warning", "wrongway", true, "If you turn the wrong way into a one-way street")
        SToggle(prefs, "Speed bumps", "bumps", true)
        SToggle(prefs, "Schools at school-run times", "schools", true)
        SToggle(prefs, "Clean Air Zones and ULEZ", "zonewarn", true)
        SToggle(prefs, "Break reminder after 2 hours", "breakremind", true)
        SToggle(prefs, "Level crossings and fords", "railford", true, "A warning sign and a spoken warning for level crossings and fords on your route")
        SToggle(prefs, "Weather advice", "wxadvice", true, "A quiet note in heavy rain, snow, fog or ice when you're near the limit")
    }
    SGroup("Your reports", footer = "Make a report from the map options button. Reports stay on this unit and fade after a few hours.") {
        SToggle(prefs, "Warn about reports ahead", "policealerts", true)
        SItem("Reports still active", "${engine.reportsActive().size}") { SButton("Clear", danger = true) { engine.clearReports() } }
    }
}

// ------------------------------------------------------------------ Sound & voice

@Composable
private fun SoundSection(prefs: Prefs, engine: NavEngine) {
    val ctx = LocalContext.current
    SGroup("Sound") {
        SChoice("Spoken guidance", listOf("0" to "All", "1" to "Alerts only", "2" to "Off"), engine.soundMode.toString(),
            "Alerts only skips turn-by-turn but still speaks cameras, limits and hazards") { prefs.put(PreferenceKeys.SOUND_MODE, it.toInt()) }
        SToggle(prefs, "Say road names", PreferenceKeys.VOICE_ROADS, true, "Off gives just the direction: \"In 200 metres, turn left\", without the road or the places on the signs")
        SToggle(prefs, "Smarter directions", "smartvoice", true, "Junction numbers and sign destinations, the next move when it comes straight after, and fewer early prompts on roads you drive a lot")
        SChoice("Voice channel", listOf("nav" to "Navigation", "media" to "Media"), prefs.s(PreferenceKeys.VOICE_CHANNEL, "nav"),
            "If you hear the beeps but not the spoken directions, choose Media: some head units don't play the navigation channel") { prefs.put(PreferenceKeys.VOICE_CHANNEL, it) }
        SItem("Voice and language", "Uses the system text-to-speech voice") { SButton("Open") { openSystemSettings(ctx, "com.android.settings.TTS_SETTINGS") } }
        SItem("Volume") { SButton("Open") { openSystemSettings(ctx, Settings.ACTION_SOUND_SETTINGS) } }
    }
}


// ------------------------------------------------------------------ Places & trips

@Composable
private fun PlaceItem(label: String, p: Place?, onChange: () -> Unit, onClear: () -> Unit) {
    SItem(label, p?.let { it.name + (if (it.sub.isNotBlank()) ", " + it.sub else "") } ?: "Not set") {
        SButton(if (p != null) "Change" else "Set", primary = true, onClick = onChange)
        if (p != null) SButton("Clear", danger = true, onClick = onClear)
    }
}

@Composable
private fun PlacesSection(engine: NavEngine) {
    val pl = engine.places
    var settingSlot by remember { mutableStateOf<String?>(null) }
    // (read again whenever the saved places change, so a change shows straight away)
    val slotsRaw = engine.prefs.s(PreferenceKeys.PLACE_HOME, "") + engine.prefs.s("place_work", "")
    SGroup("Saved places", footer = "Home and Work can also be set from the pills under the search bar, or the house or briefcase on the route options.") {
        PlaceItem("Home", remember(slotsRaw) { pl.slot("home") }, { settingSlot = "home" }) { pl.clearSlot("home") }
        PlaceItem("Work", remember(slotsRaw) { pl.slot("work") }, { settingSlot = "work" }) { pl.clearSlot("work") }
    }
    settingSlot?.let { s -> AddPlaceDialog(engine, null, slot = s) { settingSlot = null } }
    // your own labelled places: rename, reorder or remove
    val raw = engine.prefs.s(PreferenceKeys.PLACES_CUSTOM, "")
    val mine = remember(raw) { pl.customs() }
    var renaming by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    SGroup("Your places", footer = "Shown as pills under the search bar, after Home and Work. Add one with the + pill there, or the bookmark on the route options.") {
        if (mine.isEmpty()) SNote("No places of your own yet.")
        mine.forEachIndexed { i, lp ->
            if (renaming == lp.label) {
                var t by remember(lp.label) { mutableStateOf(lp.label) }
                SItem("Rename ${lp.label}") {
                    SField(t, { t = it.take(24) }, width = 220.dp)
                    SButton("Save", primary = true) { pl.renameCustom(lp.label, t); renaming = null }
                }
            } else SItem(lp.label, lp.place.name + if (lp.place.sub.isNotBlank()) ", " + lp.place.sub else "") {
                if (i > 0) SButton("↑") { pl.moveCustom(lp.label, -1) }
                SButton("Rename") { renaming = lp.label }
                SButton("Remove", danger = true) { pl.removeCustom(lp.label) }
            }
        }
        SItem("Add a place") { SButton("Add", primary = true) { adding = true } }
    }
    if (adding) AddPlaceDialog(engine, null) { adding = false }
    val favRaw = engine.prefs.s(PreferenceKeys.PLACES_FAVOURITES, "")
    val favs = remember(favRaw) { pl.favourites() }
    var renamingFav by remember { mutableStateOf<Place?>(null) }
    var slotFor by remember { mutableStateOf<Place?>(null) }
    SGroup("Favourites") {
        if (favs.isEmpty()) SNote("No favourites yet. Tap the star on a destination to add one.")
        favs.forEach { f ->
            when {
                renamingFav == f -> {
                    var t by remember(f) { mutableStateOf(f.name) }
                    SItem("Rename ${f.name}") {
                        SField(t, { t = it.take(40) }, width = 220.dp)
                        SButton("Save", primary = true) { pl.renameFavourite(f, t); renamingFav = null }
                    }
                }
                slotFor == f -> SItem("Make ${f.name}…") {
                    SButton("Home", primary = true) { pl.setSlot("home", f); slotFor = null; engine.toast("${f.name} is now Home", 2500) }
                    SButton("Work", primary = true) { pl.setSlot("work", f); slotFor = null; engine.toast("${f.name} is now Work", 2500) }
                    SButton("Cancel") { slotFor = null }
                }
                else -> SItem(f.name, f.sub) {
                    SButton("Rename") { renamingFav = f }
                    SButton("Make Home / Work") { slotFor = f }
                    SButton("Remove", danger = true) { pl.toggleFavourite(f) }
                }
            }
        }
        if (favs.size > 1) SItem("Remove every favourite") { SButton("Remove all", danger = true) { pl.clearFavourites() } }
    }
    SGroup("History", footer = "Swipe a place sideways on the Saved & recent list to take it off.") {
        SToggle(engine.prefs, "Saved & recent under the search bar", "quicklist", true, "The list of favourites and recent places on the map")
        SItem("Recent destinations", "${pl.recents().size} recent, plus places remembered for searching without signal") {
            SButton("Clear", danger = true) { pl.clearHistory(); engine.toast("History cleared", 2000) }
        }
    }
    TripsGroup(engine)
}

@Composable
private fun TripsGroup(engine: NavEngine) {
    var shown by remember { mutableStateOf(engine.trips.all()) }
    val fmt = java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.UK)
    val k = if (engine.imperial) 2.23694 else 3.6
    val unit = if (engine.imperial) "mph" else "km/h"
    SGroup("Trip log") {
        if (shown.isEmpty()) SNote("No trips yet. Every drive is recorded here when you park.")
        else SItem("${shown.size} trips", Geo.fmtDist(shown.sumOf { it.dist }, engine.imperial) + " in all") { SButton("Clear", danger = true) { engine.trips.clear(); shown = emptyList() } }
        shown.take(50).forEach { t ->
            val mins = ((t.end - t.start) / 60000L).toInt()
            SItem(
                fmt.format(java.util.Date(t.start)) + (if (t.to.isNotBlank()) " → ${t.to}" else ""),
                "${Geo.fmtDur(mins * 60.0)} · moving ${Geo.fmtDur(t.moving.toDouble())} · top ${Math.round(t.maxSpeed * k)} $unit",
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
        SItem("Default zoom", "When you're not on a route. Auto zoom still pulls out at speed and moves in at junctions, round your setting. Or zoom with + and − on the map and tap \"Make this my default\"") {
            if (kotlin.math.abs(v) > 0.01f) SButton("Reset") { v = 0f; save(0f) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Further out", color = c.sub, fontSize = 13.sp)
            androidx.compose.material3.Slider(value = v, onValueChange = { v = (Math.round(it * 4f) / 4f) }, onValueChangeFinished = { save(v) },
                valueRange = -2f..2f, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            Text("Closer", color = c.sub, fontSize = 13.sp)
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
    SGroup("Send places from your phone", footer = "Pair once: on the head unit, tap Pair a phone and type its code into Horizon Nav on your phone (in this same place). Then \"Send to car\" on the phone's route screen sends the place to the head unit, which starts it the next time it's on (within 12 hours). Places go through ntfy.sh, a free message relay, scrambled with your code so only your phone and car can read them.") {
        when {
            code.isNotBlank() && role == "car" -> {
                SItem("This is the car", "Paired. Code ${com.headunit.launcher.CarLink.pretty(code)}: type it on another phone to add it too") {
                    SButton("Unpair", danger = true) { prefs.put(PreferenceKeys.LINK_CODE, ""); prefs.put(PreferenceKeys.LINK_ROLE, ""); prefs.put(PreferenceKeys.LINK_LAST, "") }
                }
                val qr = remember(code) { donateQr(com.headunit.launcher.CarLink.pretty(code), 300) }
                if (qr != null) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(110.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(6.dp)) {
                        androidx.compose.foundation.Image(qr, "The pairing code as a QR code", Modifier.size(98.dp), filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
                    }
                    Text(com.headunit.launcher.CarLink.pretty(code), color = LocalVw.current.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
            code.isNotBlank() && role == "phone" -> SItem("This is your phone", "Paired with your car (${com.headunit.launcher.CarLink.pretty(code)}). \"Send to car\" is on the route screen, next to Save") {
                SButton("Unpair", danger = true) { prefs.put(PreferenceKeys.LINK_CODE, ""); prefs.put(PreferenceKeys.LINK_ROLE, "") }
            }
            else -> {
                SItem("On the head unit", "Makes a code to type on your phone") {
                    SButton("Pair a phone", primary = true) { prefs.put(PreferenceKeys.LINK_CODE, com.headunit.launcher.CarLink.newCode()); prefs.put(PreferenceKeys.LINK_ROLE, "car"); prefs.put(PreferenceKeys.LINK_LAST, "") }
                }
                SItem("On your phone", "Type the code the head unit shows") { SButton("Enter code") { typing = true } }
                if (typing) SFieldItem("Car's code", "", "Like K7P-4QX-M2D", placeholder = "K7P-4QX-M2D") { t ->
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
    SGroup("Share your ETA", footer = "The share button on the trip card texts your arrival time and a map link. On a head unit without a SIM it opens your messaging app instead.") {
        val pickEta = rememberEtaContactPicker(prefs)
        SItem("Send it to", prefs.s(PreferenceKeys.ETA_NAME, "").ifBlank { "No one chosen yet" }) {
            SButton(if (prefs.s(PreferenceKeys.ETA_NUMBER, "").isBlank()) "Choose" else "Change") { pickEta() }
            if (prefs.s(PreferenceKeys.ETA_NUMBER, "").isNotBlank()) SButton("Clear", danger = true) { prefs.put(PreferenceKeys.ETA_NUMBER, ""); prefs.put(PreferenceKeys.ETA_NAME, "") }
        }
    }
    SGroup("Places from other apps", footer = "Share a place to Horizon Nav from Google Maps, WhatsApp, Contacts or a web page and it plans the route. To open Google Maps links here straight away, tap Open by default and add the links.") {
        SItem("Google Maps links") {
            SButton("Open by default") {
                if (android.os.Build.VERSION.SDK_INT >= 31) openSystemSettings(ctx, Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg)
                else openSystemSettings(ctx, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            }
        }
    }
}

// ------------------------------------------------------------------ Fuel & charging

@Composable
private fun FuelSection(prefs: Prefs) {
    val ctx = LocalContext.current
    val sc = rememberCoroutineScope()
    SGroup("Fuel prices", footer = "Live prices come from the government's Fuel Finder scheme. It's free: on gov.uk search \"Fuel Finder\", register as an information recipient with a GOV.UK One Login, then paste the client ID and secret here. Without them the fuel list still works, just without prices.") {
        SChoice("Your fuel", listOf("E10" to "Unleaded", "E5" to "Super", "B7" to "Diesel", "E85" to "Bioethanol (E85)", "EV" to "Electric"), prefs.s(PreferenceKeys.FUEL_TYPE, "E10")) { prefs.put(PreferenceKeys.FUEL_TYPE, it) }
        SToggle(prefs, "Suggest a fuel stop on long trips", "smartfuel", true, if (prefs.s(PreferenceKeys.FUEL_TYPE, "E10") == "EV") "Over about 95 miles, a charging point along the way with the shortest detour" else "Over about 95 miles, the best-value station along the way")
        SFieldItem("Client ID", prefs.s(PreferenceKeys.FFID, "")) { prefs.put(PreferenceKeys.FFID, it.trim()) }
        SFieldItem("Client secret", prefs.s(PreferenceKeys.FFSECRET, ""), password = true) { prefs.put(PreferenceKeys.FFSECRET, it.trim()) }
        var st by remember { mutableStateOf(FuelFinder.status) }
        var busy by remember { mutableStateOf(false) }
        SItem("Prices", st.ifBlank { if (FuelFinder.configured(prefs)) "Not downloaded yet" else "Add your details first" }) {
            SButton(if (busy) "Updating…" else "Update now", primary = true) {
                if (!busy && FuelFinder.configured(prefs)) {
                    busy = true; st = "Downloading stations and prices (the first time takes a minute)…"
                    sc.launch {
                        try { withContext(Dispatchers.IO) { FuelFinder.refresh(ctx, prefs, force = true) } } catch (e: Exception) { }
                        st = FuelFinder.status; busy = false
                    }
                }
            }
        }
        SItem("Spotted a wrong price?") { SButton("Report it") { openSystemSettings(ctx, android.content.Intent.ACTION_VIEW, android.net.Uri.parse(FuelFinder.REPORT_URL)) } }
    }
    SGroup("Fuel prices outside the UK", footer = "France and Austria work by themselves, from their governments' own price services. Germany needs a free key from creativecommons.tankerkoenig.de (Tankerkönig). Elsewhere the fuel list still works, without prices.") {
        SFieldItem("Tankerkönig key (Germany)", prefs.s(PreferenceKeys.TANKERKOENIG_KEY, ""), placeholder = "Paste the key", password = true) { prefs.put(PreferenceKeys.TANKERKOENIG_KEY, it.trim()) }
    }
    if (prefs.s(PreferenceKeys.FUEL_TYPE, "E10") == "EV") SGroup("Your electric car", footer = "With the efficiency and price filled in, each route shows roughly what it will cost to charge for. The efficiency and battery size also give the battery estimate on the trip card and the charging stop suggestions (set your battery level on the route options).") {
        SFieldItem("Efficiency (miles per kWh)", prefs.s(PreferenceKeys.EVMPK, ""), placeholder = "e.g. 3.5", number = true) { s -> prefs.put(PreferenceKeys.EVMPK, s.filter { it.isDigit() || it == '.' }.take(5)) }
        SFieldItem("Usable battery (kWh)", prefs.s(PreferenceKeys.EV_BATTERY_KWH, ""), "For the battery estimate and charging stops (60 if left blank)", placeholder = "e.g. 64", number = true) { s -> prefs.put(PreferenceKeys.EV_BATTERY_KWH, s.filter { it.isDigit() || it == '.' }.take(5)) }
        SFieldItem("Electricity price (pence per kWh)", prefs.s(PreferenceKeys.EVPPK, ""), placeholder = "e.g. 28", number = true) { s -> prefs.put(PreferenceKeys.EVPPK, s.filter { it.isDigit() || it == '.' }.take(6)) }
    } else SGroup("Fuel cost on the route options", footer = "With both filled in, each route shows roughly what it will cost in fuel.") {
        SFieldItem("Fuel economy (miles per gallon)", prefs.s(PreferenceKeys.FUELMPG, ""), placeholder = "e.g. 45", number = true) { s -> prefs.put(PreferenceKeys.FUELMPG, s.filter { it.isDigit() || it == '.' }.take(5)) }
        SFieldItem("Fuel price (pence per litre)", prefs.s(PreferenceKeys.FUELPPL, ""), placeholder = "e.g. 139.9", number = true) { s -> prefs.put(PreferenceKeys.FUELPPL, s.filter { it.isDigit() || it == '.' }.take(6)) }
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
    var state by remember { mutableStateOf(if (OfflinePlaces.installed(ctx)) "Installed (%.1f MB)".format(OfflinePlaces.sizeMb(ctx)) else "Not downloaded") }
    var busy by remember { mutableStateOf(false) }
    SGroup("Place names", footer = "A small download of towns and villages in $where, so you can search for them without signal even without the full pack.") {
        SItem("Towns and villages", state) {
            SButton(if (busy) "Downloading…" else if (OfflinePlaces.installed(ctx)) "Update" else "Download", primary = !OfflinePlaces.installed(ctx)) {
                if (!busy) {
                    busy = true; state = "Downloading (needs signal)…"
                    sc.launch {
                        try {
                            val n = withContext(Dispatchers.IO) { OfflinePlaces.download(ctx, cc) }
                            state = "Installed: $n places"; engine.toast("Offline places ready", 3000)
                        } catch (e: Exception) { state = "Failed: ${e.message}" } finally { busy = false }
                    }
                }
            }
            if (OfflinePlaces.installed(ctx) && !busy) SButton("Remove", danger = true) { OfflinePlaces.delete(ctx); state = "Not downloaded" }
        }
    }
}

// ------------------------------------------------------------------ Screen & sizes

@Composable
private fun ScreenSection(prefs: Prefs, engine: NavEngine) {
    SGroup("Screen") {
        SSlider("Brightness", prefs.i(PreferenceKeys.BRIGHT, 100).toFloat(), 10f..100f, "%", sub = "100% leaves it to the system", live = true) { prefs.put(PreferenceKeys.BRIGHT, it) }
        SToggle(prefs, "Dim the screen at night", "dimnight", false)
        SToggle(prefs, "Keep the screen on", "keepon", true)
        val screenConfig = androidx.compose.ui.platform.LocalConfiguration.current
        val autoPercent = recommendedMainUiScale(screenConfig.screenWidthDp, screenConfig.screenHeightDp)
        val autoScale = prefs.b(PreferenceKeys.UI_AUTO_SCALE, prefs.s(PreferenceKeys.UI_SCALE, "").isBlank())
        SItem(
            "Automatically scale UI",
            if (autoScale) "Uses screen size and shape (currently $autoPercent%)" else "Manual text and button size is enabled"
        ) {
            Switch(checked = autoScale, onCheckedChange = { enabled ->
                if (!enabled) prefs.put(PreferenceKeys.UI_SCALE, autoPercent)
                prefs.put(PreferenceKeys.UI_AUTO_SCALE, enabled)
            })
        }
        SSlider(
            "Text and button size",
            (if (autoScale) autoPercent else prefs.i(PreferenceKeys.UI_SCALE, autoPercent)).toFloat(),
            60f..140f, "%", sub = if (autoScale) "Move the slider to switch to a manual size" else "Adjust manually, or turn automatic scaling back on", steps = 15, reset = 100f
        ) {
            prefs.put(PreferenceKeys.UI_AUTO_SCALE, false)
            prefs.put(PreferenceKeys.UI_SCALE, it)
        }
        var mainAdvanced by rememberSaveable { mutableStateOf(false) }
        SItem(if (mainAdvanced) "Advanced display sizes" else "Advanced display sizes", "Adjust individual elements without changing the overall scale") {
            SButton(if (mainAdvanced) "Hide" else "Show") { mainAdvanced = !mainAdvanced }
        }
        if (mainAdvanced) {
            SSlider("Card size", prefs.i(PreferenceKeys.MAIN_CARD_SIZE, 100).toFloat(), 70f..150f, "%", sub = "Turn, trip and route cards", steps = 15, reset = 100f) { prefs.put(PreferenceKeys.MAIN_CARD_SIZE, it) }
            SSlider("Button size", prefs.i(PreferenceKeys.MAIN_BUTTON_SIZE, 100).toFloat(), 70f..150f, "%", sub = "On-screen controls", steps = 15, reset = 100f) { prefs.put(PreferenceKeys.MAIN_BUTTON_SIZE, it) }
            SSlider("Text size", prefs.i(PreferenceKeys.MAIN_TEXT_SIZE, 100).toFloat(), 70f..150f, "%", sub = "Independent text scaling", steps = 15, reset = 100f) { prefs.put(PreferenceKeys.MAIN_TEXT_SIZE, it) }
            SSlider("Navigation arrow size", prefs.i(PreferenceKeys.MAIN_ARROW_SIZE, 100).toFloat(), 60f..180f, "%", sub = "Map position arrow", steps = 11, reset = 100f) { prefs.put(PreferenceKeys.MAIN_ARROW_SIZE, it) }
            SSlider("Road name size", prefs.i(PreferenceKeys.ROAD_NAME_SIZE, 100).toFloat(), 70f..200f, "%", sub = "The name of the road you're on, under your arrow", steps = 12, reset = 100f) { prefs.put(PreferenceKeys.ROAD_NAME_SIZE, it) }
        }

    }
    SGroup("While driving") {
        SToggle(prefs, "Your speed next to the limit", "mapspeed", true)
        SToggle(prefs, "Speedometer ring", "speedring", true, "Fills towards the limit, turning amber then red")
        SSlider("Speed limit and speedometer size", prefs.i(PreferenceKeys.SPEED_SIZE, 100).toFloat(), 50f..200f, "%", sub = "On the phone and the car screen", steps = 29, reset = 100f) { prefs.put(PreferenceKeys.SPEED_SIZE, it) }
        SToggle(prefs, "Road and heading when driving without a route", "cruise", true)
        SToggle(prefs, "Keep the arrow on the road with no route", "snaproad", true)
        SToggle(prefs, "Dim the map in tunnels", "tunneldim", true)
        SToggle(prefs, "Fewer buttons while driving", "drivesimple", true)
        SToggle(prefs, "Lock typing while driving", "drivelock", true, "Voice search still works")
    }
    SGroup("Units") {
        SChoice("Distance and speed", listOf("mi" to "Miles", "km" to "Kilometres"), if (engine.imperial) "mi" else "km") { prefs.put(PreferenceKeys.DISTANCE_UNIT, it) }
        SChoice("Temperature", listOf("C" to "°C", "F" to "°F"), if (isImperial(prefs)) "F" else "C") { prefs.put(PreferenceKeys.TEMPERATURE_UNIT, it) }
        SChoice("Driving side", listOf("" to "Automatic", "rhd" to "Right-hand drive", "lhd" to "Left-hand drive"), prefs.s(PreferenceKeys.DRIVE, ""),
            when (prefs.s(PreferenceKeys.DRIVE, "")) {
                "rhd" -> "Driver on the right (UK): cards on the left, buttons on the right"
                "lhd" -> "Driver on the left (US, Europe): the screen is mirrored, cards on the right"
                else -> "Follows the country you're in" + (if (engine.mirrored) ": left-hand drive, screen mirrored" else ": right-hand drive")
            }) { prefs.put(PreferenceKeys.DRIVE, it) }
    }
}

// ------------------------------------------------------------------ Android Auto

@Composable
private fun AndroidAutoSection(prefs: Prefs) {
    val ctx = LocalContext.current
    // Android Auto only lists apps that weren't installed from Play when its own "Unknown sources" is on, and some phones hide them
    // anyway: the steps that get it showing, with shortcuts
    SGroup("Car screen", footer = "If Horizon Nav doesn't appear in Android Auto: 1. Open Android Auto's settings, tap the version number at the bottom about 10 times, then in the top-right menu open Developer settings and turn on Unknown sources. 2. Back in Android Auto's settings, open Customise launcher and make sure Horizon Nav is ticked. 3. Force stop Android Auto (App info below), unplug and plug back in. If it still doesn't show, your phone's Android Auto only lists apps installed by the Play Store: install the APK through an installer that sets Play as the source (for example \"adb install -i com.android.vending\", or an app like AAAD or Install with Options).") {
        val gh = "com.google.android.projection.gearhead"
        val ghUri = android.net.Uri.parse("package:$gh")
        val hasAa = remember { try { ctx.packageManager.getPackageInfo(gh, 0); true } catch (e: Exception) { false } }
        val source = remember {
            try {
                val inst = if (android.os.Build.VERSION.SDK_INT >= 30) ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName
                    else @Suppress("DEPRECATION") ctx.packageManager.getInstallerPackageName(ctx.packageName)
                when (inst) {
                    "com.android.vending" -> "Google Play (Android Auto lists it without any extra steps)"
                    null -> "Unknown (installed from a file): needs Unknown sources in Android Auto"
                    ctx.packageName -> "Horizon Nav's own updater: needs Unknown sources in Android Auto"
                    else -> "$inst: needs Unknown sources in Android Auto"
                }
            } catch (e: Exception) { "Unknown" }
        }
        SSlider("Overall size on the car screen", prefs.i(PreferenceKeys.CARSCALE, 100).toFloat(), 40f..300f, "%", sub = "Base scale for the whole overlay", steps = 25, reset = 100f) { prefs.put(PreferenceKeys.CARSCALE, it) }
        SSlider("Speedometer and speed-limit sign size", prefs.i(PreferenceKeys.CAR_SPEED_SIZE, 100).toFloat(), 40f..180f, "%", sub = "Automatically shrinks further if the available screen area is too small", reset = 100f) { prefs.put(PreferenceKeys.CAR_SPEED_SIZE, it) }
        var carAdvanced by rememberSaveable { mutableStateOf(false) }
        SItem("Advanced car-screen sizes", "Fine-tune individual elements independently") {
            SButton(if (carAdvanced) "Hide" else "Show") { carAdvanced = !carAdvanced }
        }
        if (carAdvanced) {
            SSlider("Card size", prefs.i(PreferenceKeys.CAR_CARD_SIZE, 100).toFloat(), 70f..150f, "%", sub = "Turn, trip and route cards", steps = 16, reset = 100f) { prefs.put(PreferenceKeys.CAR_CARD_SIZE, it) }
            SSlider("Button size", prefs.i(PreferenceKeys.CAR_BUTTON_SIZE, 100).toFloat(), 70f..150f, "%", sub = "Round controls and tappable buttons", steps = 16, reset = 100f) { prefs.put(PreferenceKeys.CAR_BUTTON_SIZE, it) }
            SSlider("Text size", prefs.i(PreferenceKeys.CAR_TEXT_SIZE, 100).toFloat(), 70f..150f, "%", sub = "Independent of card and button sizing", steps = 16, reset = 100f) { prefs.put(PreferenceKeys.CAR_TEXT_SIZE, it) }
            SSlider("Navigation arrow size", prefs.i(PreferenceKeys.CAR_ARROW_SIZE, 100).toFloat(), 60f..180f, "%", reset = 100f) { prefs.put(PreferenceKeys.CAR_ARROW_SIZE, it) }
            SSlider("Road name size", prefs.i(PreferenceKeys.CAR_ROAD_NAME_SIZE, 100).toFloat(), 70f..200f, "%", sub = "The name of the road you're on, under your arrow", steps = 12, reset = 100f) { prefs.put(PreferenceKeys.CAR_ROAD_NAME_SIZE, it) }
        }
        SItem("Installed by", source)
        SItem("Android Auto settings", if (hasAa) null else "Android Auto isn't installed on this phone") {
            if (hasAa) SButton("Open") {
                val i = ctx.packageManager.getLaunchIntentForPackage(gh)
                try {
                    if (i != null) ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) else openSystemSettings(ctx, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ghUri)
                } catch (e: Exception) { openSystemSettings(ctx, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ghUri) }
            }
        }
        if (hasAa) SItem("Android Auto app info", "To force stop it after changing its settings") {
            SButton("App info") { openSystemSettings(ctx, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ghUri) }
        }
        // the most asked question: Android Auto only lists apps from Google Play unless told otherwise
        if (hasAa) SItem("Not showing in Android Auto?", "1. In Android Auto's settings, tap Version repeatedly to unlock Developer settings, then turn on Unknown sources. " +
            "2. Force stop Android Auto (App info above) and reconnect. 3. If it still isn't listed, install Horizon Nav with the free AAEnabler app, which most people need. " +
            "Voice requests through Android Auto's own microphone may not reach apps from outside Google Play: use the app's search button instead.") {
            SButton("AAEnabler") {
                try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/malebuffy/AAEnabler")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (e: Exception) { android.widget.Toast.makeText(ctx, "No browser on this device: search for AAEnabler on GitHub", android.widget.Toast.LENGTH_LONG).show() }
            }
        }
    }
}

// ------------------------------------------------------------------ Performance

@Composable
private fun PerformanceSection(prefs: Prefs) {
    SGroup("Smoothness", footer = "Lower these if the map feels slow or the head unit gets warm. Sharpness and fast drawing apply after restarting the app.") {
        SChoice("Map sharpness", listOf("100" to "Full", "80" to "Balanced", "65" to "Light", "50" to "Lowest"), prefs.s(PreferenceKeys.MAP_RESOLUTION, "80")) { prefs.put(PreferenceKeys.MAP_RESOLUTION, it) }
        SChoice("Frame rate", listOf("auto" to "Auto", "20" to "20", "30" to "30", "45" to "45", "60" to "60"), prefs.s(PreferenceKeys.MAP_FPS, "auto"),
            if (prefs.s(PreferenceKeys.MAP_FPS, "auto") == "auto") "Starts at 30 and steps down when the head unit can't keep up, so the map moves evenly" else "Lower is lighter on the head unit") { prefs.put(PreferenceKeys.MAP_FPS, it) }
        SToggle(prefs, "Fast map drawing", "mapfast", true)
        // (the old "Light map" switch is now Settings > Map > Map detail)
        SToggle(prefs, "Performance meter", "perfmeter", false, "A line at the top of the map: frames drawn, camera moves and pauses. Handy for finding what slows the map")
    }
    SGroup("Demo") {
        SToggle(prefs, "Demo drive button", "demo", false, "Plays a route back so you can try the guidance while parked")
    }
}

// ------------------------------------------------------------------ Keys & accounts

@Composable
private fun KeysSection(prefs: Prefs, engine: NavEngine) {
    SGroup("Search") {
        SChoice("Search results", searchCountryOptions(engine), searchCountryValue(prefs)) { prefs.put(PreferenceKeys.COUNTRY, it) }
        if (engine.hasKey && engine.hasMapbox) SChoice("Search with", listOf("auto" to "TomTom", "mapbox" to "Mapbox"), if (prefs.s(PreferenceKeys.SEARCHWITH, "auto") == "mapbox") "mapbox" else "auto",
            "TomTom's search is the one Horizon Nav uses: best for UK addresses and house numbers") { prefs.put(PreferenceKeys.SEARCHWITH, it) }
        else if (!engine.hasKey) SNote("For the most accurate address search, add a free TomTom key below.")
    }
    SGroup("Map accounts", footer = "Search also finds places you saved or went to before. With no signal, routes, the map and search come from the offline pack.") {
        SFieldItem("TomTom API key", prefs.s(PreferenceKeys.API_KEY, ""), if (BuildConfig.TOMTOM_API_KEY.isNotBlank() && prefs.s(PreferenceKeys.API_KEY, "").isBlank()) "Using the key built into this app unless you paste another" else "Optional: gives the most accurate address search, and a backup for routes and traffic. Free from developer.tomtom.com",
            placeholder = "Paste a key") { prefs.setApiKey(it) }
        SFieldItem("Mapbox access token", prefs.s(PreferenceKeys.MAPBOX_TOKEN, ""), "Use an active public token starting pk. (never sk.). If the map reports 401, open account.mapbox.com > Tokens, copy an active token, and check it has styles:read and fonts:read permissions.",
            placeholder = "Paste a token") { prefs.put(PreferenceKeys.MAPBOX_TOKEN, com.headunit.launcher.MapboxRest.clean(it)) }
    }
    if (engine.hasKey && engine.hasMapbox) SGroup("Routes, search and traffic", footer = "Auto uses Mapbox while the Mapbox map is showing, and TomTom otherwise. If the one in use stops working (its daily limit, a refused key, an outage), the other takes over by itself for a while.") {
        SChoice("Use", listOf("auto" to "Auto", "tomtom" to "TomTom", "mapbox" to "Mapbox"), prefs.s(PreferenceKeys.DATAPROV, "auto")) { prefs.put(PreferenceKeys.DATAPROV, it) }
    }
}

// ------------------------------------------------------------------ About & backup

@Composable
private fun AboutSection(prefs: Prefs, engine: NavEngine) {
    val ctx = LocalContext.current
    val pkg = android.net.Uri.parse("package:" + ctx.packageName)
    var donating by remember { mutableStateOf(false) }
    if (donating) DonateDialog(LocalVw.current.dark) { donating = false }
    SGroup("Support Horizon Nav") {
        SItem("Donate", "Free, no ads. Donations help keep it going (PayPal)") { SButton("Donate", primary = true) { donating = true } }
    }
    SGroup("Horizon Nav") {
        SItem("Version", BuildConfig.VERSION_NAME)
        if (com.headunit.launcher.Updater.enabled) {
            SItem("Check for updates", engine.updateStatus.ifBlank { null }) {
                val u = engine.appUpdate
                if (u != null && !engine.updateBusy) SButton("Update to ${u.version}", primary = true) { engine.installAppUpdate() }
                else SButton(if (engine.updateBusy) "Updating…" else "Check now") { engine.checkForUpdate(manual = true) }
            }
            SToggle(prefs, "Look for updates by itself", "updcheck", true, "When the app opens and there's a connection (at most every half hour)")
        }
        SItem("Setup", "Go through the first-run steps again: map key, home and work, offline maps, permissions") { SButton("Run setup") { prefs.put(PreferenceKeys.SETUP_DONE, false); prefs.put(PreferenceKeys.FORCESETUP, true) } }
        var feedback by remember { mutableStateOf(false) }
        SItem("Feedback or a problem", "Sends a report with the app version, your unit and the last crash, so it's quick to fix") { SButton("Report", primary = true) { feedback = true } }
        if (feedback) FeedbackDialog(engine) { feedback = false }
    }
    SGroup("Permissions", footer = "While a route is running, guidance carries on when you switch apps and a notification shows. Allow notifications so it can appear.") {
        val hasLoc = ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        SItem("Location", if (hasLoc) "Allowed" else "Not allowed: navigation needs it") {
            SButton("App permissions", primary = !hasLoc) { openSystemSettings(ctx, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg) }
        }
        SItem("Location services") { SButton("Open") { openSystemSettings(ctx, Settings.ACTION_LOCATION_SOURCE_SETTINGS) } }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(prefs.exportJson().toByteArray()) }
                true
            } catch (e: Exception) { false }
            android.widget.Toast.makeText(ctx, if (ok) "Backup saved" else "Could not save the backup", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val msg = try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                "Restored ${prefs.importJson(text)} settings"
            } catch (e: Exception) { "That file is not a valid backup" }
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    SGroup("Backup", footer = "Includes settings and saved places, not the API key or the offline pack. A backup from Horizon Launcher can be restored here too.") {
        SItem("Settings and saved places") {
            SButton("Back up") { exporter.launch("horizon-nav-backup.json") }
            SButton("Restore") { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }
        }
    }
    val cf = java.io.File(ctx.filesDir, "lastcrash.txt")
    var rep by remember { mutableStateOf(if (cf.exists()) cf.readText() else "") }
    if (rep.isNotBlank()) SGroup("Problem report", footer = "The app closed unexpectedly last time. Send a photo of this so it can be fixed.") {
        Text(rep.take(900), color = LocalVw.current.text, fontSize = 10.sp, modifier = Modifier.padding(vertical = 6.dp))
        SItem("Clear the report") { SButton("Clear") { cf.delete(); rep = "" } }
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
    return listOf("auto" to (if (here.isNotBlank()) "${countryName(here)} only" else "My country only"), "" to "Anywhere")
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
            Text("Feedback or a problem", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("What happened, or what would you like? Where you were and what you tapped helps.", color = c.sub, fontSize = 14.sp)
            SField(note, { note = it.take(600) }, placeholder = "e.g. Rerouted the wrong way near Southampton", width = 516.dp, fill = true)
            Text("Sent with it: the app version, your head unit, the main settings, your downloaded areas and the last crash (no places or API keys).", color = c.sub, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                SButton("Close") { onClose() }
                SButton("Copy") {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("Horizon Nav report", engine.feedbackReport(note)))
                    engine.toast("Report copied: paste it into a message or a Reddit comment", 3500)
                }
                SButton("Send on GitHub", primary = true) {
                    val title = note.lineSequence().firstOrNull()?.take(80)?.ifBlank { null } ?: "Report from Horizon Nav ${com.headunit.launcher.BuildConfig.VERSION_NAME}"
                    // a link has a length limit, so a very long report is cut down (Copy always has all of it)
                    val body = engine.feedbackReport(note).take(5000)
                    val url = "https://github.com/${BuildConfig.UPDATE_REPO}/issues/new?title=" + android.net.Uri.encode(title) + "&body=" + android.net.Uri.encode(body)
                    try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (e: Exception) { engine.toast("No browser on this unit: use Copy instead", 3500) }
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
        Triple("mapbox", "Mapbox", listOf(0xFFEDEFE9, 0xFF9CCBF0, 0xFFC6E4B4, 0xFFFFFFFF, 0xFFDCDCE0)),
        Triple("horizon", "Horizon", listOf(0xFFF4F2EE, 0xFFAADAF5, 0xFFCDEBC4, 0xFFFFFFFF, 0xFFE7E5E1)),
        Triple("faded", "Faded", listOf(0xFFF1F1F1, 0xFFD9E2EA, 0xFFE2E8DE, 0xFFFFFFFF, 0xFFE6E6E6)),
        Triple("grey", "Grey", listOf(0xFFE8E8E8, 0xFFCFCFCF, 0xFFDADADA, 0xFFFFFFFF, 0xFFD4D4D4)),
    )
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SItem("Colours", "Horizon: Mapbox's map in the app's own soft colours. Day colours shown; each has a night version")
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
