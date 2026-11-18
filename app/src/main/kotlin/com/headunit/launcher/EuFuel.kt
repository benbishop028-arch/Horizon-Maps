package com.headunit.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Live fuel prices outside the UK, from each country's own free price service:
 *  - France: the government's open price feed (data.economie.gouv.fr), no key needed.
 *  - Austria: E-Control's Spritpreisrechner, no key needed (it gives the cheapest few stations round a point).
 *  - Germany: Tankerkönig (Markttransparenzstelle data), with a free key pasted in Settings.
 *  - Australia (New South Wales and Tasmania): the NSW government's FuelCheck, with a free API key and secret pasted in Settings.
 *  - Italy: the ministry's (MIMIT) open price files, no key needed (TheDMAI). Two files for the whole country, fetched at most
 *    once a day and kept on the device as one small list, so a search there is just a look through that list.
 * Prices come back in cents a litre. Everything here is network work: call it off the main thread. Any failure gives an empty
 * list, so the app simply falls back to its normal station search without prices.
 */
object EuFuel {
    class Station(val name: String, val sub: String, val pos: LL, val cents: Double)

    /** Countries with a price service here (Germany only once a Tankerkönig key is in). */
    fun supported(country: String, prefs: Prefs): Boolean = when (country.uppercase()) {
        "FR", "AT", "IT" -> true
        "DE" -> prefs.s(PreferenceKeys.TANKERKOENIG_KEY, "").isNotBlank()
        "AU" -> prefs.s(PreferenceKeys.NSW_FUEL_KEY, "").isNotBlank() && prefs.s(PreferenceKeys.NSW_FUEL_SECRET, "").isNotBlank()
        else -> false
    }

    /** How the price is written: euros ("€1.789") in Europe, cents a litre ("189.9c") in Australia. */
    fun unit(country: String): String = if (country.uppercase() == "AU") "c" else "€"

    /** Whether the country's service has prices for this fuel at all ("E10", "E5", "B7" or "E85"). */
    fun hasFuel(country: String, fuel: String): Boolean = when (country.uppercase()) {
        "FR" -> fuel in setOf("E10", "E5", "B7", "E85", "LPG")
        "AT", "DE" -> fuel in setOf("E10", "E5", "B7")
        "AU" -> fuel in setOf("E10", "E5", "B7", "E85", "LPG")
        // (Italy's petrol is all one "Benzina", so Unleaded and Super both get it)
        "IT" -> fuel in setOf("E10", "E5", "B7", "LPG")
        else -> false
    }

    /** Stations with a price for [fuel] within [radiusKm] of [c] (at most about 25 km; the services cap it anyway). */
    fun near(country: String, prefs: Prefs, c: LL, radiusKm: Int, fuel: String): List<Station> = try {
        when (country.uppercase()) {
            "FR" -> france(c, radiusKm.coerceIn(1, 25), fuel)
            "AT" -> austria(c, fuel)
            "DE" -> germany(prefs.s(PreferenceKeys.TANKERKOENIG_KEY, "").trim(), c, radiusKm.coerceIn(1, 25), fuel)
            "AU" -> nsw(prefs, c, radiusKm.coerceIn(1, 25), fuel)
            "IT" -> italy(c, radiusKm.coerceIn(1, 25), fuel)
            else -> emptyList()
        }
    } catch (e: Exception) { emptyList() }

    /** Stations along a line (the route ahead): the service is asked round a few points spaced out along it, duplicates dropped. */
    fun along(country: String, prefs: Prefs, line: List<LL>, fuel: String, maxAsks: Int = 6): List<Station> {
        if (line.isEmpty()) return emptyList()
        val pts = ArrayList<LL>()
        var last: LL? = null
        for (p in line) if (last == null || Geo.dist(last, p) > 12_000.0) { pts += p; last = p }
        val out = LinkedHashMap<String, Station>()
        for (p in pts.take(maxAsks)) for (s in near(country, prefs, p, 8, fuel)) out["%.4f,%.4f".format(java.util.Locale.US, s.pos.lat, s.pos.lon)] = s
        return out.values.toList()
    }

