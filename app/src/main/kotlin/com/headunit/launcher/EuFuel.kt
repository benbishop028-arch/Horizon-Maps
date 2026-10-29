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
 * Prices come back in cents a litre. Everything here is network work: call it off the main thread. Any failure gives an empty
 * list, so the app simply falls back to its normal station search without prices.
 */
object EuFuel {
    class Station(val name: String, val sub: String, val pos: LL, val cents: Double)

    /** Countries with a price service here (Germany only once a Tankerkönig key is in). */
    fun supported(country: String, prefs: Prefs): Boolean = when (country.uppercase()) {
        "FR", "AT" -> true
        "DE" -> prefs.s(PreferenceKeys.TANKERKOENIG_KEY, "").isNotBlank()
        else -> false
    }

    /** Whether the country's service has prices for this fuel at all ("E10", "E5", "B7" or "E85"). */
    fun hasFuel(country: String, fuel: String): Boolean = when (country.uppercase()) {
        "FR" -> fuel in setOf("E10", "E5", "B7", "E85")
        "AT", "DE" -> fuel in setOf("E10", "E5", "B7")
        else -> false
    }

    /** Stations with a price for [fuel] within [radiusKm] of [c] (at most about 25 km; the services cap it anyway). */
    fun near(country: String, prefs: Prefs, c: LL, radiusKm: Int, fuel: String): List<Station> = try {
        when (country.uppercase()) {
            "FR" -> france(c, radiusKm.coerceIn(1, 25), fuel)
            "AT" -> austria(c, fuel)
            "DE" -> germany(prefs.s(PreferenceKeys.TANKERKOENIG_KEY, "").trim(), c, radiusKm.coerceIn(1, 25), fuel)
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
        val c = URL(url).openConnection() as HttpURLConnection
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
        val field = when (fuel) { "B7" -> "gazole_prix"; "E5" -> "sp95_prix"; "E85" -> "e85_prix"; else -> "e10_prix" }
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
}
