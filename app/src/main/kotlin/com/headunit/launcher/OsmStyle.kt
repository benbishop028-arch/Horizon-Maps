package com.headunit.launcher

import org.json.JSONArray
import org.json.JSONObject

/**
 * The OpenStreetMap map (free OpenFreeMap tiles), drawn with a style written here so it looks like the TomTom map: the same land,
 * park, water, road and building colours (sampled from the TomTom map as the app shows it), the same soft lighting on 3D buildings,
 * 3D buildings by night as well as by day, and grey upper-case place names. Switching between the two (or having no TomTom key)
 * is then hardly noticeable.
 */
object OsmStyle {
    const val HOST = "tiles.openfreemap.org"

    private fun arr(vararg v: Any?) = JSONArray().apply { v.forEach { put(it) } }
    private fun get(k: String) = arr("get", k)
    private fun cls(vararg names: String) = arr("match", get("class"), JSONArray().apply { names.forEach { put(it) } }, true, false)
    private fun notTunnel() = arr("!=", get("brunnel"), "tunnel")
    private fun stops(vararg zv: Pair<Number, Number>): JSONArray {
        val a = arr("interpolate", arr("exponential", 1.5), arr("zoom"))
        for ((z, v) in zv) { a.put(z); a.put(v) }
        return a
    }
    private fun lin(vararg zv: Pair<Number, Number>): JSONArray {
        val a = arr("interpolate", arr("linear"), arr("zoom"))
        for ((z, v) in zv) { a.put(z); a.put(v) }
        return a
    }
    private fun scaled(k: Double, vararg zv: Pair<Number, Double>) = stops(*zv.map { it.first to it.second * k }.toTypedArray())

    private class Pal(
        val land: String, val res: String, val industrial: String, val park: String, val wood: String, val grass: String,
        val water: String, val waterText: String, val bld: String, val ext: Array<String>,
        val road: String, val roadCase: String, val major: String, val majorCase: String, val motor: String, val motorCase: String,
        val path: String, val rail: String, val text: String, val sub: String, val halo: String, val num: String,
    )

    // sampled from the TomTom map with the app's Modern colours, day and night
    private val day = Pal(
        land = "#F0F1F0", res = "#ECEBE8", industrial = "#E8E7E3", park = "#C2E1BC", wood = "#ABDBA4", grass = "#C8E6C2",
        water = "#ABDAF8", waterText = "#5B8FB5", bld = "#E7E6E2", ext = arrayOf("#F1EFEB", "#E9E7E3", "#E0DED9", "#D6D4CF"),
        road = "#FFFFFF", roadCase = "#DCDAD5", major = "#FFFFFF", majorCase = "#CFCCC6", motor = "#FFFFFF", motorCase = "#C6C3BC",
        path = "#E2E1DE", rail = "#D2D0CB", text = "#5F6368", sub = "#7A7F87", halo = "#FFFFFF", num = "#9A9DA3",
    )
    private val night = Pal(
        land = "#1B1F22", res = "#1D2126", industrial = "#1E2228", park = "#213B38", wood = "#1F3833", grass = "#22393A",
        water = "#1E3A52", waterText = "#7FA6C4", bld = "#3A4170", ext = arrayOf("#3C4370", "#454C7E", "#51598F", "#5F68A0"),
        road = "#464E7C", roadCase = "#262B4B", major = "#4C5585", majorCase = "#262B4B", motor = "#5E67A0", motorCase = "#2B3052",
        path = "#343A5E", rail = "#3A4066", text = "#C9CEE8", sub = "#A3A9C6", halo = "#1B1F22", num = "#8F96BC",
    )

    private fun layer(id: String, type: String, src: String?, paint: JSONObject, filter: JSONArray? = null, layout: JSONObject? = null, minz: Number? = null, maxz: Number? = null): JSONObject {
        val o = JSONObject().put("id", id).put("type", type)
        if (src != null) o.put("source", "omt").put("source-layer", src)
        if (filter != null) o.put("filter", filter)
        if (minz != null) o.put("minzoom", minz)
        if (maxz != null) o.put("maxzoom", maxz)
        if (layout != null) o.put("layout", layout)
        o.put("paint", paint)
        return o
    }

    private val round get() = JSONObject().put("line-cap", "round").put("line-join", "round")

