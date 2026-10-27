package com.headunit.launcher

import org.json.JSONArray
import org.json.JSONObject

/** A place you saved yourself under your own name ("Mum's", "Gym"). */
class LabeledPlace(val label: String, val place: Place)

/** Saved places (home, work, your own labelled places, favourites, recents), stored as JSON in [Prefs]. */
class PlaceStore(private val prefs: Prefs) {
    private fun enc(p: Place) = JSONObject().put("n", p.name).put("s", p.sub).put("la", p.pos.lat).put("lo", p.pos.lon)
    private fun dec(o: JSONObject) = Place(o.optString("n"), o.optString("s"), LL(o.getDouble("la"), o.getDouble("lo")))

    private fun readOne(key: String): Place? {
        val s = prefs.s(key, "")
        if (s.isBlank()) return null
        return try { dec(JSONObject(s)) } catch (e: Exception) { null }
    }

    private fun readList(key: String): List<Place> {
        val s = prefs.s(key, "")
        if (s.isBlank()) return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).mapNotNull { try { dec(a.getJSONObject(it)) } catch (e: Exception) { null } }
        } catch (e: Exception) { emptyList() }
    }

    private fun writeList(key: String, list: List<Place>) {
        val a = JSONArray()
        list.forEach { a.put(enc(it)) }
        prefs.put(key, a.toString())
    }

    private fun same(a: Place, b: Place) = Geo.dist(a.pos, b.pos) < 60

    fun slot(name: String): Place? = readOne("place_$name")
    fun setSlot(name: String, p: Place) = prefs.put("place_$name", enc(p).toString())


    /** Your own labelled places, in the order you added them (shown as pills after Home and Work). */
    fun customs(): List<LabeledPlace> {
        val s = prefs.s(PreferenceKeys.PLACES_CUSTOM, "")
        if (s.isBlank()) return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).mapNotNull { i -> try { val o = a.getJSONObject(i); LabeledPlace(o.optString("l"), dec(o)) } catch (e: Exception) { null } }
        } catch (e: Exception) { emptyList() }
    }

    private fun writeCustoms(list: List<LabeledPlace>) {
        val a = JSONArray()
        list.forEach { a.put(enc(it.place).put("l", it.label)) }
        prefs.put(PreferenceKeys.PLACES_CUSTOM, a.toString())
    }

    /** Saves [p] as [label]; a place already saved there (or a label already used) is replaced. */
    fun addCustom(label: String, p: Place) {
        val l = label.trim().take(24).ifBlank { p.name.take(24) }
        writeCustoms(customs().filterNot { it.label.equals(l, true) || same(it.place, p) } + LabeledPlace(l, p))
    }

    fun removeCustom(label: String) = writeCustoms(customs().filterNot { it.label == label })
    fun renameCustom(old: String, new: String) {
        val n = new.trim().take(24); if (n.isBlank()) return
        writeCustoms(customs().map { if (it.label == old) LabeledPlace(n, it.place) else it })
    }
    fun moveCustom(label: String, by: Int) {
        val l = customs().toMutableList(); val i = l.indexOfFirst { it.label == label }; val j = i + by
        if (i < 0 || j !in l.indices) return
        val t = l[i]; l[i] = l[j]; l[j] = t; writeCustoms(l)
    }
    fun customAt(p: Place): LabeledPlace? = customs().firstOrNull { same(it.place, p) }

    fun favourites(): List<Place> = readList("places_fav")
    fun isFavourite(p: Place) = favourites().any { same(it, p) }
    fun toggleFavourite(p: Place): Boolean {
        val cur = favourites()
        return if (cur.any { same(it, p) }) {
            writeList("places_fav", cur.filterNot { same(it, p) }); false
        } else {
            writeList("places_fav", (listOf(p) + cur).take(20)); true
        }
    }

    fun recents(): List<Place> = readList("places_recent")
    /** Takes one place off the recent list (it's still remembered for searching without signal). */
    fun removeRecent(p: Place) = writeList("places_recent", recents().filterNot { same(it, p) })
    fun clearSlot(name: String) = prefs.put("place_$name", "")
    fun clearParked() = prefs.put(PreferenceKeys.PLACE_PARKED, "")
    fun clearFavourites() = prefs.put(PreferenceKeys.PLACES_FAVOURITES, "")
    /** Forgets recent destinations and the places remembered for searching without signal. */
    fun clearHistory() { prefs.put(PreferenceKeys.PLACES_RECENT, ""); prefs.put(PreferenceKeys.PLACES_SEEN, "") }
    fun addRecent(p: Place) {
        writeList("places_recent", (listOf(p) + recents().filterNot { same(it, p) }).take(8))
        // everywhere you have routed to is also kept for searching without signal
        writeList("places_seen", (listOf(p) + readList("places_seen").filterNot { same(it, p) }).take(600))
    }

    /** Search over saved, favourite and previously used places only: works without any signal. */
    fun searchLocal(q: String, near: LL?): List<Place> {
        val toks = q.lowercase().split(' ', ',').filter { it.isNotBlank() }
        if (toks.isEmpty()) return emptyList()
        val pool = ArrayList<Place>()
        slot("home")?.let { pool += Place("Home", it.name, it.pos) }
        slot("work")?.let { pool += Place("Work", it.name, it.pos) }
        for (c in customs()) pool += Place(c.label, c.place.name, c.place.pos)
        pool += favourites(); pool += recents(); pool += readList("places_seen")
        val uniq = ArrayList<Place>()
        for (p in pool) if (uniq.none { same(it, p) }) uniq += p
        return uniq.filter { p -> val h = (p.name + " " + p.sub).lowercase(); toks.all { h.contains(it) } }
            .sortedBy { p -> (if (p.name.lowercase().startsWith(toks[0])) 0.0 else 1e7) + (if (near != null) Geo.dist(near, p.pos) else 0.0) }
            .take(10)
    }
}

// ------------------------------------------------------------------ trips

class Trip(val start: Long, val end: Long, val dist: Double, val moving: Long, val maxSpeed: Double, val to: String)

class TripStore(private val prefs: Prefs) {
    fun all(): List<Trip> {
        val s = prefs.s(PreferenceKeys.TRIPS, "")
        if (s.isBlank()) return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).mapNotNull {
                try {
                    val o = a.getJSONObject(it)
                    Trip(o.getLong("s"), o.getLong("e"), o.getDouble("d"), o.getLong("m"), o.getDouble("v"), o.optString("t"))
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun add(t: Trip) {
        val a = JSONArray()
        (listOf(t) + all()).take(300).forEach {
            a.put(JSONObject().put("s", it.start).put("e", it.end).put("d", it.dist).put("m", it.moving).put("v", it.maxSpeed).put("t", it.to))
        }
        prefs.put(PreferenceKeys.TRIPS, a.toString())
    }

    fun clear() = prefs.put(PreferenceKeys.TRIPS, "")
}
