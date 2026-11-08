package com.headunit.launcher.ui

import com.headunit.launcher.L

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

/**
 * Picks the area for the offline pack: a tab per part of the world (UK & Ireland, Europe, US states...) and a search box. At the top,
 * the area near you (worked out from [here]); big countries open up to show their regions (Germany's states, England's counties...),
 * which are much smaller downloads than the whole country.
 */
@Composable
internal fun PackPickerDialog(current: PackRegion, onPick: (PackRegion) -> Unit, here: com.headunit.launcher.LL? = null, onClose: () -> Unit) {
    val c = LocalVw.current
    var group by remember { mutableStateOf(current.group) }
    var q by remember { mutableStateOf("") }
    // the country that's open to show its regions (the one the current pick is in, to start with)
    var open by remember { mutableStateOf(current.parent.ifBlank { null }) }
    val near by androidx.compose.runtime.produceState<Pair<PackRegion, PackRegion?>?>(null, here) {
        value = if (here == null) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { try { PackRegions.nearYou(here) } catch (e: Exception) { null } }
    }
    val shown = if (q.isBlank()) PackRegions.all.filter { it.group == group && it.parent.isBlank() }
                else PackRegions.all.filter { it.name.contains(q.trim(), true) || it.group.contains(q.trim(), true) || it.also.any { a -> a.contains(q.trim(), true) } }
    fun pick(r: PackRegion) { onPick(r); onClose() }
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.panel).border(1.dp, c.line, RoundedCornerShape(26.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(L.t("Choose an area"), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            SField(q, { q = it }, placeholder = L.t("Search, e.g. France, Texas, Bavaria"), width = 520.dp, fill = true)
            // near you: the smallest area that covers where you are, and (beside it) the whole country
            val nr = near
            if (q.isBlank() && nr != null) Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.accent.copy(alpha = 0.16f)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(L.t("NEAR YOU"), color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { pick(nr.first) }.padding(vertical = 4.dp)) {
                        Text(nr.first.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        nr.second?.let { Text(it.name, color = c.sub, fontSize = 12.sp) }
                    }
                    nr.second?.let { whole ->
                        Text(L.t("or all of {0}", whole.name), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { pick(whole) }.padding(horizontal = 8.dp, vertical = 8.dp))
                    }
                }
            }
            if (q.isBlank()) Segmented(PackRegions.groups.map { it to it }, group) { group = it }
            LazyColumn(Modifier.heightIn(max = 340.dp)) {
                for (r in shown) {
                    val regions = if (q.isBlank()) PackRegions.regionsOf(r.id) else emptyList()
                    val isOpen = regions.isNotEmpty() && open == r.id
                    item(key = r.id) {
                        val sel = r.id == current.id
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (sel || isOpen) c.accent.copy(alpha = 0.10f) else c.accent.copy(alpha = 0f))
                                .clickable { if (regions.isNotEmpty()) open = if (isOpen) null else r.id else pick(r) }.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, color = c.text, fontSize = 16.sp, fontWeight = if (sel || isOpen) FontWeight.SemiBold else FontWeight.Medium)
                                if (q.isNotBlank()) Text(if (r.parent.isNotBlank()) PackRegions.byId(r.parent).name else r.group, color = c.sub, fontSize = 12.sp)
                            }
                            if (regions.isNotEmpty()) Text(if (isOpen) "⌄" else L.t("{0} regions ›", regions.size), color = c.sub, fontSize = 14.sp)
                            else if (sel) Icon(Icons.Rounded.Check, null, tint = c.accent, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (isOpen) items(listOf(r.copy(name = L.t("Whole country"))) + regions, key = { "sub-" + it.id + it.name }) { sr ->
                        val target = if (sr.name == L.t("Whole country") && sr.id == r.id) r else sr
                        val sel = target.id == current.id
                        Row(
                            Modifier.fillMaxWidth().clickable { pick(target) }.padding(start = 30.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(sr.name, color = if (sel) c.accent else c.text, fontSize = 15.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            if (sel) Icon(Icons.Rounded.Check, null, tint = c.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            if (shown.isEmpty()) SNote(L.t("No area matches. Try the country's name in English."))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SButton(L.t("Close")) { onClose() } }
        }
    }
}
