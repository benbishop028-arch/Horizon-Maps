package com.headunit.launcher

import com.mapbox.bindgen.Value
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.generated.ClipLayer
import com.mapbox.maps.extension.style.layers.generated.ModelLayer
import com.mapbox.maps.extension.style.layers.properties.generated.ModelType
import com.mapbox.maps.extension.style.precipitations.generated.removeRain
import com.mapbox.maps.extension.style.precipitations.generated.removeSnow
import com.mapbox.maps.extension.style.precipitations.generated.rain
import com.mapbox.maps.extension.style.precipitations.generated.setRain
import com.mapbox.maps.extension.style.precipitations.generated.setSnow
import com.mapbox.maps.extension.style.precipitations.generated.snow
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.RasterDemSource
import com.mapbox.maps.extension.style.terrain.generated.Terrain
import com.mapbox.maps.extension.style.atmosphere.generated.removeAtmosphere
import com.mapbox.maps.extension.style.atmosphere.generated.setAtmosphere
import com.mapbox.maps.extension.style.terrain.generated.removeTerrain
import com.mapbox.maps.extension.style.terrain.generated.setTerrain
import com.mapbox.maps.Style

/**
 * The extra 3D bits of Mapbox's map, kept apart from MapBinding so each can be switched off on its own (Settings > Map):
 * - real 3D street lamps, traffic lights and speed cameras (glTF models, real size, their lights glowing at night);
 * - 3D hills and valleys (terrain);
 * - real rain and snow falling on the map when it's raining or snowing where you are;
 * - "clear view": Mapbox's 3D trees and landmarks are kept out of a small area round your arrow and along the road just ahead.
 * Everything here is best-effort: a failure leaves the map as it was (logged), never a crash.
 */
internal object Furniture {
    const val LAMP = "dl-m-lamp"; const val LAMP_LENS = "dl-m-lamp-lens"
    const val SIGNAL = "dl-m-signal"; const val SIGNAL_LENS = "dl-m-signal-lens"
    const val CAMERA = "dl-m-camera"

    /** The models, added to the style once (they're files in the app). */
    fun addModels(st: Style) {
        for ((id, f) in listOf(LAMP to "lamp", LAMP_LENS to "lamp_lens", SIGNAL to "signal", SIGNAL_LENS to "signal_lens", CAMERA to "camera")) {
            try { if (!st.hasStyleModel(id)) st.addStyleModel(id, "asset://models/$f.glb") } catch (e: Throwable) { }
        }
    }

    /** A model layer: one model per point, turned by the feature's "r" ([0, 0, bearing]), real size in metres. */
    fun layer(id: String, src: String, model: String, glow: Double, minZoom: Double, size: Double): ModelLayer =
        ModelLayer(id, src).modelId(model).modelType(ModelType.COMMON_3D)
            .modelRotation(Expression.get("r")).modelScale(listOf(size, size, size))
            .modelCastShadows(false).modelReceiveShadows(false).modelEmissiveStrength(glow).minZoom(minZoom)

    /**
     * How many times real size a model is drawn at [zoom], blended smoothly between the (zoom, size) steps of [table]. The map is told
     * the new size as the zoom changes (a size written into the style as a zoom rule was only worked out per whole zoom level for
     * these models, so they jumped a size at a time).
     */
    fun sizeAt(table: Array<Pair<Double, Double>>, zoom: Double): Double {
        if (zoom <= table.first().first) return table.first().second
        if (zoom >= table.last().first) return table.last().second
        for (i in 1 until table.size) {
            val (z1, s1) = table[i]
            if (zoom <= z1) {
                val (z0, s0) = table[i - 1]
                // blended on a log scale, so the change feels even all the way through
                val t = (zoom - z0) / (z1 - z0)
                return Math.exp(Math.log(s0) + (Math.log(s1) - Math.log(s0)) * t)
            }
        }
        return table.last().second
    }

