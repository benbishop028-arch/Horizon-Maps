package com.headunit.launcher.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.headunit.launcher.NavEngine
import com.headunit.launcher.OfflineRouter
import com.headunit.launcher.PackRegions
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.headunit.launcher.Prefs
import java.io.File
import com.headunit.launcher.PreferenceKeys

/**
 * The Mapbox map with no signal: a circle round Home, the current route's strip, and (optionally) every route's strip as it starts.
 * Mapbox keeps these on the device itself; the map uses them automatically when there's no signal.
 */
@Composable
private fun MapboxOfflineGroup(prefs: Prefs, engine: NavEngine) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { com.headunit.launcher.MapboxOffline.refresh(ctx) }
    val off = com.headunit.launcher.MapboxOffline
    var confirm by remember { mutableStateOf<String?>(null) }
    SGroup("Mapbox map with no signal", footer = "Mapbox lets each device keep only limited offline areas: a radius round home and several routes fit, a whole country doesn't. Areas you've driven recently are also kept automatically.") {
        if (!engine.hasMapbox) SNote("Add your Mapbox access token in Settings > Keys & accounts first.")
        val km = prefs.s(PreferenceKeys.MBHOMEKM, "30").toIntOrNull() ?: 30
        val home = engine.places.slot("home")
        SItem("Around home", if (home == null) "Set Home first (Search, then save a place as Home)" else "The 3D map within $km km of Home · about ${off.homeMb(km)} MB")
        // (the distance choice and Save go on a line of their own: beside the title they squeezed it into a narrow column)
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Segmented(listOf("15" to "15 km", "30" to "30 km", "50" to "50 km"), km.toString()) { prefs.put(PreferenceKeys.MBHOMEKM, it) }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            if (home != null && engine.hasMapbox) SButton(if (off.busy?.startsWith("Around home") == true) "Saving…" else "Save", primary = true) { off.saveHome(ctx, home.pos, km) }
        }
        val r = engine.route
        SItem("Save the map for this route", if (r == null) "Plan a route first" else engine.offlineRouteName() + " · a strip along the route · about ${off.routeMb(r.len)} MB") {
            if (r != null && engine.hasMapbox) SButton("Save", primary = true) { off.saveRoute(ctx, engine.offlineRouteName(), r.pts) }
        }
        SToggle(prefs, "Save every route's map", "mbautoroute", false, "When a route starts, its strip is saved in the background")
        if (prefs.b(PreferenceKeys.MBAUTOROUTE, false)) SChoice("Save routes", listOf("wifi" to "On Wi-Fi only", "always" to "Always (uses mobile data)"), prefs.s(PreferenceKeys.MAPBOX_AUTO_NETWORK, "wifi")) { prefs.put(PreferenceKeys.MAPBOX_AUTO_NETWORK, it) }
        if (off.saved.isNotEmpty() || off.busy != null) {
            SItem("Saved", null)
            for (a in off.saved) {
                SItem(a.name, "%.0f MB".format(a.mb) + if (a.complete) " · saved" else " · part saved") {
                    if (confirm == a.id) {
                        SButton("Yes, delete", danger = true) { confirm = null; off.delete(ctx, a.id) }
                        SButton("Keep") { confirm = null }
                    } else SButton("Delete", danger = true) { confirm = a.id }
                }
            }
        }
        if (off.busy != null) {
            SItem(off.busy ?: "", off.status) { SButton("Stop") { off.stop() } }
            LinearProgressIndicator(progress = { off.progress }, color = LocalVw.current.accent, trackColor = LocalVw.current.tile, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        } else if (off.status.isNotBlank()) SNote(off.status)
    }
}

private fun fmtSize(b: Long): String = if (b >= 1_000_000_000L) "%.1f GB".format(b / 1e9) else "%.0f MB".format(b / 1e6)

