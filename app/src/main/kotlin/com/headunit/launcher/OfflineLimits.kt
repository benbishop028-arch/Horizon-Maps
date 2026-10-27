package com.headunit.launcher

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/**
 * The roads of a downloaded area, with their speed limits, read from the pack's limits.bin (built from OpenStreetMap by the pack
 * workflow). It gives the speed limit lookup and the wrong-way warning the same roads the online search would, with no network.
 *
 * The file: "HNL1", the tile zoom and the tile count, then an index (tile key, offset, length) sorted by key, then one compressed
 * block per zoom-14 tile listing every road that passes through it: type, one-way flags, way id, name, ref, the limit tags and the
 * shape (delta-coded, 1e-5 degrees).
 */
object OfflineLimits {
    private val HW = arrayOf(
        "motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link", "secondary", "secondary_link",
        "tertiary", "tertiary_link", "unclassified", "residential", "living_street", "service", "road",
    )

    private class Index(val stamp: Long, val zoom: Int, val keys: IntArray, val offs: IntArray, val lens: IntArray, val dataStart: Long)

    private val indexes = HashMap<String, Index>()
    private val tiles = object : LinkedHashMap<String, List<Overpass.Way>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Overpass.Way>>?) = size > 24
    }

    /** True when some downloaded area has offline speed limits for [p]. */
    fun covers(ctx: Context, p: LL): Boolean = OfflineRouter.areaWith(ctx, "limits.bin", p) != null

    /**
     * Every road within about 250 m of [p] from the downloaded area that covers it, or null when no downloaded area with speed limits
     * covers [p] (then the online lookup is used). An empty list means "covered, but no roads here".
     */
    @Synchronized
    fun waysNear(ctx: Context, p: LL): List<Overpass.Way>? {
        val area = OfflineRouter.areaWith(ctx, "limits.bin", p) ?: return null
        val f = File(OfflineRouter.packDir(ctx, area), "limits.bin")
        val idx = index(f) ?: return null
        val dLat = 250.0 / 111320.0
        val dLon = 250.0 / (111320.0 * Math.cos(Math.toRadians(p.lat)).coerceAtLeast(0.2))
        val z = 1 shl idx.zoom
        fun tx(lon: Double) = Math.floor((lon + 180.0) / 360.0 * z).toInt().coerceIn(0, z - 1)
        fun ty(lat: Double): Int {
            val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
            return Math.floor((1.0 - Math.log(Math.tan(r) + 1.0 / Math.cos(r)) / Math.PI) / 2.0 * z).toInt().coerceIn(0, z - 1)
        }
        val x0 = tx(p.lon - dLon); val x1 = tx(p.lon + dLon)
        val y0 = ty(p.lat + dLat); val y1 = ty(p.lat - dLat)
        val seen = HashSet<Long>()
        val out = ArrayList<Overpass.Way>()
        // only the roads that actually come within the box round you: a whole tile in a city holds thousands, and the wrong-way
        // check goes through these on every GPS fix
        val s0 = p.lat - dLat; val n0 = p.lat + dLat; val w0 = p.lon - dLon; val e0 = p.lon + dLon
        for (y in y0..y1) for (x in x0..x1) {
            for (w in tile(f, idx, y * z + x)) {
                val b = w.box
                if (b[2] < s0 || b[0] > n0 || b[3] < w0 || b[1] > e0) continue
                if (w.id == 0L || seen.add(w.id)) out += w
            }
        }
        return out
    }

    private fun index(f: File): Index? {
        if (!f.exists()) return null
        val stamp = f.lastModified() xor f.length()
        indexes[f.path]?.let { if (it.stamp == stamp) return it }
        return try {
            RandomAccessFile(f, "r").use { raf ->
                val magic = ByteArray(4); raf.readFully(magic)
                if (String(magic, Charsets.US_ASCII) != "HNL1") return null
                val zoom = raf.readInt()
                val n = raf.readInt()
                if (n < 0 || n > 20_000_000 || zoom !in 8..18) return null
                val buf = ByteArray(n * 12)
                raf.readFully(buf)
                val bb = java.nio.ByteBuffer.wrap(buf)
                val keys = IntArray(n); val offs = IntArray(n); val lens = IntArray(n)
                for (i in 0 until n) { keys[i] = bb.int; offs[i] = bb.int; lens[i] = bb.int }
                Index(stamp, zoom, keys, offs, lens, 12L + n * 12L).also { indexes[f.path] = it }
            }
        } catch (e: Exception) { null }
    }

    private fun tile(f: File, idx: Index, key: Int): List<Overpass.Way> {
        val ck = f.path + "#" + key
        tiles[ck]?.let { return it }
        val i = java.util.Arrays.binarySearch(idx.keys, key)
        val list = if (i < 0) emptyList() else try {
            val raw = ByteArray(idx.lens[i])
            RandomAccessFile(f, "r").use { raf -> raf.seek(idx.dataStart + (idx.offs[i].toLong() and 0xffffffffL)); raf.readFully(raw) }
            parse(java.util.zip.InflaterInputStream(raw.inputStream()).readBytes())
        } catch (e: Exception) { emptyList() }
        tiles[ck] = list
        return list
    }

    private fun parse(b: ByteArray): List<Overpass.Way> {
        var p = 0
        fun uv(): Long {
            var r = 0L; var sh = 0
            while (true) {
                val x = b[p++].toInt() and 0xff
                r = r or ((x and 0x7f).toLong() shl sh)
                if (x and 0x80 == 0) return r
                sh += 7
            }
        }
        fun zz(): Long { val v = uv(); return (v ushr 1) xor -(v and 1) }
        fun str(): String { val n = uv().toInt(); val s = String(b, p, n, Charsets.UTF_8); p += n; return s }
        val n = uv().toInt()
        val out = ArrayList<Overpass.Way>(n)
        for (k in 0 until n) {
            val hw = b[p++].toInt() and 0xff
            val flags = b[p++].toInt() and 0xff
            val id = uv()
            val name = str(); val ref = str(); val ms = str(); val mt = str(); val fwd = str(); val bwd = str()
            val np = uv().toInt()
            var la = uv() - 9_000_000L; var lo = uv() - 18_000_000L
            val g = ArrayList<LL>(np)
            g += LL(la / 1e5, lo / 1e5)
            for (j in 1 until np) { la += zz(); lo += zz(); g += LL(la / 1e5, lo / 1e5) }
            val t = JSONObject()
            t.put("highway", HW.getOrElse(hw) { "road" })
            if (flags and 1 != 0) t.put("oneway", "yes")
            if (flags and 2 != 0) t.put("oneway", "-1")
            if (flags and 4 != 0) t.put("junction", "roundabout")
            if (name.isNotEmpty()) t.put("name", name)
            if (ref.isNotEmpty()) t.put("ref", ref)
            if (ms.isNotEmpty()) t.put("maxspeed", ms)
            // a limit given as a type or zone ("GB:nsl_single", "DE:urban"): read both as a value and as a zone
            if (mt.isNotEmpty()) { t.put("maxspeed:type", mt); t.put("source:maxspeed", mt) }
            if (fwd.isNotEmpty()) t.put("maxspeed:forward", fwd)
            if (bwd.isNotEmpty()) t.put("maxspeed:backward", bwd)
            if (g.size >= 2) out += Overpass.Way(id, t, g)
        }
        return out
    }
}
