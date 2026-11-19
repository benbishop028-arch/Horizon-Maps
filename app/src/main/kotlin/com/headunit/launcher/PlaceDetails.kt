package com.headunit.launcher

import org.json.JSONObject
import java.util.Calendar

/**
 * Details for the place preview card, from OpenStreetMap (free): opening hours, phone, website, the address, what kind of place it
 * is, and a charging station's plugs. One lookup when a place is opened (Overpass), nothing more.
 */
class PlaceDetails(
    val kind: String,          // "fuel", "charging", "cafe", ... (the shortcut it matches), or ""
    val hours: String,         // OpenStreetMap's opening_hours, as written
    val phone: String,
    val website: String,
    val address: String,
    val plugs: List<String>,   // "Type 2 ×2 · 22 kW", "CCS ×1 · 50 kW"...
)

object PlaceInfo {
    /** The OpenStreetMap place at [p] best matching [name] (or the nearest shop or amenity for a dropped pin); null if none. */
    fun lookup(p: LL, name: String): PlaceDetails? {
        val q = String.format(java.util.Locale.US,
            "[out:json][timeout:8];nwr(around:70,%.6f,%.6f)[~\"^(shop|amenity|tourism|leisure|office|craft|healthcare)$\"~\".\"];out tags center 25;", p.lat, p.lon)
        val els = Overpass.query(q, 6000, 9000, 2)
        val want = norm(name)
        var best: JSONObject? = null
        var bestScore = -1.0
        for (i in 0 until els.length()) {
            val e = els.optJSONObject(i) ?: continue
            val t = e.optJSONObject("tags") ?: continue
            val la = e.optDouble("lat", e.optJSONObject("center")?.optDouble("lat") ?: Double.NaN)
            val lo = e.optDouble("lon", e.optJSONObject("center")?.optDouble("lon") ?: Double.NaN)
            val d = if (la.isNaN() || lo.isNaN()) 70.0 else Geo.dist(p, LL(la, lo))
            val n = norm(t.optString("name"))
            val b = norm(t.optString("brand"))
            // a name match counts for most; then how near it is
            val match = when {
                want.isEmpty() -> 0.0
                n == want || b == want -> 3.0
                n.isNotEmpty() && (want.contains(n) || n.contains(want)) -> 2.0
                b.isNotEmpty() && (want.contains(b) || b.contains(want)) -> 1.5
                else -> 0.0
            }
            if (want.isNotEmpty() && match == 0.0 && d > 25.0) continue
            val score = match * 100.0 - d
            if (score > bestScore) { bestScore = score; best = t }
        }
        val t = best ?: return null
        val addr = listOf(listOf(t.optString("addr:housenumber"), t.optString("addr:street")).filter { it.isNotBlank() }.joinToString(" "),
            t.optString("addr:city").ifBlank { t.optString("addr:suburb") }, t.optString("addr:postcode")).filter { it.isNotBlank() }.joinToString(", ")
        return PlaceDetails(kindOf(t), t.optString("opening_hours").trim(), t.optString("phone").ifBlank { t.optString("contact:phone") }.substringBefore(';').trim(),
            t.optString("website").ifBlank { t.optString("contact:website") }.substringBefore(';').trim(), addr, plugsOf(t))
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun kindOf(t: JSONObject): String = when (t.optString("amenity")) {
        "fuel" -> "fuel"; "charging_station" -> "charging"; "cafe" -> "cafe"
        "restaurant", "fast_food", "pub", "bar", "food_court" -> "food"; "parking", "parking_entrance" -> "parking"; "toilets" -> "toilets"
        else -> ""
    }

    private fun plugsOf(t: JSONObject): List<String> {
        val names = listOf("type2_combo" to "CCS", "chademo" to "CHAdeMO", "type2" to "Type 2", "type2_cable" to "Type 2", "tesla_supercharger" to "Tesla",
            "type1_combo" to "CCS1", "type1" to "Type 1", "nacs" to "NACS", "schuko" to "Schuko", "bs1363" to "3-pin")
        val out = ArrayList<String>()
        for ((k, label) in names) {
            val n = t.optString("socket:$k").trim()
            if (n.isEmpty() || n == "no" || n == "0") continue
            val kw = t.optString("socket:$k:output").trim().ifBlank { "" }.replace("kW", " kW").replace("  ", " ")
            out += label + (if (n.toIntOrNull() != null) " ×$n" else "") + (if (kw.isNotBlank()) " · $kw" else "")
        }
        return out.distinct()
    }
}

/**
 * Reads OpenStreetMap's opening_hours for the common forms ("Mo-Sa 07:00-22:00; Su 10:00-16:00", "24/7", "Mo-Fr 08:00-12:00,13:00-18:00",
 * "Su off"). Anything it can't follow (public holidays, months, "sunrise") is left out of "open now": then only the hours are shown.
 */
object OpeningHours {
    private val dayNames = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

