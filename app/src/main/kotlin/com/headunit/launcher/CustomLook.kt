package com.headunit.launcher

import org.json.JSONArray
import org.json.JSONObject

/**
 * The Custom map theme (Settings > Map style > Colours > Custom): a theme it starts from ([PreferenceKeys.CUSTOM_BASE]) with a few
 * of its colours changed, separately for day and night. An untouched part keeps the starting theme's colour, so a new Custom looks
 * exactly like the theme you had. Themes can be saved under a name and picked again later.
 */
object CustomLook {
    /** A part of the map that can have its own colour: key, name, what it covers, and Mapbox's settings for it. */
    class Part(val key: String, val name: String, val sub: String, val config: List<String>)

    val parts = listOf(
        Part("land", "Ground", "Land, between everything", listOf("colorLand")),
        Part("water", "Water", "Sea, rivers, lakes", listOf("colorWater")),
        Part("green", "Parks and grass", "Parks, woods, fields", listOf("colorGreenspace")),
        Part("roads", "Roads", "Ordinary roads", listOf("colorRoads")),
        Part("main", "Main roads and motorways", "A roads, dual carriageways, motorways", listOf("colorTrunks", "colorMotorways")),
        Part("buildings", "Buildings", "Mapbox's buildings, detailed fronts and all", listOf("colorBuildings")),
    )

    /** One of day or night: part key to "#RRGGBB" (a part not here keeps the theme's colour), and the route line's colour. */
    data class Spec(val colours: Map<String, String> = emptyMap(), val route: String? = null) {
        val isEmpty: Boolean get() = colours.isEmpty() && route == null
        fun toJson(): String = JSONObject().apply {
            put("colours", JSONObject(colours as Map<*, *>))
            route?.let { put("route", it) }
        }.toString()
    }

    fun parse(json: String?): Spec {
        if (json.isNullOrBlank()) return Spec()
        return try {
            val o = JSONObject(json)
            val cols = HashMap<String, String>()
            o.optJSONObject("colours")?.let { c -> for (k in c.keys()) { val v = c.optString(k); if (validHex(v)) cols[k] = v } }
            Spec(cols, o.optString("route").takeIf { validHex(it) })
        } catch (e: Exception) { Spec() }
    }

    fun validHex(s: String?): Boolean = s != null && Regex("^#[0-9A-Fa-f]{6}$").matches(s)
    fun hex(c: Int): String = String.format("#%06X", c and 0xFFFFFF)

    /** A saved theme. */
    class Saved(val name: String, val base: String, val day: String, val night: String, val width: Int)

    fun saved(prefs: Prefs): List<Saved> = try {
        val a = JSONArray(prefs.s(PreferenceKeys.CUSTOM_SAVED, "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Saved(o.optString("name").ifBlank { "My theme" }, o.optString("base", "mapbox"), o.optString("day"), o.optString("night"), o.optInt("width", 100))
        }
    } catch (e: Exception) { emptyList() }

    private fun store(prefs: Prefs, list: List<Saved>) {
        val a = JSONArray()
        for (s in list) a.put(JSONObject().put("name", s.name).put("base", s.base).put("day", s.day).put("night", s.night).put("width", s.width))
        prefs.put(PreferenceKeys.CUSTOM_SAVED, a.toString())
    }

    /** Saves the Custom theme as it is now under [name] (replacing one of the same name). Up to 12. */
    fun saveCurrent(prefs: Prefs, name: String) {
        val n = name.trim().take(30).ifBlank { "My theme" }
        val s = Saved(n, prefs.s(PreferenceKeys.CUSTOM_BASE, "mapbox"), prefs.s(PreferenceKeys.CUSTOM_DAY, ""), prefs.s(PreferenceKeys.CUSTOM_NIGHT, ""),
            prefs.i(PreferenceKeys.CUSTOM_ROUTE_W, 100))
        store(prefs, (saved(prefs).filter { !it.name.equals(n, true) } + s).takeLast(12))
    }

    /** Makes saved theme [s] the Custom theme, and switches to it. */
    fun use(prefs: Prefs, s: Saved) {
        prefs.put(PreferenceKeys.CUSTOM_BASE, s.base)
        prefs.put(PreferenceKeys.CUSTOM_DAY, s.day)
        prefs.put(PreferenceKeys.CUSTOM_NIGHT, s.night)
        prefs.put(PreferenceKeys.CUSTOM_ROUTE_W, s.width)
        prefs.put(PreferenceKeys.MAP_COLOURS, "custom")
    }

    fun delete(prefs: Prefs, name: String) = store(prefs, saved(prefs).filter { it.name != name })
}
