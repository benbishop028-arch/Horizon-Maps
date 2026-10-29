package com.headunit.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One change from the release notes, ready to show as its own card. */
internal class NoteItem(val kind: Int, val title: String, val desc: String, val icon: ImageVector)

/** 0 new, 1 improved, 2 fixed. */
private val kindNames = listOf("NEW", "IMPROVED", "FIXED")

/**
 * Turns release notes ("- Something: what it does" lines, maybe under "### 1.0.N" headings when several versions are shown) into
 * cards: sorted into new, improved and fixed by how each line starts, a bold headline (the part before the first colon, or the first
 * short clause) with the rest underneath, and an icon picked from what it's about.
 */
internal fun parseNotes(notes: String): List<NoteItem> {
    val out = ArrayList<NoteItem>()
    // a line that doesn't start with a dash carries on the bullet before it; "### New", "### Improved" and "### Fixed" headings
    // say which kind the bullets under them are (without one, it's worked out from how the line starts)
    val bullets = ArrayList<Pair<String, Int>>()
    var headKind = -1
    for (raw in notes.lines()) {
        val l = raw.trim()
        if (l.isEmpty()) continue
        if (l.startsWith("#")) {
            val h = l.trimStart('#').trim().lowercase()
            headKind = when { h.startsWith("new") || h.startsWith("added") -> 0; h.startsWith("improv") || h.startsWith("changed") -> 1; h.startsWith("fix") -> 2; else -> -1 }
            continue
        }
        if (l.startsWith("- ") || l.startsWith("* ") || l.startsWith("• ")) bullets += l.substring(2).trim() to headKind
        else if (bullets.isNotEmpty()) bullets[bullets.size - 1] = (bullets.last().first + " " + l) to bullets.last().second
        else bullets += l to headKind
    }
    for ((b0, hk) in bullets) {
        val b = b0.replace("**", "").replace("`", "").trim()
        if (b.isEmpty()) continue
        val low = b.lowercase()
        val kind = if (hk >= 0) hk else when {
            low.startsWith("fix") || low.startsWith("stop ") || low.startsWith("no more") || low.contains(" no longer ") && low.indexOf(" no longer ") < 40 -> 2
            low.startsWith("new") || low.startsWith("add") || low.startsWith("added") || low.contains(" new ") && low.indexOf(" new ") < 25 -> 0
            else -> 1
        }
        // the headline: before the first colon if that comes early, else the first short clause, else it all
        var title: String; var desc: String
        val colon = b.indexOf(": ")
        if (colon in 3..70) { title = b.substring(0, colon); desc = b.substring(colon + 2) }
        else if (b.length <= 75) { title = b.trimEnd('.'); desc = "" }
        else {
            val cut = listOf(". ", " (", ", ", " - ", " – ").map { b.indexOf(it) }.filter { it in 20..80 }.minOrNull()
            if (cut != null) { title = b.substring(0, cut); desc = b.substring(cut).trimStart('.', ',', ' ', '-', '–') }
            else { title = ""; desc = b }
        }
        desc = desc.trim()
        // "(it used to vanish…)" on its own reads better without the brackets
        if (desc.startsWith("(") && desc.endsWith(")")) desc = desc.substring(1, desc.length - 1)
        else if (desc.startsWith("(") && desc.endsWith(").")) desc = desc.substring(1, desc.length - 2) + "."
        desc = desc.replaceFirstChar { it.uppercase() }
        out += NoteItem(kind, title.trim(), desc, iconFor(low))
    }
    return out.sortedBy { it.kind }
}