    /**
     * Traffic lights and speed cameras (about 3.5 m tall): kept big enough to see (about 30 to 60 dp tall) from zoom 14 to 18, so they
     * don't shrink to nothing as you zoom out; closer in than that they grow with the map, but only gently past zoom 19.
     */
    val SIGNAL_SIZE = arrayOf(14.0 to 24.0, 15.0 to 13.6, 16.0 to 7.7, 17.0 to 4.3, 18.0 to 3.2, 19.0 to 2.0, 20.0 to 1.4)
    /** Street lamps (about 7 m tall): the same idea, a little smaller as there are so many of them. */
    val LAMP_SIZE = arrayOf(15.0 to 5.5, 16.0 to 3.8, 17.0 to 2.6, 18.0 to 1.7, 19.0 to 1.2, 20.0 to 1.0)

    /** A point for a model, turned to [bearing] (degrees clockwise from north). */
    fun point(lat: Double, lon: Double, bearing: Double): Feature {
        val f = Feature.fromGeometry(Point.fromLngLat(lon, lat))
        val a = com.google.gson.JsonArray(); a.add(0.0); a.add(0.0); a.add(((bearing % 360) + 360) % 360)
        f.addProperty("r", a)
        return f
    }

    /** [p] moved [m] metres towards [bearing]. */
    fun offset(p: LL, bearing: Double, m: Double): LL {
        val b = Math.toRadians(bearing)
        return LL(p.lat + Math.cos(b) * m / 111320.0, p.lon + Math.sin(b) * m / (111320.0 * Math.cos(Math.toRadians(p.lat))))
    }
}

/** Precipitation and terrain need the map engine's newer ("experimental") style calls. */
@OptIn(MapboxExperimental::class)
internal object Sky {
    /** Rain or snow falling on the map for an Open-Meteo weather code (null or a dry code: none). Returns whether it worked. */
    fun weather(st: Style, code: Int?): Boolean = try {
        val c = code ?: -1
        val isSnow = c in 71..77 || c == 85 || c == 86
        val isRain = c in 51..67 || c in 80..82 || c in 95..99
        val heavy = c == 63 || c == 65 || c == 81 || c == 82 || c >= 95 || c == 75 || c == 86
        if (isRain) st.setRain(rain { density(if (heavy) 0.9 else 0.5); intensity(if (heavy) 1.0 else 0.6); opacity(if (heavy) 0.55 else 0.4); vignette(if (heavy) 0.4 else 0.2) })
        else try { st.removeRain() } catch (e: Throwable) { }
        if (isSnow) st.setSnow(snow { density(if (heavy) 0.9 else 0.55); intensity(if (heavy) 1.0 else 0.7); opacity(0.8); vignette(0.2) })
        else try { st.removeSnow() } catch (e: Throwable) { }
        true
    } catch (e: Throwable) { android.util.Log.w("MapExtras", "weather: ${e.message}"); false }

    /** 3D hills and valleys from Mapbox's elevation data (on), or flat (off). */
    fun terrain(st: Style, on: Boolean) {
        try {
            if (on) {
                if (!st.styleSourceExists("dl-dem")) st.addSource(RasterDemSource.Builder("dl-dem").url("mapbox://mapbox.mapbox-terrain-dem-v1").tileSize(514L).maxzoom(14L).build())
                st.setTerrain(Terrain("dl-dem").exaggeration(1.0))
            } else {
                try { st.removeTerrain() } catch (e: Throwable) { }
            }
        } catch (e: Throwable) { android.util.Log.w("MapExtras", "terrain: ${e.message}") }
    }

    fun clearTerrain(st: Style) { try { st.removeTerrain() } catch (e: Throwable) { } }

