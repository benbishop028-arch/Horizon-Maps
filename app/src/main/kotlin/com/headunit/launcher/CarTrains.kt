package com.headunit.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.cos
import kotlin.math.hypot

/**
 * The world's car-train lines (Resul9704, #176): the Channel Tunnel shuttle, the Swiss and Austrian Alpine tunnel trains and the
 * like, from OpenStreetMap (route=shuttle_train). Mapbox's route doesn't mark these stretches as a train (and exclude=ferry leaves
 * them in), so with "Avoid car trains" on, a route is checked against this list. Fetched once (a few hundred lines worldwide) and
 * kept on the device for a month.
 */
object CarTrains {
    @Volatile var filesDir: File? = null
    @Volatile private var lines: List<List<LL>>? = null
    @Volatile private var failAt = 0L
    private val lock = Any()

    private fun list(): List<List<LL>> = synchronized(lock) {
        lines?.let { return it }
        val dir = filesDir ?: return emptyList()
        val f = File(dir, "cartrains.json")
        val now = System.currentTimeMillis()
        if ((!f.exists() || now - f.lastModified() > 30L * 86_400_000L) && now - failAt > 30 * 60_000L) {
            try {
                // the lines tagged as car trains themselves, and the track of car-train routes mapped as a relation
                val els = Overpass.query("[out:json][timeout:60];(way[\"route\"=\"shuttle_train\"];relation[\"route\"=\"shuttle_train\"]->.r;way(r.r)[\"railway\"];);out geom;", 12000, 70000, 2)
                val arr = JSONArray()
                for (i in 0 until els.length()) {
                    val g = els.optJSONObject(i)?.optJSONArray("geometry") ?: continue
                    val pts = JSONArray()
                    for (j in 0 until g.length()) { val p = g.optJSONObject(j) ?: continue; pts.put(p.optDouble("lat")).put(p.optDouble("lon")) }
                    if (pts.length() >= 4) arr.put(pts)
                }
                if (arr.length() > 0) f.writeText(arr.toString()) else failAt = now
            } catch (e: Exception) { failAt = now }
        }
        val out = ArrayList<List<LL>>()
        try {
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    val a = arr.optJSONArray(i) ?: continue
                    val pts = ArrayList<LL>(a.length() / 2)
                    var k = 0
                    while (k + 1 < a.length()) { pts += LL(a.getDouble(k), a.getDouble(k + 1)); k += 2 }
                    out += pts
                }
            }
        } catch (e: Exception) { }
        if (out.isNotEmpty()) lines = out
        return out
    }

    /**
     * Points on a Mapbox route ([r], with geojson geometry) where it rides a car train: the middle of each stretch of the route
     * that runs along one of the lines, to leave out by position. Empty when it takes none (or the list can't be had).
     */
    fun pointsOn(r: JSONObject): List<LL> {
        val c = r.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return emptyList()
        if (c.length() < 2) return emptyList()
        val route = ArrayList<LL>(c.length())
        var minLat = 90.0; var maxLat = -90.0; var minLon = 180.0; var maxLon = -180.0
        for (i in 0 until c.length()) {
            val p = c.optJSONArray(i) ?: continue
            val ll = LL(p.optDouble(1), p.optDouble(0)); route += ll
            minLat = minOf(minLat, ll.lat); maxLat = maxOf(maxLat, ll.lat); minLon = minOf(minLon, ll.lon); maxLon = maxOf(maxLon, ll.lon)
        }
        val all = list()
        if (all.isEmpty()) return emptyList()
        val out = ArrayList<LL>()
        for (line in all) {
            // (only lines inside the route's box are looked at closely)
            if (line.none { it.lat in minLat - 0.02..maxLat + 0.02 && it.lon in minLon - 0.03..maxLon + 0.03 }) continue
            // the route's points right on this line (25 m: a road running beside the track isn't mistaken for it), over two
            // kilometres or more: riding the train. The point left out is the line's own nearest point, on the track itself
            val near = route.filter { p -> nearLine(p, line, 25.0) }
            if (near.size >= 3 && Geo.dist(near.first(), near.last()) > 2000.0) {
                val mid = near[near.size / 2]
                out += closestOn(mid, line)
            }
        }
        return out
    }

    /** The point on [line] closest to [p] (a long tunnel can be a single straight piece, so not just its nearest corner). */
    private fun closestOn(p: LL, line: List<LL>): LL {
        val kx = cos(Math.toRadians(p.lat)) * 111_320.0
        val ky = 110_540.0
        var best = Double.MAX_VALUE; var out = p
        for (i in 0 until line.size - 1) {
            val ax = (line[i].lon - p.lon) * kx; val ay = (line[i].lat - p.lat) * ky
            val bx = (line[i + 1].lon - p.lon) * kx; val by = (line[i + 1].lat - p.lat) * ky
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 <= 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val d = hypot(ax + t * dx, ay + t * dy)
            if (d < best) { best = d; out = LL(p.lat + (ay + t * dy) / ky, p.lon + (ax + t * dx) / kx) }
        }
        return out
    }

    private fun nearLine(p: LL, line: List<LL>, m: Double): Boolean {
        val kx = cos(Math.toRadians(p.lat)) * 111_320.0
        val ky = 110_540.0
        for (i in 0 until line.size - 1) {
            val ax = (line[i].lon - p.lon) * kx; val ay = (line[i].lat - p.lat) * ky
            val bx = (line[i + 1].lon - p.lon) * kx; val by = (line[i + 1].lat - p.lat) * ky
            // distance from the point (at 0,0) to the segment a-b
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 <= 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            if (hypot(ax + t * dx, ay + t * dy) <= m) return true
        }
        return false
    }
}
