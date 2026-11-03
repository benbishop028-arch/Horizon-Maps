package com.headunit.launcher

import org.json.JSONObject
import java.util.Locale

/**
 * The vehicle routes are planned for (Settings > Routes > Vehicle): a car, or a lorry with its size and weight, so routes
 * keep off low bridges, weight limits and lorry bans. Read from the settings before every route ([load]).
 *
 * TomTom takes every measurement (lorry mode), the offline router (Valhalla's truck mode) takes every measurement too,
 * Mapbox only height, width and weight. A lorry never falls back to a car route: no lorry route means "No route found".
 */
object Vehicle {
    @Volatile var lorry = false
    @Volatile var heightCm = 400
    @Volatile var widthCm = 255
    @Volatile var lengthCm = 1650
    /** Weights in tonnes x 10 (440 = 44 t). */
    @Volatile var weightT10 = 440
    @Volatile var axleT10 = 115
    @Volatile var hazmat = false

    fun load(prefs: Prefs) {
        lorry = prefs.s(PreferenceKeys.VEHICLE, "car") == "lorry"
        heightCm = prefs.i(PreferenceKeys.LORRY_HEIGHT, 400).coerceIn(150, 600)
        widthCm = prefs.i(PreferenceKeys.LORRY_WIDTH, 255).coerceIn(150, 400)
        lengthCm = prefs.i(PreferenceKeys.LORRY_LENGTH, 1650).coerceIn(400, 3000)
        weightT10 = prefs.i(PreferenceKeys.LORRY_WEIGHT, 440).coerceIn(35, 1000)
        axleT10 = prefs.i(PreferenceKeys.LORRY_AXLE, 115).coerceIn(10, 300)
        hazmat = prefs.b(PreferenceKeys.LORRY_HAZMAT, false)
    }

    private fun f(v: Double) = String.format(Locale.US, "%.2f", v)

    /** TomTom's lorry mode (empty for a car). Weights in kilograms, sizes in metres. */
    fun tomtom(): String {
        if (!lorry) return ""
        val sb = StringBuilder("&travelMode=truck&vehicleCommercial=true")
        sb.append("&vehicleHeight=").append(f(heightCm / 100.0))
        sb.append("&vehicleWidth=").append(f(widthCm / 100.0))
        sb.append("&vehicleLength=").append(f(lengthCm / 100.0))
        sb.append("&vehicleWeight=").append(weightT10 * 100)
        sb.append("&vehicleAxleWeight=").append(axleT10 * 100)
        if (hazmat) sb.append("&vehicleLoadType=otherHazmatGeneral")
        return sb.toString()
    }

    /** Mapbox's limits (empty for a car): it can only take height, width (both up to 10 m) and weight (up to 100 t). */
    fun mapbox(): String {
        if (!lorry) return ""
        return "&max_height=" + f((heightCm / 100.0).coerceAtMost(10.0)) +
            "&max_width=" + f((widthCm / 100.0).coerceAtMost(10.0)) +
            "&max_weight=" + f((weightT10 / 10.0).coerceAtMost(100.0))
    }

    /** The offline router's way of travelling: "truck" with the lorry's measurements, or "auto" for a car. */
    fun valhalla(req: JSONObject) {
        if (!lorry) { req.put("costing", "auto"); return }
        req.put("costing", "truck")
        req.put("costing_options", JSONObject().put("truck", JSONObject()
            .put("height", heightCm / 100.0)
            .put("width", widthCm / 100.0)
            .put("length", lengthCm / 100.0)
            .put("weight", weightT10 / 10.0)
            .put("axle_load", axleT10 / 10.0)
            .put("hazmat", hazmat)))
    }

    /** A height in metres, with feet and inches too when [imperial] ("4.20 m · 13′ 9″"). */
    fun heightText(cm: Int, imperial: Boolean): String {
        val m = String.format(Locale.US, "%.2f m", cm / 100.0)
        if (!imperial) return m
        val inches = Math.round(cm / 2.54).toInt()
        return "$m · ${inches / 12}′ ${inches % 12}″"
    }
}
