package com.headunit.launcher

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Something along the route from OpenStreetMap: motorway services, a speed bump or a school. */
class RoutePoi(val kind: String, val name: String, val pos: LL)

/** An average speed check from OpenStreetMap: where it starts and ends, and its limit (m/s, or null when not mapped). */
class AvgDef(val id: Long, val name: String, val starts: List<LL>, val ends: List<LL>, val limit: Double?)

/** The same things placed on a particular route, by distance from the route start (metres). */
class PoiOnRoute(val poi: RoutePoi, val off: Double, val side: Double)
class AvgOnRoute(val def: AvgDef, val start: Double, val end: Double)

/** Live state of the average speed check you are in. */
/** [safeMs]: the most you can do for the rest of the zone and still average under the limit (0 = can't any more; null = not known yet). */
class AvgInfo(val avgMs: Double, val limitMs: Double?, val leftM: Double, val over: Boolean, val ahead: Boolean, val toStartM: Double, val safeMs: Double? = null)

/** Next motorway services ahead. */
class ServiceAhead(val name: String, val dist: Double, val fuel: String)

/** A marker on the route bar: what it is and how far along the route. */
class BarEvent(val off: Double, val kind: String)

object RouteExtras {
    /** Points along the route between two distances, about every [step] metres (used as an Overpass "around" line). */
    fun line(r: Route, from: Double, to: Double, step: Double, maxPts: Int): List<LL> {
        val out = ArrayList<LL>()
        if (r.pts.size < 2) return out
        val a = max(0.0, from); val b = min(r.len, to)
        if (b <= a) return out
        val st = max(step, (b - a) / maxPts)
        var last = -1e9
        for (i in r.pts.indices) {
            val c = r.cum[i]
            if (c < a) continue
            if (c > b) { out += r.pts[i]; break }
            if (c - last >= st) { out += r.pts[i]; last = c }
        }
        if (out.size == 1) out += r.pts.last()
        return out
    }

    /** Distance along the route of the closest point to [p], and how far [p] is from the route. Null when it is further than [maxOff]. */
    fun project(r: Route, p: LL, maxOff: Double, fromOff: Double = 0.0): Pair<Double, Double>? {
        val pts = r.pts
        if (pts.size < 2) return null
        val kx = cos(Math.toRadians(p.lat)) * 111320.0
        val ky = 110574.0
        var bd = maxOff; var bo = -1.0
        var i = 0
        while (i < pts.size - 1) {
            if (r.cum[i + 1] < fromOff) { i++; continue }
            val ax = (pts[i].lon - p.lon) * kx; val ay = (pts[i].lat - p.lat) * ky
            val bx = (pts[i + 1].lon - p.lon) * kx; val by = (pts[i + 1].lat - p.lat) * ky
            // the whole segment lies well off to one side: it cannot be the closest
            if ((ax > maxOff && bx > maxOff) || (ax < -maxOff && bx < -maxOff) || (ay > maxOff && by > maxOff) || (ay < -maxOff && by < -maxOff)) { i++; continue }
            val dx = bx - ax; val dy = by - ay
            val l2 = dx * dx + dy * dy
            val t = if (l2 > 0) (-(ax * dx + ay * dy) / l2).coerceIn(0.0, 1.0) else 0.0
            val d = hypot(ax + dx * t, ay + dy * t)
            if (d < bd) { bd = d; bo = r.cum[i] + (r.cum[i + 1] - r.cum[i]) * t }
            i++
        }
        return if (bo >= 0) bo to bd else null
    }

    private fun poly(line: List<LL>) = line.joinToString(",") { String.format(java.util.Locale.US, "%.5f,%.5f", it.lat, it.lon) }