    private fun get(url: String): String {
        val c = URL(url).also { com.headunit.launcher.Net.check() }.openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 12000
        c.setRequestProperty("User-Agent", "HorizonNav/${BuildConfig.VERSION_NAME}")
        c.setRequestProperty("Accept", "application/json")
        try {
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { r ->
                val sb = StringBuilder(); val buf = CharArray(8192)
                while (true) { val n = r.read(buf); if (n < 0) break; sb.append(buf, 0, n); if (sb.length > 3_000_000) break }
                sb.toString()
            }
        } finally { c.disconnect() }
    }

    private fun france(c: LL, km: Int, fuel: String): List<Station> {
        val field = when (fuel) { "B7" -> "gazole_prix"; "E5" -> "sp95_prix"; "E85" -> "e85_prix"; "LPG" -> "gplc_prix"; else -> "e10_prix" }
        val where = "within_distance(geom, GEOM'POINT(${c.lon} ${c.lat})', ${km}km) AND $field IS NOT NULL"
        val url = "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2/records" +
            "?limit=60&where=" + URLEncoder.encode(where, "UTF-8")
        val res = JSONObject(get(url)).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Station>()
        for (i in 0 until res.length()) {
            val r = res.optJSONObject(i) ?: continue
            // E5: SP95 where it's sold, otherwise SP98
            var euros = r.optDouble(field, Double.NaN)
            if (euros.isNaN() && fuel == "E5") euros = r.optDouble("sp98_prix", Double.NaN)
            if (euros.isNaN() || euros <= 0.0) continue
            val g = r.optJSONObject("geom")
            val pos = if (g != null && g.has("lat")) LL(g.getDouble("lat"), g.getDouble("lon")) else continue
            val town = r.optString("ville").trim()
            val addr = r.optString("adresse").trim()
            out += Station(if (addr.isNotBlank()) addr else "Station", town, pos, euros * 100.0)
        }
        return out
    }

