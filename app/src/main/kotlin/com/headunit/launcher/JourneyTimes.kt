package com.headunit.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Better arrival times with no live traffic (the free version, and any route planned from the downloaded pack), worked out on the
 * phone, with nothing sent anywhere:
 *  - usual traffic: the route's own time (roads at their speed) plus the hold-ups that are normal at that hour, more in the
 *    weekday rush hours and on town roads than on motorways, and
 *  - your own journeys: how long trips from the same place to the same place, at the same sort of time, really took you. Once
 *    there are two or more of them, they're used instead of the general estimate.
 */
object JourneyTimes {
    private const val FILE = "journeys"
    private const val KEY = "trips"
    private const val MAX_TRIPS = 300

    private class Trip(val fLat: Double, val fLon: Double, val tLat: Double, val tLon: Double, val weekend: Boolean, val minute: Int, val ratio: Double)

    @Volatile private var cache: MutableList<Trip>? = null

    /** How much longer than [secs] (roads at their own speed) a trip of average speed [avg] m/s usually takes, leaving at [atMs]. */
    fun usualFactor(avg: Double, atMs: Long): Double {
        val c = Calendar.getInstance().apply { timeInMillis = atMs }
        val dow = c.get(Calendar.DAY_OF_WEEK)
        val weekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        // (town roads, average under ~45 km/h: they slow down most; motorways least)
        val town = avg < 12.5
        val fast = avg > 19.5
        val rush = !weekend && (m in 435..570 || m in 960..1110)          // 07:15-09:30, 16:00-18:30
        val busy = if (weekend) m in 600..1080 else m in 571..959         // daytime
        val evening = m in 1111..1260                                      // 18:30-21:00
        val extra = when {
            rush -> if (town) 0.28 else if (fast) 0.14 else 0.20
            busy -> if (town) 0.12 else if (fast) 0.05 else 0.08
            evening -> if (town) 0.06 else 0.03
            else -> 0.0
        }
        // junctions, lights and corners take a little time even on an empty road
        val base = if (town) 0.06 else if (fast) 0.02 else 0.04
        return 1.0 + base + extra
    }

    /** Your own trips like this one (starting within 400 m, ending within 250 m, the same kind of day, within 90 minutes of the time). */
    fun learnedFactor(ctx: Context, from: LL, to: LL, atMs: Long): Double? {
        val c = Calendar.getInstance().apply { timeInMillis = atMs }
        val dow = c.get(Calendar.DAY_OF_WEEK)
        val weekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val all = trips(ctx)
        val like = synchronized(all) { all.filter { t ->
            t.weekend == weekend && minOf(Math.abs(t.minute - m), 1440 - Math.abs(t.minute - m)) <= 90 &&
                Geo.dist(LL(t.fLat, t.fLon), from) < 400.0 && Geo.dist(LL(t.tLat, t.tLon), to) < 250.0
        } }.takeLast(12)
        if (like.size < 2) return null
        val r = like.map { it.ratio }.sorted()
        // the middle one, so one unusual day (a crash, a stop for fuel) doesn't count for much
        return r[r.size / 2].coerceIn(0.8, 2.5)
    }

    /** A trip you finished: [baseSecs] the route's own time when you set off, [actualSecs] how long it really took. */
    fun record(ctx: Context, from: LL, to: LL, startMs: Long, baseSecs: Double, actualSecs: Double) {
        if (baseSecs < 240.0 || actualSecs < 240.0) return
        val ratio = actualSecs / baseSecs
        // (stops on the way, a detour or a wrong turn: not a fair time for the trip)
        if (ratio !in 0.7..2.5) return
        val c = Calendar.getInstance().apply { timeInMillis = startMs }
        val dow = c.get(Calendar.DAY_OF_WEEK)
        val t = Trip(from.lat, from.lon, to.lat, to.lon, dow == Calendar.SATURDAY || dow == Calendar.SUNDAY,
            c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE), ratio)
        val all = trips(ctx)
        synchronized(all) {
            all += t
            while (all.size > MAX_TRIPS) all.removeAt(0)
            save(ctx, all)
        }
    }

    /** [r], an offline route, with its time set by your own trips if there are enough, or by usual traffic otherwise. */
    fun adjust(ctx: Context, r: Route, to: LL, atMs: Long = System.currentTimeMillis()): Route {
        if (r.time <= 0 || r.len <= 0 || r.pts.isEmpty()) return r
        val base = if (r.baseTime > 0) r.baseTime else r.time
        val f = try { learnedFactor(ctx, r.pts.first(), to, atMs) } catch (e: Exception) { null } ?: usualFactor(r.len / base, atMs)
        return r.withTime(base * f).also { it.baseTime = base }
    }

    private fun trips(ctx: Context): MutableList<Trip> {
        cache?.let { return it }
        val out = ArrayList<Trip>()
        try {
            val a = JSONArray(ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "[]"))
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                out += Trip(o.getDouble("fa"), o.getDouble("fo"), o.getDouble("ta"), o.getDouble("to"), o.optBoolean("we"), o.optInt("m"), o.getDouble("r"))
            }
        } catch (e: Exception) { }
        val list = java.util.Collections.synchronizedList(out)
        cache = list
        return list
    }

    private fun save(ctx: Context, all: List<Trip>) {
        val a = JSONArray()
        for (t in all) a.put(JSONObject().put("fa", t.fLat).put("fo", t.fLon).put("ta", t.tLat).put("to", t.tLon).put("we", t.weekend).put("m", t.minute).put("r", t.ratio))
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply()
    }
}
