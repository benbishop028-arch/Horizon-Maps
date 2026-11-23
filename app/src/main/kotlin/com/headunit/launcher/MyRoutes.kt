package com.headunit.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A route you saved after driving it (igalshe-ai #321): the way you really went from [from] to [to] ([toName]), with the time of
 * day it was driven ([minute], 0 to 1439) so a morning and an evening version can be told apart. [pts] are only read when needed.
 */
class MyRoute(val id: String, val name: String, val from: LL, val to: LL, val toName: String, val minute: Int, val len: Double, val at: Long,
    val pts: List<LL> = emptyList())

/**
 * Your own routes (Settings › Places & trips › My routes): saved from the arrival card, kept in files/myroutes, and offered in the
 * route choice next time you go from near the same start to the same place. Kept small, like GPX tracks: points under 15 m apart
 * are merged and a route is capped at 4000 points.
 */
object MyRoutes {
    var list by mutableStateOf<List<MyRoute>>(emptyList())
        private set
    @Volatile private var loaded = false

    private fun dir(c: Context) = File(c.filesDir, "myroutes").apply { mkdirs() }

    /** The list, read now if it hasn't been yet (any thread). */
    fun all(c: Context): List<MyRoute> {
        if (loaded) return list
        val l = readAll(c)
        loaded = true
        post { list = l }
        return l
    }

    /** Reads the list again (after a backup brought routes back). */
    fun reload(c: Context) { val l = readAll(c); loaded = true; post { list = l } }

    private fun post(f: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) f() else android.os.Handler(android.os.Looper.getMainLooper()).post(f)
    }

    private fun readAll(c: Context): List<MyRoute> = try {
        (dir(c).listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()).mapNotNull { f ->
            try { fromJson(f.nameWithoutExtension, JSONObject(f.readText()), withPts = false) } catch (e: Exception) { null }
        }.sortedByDescending { it.at }
    } catch (e: Exception) { emptyList() }

    private fun fromJson(id: String, o: JSONObject, withPts: Boolean): MyRoute {
        val pts = ArrayList<LL>()
        if (withPts) {
            val a = o.getJSONArray("pts")
            var i = 0
            while (i + 1 < a.length()) { pts += LL(a.getDouble(i), a.getDouble(i + 1)); i += 2 }
        }
        return MyRoute(id, o.optString("name"), LL(o.getDouble("fla"), o.getDouble("flo")), LL(o.getDouble("tla"), o.getDouble("tlo")),
            o.optString("toName"), o.optInt("min", 0), o.optDouble("len", 0.0), o.optLong("at", 0L), pts)
    }

    /** The route with its points, or null. (Off the main thread.) */
    fun read(c: Context, id: String): MyRoute? = try { fromJson(id, JSONObject(File(dir(c), "$id.json").readText()), withPts = true) } catch (e: Exception) { null }

    /** Saves a drive ([raw]: where you went, in order). Returns the saved route, or null if it was too short or couldn't be written. */
    fun save(c: Context, name: String, toName: String, raw: List<LL>, minute: Int): MyRoute? {
        val thin = ArrayList<LL>()
        for (p in raw) { val last = thin.lastOrNull(); if (last == null || Geo.dist(last, p) >= 15.0) thin += p }
        if (raw.isNotEmpty() && thin.last() !== raw.last()) thin += raw.last()
        if (thin.size < 3) return null
        val pts = if (thin.size <= 4000) thin else { val step = thin.size / 4000.0; (0 until 4000).map { thin[(it * step).toInt()] } + thin.last() }
        var len = 0.0
        for (i in 1 until pts.size) len += Geo.dist(pts[i - 1], pts[i])
        if (len < 300.0) return null
        val id = System.currentTimeMillis().toString()
        val arr = JSONArray()
        for (p in pts) { arr.put(Math.round(p.lat * 1e6) / 1e6); arr.put(Math.round(p.lon * 1e6) / 1e6) }
        val now = System.currentTimeMillis()
        val title = name.trim().take(60).ifBlank { toName.ifBlank { "My route" } }
        val o = JSONObject().put("name", title).put("toName", toName).put("min", minute).put("len", len).put("at", now)
            .put("fla", pts.first().lat).put("flo", pts.first().lon).put("tla", pts.last().lat).put("tlo", pts.last().lon).put("pts", arr)
        return try {
            File(dir(c), "$id.json").writeText(o.toString())
            val r = MyRoute(id, title, pts.first(), pts.last(), toName, minute, len, now)
            all(c)
            post { list = listOf(r) + list.filter { it.id != id } }
            r
        } catch (e: Exception) { null }
    }

    fun rename(c: Context, id: String, name: String) {
        val n = name.trim().take(60)
        if (n.isBlank()) return
        try {
            val f = File(dir(c), "$id.json")
            f.writeText(JSONObject(f.readText()).put("name", n).toString())
        } catch (e: Exception) { return }
        post { list = list.map { if (it.id == id) MyRoute(it.id, n, it.from, it.to, it.toName, it.minute, it.len, it.at) else it } }
    }

    fun delete(c: Context, id: String) {
        try { File(dir(c), "$id.json").delete() } catch (e: Exception) { }
        post { list = list.filter { it.id != id } }
    }

    /**
     * Your routes that fit a trip from [from] to [to]: starting within 500 m of where you are and ending within 300 m of where you're
     * going, the one driven nearest this time of day first (so a morning and an evening version each come up at their own time).
     */
    fun matching(c: Context, from: LL, to: LL, max: Int = 2): List<MyRoute> {
        val cal = java.util.Calendar.getInstance()
        val now = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        fun gap(m: Int): Int { val d = kotlin.math.abs(m - now) % 1440; return minOf(d, 1440 - d) }
        return all(c).filter { Geo.dist(it.from, from) < 500.0 && Geo.dist(it.to, to) < 300.0 }.sortedBy { gap(it.minute) }.take(max)
    }

    /**
     * Points along [pts] for a route to go through, so it follows the roads you took: about every 400 m (further apart on a long
     * route, at most [max]), leaving out any within 250 m of the start or end (a car park beside the road shouldn't pull the route in).
     */
    fun viaPoints(pts: List<LL>, max: Int = 20): List<LL> {
        if (pts.size < 3) return emptyList()
        var len = 0.0
        for (i in 1 until pts.size) len += Geo.dist(pts[i - 1], pts[i])
        val gap = maxOf(400.0, len / (max + 1))
        val out = ArrayList<LL>()
        var acc = 0.0
        for (i in 1 until pts.size - 1) {
            acc += Geo.dist(pts[i - 1], pts[i])
            if (acc >= gap) {
                acc = 0.0
                if (Geo.dist(pts[i], pts.first()) > 250.0 && Geo.dist(pts[i], pts.last()) > 250.0) out += pts[i]
                if (out.size >= max) break
            }
        }
        return out
    }
}
