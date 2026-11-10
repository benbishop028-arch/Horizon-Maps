package com.headunit.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.max
import kotlin.math.min

/**
 * Mapbox, with your own access token (free from mapbox.com): the map in Mapbox's own looks, satellite imagery, routes with live
 * traffic (and the speed limit of each stretch of road along them), incidents on the route, place search and speed limits when not
 * on a route (from the roads matched to your last few positions).
 *
 * (In Horizon Nav the map itself is drawn by the Mapbox Maps SDK; this file is the data side: routes, search, limits.)
 */
object MapboxRest {
    private const val API = "https://api.mapbox.com"

    /** A public token ("pk.…") looks usable. Secret tokens ("sk.…") work too but shouldn't be put in an app. */
    /** A pasted token with anything that can't be part of one taken out: spaces, line breaks, and the invisible characters some
     *  phones and websites slip in when copying (one of those makes Mapbox answer 401 for a token that looks exactly right). */
    fun clean(t: String) = t.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '_' || it == '-' }

    fun looksValid(t: String) = t.length > 30 && (t.startsWith("pk.") || t.startsWith("sk.") || t.startsWith("tk."))

    /**
     * Checked without a connection: a whole token is three parts, and its middle part is readable data naming the Mapbox account.
     * A token typed in by hand, or cut short when copying, usually fails this. (Only used for a warning, never to refuse a token.)
     */
    fun looksWhole(t: String): Boolean = try {
        val p = t.split('.')
        if (p.size != 3 || p[1].length < 8 || p[2].length < 10) false
        else {
            val json = String(android.util.Base64.decode(p[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING), Charsets.UTF_8).trim()
            json.startsWith("{") && json.endsWith("}") && json.contains("\"u\"")
        }
    } catch (e: Exception) { false }

    /**
     * Mapbox's own verdict on a token (its Tokens API, which works for any token): "TokenValid", "TokenMalformed", "TokenInvalid",
     * "TokenExpired" or "TokenRevoked", with the account it belongs to. Null when Mapbox couldn't be asked (no signal).
     */
    fun checkToken(t: String): Pair<String, String>? = try {
        val c = URL("$API/tokens/v2?access_token=" + enc(t)).also { com.headunit.launcher.Net.check() }.openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 10000
        c.setRequestProperty("User-Agent", "HorizonNav/1.0")
        try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            val o = try { JSONObject(body) } catch (e: Exception) { null }
            val v = o?.optString("code").orEmpty()
            when {
                v.startsWith("Token") -> v to (o?.optJSONObject("token")?.optString("user").orEmpty())
                code == 401 -> "TokenInvalid" to ""
                else -> null
            }
        } finally { try { c.disconnect() } catch (e: Exception) { } }
    } catch (e: Exception) { null }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun httpError(code: Int, body: String): IOException = IOException(when (code) {
        401 -> "Mapbox didn't accept the access token (401). Check it in Settings > Keys & accounts"
        403 -> "This Mapbox token isn't allowed to use that service (403). Check its scopes at account.mapbox.com"
        429 -> "Mapbox's request limit was reached. Try again in a minute"
        else -> "HTTP $code ${body.take(140)}"
    })

    private fun get(url: String, connectMs: Int = 12000, readMs: Int = 20000): String {
        val c = URL(url).also { com.headunit.launcher.Net.check() }.openConnection() as HttpURLConnection
        c.connectTimeout = connectMs
        c.readTimeout = readMs
        c.setRequestProperty("User-Agent", "HorizonNav/1.0")
        val code = try { c.responseCode } catch (e: IOException) { NetHealth.note(e); throw IOException("no signal") }
        val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        if (code !in 200..299) throw httpError(code, body)
        return body
    }

    // ------------------------------------------------------------------ the map

    /** Mapbox's own looks: Navigation (made for driving, with live traffic), Streets, and Satellite with roads and labels. */
    fun styleId(look: String, dark: Boolean): String = when (look) {
        // the Horizon look starts from Mapbox's driving map (it has the traffic lines) and the app recolours it
        "horizon" -> if (dark) "navigation-night-v1" else "navigation-day-v1"
        "streets" -> if (dark) "dark-v11" else "streets-v12"
        "satellite" -> "satellite-streets-v12"
        else -> if (dark) "navigation-night-v1" else "navigation-day-v1"
    }

    private val styleCache = HashMap<String, String>()

    /**
     * The style for MapLibre, as JSON text: Mapbox's style with its "mapbox://" addresses turned into web addresses carrying the
     * token, and the few parts MapLibre can't draw (Mapbox-only effects) left out. [traffic] off removes the live traffic lines.
     * Kept in memory and on disk, so the map starts with no signal too.
     */
    fun styleJson(ctx: android.content.Context, token: String, look: String, dark: Boolean, traffic: Boolean): String {
        val id = styleId(look, dark)
        val key = "$id|$look|$traffic|${token.hashCode()}"
        synchronized(styleCache) { styleCache[key]?.let { return it } }
        val file = java.io.File(ctx.cacheDir, "mapbox-$id.json")
        val raw = try {
            get("$API/styles/v1/mapbox/$id?access_token=${enc(token)}").also { try { file.writeText(it) } catch (e: Exception) { } }
        } catch (e: IOException) {
            // no signal: the copy from last time, if there is one (the token check still happens on the map's own requests)
            if ((e.message ?: "") == "no signal" && file.exists()) file.readText() else throw e
        }
        val out = convert(JSONObject(raw), token, traffic, look == "horizon").toString()
        synchronized(styleCache) { styleCache[key] = out }
        return out
    }

    private val dropRoot = setOf("fog", "projection", "terrain", "lights", "imports", "schema", "camera", "models", "fragment", "color-theme", "featuresets", "iconsets", "rain", "snow")
    private val dropTypes = setOf("sky", "model", "clip", "slot", "raster-particle", "building")

    /** Paint and layout properties only Mapbox's own engine understands (MapLibre would refuse the whole layer). */
    private fun mapboxOnly(k: String) = k.contains("emissive") || k.contains("occlusion") || k.contains("flood-light") || k.contains("rounded-roof") ||
        k.contains("ground-attenuation") || k.contains("vertical-scale") || k.contains("cutoff-fade") || k.contains("z-offset") || k.contains("elevation") ||
        k == "line-trim-offset" || k == "line-trim-fade-range" || k == "line-trim-color" || k == "symbol-z-elevate" || k == "icon-image-cross-fade" ||
        k == "fill-extrusion-base-alignment" || k == "fill-extrusion-height-alignment" || k == "fill-extrusion-line-width" || k == "line-border-width" ||
        k == "line-border-color" || k == "fill-tunnel-structure-color" || k == "fill-bridge-guard-rail-color" || k == "symbol-elevation-reference" ||
        k == "icon-size-scale-range" || k == "text-size-scale-range" || k == "fill-extrusion-cast-shadows" || k == "fill-z-offset" || k == "line-width-unit"

    private fun convert(st: JSONObject, token: String, traffic: Boolean, threeD: Boolean): JSONObject {
        val t = enc(token)
        for (k in dropRoot) st.remove(k)
        // icons
        when (val sp = st.opt("sprite")) {
            is String -> st.put("sprite", spriteUrl(sp, t))
            is JSONArray -> {
                // several sprite sheets: MapLibre takes the same form; each address converted
                for (i in 0 until sp.length()) sp.optJSONObject(i)?.let { o -> o.optString("url").takeIf { it.isNotBlank() }?.let { o.put("url", spriteUrl(it, t)) } }
            }
        }
        // label fonts
        st.optString("glyphs").takeIf { it.startsWith("mapbox://fonts/") }?.let {
            st.put("glyphs", "$API/fonts/v1/" + it.removePrefix("mapbox://fonts/") + "?access_token=$t")
        }
        // map data
        val srcs = st.optJSONObject("sources") ?: JSONObject()
        val names = srcs.keys().asSequence().toList()
        for (n in names) {
            val s = srcs.optJSONObject(n) ?: continue
            val url = s.optString("url")
            if (!url.startsWith("mapbox://")) continue
            val ids = url.removePrefix("mapbox://")
            s.remove("url")
            when (s.optString("type")) {
                "vector" -> {
                    s.put("tiles", JSONArray().put("$API/v4/$ids/{z}/{x}/{y}.vector.pbf?access_token=$t"))
                    s.put("minzoom", 0); s.put("maxzoom", 16)
                }
                "raster" -> {
                    s.put("tiles", JSONArray().put("$API/v4/$ids/{z}/{x}/{y}@2x.jpg90?access_token=$t"))
                    s.put("tileSize", 256); s.put("maxzoom", 22)
                }
                "raster-dem" -> {
                    s.put("tiles", JSONArray().put("$API/v4/$ids/{z}/{x}/{y}.pngraw?access_token=$t"))
                    s.put("tileSize", 256); s.put("maxzoom", 14); s.put("encoding", "mapbox")
                }
                else -> { srcs.remove(n); continue }
            }
            if (!s.has("attribution")) s.put("attribution", "© Mapbox © OpenStreetMap")
        }
        // layers MapLibre can draw (and, with traffic off, without the traffic lines)
        val layers = st.optJSONArray("layers") ?: JSONArray()
        val kept = JSONArray()
        var has3d = (0 until layers.length()).any { layers.optJSONObject(it)?.optString("type") == "fill-extrusion" }
        for (i in 0 until layers.length()) {
            val l = layers.optJSONObject(i) ?: continue
            if (l.optString("type") in dropTypes) continue
            val src = l.optString("source")
            if (src.isNotEmpty() && !srcs.has(src)) continue
            if (!traffic && (l.optString("source-layer") == "traffic" || l.optString("id").contains("traffic", true))) continue
            l.remove("slot")
            for (part in listOf("paint", "layout")) {
                val o = l.optJSONObject(part) ?: continue
                for (k in o.keys().asSequence().toList()) if (mapboxOnly(k) || k.endsWith("-transition") && mapboxOnly(k.removeSuffix("-transition"))) o.remove(k)
            }
            kept.put(l)
            // the Horizon look: 3D buildings from Mapbox's building heights, straight after its flat buildings (the app colours them
            // and switches them with its 3D buildings setting)
            if (threeD && !has3d && l.optString("type") == "fill" && l.optString("source-layer") == "building") {
                has3d = true
                kept.put(building3d(l.optString("source")))
            }
        }
        // no flat building layer to follow (some looks leave buildings out): the 3D ones go under the first labels
        val compSrc = names.firstOrNull { (srcs.optJSONObject(it)?.optJSONArray("tiles")?.optString(0) ?: "").contains("mapbox-streets-v8") }
        if (threeD && !has3d && compSrc != null) {
            val withB = JSONArray()
            var added = false
            for (i in 0 until kept.length()) {
                val l = kept.getJSONObject(i)
                if (!added && l.optString("type") == "symbol") { withB.put(building3d(compSrc)); added = true }
                withB.put(l)
            }
            if (!added) withB.put(building3d(compSrc))
            st.put("layers", withB)
            return st
        }
        st.put("layers", kept)
        return st
    }

    /** 3D buildings from Mapbox's building heights (rising as you zoom in, as in the app's other maps). */
    private fun building3d(source: String): JSONObject = JSONObject()
        .put("id", "building-3d").put("type", "fill-extrusion").put("source", source).put("source-layer", "building")
        .put("minzoom", 15)
        .put("filter", JSONArray("[\"all\", [\"!=\", [\"get\", \"extrude\"], \"false\"], [\"!=\", [\"get\", \"underground\"], \"true\"]]"))
        .put("paint", JSONObject()
            .put("fill-extrusion-color", "#E7E5E1")
            .put("fill-extrusion-height", JSONArray("[\"interpolate\", [\"linear\"], [\"zoom\"], 15, 0, 15.6, [\"to-number\", [\"get\", \"height\"], 8]]"))
            .put("fill-extrusion-base", JSONArray("[\"interpolate\", [\"linear\"], [\"zoom\"], 15, 0, 15.6, [\"to-number\", [\"get\", \"min_height\"], 0]]"))
            .put("fill-extrusion-opacity", 0.95))

    private fun spriteUrl(s: String, t: String): String =
        if (s.startsWith("mapbox://sprites/")) "$API/styles/v1/" + s.removePrefix("mapbox://sprites/") + "/sprite?access_token=$t" else s

    /** Satellite imagery tiles (for the satellite view on any map source while a token is set). */
    fun satelliteTiles(token: String) = "$API/v4/mapbox.satellite/{z}/{x}/{y}@2x.jpg90?access_token=${enc(token)}"

    // ------------------------------------------------------------------ routes

    /** True when the last request was accepted with its departure time. */
    @Volatile var departApplied = false

    /** Countries (two-letter codes) where toll roads are avoided even though "Avoid toll roads" is off. */
    @Volatile var tollCountries: Set<String> = emptySet()

    /** The route pays a toll in one of [countries]: a toll stretch (Mapbox marks its junctions "toll") in that country. */
    private fun tollIn(route: JSONObject, countries: Set<String>): Boolean {
        val legs = route.optJSONArray("legs") ?: return false
        for (li in 0 until legs.length()) {
            val leg = legs.optJSONObject(li) ?: continue
            val admins = leg.optJSONArray("admins") ?: continue
            val steps = leg.optJSONArray("steps") ?: continue
            for (si in 0 until steps.length()) {
                val ints = steps.optJSONObject(si)?.optJSONArray("intersections") ?: continue
                for (ii in 0 until ints.length()) {
                    val x = ints.optJSONObject(ii) ?: continue
                    val cl = x.optJSONArray("classes") ?: continue
                    if ((0 until cl.length()).none { cl.optString(it) == "toll" }) continue
                    val cc = admins.optJSONObject(x.optInt("admin_index", 0))?.optString("iso_3166_1")?.uppercase() ?: continue
                    if (cc in countries) return true
                }
            }
        }
        return false
    }

    /** Turns TomTom-style route preferences (avoid tollRoads, motorways, ferries, unpavedRoads) into Mapbox's. */
    private fun excludes(avoid: List<String>): String = avoid.mapNotNull {
        when (it) { "tollRoads" -> "toll"; "motorways" -> "motorway"; "ferries" -> "ferry"; "unpavedRoads" -> "unpaved"; else -> null }
    }.joinToString(",")

    /**
     * Driving routes with live traffic (Mapbox's driving-traffic profile): the line, turn by turn (roundabout exits, motorway exits,
     * lanes), traffic along it, the speed limit of each stretch, and incidents on it. [leftHand]: driving on the left (for U-turn lanes).
     */
    /** Points on closed roads to keep the route off (set before each route; see NavEngine's road closures). */
    @Volatile var avoidPoints: List<LL> = emptyList()
    /** Arrive on your side of the road (the kerb side of where you're going). */
    @Volatile var curbSide = true
    /** Ask for Mapbox's junction pictures (banner instructions). */
    @Volatile var junctionPics = false

    fun routes(token: String, from: LL, to: LL, alternatives: Boolean, departAtMs: Long = 0L, via: List<LL> = emptyList(),
               headingDeg: Double = -1.0, quick: Boolean = false, leftHand: Boolean = false): List<Route> {
        val pts = avoidPoints.take(25)
        if (pts.isEmpty()) return routesWith(token, from, to, alternatives, departAtMs, via, headingDeg, quick, leftHand, "")
        val pex = pts.joinToString(",") { String.format(java.util.Locale.US, "point(%.6f %.6f)", it.lon, it.lat) }
        // with the closed roads left out; if there's then no way at all (or Mapbox won't take them), as before
        return try { routesWith(token, from, to, alternatives, departAtMs, via, headingDeg, quick, leftHand, pex) }
        catch (e: IOException) {
            val m = e.message ?: ""
            if (m == "no signal" || m.startsWith("HTTP 401") || m.startsWith("HTTP 403") || m.startsWith("HTTP 429")) throw e
            routesWith(token, from, to, alternatives, departAtMs, via, headingDeg, quick, leftHand, "")
        }
    }

    private fun routesWith(token: String, from: LL, to: LL, alternatives: Boolean, departAtMs: Long, via: List<LL>,
                           headingDeg: Double, quick: Boolean, leftHand: Boolean, pointEx: String): List<Route> {
        val pts = listOf(from) + via + listOf(to)
        val coords = pts.joinToString(";") { String.format(java.util.Locale.US, "%.6f,%.6f", it.lon, it.lat) }
        val ex = listOf(excludes(TomTomRest.avoid), enc(pointEx)).filter { it.isNotEmpty() }.joinToString(",")
        // (Mapbox's directions have no Thai: an unknown language would fail the route, so English instructions are asked for there)
        val lang = if (L.lang == "th") "en" else L.lang
        var base = "$API/directions/v5/mapbox/driving-traffic/$coords?geometries=geojson&overview=full&steps=true" +
            "&annotations=maxspeed,congestion,duration,distance,freeflow_speed&language=$lang&alternatives=${alternatives && via.isEmpty()}&access_token=${enc(token)}" + Vehicle.mapbox()
        // the way you're facing, so a reroute doesn't send you the wrong way down the road you're on
        if (headingDeg >= 0) base += "&bearings=" + enc((listOf("${headingDeg.toInt() % 360},45") + List(pts.size - 1) { "" }).joinToString(";"))
        // arrive on your side of the road (the destination on the kerb side), the other points as Mapbox likes
        if (curbSide) base += "&approaches=" + enc((List(pts.size - 1) { "" } + listOf("curb")).joinToString(";"))
        // pictures of motorway junctions, when Mapbox has them for this account
        if (junctionPics) base += "&banner_instructions=true"
        val dep = if (departAtMs > 0) "&depart_at=" + enc(java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US).format(java.util.Date(departAtMs))) else ""
        val attempts = ArrayList<String>()
        if (dep.isNotEmpty() && !quick) { if (ex.isNotEmpty()) attempts += "$base&exclude=$ex$dep"; attempts += "$base$dep" }
        if (ex.isNotEmpty()) attempts += "$base&exclude=$ex"
        attempts += base
        // (the same without the free-flow speeds, should Mapbox ever refuse them: the route matters more than the delay)
        attempts += base.replace(",freeflow_speed", "")
        var body: String? = null
        var last: IOException? = null
        departApplied = false
        for (u in attempts) {
            try { body = get(u, if (quick) 8000 else 12000, if (quick) 12000 else 25000); departApplied = u.contains("depart_at="); break }
            catch (e: IOException) {
                last = e
                val m = e.message ?: ""
                // no signal, a refused token or the limit: another variant can't help
                if (!m.startsWith("HTTP") || m.startsWith("HTTP 401") || m.startsWith("HTTP 403") || m.startsWith("HTTP 429") || m.startsWith("Mapbox")) break
            }
        }
        if (body == null) throw last ?: IOException("Routing failed")
        var o = JSONObject(body)
        if (o.optString("code") != "Ok") throw IOException(if (o.optString("code") == "NoRoute") "No route found" else o.optString("message").ifBlank { "Routing failed" })
        // tolls avoided in chosen countries only (Settings > Routes): Mapbox can only leave out toll roads everywhere, so when the
        // best route would pay a toll in one of those countries it's asked again without toll roads
        val tc = tollCountries
        if (tc.isNotEmpty() && !ex.split(',').contains("toll") && o.optJSONArray("routes")?.optJSONObject(0)?.let { tollIn(it, tc) } == true) {
            val ex2 = (ex.split(',').filter { it.isNotBlank() } + "toll").joinToString(",")
            try {
                val o2 = JSONObject(get("$base&exclude=$ex2" + (if (departApplied) dep else ""), if (quick) 8000 else 12000, if (quick) 12000 else 25000))
                if (o2.optString("code") == "Ok" && (o2.optJSONArray("routes")?.length() ?: 0) > 0) o = o2
            } catch (e: IOException) { /* keep the route with the toll rather than none */ }
        }
        val arr = o.optJSONArray("routes") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { try { parse(arr.getJSONObject(it), leftHand) } catch (e: Exception) { null } }
    }

    private fun maneuverOf(type: String, mod: String, leftHand: Boolean): String {
        val left = mod.contains("left"); val right = mod.contains("right")
        fun turn(): String = when {
            mod == "uturn" -> "MAKE_UTURN"
            mod.startsWith("sharp") -> if (left) "SHARP_LEFT" else "SHARP_RIGHT"
            mod.startsWith("slight") -> if (left) "BEAR_LEFT" else "BEAR_RIGHT"
            left -> "TURN_LEFT"
            right -> "TURN_RIGHT"
            else -> "STRAIGHT"
        }
        return when (type) {
            "depart" -> "DEPART"
            "arrive" -> if (left) "ARRIVE_LEFT" else if (right) "ARRIVE_RIGHT" else "ARRIVE"
            "turn", "end of road", "roundabout turn" -> turn()
            "continue", "new name" -> if (mod.isBlank() || mod == "straight") "STRAIGHT" else turn()
            "fork", "merge" -> if (left) "KEEP_LEFT" else if (right) "KEEP_RIGHT" else "STRAIGHT"
            "on ramp" -> if (left) "KEEP_LEFT" else if (right) "KEEP_RIGHT" else "ENTRANCE_RAMP"
            "off ramp" -> if (left) "MOTORWAY_EXIT_LEFT" else "MOTORWAY_EXIT_RIGHT"
            "roundabout", "rotary" -> when {
                mod == "uturn" -> "ROUNDABOUT_BACK"
                mod.startsWith("slight") || mod == "straight" || mod.isBlank() -> "ROUNDABOUT_CROSS"
                left -> "ROUNDABOUT_LEFT"
                else -> "ROUNDABOUT_RIGHT"
            }
            "ferry" -> "TAKE_FERRY"
            else -> turn()
        }
    }

    private fun laneDir(ind: String, leftHand: Boolean): String = when (ind) {
        "uturn" -> if (leftHand) "RIGHT_U_TURN" else "LEFT_U_TURN"
        "sharp left" -> "SHARP_LEFT"; "left" -> "LEFT"; "slight left" -> "SLIGHT_LEFT"
        "sharp right" -> "SHARP_RIGHT"; "right" -> "RIGHT"; "slight right" -> "SLIGHT_RIGHT"
        else -> "STRAIGHT"
    }

    private fun incidentCat(type: String): String = when (type) {
        "accident" -> "accident"; "construction" -> "roadWorks"; "road_closure" -> "roadClosed"; "congestion" -> "jam"
        "lane_restriction" -> "laneClosed"; "disabled_vehicle", "road_hazard", "weather" -> "danger"; else -> "other"
    }

    private fun parse(r: JSONObject, leftHand: Boolean): Route {
        val pts = ArrayList<LL>()
        val g = r.getJSONObject("geometry").getJSONArray("coordinates")
        for (i in 0 until g.length()) { val c = g.getJSONArray(i); pts += LL(c.getDouble(1), c.getDouble(0)) }
        val cum = DoubleArray(pts.size)
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + Geo.dist(pts[i - 1], pts[i])
        val total = cum.lastOrNull() ?: 0.0
        val apiLen = r.optDouble("distance", total).takeIf { it > 0 } ?: total
        // Mapbox's distances and the line's own differ by a few metres: offsets are scaled onto the line
        val k = if (apiLen > 0) total / apiLen else 1.0
        val steps = ArrayList<Step>()
        val laneSecs = ArrayList<LaneSec>()
        val sevList = ArrayList<Int>()
        val segDur = ArrayList<Double>()
        // what each stretch would take with no traffic (from Mapbox's free-flow speed), -1 where it isn't given
        val segFree = ArrayList<Double>()
        val limits = ArrayList<LimitSec>()
        val incidents = ArrayList<Incident>()
        val legs = r.optJSONArray("legs") ?: JSONArray()
        var legStart = 0.0
        var legPtStart = 0
        // the roundabout step waiting for its exit (the exit's own node comes as the first junction of the step after it)
        var rbIdx = -1
        val rbOthers = ArrayList<Double>()
        // tunnels: each junction marked "tunnel" starts one, running to the next junction (or the end of its step)
        val tunnelSpots = ArrayList<Pair<LL, LL?>>()
        for (li in 0 until legs.length()) {
            val leg = legs.getJSONObject(li)
            var off = legStart
            var prevOff = legStart
            // the last junction passed on the way to the next turn (an earlier slip road, say): the next turn's lanes start after it
            var lastPassed: LL? = null
            val ss = leg.optJSONArray("steps") ?: JSONArray()
            for (si in 0 until ss.length()) {
                val s = ss.getJSONObject(si)
                val m = s.optJSONObject("maneuver") ?: JSONObject()
                val type = m.optString("type"); val mod = m.optString("modifier")
                val at = off * k
                // a roundabout's exits, from the junctions round it: every road off the ring other than the ring itself
                try {
                    val ints = s.optJSONArray("intersections")
                    if (rbIdx >= 0 && ints != null && ints.length() > 0) {
                        // the first junction of the step after the roundabout is where you leave it: its "out" is your exit
                        val x = ints.getJSONObject(0); val b = x.optJSONArray("bearings")
                        val outI = x.optInt("out", -1); val inI = x.optInt("in", -1)
                        if (b != null && outI in 0 until b.length()) {
                            for (bi in 0 until b.length()) if (bi != outI && bi != inI) rbOthers += b.getDouble(bi)
                            steps[rbIdx] = steps[rbIdx].copy(rbOut = b.getDouble(outI), rbExits = rbOthers.toList())
                        }
                        rbIdx = -1
                    }
                    if (type == "roundabout" || type == "rotary") {
                        rbOthers.clear()
                        if (ints != null) for (ii in 1 until ints.length()) {
                            val x = ints.getJSONObject(ii); val b = x.optJSONArray("bearings") ?: continue
                            val outI = x.optInt("out", -1); val inI = x.optInt("in", -1)
                            for (bi in 0 until b.length()) if (bi != outI && bi != inI) rbOthers += b.getDouble(bi)
                        }
                    }
                } catch (e: Exception) { rbIdx = -1 }
                try {
                    val ints = s.optJSONArray("intersections")
                    if (ints != null) for (ii in 0 until ints.length()) {
                        val x = ints.getJSONObject(ii)
                        val cl = x.optJSONArray("classes") ?: continue
                        if ((0 until cl.length()).none { cl.optString(it) == "tunnel" }) continue
                        val loc = x.optJSONArray("location") ?: continue
                        val a = LL(loc.getDouble(1), loc.getDouble(0))
                        val nx = ints.optJSONObject(ii + 1)?.optJSONArray("location")
                        tunnelSpots += a to nx?.let { LL(it.getDouble(1), it.getDouble(0)) }
                    }
                } catch (e: Exception) { }
                // a stop on the way: not a step of its own (the next leg carries on from it)
                val skip = type == "notification" || type == "exit roundabout" || type == "exit rotary" ||
                    (type == "arrive" && li < legs.length() - 1) || (type == "depart" && li > 0)
                if (!skip) {
                    val man = maneuverOf(type, mod, leftHand)
                    val name = s.optString("name")
                    val ref = s.optString("ref").substringBefore(';').trim()
                    val street = if (type == "off ramp" && name.isBlank()) s.optString("destinations") else name.ifBlank { ref }
                    val exit = if (man.startsWith("ROUNDABOUT")) m.optInt("exit", 0) else 0
                    // Mapbox's picture of the junction (from its banner instructions, when asked for and it has one)
                    var pic = ""
                    s.optJSONArray("bannerInstructions")?.let { bi ->
                        for (k in 0 until bi.length()) {
                            val comps = bi.optJSONObject(k)?.optJSONObject("view")?.optJSONArray("components") ?: continue
                            for (q in 0 until comps.length()) { val u = comps.optJSONObject(q)?.optString("imageURL").orEmpty(); if (u.startsWith("https://")) { pic = u; break } }
                            if (pic.isNotEmpty()) break
                        }
                    }
                    steps += Step(at, man, street, m.optString("instruction"), exit, if (type == "off ramp") s.optString("exits").substringBefore(';') else "", s.optString("destinations"), junctionPic = pic,
                        rbIn = if (type == "roundabout" || type == "rotary") m.optDouble("bearing_before", -1.0) else -1.0)
                    if (type == "roundabout" || type == "rotary") rbIdx = steps.size - 1
                    // the lanes at this junction: which go where, and the ones to be in
                    try {
                        val lanes = s.optJSONArray("intersections")?.optJSONObject(0)?.optJSONArray("lanes")
                        if (lanes != null && lanes.length() >= 2 && type != "depart" && type != "arrive") {
                            // Mapbox marks the lanes you can use ("valid") and, of those, the ones to be in ("active"): when it picks
                            // some, only those are lit (two lanes lit where the road is really keep-left came from using every valid one)
                            val anyActive = (0 until lanes.length()).any { n -> lanes.getJSONObject(n).let { it.optBoolean("valid", false) && it.optBoolean("active", false) } }
                            val out = (0 until lanes.length()).map { n ->
                                val ln = lanes.getJSONObject(n)
                                val ind = ln.optJSONArray("indications") ?: JSONArray()
                                val dirs = (0 until ind.length()).map { laneDir(ind.optString(it), leftHand) }.distinct().ifEmpty { listOf("STRAIGHT") }
                                val vi = ln.optString("valid_indication")
                                val use = ln.optBoolean("valid", false) && (!anyActive || ln.optBoolean("active", false))
                                Lane(dirs, if (use) (if (vi.isNotBlank()) laneDir(vi, leftHand) else dirs.first()) else null)
                            }
                            // shown from 300 m out, but never before a junction still to be passed on the way (with two slip roads close
                            // together, the second one's lanes lit up at the first and made it look like the exit to take)
                            var from = max(prevOff * k, at - 300.0)
                            val ml = m.optJSONArray("location")
                            if (lastPassed != null && ml != null) from = max(from, at - Geo.dist(lastPassed!!, LL(ml.getDouble(1), ml.getDouble(0))) + 15.0)
                            if (out.any { it.follow != null } && out.any { it.follow == null } && from < at - 20.0) laneSecs += LaneSec(from, at, out)
                        }
                    } catch (e: Exception) { }
                }
                prevOff = off
                off += s.optDouble("distance", 0.0)
                // the last lane-marked junction along this step (other than where it starts), passed before the next turn: an
                // earlier slip road, say (plain side roads have no lanes given, so they don't cut the lane picture short)
                lastPassed = try {
                    val ints = s.optJSONArray("intersections")
                    var p: LL? = null
                    if (ints != null) for (ii in ints.length() - 1 downTo 1) {
                        val x = ints.getJSONObject(ii)
                        if ((x.optJSONArray("lanes")?.length() ?: 0) >= 2) { p = x.optJSONArray("location")?.let { LL(it.getDouble(1), it.getDouble(0)) }; break }
                    }
                    p
                } catch (e: Exception) { null }
            }
            // traffic, durations and speed limits for each stretch of the line
            val ann = leg.optJSONObject("annotation")
            val cong = ann?.optJSONArray("congestion")
            val dur = ann?.optJSONArray("duration")
            val ms = ann?.optJSONArray("maxspeed")
            val ffs = ann?.optJSONArray("freeflow_speed")
            val dists = ann?.optJSONArray("distance")
            val n = max(cong?.length() ?: 0, max(dur?.length() ?: 0, ms?.length() ?: 0))
            for (i in 0 until n) {
                sevList += when (cong?.optString(i)) { "severe", "heavy" -> 2; "moderate" -> 1; else -> 0 }
                segDur += dur?.optDouble(i, 0.0) ?: 0.0
                val ffKmh = ffs?.optDouble(i, -1.0) ?: -1.0
                val dm = dists?.optDouble(i, -1.0) ?: -1.0
                segFree += if (ffKmh > 3.0 && dm >= 0.0) dm / (ffKmh / 3.6) else -1.0
                val idx = legPtStart + i
                val spd = ms?.optJSONObject(i)
                if (spd != null && spd.has("speed") && idx + 1 < cum.size) {
                    val v = spd.optDouble("speed", -1.0)
                    val mps = if (spd.optString("unit") == "mph") v * 0.44704 else v / 3.6
                    if (v > 0 && mps < 70) {
                        val lastL = limits.lastOrNull()
                        if (lastL != null && kotlin.math.abs(lastL.ms - mps) < 0.05 && kotlin.math.abs(lastL.end - cum[idx]) < 1.0) limits[limits.size - 1] = LimitSec(lastL.off, cum[idx + 1], mps)
                        else limits += LimitSec(cum[idx], cum[idx + 1], mps)
                    }
                }
            }
            // incidents on this part of the route (accidents, roadworks, closures...)
            val inc = leg.optJSONArray("incidents")
            if (inc != null) for (i in 0 until inc.length()) try {
                val x = inc.getJSONObject(i)
                val a = (legPtStart + x.optInt("geometry_index_start", 0)).coerceIn(0, max(0, pts.size - 1))
                val b = (legPtStart + x.optInt("geometry_index_end", x.optInt("geometry_index_start", 0))).coerceIn(a, max(0, pts.size - 1))
                val text = x.optString("description").ifBlank { x.optString("long_description") }
                val mag = when (x.optString("impact")) { "critical" -> 3; "major" -> 2; "minor", "low" -> 1; else -> 0 }
                incidents += Incident("mb-" + x.optString("id", "$a"), incidentCat(x.optString("type")), text, 0, pts.subList(a, b + 1).toList(), mag)
            } catch (e: Exception) { }
            legStart += leg.optDouble("distance", 0.0)
            legPtStart += n
        }
        // traffic level per segment of the line (filled to its length if the counts differ)
        val sev = IntArray(max(0, pts.size - 1)) { i -> sevList.getOrElse(i) { 0 } }
        val time = r.optDouble("duration", 0.0)
        val typical = r.optDouble("duration_typical", time)
        // the traffic delay: on the stretches Mapbox shows as slow (amber or red), the time over what they take with no traffic.
        // It used to be the time over the *usual* time for that hour, so traffic that's there every rush hour counted as none:
        // the time left stayed green with red on the map (the total time always included the traffic; only the delay was missing)
        val haveFree = segFree.isNotEmpty() && segFree.count { it >= 0.0 } >= segFree.size / 2
        val extra = DoubleArray(sev.size) { i ->
            val f = segFree.getOrElse(i) { -1.0 }
            if (haveFree && sev[i] >= 1 && f >= 0.0) max(0.0, segDur.getOrElse(i) { 0.0 } - f) else 0.0
        }
        val delay = if (haveFree) min(extra.sum(), time * 0.8) else max(0.0, time - typical)
        // where the delay is: spread over the slow stretches by how slow they are, then joined into runs
        val delays = ArrayList<DelaySeg>()
        if (haveFree && delay >= 30.0) {
            var i = 0
            while (i < sev.size) {
                if (extra[i] <= 0.0) { i++; continue }
                var j = i; var d = 0.0
                while (j < sev.size && extra[j] > 0.0) { d += extra[j]; j++ }
                if (d >= 5.0) delays += DelaySeg(cum[i], cum[j.coerceAtMost(cum.size - 1)], d)
                i = j
            }
        } else if (delay >= 30.0) {
            val w = DoubleArray(sev.size) { i -> segDur.getOrElse(i) { 0.0 } * when (sev[i]) { 2 -> 0.6; 1 -> 0.3; else -> 0.0 } }
            val sum = w.sum()
            if (sum > 0) {
                var i = 0
                while (i < sev.size) {
                    if (w[i] <= 0) { i++; continue }
                    var j = i; var d = 0.0
                    while (j < sev.size && w[j] > 0) { d += w[j]; j++ }
                    val secs = d / sum * delay
                    if (secs >= 5.0) delays += DelaySeg(cum[i], cum[j.coerceAtMost(cum.size - 1)], secs)
                    i = j
                }
            }
        }
        // the tunnels as stretches along the line (each spot found on the line in order; a tunnel with no next junction runs 150 m)
        val tun = ArrayList<Pair<Double, Double>>()
        try {
            var from = 0
            fun near(p: LL): Int {
                var bi = from; var bd = Double.MAX_VALUE
                for (i in from until min(pts.size, from + 4000)) { val d = Geo.dist(pts[i], p); if (d < bd) { bd = d; bi = i } }
                return if (bd < 40.0) bi else -1
            }
            for ((a, b) in tunnelSpots) {
                val ia = near(a); if (ia < 0) continue
                from = ia
                val ib = if (b != null) near(b) else -1
                val s0 = cum[ia]; val s1 = if (ib > ia) cum[ib] else min(total, s0 + 150.0)
                if (s1 > s0 + 5.0) tun += s0 to s1
            }
        } catch (e: Exception) { }
        return Route(pts, cum, steps, total, time, delay, laneSecs, sev).also { it.delays = delays; it.limits = limits; it.incidents = incidents; it.tunnels = tun }
    }

    // ------------------------------------------------------------------ search

    private fun feature(x: JSONObject, q: String): Place? {
        val g = x.optJSONObject("geometry")?.optJSONArray("coordinates")
        val pr = x.optJSONObject("properties") ?: JSONObject()
        val co = pr.optJSONObject("coordinates")
        val lat = co?.optDouble("latitude") ?: g?.optDouble(1) ?: return null
        val lon = co?.optDouble("longitude") ?: g?.optDouble(0) ?: return null
        if (lat.isNaN() || lon.isNaN()) return null
        val name = pr.optString("name").ifBlank { pr.optString("full_address").substringBefore(',') }.ifBlank { q }
        val sub = pr.optString("full_address").takeIf { it.isNotBlank() && it != name && pr.optString("feature_type") == "poi" }
            ?: pr.optString("place_formatted")
        val web = pr.optJSONObject("metadata")?.optString("website").orEmpty()
        return Place(name, sub, LL(lat, lon), web)
    }

    /** Branches of a shop or chain named [q] near [near] (Mapbox gives up to 10), for "See all locations". */
    fun chain(token: String, q: String, near: LL): List<Place> {
        val t = token.trim()
        val arr = JSONObject(get("$API/search/searchbox/v1/forward?q=${enc(q)}&types=poi&limit=10&proximity=${near.lon},${near.lat}&language=${L.lang}&access_token=${enc(t)}", 8000, 10000))
            .optJSONArray("features") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { feature(arr.optJSONObject(it) ?: return@mapNotNull null, q) }
    }

    /**
     * Drive time (seconds) and distance (metres) from [from] to each of [to], with today's traffic, from Mapbox's matrix service
     * (one request per 9 places). Null for a place it couldn't time.
     */
    fun matrix(token: String, from: LL, to: List<LL>): List<Pair<Double, Double>?> {
        val t = token.trim()
        val out = ArrayList<Pair<Double, Double>?>()
        for (part in to.chunked(9)) {
            val coords = (listOf(from) + part).joinToString(";") { "${it.lon},${it.lat}" }
            val res = try {
                JSONObject(get("$API/directions-matrix/v1/mapbox/driving-traffic/$coords?sources=0&annotations=duration,distance&access_token=${enc(t)}", 8000, 12000))
            } catch (e: Exception) { null }
            val dur = res?.optJSONArray("durations")?.optJSONArray(0)
            val dis = res?.optJSONArray("distances")?.optJSONArray(0)
            for (i in part.indices) {
                val d = dur?.optDouble(i + 1, Double.NaN) ?: Double.NaN
                val m = dis?.optDouble(i + 1, Double.NaN) ?: Double.NaN
                out += if (d.isNaN()) null else d to (if (m.isNaN()) Geo.dist(from, part[i]) else m)
            }
        }
        return out
    }

    /** Why the last Mapbox search failed (both of its searches), or null: shown instead of "no matches". */
    @Volatile var lastError: String? = null

    private val UK_FULL = Regex("^([A-Za-z]{1,2}[0-9][A-Za-z0-9]?)\\s*([0-9][A-Za-z]{2})$")
    private val UK_OUT = Regex("^[A-Za-z]{1,2}[0-9][A-Za-z0-9]?$")

    /** A UK postcode, exactly, from postcodes.io (free, no key): full ones ("SO17 1BJ") and districts ("SO17"). */
    private fun ukPostcode(q: String): Place? {
        val t = q.trim()
        val full = UK_FULL.find(t)
        return try {
            if (full != null) {
                val pc = (full.groupValues[1] + " " + full.groupValues[2]).uppercase()
                val r = JSONObject(get("https://api.postcodes.io/postcodes/" + enc(pc), 8000, 10000)).optJSONObject("result") ?: return null
                val sub = listOf(r.optString("admin_ward"), r.optString("admin_district")).filter { it.isNotBlank() && it != "null" }.distinct().joinToString(", ")
                Place(r.optString("postcode", pc), sub, LL(r.getDouble("latitude"), r.getDouble("longitude")))
            } else if (UK_OUT.matches(t)) {
                val oc = t.uppercase()
                val r = JSONObject(get("https://api.postcodes.io/outcodes/" + enc(oc), 8000, 10000)).optJSONObject("result") ?: return null
                val d = r.optJSONArray("admin_district")?.optString(0) ?: ""
                Place(r.optString("outcode", oc), if (d.isNotBlank() && d != "null") d else "Postcode district", LL(r.getDouble("latitude"), r.getDouble("longitude")))
            } else null
        } catch (e: Exception) { null }
    }

    // the start of a full postcode: the district and the first figure of the rest ("SO17 1", "SO171")
    private val UK_PART = Regex("^([A-Za-z]{1,2}[0-9][A-Za-z0-9]?)\\s*([0-9][A-Za-z]?)$")

    /** Whether [q] looks like a UK postcode, whole or half-typed ("SO17 1BJ", "SO17 1", "SO17"). */
    fun looksLikePostcode(q: String): Boolean { val t = q.trim(); return UK_FULL.matches(t) || UK_OUT.matches(t) || UK_PART.matches(t) }

    /**
     * The postcode itself as search results, for any search (TomTom's and Mapbox's both tend to give the addresses in it
     * instead): the exact postcode or district, or for a half-typed one the postcodes it could be (up to 6). From
     * postcodes.io (free, no key, UK only). Empty when it isn't a postcode or nothing is found.
     */
    fun postcodes(q: String): List<Place> {
        val t = q.trim()
        if (t.length > 9) return emptyList()
        if (UK_FULL.matches(t) || UK_OUT.matches(t)) return listOfNotNull(ukPostcode(t))
        if (!UK_PART.matches(t)) return emptyList()
        return try {
            val arr = JSONObject(get("https://api.postcodes.io/postcodes/" + enc(t) + "/autocomplete?limit=6", 8000, 10000)).optJSONArray("result") ?: return emptyList()
            val codes = (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }.take(6)
            codes.mapNotNull { c -> try { ukPostcode(c) } catch (e: Exception) { null } }
        } catch (e: Exception) { emptyList() }
    }

    /** Runs [work] on its own thread; [join] waits for it (at most [ms]) and gives its result, or null. */
    private class SearchJob<T>(private val ms: Long, work: () -> T) {
        @Volatile private var v: T? = null
        @Volatile var error: Exception? = null
        private val t = Thread { try { v = work() } catch (e: Exception) { error = e } }.apply { isDaemon = true; start() }
        fun join(): T? { try { t.join(ms) } catch (e: InterruptedException) { }; return v }
    }

    /**
     * Place search, three ways at once so each kind of search finds what you meant:
     * - Mapbox's Search Box (businesses, points of interest, places, and addresses), with partial and misspelt words allowed;
     * - Mapbox's geocoder (house numbers, streets, postcodes, towns), which knows addresses best;
     * - a UK postcode, exactly, from postcodes.io.
     * Addresses come first when you type a house number or a postcode, otherwise places and businesses. OpenStreetMap's Nominatim
     * fills in when there's still little. [country]: ISO code, blank = anywhere.
     */
    fun geocode(token: String, country: String, q: String, near: LL?): List<Place> {
        val t = enc(token)
        val qq = enc(q.trim())
        val cc = country.trim().lowercase()
        val lang = L.lang
        val prox = if (near != null) String.format(java.util.Locale.US, "&proximity=%.5f,%.5f", near.lon, near.lat) else ""
        val ctry = if (cc.isNotEmpty()) "&country=$cc" else ""
        val ukish = cc.isEmpty() || cc == "gb"
        val box = SearchJob(9000L) {
            JSONObject(get("$API/search/searchbox/v1/forward?q=$qq&limit=10&auto_complete=true&language=$lang$prox$ctry&access_token=$t", 8000, 9000)).optJSONArray("features")
        }
        val geo = SearchJob(9000L) {
            JSONObject(get("$API/search/geocode/v6/forward?q=$qq&limit=8&autocomplete=true&types=address,street,postcode,place,locality,neighborhood,district&language=$lang$prox$ctry&access_token=$t", 8000, 9000)).optJSONArray("features")
        }
        val pc = if (ukish && (UK_FULL.containsMatchIn(q.trim()) || UK_OUT.matches(q.trim()))) SearchJob(9000L) { ukPostcode(q) } else null
        // Photon (OpenStreetMap, free): good with half-typed addresses and house numbers near you
        val osm = SearchJob(9000L) { OsmSearch.geocode(country, q, near) }
        val boxArr = box.join(); val geoArr = geo.join(); val pcPlace = pc?.join(); val osmList = osm.join()
        lastError = (box.error ?: geo.error)?.message?.takeIf { boxArr == null && geoArr == null }
        val out = ArrayList<Place>()
        fun addOne(p: Place) { if (out.none { (it.name.equals(p.name, true) && Geo.dist(it.pos, p.pos) < 80) || Geo.dist(it.pos, p.pos) < 15 }) out += p }
        fun add(arr: JSONArray?) {
            if (arr == null) return
            for (i in 0 until arr.length()) addOne(feature(arr.optJSONObject(i) ?: continue, q) ?: continue)
        }
        val addressy = pcPlace != null || q.trim().firstOrNull()?.isDigit() == true
        if (pcPlace != null) out += pcPlace
        if (addressy) { add(geoArr); osmList?.forEach { addOne(it) }; add(boxArr) } else { add(boxArr); add(geoArr); osmList?.forEach { addOne(it) } }
        // both Mapbox searches failed (a refused token, a limit): say so, rather than "no matches"
        if (out.isEmpty() && boxArr == null && geoArr == null) (box.error ?: geo.error)?.let { if (it.message?.contains("(40") == true || it.message?.contains("limit") == true) throw it }
        if (out.size < 5 && osmList == null) try {
            var url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=10&q=$qq"
            if (near != null) url += "&viewbox=${near.lon - 0.5},${near.lat + 0.35},${near.lon + 0.5},${near.lat - 0.35}"
            if (cc.isNotEmpty()) url += "&countrycodes=$cc"
            val arr = JSONArray(get(url, 8000, 10000))
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                val disp = x.optString("display_name")
                addOne(Place(x.optString("name").ifBlank { disp.substringBefore(',') }, disp.substringAfter(", ", ""), LL(x.getDouble("lat"), x.getDouble("lon"))))
            }
        } catch (e: Exception) { }
        return out
    }

    private fun poiQuery(kind: String) = when (kind) {
        "fuel" -> "petrol station"; "charging" -> "ev charging station"; "parking" -> "car park"
        "food" -> "restaurant"; "coffee" -> "cafe"; "toilets" -> "public toilet"; "services" -> "motorway services"; else -> kind
    }

    /**
     * Mapbox's category ids for each kind, most likely first. "gas_station" is in Mapbox's documentation; the rest are its usual
     * names, so a kind tries the next id when Mapbox doesn't know one (and the free-text search after that).
     */
    private fun categoryIds(kind: String): List<String> = when (kind) {
        "fuel" -> listOf("gas_station")
        "charging" -> listOf("charging_station", "ev_charging_station")
        "parking" -> listOf("parking_lot", "parking")
        "food" -> listOf("restaurant", "food")
        "coffee" -> listOf("coffee", "cafe")
        "toilets" -> listOf("toilet", "public_toilet", "restroom")
        "services" -> listOf("rest_area")
        else -> emptyList()
    }

    /** Category ids Mapbox said it doesn't know this run, so they aren't asked for again. */
    private val badCategories = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * Places of a kind around [c] within about [radius] metres. Mapbox's category search first (it returns the nearest places of that
     * kind, up to 25), then its free-text search. The free-text search alone often returned places too far away, which were all
     * filtered out, so the app fell back to the free OpenStreetMap servers; those are often busy ("HTTP 429", "HTTP 502").
     */
    fun poiNearby(token: String, kind: String, c: LL, radius: Int): List<Nearby> {
        val lang = L.lang
        val near = String.format(java.util.Locale.US, "&proximity=%.5f,%.5f", c.lon, c.lat)
        // a box round the point (about [radius] each way), so the results really are nearby
        val dLat = radius / 111320.0; val dLon = radius / (111320.0 * Math.cos(Math.toRadians(c.lat)).coerceAtLeast(0.2))
        val box = String.format(java.util.Locale.US, "&bbox=%.5f,%.5f,%.5f,%.5f", c.lon - dLon, c.lat - dLat, c.lon + dLon, c.lat + dLat)
        for (id in categoryIds(kind)) {
            if (id in badCategories) continue
            val body = try {
                get("$API/search/searchbox/v1/category/${enc(id)}?limit=25&language=$lang$near$box&access_token=${enc(token)}", 8000, 12000)
            } catch (e: IOException) {
                // 400 / 404: Mapbox doesn't know that id, so try the next; anything else (no signal, key refused) goes to the caller
                val m = e.message ?: ""
                if (m.contains("400") || m.contains("404") || m.contains("unknown", true) || m.contains("not found", true) || m.contains("invalid", true)) { badCategories += id; continue }
                throw e
            }
            val out = parsePois(body, kind, c, radius)
            if (out.isNotEmpty()) return out
        }
        val body = get("$API/search/searchbox/v1/forward?q=${enc(poiQuery(kind))}&types=poi&limit=10&language=$lang$near&access_token=${enc(token)}", 8000, 12000)
        return parsePois(body, kind, c, radius)
    }

    private fun parsePois(body: String, kind: String, c: LL, radius: Int): List<Nearby> {
        val arr = JSONObject(body).optJSONArray("features") ?: JSONArray()
        val out = ArrayList<Nearby>()
        for (i in 0 until arr.length()) {
            val x = arr.optJSONObject(i) ?: continue
            val p = feature(x, Overpass.kindLabel(kind)) ?: continue
            val d = Geo.dist(c, p.pos)
            if (d > radius * 1.5) continue
            val brand = x.optJSONObject("properties")?.optJSONArray("brand")?.optString(0).orEmpty()
            out += Nearby(Place(p.name, p.sub, p.pos), d, if (brand.isNotBlank() && !p.name.contains(brand, true)) brand else "")
        }
        return out
    }

    // ------------------------------------------------------------------ speed limits off a route

    /**
     * The posted limit (m/s) of the road you're on, from Mapbox matching your last few positions [trail] (oldest first) to its roads:
     * null when it couldn't tell, -1 when the road has no limit known.
     */
    /** What's at [p] (Mapbox's map data round it): the road and its kind, what the land is, the place it's in. */
    class Here(val road: String, val roadKind: String, val land: String, val place: String, val roadClass: String = "", val ref: String = "", val landClass: String = "")

    fun whatsHere(token: String, p: LL): Here? = try {
        val url = String.format(java.util.Locale.US, "$API/v4/mapbox.mapbox-streets-v8/tilequery/%.6f,%.6f.json?radius=40&limit=20&dedupe=true&layers=road,landuse,landuse_overlay,place_label,building&access_token=%s",
            p.lon, p.lat, enc(token))
        val fs = JSONObject(get(url, 8000, 10000)).optJSONArray("features") ?: JSONArray()
        var road = ""; var kind = ""; var land = ""; var place = ""; var cls = ""; var ref = ""; var landCls = ""
        for (i in 0 until fs.length()) {
            val f = fs.optJSONObject(i) ?: continue
            val pr = f.optJSONObject("properties") ?: continue
            when (pr.optJSONObject("tilequery")?.optString("layer")) {
                "road" -> if (road.isEmpty() && kind.isEmpty()) {
                    road = pr.optString("name").ifBlank { pr.optString("ref") }
                    cls = pr.optString("class"); ref = pr.optString("ref").substringBefore(';')
                    kind = when (pr.optString("class")) {
                        "motorway" -> "Motorway"; "trunk" -> "Main road (dual carriageway or trunk)"; "primary" -> "A road"; "secondary" -> "B road"
                        "tertiary" -> "Minor road"; "street", "street_limited" -> "Street"; "service" -> "Service road"; "path", "pedestrian" -> "Path"
                        "motorway_link", "trunk_link", "primary_link" -> "Slip road"; else -> pr.optString("class").replace('_', ' ').replaceFirstChar { it.uppercase() }
                    }
                }
                "landuse", "landuse_overlay" -> if (land.isEmpty()) { landCls = pr.optString("class"); land = landCls.replace('_', ' ').replaceFirstChar { it.uppercase() } }
                "building" -> if (land.isEmpty()) { land = "Building"; landCls = "building" }
                "place_label" -> if (place.isEmpty()) place = pr.optString("name")
            }
        }
        Here(road, kind, land, place, cls, ref, landCls)
    } catch (e: Exception) { null }

    fun speedLimit(token: String, trail: List<LL>): Double? {
        if (trail.size < 2) return null
        val coords = trail.joinToString(";") { String.format(java.util.Locale.US, "%.6f,%.6f", it.lon, it.lat) }
        val rad = List(trail.size) { "25" }.joinToString(";")
        val body = get("$API/matching/v5/mapbox/driving/$coords?annotations=maxspeed&overview=false&tidy=true&radiuses=${enc(rad)}&access_token=${enc(token)}", 6000, 8000)
        val legs = JSONObject(body).optJSONArray("matchings")?.optJSONObject(0)?.optJSONArray("legs") ?: return null
        val ms = legs.optJSONObject(legs.length() - 1)?.optJSONObject("annotation")?.optJSONArray("maxspeed") ?: return null
        val last = ms.optJSONObject(ms.length() - 1) ?: return null
        if (!last.has("speed")) return if (last.optBoolean("none", false)) -1.0 else null
        val v = last.optDouble("speed", -1.0)
        if (v <= 0) return null
        return if (last.optString("unit") == "mph") v * 0.44704 else v / 3.6
    }
}
