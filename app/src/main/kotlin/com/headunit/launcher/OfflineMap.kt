package com.headunit.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The map drawn from files on the device, for when there is no signal. The pack holds the map tiles (map.pmtiles, built from OpenStreetMap)
 * and fonts for the labels. The style below is written here in code, so it needs nothing from the internet.
 */
object OfflineMap {
    fun file(ctx: Context) = File(OfflineRouter.dir(ctx), "map.pmtiles")
    fun fontsDir(ctx: Context) = File(OfflineRouter.dir(ctx), "fonts")
    fun installed(ctx: Context): Boolean = file(ctx).let { it.exists() && it.length() > 200_000 }
    fun hasFonts(ctx: Context): Boolean = File(fontsDir(ctx), "NotoSansRegular").isDirectory

    private fun arr(vararg v: Any?) = JSONArray().apply { v.forEach { put(it) } }
    private fun get(k: String) = arr("get", k)
    private fun cls(vararg names: String) = arr("match", get("class"), JSONArray().apply { names.forEach { put(it) } }, true, false)
    private fun stops(vararg zv: Pair<Int, Double>): JSONArray {
        val a = arr("interpolate", arr("exponential", 1.5), arr("zoom"))
        for ((z, v) in zv) { a.put(z); a.put(v) }
        return a
    }
    private fun scaled(k: Double, vararg zv: Pair<Int, Double>) = stops(*zv.map { it.first to it.second * k }.toTypedArray())

    private class Pal(
        val bg: String, val water: String, val park: String, val wood: String, val grass: String, val res: String, val building: String,
        val road: String, val roadCase: String, val major: String, val majorCase: String, val motor: String, val motorCase: String,
        val rail: String, val text: String, val halo: String, val sub: String,
    )

    private val day = Pal("#EDEAE3", "#AAD3E6", "#CFE6C0", "#C5DDB5", "#DCEACB", "#E7E3DB", "#D9D4C9",
        "#FFFFFF", "#CBC6BB", "#FFF1C4", "#DDB85E", "#F9C27A", "#D9923A", "#A9A9B0", "#3A3A3F", "#FFFFFF", "#6D6D75")
    private val night = Pal("#1D2027", "#10202F", "#203428", "#1C2E25", "#223729", "#23262E", "#2E323C",
        "#3B404C", "#14161B", "#4A5470", "#14161B", "#66779F", "#14161B", "#4A4F5C", "#D2D6E0", "#171A20", "#9AA1B3")