/** The downloaded areas: what's on the unit, which is in use, add another, update and delete. */
@Composable
internal fun OfflineMapsContent(prefs: Prefs, engine: NavEngine) {
    val c = LocalVw.current
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val rev = engine.packRev // re-read the files when a download, switch or delete finishes
    @Suppress("UNUSED_VARIABLE") val pick = prefs.s(PreferenceKeys.PACK_REGION, "")   // redraw when the area to add changes
    val region = engine.chosenPack()
    val have = engine.installedPacks()
    val activeId = engine.installedPackId()
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    // has the area to add been built yet? (checked when it changes)
    var avail by remember(region.id) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(region.id, have.size) { if (region.id !in have) avail = withContext(Dispatchers.IO) { PackRegions.available(region) } }
    // sizes and free space are worked out in the background (a pack's font folder holds thousands of small files)
    val sizes by androidx.compose.runtime.produceState(emptyMap<String, Long>(), have, rev) {
        value = withContext(Dispatchers.IO) { have.associateWith { OfflineRouter.sizeOf(OfflineRouter.packDir(ctx, it)) } }
    }
    // areas downloaded before the speed limit and camera files existed: has the file been published for them since?
    val extrasReady by androidx.compose.runtime.produceState(emptySet<String>(), have, rev) {
        value = withContext(Dispatchers.IO) {
            have.filter { !File(OfflineRouter.packDir(ctx, it), "limits.bin").exists() && PackRegions.hasExtras(PackRegions.byId(it)) == true }.toSet()
        }
    }
    val free by androidx.compose.runtime.produceState(-1L, rev) {
        value = withContext(Dispatchers.IO) { try { android.os.StatFs(OfflineRouter.packsRoot(ctx).path).availableBytes } catch (e: Exception) { -1L } }
    }

    MapboxOfflineGroup(prefs, engine)
    SGroup("Downloaded areas", footer = "Keep as many areas as you like: the one you're in is used automatically (routes work offline within one area). Each is a snapshot of the roads on the day it was built, so update now and then." + if (free >= 0) " ${fmtSize(free)} free." else "") {
        if (have.isEmpty()) SNote("Nothing downloaded yet. Pick an area below and download it on Wi-Fi.")
        for (id in have) {
            val r = PackRegions.byId(id)
            val d = OfflineRouter.packDir(ctx, id)
            val tiles = File(d, "tiles.tar")
            val built = if (tiles.exists()) java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.UK).format(java.util.Date(tiles.lastModified())) else ""
            val inUse = id == activeId
            val hasLimits = File(d, "limits.bin").exists()
            SItem(r.name + if (inUse) " · in use" else "", (sizes[id]?.let { fmtSize(it) } ?: "…") + (if (built.isNotEmpty()) " · built $built" else "") +
                (if (hasLimits) " · speed limits and cameras offline" else "")) {
                if (confirmDelete == id) {
                    SButton("Yes, delete", danger = true) { confirmDelete = null; engine.deletePack(id) }
                    SButton("Keep") { confirmDelete = null }
                } else {
                    if (!inUse && have.size > 1) SButton("Use") { engine.useArea(id) }
                    SButton(if (engine.packDownloading == id) "Updating…" else "Update") { engine.downloadRegion(r) }
                    SButton("Delete", danger = true) { confirmDelete = id }
                }
            }
        }
        for (id in extrasReady) if (engine.packDownloading != id)
            SNote("Offline speed limits and cameras are now ready for ${PackRegions.byId(id).name}: tap its Update (only the new files download, usually under 100 MB).")
        val age = engine.packAgeDays()
        if (age != null && age >= 120 && !engine.packBusy) SNote("The area in use is about ${age / 30} months old. Update it on Wi-Fi to pick up new roads and junction changes.")
    }
    SGroup("Add an area") {
        SItem("Area", region.name + " · " + region.group) { SButton("Change") { picking = true } }
        if (region.id !in have && avail == false) SNote("The ${region.name} pack hasn't been built yet, so it can't be downloaded at the moment.")
        SItem(if (region.id in have) "${region.name} is already downloaded" else "Download ${region.name}", "Best done on Wi-Fi: a country is usually 1 to 4 GB") {
            SButton(if (engine.packBusy) "Downloading…" else if (region.id in have) "Update" else "Download", primary = true) { engine.downloadRegion(region) }
        }
        if (engine.packBusy) LinearProgressIndicator(color = c.accent, trackColor = c.tile, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        if (engine.packStatus.isNotBlank()) SNote((engine.packDownloading?.let { PackRegions.byId(it).name + ": " } ?: "") + engine.packStatus + if (engine.packBusy) " · you can leave this page" else "")
    }
    SGroup("Without signal") {
        SChoice("Which map to draw", listOf("auto" to "Auto", "always" to "Downloaded", "never" to "Online"), prefs.s(PreferenceKeys.OFF_MAP, "auto"),
            "Auto: with no signal Mapbox's map carries on with the areas it has stored (where you've been recently, and saved Mapbox areas); the downloaded pack's map takes over only where Mapbox has nothing, and the online map comes back with signal") { prefs.put(PreferenceKeys.OFF_MAP, it); engine.restyle() }
    }
    if (picking) PackPickerDialog(region, { prefs.put(PreferenceKeys.PACK_REGION, it.id) }) { picking = false }
}