    /**
     * The haze towards the horizon to match the weather (Open-Meteo code): a light grey haze when it's overcast, thicker in rain or
     * snow, thick in fog. Clear weather (or null) leaves Mapbox's own sky, which already glows at dawn and dusk.
     */
    fun atmosphere(st: Style, code: Int?, night: Boolean) {
        try {
            val c = code ?: -1
            val kind = when (c) {
                45, 48 -> 3
                in 51..67, in 80..82, in 95..99, in 71..77, 85, 86 -> 2
                3 -> 1
                else -> 0
            }
            if (kind == 0) { try { st.removeAtmosphere() } catch (e: Throwable) { }; return }
            val col = if (night) "#2A2F3D" else when (kind) { 3 -> "#D3D6DB"; 2 -> "#C2C7CF"; else -> "#CDD3DB" }
            val high = if (night) "#1C2131" else when (kind) { 3 -> "#C9CDD3"; 2 -> "#A9B1BD"; else -> "#B9C4D2" }
            val rg = when (kind) { 3 -> listOf(0.2, 2.5); 2 -> listOf(0.6, 5.5); else -> listOf(1.0, 9.0) }
            st.setAtmosphere(com.mapbox.maps.extension.style.atmosphere.generated.atmosphere {
                color(col); highColor(high); horizonBlend(if (kind == 3) 0.3 else 0.15); range(rg)
                spaceColor(if (night) "#0B0E17" else high); starIntensity(if (night && kind < 2) 0.15 else 0.0)
            })
        } catch (e: Throwable) { android.util.Log.w("MapExtras", "sky: ${e.message}") }
    }
}

/** The "clear view" area: a circle round your arrow and, on a route, a narrow strip along the next ~180 m of it, as polygons. */
internal object ClearView {
    fun shapes(me: LL?, heading: Double, route: List<LL>): FeatureCollection {
        val feats = ArrayList<Feature>()
        val p = me ?: return FeatureCollection.fromFeatures(feats)
        val kx = 111320.0 * Math.cos(Math.toRadians(p.lat)); val ky = 111320.0
        // round the arrow (about 14 m)
        // (kept tight: Mapbox takes away the whole of any 3D model the area touches, and with a wider area big landmark buildings
        // beside the road vanished as you drove past, not just the trees over the road)
        val ring = (0..20).map { k -> val a = 2 * Math.PI * k / 20; Point.fromLngLat(p.lon + 14.0 * Math.cos(a) / kx, p.lat + 14.0 * Math.sin(a) / ky) }
        feats += Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
        // the road just ahead: along the route when there is one, else straight on in the direction you're heading
        val ahead = ArrayList<LL>()
        if (route.size >= 2) {
            var best = 0; var bd = Double.MAX_VALUE
            for (i in 0 until minOf(route.size, 600)) { val d = Geo.dist(route[i], p); if (d < bd) { bd = d; best = i } }
            if (bd < 60) {
                ahead += p; var run = 0.0; var i = best
                while (i + 1 < route.size && run < 180.0) { run += Geo.dist(route[i], route[i + 1]); ahead += route[i + 1]; i++ }
            }
        }
        // with no route there's only the circle: a straight line ahead used to cut through the buildings on a bend, and Mapbox
        // takes away the whole of any 3D model it touches, so whole landmark buildings went missing
        if (ahead.size < 2) return FeatureCollection.fromFeatures(feats)
        // the middle of the road only: 10 m across reached the building fronts in narrow streets (Paris), so it's now 5 m
        val half = 2.5
        for (i in 0 until ahead.size - 1) {
            val a = ahead[i]; val b = ahead[i + 1]
            val br = Geo.bearing(a, b)
            val l1 = Furniture.offset(a, br - 90, half); val r1 = Furniture.offset(a, br + 90, half)
            val l2 = Furniture.offset(b, br - 90, half); val r2 = Furniture.offset(b, br + 90, half)
            val poly = listOf(l1, l2, r2, r1, l1).map { Point.fromLngLat(it.lon, it.lat) }
            feats += Feature.fromGeometry(Polygon.fromLngLats(listOf(poly)))
        }
        return FeatureCollection.fromFeatures(feats)
    }

    fun layer(): ClipLayer = ClipLayer("dl-clip", "dl-clip").clipLayerTypes(listOf("model")).clipLayerScope(listOf("basemap"))
    fun source(): GeoJsonSource = GeoJsonSource.Builder("dl-clip").featureCollection(FeatureCollection.fromFeatures(ArrayList<Feature>())).build()
}
