package com.headunit.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mapbox.bindgen.Value
import com.mapbox.common.Cancelable
import com.mapbox.common.MapboxOptions
import com.mapbox.common.NetworkRestriction
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileStore
import com.mapbox.geojson.Geometry
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.GlyphsRasterizationMode
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.Style
import com.mapbox.maps.StylePackLoadOptions
import com.mapbox.maps.TilesetDescriptorOptions
import com.mapbox.maps.mapsOptions
import org.json.JSONObject

/**
 * The Mapbox map kept on the device for when there's no signal: a circle round Home, and strips along routes. Mapbox stores these
 * itself ("tile regions"), and the map uses them automatically when it can't reach Mapbox. Each device may keep only a limited
 * amount (750 of Mapbox's "tile packs": roughly 25,000 km² at street detail), so areas are kept small.
 *
 * Everything here reports back on the main thread, into Compose state the Offline maps page shows. Downloads run inside Mapbox
 * (never on the app's own threads), so nothing here can freeze the screen.
 */
object MapboxOffline {
    class Saved(val id: String, val name: String, val mb: Double, val complete: Boolean)

    /** The area being saved now (its name), or null. */
    var busy by mutableStateOf<String?>(null)
        private set
    var progress by mutableStateOf(0f)
        private set
    var status by mutableStateOf("")
        private set
    var saved by mutableStateOf<List<Saved>>(emptyList())
        private set

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val offline by lazy { OfflineManager() }
    private var running: Cancelable? = null
    private var stylePackAt = 0L

    private fun tileStore(): TileStore =
        MapboxOptions.mapsOptions.tileStore ?: TileStore.create().also { MapboxOptions.mapsOptions.tileStore = it }

    private fun names(ctx: Context): JSONObject = try { JSONObject(Prefs(ctx).s(PreferenceKeys.MBREGIONS, "{}")) } catch (e: Exception) { JSONObject() }
    private fun saveNames(ctx: Context, j: JSONObject) = Prefs(ctx).put("mbregions", j.toString())

    /** Roughly how big a saved area is, in MB (Standard at street detail is about 0.12 MB per km²). */
    fun homeMb(km: Int): Int = (Math.PI * km * km * 0.12).toInt()
    /** A route's strip: Mapbox keeps its map in blocks about 6 km wide along the line. */
    fun routeMb(lengthM: Double): Int = maxOf(5, (lengthM / 1000.0 * 6.0 * 0.12).toInt())