    /** Per day (0 = Monday), the open stretches in minutes from midnight; null if it couldn't be read. */
    fun parse(s: String): List<List<IntRange>>? {
        val txt = s.trim()
        if (txt.isEmpty()) return null
        if (txt == "24/7") return List(7) { listOf(0..1440) }
        val week = MutableList(7) { mutableListOf<IntRange>() }
        for (rule0 in txt.split(';')) {
            val rule = rule0.trim()
            if (rule.isEmpty()) continue
            if (rule.startsWith("PH") || rule.startsWith("SH")) continue
            val m = Regex("^((?:(?:Mo|Tu|We|Th|Fr|Sa|Su)(?:-(?:Mo|Tu|We|Th|Fr|Sa|Su))?,?)+)?\\s*(.*)$").find(rule) ?: return null
            val daysPart = m.groupValues[1]
            val timesPart = m.groupValues[2].trim()
            val days = if (daysPart.isBlank()) (0..6).toList() else daysPart.split(',').filter { it.isNotBlank() }.flatMap { d ->
                val ab = d.split('-')
                val a = dayNames.indexOf(ab[0]); val b = dayNames.indexOf(ab.getOrElse(1) { ab[0] })
                if (a < 0 || b < 0) return null
                if (a <= b) (a..b).toList() else (a..6).toList() + (0..b).toList()
            }
            if (timesPart == "off" || timesPart == "closed") { days.forEach { week[it].clear() }; continue }
            val ranges = ArrayList<IntRange>()
            for (tr in timesPart.split(',')) {
                val t = Regex("^(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})\\+?$").find(tr.trim()) ?: return null
                val a = t.groupValues[1].toInt() * 60 + t.groupValues[2].toInt()
                var b = t.groupValues[3].toInt() * 60 + t.groupValues[4].toInt()
                if (b <= a) b += 1440
                ranges += a..b
            }
            days.forEach { week[it].clear(); week[it].addAll(ranges) }
        }
        return week
    }

    /** Open now, and the time it next closes or opens ("22:00"), or null when the hours couldn't be read. */
    fun state(s: String, now: Calendar = Calendar.getInstance()): Pair<Boolean, String>? {
        val w = parse(s) ?: return null
        val day = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        fun at(d: Int) = w[((d % 7) + 7) % 7]
        // open now: today's stretches, or last night's running past midnight
        for (r in at(day)) if (m in r.first until r.last) return true to (if (r.last >= 1440 && r == 0..1440) "" else hm(r.last))
        for (r in at(day - 1)) if (r.last > 1440 && m + 1440 in r.first until r.last) return true to hm(r.last)
        // closed: the next opening this week
        for (k in 0..7) {
            val opens = at(day + k).filter { k > 0 || it.first > m }.minByOrNull { it.first } ?: continue
            return false to (if (k == 0) hm(opens.first) else dayName(day + k) + " " + hm(opens.first))
        }
        return false to ""
    }

    /** The hours written out in the phone's language ("Mon–Sat 07:00–22:00 · Sun 10:00–16:00"), or the original if unreadable. */
    fun pretty(s: String): String {
        val w = parse(s) ?: return s
        if (w.all { it == listOf(0..1440) }) return ""
        val groups = ArrayList<Pair<IntRange, List<IntRange>>>()
        var i = 0
        while (i < 7) {
            var j = i
            while (j + 1 < 7 && w[j + 1] == w[i]) j++
            groups += (i..j) to w[i]
            i = j + 1
        }
        return groups.filter { it.second.isNotEmpty() }.joinToString(" · ") { (d, rs) ->
            (if (d.first == d.last) dayName(d.first) else dayName(d.first) + "–" + dayName(d.last)) + " " + rs.joinToString(", ") { hm(it.first) + "–" + hm(it.last) }
        }
    }

    private fun hm(min: Int): String { val m = min % 1440; return String.format(java.util.Locale.US, "%02d:%02d", m / 60, m % 60) }

    private fun dayName(d: Int): String {
        val names = java.text.DateFormatSymbols.getInstance(L.locale).shortWeekdays   // [1] = Sunday
        val idx = ((d % 7) + 7) % 7
        return names[if (idx == 6) 1 else idx + 2].trimEnd('.')
    }
}