    private fun austria(c: LL, fuel: String): List<Station> {
        val type = if (fuel == "B7") "DIE" else "SUP"
        val url = "https://api.e-control.at/sprit/1.0/search/gas-stations/by-address?latitude=${c.lat}&longitude=${c.lon}&fuelType=$type&includeClosed=false"
        val arr = JSONArray(get(url))
        val out = ArrayList<Station>()
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val loc = s.optJSONObject("location") ?: continue
            val pr = s.optJSONArray("prices")?.optJSONObject(0) ?: continue
            val euros = pr.optDouble("amount", Double.NaN)
            if (euros.isNaN() || euros <= 0.0) continue
            out += Station(s.optString("name").ifBlank { "Station" }, loc.optString("city"), LL(loc.getDouble("latitude"), loc.getDouble("longitude")), euros * 100.0)
        }
        return out
    }

    private fun germany(key: String, c: LL, km: Int, fuel: String): List<Station> {
        if (key.isBlank()) return emptyList()
        val type = when (fuel) { "B7" -> "diesel"; "E5" -> "e5"; else -> "e10" }
        val url = "https://creativecommons.tankerkoenig.de/json/list.php?lat=${c.lat}&lng=${c.lon}&rad=$km&sort=dist&type=all&apikey=" + URLEncoder.encode(key, "UTF-8")
        val o = JSONObject(get(url))
        if (!o.optBoolean("ok", false)) return emptyList()
        val arr = o.optJSONArray("stations") ?: return emptyList()
        val out = ArrayList<Station>()
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            if (s.has("isOpen") && !s.optBoolean("isOpen", true)) continue
            val euros = s.optDouble(type, Double.NaN)
            if (euros.isNaN() || euros <= 0.0) continue
            val name = s.optString("brand").ifBlank { s.optString("name") }.ifBlank { "Station" }
            out += Station(name, s.optString("place"), LL(s.getDouble("lat"), s.getDouble("lng")), euros * 100.0)
        }
        return out
    }

    // ---- Australia: NSW FuelCheck (covers New South Wales and Tasmania) ----

    @Volatile private var nswToken = ""
    @Volatile private var nswTokenUntil = 0L
    @Volatile private var nswTokenFor = ""
    private var nswTx = 1

    /**
     * The NSW government's FuelCheck: a token from the API key and secret (kept until it runs out), then the stations near a
     * point with today's price for one fuel. "Unleaded" is U91, "Super unleaded" P95, diesel DL. A refused key (401 / 403) says
     * so in the error (it shows as no prices, and the station search carries on without them).
     */
    private fun nsw(prefs: Prefs, c: LL, km: Int, fuel: String): List<Station> {
        val key = prefs.s(PreferenceKeys.NSW_FUEL_KEY, "").trim()
        val secret = prefs.s(PreferenceKeys.NSW_FUEL_SECRET, "").trim()
        if (key.isBlank() || secret.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        if (nswToken.isBlank() || now > nswTokenUntil || nswTokenFor != key) {
            val basic = android.util.Base64.encodeToString("$key:$secret".toByteArray(), android.util.Base64.NO_WRAP)
            val t = nswHttp("GET", "https://api.onegov.nsw.gov.au/oauth/client_credential/accesstoken?grant_type=client_credentials", null,
                mapOf("Authorization" to "Basic $basic"))
            val o = JSONObject(t)
            nswToken = o.optString("access_token")
            if (nswToken.isBlank()) throw java.io.IOException("FuelCheck didn't give a token")
            // (tokens last about 12 hours: renewed a little early)
            val secs = o.optString("expires_in").toLongOrNull() ?: 3600L
            nswTokenUntil = now + (secs - 300).coerceAtLeast(300) * 1000L
            nswTokenFor = key
        }
        val type = when (fuel) { "B7" -> "DL"; "E5" -> "P95"; "E85" -> "E85"; "LPG" -> "LPG"; else -> "U91" }
        val stamp = java.text.SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
        val body = JSONObject().put("fueltype", type).put("latitude", c.lat.toString()).put("longitude", c.lon.toString())
            .put("radius", km.toString()).put("sortby", "Price").put("sortascending", "true").toString()
        val res = JSONObject(nswHttp("POST", "https://api.onegov.nsw.gov.au/FuelPriceCheck/v2/fuel/prices/nearby", body, mapOf(
            "apikey" to key, "Authorization" to "Bearer $nswToken", "transactionid" to (nswTx++).toString(), "requesttimestamp" to stamp,
            "Content-Type" to "application/json; charset=utf-8")))
        val prices = HashMap<String, Double>()
        res.optJSONArray("prices")?.let { arr -> for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { p ->
            if (p.optString("fueltype").equals(type, true) || p.optString("fueltype").isBlank()) {
                val v = p.optDouble("price", Double.NaN)
                if (!v.isNaN() && v > 0) prices[p.optString("stationcode")] = v
            }
        } }
        val out = ArrayList<Station>()
        res.optJSONArray("stations")?.let { arr -> for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val cents = prices[s.optString("code")] ?: continue
            val loc = s.optJSONObject("location") ?: continue
            val name = s.optString("brand").ifBlank { s.optString("name") }.ifBlank { "Station" }
            out += Station(name, s.optString("address"), LL(loc.optDouble("latitude"), loc.optDouble("longitude")), cents)
        } }
        return out
    }

    private fun nswHttp(method: String, url: String, body: String?, headers: Map<String, String>): String {
        val c = URL(url).also { com.headunit.launcher.Net.check() }.openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 15000
        c.requestMethod = method
        c.setRequestProperty("User-Agent", "HorizonNav/${BuildConfig.VERSION_NAME}")
        c.setRequestProperty("Accept", "application/json")
        for ((k, v) in headers) c.setRequestProperty(k, v)
        try {
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
            val code = c.responseCode
            if (code == 401 || code == 403) { nswToken = ""; throw java.io.IOException("FuelCheck refused the key (HTTP $code)") }
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    // ------------------------------------------------------------------ Italy (MIMIT open data)

    /** Where the app keeps its files (set when the app starts), for Italy's list. */
    @Volatile var filesDir: java.io.File? = null

    /** One Italian station: where, its name and town, and its price in cents for petrol, diesel and LPG (NaN when it has none). */
    private class ItSt(val pos: LL, val name: String, val town: String, val petrol: Double, val diesel: Double, val lpg: Double)
    @Volatile private var itList: List<ItSt>? = null
    @Volatile private var itListAt = 0L
    @Volatile private var itFailAt = 0L
    private val itLock = Any()

    private fun italy(c: LL, km: Int, fuel: String): List<Station> {
        val list = italyList() ?: return emptyList()
        val r = km * 1000.0
        val dLat = r / 111_000.0
        val dLon = r / (111_000.0 * Math.cos(Math.toRadians(c.lat)).coerceAtLeast(0.2))
        val out = ArrayList<Station>()
        for (s in list) {
            if (Math.abs(s.pos.lat - c.lat) > dLat || Math.abs(s.pos.lon - c.lon) > dLon) continue
            val p = when (fuel) { "B7" -> s.diesel; "LPG" -> s.lpg; else -> s.petrol }
            if (p.isNaN()) continue
            if (Geo.dist(c, s.pos) <= r) out += Station(s.name, s.town, s.pos, p)
        }
        // the cheapest 60, as the other services give
        return out.sortedBy { it.cents }.take(60)
    }

    /** The list for the whole of Italy: from memory, else from the device, fetched fresh when it's over a day old. */
    private fun italyList(): List<ItSt>? = synchronized(itLock) {
        val dir = filesDir ?: return null
        val f = java.io.File(dir, "it_fuel.tsv")
        val now = System.currentTimeMillis()
        // fresh enough: the ministry publishes once a day (the prices in force at 8 that morning)
        // (after a failed fetch, not tried again for half an hour: with no signal every search would wait on it)
        if ((!f.exists() || now - f.lastModified() > 20 * 3600_000L) && now - itFailAt > 30 * 60_000L) {
            try { italyFetch(f) } catch (e: Exception) { itFailAt = now }   // (no signal: yesterday's prices beat none)
        }
        if (!f.exists()) return null
        if (itList != null && itListAt == f.lastModified()) return itList
        val out = ArrayList<ItSt>(24_000)
        try {
            f.bufferedReader().useLines { lines ->
                for (ln in lines) {
                    val p = ln.split('\t')
                    if (p.size < 7) continue
                    val lat = p[0].toDoubleOrNull() ?: continue; val lon = p[1].toDoubleOrNull() ?: continue
                    out += ItSt(LL(lat, lon), p[2], p[3], p[4].toDoubleOrNull() ?: Double.NaN, p[5].toDoubleOrNull() ?: Double.NaN, p[6].toDoubleOrNull() ?: Double.NaN)
                }
            }
        } catch (e: Exception) { return null }
        itList = out; itListAt = f.lastModified()
        return out
    }

    /**
     * Fetches the two files (the stations, then the prices) and boils them down to one line per station with a price:
     * position, name, town, then petrol, diesel and LPG in cents. Read line by line as they arrive, so the few megabytes never sit
     * in memory at once. Written to a new file and swapped in only when both came through whole.
     */
    private fun italyFetch(out: java.io.File) {
        class Row(val pos: LL, val name: String, val town: String) { var petrol = Double.NaN; var diesel = Double.NaN; var lpg = Double.NaN; var self = BooleanArray(3) }
        val rows = HashMap<String, Row>(30_000)
        italyLines("anagrafica_impianti_attivi.csv") { cols, ix ->
            val id = cols.getOrNull(ix("idimpianto")) ?: return@italyLines
            val lat = cols.getOrNull(ix("latitudine"))?.replace(',', '.')?.toDoubleOrNull() ?: return@italyLines
            val lon = cols.getOrNull(ix("longitudine"))?.replace(',', '.')?.toDoubleOrNull() ?: return@italyLines
            // (stations with no position, or a made-up one outside Italy, are left out)
            if (lat !in 35.0..48.0 || lon !in 6.0..19.0) return@italyLines
            val brand = cols.getOrNull(ix("bandiera")).orEmpty().trim()
            val name = cols.getOrNull(ix("nome impianto")).orEmpty().trim()
            val shown = (if (brand.isNotBlank() && !brand.equals("Pompe Bianche", true)) brand else name).ifBlank { "Distributore" }
            val town = cols.getOrNull(ix("comune")).orEmpty().trim().lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
            rows[id.trim()] = Row(LL(lat, lon), shown.replace('\t', ' '), town.replace('\t', ' '))
        }
        if (rows.size < 1000) throw java.io.IOException("Station list too short")
        // prices older than two months are left out (a station that stopped reporting)
        val tooOld = System.currentTimeMillis() - 60L * 86_400_000L
        val df = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.ITALY)
        var priced = 0
        italyLines("prezzo_alle_8.csv") { cols, ix ->
            val r = rows[cols.getOrNull(ix("idimpianto"))?.trim() ?: return@italyLines] ?: return@italyLines
            val kind = cols.getOrNull(ix("desccarburante")).orEmpty().trim().lowercase()
            val slot = when (kind) { "benzina" -> 0; "gasolio" -> 1; "gpl" -> 2; else -> return@italyLines }
            val euros = cols.getOrNull(ix("prezzo"))?.replace(',', '.')?.toDoubleOrNull() ?: return@italyLines
            if (euros !in 0.3..4.0) return@italyLines
            val at = try { df.parse(cols.getOrNull(ix("dtcomu")).orEmpty().trim())?.time ?: 0L } catch (e: Exception) { 0L }
            if (at in 1 until tooOld) return@italyLines
            val self = cols.getOrNull(ix("isself"))?.trim() == "1"
            val cents = euros * 100.0
            // self-service when the station has it (the price most drivers pay), else the served price; the lower of each
            val cur = when (slot) { 0 -> r.petrol; 1 -> r.diesel; else -> r.lpg }
            val better = cur.isNaN() || (self && !r.self[slot]) || (self == r.self[slot] && cents < cur)
            if (!better) return@italyLines
            r.self[slot] = self
            when (slot) { 0 -> r.petrol = cents; 1 -> r.diesel = cents; else -> r.lpg = cents }
            priced++
        }
        if (priced < 1000) throw java.io.IOException("Price list too short")
        val tmp = java.io.File(out.parentFile, out.name + ".tmp")
        tmp.bufferedWriter().use { w ->
            for (r in rows.values) {
                if (r.petrol.isNaN() && r.diesel.isNaN() && r.lpg.isNaN()) continue
                fun n(x: Double) = if (x.isNaN()) "" else String.format(java.util.Locale.US, "%.1f", x)
                w.write("${r.pos.lat}\t${r.pos.lon}\t${r.name}\t${r.town}\t${n(r.petrol)}\t${n(r.diesel)}\t${n(r.lpg)}\n")
            }
        }
        if (!tmp.renameTo(out)) { out.delete(); if (!tmp.renameTo(out)) throw java.io.IOException("Couldn't save") }
    }

    /**
     * One of the ministry's files, a line at a time: the first line is the extraction date, the second the column names. The
     * columns have been split by ";" and (since February 2026) by "|": whichever the names line uses is used. [ix] finds a
     * column by its name (any case). Tried at the ministry's current address, then its old one.
     */
    private fun italyLines(file: String, each: (List<String>, (String) -> Int) -> Unit) {
        var last: Exception? = null
        for (host in listOf("https://www.mimit.gov.it/images/exportCSV/", "https://www.mise.gov.it/images/exportCSV/")) {
            val c = URL(host + file).also { com.headunit.launcher.Net.check() }.openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "HorizonNav/${BuildConfig.VERSION_NAME}")
            try {
                if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
                var total = 0L
                c.inputStream.bufferedReader(Charsets.UTF_8).use { rd ->
                    var names: List<String>? = null
                    var sep = '|'
                    var index: (String) -> Int = { -1 }
                    while (true) {
                        val ln = rd.readLine() ?: break
                        total += ln.length
                        if (total > 60_000_000L) throw java.io.IOException("File too large")
                        if (names == null) {
                            if (!ln.contains("idImpianto", true)) continue   // (the "Estrazione del ..." line)
                            sep = if (ln.count { it == '|' } >= ln.count { it == ';' }) '|' else ';'
                            val nm = ln.split(sep).map { it.trim().trim('"').lowercase() }
                            names = nm
                            index = { n -> nm.indexOf(n) }
                            continue
                        }
                        each(ln.split(sep).map { it.trim('"') }, index)
                    }
                    if (names == null) throw java.io.IOException("No column names")
                }
                return
            } catch (e: Exception) { last = e } finally { c.disconnect() }
        }
        throw last ?: java.io.IOException("Couldn't fetch $file")
    }
}
