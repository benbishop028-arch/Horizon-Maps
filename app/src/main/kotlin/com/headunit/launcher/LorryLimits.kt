package com.headunit.launcher

import java.util.Locale
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Height, weight, width and length limits on the road ahead, for a lorry (see [Vehicle]), from OpenStreetMap.
 * Lorry routes should already keep off any the lorry is over; these are shown on the map, and one the lorry is over
 * gets a red warning card and is spoken (the map data being newer than the route's, or the driver having left the route).
 */
object LorryLimits {
    /** One limit: [off] metres along the route, [kind] "height" / "weight" / "width" / "length", [value] in metres or tonnes. */
    class Lim(val off: Double, val pos: LL, val kind: String, val value: Double, val over: Boolean)

    /** A length in metres from an OSM value: "4.1", "4.1 m", "13'6\"", "13 ft 6 in". Null for "none", "default" and the like. */
    fun parseLen(raw: String?): Double? {
        val v = raw?.trim()?.lowercase(Locale.US) ?: return null
        if (v.isEmpty()) return null
        Regex("""^(\d+(?:\.\d+)?)\s*(?:m)?$""").find(v)?.let { return it.groupValues[1].toDoubleOrNull()?.takeIf { m -> m in 1.0..30.0 } }
        Regex("""^(\d+)\s*(?:'|ft|′)\s*(?:(\d+)\s*(?:"|in|″)?)?$""").find(v)?.let {
            val ft = it.groupValues[1].toDoubleOrNull() ?: return null
            val inch = it.groupValues[2].toDoubleOrNull() ?: 0.0
            return ((ft * 12 + inch) * 0.0254).takeIf { m -> m in 1.0..30.0 }
        }
        return null
    }

    /** A weight in tonnes from an OSM value: "7.5", "7.5 t", "7500 kg", "10 st" (short tons). */
    fun parseWeight(raw: String?): Double? {
        val v = raw?.trim()?.lowercase(Locale.US) ?: return null
        val m = Regex("""^(\d+(?:\.\d+)?)\s*(t|kg|st|lbs)?$""").find(v) ?: return null
        val n = m.groupValues[1].toDoubleOrNull() ?: return null
        val t = when (m.groupValues[2]) { "kg" -> n / 1000.0; "st" -> n * 0.907185; "lbs" -> n * 0.000453592; else -> n }
        return t.takeIf { it in 0.5..200.0 }
    }

    /** The sign's text: "4.1m" (or 13′6″ when [imperial]) for a height, "7.5t" for a weight. */
    fun signText(l: Lim, imperial: Boolean): String = when (l.kind) {
        "weight" -> fmt(l.value) + "t"
        "height" -> if (imperial) { val inch = Math.round(l.value / 0.0254).toInt(); "${inch / 12}′${inch % 12}″" } else fmt(l.value) + "m"
        else -> fmt(l.value) + "m"
    }

    fun title(kind: String): String = when (kind) { "height" -> "Low bridge"; "weight" -> "Weight limit"; "width" -> "Width limit"; else -> "Length limit" }

    private fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

    /** Whether the lorry is over the limit (a little leeway for rounding on the sign). */
    private fun over(kind: String, value: Double): Boolean = when (kind) {
        "height" -> value + 0.005 < Vehicle.heightCm / 100.0
        "width" -> value + 0.005 < Vehicle.widthCm / 100.0
        "length" -> value + 0.05 < Vehicle.lengthCm / 100.0
        else -> value + 0.05 < Vehicle.weightT10 / 10.0
    }

    /**
     * The limits on [r] from [from] to [from] + [ahead] metres along it, in order. Null when the lookup failed (try again later).
     * A limited road only counts when it runs along the route (two of its points close to it), so a weight-limited side road
     * that just meets the route at a junction isn't taken for one on it.
     */
    fun along(r: Route, from: Double, ahead: Double): List<Lim>? {
        val pts = r.pts; val cum = r.cum
        if (pts.size < 2 || cum.size != pts.size) return emptyList()
        var i0 = 0
        while (i0 < pts.size - 1 && cum[i0 + 1] < from) i0++
        var i1 = i0
        while (i1 < pts.size - 1 && cum[i1] < from + ahead) i1++
        // the route ahead, thinned to about one point every 120 m, for the query
        val line = ArrayList<LL>()
        var last = -1e9
        for (i in i0..i1) if (cum[i] - last >= 120.0 || i == i1) { line += pts[i]; last = cum[i] }
        if (line.size < 2) return emptyList()
        val q = buildString {
            val ln = line.joinToString(",") { String.format(Locale.US, "%.5f,%.5f", it.lat, it.lon) }
            append("[out:json][timeout:20];(")
            for (k in listOf("maxheight", "maxheight:physical", "maxweight", "maxwidth", "maxlength")) append("way(around:25,$ln)[\"highway\"][\"$k\"];")
            append("node(around:25,$ln)[\"maxheight\"];")
            append(");out tags geom 400;")
        }
        val els = try { Overpass.query(q, 10000, 25000, 2) } catch (e: Exception) { return null }
        // the route's points in the window, in a flat local frame (metres), for quick distance checks
        val lat0 = pts[i0].lat
        val kx = 111_320.0 * cos(Math.toRadians(lat0)); val ky = 110_540.0
        var minLat = 90.0; var maxLat = -90.0; var minLon = 180.0; var maxLon = -180.0
        for (i in i0..i1) { val q0 = pts[i]; if (q0.lat < minLat) minLat = q0.lat; if (q0.lat > maxLat) maxLat = q0.lat; if (q0.lon < minLon) minLon = q0.lon; if (q0.lon > maxLon) maxLon = q0.lon }
        val padLat = 0.0005; val padLon = 0.0008
        fun near(p: LL, maxM: Double): Double? {
            if (p.lat < minLat - padLat || p.lat > maxLat + padLat || p.lon < minLon - padLon || p.lon > maxLon + padLon) return null
            var best = Double.MAX_VALUE; var bestOff = -1.0
            for (i in i0 until i1) {
                val ax = (pts[i].lon - p.lon) * kx; val ay = (pts[i].lat - p.lat) * ky
                val bx = (pts[i + 1].lon - p.lon) * kx; val by = (pts[i + 1].lat - p.lat) * ky
                val dx = bx - ax; val dy = by - ay
                val len2 = dx * dx + dy * dy
                val t = if (len2 > 0) (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0) else 0.0
                val cx = ax + dx * t; val cy = ay + dy * t
                val d = sqrt(cx * cx + cy * cy)
                if (d < best) { best = d; bestOff = cum[i] + (cum[i + 1] - cum[i]) * t }
            }
            return if (best <= maxM) bestOff else null
        }
        val out = ArrayList<Lim>()
        for (n in 0 until els.length()) {
            val e = els.optJSONObject(n) ?: continue
            val tags = e.optJSONObject("tags") ?: continue
            // where along the route: a barrier's own point, or the first point of a limited road that runs along the route
            var off: Double? = null; var at: LL? = null
            if (e.optString("type") == "node") {
                if (!e.has("lat")) continue
                val p = LL(e.getDouble("lat"), e.getDouble("lon"))
                off = near(p, 15.0); at = p
            } else {
                val g = e.optJSONArray("geometry") ?: continue
                var hits = 0
                for (k in 0 until g.length()) {
                    val o = g.optJSONObject(k) ?: continue
                    val p = LL(o.optDouble("lat"), o.optDouble("lon"))
                    val f = near(p, 15.0) ?: continue
                    hits++
                    if (off == null || f < off) { off = f; at = p }
                }
                if (hits < 2) continue
            }
            if (off == null || at == null) continue
            val h = parseLen(tags.optString("maxheight:physical").ifBlank { tags.optString("maxheight") })
            if (h != null) out += Lim(off, at, "height", h, over("height", h))
            parseWeight(tags.optString("maxweight"))?.let { out += Lim(off, at, "weight", it, over("weight", it)) }
            parseLen(tags.optString("maxwidth"))?.let { out += Lim(off, at, "width", it, over("width", it)) }
            parseLen(tags.optString("maxlength"))?.let { out += Lim(off, at, "length", it, over("length", it)) }
        }
        // one of each kind per place (a bridge is often split into several short roads with the same limit)
        return out.sortedBy { it.off }.fold(ArrayList<Lim>()) { acc, l ->
            if (acc.none { it.kind == l.kind && kotlin.math.abs(it.off - l.off) < 80.0 }) acc += l
            acc
        }
    }
}