private fun iconFor(low: String): ImageVector = when {
    low.contains("android auto") || low.contains("car screen") -> Icons.Rounded.DirectionsCar
    low.contains("electric") || low.contains("charg") || low.contains("battery") -> Icons.Rounded.BatteryChargingFull
    low.contains("fuel") || low.contains("petrol") || low.contains("diesel") || low.contains("e85") -> Icons.Rounded.LocalGasStation
    low.contains("traffic") -> Icons.Rounded.Traffic
    low.contains("voice") || low.contains("spoken") || low.contains("say ") || low.contains("sound") -> Icons.Rounded.RecordVoiceOver
    low.contains("lane") || low.contains("turn") || low.contains("junction") || low.contains("roundabout") -> Icons.Rounded.TurnRight
    low.contains("camera") || low.contains("speed") || low.contains("limit") -> Icons.Rounded.Speed
    low.contains("route") || low.contains("toll") || low.contains("stop") -> Icons.Rounded.Route
    low.contains("offline") || low.contains("download") -> Icons.Rounded.CloudDownload
    low.contains("key") || low.contains("mapbox") || low.contains("tomtom") -> Icons.Rounded.VpnKey
    low.contains("update") || low.contains("what's new") -> Icons.Rounded.SystemUpdate
    low.contains("home") || low.contains("place") || low.contains("favourite") -> Icons.Rounded.Place
    low.contains("setting") -> Icons.Rounded.Settings
    low.contains("map") || low.contains("3d") || low.contains("satellite") || low.contains("building") -> Icons.Rounded.Map
    low.startsWith("fix") -> Icons.Rounded.Build
    else -> Icons.Rounded.AutoAwesome
}

/**
 * The What's new card's list: a heading pill for each kind, then a card per change. [columns] 2 lays the cards out in pairs (the
 * wide landscape card); [maxHeight] caps how tall it gets before it scrolls.
 */
@Composable
internal fun WhatsNewList(notes: String, dark: Boolean, columns: Int = 1, maxHeight: androidx.compose.ui.unit.Dp? = null) {
    val items = remember(notes) { parseNotes(notes) }
    val v = if (dark) DarkVw else LightVw
    // new: green, improved: blue, fixed: amber (darker tints on the light card)
    val tints = if (dark) listOf(Color(0xFF7FE0A5), Color(0xFF7FB0FF), Color(0xFFFFC46B)) else listOf(Color(0xFF15803D), Color(0xFF1D5FD1), Color(0xFFB45309))
    val tintBg = if (dark) listOf(Color(0xFF1D4A30), Color(0xFF1D3359), Color(0xFF4A3613)) else listOf(Color(0xFFDDF5E7), Color(0xFFDDE8FC), Color(0xFFFBEBD5))
    val cardBg = if (dark) Color(0xFF222737) else Color(0xFFF4F6FA)
    val capH = maxHeight ?: (LocalConfiguration.current.screenHeightDp.dp * 0.55f).coerceAtLeast(160.dp)
    Column(Modifier.fillMaxWidth().heightIn(max = capH).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (items.isEmpty()) {
            Text(if (notes.isBlank()) "This version is installed and ready. The details of what changed aren't available right now." else notes,
                color = v.text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(4.dp))
            return@Column
        }
        val cols = columns.coerceIn(1, 2)
        var first = true
        for (kind in 0..2) {
            val group = items.filter { it.kind == kind }
            if (group.isEmpty()) continue
            Text(kindNames[kind], color = tints[kind], fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp,
                modifier = Modifier.padding(top = if (first) 2.dp else 10.dp, start = 2.dp).clip(RoundedCornerShape(10.dp)).background(tintBg[kind]).padding(horizontal = 9.dp, vertical = 3.dp))
            first = false
            for (row in group.chunked(cols)) {
                if (cols == 1) NoteCard(row[0], v, tints, tintBg, cardBg, Modifier.fillMaxWidth())
                else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (it in row) NoteCard(it, v, tints, tintBg, cardBg, Modifier.weight(1f).fillMaxHeight())
                    // an odd one out keeps half the width, so it lines up with the cards above it
                    if (row.size < cols) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NoteCard(it: NoteItem, v: Vw, tints: List<Color>, tintBg: List<Color>, cardBg: Color, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(cardBg).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tintBg[it.kind]), contentAlignment = Alignment.Center) {
            Icon(it.icon, null, tint = tints[it.kind], modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f)) {
            if (it.title.isNotBlank()) Text(it.title, color = v.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp)
            if (it.desc.isNotBlank()) Text(it.desc, color = v.sub, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = if (it.title.isNotBlank()) 3.dp else 0.dp))
        }
    }
}

/** "3 new · 2 improved · 4 fixed" for the card's header. */
internal fun notesSummary(notes: String): String {
    val items = parseNotes(notes)
    if (items.isEmpty()) return ""
    return listOf(0, 1, 2).mapNotNull { k -> items.count { it.kind == k }.takeIf { it > 0 }?.let { "$it ${kindNames[k].lowercase()}" } }.joinToString(" · ")
}
