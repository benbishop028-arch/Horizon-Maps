package com.headunit.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs

// ------------------------------------------------------------------ habits: learn where you usually go and when
class HabitStore(private val prefs: Prefs) {
    private class Rec(val name: String, val sub: String, val lat: Double, val lon: Double, val t: Long)

    private fun all(): List<Rec> {
        val s = prefs.s(PreferenceKeys.HABITS, "")
        if (s.isBlank()) return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map { val o = a.getJSONObject(it); Rec(o.getString("n"), o.optString("s"), o.getDouble("a"), o.getDouble("o"), o.getLong("t")) }
        } catch (e: Exception) { emptyList() }
    }

    fun record(p: Place) {
        if (p.name == "Dropped pin" || p.name == "My car") return
        val list = (all() + Rec(p.name, p.sub, p.pos.lat, p.pos.lon, System.currentTimeMillis())).takeLast(250)
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("n", it.name).put("s", it.sub).put("a", it.lat).put("o", it.lon).put("t", it.t)) }
        prefs.put(PreferenceKeys.HABITS, a.toString())
    }

    private fun minutes(t: Long): Int { val c = Calendar.getInstance().apply { timeInMillis = t }; return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE) }
    private fun weekend(t: Long): Boolean { val d = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.DAY_OF_WEEK); return d == Calendar.SATURDAY || d == Calendar.SUNDAY }

    /** A place you have driven to at least 3 times on the same kind of day within 45 minutes of now. */
    fun suggest(now: Long): Place? {
        val cutoff = now - 90L * 86_400_000L
        val near = all().filter { it.t > cutoff && weekend(it.t) == weekend(now) && abs(minutes(it.t) - minutes(now)) <= 45 }
        val groups = near.groupBy { "${Math.round(it.lat * 500)}:${Math.round(it.lon * 500)}" }
        val best = groups.values.maxByOrNull { it.size } ?: return null
        if (best.size < 3) return null
        val r = best.last()
        val avg = best.map { minutes(it.t) }.average().toInt()
        return Place(r.name, "Usually around %02d:%02d".format(avg / 60, avg % 60), LL(r.lat, r.lon))
    }
}
