package com.headunit.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Street lamps from OpenStreetMap, fetched online for the area you're in (so they show without an offline pack), and which roads
 * OpenStreetMap says are lit or unlit (its "lit" tag, mapped on far more roads than the lamps themselves are). The lamps on the
 * map use the real ones where people have mapped them, and only put guessed lamps on roads that aren't marked unlit.
 *
 * Fetched in squares of about 2 km, one at a time in the background, kept for a month on the unit (so the same streets aren't
 * asked for again) and never while offline. A square that couldn't be fetched is tried again after a few minutes.
 */
object OsmLamps {
    /** A square's lamps (lat, lon) and its roads marked lit (true) or unlit (false), each as lat, lon, lat, lon… */
    class Area(val lamps: List<DoubleArray>, val ways: List<Pair<Boolean, DoubleArray>>)

    private const val T = 0.02
    private const val KEEP_MS = 30L * 86_400_000L

    private val mem = android.util.LruCache<String, Area>(16)
    private val failedAt = HashMap<String, Long>()
    @Volatile private var busy = false
    private val exec = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "osm-lamps").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }

    private fun key(i: Int, j: Int) = "${i}_$j"

    /**
     * The squares covering [south]..[north], [west]..[east] that are already to hand. Never waits: a missing square is fetched in
     * the background (or read from the unit), and [ready] is called once it is, so the lamps can be placed again.
     */
    fun around(ctx: Context, south: Double, west: Double, north: Double, east: Double, ready: () -> Unit): List<Area> {
        val out = ArrayList<Area>()
        val i0 = Math.floor(south / T).toInt(); val i1 = Math.floor(north / T).toInt()
        val j0 = Math.floor(west / T).toInt(); val j1 = Math.floor(east / T).toInt()
        for (i in i0..i1) for (j in j0..j1) {
            val k = key(i, j)
            val a = mem.get(k)
            if (a != null) { out += a; continue }
            fetch(ctx, i, j, ready)
        }
        return out
    }

    private fun fetch(ctx: Context, i: Int, j: Int, ready: () -> Unit) {
        val k = key(i, j)
        synchronized(failedAt) {
            if (busy) return
            val f = failedAt[k]
            if (f != null && System.currentTimeMillis() - f < 5 * 60_000L) return
            busy = true
        }
        try {
            exec.execute {
                try {
                    val a = fromDisk(ctx, k) ?: (if (Net.offline) null else download(ctx, i, j, k))
                    if (a != null) { mem.put(k, a); ready() } else synchronized(failedAt) { failedAt[k] = System.currentTimeMillis() }
                } catch (e: Throwable) {
                    synchronized(failedAt) { failedAt[k] = System.currentTimeMillis() }
                } finally { busy = false }
            }
        } catch (e: Throwable) { busy = false }
    }

    private fun file(ctx: Context, k: String) = File(File(ctx.cacheDir, "osmlamps").apply { mkdirs() }, "$k.json")

    private fun fromDisk(ctx: Context, k: String): Area? {
        val f = file(ctx, k)
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > KEEP_MS) return null
        return try { parse(JSONObject(f.readText())) } catch (e: Exception) { null }
    }

    private fun download(ctx: Context, i: Int, j: Int, k: String): Area? {
        val s = i * T; val w = j * T; val n = s + T; val e = w + T
        val bb = String.format(java.util.Locale.US, "%.5f,%.5f,%.5f,%.5f", s, w, n, e)
        // the lamps, and the roads (not footpaths) that say whether they're lit
        val q = "[out:json][timeout:25];(node[\"highway\"=\"street_lamp\"]($bb);" +
            "way[\"highway\"~\"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|road)(_link)?$\"][\"lit\"~\"^(yes|no)$\"]($bb););out geom qt;"
        val els = Overpass.query(q, connectMs = 10000, readMs = 30000, tries = 2)
        val lamps = JSONArray(); val ways = JSONArray()
        for (x in 0 until els.length()) {
            val o = els.optJSONObject(x) ?: continue
            when (o.optString("type")) {
                "node" -> lamps.put(JSONArray().put(round(o.optDouble("lat"))).put(round(o.optDouble("lon"))))
                "way" -> {
                    val g = o.optJSONArray("geometry") ?: continue
                    val lit = o.optJSONObject("tags")?.optString("lit") == "yes"
                    val pts = JSONArray().put(if (lit) 1 else 0)
                    for (y in 0 until g.length()) { val p = g.optJSONObject(y) ?: continue; pts.put(round(p.optDouble("lat"))).put(round(p.optDouble("lon"))) }
                    if (pts.length() >= 5) ways.put(pts)
                }
            }
        }
        val got = JSONObject().put("l", lamps).put("w", ways)
        try { file(ctx, k).writeText(got.toString()) } catch (e: Exception) { }
        return parse(got)
    }

    private fun round(v: Double) = Math.round(v * 1e6) / 1e6

    private fun parse(j: JSONObject): Area {
        val l = j.optJSONArray("l") ?: JSONArray()
        val lamps = (0 until l.length()).mapNotNull { x -> l.optJSONArray(x)?.let { doubleArrayOf(it.optDouble(0), it.optDouble(1)) } }
        val w = j.optJSONArray("w") ?: JSONArray()
        val ways = (0 until w.length()).mapNotNull { x ->
            val a = w.optJSONArray(x) ?: return@mapNotNull null
            if (a.length() < 5) return@mapNotNull null
            Pair(a.optInt(0) == 1, DoubleArray(a.length() - 1) { a.optDouble(it + 1) })
        }
        return Area(lamps, ways)
    }
}