    /** Reads the list of saved areas (and their sizes) from Mapbox. */
    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        try {
            tileStore().getAllTileRegions { res ->
                val list = res.value ?: emptyList()
                val nm = names(app)
                val out = list.map { r ->
                    val n = nm.optJSONObject(r.id)?.optString("name")?.takeIf { it.isNotBlank() } ?: r.id
                    Saved(r.id, n, r.completedResourceSize / 1_000_000.0, r.requiredResourceCount > 0 && r.completedResourceCount >= r.requiredResourceCount)
                }.sortedBy { if (it.id == "home") "" else it.name }
                main.post { saved = out }
            }
        } catch (e: Throwable) { }
    }

    /** The map within [km] of [home]. */
    fun saveHome(ctx: Context, home: LL, km: Int) {
        val ring = ArrayList<Point>()
        val kLat = km * 1000.0 / 110574.0
        val kLon = km * 1000.0 / (111320.0 * Math.cos(Math.toRadians(home.lat)))
        for (i in 0..48) {
            val a = 2 * Math.PI * (i % 48) / 48.0
            ring += Point.fromLngLat(home.lon + Math.cos(a) * kLon, home.lat + Math.sin(a) * kLat)
        }
        save(ctx, "home", "Around home · $km km", Polygon.fromLngLats(listOf(ring)), doubleArrayOf(home.lat - kLat, home.lon - kLon, home.lat + kLat, home.lon + kLon))
    }

    /** A strip along [pts] (the route), named after where it goes. */
    fun saveRoute(ctx: Context, name: String, pts: List<LL>) {
        if (pts.size < 2) return
        // every route has its own area; a route to the same place again replaces the old one
        val id = "route-" + name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
        val step = maxOf(1, pts.size / 400)
        val line = pts.filterIndexed { i, _ -> i % step == 0 || i == pts.size - 1 }.map { Point.fromLngLat(it.lon, it.lat) }
        // the strip Mapbox keeps is about 6 km wide: the area counted as covered is the route's line plus 2 km either side
        val box = doubleArrayOf(pts.minOf { it.lat } - 0.018, pts.minOf { it.lon } - 0.03, pts.maxOf { it.lat } + 0.018, pts.maxOf { it.lon } + 0.03)
        val step2 = maxOf(1, pts.size / 200)
        val thin = pts.filterIndexed { i, _ -> i % step2 == 0 || i == pts.size - 1 }
        save(ctx, id, name, LineString.fromLngLats(line), box, thin)
    }

    /** Saves the strip of a route when you start following it, if Settings asks for that (on Wi-Fi only, unless set to always). */
    fun autoSaveRoute(ctx: Context, prefs: Prefs, name: String, pts: List<LL>, onWifi: Boolean) {
        if (!prefs.b(PreferenceKeys.MBAUTOROUTE, false)) return
        if (prefs.s(PreferenceKeys.MAPBOX_AUTO_NETWORK, "wifi") == "wifi" && !onWifi) return
        if (busy != null) return
        saveRoute(ctx, name, pts)
    }

    private fun save(ctx: Context, id: String, name: String, geometry: Geometry, box: DoubleArray, line: List<LL>? = null) {
        val app = ctx.applicationContext
        if (busy != null) { status = "Already saving ${busy}: wait for it to finish, or stop it"; return }
        busy = name; progress = 0f; status = "Starting…"
        val rec = JSONObject().put("name", name).put("t", System.currentTimeMillis())
            .put("box", org.json.JSONArray().put(box[0]).put(box[1]).put(box[2]).put(box[3]))
        if (line != null) rec.put("line", org.json.JSONArray().apply { line.forEach { put(it.lat); put(it.lon) } })
        val j = names(app); j.put(id, rec); saveNames(app, j)
        try {
            // the style itself (Standard's look, fonts and 3D models), once a day at most
            if (System.currentTimeMillis() - stylePackAt > 86_400_000L) {
                stylePackAt = System.currentTimeMillis()
                offline.loadStylePack(Style.STANDARD,
                    StylePackLoadOptions.Builder().glyphsRasterizationMode(GlyphsRasterizationMode.IDEOGRAPHS_RASTERIZED_LOCALLY).build(),
                    { _ -> }, { res -> if (res.error != null) stylePackAt = 0L })
            }
            val desc = offline.createTilesetDescriptor(
                TilesetDescriptorOptions.Builder().styleURI(Style.STANDARD)
                    .pixelRatio(app.resources.displayMetrics.density).minZoom(0).maxZoom(16).build()
            )
            running = tileStore().loadTileRegion(id,
                TileRegionLoadOptions.Builder().geometry(geometry).descriptors(listOf(desc))
                    .metadata(Value(name)).acceptExpired(true).networkRestriction(NetworkRestriction.NONE).build(),
                { p ->
                    val f = if (p.requiredResourceCount > 0) p.completedResourceCount.toFloat() / p.requiredResourceCount else 0f
                    val mb = p.completedResourceSize / 1_000_000.0
                    main.post { progress = f.coerceIn(0f, 1f); status = "Saving ${(f * 100).toInt()}% · ${"%.0f".format(mb)} MB" }
                },
            ) { res ->
                val err = res.error
                main.post {
                    running = null; busy = null
                    status = when {
                        err == null -> "Saved: $name"
                        err.message.contains("limit", true) || err.message.contains("750") || err.message.contains("pack", true) ->
                            "Too big for Mapbox's offline limit: try a smaller distance, or delete a saved area"
                        err.message.contains("cancel", true) -> "Stopped"
                        else -> "Couldn't save the map: ${err.message.take(120)}"
                    }
                    refresh(app)
                }
            }
        } catch (e: Throwable) {
            busy = null; status = "Couldn't save the map: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Stops the save that's running (what's been saved so far is kept). */
    fun stop() { try { running?.cancel() } catch (e: Throwable) { } }

    /** Deletes a saved area (its map is then only kept while there's room, like any recently seen area). */
    fun delete(ctx: Context, id: String) {
        val app = ctx.applicationContext
        try { tileStore().removeTileRegion(id) } catch (e: Throwable) { }
        val j = names(app); j.remove(id); saveNames(app, j)
        main.postDelayed({ refresh(app) }, 300L)
    }

    /**
     * Whether a saved area covers [p] (so with no signal the saved Mapbox map is shown rather than the pack's): inside Home's circle,
     * or within about 2 km of a saved route's line.
     */
    fun covers(ctx: Context, p: LL?): Boolean {
        if (p == null) return false
        val j = names(ctx.applicationContext)
        for (k in j.keys()) {
            val r = j.optJSONObject(k) ?: continue
            val b = r.optJSONArray("box") ?: continue
            if (p.lat < b.optDouble(0) || p.lon < b.optDouble(1) || p.lat > b.optDouble(2) || p.lon > b.optDouble(3)) continue
            val line = r.optJSONArray("line") ?: return true
            var i = 0
            while (i + 1 < line.length()) {
                if (Geo.dist(p, LL(line.optDouble(i), line.optDouble(i + 1))) < 2500.0) return true
                i += 2
            }
        }
        return false
    }
}
