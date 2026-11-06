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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.headunit.launcher.CustomLook
import com.headunit.launcher.PreferenceKeys
import com.headunit.launcher.Prefs

/** The settings' map preview's light ("day", "night" or "now"): the Custom theme's Day / Night tabs set it, so you see what you edit. */
object PreviewLight {
    var mode by mutableStateOf("now")
}

private val palette = listOf(
    "#0F1424", "#1F2840", "#2A2F4A", "#33405F", "#3A5590", "#4C8DFF", "#7FA6FF", "#C6D4FF",
    "#14304F", "#2C4A42", "#3F6B4F", "#6B5A3A", "#5D6A86", "#8A8F99", "#D0D4DC", "#FFFFFF",
    "#F4F2EE", "#E6E9F0", "#AADAF5", "#CDEBC4", "#F2D9B8", "#E8C4C4", "#3A2A4A", "#000000",
)

/**
 * The Custom theme's settings: what it starts from, Day and Night tabs (the preview at the top shows the one being edited), the
 * colours of the ground, water, parks, roads, main roads and buildings, the route line's colour and width, night brightness, save
 * as a theme, and reset.
 */
@Composable
internal fun CustomThemeGroup(prefs: Prefs) {
    val c = LocalVw.current
    val night = PreviewLight.mode == "night"
    val key = if (night) PreferenceKeys.CUSTOM_NIGHT else PreferenceKeys.CUSTOM_DAY
    val spec = CustomLook.parse(prefs.s(key, ""))
    fun save(s: CustomLook.Spec) = prefs.put(key, if (s.isEmpty) "" else s.toJson())
    var open by remember { mutableStateOf<String?>(null) }
    SGroup("Custom colours", footer = "A part you haven't changed keeps the colour of the theme it starts from. At night the light darkens the colours you pick, as it does every theme: the preview at the top shows the real result.") {
        // what it starts from
        SItem("Starts from", "The theme your colours go on top of")
        Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val base = prefs.s(PreferenceKeys.CUSTOM_BASE, "mapbox")
            for ((id, label) in listOf("mapbox" to "Mapbox", "horizon" to "Horizon", "twilight" to "Twilight", "mono" to "Mono", "ember" to "Ember", "arctic" to "Arctic")) {
                val sel = base == id
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (sel) c.accent.copy(alpha = 0.22f) else c.tile)
                        .border(1.dp, if (sel) c.accent else Color.Transparent, RoundedCornerShape(50))
                        .pressClick { prefs.put(PreferenceKeys.CUSTOM_BASE, id) }.padding(horizontal = 12.dp, vertical = 7.dp),
                ) { Text(label, color = c.text, fontSize = 13.sp) }
            }
        }
        // Day / Night: which colours you're editing, and how the preview shows the map
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((n, label) in listOf(false to "Day colours", true to "Night colours")) {
                val sel = night == n
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (sel) c.accent else c.tile)
                        .pressClick { PreviewLight.mode = if (n) "night" else "day"; open = null }.padding(horizontal = 18.dp, vertical = 9.dp),
                ) { Text(label, color = if (sel) Color.White else c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        for (part in CustomLook.parts) {
            ColourRow(part.name, part.sub, spec.colours[part.key], open == part.key, { open = if (open == part.key) null else part.key }) { v ->
                save(spec.copy(colours = if (v == null) spec.colours - part.key else spec.colours + (part.key to v)))
            }
        }
        ColourRow("Route line", "The route you're following (traffic still shows amber and red)", spec.route, open == "route", { open = if (open == "route") null else "route" }) { v ->
            save(spec.copy(route = v))
        }
        SPercent("Route line width", "Day and night", prefs, PreferenceKeys.CUSTOM_ROUTE_W, 60, 180, 100)
        if (night) SPercent("Night brightness", "How bright the whole night map is (for every theme; also in Light & 3D)", prefs, PreferenceKeys.NIGHT_BRIGHT, 30, 200, 100)
        // save as a theme
        var name by remember { mutableStateOf("") }
        SFieldItem("Save as a theme", name, "Name it, then Save: it's kept under Your themes", placeholder = "My theme") { name = it }
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SButton("Save", primary = true) { CustomLook.saveCurrent(prefs, name); name = "" }
            SButton(if (night) "Reset night" else "Reset day") { save(CustomLook.Spec()); open = null }
            SButton("Reset all") {
                prefs.put(PreferenceKeys.CUSTOM_DAY, ""); prefs.put(PreferenceKeys.CUSTOM_NIGHT, ""); prefs.put(PreferenceKeys.CUSTOM_ROUTE_W, 100); open = null
            }
        }
    }
}