    private fun layer(id: String, type: String, src: String?, paint: JSONObject, filter: JSONArray? = null, layout: JSONObject? = null, minz: Int? = null, maxz: Int? = null): JSONObject {
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

    fun styleJson(ctx: Context, dark: Boolean): String {
        val p = if (dark) night else day
        val layers = JSONArray()
        layers.put(JSONObject().put("id", "background").put("type", "background").put("paint", JSONObject().put("background-color", p.bg)))
        layers.put(layer("landcover-wood", "fill", "landcover", JSONObject().put("fill-color", p.wood), cls("wood", "forest")))
        layers.put(layer("landcover-grass", "fill", "landcover", JSONObject().put("fill-color", p.grass), cls("grass", "farmland", "scrub", "heath")))
        layers.put(layer("landuse-res", "fill", "landuse", JSONObject().put("fill-color", p.res), cls("residential", "suburb", "neighbourhood", "commercial", "retail", "industrial")))
        layers.put(layer("landuse-green", "fill", "landuse", JSONObject().put("fill-color", p.park), cls("cemetery", "playground", "pitch", "stadium", "track", "garden", "allotments")))
        layers.put(layer("park", "fill", "park", JSONObject().put("fill-color", p.park)))
        layers.put(layer("water", "fill", "water", JSONObject().put("fill-color", p.water)))
        layers.put(layer("waterway", "line", "waterway", JSONObject().put("line-color", p.water).put("line-width", scaled(1.0, 10 to 0.5, 14 to 2.0, 18 to 6.0)), layout = round))
        layers.put(layer("aeroway", "fill", "aeroway", JSONObject().put("fill-color", p.roadCase), minz = 10))
        // railways sit under the roads
        layers.put(layer("road-rail", "line", "transportation", JSONObject().put("line-color", p.rail).put("line-width", scaled(1.0, 11 to 0.5, 15 to 1.5, 18 to 3.0)), cls("rail"), minz = 11))
        // roads: a darker edge for each class, then the fills, small roads first so the big ones end up on top
        data class R(val id: String, val classes: Array<String>, val fill: String, val edge: String, val k: Double, val z: Int)
        val roads = listOf(
            R("road-track", arrayOf("track", "path", "pedestrian"), p.road, p.roadCase, 0.5, 14),
            R("road-service", arrayOf("service"), p.road, p.roadCase, 0.7, 13),
            R("road-minor", arrayOf("minor"), p.road, p.roadCase, 1.0, 11),
            R("road-tertiary", arrayOf("secondary", "tertiary"), p.major, p.majorCase, 1.0, 9),
            R("road-primary", arrayOf("primary"), p.major, p.majorCase, 1.25, 7),
            R("road-trunk", arrayOf("trunk", "motorway"), p.motor, p.motorCase, 1.4, 5),
        )
        for (r in roads) {
            val w = scaled(r.k, 6 to 0.6, 10 to 1.2, 12 to 2.4, 14 to 4.5, 16 to 9.0, 18 to 18.0)
            layers.put(layer(r.id + "-casing", "line", "transportation",
                JSONObject().put("line-color", r.edge).put("line-width", scaled(r.k, 6 to 1.0, 10 to 2.0, 12 to 3.6, 14 to 6.0, 16 to 11.0, 18 to 21.0)),
                arr("all", cls(*r.classes), arr("!=", get("brunnel"), "tunnel")), round, r.z))
            layers.put(layer(r.id, "line", "transportation", JSONObject().put("line-color", r.fill).put("line-width", w),
                arr("all", cls(*r.classes), arr("!=", get("brunnel"), "tunnel")), round, r.z))
        }
        layers.put(layer("building", "fill", "building", JSONObject().put("fill-color", p.building), minz = 14, maxz = 16))
        val bc = if (dark) listOf("#3C4370","#464D7B","#525A8D","#5F68A0") else listOf("#DDD8CD","#D3CEC2","#C6C0B3","#B8B1A3")
        layers.put(layer("building-3d", "fill-extrusion", "building",
            JSONObject().put("fill-extrusion-color", arr("interpolate", arr("linear"), arr("coalesce", get("render_height"), 6), 0, bc[0], 20, bc[1], 60, bc[2], 150, bc[3])).put("fill-extrusion-opacity", 0.92).put("fill-extrusion-vertical-gradient", true)
                .put("fill-extrusion-height", arr("coalesce", get("render_height"), 6)).put("fill-extrusion-base", arr("coalesce", get("render_min_height"), 0)), minz = 16))
        // labels
        val font = JSONArray().put("NotoSansRegular")
        val bold = JSONArray().put("NotoSansBold")
        val halo = JSONObject().put("text-color", p.text).put("text-halo-color", p.halo).put("text-halo-width", 1.6)
        layers.put(layer("road-label", "symbol", "transportation_name", JSONObject(halo.toString()),
            layout = JSONObject().put("symbol-placement", "line").put("text-field", arr("coalesce", get("name"), get("ref"))).put("text-font", font)
                .put("text-size", stops(12 to 10.0, 16 to 13.0, 18 to 15.0)).put("text-max-angle", 30).put("symbol-spacing", 300), minz = 13))
        val place = { id: String, names: Array<String>, size: JSONArray, f: JSONArray, minz: Int?, maxz: Int? ->
            layers.put(layer(id, "symbol", "place", JSONObject(halo.toString()),
                cls(*names),
                JSONObject().put("text-field", get("name")).put("text-font", f).put("text-size", size).put("text-max-width", 8), minz, maxz))
        }
        place("place-label-city", arrayOf("city"), stops(5 to 13.0, 10 to 20.0), bold, 4, 13)
        place("place-label-town", arrayOf("town"), stops(7 to 12.0, 13 to 18.0), bold, 7, 15)
        place("place-label-village", arrayOf("village", "suburb"), stops(9 to 11.0, 15 to 16.0), font, 10, 17)
        place("place-label-small", arrayOf("hamlet", "neighbourhood", "quarter"), stops(12 to 10.0, 17 to 14.0), font, 13, 18)
        val d = OfflineRouter.dir(ctx)
        val src = JSONObject().put("type", "vector").put("url", "pmtiles://file://" + file(ctx).absolutePath).put("minzoom", 0).put("maxzoom", 14)
        return JSONObject().put("version", 8).put("name", "Offline")
            .put("glyphs", "file://" + File(d, "fonts").absolutePath + "/{fontstack}/{range}.pbf")
            .put("sources", JSONObject().put("omt", src)).put("layers", layers).toString()
    }

    /** Unpacks fonts.zip (downloaded with the pack) into the fonts folder [out]. */
    fun unpackFonts(zip: File, out: File) {
        out.mkdirs()
        java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val f = File(out, e.name)
                if (f.canonicalPath.startsWith(out.canonicalPath)) {
                    if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { o -> z.copyTo(o) } }
                }
                e = z.nextEntry
            }
        }
        zip.delete()
    }
}