    fun json(dark: Boolean): String {
        val p = if (dark) night else day
        val L = JSONArray()
        L.put(JSONObject().put("id", "background").put("type", "background").put("paint", JSONObject().put("background-color", p.land)))
        // land use: built-up areas a touch off the land colour, then the greens (parks, woods, grass, pitches...)
        L.put(layer("landuse-residential", "fill", "landuse", JSONObject().put("fill-color", p.res), cls("residential", "suburb", "neighbourhood", "quarter")))
        L.put(layer("landuse-industrial", "fill", "landuse", JSONObject().put("fill-color", p.industrial), cls("commercial", "retail", "industrial", "railway", "garages")))
        L.put(layer("landcover-grass", "fill", "landcover", JSONObject().put("fill-color", p.grass), cls("grass", "farmland", "scrub", "heath", "wetland")))
        L.put(layer("landcover-wood", "fill", "landcover", JSONObject().put("fill-color", p.wood), cls("wood", "forest")))
        L.put(layer("landuse-green", "fill", "landuse", JSONObject().put("fill-color", p.park),
            cls("cemetery", "playground", "pitch", "stadium", "track", "garden", "allotments", "park", "recreation_ground", "village_green", "golf_course")))
        L.put(layer("park", "fill", "park", JSONObject().put("fill-color", p.park)))
        // water
        L.put(layer("water", "fill", "water", JSONObject().put("fill-color", p.water).put("fill-antialias", true), arr("!=", get("brunnel"), "tunnel")))
        L.put(layer("waterway", "line", "waterway", JSONObject().put("line-color", p.water).put("line-width", stops(10 to 0.6, 14 to 2.0, 18 to 6.0)),
            arr("!=", get("brunnel"), "tunnel"), round, 10))
        L.put(layer("aeroway-area", "fill", "aeroway", JSONObject().put("fill-color", p.industrial), arr("==", arr("geometry-type"), "Polygon"), minz = 11))
        L.put(layer("aeroway-runway", "line", "aeroway", JSONObject().put("line-color", p.path).put("line-width", stops(11 to 3.0, 17 to 50.0)), cls("runway", "taxiway"), round, 11))
        // railways under the roads
        L.put(layer("rail", "line", "transportation", JSONObject().put("line-color", p.rail).put("line-width", stops(11 to 0.6, 15 to 1.6, 18 to 3.0)),
            arr("all", cls("rail", "transit"), notTunnel()), minz = 11))
        // roads: an edge for each class, then the fills, small roads first so the big ones end up on top
        data class R(val id: String, val classes: Array<String>, val fill: String, val edge: String, val k: Double, val z: Int)
        val roads = listOf(
            R("road-path", arrayOf("path", "track", "pedestrian"), p.path, p.path, 0.45, 15),
            R("road-service", arrayOf("service"), p.road, p.roadCase, 0.65, 14),
            R("road-minor", arrayOf("minor"), p.road, p.roadCase, 1.0, 12),
            R("road-secondary", arrayOf("secondary", "tertiary"), p.major, p.majorCase, 1.15, 9),
            R("road-primary", arrayOf("primary"), p.major, p.majorCase, 1.3, 7),
            R("road-trunk", arrayOf("trunk"), p.motor, p.motorCase, 1.4, 5),
            R("road-motorway", arrayOf("motorway"), p.motor, p.motorCase, 1.5, 5),
        )
        // tunnels drawn faintly underneath everything else
        L.put(layer("road-tunnel", "line", "transportation",
            JSONObject().put("line-color", p.roadCase).put("line-opacity", 0.6).put("line-width", stops(10 to 1.0, 14 to 4.0, 18 to 16.0)),
            arr("all", arr("==", get("brunnel"), "tunnel"), cls("motorway", "trunk", "primary", "secondary", "tertiary", "minor")), round, 12))
        for (r in roads) {
            val path = r.id == "road-path"
            val filt = arr("all", cls(*r.classes), notTunnel())
            if (!path) L.put(layer(r.id + "-casing", "line", "transportation",
                JSONObject().put("line-color", r.edge).put("line-width", scaled(r.k, 6 to 1.0, 10 to 2.0, 12 to 3.4, 14 to 6.0, 16 to 11.5, 18 to 22.0, 20 to 50.0)),
                filt, round, r.z))
            L.put(layer(r.id, "line", "transportation",
                JSONObject().put("line-color", r.fill).put("line-width", scaled(r.k, 6 to 0.6, 10 to 1.2, 12 to 2.4, 14 to 4.6, 16 to 9.5, 18 to 19.0, 20 to 45.0)).apply { if (path) put("line-dasharray", arr(1.5, 1.2)) },
                filt, if (path) null else round, r.z))
        }
        // buildings: flat footprints (kept for when 3D buildings are turned off), solid 3D blocks from close in, day and night
        L.put(layer("building", "fill", "building", JSONObject().put("fill-color", p.bld).put("fill-opacity", lin(13 to 0.0, 14 to 1.0)), minz = 13))
        val h = arr("coalesce", get("render_height"), 6)
        L.put(layer("building-3d", "fill-extrusion", "building",
            JSONObject().put("fill-extrusion-color", arr("interpolate", arr("linear"), h, 0, p.ext[0], 20, p.ext[1], 60, p.ext[2], 150, p.ext[3]))
                .put("fill-extrusion-opacity", if (dark) 0.9 else 0.96).put("fill-extrusion-vertical-gradient", true)
                .put("fill-extrusion-height", arr("interpolate", arr("linear"), arr("zoom"), 14.5, 0, 15.5, h))
                .put("fill-extrusion-base", arr("coalesce", get("render_min_height"), 0)), minz = 14.5))
        // labels
        val reg = arr("Noto Sans Regular")
        val bold = arr("Noto Sans Bold")
        val ital = arr("Noto Sans Italic")
        val name = arr("coalesce", get("name:en"), get("name:latin"), get("name"))
        fun halo(col: String, w: Double = 1.6) = JSONObject().put("text-color", col).put("text-halo-color", p.halo).put("text-halo-width", w)
        L.put(layer("water-name-line", "symbol", "waterway", halo(p.waterText, 1.2),
            arr("has", "name"),
            JSONObject().put("symbol-placement", "line").put("text-field", name).put("text-font", ital).put("text-size", lin(13 to 11, 18 to 15))
                .put("text-letter-spacing", 0.15).put("symbol-spacing", 400).put("text-max-angle", 30), 13))
        L.put(layer("water-name", "symbol", "water_name", halo(p.waterText, 1.2),
            arr("has", "name"),
            JSONObject().put("text-field", name).put("text-font", ital).put("text-size", lin(10 to 12, 16 to 17)).put("text-letter-spacing", 0.15).put("text-max-width", 6), 10))
        L.put(layer("road-label", "symbol", "transportation_name", halo(p.text),
            arr("has", "name"),
            JSONObject().put("symbol-placement", "line").put("text-field", name).put("text-font", reg)
                .put("text-size", lin(14 to 11, 17 to 13.5, 19 to 16)).put("text-max-angle", 30).put("symbol-spacing", 320).put("text-padding", 6)
                .put("text-pitch-alignment", "viewport").put("text-rotation-alignment", "map"), 14.5))
        L.put(layer("housenumber", "symbol", "housenumber", halo(p.num, 1.2),
            layout = JSONObject().put("text-field", get("housenumber")).put("text-font", reg).put("text-size", 11).put("text-padding", 4), minz = 17))
        // points of interest (shops, cafés, stations...) with the OpenFreeMap icons, only close in
        val icon = arr("match", get("subclass"), arr("florist", "furniture"), get("subclass"), get("class"))
        fun poi(id: String, lo: Int, hi: Int?, z: Int) {
            val f = arr("all", arr("match", arr("geometry-type"), arr("MultiPoint", "Point"), true, false), arr(">=", get("rank"), lo)).apply { if (hi != null) put(arr("<", get("rank"), hi)) }
            L.put(layer(id, "symbol", "poi", halo(p.sub, 1.4), f,
                JSONObject().put("icon-image", icon).put("text-field", name).put("text-font", reg).put("text-size", 11).put("text-max-width", 8)
                    .put("text-anchor", "top").put("text-offset", arr(0, 0.7)).put("text-optional", true), z))
        }
        poi("poi-r1", 1, 7, 16)
        poi("poi-r7", 7, 20, 17)
        // places: grey upper case with a little spacing, like the TomTom map
        fun place(id: String, names: Array<String>, size: JSONArray, f: JSONArray, minz: Int, maxz: Int, col: String, caps: Boolean) {
            L.put(layer(id, "symbol", "place", halo(col, 1.8), cls(*names),
                JSONObject().put("text-field", name).put("text-font", f).put("text-size", size).put("text-max-width", 8)
                    .put("text-transform", if (caps) "uppercase" else "none").put("text-letter-spacing", if (caps) 0.08 else 0.0), minz, maxz))
        }
        place("place-city", arrayOf("city"), lin(5 to 13, 10 to 20), bold, 4, 13, p.text, false)
        place("place-town", arrayOf("town"), lin(7 to 12, 13 to 18), bold, 7, 15, p.text, false)
        place("place-village", arrayOf("village", "suburb"), lin(10 to 11, 15 to 16), reg, 10, 17, p.text, true)
        place("place-small", arrayOf("hamlet", "neighbourhood", "quarter"), lin(12 to 10, 17 to 14), reg, 12, 18, p.sub, true)
        val src = JSONObject().put("type", "vector").put("url", "https://$HOST/planet")
        return JSONObject().put("version", 8).put("name", if (dark) "Horizon OSM dark" else "Horizon OSM day")
            .put("sprite", "https://$HOST/sprites/ofm_f384/ofm")
            .put("glyphs", "https://$HOST/fonts/{fontstack}/{range}.pbf")
            // the same soft, even lighting as the TomTom map gets
            .put("light", JSONObject().put("anchor", "viewport").put("position", arr(1.25, 215, 48)).put("intensity", if (dark) 0.36 else 0.3).put("color", "#FFFFFF"))
            .put("sources", JSONObject().put("omt", src)).put("layers", L).toString()
    }
}
