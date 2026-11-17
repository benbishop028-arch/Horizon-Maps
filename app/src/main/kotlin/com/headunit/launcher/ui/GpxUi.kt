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
@Composable
internal fun GpxGroup(engine: NavEngine, onShown: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalVw.current
    LaunchedEffect(Unit) { GpxStore.load(ctx) }
    var busy by remember { mutableStateOf(false) }
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
        for (t in GpxStore.tracks) {
            var ask by remember(t.id) { mutableStateOf(false) }
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
                    SButton(L.t("Drive"), primary = true) { engine.driveTrack(t.id); onShown() }
                    Box(Modifier.size(44.dp).clip(CircleShape).pressClick { ask = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Delete, L.t("Delete"), tint = c.sub, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
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
        ImmPill(L.t("Drive this"), { engine.driveTrack(t.id) }, filled = true)
        Box(Modifier.size(40.dp).clip(CircleShape).pressClick { engine.closeTrack() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, L.t("Close"), tint = c.sub, modifier = Modifier.size(22.dp))
        }
    }
}
