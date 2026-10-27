package com.headunit.launcher

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Search that works without signal. Two sources:
 *  - the places you have saved, favourited or routed to before (kept by [PlaceStore]), and
 *  - an optional download of every populated place in the UK (towns, villages and suburbs, from GeoNames), kept in a small file on the device.
 * Streets, house numbers and businesses need the online search.
 */
object OfflinePlaces {
    private class Entry(val name: String, val lowName: String, val pos: LL, val pop: Int, val kind: String)

    @Volatile private var cache: List<Entry>? = null
    private fun file(ctx: Context) = File(ctx.filesDir, "offline_places.tsv")

    fun installed(ctx: Context): Boolean = file(ctx).let { it.exists() && it.length() > 1000 }
    fun sizeMb(ctx: Context): Double = file(ctx).length() / 1048576.0

    private fun load(ctx: Context): List<Entry> {
        cache?.let { return it }
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        val out = ArrayList<Entry>(50000)
        try {
            f.forEachLine { line ->
                val p = line.split('\t')
                if (p.size >= 5) {
                    val la = p[1].toDoubleOrNull(); val lo = p[2].toDoubleOrNull()
                    if (la != null && lo != null) out += Entry(p[0], p[0].lowercase(), LL(la, lo), p[3].toIntOrNull() ?: 0, p[4])
                }
            }
        } catch (e: Exception) { }
        cache = out
        return out
    }

    /** Matches [q] against place names (every word must appear), biggest and nearest first. */
    fun search(ctx: Context, q: String, near: LL?): List<Place> {
        val toks = q.lowercase().split(' ', ',').filter { it.isNotBlank() }
        if (toks.isEmpty()) return emptyList()
        val hits = load(ctx).filter { e -> toks.all { e.lowName.contains(it) } }
        if (hits.isEmpty()) return emptyList()
        return hits.sortedBy { e ->
            val starts = if (e.lowName.startsWith(toks[0])) 0.0 else 1.0
            val far = if (near != null) minOf(Geo.dist(near, e.pos) / 1000.0, 999.0) else 0.0
            // names that start with what you typed come first, then bigger places, then closer ones
            starts * 1e9 - e.pop.toDouble() * 10.0 + far
        }.take(8).map { e ->
            Place(e.name, (if (e.kind == "PPLC" || e.kind.startsWith("PPLA")) "Town / city" else "Village / area") + " · offline", e.pos)
        }
    }

    /** Downloads one country's populated places ([cc], e.g. "GB", "FR", "US") and keeps only what is needed (a few MB). Returns the number of places, or throws. */
    fun download(ctx: Context, cc: String = "GB"): Int {
        val code = cc.uppercase().ifBlank { "GB" }
        val c = URL("https://download.geonames.org/export/dump/$code.zip").openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 60000
        c.setRequestProperty("User-Agent", "HorizonNav/1.0")
        if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
        val tmp = File(ctx.filesDir, "offline_places.tmp")
        var n = 0
        ZipInputStream(c.inputStream.buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null && e.name != "$code.txt") { e = zin.nextEntry }
            if (e == null) throw java.io.IOException("Unexpected download contents")
            tmp.bufferedWriter().use { w ->
                zin.bufferedReader().forEachLine { line ->
                    val f = line.split('\t')
                    // columns: 1 name, 4 lat, 5 lon, 6 feature class, 7 feature code, 14 population
                    if (f.size > 14 && f[6] == "P") {
                        w.write(f[1].replace('\t', ' ') + "\t" + f[4] + "\t" + f[5] + "\t" + f[14] + "\t" + f[7]); w.newLine(); n++
                    }
                }
            }
        }
        if (n < 50) { tmp.delete(); throw java.io.IOException("Download looked incomplete") }
        val dst = file(ctx)
        dst.delete()
        tmp.renameTo(dst)
        cache = null
        return n
    }

    fun delete(ctx: Context) { file(ctx).delete(); cache = null }
}
