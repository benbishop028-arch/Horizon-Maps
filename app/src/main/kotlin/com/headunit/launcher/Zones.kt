package com.headunit.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Clean Air Zones, ULEZ, low emission zones and charging zones in Great Britain, from OpenStreetMap
 * (boundary=low_emission_zone and similar). Downloaded once and refreshed monthly; only as good as the map data.
 */
object Zones {
    class Zone(val name: String, val rings: List<List<LL>>) {
        val minLat = rings.minOf { r -> r.minOf { it.lat } }
        val maxLat = rings.maxOf { r -> r.maxOf { it.lat } }
        val minLon = rings.minOf { r -> r.minOf { it.lon } }
        val maxLon = rings.maxOf { r -> r.maxOf { it.lon } }
    }

    @Volatile private var cache: List<Zone>? = null
    @Volatile private var fetching = false
    @Volatile var status = ""

    private fun file(ctx: Context) = File(ctx.filesDir, "zones.json")

    /** The zones known on this device (empty until the first download has worked). */
    fun all(ctx: Context): List<Zone> {
        cache?.let { return it }
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        val out = ArrayList<Zone>()
        try {
            val a = JSONArray(f.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val rs = o.getJSONArray("r")
                val rings = (0 until rs.length()).map { k ->
                    val pa = rs.getJSONArray(k)
                    (0 until pa.length() / 2).map { j -> LL(pa.getDouble(j * 2), pa.getDouble(j * 2 + 1)) }
                }.filter { it.size >= 4 }
                if (rings.isNotEmpty()) out += Zone(o.optString("n"), rings)
            }
        } catch (e: Exception) { }
        cache = out
        status = if (out.isEmpty()) "No zones found in the map data" else "${out.size} zones: " + out.joinToString(", ") { it.name }.take(200)
        return out
    }

    fun needsRefresh(ctx: Context): Boolean {
        val f = file(ctx)
        return !f.exists() || System.currentTimeMillis() - f.lastModified() > 30L * 86_400_000L
    }

    /** Joins the outer ways of a boundary into closed rings. */
    private fun stitch(ways: List<List<LL>>): List<List<LL>> {
        val left = ways.filter { it.size >= 2 }.toMutableList()
        val rings = ArrayList<List<LL>>()
        fun same(a: LL, b: LL) = Math.abs(a.lat - b.lat) < 1e-7 && Math.abs(a.lon - b.lon) < 1e-7
        while (left.isNotEmpty()) {
            val ring = ArrayList(left.removeAt(0))
            var grew = true
            while (!same(ring.first(), ring.last()) && grew) {
                grew = false
                val it = left.iterator()
                while (it.hasNext()) {
                    val w = it.next()
                    when {
                        same(ring.last(), w.first()) -> { ring.addAll(w.drop(1)); it.remove(); grew = true }
                        same(ring.last(), w.last()) -> { ring.addAll(w.asReversed().drop(1)); it.remove(); grew = true }
                        else -> continue
                    }
                    if (same(ring.first(), ring.last())) break
                }
            }
            if (ring.size >= 4) rings += ring
        }
        return rings
    }

    /** Keeps at most [max] points of a ring, so checks and route avoidance stay quick. */
    private fun thin(r: List<LL>, max: Int): List<LL> {
        if (r.size <= max) return r
        val step = Math.ceil(r.size / max.toDouble()).toInt()
        return r.filterIndexed { i, _ -> i % step == 0 } + r.last()
    }

    /** Downloads the zones for Great Britain (blocking: call it off the main thread). */
    fun fetch(ctx: Context) {
        if (fetching) return
        fetching = true
        try {
            val q = "[out:json][timeout:90];(relation[\"boundary\"~\"^(low_emission_zone|congestion_charge|clean_air_zone)$\"](49.8,-8.7,60.9,1.9);" +
                "way[\"boundary\"~\"^(low_emission_zone|congestion_charge|clean_air_zone)$\"](49.8,-8.7,60.9,1.9););out geom;"
            val els = Overpass.query(q, 15000, 120000, 3)
            val out = JSONArray()
            var n = 0
            for (i in 0 until els.length()) {
                val e = els.getJSONObject(i)
                val tags = e.optJSONObject("tags") ?: JSONObject()
                val name = tags.optString("name").ifBlank { tags.optString("official_name") }.ifBlank { "Low emission zone" }
                val ways = ArrayList<List<LL>>()
                if (e.optString("type") == "way") {
                    val g = e.optJSONArray("geometry") ?: continue
                    ways += (0 until g.length()).map { k -> g.getJSONObject(k).let { LL(it.getDouble("lat"), it.getDouble("lon")) } }
                } else {
                    val ms = e.optJSONArray("members") ?: continue
                    for (k in 0 until ms.length()) {
                        val m = ms.getJSONObject(k)
                        if (m.optString("type") != "way" || m.optString("role") == "inner") continue
                        val g = m.optJSONArray("geometry") ?: continue
                        ways += (0 until g.length()).mapNotNull { j -> g.optJSONObject(j)?.let { LL(it.getDouble("lat"), it.getDouble("lon")) } }
                    }
                }
                val rings = stitch(ways).map { thin(it, 400) }
                if (rings.isEmpty()) continue
                val ra = JSONArray()
                for (r in rings) { val pa = JSONArray(); for (p in r) { pa.put(Math.round(p.lat * 1e5) / 1e5); pa.put(Math.round(p.lon * 1e5) / 1e5) }; ra.put(pa) }
                out.put(JSONObject().put("n", name).put("r", ra))
                n++
            }
            file(ctx).writeText(out.toString())
            cache = null
            all(ctx)
            if (n == 0) status = "No zones found in the map data"
        } catch (e: Exception) {
            status = "Zone download failed: " + (e.message ?: e.javaClass.simpleName).take(120)
        } finally { fetching = false }
    }

    fun inside(z: Zone, p: LL): Boolean {
        if (p.lat < z.minLat || p.lat > z.maxLat || p.lon < z.minLon || p.lon > z.maxLon) return false
        var inside = false
        for (r in z.rings) {
            var j = r.size - 1
            for (i in r.indices) {
                val a = r[i]; val b = r[j]
                if ((a.lat > p.lat) != (b.lat > p.lat) && p.lon < (b.lon - a.lon) * (p.lat - a.lat) / (b.lat - a.lat) + a.lon) inside = !inside
                j = i
            }
        }
        return inside
    }

    /** Where a route goes into each zone: (zone, metres from the route start). A route that starts inside a zone counts as entering it at 0. */
    fun entries(r: Route, zones: List<Zone>): List<Pair<Zone, Double>> {
        if (zones.isEmpty() || r.pts.size < 2) return emptyList()
        var minLat = 90.0; var maxLat = -90.0; var minLon = 180.0; var maxLon = -180.0
        for (p in r.pts) { if (p.lat < minLat) minLat = p.lat; if (p.lat > maxLat) maxLat = p.lat; if (p.lon < minLon) minLon = p.lon; if (p.lon > maxLon) maxLon = p.lon }
        val out = ArrayList<Pair<Zone, Double>>()
        for (z in zones) {
            if (z.maxLat < minLat || z.minLat > maxLat || z.maxLon < minLon || z.minLon > maxLon) continue
            var last = -1e9
            var wasIn = false
            for (i in r.pts.indices) {
                if (r.cum[i] - last < 150.0 && i != r.pts.size - 1) continue
                last = r.cum[i]
                val inZ = inside(z, r.pts[i])
                if (inZ && !wasIn) { out += z to r.cum[i]; break }
                wasIn = inZ
            }
        }
        return out.sortedBy { it.second }
    }
}
