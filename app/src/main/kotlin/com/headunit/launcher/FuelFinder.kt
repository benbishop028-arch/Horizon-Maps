package com.headunit.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Live UK pump prices from the government's Fuel Finder scheme (fuel-finder.service.gov.uk).
 * Needs your own free API client ID and secret (register as an "information recipient" with a GOV.UK One Login).
 * Station details are cached for a week and prices for 30 minutes, both in files, so a list of stations
 * still works with no signal (with the last known prices).
 */
object FuelFinder {
    private const val BASE = "https://www.fuel-finder.service.gov.uk"
    const val REPORT_URL = "https://www.fuel-finder.service.gov.uk/motorist/price-report"

    class Station(val id: String, val name: String, val brand: String, val pos: LL, val motorway: Boolean, val postcode: String)
    class Price(val pence: Double, val updated: String)

    fun configured(prefs: Prefs) = prefs.s(PreferenceKeys.FFID, "").isNotBlank() && prefs.s(PreferenceKeys.FFSECRET, "").isNotBlank()

    /** What happened last time, for the settings page. */
    @Volatile var status = ""

    private var token: String? = null
    private var tokenType = "Bearer"
    private var tokenUntil = 0L

    @Volatile private var stationCache: List<Station>? = null
    @Volatile private var priceCache: Map<String, Map<String, Price>>? = null

    private fun stationsFile(ctx: Context) = File(ctx.filesDir, "ff_stations.tsv")
    private fun pricesFile(ctx: Context) = File(ctx.filesDir, "ff_prices.tsv")

