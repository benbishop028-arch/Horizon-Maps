package com.headunit.launcher

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Spain's official live traffic incidents, from the DGT (Dirección General de Tráfico) National Access Point: accidents,
 * roadworks, closures, hazards and, since January 2026, every connected V16 warning beacon a driver switches on next to a
 * broken-down or crashed car (reported through DGT 3.0). Free and open, with no key or account: a DATEX II v3 XML file
 * covering the state road network (Catalonia and the Basque Country publish their own and aren't in it).
 *
 * The whole country comes in one file (a few MB), so it is fetched at most every 3 minutes and shared by the area icons and
 * the route checks. Everything is turned into the same [Incident] TomTom's incidents use, so the alerts, icons, route colours
 * and closure avoidance all work with it unchanged.
 */
object DgtIncidents {
    const val URL_FEED = "https://nap.dgt.es/datex2/v3/dgt/SituationPublication/datex2_v36.xml"
    private const val TTL_MS = 3 * 60_000L

    @Volatile private var cache: List<Incident> = emptyList()
    @Volatile private var cacheAt = 0L
    private val lock = Any()

    /** Mainland Spain, the Balearic and Canary Islands, Ceuta and Melilla (a quick test before any download). */
    fun inSpain(p: LL): Boolean =
        (p.lat in 35.8..43.9 && p.lon in -9.5..4.4) || (p.lat in 38.6..40.1 && p.lon in 1.1..4.4) ||
            (p.lat in 27.5..29.5 && p.lon in -18.3..-13.3) || (p.lat in 35.2..35.95 && p.lon in -5.45..-2.9)

    /** The incidents inside the box (call off the main thread). Uses the copy from the last 3 minutes when there is one. */
    fun near(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double): List<Incident> =
        all().filter { inc -> inc.pts.any { it.lat in minLat..maxLat && it.lon in minLon..maxLon } }

    fun all(): List<Incident> {
        val now = System.currentTimeMillis()
        if (now - cacheAt < TTL_MS) return cache
        synchronized(lock) {
            if (System.currentTimeMillis() - cacheAt < TTL_MS) return cache
            val got = try { fetch() } catch (e: Exception) { null }
            // on a failure keep what we had, and try again in a minute rather than at once
            if (got != null) { cache = got; cacheAt = System.currentTimeMillis() } else cacheAt = System.currentTimeMillis() - TTL_MS + 60_000L
            return cache
        }
    }

    /** When the copy we have was published, so an unchanged file isn't downloaded again (it's a few MB, often on a phone's data). */
    @Volatile private var lastModified: String? = null

    private fun fetch(): List<Incident> {
        Net.check()
        val c = URL(URL_FEED).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("Accept-Encoding", "gzip")
        c.setRequestProperty("User-Agent", "HorizonNav/${BuildConfig.VERSION_NAME}")
        lastModified?.let { if (cache.isNotEmpty()) c.setRequestProperty("If-Modified-Since", it) }
        val code = c.responseCode
        // unchanged since last time: keep what we have
        if (code == HttpURLConnection.HTTP_NOT_MODIFIED) { c.disconnect(); return cache }
        if (code !in 200..299) throw IOException("HTTP $code")
        lastModified = c.getHeaderField("Last-Modified")
        val raw = c.inputStream
        val input = if (c.contentEncoding.equals("gzip", true)) GZIPInputStream(raw) else raw
        return input.use { parse(it) }
    }

    /** One situation record as it is read. */
    private class Rec {
        var id = ""; var source = ""; var cause = ""; var detail = ""; var mgmt = ""; var status = ""
        var road = ""; var km = ""; var severity = ""
        val pts = ArrayList<LL>(); var fromPt: LL? = null; var toPt: LL? = null
        var lat = Double.NaN; var where = ""   // where the coordinates being read belong: "from", "to" or a plain point
    }

    /** Reads the DATEX II file as a stream (no whole-document tree, so a few MB stay cheap on a head unit). */
    fun parse(input: InputStream): List<Incident> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(input, null)
        val out = ArrayList<Incident>()
        var rec: Rec? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    val name = p.name.substringAfter(':')
                    when (name) {
                        "situationRecord" -> rec = Rec().also { it.id = p.getAttributeValue(null, "id").orEmpty() }
                        "from", "to" -> rec?.where = name
                        "sourceIdentification", "causeType", "validityStatus", "roadName", "kilometerPoint", "severity",
                        "vehicleObstructionType", "obstructionType", "accidentType", "roadMaintenanceType", "environmentalObstructionType",
                        "poorEnvironmentType", "abnormalTrafficType", "roadOrCarriagewayOrLaneManagementType", "latitude", "longitude" -> {
                            val r = rec
                            if (r != null) {
                                val t = p.nextText().trim()
                                when (name) {
                                    "sourceIdentification" -> if (r.source.isEmpty()) r.source = t
                                    "causeType" -> if (r.cause.isEmpty()) r.cause = t
                                    "validityStatus" -> r.status = t
                                    "roadName" -> if (r.road.isEmpty()) r.road = t
                                    "kilometerPoint" -> if (r.km.isEmpty()) r.km = t
                                    "severity" -> if (r.severity.isEmpty()) r.severity = t
                                    "roadOrCarriagewayOrLaneManagementType" -> if (r.mgmt.isEmpty()) r.mgmt = t
                                    "latitude" -> r.lat = t.toDoubleOrNull() ?: Double.NaN
                                    "longitude" -> {
                                        val lon = t.toDoubleOrNull()
                                        if (lon != null && !r.lat.isNaN()) {
                                            val ll = LL(r.lat, lon)
                                            when (r.where) { "from" -> r.fromPt = ll; "to" -> r.toPt = ll; else -> r.pts += ll }
                                        }
                                        r.lat = Double.NaN
                                    }
                                    else -> if (r.detail.isEmpty()) r.detail = t
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = p.name.substringAfter(':')
                    if (name == "from" || name == "to") rec?.where = ""
                    else if (name == "situationRecord") { rec?.let { toIncident(it) }?.let { out += it }; rec = null }
                }
            }
            ev = p.next()
        }
        return out
    }

    private val LANE_TYPES = setOf(
        "laneClosures", "carriagewayClosures", "narrowLanes", "singleAlternateLineTraffic", "lanesDeviated",
        "doNotUseSpecifiedLanesOrCarriageways", "intermittentShortTermClosures", "newRoadworksLayout",
    )

    private fun toIncident(r: Rec): Incident? {
        if (r.status.isNotEmpty() && r.status != "active") return null
        // a stretch runs from its "from" point to its "to" point; a single place is just its point
        val pts = ArrayList<LL>()
        r.fromPt?.let { pts += it }; pts += r.pts; r.toPt?.let { pts += it }
        if (pts.isEmpty()) return null
        val stuck = r.detail == "vehicleStuck"
        val cat = when {
            // a V16 beacon (sent through DGT 3.0), or a broken-down car reported by the DGT or 112
            r.cause == "vehicleObstruction" && stuck -> "breakdown"
            r.mgmt == "roadClosed" -> "roadClosed"
            r.cause == "roadMaintenance" -> "roadWorks"
            r.mgmt in LANE_TYPES -> "laneClosed"
            r.cause == "accident" -> "accident"
            r.cause == "abnormalTraffic" -> "jam"
            r.cause in setOf("vehicleObstruction", "obstruction", "environmentalObstruction", "poorEnvironment",
                "infrastructureDamageObstruction", "nonWeatherRelatedRoadConditions") -> "danger"
            else -> return null
        }
        val text = buildString {
            append(r.road)
            if (r.km.isNotEmpty()) { if (isNotEmpty()) append(", "); append("km ").append(r.km.substringBefore('.')) }
        }
        // no delay or size is given, so a closure is red and nothing else is painted on the route's own traffic colours
        val mag = if (r.severity == "highest" && r.cause == "abnormalTraffic") 3 else 0
        return Incident("dgt-${r.id}", cat, text, 0, pts, mag)
    }
}
