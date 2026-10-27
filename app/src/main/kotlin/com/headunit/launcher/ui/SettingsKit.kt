package com.headunit.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.Prefs
import kotlin.math.roundToInt

// Building blocks for the settings screen: rounded groups of rows, in the same soft style as the map screen.

/** A titled card holding a few related settings. */
@Composable
internal fun SGroup(title: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalVw.current
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        if (title != null) Text(title.uppercase(), color = c.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
        // the same card look as the map screen: soft surface with a hairline edge
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.panel).border(1.dp, c.line, RoundedCornerShape(22.dp)).padding(horizontal = 18.dp, vertical = 6.dp).animateContentSize(), content = content)
        if (footer != null) Text(footer, color = c.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp))
    }
}

/** The setting picked from the settings search: it scrolls into view and glows for a moment. */
internal val LocalSettingHighlight = androidx.compose.runtime.compositionLocalOf<String?> { null }

/** One setting: a title (and an optional line under it) on the left, its control on the right. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun SItem(title: String, sub: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    val c = LocalVw.current
    val hl = LocalSettingHighlight.current == title
    var hlMod: Modifier = Modifier
    if (hl) {
        val req = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
        val glow = remember { androidx.compose.animation.core.Animatable(0f) }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(380)   // let the section finish sliding in first
            try { req.bringIntoView() } catch (e: Exception) { }
            glow.animateTo(1f, androidx.compose.animation.core.tween(250))
            kotlinx.coroutines.delay(900)
            glow.animateTo(0f, androidx.compose.animation.core.tween(900))
        }
        hlMod = Modifier.bringIntoViewRequester(req).clip(RoundedCornerShape(14.dp)).drawBehind { drawRect(c.accent.copy(alpha = 0.18f * glow.value)) }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).then(hlMod).then(if (onClick != null) Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick) else Modifier).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (!sub.isNullOrBlank()) Text(sub, color = c.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

/** An on/off setting stored under [key]. The whole row can be tapped. */
@Composable
internal fun SToggle(prefs: Prefs, title: String, key: String, def: Boolean, sub: String? = null) {
    val on = prefs.b(key, def)
    SItem(title, sub, onClick = { prefs.put(key, !on) }) { Switch(on, { prefs.put(key, it) }) }
}

/** A row of choices in a rounded track, with the chosen one filled. */
@Composable
internal fun Segmented(options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    val c = LocalVw.current
    Row(Modifier.clip(RoundedCornerShape(50)).background(c.tile).horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(3.dp)) {
        for ((k, label) in options) {
            val sel = k == value
            val bg by androidx.compose.animation.animateColorAsState(if (sel) c.accent else c.accent.copy(alpha = 0f), androidx.compose.animation.core.tween(180), label = "seg")
            val fg by androidx.compose.animation.animateColorAsState(if (sel) (if (c.accent.luminance() > 0.6f) Color(0xFF10131C) else Color.White) else c.text, androidx.compose.animation.core.tween(180), label = "segT")
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(bg).clickable { onPick(k) }.padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = fg, fontSize = 14.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/** A choice between a few options. Short lists sit on the right; longer ones go under the title. */
@Composable
internal fun SChoice(title: String, options: List<Pair<String, String>>, value: String, sub: String? = null, onPick: (String) -> Unit) {
    val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 720
    val long = options.size > 3 || options.sumOf { it.second.length } > (if (narrow) 12 else 26)
    if (!long) SItem(title, sub) { Segmented(options, value, onPick) }
    else Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SItem(title, sub)
        Segmented(options, value, onPick)
    }
}

/** A slider with its value shown beside it; the value is saved when you let go. */
@Composable
internal fun SSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, sub: String? = null, steps: Int = 0, reset: Float? = null, live: Boolean = false, onDone: (Int) -> Unit) {
    val c = LocalVw.current
    var v by remember(title, value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        SItem(title, sub) {
            Text("${v.roundToInt()}$unit", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (reset != null && v.roundToInt() != reset.roundToInt()) SButton("Reset") { v = reset; onDone(reset.roundToInt()) }
        }
        Slider(
            value = v, onValueChange = { v = it; if (live) onDone(it.roundToInt()) }, onValueChangeFinished = { onDone(v.roundToInt()) },
            valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.tile),
        )
    }
}

/** A rounded button: plain, filled (primary) or red (danger). */
@Composable
internal fun SButton(text: String, primary: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val c = LocalVw.current
    val bg = if (danger) Bad.copy(alpha = 0.16f) else if (primary) c.accent else c.tile
    val fg = if (danger) Bad else if (primary) (if (c.accent.luminance() > 0.6f) Color(0xFF10131C) else Color.White) else c.text
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).pressClick(onClick).padding(horizontal = 16.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** A text box for a key, number or address. */
@Composable
internal fun SField(value: String, onChange: (String) -> Unit, placeholder: String = "", password: Boolean = false, number: Boolean = false, width: Dp = 360.dp, fill: Boolean = false) {
    val c = LocalVw.current
    TextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(placeholder, color = c.sub) },
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = c.tile, unfocusedContainerColor = c.tile,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = c.text, unfocusedTextColor = c.text, cursorColor = c.accent,
        ),
        // [fill]: as wide as there is room for, up to [width] (so it fits a portrait screen too)
        modifier = if (fill) Modifier.widthIn(max = width).fillMaxWidth() else Modifier.width(width),
    )
}

/** A setting that is a text box under its title. */
@Composable
internal fun SFieldItem(title: String, value: String, sub: String? = null, placeholder: String = "", password: Boolean = false, number: Boolean = false, onChange: (String) -> Unit) {
    var v by remember(title, value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SItem(title, sub)
        SField(v, { v = it; onChange(it) }, placeholder, password, number, width = 460.dp, fill = true)
    }
}

/** A line of explanation inside a group. */
@Composable
internal fun SNote(text: String) {
    Text(text, color = LocalVw.current.sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
}


/** A row of colour dots to pick from; the chosen one has a ring and a tick. */
@Composable
internal fun SColours(title: String, options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    val c = LocalVw.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SItem(title, options.firstOrNull { it.first.equals(value, true) }?.second)
        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((hex, name) in options) {
                val col = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray }
                val sel = hex.equals(value, true)
                Box(
                    Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .border(if (sel) 3.dp else 1.dp, if (sel) c.accent else c.line, androidx.compose.foundation.shape.CircleShape)
                        .padding(if (sel) 5.dp else 3.dp).clip(androidx.compose.foundation.shape.CircleShape).background(col)
                        .clickable { onPick(hex) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (sel) androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Check, name, tint = if (col.luminance() > 0.6f) Color(0xFF14171C) else Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
