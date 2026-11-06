package com.headunit.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * The Custom map colours (Settings > Map look > Colours > Custom): one [Spec] for day and one for night, each starting empty,
 * which means "as the theme it started from" ([PreferenceKeys.CUSTOM_BASE]). Only what's been changed is set on the map.
 * Saved as JSON in [PreferenceKeys.CUSTOM_DAY] / [PreferenceKeys.CUSTOM_NIGHT].
 */
object CustomLook {
    /** The parts of the map that can be given their own colour: key, name, what it covers, and Standard's settings for it. */
    class Part(val key: String, val name: String, val sub: String, val config: List<String>)

    val parts = listOf(
        Part("land", "Ground", "Land, between everything", listOf("colorLand")),
        Part("water", "Water", "Sea, rivers, lakes", listOf("colorWater")),
        Part("green", "Parks and grass", "Parks, woods, fields", listOf("colorGreenspace")),
        Part("roads", "Roads", "Ordinary roads", listOf("colorRoads")),
        Part("main", "Main roads and motorways", "A roads, dual carriageways, motorways", listOf("colorTrunks", "colorMotorways")),
        Part("buildings", "Buildings", "Mapbox's buildings (fronts and all)", listOf("colorBuildings")),
        Part("areas", "Shops, schools, hospitals, industry", "Coloured areas of the map", listOf("colorCommercial", "colorEducation", "colorMedical", "colorIndustrial")),
        Part("labels", "Labels", "Place and road names", listOf("colorPlaceLabels", "colorRoadLabels")),
    )

    data class Spec(
        /** Part key to "#RRGGBB"; a part not here keeps the theme's own colour. */
        val colours: Map<String, String> = emptyMap(),
        /** Night only: the night light's strength in percent of Mapbox's own (null: Mapbox's own light). */
        val bright: Int? = null,
        /** A colour wash over the whole map, and how strong (0 to 100). Null: none. */
        val tint: String? = null,
        val tintStr: Int = 30,
        /** The app draws the buildings, lit from within (hides Mapbox's own buildings and their detailed fronts). */
        val glow: Boolean = false,
        val glowStr: Int = 60,
        /** Soft light on the ground round each building. */
        val under: Boolean = false,
        val underCol: String? = null,
        val underStr: Int = 40,
    ) {
        val isEmpty: Boolean get() = this == Spec()

        fun toJson(): String = JSONObject().apply {
            put("colours", JSONObject(colours as Map<*, *>))
            bright?.let { put("bright", it) }
            tint?.let { put("tint", it) }
            put("tintStr", tintStr)
            put("glow", glow); put("glowStr", glowStr)
            put("under", under); underCol?.let { put("underCol", it) }; put("underStr", underStr)
        }.toString()
    }

    fun parse(json: String?): Spec {
        if (json.isNullOrBlank()) return Spec()
        return try {
            val o = JSONObject(json)
            val cols = HashMap<String, String>()
            o.optJSONObject("colours")?.let { c -> for (k in c.keys()) { val v = c.optString(k); if (validHex(v)) cols[k] = v } }
            Spec(
                colours = cols,
                bright = if (o.has("bright")) o.optInt("bright", 100).coerceIn(25, 200) else null,
                tint = o.optString("tint").takeIf { validHex(it) },
                tintStr = o.optInt("tintStr", 30).coerceIn(0, 100),
                glow = o.optBoolean("glow", false),
                glowStr = o.optInt("glowStr", 60).coerceIn(0, 100),
                under = o.optBoolean("under", false),
                underCol = o.optString("underCol").takeIf { validHex(it) },
                underStr = o.optInt("underStr", 40).coerceIn(0, 100),
            )
        } catch (e: Exception) { Spec() }
    }

    fun validHex(s: String?): Boolean = s != null && Regex("^#[0-9A-Fa-f]{6}$").matches(s)

    fun hex(c: Int): String = String.format("#%06X", c and 0xFFFFFF)

    // ---- the tint: a colour look-up table made on the device ----
    private var lutKey = ""
    private var lutData = ""

    /**
     * Mapbox's colour table (1024 x 32: blue in blocks of 32 across, red across each block, green down) with [tint] washed over it
     * at [str] percent, on top of the theme's own table [baseB64] (or no change, for Mapbox's own colours). Kept for next time.
     */
    fun lut(baseB64: String?, tint: String, str: Int): String? {
        val key = "${baseB64?.length}|${baseB64?.hashCode()}|$tint|$str"
        if (key == lutKey && lutData.isNotEmpty()) return lutData
        return try {
            val w = 1024; val h = 32
            val px = IntArray(w * h)
            val base = baseB64?.let { val b = android.util.Base64.decode(it, android.util.Base64.DEFAULT); BitmapFactory.decodeByteArray(b, 0, b.size) }
            if (base != null && base.width == w && base.height == h) base.getPixels(px, 0, w, 0, 0, w, h)
            else for (y in 0 until h) for (x in 0 until w) {
                val r = (x % 32) * 255 / 31; val g = y * 255 / 31; val bl = (x / 32) * 255 / 31
                px[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
            }
            val t = android.graphics.Color.parseColor(tint)
            val tr = ((t shr 16) and 0xFF) / 255f; val tg = ((t shr 8) and 0xFF) / 255f; val tb = (t and 0xFF) / 255f
            // the wash keeps each colour's lightness and pulls it towards the tint's colour
            val k = str.coerceIn(0, 100) / 100f * 0.7f
            val tl = (0.2126f * tr + 0.7152f * tg + 0.0722f * tb).coerceAtLeast(0.05f)
            for (i in px.indices) {
                val p = px[i]
                val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val nr = (l * tr / tl).coerceIn(0f, 255f); val ng = (l * tg / tl).coerceIn(0f, 255f); val nb = (l * tb / tl).coerceIn(0f, 255f)
                px[i] = (0xFF shl 24) or ((r + (nr - r) * k).toInt() shl 16) or ((g + (ng - g) * k).toInt() shl 8) or (b + (nb - b) * k).toInt()
            }
            val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            lutData = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
            lutKey = key
            lutData
        } catch (e: Throwable) { null }
    }
}