    /** Motorway services and rest areas next to the route (blocking). */
    fun services(line: List<LL>): List<RoutePoi> {
        if (line.size < 2) return emptyList()
        val els = Overpass.query("[out:json][timeout:40];(node(around:350,${poly(line)})[\"highway\"~\"^(services|rest_area)$\"];way(around:350,${poly(line)})[\"highway\"~\"^(services|rest_area)$\"];);out center tags 120;", 12000, 50000, 3)
        val out = ArrayList<RoutePoi>()
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val t = e.optJSONObject("tags") ?: JSONObject()
            val pos = if (e.has("lat")) LL(e.getDouble("lat"), e.getDouble("lon")) else e.optJSONObject("center")?.let { LL(it.getDouble("lat"), it.getDouble("lon")) } ?: continue
            val rest = t.optString("highway") == "rest_area"
            val name = t.optString("name").ifBlank { t.optString("operator") }.ifBlank { if (rest) "Rest area" else "Services" }
            out += RoutePoi(if (rest) "rest" else "services", name, pos)
        }
        return out
    }

    /** Speed bumps and schools right next to the route, and average speed checks around it (blocking). */
    fun hazards(line: List<LL>): Pair<List<RoutePoi>, List<AvgDef>> {
        if (line.size < 2) return emptyList<RoutePoi>() to emptyList()
        var minLat = 90.0; var maxLat = -90.0; var minLon = 180.0; var maxLon = -180.0
        for (p in line) { minLat = min(minLat, p.lat); maxLat = max(maxLat, p.lat); minLon = min(minLon, p.lon); maxLon = max(maxLon, p.lon) }
        val bbox = String.format(java.util.Locale.US, "%.5f,%.5f,%.5f,%.5f", minLat - 0.01, minLon - 0.015, maxLat + 0.01, maxLon + 0.015)
        val pl = poly(line)
        val q = "[out:json][timeout:30];" +
            "(node(around:14,$pl)[\"traffic_calming\"~\"^(bump|hump|table|cushion)$\"];" +
            "node(around:90,$pl)[\"amenity\"=\"school\"];way(around:90,$pl)[\"amenity\"=\"school\"];)->.a;.a out center tags 300;" +
            "node(around:10,$pl)[\"highway\"=\"crossing\"][\"crossing\"~\"^(zebra|marked|traffic_signals|uncontrolled|pelican|toucan|puffin|pegasus)$\"]->.c;.c out body 400;" +
            "node(around:12,$pl)[\"highway\"=\"stop\"]->.s;.s out body 300;" +
            "(node(around:12,$pl)[\"railway\"=\"level_crossing\"];node(around:12,$pl)[\"ford\"=\"yes\"];way(around:12,$pl)[\"ford\"=\"yes\"];)->.f;.f out center tags 200;" +
            "rel($bbox)[\"enforcement\"=\"average_speed\"]->.r;.r out body;node(r.r);out skel qt;"
        val els = Overpass.query(q, 12000, 40000, 3)
        val pois = ArrayList<RoutePoi>()
        // (crossings come from their own set, so they never crowd out the bumps and schools)
        val nodes = HashMap<Long, LL>()
        val rels = ArrayList<JSONObject>()
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            when (e.optString("type")) {
                "relation" -> rels += e
                else -> {
                    val pos = if (e.has("lat")) LL(e.getDouble("lat"), e.getDouble("lon")) else e.optJSONObject("center")?.let { LL(it.getDouble("lat"), it.getDouble("lon")) }
                    if (pos != null && e.optString("type") == "node") nodes[e.optLong("id")] = pos
                    val t = e.optJSONObject("tags") ?: continue
                    if (pos == null) continue
                    if (t.optString("highway") == "stop") pois += RoutePoi("stop", "Stop", pos)
                    else if (t.optString("railway") == "level_crossing") pois += RoutePoi("rail", "Level crossing", pos)
                    else if (t.optString("ford") == "yes") pois += RoutePoi("ford", "Ford", pos)
                    else if (t.optString("highway") == "crossing") pois += RoutePoi("crossing", t.optString("crossing"), pos)
                    else if (t.has("traffic_calming")) pois += RoutePoi("bump", t.optString("traffic_calming"), pos)
                    else if (t.optString("amenity") == "school") pois += RoutePoi("school", t.optString("name").ifBlank { "School" }, pos)
                }
            }
        }
        val avgs = ArrayList<AvgDef>()
        for (rel in rels) {
            val ms = rel.optJSONArray("members") ?: continue
            val from = ArrayList<LL>(); val to = ArrayList<LL>(); val dev = ArrayList<LL>()
            for (k in 0 until ms.length()) {
                val m = ms.getJSONObject(k)
                if (m.optString("type") != "node") continue
                val p = nodes[m.optLong("ref")] ?: continue
                when (m.optString("role")) { "from" -> from += p; "to" -> to += p; else -> dev += p }
            }
            val t = rel.optJSONObject("tags") ?: JSONObject()
            val lim = t.optString("maxspeed").takeIf { it.isNotBlank() }?.let { Overpass.parseSpeed(it, "GB") }
            val starts = from.ifEmpty { dev }; val ends = to.ifEmpty { dev }
            if (starts.isEmpty() || ends.isEmpty()) continue
            avgs += AvgDef(rel.optLong("id"), t.optString("name").ifBlank { "Average speed check" }, starts, ends, lim)
        }
        return pois to avgs
    }

    /** Places an average speed check on the route: it runs from its first start point to its last end point. */
    fun placeAvg(r: Route, d: AvgDef): AvgOnRoute? {
        val s = d.starts.mapNotNull { project(r, it, 80.0)?.first }.minOrNull() ?: return null
        val e = d.ends.mapNotNull { project(r, it, 80.0)?.first }.maxOrNull() ?: return null
        return if (e - s in 400.0..60_000.0) AvgOnRoute(d, s, e) else null
    }

    /** True on weekday school-run times (term dates are not known, so holidays are included). */
    fun schoolTime(now: Long): Boolean {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val d = c.get(java.util.Calendar.DAY_OF_WEEK)
        if (d == java.util.Calendar.SATURDAY || d == java.util.Calendar.SUNDAY) return false
        val m = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
        return m in (8 * 60)..(9 * 60 + 30) || m in (14 * 60 + 45)..(16 * 60 + 15)
    }

    /** Compass point for a heading in degrees. */
    fun compass(h: Double): String = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((h % 360.0) + 360.0 + 22.5) / 45.0).toInt() % 8]
}


