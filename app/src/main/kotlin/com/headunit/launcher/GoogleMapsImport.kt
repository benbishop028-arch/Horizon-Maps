package com.headunit.launcher

import org.json.JSONArray
import org.json.JSONObject

/** Reads the point features in a Google Takeout Maps "Saved Places.json" export. */
object GoogleMapsImport {
    private const val MAX_CHARS = 24 * 1024 * 1024

    fun parse(text: String): List<Place> {
        require(text.isNotBlank() && text.length <= MAX_CHARS) { "Invalid or oversized file" }
        val rootObject = try { JSONObject(text) } catch (_: Exception) { null }
        val rootArray = if (rootObject == null) try { JSONArray(text) } catch (_: Exception) { null } else null
        val entries = rootObject?.optJSONArray("features")
            ?: rootObject?.optJSONArray("savedPlaces")
            ?: rootObject?.optJSONArray("places")
            ?: rootObject?.optJSONArray("items")
            ?: rootArray
            ?: JSONArray()
        val result = ArrayList<Place>()
        for (i in 0 until entries.length()) {
            val feature = entries.optJSONObject(i) ?: continue
            val properties = feature.optJSONObject("properties") ?: feature
            val geometry = feature.optJSONObject("geometry")
            val coordinates = geometry?.optJSONArray("coordinates")
                ?: feature.optJSONArray("coordinates")
                ?: properties.optJSONArray("coordinates")
            val location = feature.optJSONObject("location")
                ?: feature.optJSONObject("Location")
                ?: properties.optJSONObject("location")
                ?: properties.optJSONObject("Location")
            val lat: Double
            val lon: Double
            if (coordinates != null && coordinates.length() >= 2) {
                // GeoJSON stores longitude first, latitude second.
                lon = coordinates.optDouble(0, Double.NaN)
                lat = coordinates.optDouble(1, Double.NaN)
            } else {
                lat = number(feature, properties, location, keys = arrayOf("latitude", "lat"))
                lon = number(feature, properties, location, keys = arrayOf("longitude", "lng", "lon"))
            }
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            val name = string(properties, "Title", "title", "name", "Name", "label")
                .ifBlank { string(feature, "Title", "title", "name", "Name") }
            if (name.isBlank()) continue
            val sub = string(properties, "address", "Address", "Comment", "comment", "description", "Description", "Category", "category")
                .ifBlank { string(feature, "address", "Address", "Comment", "comment") }
            result += Place(name.take(120), sub.take(240), LL(lat, lon))
        }
        return result
    }

    private fun string(obj: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = obj.opt(key)
            if (value is String && value.isNotBlank()) return value.trim()
        }
        return ""
    }

    private fun number(vararg objects: JSONObject?, keys: Array<out String>): Double {
        for (obj in objects) {
            if (obj == null) continue
            for (key in keys) {
                if (!obj.has(key)) continue
                val value = obj.optDouble(key, Double.NaN)
                if (value.isFinite()) return value
            }
        }
        return Double.NaN
    }
}
