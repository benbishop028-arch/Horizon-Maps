package com.headunit.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.headunit.launcher.PackRegion
import com.headunit.launcher.PackRegions

/** Picks the area for the offline pack: a tab per part of the world (UK & Ireland, Europe, US states...) and a search box. */
@Composable
internal fun PackPickerDialog(current: PackRegion, onPick: (PackRegion) -> Unit, onClose: () -> Unit) {
    val c = LocalVw.current
    var group by remember { mutableStateOf(current.group) }
    var q by remember { mutableStateOf("") }
    val shown = if (q.isBlank()) PackRegions.all.filter { it.group == group }
                else PackRegions.all.filter { it.name.contains(q.trim(), true) || it.group.contains(q.trim(), true) }
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.panel).border(1.dp, c.line, RoundedCornerShape(26.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Choose an area", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            SField(q, { q = it }, placeholder = "Search, e.g. France, Texas", width = 520.dp, fill = true)
            if (q.isBlank()) Segmented(PackRegions.groups.map { it to it }, group) { group = it }
            LazyColumn(Modifier.heightIn(max = 340.dp)) {
                items(shown) { r ->
                    val sel = r.id == current.id
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (sel) c.accent.copy(alpha = 0.14f) else c.accent.copy(alpha = 0f))
                            .clickable { onPick(r); onClose() }.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, color = c.text, fontSize = 16.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium)
                            if (q.isNotBlank()) Text(r.group, color = c.sub, fontSize = 12.sp)
                        }
                        if (sel) Icon(Icons.Rounded.Check, null, tint = c.accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (shown.isEmpty()) SNote("No area matches. Try the country's name in English.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SButton("Close") { onClose() } }
        }
    }
}
