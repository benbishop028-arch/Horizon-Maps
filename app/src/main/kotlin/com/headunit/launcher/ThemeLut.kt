package com.headunit.launcher

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * The extra map colour themes (Settings > Map look > Colours): each is a colour look-up table for Mapbox's map, made on the device
 * from a simple recipe and kept for next time. Like Horizon's, the table recolours the whole of Mapbox's map (day and night), while
 * the app's own route line, warnings and car keep their colours.
 *
 * Mapbox's table is 1024 x 32: blue in blocks of 32 across, red across each block, green down.
 */
object ThemeLut {
    val ids = setOf("ember", "mono", "forest", "arctic", "contrast", "sepia", "neon", "pastel", "car")

    private val cache = HashMap<String, String>()

    /** The table for theme [id] ([carTint] "#RRGGBB" and [carStr] 0 to 100 for "car"), as base64 PNG; null if it couldn't be made. */
    fun lut(id: String, carTint: String = "#8A5AD0", carStr: Int = 40): String? {
        val key = if (id == "car") "car|$carTint|$carStr" else id
        cache[key]?.let { return it }
        return try {
            val w = 1024; val h = 32
            val px = IntArray(w * h)
            val tint = if (id == "car") rgb(carTint) else null
            val k = carStr.coerceIn(0, 100) / 100f
            val out = FloatArray(3)
            for (y in 0 until h) for (x in 0 until w) {
                val r = (x % 32) / 31f; val g = y / 31f; val b = (x / 32) / 31f
                recipe(id, r, g, b, tint, k, out)
                px[y * w + x] = (0xFF shl 24) or (to255(out[0]) shl 16) or (to255(out[1]) shl 8) or to255(out[2])
            }
            val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
            val bo = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bo)
            val s = android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP)
            if (id == "car") cache.keys.removeAll { it.startsWith("car|") }
            cache[key] = s
            s
        } catch (e: Throwable) { null }
    }

    private fun to255(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    private fun rgb(hex: String): FloatArray = try {
        val c = android.graphics.Color.parseColor(hex)
        floatArrayOf(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
    } catch (e: Exception) { floatArrayOf(0.54f, 0.35f, 0.82f) }

    private fun lum(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b
    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** Pulls a colour towards [t]'s hue at the colour's own lightness, by [k]. */
    private fun wash(r: Float, g: Float, b: Float, t: FloatArray, k: Float, out: FloatArray) {
        val l = lum(r, g, b); val tl = lum(t[0], t[1], t[2]).coerceAtLeast(0.05f)
        out[0] = mix(r, l * t[0] / tl, k); out[1] = mix(g, l * t[1] / tl, k); out[2] = mix(b, l * t[2] / tl, k)
    }

    private val EMBER = floatArrayOf(1.0f, 0.74f, 0.48f)
    private val FOREST = floatArrayOf(0.72f, 0.92f, 0.62f)
    private val ARCTIC = floatArrayOf(0.78f, 0.9f, 1.0f)
    private val SEPIA = floatArrayOf(1.0f, 0.86f, 0.64f)
    private val NEON_DARK = floatArrayOf(0.55f, 0.3f, 1.0f)

    private fun recipe(id: String, r: Float, g: Float, b: Float, tint: FloatArray?, k: Float, o: FloatArray) {
        val l = lum(r, g, b)
        val dark = (1f - l) * (1f - l)
        when (id) {
            // warm and dark at night: amber-tinted, strongest in the dark colours
            "ember" -> wash(r, g, b, EMBER, 0.25f + 0.45f * dark, o)
            // greys only (the route, warnings and your car keep their colour)
            "mono" -> { o[0] = l; o[1] = l; o[2] = l }
            // greens and earthy browns
            "forest" -> wash(r, g, b, FOREST, 0.2f + 0.35f * dark, o)
            // cold blue-white by day, steel blue at night
            "arctic" -> wash(r, g, b, ARCTIC, 0.35f + 0.3f * dark, o)
            // stronger contrast, less colour: dark darker, light lighter
            "contrast" -> {
                val s = 0.7f
                for ((i, c) in floatArrayOf(r, g, b).withIndex()) {
                    val d = mix(c, l, 1f - s)
                    o[i] = ((d - 0.5f) * 1.55f + 0.5f)
                }
            }
            // an old paper map
            "sepia" -> wash(r, g, b, SEPIA, 0.7f, o)
            // more vivid colours, dark parts pulled towards violet
            "neon" -> {
                for ((i, c) in floatArrayOf(r, g, b).withIndex()) o[i] = l + (c - l) * 1.8f
                val t = 0.35f * dark
                o[0] = mix(o[0], l * NEON_DARK[0] * 1.6f, t); o[1] = mix(o[1], l * NEON_DARK[1] * 1.6f, t); o[2] = mix(o[2], l * NEON_DARK[2] * 1.6f, t)
            }
            // soft, light, low colour
            "pastel" -> for ((i, c) in floatArrayOf(r, g, b).withIndex()) o[i] = mix(mix(c, l, 0.45f), 1f, 0.12f)
            // a hint of the car's interior lighting colour, strongest in the dark colours (night)
            "car" -> wash(r, g, b, tint ?: NEON_DARK, k * (0.25f + 0.6f * dark), o)
            else -> { o[0] = r; o[1] = g; o[2] = b }
        }
    }
}

/** The Light and weather settings, for the map. */
fun lightOptsFrom(prefs: Prefs) = MapBinding.LightOpts(
    nightBright = prefs.i(PreferenceKeys.NIGHT_BRIGHT, 100).coerceIn(30, 200),
    moon = prefs.b(PreferenceKeys.MOONLIT, false),
    moonStr = prefs.i(PreferenceKeys.MOON_STR, 100).coerceIn(0, 200),
    shadows = prefs.b(PreferenceKeys.SHADOWS, true),
    sunReal = prefs.b(PreferenceKeys.SUN_REAL, false),
    overcast = prefs.b(PreferenceKeys.OVERCAST, false),
    weatherLight = prefs.b(PreferenceKeys.WEATHER_LIGHT, false),
)
