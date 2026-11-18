package com.headunit.launcher

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File

/** A GPX track you imported (GitHub #153): [len] in metres, [at] when it was imported. */
class GpxTrack(val id: String, val name: String, val len: Double, val at: Long, val pts: List<LL>, val group: String = "", val start: LL? = null)

/**
 * GPX tracks (GitHub #153): imported from a .gpx file (Komoot, Strava, Garmin, a track you recorded…), kept in files/gpx, shown on
 * the map and driven with turn-by-turn directions. Kept small for slow head units: points closer than 15 m together are merged and a
 * track is capped at 4000 points (plenty to draw it; the route only needs a couple of dozen of them).
 */
object GpxStore {
    /** The tracks, newest first, without their points (those are read when one is shown or driven). */
    var tracks by mutableStateOf<List<GpxTrack>>(emptyList())
        private set
    private var loaded = false

    private fun dir(c: Context) = File(c.filesDir, "gpx").apply { mkdirs() }

    /** Reads the list (once; on a background thread). */
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        Thread {
            val list = readAll(c)
            android.os.Handler(android.os.Looper.getMainLooper()).post { tracks = list }
        }.start()
    }

    /** Reads the list again (after a backup brought tracks back). */
    fun reload(c: Context) {
        val list = readAll(c)
        loaded = true
        android.os.Handler(android.os.Looper.getMainLooper()).post { tracks = list }
    }

    /** The list, read now if it hasn't been yet (for the search, which runs off the main thread). */
    fun tracksNow(c: Context): List<GpxTrack> {
        if (loaded) return tracks
        loaded = true
        val list = readAll(c)
        android.os.Handler(android.os.Looper.getMainLooper()).post { tracks = list }
        return list
    }

    private fun readAll(c: Context): List<GpxTrack> = try {
        (dir(c).listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()).mapNotNull { f ->
            try {
                val o = JSONObject(f.readText())
                // where it starts (the first point), for the search's distance
                val a = o.optJSONArray("pts")
                val st = if (a != null && a.length() >= 2) LL(a.getDouble(0), a.getDouble(1)) else null
                GpxTrack(f.nameWithoutExtension, o.optString("name"), o.optDouble("len", 0.0), o.optLong("at", 0L), emptyList(), o.optString("group"), st)
            } catch (e: Exception) { null }
        }.sortedByDescending { it.at }
    } catch (e: Exception) { emptyList() }

    /** The groups in use, A to Z (GitHub #162). */
    fun groups(): List<String> = tracks.map { it.group }.filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() }

    /** Files a track in a group ("" for none). */
    fun setGroup(c: Context, id: String, group: String) {
        val g = group.trim().take(24)
        try {
            val f = File(dir(c), "$id.json")
            val o = JSONObject(f.readText()).put("group", g)
            f.writeText(o.toString())
        } catch (e: Exception) { return }
        tracks = tracks.map { if (it.id == id) GpxTrack(it.id, it.name, it.len, it.at, emptyList(), g, it.start) else it }
    }

    /** Renames a group: every track in it moves to the new name. */
    fun renameGroup(c: Context, from: String, to: String) {
        val t = to.trim().take(24)
        if (t.isBlank() || t == from) return
        for (tr in tracks.filter { it.group == from }) setGroup(c, tr.id, t)
    }

    /** The track with its points, or null if it can't be read. (Call off the main thread.) */
    fun read(c: Context, id: String): GpxTrack? = try {
        val o = JSONObject(File(dir(c), "$id.json").readText())
        val a = o.getJSONArray("pts")
        val pts = ArrayList<LL>(a.length() / 2)
        var i = 0
        while (i + 1 < a.length()) { pts += LL(a.getDouble(i), a.getDouble(i + 1)); i += 2 }
        GpxTrack(id, o.optString("name"), o.optDouble("len", 0.0), o.optLong("at", 0L), pts, o.optString("group"), pts.firstOrNull())
    } catch (e: Exception) { null }

    fun delete(c: Context, id: String) {
        try { File(dir(c), "$id.json").delete() } catch (e: Exception) { }
        tracks = tracks.filter { it.id != id }
    }

    /** Imports the picked file. Null when it worked, or why it didn't. (Call off the main thread.) */
    fun import(c: Context, uri: Uri): String? {
        val (name, raw) = try { c.contentResolver.openInputStream(uri)?.use { parse(it) } ?: return "Couldn't open that file" }
            catch (e: Exception) { return "That isn't a GPX file" }
        if (raw.size < 2) return "That file has no track in it"
        // merge points closer than 15 m, then cap the count
        val thin = ArrayList<LL>()
        for (p in raw) { val last = thin.lastOrNull(); if (last == null || Geo.dist(last, p) >= 15.0) thin += p }
        if (raw.last() !== thin.last()) thin += raw.last()
        val pts = if (thin.size <= 4000) thin else { val step = thin.size / 4000.0; (0 until 4000).map { thin[(it * step).toInt()] } + thin.last() }
        var len = 0.0
        for (i in 1 until pts.size) len += Geo.dist(pts[i - 1], pts[i])
        val fileName = try {
            c.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
                if (cur.moveToFirst()) cur.getString(0) else null
            }
        } catch (e: Exception) { null }
        val title = name.ifBlank { fileName?.substringBeforeLast('.') ?: "" }.ifBlank { "Track" }.take(60)
        val id = System.currentTimeMillis().toString()
        val arr = JSONArray()
        for (p in pts) { arr.put(Math.round(p.lat * 1e6) / 1e6); arr.put(Math.round(p.lon * 1e6) / 1e6) }
        val o = JSONObject().put("name", title).put("len", len).put("at", System.currentTimeMillis()).put("pts", arr)
        return try {
            File(dir(c), "$id.json").writeText(o.toString())
            val t = GpxTrack(id, title, len, System.currentTimeMillis(), emptyList(), "", pts.first())
            android.os.Handler(android.os.Looper.getMainLooper()).post { tracks = listOf(t) + tracks.filter { it.id != id } }
            null
        } catch (e: Exception) { "Couldn't save the track" }
    }

    /**
     * The track's name and points: the track points (trkpt) of every segment, or failing those a route's points (rtept), or
     * failing those its waypoints (wpt) in order.
     */
    private fun parse(input: java.io.InputStream): Pair<String, List<LL>> {
        val p = android.util.Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(input, null)
        val trk = ArrayList<LL>(); val rte = ArrayList<LL>(); val wpt = ArrayList<LL>()
        var name = ""
        var inTrkOrRte = 0
        var inWpt = false
        var ev = p.eventType
        var read = 0
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                val tag = p.name.substringAfter(':')
                when (tag) {
                    "trk", "rte" -> inTrkOrRte++
                    "trkpt", "rtept", "wpt" -> {
                        if (tag == "wpt") inWpt = true
                        val la = p.getAttributeValue(null, "lat")?.toDoubleOrNull(); val lo = p.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        if (la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0) {
                            when (tag) { "trkpt" -> trk += LL(la, lo); "rtept" -> rte += LL(la, lo); else -> wpt += LL(la, lo) }
                            // (a guard against enormous files on a head unit with little memory)
                            if (++read > 400_000) break
                        }
                    }
                    "name" -> if (name.isBlank() && !inWpt && (inTrkOrRte > 0 || p.depth <= 3)) name = try { p.nextText().trim() } catch (e: Exception) { "" }
                }
            } else if (ev == XmlPullParser.END_TAG) {
                val tag = p.name.substringAfter(':')
                if (tag == "trk" || tag == "rte") inTrkOrRte--
                if (tag == "wpt") inWpt = false
            }
            ev = p.next()
        }
        return name to (if (trk.size >= 2) trk else if (rte.size >= 2) rte else wpt)
    }

    /**
     * Points along the track for the route to go through (silently: no "stop reached"), from where it starts: one every so often,
     * at most [max] (Mapbox takes 25 points in all), never closer than 1 km, and not the very start or end.
     */
    fun viaPoints(pts: List<LL>, max: Int = 18): List<LL> {
        if (pts.size < 3) return emptyList()
        var len = 0.0
        for (i in 1 until pts.size) len += Geo.dist(pts[i - 1], pts[i])
        val gap = maxOf(1000.0, len / (max + 1))
        val out = ArrayList<LL>()
        var acc = 0.0
        for (i in 1 until pts.size - 1) {
            acc += Geo.dist(pts[i - 1], pts[i])
            if (acc >= gap && len - (out.size + 1) * gap > gap * 0.5) { out += pts[i]; acc = 0.0; if (out.size >= max) break }
        }
        return out
    }
}
