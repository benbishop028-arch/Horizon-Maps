package com.headunit.launcher

import org.json.JSONArray
import org.json.JSONObject

/** A place you saved yourself under your own name ("Mum's", "Gym"). */
/** [pinned]: shown in the row next to Home and Work (the star in Settings › Places & trips › Your places, GitHub bijindas). */
class LabeledPlace(val label: String, val place: Place, val group: String = "", val pinned: Boolean = true)

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
            (0 until a.length()).mapNotNull { i -> try { val o = a.getJSONObject(i); LabeledPlace(o.optString("l"), dec(o), o.optString("g"), !o.optBoolean("h", false)) } catch (e: Exception) { null } }
        } catch (e: Exception) { emptyList() }
    }

    private fun writeCustoms(list: List<LabeledPlace>) {
        val a = JSONArray()
        list.forEach { a.put(enc(it.place).put("l", it.label).also { o -> if (it.group.isNotBlank()) o.put("g", it.group); if (!it.pinned) o.put("h", true) }) }
        prefs.put(PreferenceKeys.PLACES_CUSTOM, a.toString())
    }

    /** Saves [p] as [label]; a place already saved there (or a label already used) is replaced. */
    fun addCustom(label: String, p: Place) {
        val l = label.trim().take(24).ifBlank { p.name.take(24) }
        // (a place saved again keeps the group it was in)
        val old = customs().firstOrNull { it.label.equals(l, true) || same(it.place, p) }
        writeCustoms(customs().filterNot { it.label.equals(l, true) || same(it.place, p) } + LabeledPlace(l, p, old?.group.orEmpty(), old?.pinned ?: true))
    }

    fun removeCustom(label: String) = writeCustoms(customs().filterNot { it.label == label })
    fun renameCustom(old: String, new: String) {
        val n = new.trim().take(24); if (n.isBlank()) return
        writeCustoms(customs().map { if (it.label == old) LabeledPlace(n, it.place, it.group, it.pinned) else it })
    }

    /** The groups your places are filed in (GitHub #138), in the order they first appear. */
    fun groups(): List<String> = customs().map { it.group }.filter { it.isNotBlank() }.distinct()
    /** Puts the place [label] in [group] ("" for no group). */
    fun setGroup(label: String, group: String) =
        writeCustoms(customs().map { if (it.label == label) LabeledPlace(it.label, it.place, group.trim().take(24), it.pinned) else it })
    /** Renames a group for every place in it. */
    fun renameGroup(old: String, new: String) {
        val n = new.trim().take(24); if (n.isBlank()) return
        writeCustoms(customs().map { if (it.group == old) LabeledPlace(it.label, it.place, n, it.pinned) else it })
    }
    fun moveCustom(label: String, by: Int) {
        val l = customs().toMutableList(); val i = l.indexOfFirst { it.label == label }; val j = i + by
        if (i < 0 || j !in l.indices) return
        val t = l[i]; l[i] = l[j]; l[j] = t; writeCustoms(l)
    }
    /** Shows [label] in the row next to Home and Work, or not. */
    fun setPinned(label: String, pinned: Boolean) =
        writeCustoms(customs().map { if (it.label == label) LabeledPlace(it.label, it.place, it.group, pinned) else it })
    fun customAt(p: Place): LabeledPlace? = customs().firstOrNull { same(it.place, p) }

    fun favourites(): List<Place> = readList("places_fav")
    fun isFavourite(p: Place) = favourites().any { same(it, p) }
    fun toggleFavourite(p: Place): Boolean {
        val cur = favourites()
        return if (cur.any { same(it, p) }) {
            writeList("places_fav", cur.filterNot { same(it, p) }); false
        } else {
            writeList("places_fav", listOf(p) + cur); true
        }
    }

    /** Imports saved places without replacing existing favourites; locations within 60 m count as duplicates. */
    fun importFavourites(imported: List<Place>): Pair<Int, Int> {
        val current = favourites().toMutableList()
        var added = 0
        var skipped = 0
        for (place in imported) {
            if (current.any { same(it, place) }) {
                skipped++
            } else {
                current += place
                added++
            }
        }
        if (added > 0) writeList("places_fav", current)
        return added to skipped
    }

    /**
     * Files [imported] places in Your places, in [group] and not starred, so a long list (50 places from Google Maps) folds into
     * one group row instead of filling the row of tiles next to Home and Work (GitHub #261, Dixet). Places within 60 m of one
     * already saved (in Your places, favourites, Home or Work) are skipped. Two places with the same name get "Name 2", "Name 3",
     * as each label in Your places is unique. Returns how many were added and how many skipped.
     */
    fun importToPlaces(imported: List<Place>, group: String): Pair<Int, Int> {
        val list = customs().toMutableList()
        val favs = favourites()
        val slots = listOfNotNull(slot("home"), slot("work"))
        val g = group.trim().take(24)
        var added = 0; var skipped = 0
        for (p in imported) {
            if (list.any { same(it.place, p) } || favs.any { same(it, p) } || slots.any { same(it, p) }) { skipped++; continue }
            list += LabeledPlace(uniqueLabel(p.name, list), p, g, pinned = false)
            added++
        }
        if (added > 0) writeCustoms(list)
        return added to skipped
    }

    private fun uniqueLabel(name: String, list: List<LabeledPlace>): String {
        val base = name.trim().ifBlank { "Place" }.take(24)
        if (list.none { it.label.equals(base, true) }) return base
        var n = 2
        while (true) {
            val tail = " $n"
            val l = base.take(24 - tail.length).trimEnd() + tail
            if (list.none { it.label.equals(l, true) }) return l
            n++
        }
    }

    /** Moves the favourite [p] to Your places (not starred, so it leaves the row next to Home and Work), keeping its name. */
    fun moveFavouriteToPlaces(p: Place, group: String = "") {
        val list = customs().toMutableList()
        if (list.none { same(it.place, p) }) list += LabeledPlace(uniqueLabel(p.name, list), p, group.trim().take(24), pinned = false)
        writeCustoms(list)
        writeList("places_fav", favourites().filterNot { same(it, p) })
    }

    /** Moves every favourite to Your places, in [group]. Returns how many were moved. */
    fun moveAllFavouritesToPlaces(group: String): Int {
        val favs = favourites()
        val list = customs().toMutableList()
        val g = group.trim().take(24)
        for (f in favs) if (list.none { same(it.place, f) }) list += LabeledPlace(uniqueLabel(f.name, list), f, g, pinned = false)
        writeCustoms(list)
        writeList("places_fav", emptyList())
        return favs.size
    }

    /** Gives a favourite a name of your own (it stays in the same place in the list). */
    fun renameFavourite(p: Place, name: String) {
        val n = name.trim().take(40)
        if (n.isEmpty()) return
        writeList("places_fav", favourites().map { if (same(it, p)) it.copy(name = n) else it })
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