    private fun http(method: String, url: String, body: String?, auth: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000; c.readTimeout = 30000
        c.requestMethod = method
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "HorizonNav/1.0")
        if (auth != null) c.setRequestProperty("Authorization", auth)
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        if (code !in 200..299) throw IOException("HTTP $code ${text.take(160)}")
        return text
    }

    private fun ensureToken(prefs: Prefs) {
        if (token != null && System.currentTimeMillis() < tokenUntil) return
        val body = JSONObject().put("client_id", prefs.s(PreferenceKeys.FFID, "").trim()).put("client_secret", prefs.s(PreferenceKeys.FFSECRET, "").trim()).toString()
        val o = JSONObject(http("POST", "$BASE/api/v1/oauth/generate_access_token", body, null))
        val d = o.optJSONObject("data") ?: o
        val t = d.optString("access_token")
        if (t.isBlank()) throw IOException("sign-in refused: " + o.optString("message").ifBlank { "check the client ID and secret" })
        token = t
        tokenType = d.optString("token_type").ifBlank { "Bearer" }.let { if (it.equals("bearer", true)) "Bearer" else it }
        tokenUntil = System.currentTimeMillis() + (d.optLong("expires_in", 1800L).coerceIn(60L, 86_400L) - 60L) * 1000L
    }

    /** The API answers with a plain list, or a list wrapped in "data" (sometimes twice). */
    private fun arrayIn(text: String): JSONArray {
        val t = text.trim()
        if (t.startsWith("[")) return JSONArray(t)
        val o = JSONObject(t)
        o.optJSONArray("data")?.let { return it }
        o.optJSONObject("data")?.let { d -> d.optJSONArray("data")?.let { return it } }
        return JSONArray()
    }

    /** Reads every batch of an endpoint until an empty or short one comes back. */
    private fun getAll(prefs: Prefs, path: String, since: String?): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        var batch = 1
        var retries = 0
        while (batch <= 60) {
            ensureToken(prefs)
            var url = "$BASE$path?batch-number=$batch"
            if (since != null) url += "&effective-start-timestamp=" + URLEncoder.encode(since, "UTF-8")
            val text = try { http("GET", url, null, "$tokenType $token") } catch (e: IOException) {
                val m = e.message.orEmpty()
                if (m.startsWith("HTTP 404") || m.contains("No more data", true)) break
                if (retries < 2 && (m.startsWith("HTTP 401") || (m.startsWith("HTTP 403") && m.contains("token", true)))) { retries++; token = null; continue }
                throw e
            }
            val arr = arrayIn(text)
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out += it }
            if (arr.length() < 500) break
            batch++
        }
        return out
    }

    /** Prices come in pence, pounds or tenths of a penny depending on the retailer: everything is turned into pence. */
    private fun pence(v: Double): Double? {
        var p = v
        if (p <= 0.0) return null
        if (p < 10.0) p *= 100.0
        if (p > 400.0) p /= 10.0
        return if (p in 60.0..400.0) Math.round(p * 10.0) / 10.0 else null
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').trim()

    /** Brings the station list and prices up to date (blocking: call it off the main thread). */
    fun refresh(ctx: Context, prefs: Prefs, force: Boolean = false) {
        if (!configured(prefs)) return
        try {
            val sf = stationsFile(ctx)
            if (force || !sf.exists() || System.currentTimeMillis() - sf.lastModified() > 7L * 86_400_000L) {
                val all = getAll(prefs, "/api/v1/pfs", null)
                val sb = StringBuilder()
                var n = 0
                for (o in all) {
                    if (o.optBoolean("permanent_closure", false) || o.optBoolean("temporary_closure", false)) continue
                    val loc = o.optJSONObject("location") ?: continue
                    val la = loc.optDouble("latitude", Double.NaN); val lo = loc.optDouble("longitude", Double.NaN)
                    if (la.isNaN() || lo.isNaN() || la == 0.0) continue
                    val id = o.optString("node_id"); if (id.isBlank()) continue
                    val name = clean(o.optString("trading_name").ifBlank { o.optString("brand_name") }.ifBlank { "Fuel station" })
                    sb.append(id).append('\t').append(name).append('\t').append(clean(o.optString("brand_name"))).append('\t')
                        .append(la).append('\t').append(lo).append('\t').append(if (o.optBoolean("is_motorway_service_station", false)) "1" else "0").append('\t')
                        .append(clean(loc.optString("postcode"))).append('\n')
                    n++
                }
                if (n > 0) { sf.writeText(sb.toString()); stationCache = null }
            }
            val pf = pricesFile(ctx)
            val age = if (pf.exists()) System.currentTimeMillis() - pf.lastModified() else Long.MAX_VALUE
            if (force || age > 30L * 60_000L) {
                val cur = HashMap<String, HashMap<String, Price>>()
                // only what changed since the last download (with an hour's overlap), unless there is nothing yet
                var since: String? = null
                if (pf.exists() && !force && age < 3L * 86_400_000L) {
                    for ((id, m) in loadPrices(ctx)) cur[id] = HashMap(m)
                    val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.UK)
                    f.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    since = f.format(java.util.Date(pf.lastModified() - 3_600_000L))
                }
                for (o in getAll(prefs, "/api/v1/pfs/fuel-prices", since)) {
                    val id = o.optString("node_id"); if (id.isBlank()) continue
                    val arr = o.optJSONArray("fuel_prices") ?: continue
                    val m = cur.getOrPut(id) { HashMap() }
                    for (i in 0 until arr.length()) {
                        val e = arr.optJSONObject(i) ?: continue
                        val type = e.optString("fuel_type").trim().uppercase(); if (type.isBlank()) continue
                        val p = pence(e.optDouble("price", -1.0)) ?: continue
                        m[type] = Price(p, e.optString("price_last_updated").ifBlank { e.optString("price_change_effective_timestamp") })
                    }
                }
                val sb = StringBuilder()
                for ((id, m) in cur) for ((t, p) in m) sb.append(id).append('\t').append(t).append('\t').append(p.pence).append('\t').append(clean(p.updated)).append('\n')
                if (cur.isNotEmpty()) { pf.writeText(sb.toString()); priceCache = null }
            }
            status = "Prices updated " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(java.util.Date(pricesFile(ctx).lastModified())) +
                " · ${stations(ctx).size} stations"
        } catch (e: Exception) {
            status = "Fuel prices failed: " + (e.message ?: e.javaClass.simpleName).take(160)
            throw e
        }
    }

    fun stations(ctx: Context): List<Station> {
        stationCache?.let { return it }
        val f = stationsFile(ctx)
        if (!f.exists()) return emptyList()
        val out = ArrayList<Station>()
        try {
            f.forEachLine { l ->
                val p = l.split('\t')
                if (p.size >= 6) {
                    val la = p[3].toDoubleOrNull(); val lo = p[4].toDoubleOrNull()
                    if (la != null && lo != null) out += Station(p[0], p[1], p[2], LL(la, lo), p[5] == "1", p.getOrNull(6).orEmpty())
                }
            }
        } catch (e: Exception) { }
        stationCache = out
        return out
    }

    private fun loadPrices(ctx: Context): Map<String, Map<String, Price>> {
        priceCache?.let { return it }
        val f = pricesFile(ctx)
        val out = HashMap<String, HashMap<String, Price>>()
        if (f.exists()) try {
            f.forEachLine { l ->
                val p = l.split('\t')
                if (p.size >= 3) { val v = p[2].toDoubleOrNull(); if (v != null) out.getOrPut(p[0]) { HashMap() }[p[1]] = Price(v, p.getOrNull(3).orEmpty()) }
            }
        } catch (e: Exception) { }
        priceCache = out
        return out
    }

    /** Price of the chosen fuel ("E10", "E5" or "B7") at a station, or null. */
    fun priceOf(ctx: Context, id: String, fuel: String): Price? {
        val m = loadPrices(ctx)[id] ?: return null
        m[fuel]?.let { return it }
        return when (fuel) {
            "B7" -> m["B7_STANDARD"] ?: m.entries.firstOrNull { it.key.startsWith("B7") && !it.key.contains("PREMIUM") }?.value ?: m.entries.firstOrNull { it.key.contains("DIESEL") }?.value
            "E5" -> m.entries.firstOrNull { it.key.startsWith("E5") }?.value
            "E85", "EV" -> null
            else -> m.entries.firstOrNull { it.key.startsWith("E10") }?.value
        }
    }

    /** "2 h ago" style age of a price, from its timestamp text. */
    fun ageText(updated: String): String {
        return try {
            val t = updated.replace('T', ' ').take(19)
            val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.UK)
            f.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val ms = System.currentTimeMillis() - (f.parse(t)?.time ?: return "")
            val h = ms / 3_600_000L
            if (h < 1) "just now" else if (h < 48) "${h} h ago" else "${h / 24} days ago"
        } catch (e: Exception) { "" }
    }

    fun fuelName(fuel: String) = when (fuel) { "E5" -> "Super unleaded"; "B7" -> "Diesel"; "E85" -> "Bioethanol (E85)"; "EV" -> "Electric"; else -> "Unleaded" }
}
