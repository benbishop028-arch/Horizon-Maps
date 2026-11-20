package com.headunit.launcher

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/**
 * Current weather from the Met Office (UK) with your own key from the Met Office Weather DataHub (Site-Specific "Global
 * spot" API, free plan: 360 calls a day). Open-Meteo's model often kept showing rain over the UK for hours after it had stopped;
 * the Met Office's own forecast for the spot is closer. Used for the current weather only (rain on the map, weather advice);
 * anything that goes wrong falls back to Open-Meteo.
 *
 * API: GET https://data.hub.api.metoffice.gov.uk/sitespecific/v0/point/hourly?latitude=..&longitude=.. with header "apikey".
 * Reply: GeoJSON, features[0].properties.timeSeries[] = { time: "2026-11-20T14:00Z", screenTemperature, significantWeatherCode, ... }
 */
object MetOffice {
    private const val TAG = "HorizonWeather"
    private const val URL_HOURLY = "https://data.hub.api.metoffice.gov.uk/sitespecific/v0/point/hourly"

    /** What the last Met Office request did, shown under the key in Settings (and in logcat, tag HorizonWeather). */
    var status by mutableStateOf("")
        private set

    class Result(val tempC: Double, val moCode: Int, val wmo: Int, val slot: String, val rateMmH: Double, val site: String)

    /** The current hour's weather at [p], or an exception saying why not (HTTP status, bad key, no data). */
    fun now(p: LL, key: String): Result {
        val t0 = System.currentTimeMillis()
        try {
            val url = "$URL_HOURLY?latitude=${"%.4f".format(java.util.Locale.US, p.lat)}&longitude=${"%.4f".format(java.util.Locale.US, p.lon)}" +
                "&includeLocationName=true"
            val c = URL(url).also { Net.check() }.openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 15000
            c.setRequestProperty("apikey", key.trim())
            c.setRequestProperty("accept", "application/json")
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
            if (code !in 200..299) {
                // 401/403: key wrong or not subscribed to Site-Specific; 429: the day's calls are used up
                val why = when (code) { 401, 403 -> "key refused (check it's subscribed to Site-Specific Forecast)"; 429 -> "daily limit reached"; else -> "error" }
                throw IOException("HTTP $code $why ${body.take(160)}")
            }
            val props = JSONObject(body).getJSONArray("features").getJSONObject(0).getJSONObject("properties")
            val site = props.optJSONObject("location")?.optString("name").orEmpty()
            val ts = props.getJSONArray("timeSeries")
            if (ts.length() == 0) throw IOException("no timeSeries in reply")
            // the hour nearest to now: the code and temperature are for that moment, not a summary of the hour before
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            var best = ts.getJSONObject(0); var bestDiff = Long.MAX_VALUE
            for (i in 0 until ts.length()) {
                val o = ts.getJSONObject(i)
                val at = try { fmt.parse(o.getString("time").take(16))?.time } catch (e: Exception) { null } ?: continue
                val d = abs(at - t0)
                if (d < bestDiff) { bestDiff = d; best = o }
            }
            val mo = best.optInt("significantWeatherCode", -99)
            if (mo == -99) throw IOException("no significantWeatherCode at ${best.optString("time")}")
            val r = Result(best.getDouble("screenTemperature"), mo, toWmo(mo), best.optString("time"), best.optDouble("precipitationRate", 0.0), site)
            status = "OK ${clock()}: ${describe(mo)} (Met Office code $mo), %.1f°C, rain %.1f mm/h, for ${r.slot}%s".format(
                java.util.Locale.US, r.tempC, r.rateMmH, if (site.isNotBlank()) " near $site" else "")
            Log.i(TAG, "MetOffice ${System.currentTimeMillis() - t0} ms: $status")
            return r
        } catch (e: Exception) {
            status = "Failed ${clock()}: ${e.message ?: e.javaClass.simpleName}. Using Open-Meteo instead."
            Log.w(TAG, "MetOffice: $status")
            throw e
        }
    }

    private fun clock() = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())

    /** Met Office significant weather code (0..30, -1 trace rain) to the WMO / Open-Meteo code the rest of the app reads. */
    fun toWmo(mo: Int): Int = when (mo) {
        -1 -> 3                 // trace rain: not enough to call it rain
        0, 1 -> 0               // clear night / sunny day
        2, 3 -> 2               // partly cloudy
        5, 6 -> 45              // mist, fog
        7, 8 -> 3               // cloudy, overcast
        9, 10 -> 80             // light rain shower
        11 -> 51                // drizzle
        12 -> 61                // light rain
        13, 14 -> 82            // heavy rain shower
        15 -> 65                // heavy rain
        16, 17 -> 80            // sleet shower (shown as rain: no sleet in WMO codes the app uses)
        18 -> 63                // sleet
        19, 20 -> 81            // hail shower
        21 -> 82                // hail
        22, 23 -> 85            // light snow shower
        24 -> 71                // light snow
        25, 26 -> 86            // heavy snow shower
        27 -> 75                // heavy snow
        28, 29, 30 -> 95        // thunder shower, thunder
        else -> 3
    }

    fun describe(mo: Int): String = when (mo) {
        -1 -> "Trace rain"; 0 -> "Clear night"; 1 -> "Sunny"; 2, 3 -> "Partly cloudy"; 5 -> "Mist"; 6 -> "Fog"
        7 -> "Cloudy"; 8 -> "Overcast"; 9, 10 -> "Light rain shower"; 11 -> "Drizzle"; 12 -> "Light rain"
        13, 14 -> "Heavy rain shower"; 15 -> "Heavy rain"; 16, 17 -> "Sleet shower"; 18 -> "Sleet"; 19, 20 -> "Hail shower"
        21 -> "Hail"; 22, 23 -> "Light snow shower"; 24 -> "Light snow"; 25, 26 -> "Heavy snow shower"; 27 -> "Heavy snow"
        28, 29 -> "Thunder shower"; 30 -> "Thunder"; else -> "Unknown"
    }
}