/** Themes saved from Custom: use one (it becomes the Custom theme) or delete it. */
@Composable
internal fun SavedThemesGroup(prefs: Prefs) {
    val list = CustomLook.saved(prefs)
    if (list.isEmpty()) return
    SGroup("Your themes") {
        for (t in list) {
            val dayCols = CustomLook.parse(t.day).colours
            SItem(t.name, "Starts from " + t.base.replaceFirstChar { it.uppercase() }) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (k in listOf("land", "water", "green", "buildings")) dayCols[k]?.let { Swatch(it, 18) }
                    Spacer(Modifier.width(6.dp))
                    SButton("Use", primary = true) { CustomLook.use(prefs, t) }
                    SButton("Delete") { CustomLook.delete(prefs, t.name) }
                }
            }
        }
    }
}

@Composable
private fun Swatch(hex: String?, size: Int = 30) {
    val c = LocalVw.current
    val m = Modifier.size(size.dp).clip(RoundedCornerShape(8.dp))
    if (hex == null) Box(m.border(2.dp, c.line, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text("–", color = c.sub, fontSize = 13.sp) }
    else Box(m.background(Color(android.graphics.Color.parseColor(hex))).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp)))
}

/** One part's colour: tap to open the palette and the shade / lightness sliders; "Theme's own" takes the change away. */
@Composable
private fun ColourRow(name: String, sub: String?, hex: String?, expanded: Boolean, onToggle: () -> Unit, onPick: (String?) -> Unit) {
    val c = LocalVw.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().pressClick { onToggle() }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, color = c.text, fontSize = 15.sp)
                Text(if (hex == null) (sub?.let { "$it · theme's own" } ?: "Theme's own") else (sub ?: hex), color = c.sub, fontSize = 11.5.sp)
            }
            Swatch(hex)
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.tile).padding(10.dp)) {
                for (rowCols in palette.chunked(8)) {
                    Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (p in rowCols) {
                            val sel = p.equals(hex, true)
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(p)))
                                    .border(if (sel) 3.dp else 1.dp, if (sel) c.accent else Color.White.copy(alpha = 0.2f), CircleShape)
                                    .pressClick { onPick(p) },
                            )
                        }
                    }
                }
                // fine-tune: shade (hue) and lightness, keeping the colour's strength
                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(android.graphics.Color.parseColor(hex ?: "#3A5590"), hsl)
                var hue by remember(hex) { mutableStateOf(hsl[0]) }
                var light by remember(hex) { mutableStateOf(hsl[2]) }
                val satv = if (hsl[1] < 0.05f) 0.45f else hsl[1]
                fun make(h: Float, l: Float) = CustomLook.hex(ColorUtils.HSLToColor(floatArrayOf(h, satv, l)))
                Text("Shade", color = c.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Brush.horizontalGradient((0..6).map { Color.hsl(it * 60f % 360f, satv, 0.5f) })))
                Slider(hue, { hue = it }, onValueChangeFinished = { onPick(make(hue, light)) }, valueRange = 0f..359f,
                    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent))
                Text("Lightness", color = c.sub, fontSize = 12.sp)
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Brush.horizontalGradient(listOf(Color.Black, Color.hsl(hue, satv, 0.5f), Color.White))))
                Slider(light, { light = it }, onValueChangeFinished = { onPick(make(hue, light)) }, valueRange = 0.02f..0.98f,
                    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Swatch(make(hue, light), 24)
                    Spacer(Modifier.width(8.dp))
                    Text(make(hue, light), color = c.sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (hex != null) SButton("Theme's own") { onPick(null) }
                }
            }
        }
    }
}
