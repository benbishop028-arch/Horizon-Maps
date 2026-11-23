package com.headunit.launcher.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.Geo
import com.headunit.launcher.GpxStore
import com.headunit.launcher.GpxTrack
import com.headunit.launcher.L
import com.headunit.launcher.NavEngine

/** Settings › Places & trips › GPX tracks (GitHub #153): import a .gpx file, then show a track on the map or drive it. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun GpxGroup(engine: NavEngine, onShown: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalVw.current
    LaunchedEffect(Unit) { GpxStore.load(ctx) }
    var busy by remember { mutableStateOf(false) }
    // the track whose group is being picked, the groups folded openGs, and the group being renamed
    var filing by remember { mutableStateOf<String?>(null) }
    var openGs by remember { mutableStateOf(setOf<String>()) }
    var renaming by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data ?: return@rememberLauncherForActivityResult
        val app = ctx.applicationContext
        busy = true
        Thread {
            val why = try { GpxStore.import(app, uri) } catch (e: Throwable) { "That isn't a GPX file" }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                busy = false
                engine.toast(if (why == null) L.t("Track imported") else L.t(why), 3500)
            }
        }.start()
    }
    fun openPicker() {
        // (.gpx files have no agreed type on Android, so any file is offered and checked once picked)
        val tries = listOf(
            Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),
            Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),
        )
        for (i in tries) { try { pick.launch(i); return } catch (e: Exception) { } }
        engine.toast(L.t("No app on this unit can pick a file (try installing a file manager)"), 4000)
    }
    SGroup(L.t("GPX tracks"), footer = L.t("Tracks from a .gpx file (Komoot, Strava, Garmin…). Drive follows the track with spoken turns.")) {
        // (in groups if you like, GitHub #162: a group folds openGs to its tracks, then the tracks in no group; each track's folder
        // button files it, the same way as your places)
        val groups = GpxStore.groups()
        @Composable
        fun trackRow(t: GpxTrack, indent: Boolean) {
            var ask by remember(t.id) { mutableStateOf(false) }
            Column(if (indent) Modifier.padding(start = 18.dp) else Modifier) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Geo.fmtDist(t.len, engine.imperial), color = c.sub, fontSize = 13.sp)
                    }
                    if (ask) {
                        SButton(L.t("Yes, delete"), danger = true) { GpxStore.delete(ctx, t.id); ask = false }
                        SButton(L.t("Cancel")) { ask = false }
                    } else {
                        SButton(L.t("Show")) { engine.showTrack(t.id); onShown() }
                        SButton(L.t("Navigate"), primary = true) { engine.driveTrack(t.id); onShown() }
                        Box(Modifier.size(44.dp).clip(CircleShape).pressClick { filing = if (filing == t.id) null else t.id }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Folder, L.t("Group"), tint = if (filing == t.id || t.group.isNotBlank()) c.accent else c.sub, modifier = Modifier.size(22.dp))
                        }
                        Box(Modifier.size(44.dp).clip(CircleShape).pressClick { ask = true }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Delete, L.t("Delete"), tint = c.sub, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                if (filing == t.id) {
                    var newName by remember(t.id) { mutableStateOf("") }
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (g in groups) if (g != t.group) SButton(g) { GpxStore.setGroup(ctx, t.id, g); filing = null; openGs = openGs + g }
                        SField(newName, { newName = it.take(24) }, placeholder = L.t("New group"), width = 180.dp)
                        if (newName.isNotBlank()) SButton(L.t("Add"), primary = true) { GpxStore.setGroup(ctx, t.id, newName); openGs = openGs + newName.trim(); filing = null }
                        if (t.group.isNotBlank()) SButton(L.t("No group")) { GpxStore.setGroup(ctx, t.id, ""); filing = null }
                        SButton(L.t("Cancel")) { filing = null }
                    }
                }
            }
        }
        for (g in groups) {
            val inG = GpxStore.tracks.filter { it.group == g }
            val isOpen = g in openGs
            if (renaming == g) {
                var t by remember(g) { mutableStateOf(g) }
                SItem(L.t("Rename {0}", g)) {
                    SField(t, { t = it.take(24) }, width = 220.dp)
                    SButton(L.t("Save"), primary = true) { GpxStore.renameGroup(ctx, g, t); if (isOpen) openGs = openGs - g + t.trim(); renaming = null }
                }
            } else SItem((if (isOpen) "▾ " else "▸ ") + g, if (inG.size == 1) L.t("1 track") else L.t("{0} tracks", inG.size),
                onClick = { openGs = if (isOpen) openGs - g else openGs + g }) {
                SButton(L.t("Rename")) { renaming = g }
            }
            if (isOpen) for (t in inG) trackRow(t, true)
        }
        for (t in GpxStore.tracks) if (t.group.isBlank()) trackRow(t, false)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            if (GpxStore.tracks.isEmpty()) Text(L.t("No tracks yet."), color = c.sub, fontSize = 14.sp, modifier = Modifier.weight(1f).align(Alignment.CenterVertically))
            else Box(Modifier.weight(1f))
            SButton(if (busy) L.t("Importing…") else L.t("Import GPX")) { if (!busy) openPicker() }
        }
    }
}

/** The card on the map for a track you're looking at: its name and length, how far its start is, Drive this and Close. */
@Composable
internal fun TrackCard(engine: NavEngine, t: GpxTrack) {
    val c = LocalVw.current
    val here = engine.fix?.pos
    val toStart = here?.let { h -> t.pts.firstOrNull()?.let { Geo.dist(h, it) } }
    Row(Modifier.fillMaxWidth().panel(10.dp, RoundedCornerShape(22.dp), c.surface).padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFD4537E).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Route, null, tint = Color(0xFFD4537E), modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(t.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = Geo.fmtDist(t.len, engine.imperial) + (if (toStart != null && toStart > 300.0) " · " + L.t("starts {0} away", Geo.fmtDist(toStart, engine.imperial)) else "")
            Text(sub, color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ImmPill(L.t("Navigate"), { engine.driveTrack(t.id) }, filled = true)   // (was "Drive this": bijindas)
        Box(Modifier.size(40.dp).clip(CircleShape).pressClick { engine.closeTrack() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, L.t("Close"), tint = c.sub, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Settings › Places & trips › My routes (igalshe-ai #321): the routes you saved from the arrival card, offered in the route choice
 * when a trip fits one. Show one on the map, rename or delete it, and choose what happens when one fits.
 */
@Composable
internal fun MyRoutesGroup(engine: NavEngine, onShown: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalVw.current
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.headunit.launcher.MyRoutes.all(ctx.applicationContext) } }
    val list = com.headunit.launcher.MyRoutes.list
    val fmt = remember { java.text.SimpleDateFormat("d MMM", L.locale) }
    SGroup(L.t("My routes"), footer = L.t("Save the way you drove from the arrival card. When you next go from near the same start to the same place, it's offered as \"Your route\" beside the fastest, with the difference in time.")) {
        for (r in list) {
            var ask by remember(r.id) { mutableStateOf(false) }
            var renaming by remember(r.id) { mutableStateOf(false) }
            var newName by remember(r.id) { mutableStateOf(r.name) }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (renaming) {
                    SField(newName, { newName = it.take(60) }, width = 240.dp)
                    Box(Modifier.weight(1f))
                    SButton(L.t("Save"), primary = true) { com.headunit.launcher.MyRoutes.rename(ctx, r.id, newName); renaming = false }
                    SButton(L.t("Cancel")) { renaming = false; newName = r.name }
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Geo.fmtDist(r.len, engine.imperial) + " · " + L.t("saved {0}", fmt.format(java.util.Date(r.at))), color = c.sub, fontSize = 13.sp, maxLines = 1)
                    }
                    if (ask) {
                        SButton(L.t("Yes, delete"), danger = true) { com.headunit.launcher.MyRoutes.delete(ctx, r.id); ask = false }
                        SButton(L.t("Cancel")) { ask = false }
                    } else {
                        SButton(L.t("Show")) { engine.showMyRoute(r.id); onShown() }
                        SButton(L.t("Rename")) { renaming = true }
                        Box(Modifier.size(44.dp).clip(CircleShape).pressClick { ask = true }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Delete, L.t("Delete"), tint = c.sub, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
        if (list.isEmpty()) Text(L.t("No routes saved yet. When you arrive, tap \"Save as my route\" on the arrival card."), color = c.sub, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
        SChoice(L.t("When a saved route fits"), listOf("offer" to L.t("Offer it first"), "start" to L.t("Start on it straight away"), "off" to L.t("Don't offer")),
            engine.prefs.s(com.headunit.launcher.PreferenceKeys.MY_ROUTES, "offer")) { engine.prefs.put(com.headunit.launcher.PreferenceKeys.MY_ROUTES, it) }
    }
}
