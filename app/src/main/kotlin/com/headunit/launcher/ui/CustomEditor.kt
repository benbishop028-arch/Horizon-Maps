package com.headunit.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.headunit.launcher.MapBinding
import com.headunit.launcher.PreferenceKeys
import com.headunit.launcher.Prefs
import kotlin.math.roundToInt

/** Whether the Custom colours editor is open (it shows over the live map, with the settings put away meanwhile). */
object CustomEditState {
    var open by mutableStateOf(false)
}

private val palette = listOf(
    "#0F1424", "#1F2840", "#2A2F4A", "#33405F", "#3A5590", "#4C8DFF", "#7FA6FF", "#C6D4FF",
    "#14304F", "#2C4A42", "#3F6B4F", "#6B5A3A", "#5D6A86", "#8A8F99", "#D0D4DC", "#FFFFFF",
    "#F4F2EE", "#E6E9F0", "#AADAF5", "#CDEBC4", "#F2D9B8", "#E8C4C4", "#3A2A4A", "#000000",
)

/**
 * The Custom colours editor: a panel over the live map (left side when wide, bottom half when tall), with Day and Night tabs. The
 * map shows the tab being edited (day or night) while it's open, and every change shows on it at once.
 */
@Composable
fun CustomEditor(prefs: Prefs, map: MapBinding, onClose: () -> Unit) {
    val c = LocalVw.current
    var night by remember { mutableStateOf(prefs.b("customtabnight", true)) }
    DisposableEffect(night) {
        map.forceLight(if (night) "night" else "day")
        onDispose { }
    }
    DisposableEffect(Unit) { onDispose { map.forceLight(null) } }
    androidx.activity.compose.BackHandler { onClose() }

    val key = if (night) PreferenceKeys.CUSTOM_NIGHT else PreferenceKeys.CUSTOM_DAY
    val spec = CustomLook.parse(prefs.s(key, ""))
    fun save(s: CustomLook.Spec) = prefs.put(key, if (s.isEmpty) "" else s.toJson())
    val base = prefs.s(PreferenceKeys.CUSTOM_BASE, "mapbox")
    var open by remember { mutableStateOf<String?>(null) }

    val wide = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    Box(Modifier.fillMaxSize(), contentAlignment = if (wide) Alignment.CenterStart else Alignment.BottomCenter) {
        val panel = if (wide) Modifier.fillMaxHeight().width(460.dp) else Modifier.fillMaxWidth().fillMaxHeight(0.55f)
        Column(
            panel.padding(10.dp).clip(RoundedCornerShape(18.dp)).background(c.surface.copy(alpha = 0.97f))
                .verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Custom colours", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                SButton("Done", primary = true) { onClose() }
            }
            Spacer(Modifier.height(10.dp))
            // Day / Night
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((n, label) in listOf(false to "Day", true to "Night")) {
                    val sel = night == n
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (sel) c.accent else c.tile)
                            .clickable { night = n; open = null; prefs.put("customtabnight", n) }.padding(horizontal = 18.dp, vertical = 8.dp),
                    ) { Text(label, color = if (sel) Color.White else c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Starts from", color = c.sub, fontSize = 12.sp)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((id, label) in listOf("mapbox" to "Mapbox", "horizon" to "Horizon", "twilight" to "Twilight", "faded" to "Faded", "grey" to "Grey")) {
                    val sel = base == id
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (sel) c.accent.copy(alpha = 0.25f) else c.tile)
                            .border(1.dp, if (sel) c.accent else Color.Transparent, RoundedCornerShape(50))
                            .clickable { prefs.put(PreferenceKeys.CUSTOM_BASE, id) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text(label, color = c.text, fontSize = 12.sp) }
                }
            }

            Head("COLOURS")
            for (part in CustomLook.parts) {
                val cur = spec.colours[part.key]
                ColourRow(part.name, part.sub, cur, open == part.key, { open = if (open == part.key) null else part.key }) { v ->
                    save(spec.copy(colours = if (v == null) spec.colours - part.key else spec.colours + (part.key to v)))
                }
            }

            Head("LIGHT AND EFFECTS")
            if (night) {
                val b = spec.bright
                PercentRow("Night brightness", if (b == null) "Mapbox's own night light. Lights the whole map" else "How strong the night light is, for the whole map",
                    b ?: 100, 25, 200, if (b != null) ({ save(spec.copy(bright = null)) }) else null) { save(spec.copy(bright = it)) }
            }
            if (base !in setOf("faded", "grey")) {
                ColourRow("Overall tint", "A colour wash over the whole map", spec.tint, open == "tint", { open = if (open == "tint") null else "tint" }) { v -> save(spec.copy(tint = v)) }
                if (spec.tint != null) PercentRow("Tint strength", null, spec.tintStr, 0, 100, null) { save(spec.copy(tintStr = it)) }
            }
            ToggleRow("Glowing buildings", "The app draws the buildings, lit from within. Hides Mapbox's detailed building fronts while on. Full 3D only", spec.glow) { save(spec.copy(glow = it)) }
            if (spec.glow) PercentRow("Glow strength", "Uses the Buildings colour above", spec.glowStr, 0, 100, null) { save(spec.copy(glowStr = it)) }
            ToggleRow("Underglow", "Soft light on the ground round each building, close in. Full 3D only", spec.under) { save(spec.copy(under = it)) }
            if (spec.under) {
                ColourRow("Underglow colour", null, spec.underCol, open == "under", { open = if (open == "under") null else "under" }) { v -> save(spec.copy(underCol = v)) }
                PercentRow("Underglow strength", null, spec.underStr, 0, 100, null) { save(spec.copy(underStr = it)) }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SButton(if (night) "Reset night" else "Reset day") { save(CustomLook.Spec()); open = null }
                if (night) SButton("Copy from day") { prefs.put(PreferenceKeys.CUSTOM_NIGHT, prefs.s(PreferenceKeys.CUSTOM_DAY, "")) }
                else SButton("Copy to night") { prefs.put(PreferenceKeys.CUSTOM_NIGHT, prefs.s(PreferenceKeys.CUSTOM_DAY, "")) }
            }
            Spacer(Modifier.height(6.dp))
            Text("An untouched part keeps the colour of the theme it starts from. At night the light darkens the colours you pick, as it does every theme: the map shows the real result.", color = c.sub, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Head(t: String) {
    Text(t, color = LocalVw.current.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun Swatch(hex: String?, size: Int = 30) {
    val c = LocalVw.current
    val m = Modifier.size(size.dp).clip(RoundedCornerShape(8.dp))
    if (hex == null) Box(m.border(2.dp, c.line, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text("–", color = c.sub, fontSize = 13.sp) }
    else Box(m.background(Color(android.graphics.Color.parseColor(hex))).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp)))
}

/** A part's colour: tap to open the palette and the shade / lightness sliders; "Theme's own" takes the change away. */
@Composable
private fun ColourRow(name: String, sub: String?, hex: String?, expanded: Boolean, onToggle: () -> Unit, onPick: (String?) -> Unit) {
    val c = LocalVw.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                Modifier.size(28.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(p)))
                                    .border(if (sel) 3.dp else 1.dp, if (sel) c.accent else Color.White.copy(alpha = 0.2f), CircleShape)
                                    .clickable { onPick(p) },
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

@Composable
private fun PercentRow(name: String, sub: String?, value: Int, min: Int, max: Int, onReset: (() -> Unit)?, onSet: (Int) -> Unit) {
    val c = LocalVw.current
    var v by remember(value) { mutableStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, color = c.text, fontSize = 15.sp)
                if (sub != null) Text(sub, color = c.sub, fontSize = 11.5.sp)
            }
            Text("${v.roundToInt()}%", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (onReset != null) { Spacer(Modifier.width(8.dp)); SButton("Reset") { onReset() } }
        }
        Slider(v, { v = (it / 5f).roundToInt() * 5f }, onValueChangeFinished = { onSet(v.roundToInt().coerceIn(min, max)) },
            valueRange = min.toFloat()..max.toFloat(),
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.tile))
    }
}

@Composable
private fun ToggleRow(name: String, sub: String, on: Boolean, onSet: (Boolean) -> Unit) {
    val c = LocalVw.current
    Row(Modifier.fillMaxWidth().clickable { onSet(!on) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, color = c.text, fontSize = 15.sp)
            Text(sub, color = c.sub, fontSize = 11.5.sp)
        }
        Switch(on, { onSet(it) }, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
    }
}
