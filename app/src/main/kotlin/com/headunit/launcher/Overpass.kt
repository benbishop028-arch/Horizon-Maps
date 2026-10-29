package com.headunit.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A place found nearby or along the route. [price] is pence per litre (fuel), [detourMin] the rough extra time to call there (-1 when not known). */
/** [price] is in pence or cents a litre; [unit] says which ("p" or "€"), so it can be shown the local way (139.9p, €1.789). */
data class Nearby(val place: Place, val dist: Double, val extra: String, val price: Double? = null, val detourMin: Int = -1, val ahead: Boolean = false, val unit: String = "p")

/** A fuel price the local way: "139.9p" in the UK, "€1.789" in the euro countries. [price] is in pence or cents a litre. */
fun fmtFuelPrice(price: Double, unit: String): String =
    if (unit == "€") String.format(java.util.Locale.UK, "€%.3f", price / 100.0) else String.format(java.util.Locale.UK, "%.1fp", price)

/** Free OpenStreetMap queries: speed limits, speed cameras and nearby fuel / charging / parking. */
object Overpass {
    private val endpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )

    /** The server that answered last time is tried first. */
    @Volatile private var preferred = 0

    /** What the last quick query did, for the settings page. */
    @Volatile var lastNote = ""

    /** Runs a query for other parts of the app (route extras, zones). */
    fun query(q: String, connectMs: Int = 10000, readMs: Int = 30000, tries: Int = endpoints.size): JSONArray = run(q, connectMs, readMs, tries)

    private fun run(query: String, connectMs: Int = 8000, readMs: Int = 18000, tries: Int = endpoints.size): JSONArray {
        val errors = ArrayList<String>()
        for (n in 0 until minOf(tries, endpoints.size)) {
            val idx = (preferred + n) % endpoints.size
            val ep = endpoints[idx]
            val host = ep.removePrefix("https://").substringBefore('/')
            try {
                val c = URL(ep).openConnection() as HttpURLConnection
                c.connectTimeout = connectMs
                c.readTimeout = readMs
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("User-Agent", "HorizonNav/1.0 (Android navigation app)")
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
                val code = c.responseCode
                if (code !in 200..299) throw IOException("HTTP $code")
                val body = c.inputStream.bufferedReader().readText()
                val j = JSONObject(body)
                // a busy server often answers "OK" with no results and a note saying it gave up: that's a failure, not "nothing here"
                val remark = j.optString("remark")
                if (remark.contains("error", true) || remark.contains("timed out", true) || remark.contains("out of memory", true) || remark.contains("timeout", true))
                    throw IOException("busy (" + remark.take(50) + ")")
                val els = j.optJSONArray("elements") ?: JSONArray()
                preferred = idx
                lastNote = host.removePrefix("www.").substringBefore('.') + " ok"
                return els
            } catch (e: Exception) { errors += host.removePrefix("www.").substringBefore('.') + ": " + (e.message ?: e.javaClass.simpleName) }
        }
        lastNote = errors.joinToString("; ")
        throw IOException(errors.joinToString("; "))
    }

    private const val MPH = 0.44704

    /** Country of a position, from where it is rather than the head unit's language setting (which is often en-US). */
    fun countryAt(p: LL): String {
        val inGb = p.lat in 49.8..60.9 && p.lon in -8.7..1.8 &&
            !(p.lon < -5.4 && (p.lat < 54.0 || p.lon < -8.2 || (p.lon < -7.25 && p.lat > 54.65)))
        return if (inGb) "GB" else java.util.Locale.getDefault().country
    }

    /** UK limits are only ever 20, 30, 40, 50, 60 or 70 mph (plus 5, 10, 15 in car parks and yards). */
    private val gbMph = intArrayOf(5, 10, 15, 20, 30, 40, 50, 60, 70)
    private fun snapGb(ms: Double): Double {
        val mph = ms / MPH
        var best = gbMph[0]
        for (v in gbMph) if (Math.abs(v - mph) < Math.abs(best - mph) - 1e-9) best = v
        return best * MPH
    }

    fun parseSpeed(s: String, country: String): Double? {
        val t = s.trim().lowercase()
        if (country == "GB") {
            when (t) {
                "gb:nsl_single", "uk:nsl_single" -> return 60 * MPH
                "gb:nsl_dual", "uk:nsl_dual", "gb:motorway", "uk:motorway" -> return 70 * MPH
                "gb:urban", "uk:urban", "gb:nsl_restricted", "uk:nsl_restricted" -> return 30 * MPH
            }
        }
        val m = Regex("^(\\d+)\\s*(mph|km/h|kmh|kph)?$").find(t) ?: return null
        val v = m.groupValues[1].toDouble()
        val unit = m.groupValues[2]
        // in Britain every limit is in mph, even when the mapper left the unit off
        if (country == "GB") return snapGb(if (unit == "mph" || unit.isEmpty()) v * MPH else v / 3.6)
        return if (unit == "mph") v * MPH else v / 3.6
    }

    /**
     * A limit for the road at a point. [wayId] identifies the stretch of road (a new one usually means the limit may change),
     * [names] holds its name and road numbers (to check another source is talking about the same road), and [sure] says the
     * road was matched closely and runs the way you are heading.
     */
    class Limit(val ms: Double, val estimated: Boolean, val road: String = "", val wayId: Long = 0L, val names: List<String> = emptyList(), val sure: Boolean = false)

    /** Set when the last speed-limit lookup failed (no network, server busy…), null when it worked. */
    @Volatile var lastError: String? = null

    private val driveable = setOf(
        "motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link", "secondary", "secondary_link",
        "tertiary", "tertiary_link", "unclassified", "residential", "living_street", "service", "road",
    )

    /** Typical limit for a road without a posted one, from its type and the country (m/s). */
    private fun estimate(tags: JSONObject, country: String): Double? {
        val hw = tags.optString("highway")
        if (hw !in driveable) return null
        val link = hw.endsWith("_link")
        val base = hw.removeSuffix("_link")
        val lit = tags.optString("lit") == "yes"
        val oneway = tags.optString("oneway") == "yes"
        val mph = 0.44704
        val kmh = 1.0 / 3.6
        return when (country) {
            "GB" -> when (base) {
                "motorway" -> if (link) 50 * mph else 70 * mph
                "trunk", "primary", "secondary", "tertiary", "unclassified" ->
                    if (link) 40 * mph else if (lit) 30 * mph else if (oneway && (base == "trunk" || base == "primary")) 70 * mph else 60 * mph
                "residential" -> 30 * mph
                "living_street" -> 20 * mph
                "service" -> 10 * mph
                else -> 30 * mph
            }
            "US" -> when (base) {
                "motorway" -> if (link) 45 * mph else 65 * mph
                "trunk", "primary" -> if (lit) 35 * mph else 55 * mph
                "secondary", "tertiary" -> if (lit) 30 * mph else 45 * mph
                "unclassified" -> 35 * mph
                "residential" -> 25 * mph
                "living_street" -> 10 * mph
                else -> 15 * mph
            }
            else -> when (base) {
                "motorway" -> if (link) 60 * kmh else 130 * kmh
                "trunk", "primary" -> if (link) 60 * kmh else if (lit) 50 * kmh else 100 * kmh
                "secondary", "tertiary", "unclassified" -> if (link) 50 * kmh else if (lit) 50 * kmh else 80 * kmh
                "residential" -> 30 * kmh
                "living_street" -> 10 * kmh
                else -> 20 * kmh
            }
        }
    }

    /** A road: its OpenStreetMap id, tags and shape. */
    class Way(val id: Long, val tags: JSONObject, val g: List<LL>) {
        /** south, west, north, east of the shape (worked out once) */
        val box: DoubleArray by lazy {
            var s = 90.0; var w = 180.0; var n = -90.0; var e = -180.0
            for (p in g) { s = minOf(s, p.lat); n = maxOf(n, p.lat); w = minOf(w, p.lon); e = maxOf(e, p.lon) }
            doubleArrayOf(s, w, n, e)
        }
    }

    /** Roads from a downloaded area (set by the engine): null when no downloaded area covers the spot, so the online search is used. */
    @Volatile var localWays: ((LL) -> List<Way>?)? = null
    private var cacheAt: LL? = null
    @Volatile private var cacheWays: List<Way> = emptyList()

    private fun bearingOf(a: LL, b: LL): Double {
        val dy = b.lat - a.lat
        val dx = (b.lon - a.lon) * Math.cos(Math.toRadians(a.lat))
        return (Math.toDegrees(Math.atan2(dx, dy)) + 360.0) % 360.0
    }

    /** Loads the roads around [p] once and keeps them, so the next few hundred metres of lookups need no network at all. */
    private fun loadWays(centre: LL): List<Way> {
        val els = run("[out:json][timeout:10];way(around:220,${centre.lat},${centre.lon})[\"highway\"];out tags geom 160;")
        val out = ArrayList<Way>()
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: continue
            if (tags.optString("highway") !in driveable) continue
            val g = e.optJSONArray("geometry") ?: continue
            val pts = (0 until g.length()).mapNotNull { n -> g.optJSONObject(n)?.let { o -> LL(o.getDouble("lat"), o.getDouble("lon")) } }
            if (pts.size >= 2) out += Way(e.optLong("id", 0L), tags, pts)
        }
        return out
    }

    /**
     * Speed limit of the road at [p]: the posted one when OpenStreetMap has it, otherwise the usual limit for that kind of road.
     * [heading] (degrees, or negative when unknown) is used to prefer the road you are driving along over one that crosses it.
     */
    fun speedLimit(p: LL, heading: Double = -1.0, country: String = countryAt(p)): Limit? = try {
        // a downloaded area with speed limits covers this spot: its roads, straight from the file (no network)
        val local = try { localWays?.invoke(p) } catch (e: Throwable) { null }
        if (local != null) { cacheWays = local; cacheAt = null }
        val c0 = cacheAt
        if (local == null && (c0 == null || Geo.dist(c0, p) > 150.0)) {
            // fetch well ahead of the car so the next stretch is already known (one download covers a few hundred metres)
            val ah = if (heading >= 0) 110.0 else 0.0
            val ahead = LL(p.lat + ah * Math.cos(Math.toRadians(heading)) / 111320.0, p.lon + ah * Math.sin(Math.toRadians(heading)) / (111320.0 * Math.cos(Math.toRadians(p.lat))))
            try { cacheWays = loadWays(ahead); cacheAt = ahead }
            catch (e: Exception) { if (c0 == null || Geo.dist(c0, p) > 210.0) throw e }
        }
        var best: Way? = null
        var bd = 1e9; var bDist = 1e9; var bDiff = 90.0; var bAligned = false
        val mLat = 111320.0
        val mLon = 111320.0 * Math.cos(Math.toRadians(p.lat))
        for (w in cacheWays) {
            var d = 1e9; var brg = 0.0
            for (n in 0 until w.g.size - 1) {
                val a = w.g[n]; val b = w.g[n + 1]
                val ax = (a.lon - p.lon) * mLon; val ay = (a.lat - p.lat) * mLat
                val bx = (b.lon - p.lon) * mLon; val by = (b.lat - p.lat) * mLat
                val dx = bx - ax; val dy = by - ay
                val l2 = dx * dx + dy * dy
                val t = if (l2 > 0) ((-ax * dx - ay * dy) / l2).coerceIn(0.0, 1.0) else 0.0
                val ex = ax + t * dx; val ey = ay + t * dy
                val dd = Math.hypot(ex, ey)
                if (dd < d) { d = dd; brg = bearingOf(a, b) }
            }
            var pen = if (w.tags.optString("highway") in setOf("service", "living_street")) 15.0 else 0.0
            var diff = 90.0
            var aligned = heading < 0
            if (heading >= 0 && d < 60.0) {
                diff = Math.abs(((brg - heading + 540.0) % 360.0) - 180.0)   // 0 = same way as the road is drawn, 180 = opposite
                val oneway = w.tags.optString("oneway") == "yes"
                aligned = if (oneway) diff <= 30.0 else (diff >= 150.0 || diff <= 30.0)
                val back = oneway && diff >= 150.0
                // a road crossing yours (a side road at a junction) is heavily penalised, so its limit is not picked up as you pass it
                if (back) pen += 40.0 else if (!aligned) pen += if (diff in 50.0..130.0) 35.0 else 12.0
            }
            if (d + pen < bd) { bd = d + pen; best = w; bDist = d; bDiff = diff; bAligned = aligned }
        }
        lastError = null
        val w = if (bd <= 30.0) best else null
        if (w == null) null
        else {
            val t = w.tags
            // a limit that differs by direction: forward is the way the road is drawn
            val dirKey = if (heading >= 0 && bDiff > 90.0) "maxspeed:backward" else "maxspeed:forward"
            val posted = parseSpeed(t.optString(dirKey), country)
                ?: parseSpeed(t.optString("maxspeed"), country)
                ?: parseSpeed(t.optString("maxspeed:type"), country)
                ?: parseSpeed(t.optString("source:maxspeed"), country)
                ?: zoneSpeed(t.optString("maxspeed").ifBlank { t.optString("zone:maxspeed").ifBlank { t.optString("source:maxspeed") } }, country)
            val ref = t.optString("ref").split(";").firstOrNull()?.trim().orEmpty()
            val nm = t.optString("name").trim()
            val major = t.optString("highway") in setOf("motorway", "trunk", "primary", "motorway_link", "trunk_link")
            val road = if (major && ref.isNotBlank()) ref else nm.ifBlank { ref }
            val names = (listOf(nm) + t.optString("ref").split(";").map { it.trim() }).filter { it.isNotBlank() }
            val sure = bDist <= 18.0 && bAligned
            if (posted != null) Limit(posted, false, road, w.id, names, sure)
            else estimate(t, country)?.let { Limit(if (country == "GB") snapGb(it) else it, true, road, w.id, names, sure) }
                ?: Limit(-1.0, true, road, w.id, names, sure)
        }
    } catch (e: Exception) { lastError = e.message ?: e.javaClass.simpleName; null }

    /** Limits given as a zone ("DE:urban", "FR:rural", "DE:zone30"): the usual urban limit is 50 km/h almost everywhere in Europe. */
    private fun zoneSpeed(v: String, country: String): Double? {
        val t = v.trim().lowercase()
        if (!t.contains(':')) return null
        val kind = t.substringAfter(':')
        Regex("zone:?(\\d+)").find(kind)?.let { return it.groupValues[1].toDouble().let { n -> if (country == "GB" || country == "US") n * MPH else n / 3.6 } }
        if (country == "GB" || country == "US") return null
        return when (kind) {
            "urban" -> 50.0 / 3.6
            "living_street" -> 20.0 / 3.6
            "motorway" -> when (country) { "DE" -> null; else -> 130.0 / 3.6 }
            else -> null      // "rural" differs too much between countries to guess
        }
    }

    /**
     * The nearest point on a road running your way (within 24 m), from the roads already loaded for the speed limit (downloaded or
     * looked up), or null when none are loaded near [p]. Cheap, and never waits on the map.
     */
    fun snapToRoad(p: LL, heading: Double): LL? {
        val ways = cacheWays
        if (ways.isEmpty()) return null
        val mLat = 111320.0
        val mLon = 111320.0 * Math.cos(Math.toRadians(p.lat))
        var best: LL? = null; var bd = 24.0
        for (w in ways) {
            val b = w.box
            if (p.lat < b[0] - 0.0005 || p.lat > b[2] + 0.0005 || p.lon < b[1] - 0.0008 || p.lon > b[3] + 0.0008) continue
            for (n in 0 until w.g.size - 1) {
                val a = w.g[n]; val c = w.g[n + 1]
                val ax = (a.lon - p.lon) * mLon; val ay = (a.lat - p.lat) * mLat
                val bx = (c.lon - p.lon) * mLon; val by = (c.lat - p.lat) * mLat
                val dx = bx - ax; val dy = by - ay
                val l2 = dx * dx + dy * dy
                if (l2 < 0.01) continue
                val t = (-(ax * dx + ay * dy) / l2).coerceIn(0.0, 1.0)
                val d = Math.hypot(ax + dx * t, ay + dy * t)
                if (d >= bd) continue
                var diff = Math.abs(bearingOf(a, c) - heading) % 360.0
                if (diff > 180.0) diff = 360.0 - diff
                if (diff > 35.0 && diff < 145.0) continue
                bd = d
                best = LL(p.lat + (ay + dy * t) / mLat, p.lon + (ax + dx * t) / mLon)
            }
        }
        return best
    }

    /**
     * True when the nearest road under you is one-way and you are heading against it, with no road going your way just as close.
     * Uses the roads already loaded for the speed limit, so it needs no extra download.
     */
    fun wrongWay(p: LL, heading: Double): Boolean {
        val ways = cacheWays
        if (ways.isEmpty()) return false
        val mLat = 111320.0
        val mLon = 111320.0 * Math.cos(Math.toRadians(p.lat))
        var aligned = 1e9; var against = 1e9
        for (w in ways) {
            var d = 1e9; var brg = 0.0
            for (n in 0 until w.g.size - 1) {
                val a = w.g[n]; val b = w.g[n + 1]
                val ax = (a.lon - p.lon) * mLon; val ay = (a.lat - p.lat) * mLat
                val bx = (b.lon - p.lon) * mLon; val by = (b.lat - p.lat) * mLat
                val dx = bx - ax; val dy = by - ay
                val l2 = dx * dx + dy * dy
                val t = if (l2 > 0) ((-ax * dx - ay * dy) / l2).coerceIn(0.0, 1.0) else 0.0
                val dd = Math.hypot(ax + t * dx, ay + t * dy)
                if (dd < d) { d = dd; brg = bearingOf(a, b) }
            }
            if (d > 25.0) continue
            val ow = w.tags.optString("oneway")
            val hw = w.tags.optString("highway")
            val jn = w.tags.optString("junction")
            val oneway = ow == "yes" || ow == "true" || ow == "1" || ow == "-1" || jn == "roundabout" || jn == "circular" || hw == "motorway" || hw == "motorway_link"
            var diff = Math.abs(((brg - heading + 540.0) % 360.0) - 180.0)   // 0 = same way as the line is drawn
            if (ow == "-1") diff = 180.0 - diff
            if (!oneway) { if (diff <= 35.0 || diff >= 145.0) aligned = minOf(aligned, d) }
            else if (diff <= 35.0) aligned = minOf(aligned, d)
            else if (diff >= 150.0) against = minOf(against, d)
        }
        return against < 8.0 && aligned > against + 6.0
    }

    /** Traffic signals inside a box (used to draw traffic lights on the map). */
    fun signals(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<LL> = run("[out:json][timeout:15];node($minLat,$minLon,$maxLat,$maxLon)[\"highway\"=\"traffic_signals\"];out skel 400;", 10000, 20000, 2).let { els ->
        (0 until els.length()).mapNotNull {
            val e = els.getJSONObject(it)
            if (e.has("lat") && e.has("lon")) LL(e.getDouble("lat"), e.getDouble("lon")) else null
        }
    }

    /**
     * Which way each known speed camera checks traffic (degrees, 0 = north), from OpenStreetMap's direction tag, keyed by [camKey].
     * Cameras without one aren't in here (they count for both directions).
     */
    val camDir = java.util.concurrent.ConcurrentHashMap<Long, Double>()

    fun camKey(p: LL): Long = Math.round(p.lat * 1e5) * 1000003L + Math.round(p.lon * 1e5)

    private val cardinal = mapOf("N" to 0.0, "NNE" to 22.5, "NE" to 45.0, "ENE" to 67.5, "E" to 90.0, "ESE" to 112.5, "SE" to 135.0, "SSE" to 157.5,
        "S" to 180.0, "SSW" to 202.5, "SW" to 225.0, "WSW" to 247.5, "W" to 270.0, "WNW" to 292.5, "NW" to 315.0, "NNW" to 337.5)

    /** A direction tag as degrees, or null when it's missing or covers both ways ("90;270", "both", "forward" along an unknown road). */
    fun parseDir(v: String?): Double? {
        val t = v?.trim()?.uppercase() ?: return null
        if (t.isEmpty() || t.contains(';')) return null
        cardinal[t]?.let { return it }
        return t.toDoubleOrNull()?.let { ((it % 360.0) + 360.0) % 360.0 }
    }

    private fun noteDir(p: LL, tags: JSONObject?) {
        val d = parseDir(tags?.optString("direction")?.ifBlank { null } ?: tags?.optString("camera:direction"))
        if (d != null) camDir[camKey(p)] = d
    }

    /** Big background searches (along the route) go one at a time, so they don't crowd out the quick ones or get the app refused. */
    private val bgGate = java.util.concurrent.Semaphore(1, true)

    /** Traffic lights and speed cameras within [radius] metres of the line through [pts], in one request. Throws on failure. */
    fun routeFeatures(pts: List<LL>, radius: Int): Pair<List<LL>, List<LL>> {
        val line = pts.joinToString(",") { String.format(java.util.Locale.US, "%.6f,%.6f", it.lat, it.lon) }
        val q = "[out:json][timeout:25];(node(around:$radius,$line)[\"highway\"=\"traffic_signals\"];node(around:$radius,$line)[\"highway\"=\"speed_camera\"];);out 3000;"
        bgGate.acquire()
        val els = try { run(q, 12000, 35000) } finally { bgGate.release() }
        val sig = ArrayList<LL>(); val cam = ArrayList<LL>()
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            if (!e.has("lat") || !e.has("lon")) continue
            val p = LL(e.getDouble("lat"), e.getDouble("lon"))
            if (e.optJSONObject("tags")?.optString("highway") == "speed_camera") { cam += p; noteDir(p, e.optJSONObject("tags")) } else sig += p
        }
        return sig to cam
    }

    /** Speed cameras within [radius] m of [p]; null when the lookup failed (so the ones already shown are kept, not wiped). */
    fun cameras(p: LL, radius: Int): List<LL>? = try {
        val els = run("[out:json][timeout:15];node(around:$radius,${p.lat},${p.lon})[\"highway\"=\"speed_camera\"];out;", 10000, 20000)
        (0 until els.length()).mapNotNull {
            val e = els.getJSONObject(it)
            if (e.has("lat") && e.has("lon")) LL(e.getDouble("lat"), e.getDouble("lon")).also { c -> noteDir(c, e.optJSONObject("tags")) } else null
        }
    } catch (e: Exception) { null }

    /** Speed cameras inside a bounding box (used to find the ones along a route). */
    fun camerasIn(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<LL> = try {
        val els = run("[out:json][timeout:15];node($minLat,$minLon,$maxLat,$maxLon)[\"highway\"=\"speed_camera\"];out;")
        (0 until els.length()).mapNotNull {
            val e = els.getJSONObject(it)
            if (e.has("lat") && e.has("lon")) LL(e.getDouble("lat"), e.getDouble("lon")) else null
        }
    } catch (e: Exception) { emptyList() }

    /** OpenStreetMap filter for each kind of place that can be searched for nearby or along the route. */
    private fun filterOf(kind: String): String = when (kind) {
        "fuel" -> "[\"amenity\"=\"fuel\"]"
        // stations mapped as selling bioethanol (E85)
        "fuel_e85" -> "[\"amenity\"=\"fuel\"][\"fuel:e85\"=\"yes\"]"
        "charging" -> "[\"amenity\"=\"charging_station\"]"
        "food" -> "[\"amenity\"~\"^(restaurant|fast_food)$\"]"
        "coffee" -> "[\"amenity\"=\"cafe\"]"
        "toilets" -> "[\"amenity\"=\"toilets\"]"
        "services" -> "[\"highway\"~\"^(services|rest_area)$\"]"
        else -> "[\"amenity\"=\"parking\"]"
    }

    fun kindLabel(kind: String): String = when (kind) {
        "fuel" -> "Fuel station"; "fuel_e85" -> "E85 station"; "charging" -> "Charging point"; "food" -> "Food"; "coffee" -> "Coffee"
        "toilets" -> "Toilets"; "services" -> "Services"; else -> "Car park"
    }

    /** kind: "fuel", "charging", "parking", "food", "coffee", "toilets" or "services". */
    fun nearby(kind: String, c: LL, radius: Int): List<Nearby> {
        val f = filterOf(kind)
        val q = "[out:json][timeout:20];(node(around:$radius,${c.lat},${c.lon})$f;way(around:$radius,${c.lat},${c.lon})$f;);out center tags 80;"
        return parseNearby(run(q, 6000, 12000, 2), kind, c).sortedBy { it.dist }.take(40)
    }

    /** Places within [radius] metres of a line (the route ahead), in no particular order. */
    fun nearbyAlong(kind: String, line: List<LL>, radius: Int): List<Nearby> {
        if (line.size < 2) return emptyList()
        val f = filterOf(kind)
        val poly = line.joinToString(",") { String.format(java.util.Locale.US, "%.5f,%.5f", it.lat, it.lon) }
        val q = "[out:json][timeout:25];(node(around:$radius,$poly)$f;way(around:$radius,$poly)$f;);out center tags 150;"
        return parseNearby(run(q, 6000, 15000, 2), kind, line.first())
    }

    private fun parseNearby(els: JSONArray, kind: String, c: LL): List<Nearby> {
        val out = ArrayList<Nearby>()
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: JSONObject()
            val pos = if (e.has("lat")) LL(e.getDouble("lat"), e.getDouble("lon"))
            else e.optJSONObject("center")?.let { LL(it.getDouble("lat"), it.getDouble("lon")) } ?: continue
            val label = kindLabel(kind)
            val name = tags.optString("name").ifBlank { tags.optString("brand") }.ifBlank { tags.optString("operator") }.ifBlank { label }
            val extra = ArrayList<String>()
            when (kind) {
                "fuel", "fuel_e85" -> {
                    tags.optString("brand").takeIf { it.isNotBlank() && it != name }?.let { extra += it }
                    tags.optString("opening_hours").takeIf { it.isNotBlank() }?.let { extra += it }
                }
                "charging" -> {
                    val sockets = ArrayList<String>()
                    val it2 = tags.keys()
                    while (it2.hasNext()) {
                        val k = it2.next()
                        if (k.startsWith("socket:") && k.count { ch -> ch == ':' } == 1) {
                            val nice = k.removePrefix("socket:").replace("_", " ")
                            val cnt = tags.optString(k)
                            sockets += if (cnt.toIntOrNull() != null) "$nice ×$cnt" else nice
                        }
                    }
                    if (sockets.isNotEmpty()) extra += sockets.take(3).joinToString(", ")
                    tags.optString("capacity").takeIf { it.isNotBlank() }?.let { extra += "$it points" }
                    tags.optString("operator").takeIf { it.isNotBlank() && it != name }?.let { extra += it }
                }
                "parking" -> {
                    when (tags.optString("fee")) { "yes" -> extra += "Paid"; "no" -> extra += "Free" }
                    tags.optString("capacity").takeIf { it.isNotBlank() }?.let { extra += "$it spaces" }
                    when (tags.optString("parking")) { "multi-storey" -> extra += "Multi-storey"; "underground" -> extra += "Underground" }
                }
                "food", "coffee" -> {
                    tags.optString("cuisine").takeIf { it.isNotBlank() }?.let { extra += it.replace("_", " ").replace(";", ", ") }
                    tags.optString("opening_hours").takeIf { it.isNotBlank() }?.let { extra += it }
                }
                "toilets" -> {
                    when (tags.optString("fee")) { "yes" -> extra += "Paid"; "no" -> extra += "Free" }
                    if (tags.optString("wheelchair") == "yes") extra += "Accessible"
                }
                else -> {
                    tags.optString("operator").takeIf { it.isNotBlank() && it != name }?.let { extra += it }
                }
            }
            out += Nearby(Place(name, tags.optString("addr:street"), pos), Geo.dist(c, pos), extra.joinToString(" · "))
        }
        return out
    }
}