/** Bends on a route that are not junctions: (distance along the route, -1 left / +1 right). */
fun routeBends(r: Route): List<Pair<Double, Int>> {
    val n = r.pts.size
    if (n < 3) return emptyList()
    val out = ArrayList<Pair<Double, Int>>()
    val span = 35.0
    var a = 0; var b = 0
    var lastAt = -1e9
    val stepOffs = r.steps.map { it.off }
    var si = 0
    for (i in 1 until n - 1) {
        val c = r.cum[i]
        while (a < i && r.cum[a] < c - span) a++
        if (b < i) b = i
        while (b < n - 1 && r.cum[b] < c + span) b++
        if (a >= i || b <= i || c - r.cum[a] < span * 0.6 || r.cum[b] - c < span * 0.6) continue
        val h1 = Geo.bearing(r.pts[a], r.pts[i]); val h2 = Geo.bearing(r.pts[i], r.pts[b])
        val d = (h2 - h1 + 540.0) % 360.0 - 180.0
        if (abs(d) < 48.0 || c - lastAt < 150.0) continue
        while (si < stepOffs.size - 1 && stepOffs[si] < c - 60.0) si++
        if (stepOffs.isNotEmpty() && abs(stepOffs[si] - c) < 60.0) continue   // that's a junction, not a bend
        lastAt = c
        out += c to (if (d < 0) -1 else 1)
    }
    return out
}
