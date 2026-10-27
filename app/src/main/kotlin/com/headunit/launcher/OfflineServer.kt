package com.headunit.launcher

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream

/**
 * The downloaded pack's own map (map.pmtiles, OpenStreetMap data) for the Mapbox map engine. Mapbox can't read .pmtiles files,
 * so a tiny web server on the device itself (127.0.0.1, unreachable from outside the phone) hands it the map tiles and the label
 * fonts from the pack, as an ordinary map source would. It only runs while the downloaded map is showing.
 */
object OfflineServer {
    @Volatile private var server: ServerSocket? = null
    @Volatile private var port = 0
    @Volatile private var pm: PmTiles? = null
    @Volatile private var pmFile: File? = null
    private val pool = Executors.newFixedThreadPool(4) { r -> Thread(r, "offline-tiles").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }

    /** The offline map's style, with its tiles and fonts served from the pack (starts the server if it isn't running). */
    fun styleJson(ctx: Context, dark: Boolean): String {
        val p = start(ctx)
        val base = org.json.JSONObject(OfflineMap.styleJson(ctx, dark))
        val src = org.json.JSONObject().put("type", "vector")
            .put("tiles", org.json.JSONArray().put("http://127.0.0.1:$p/t/{z}/{x}/{y}.pbf"))
            .put("minzoom", 0).put("maxzoom", pm?.maxZoom ?: 14)
        base.put("sources", org.json.JSONObject().put("omt", src))
        base.put("glyphs", "http://127.0.0.1:$p/f/{fontstack}/{range}.pbf")
        return base.toString()
    }

    @Synchronized
    private fun start(ctx: Context): Int {
        val f = OfflineMap.file(ctx)
        // the pack in use changed (another area): read the new file
        if (pmFile?.absolutePath != f.absolutePath || pmFile?.lastModified() != f.lastModified()) {
            try { pm?.close() } catch (e: Exception) { }
            pm = try { PmTiles(f) } catch (e: Exception) { null }
            pmFile = f
        }
        server?.let { if (!it.isClosed) return port }
        val s = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
        server = s; port = s.localPort
        val fonts = OfflineMap.fontsDir(ctx)
        Thread({
            while (!s.isClosed) {
                val c = try { s.accept() } catch (e: Exception) { break }
                try { pool.execute { serve(c, fonts) } } catch (e: Exception) { try { c.close() } catch (x: Exception) { } }
            }
        }, "offline-server").apply { isDaemon = true }.start()
        return port
    }

    /** Stops the server (when the online map is back). */
    @Synchronized
    fun stop() {
        try { server?.close() } catch (e: Exception) { }
        server = null
    }

    private const val MAX_HEADER_LINE = 8192
    private const val MAX_HEADER_BYTES = 32768
    private const val MAX_HEADER_COUNT = 64

    /** Reads one line without allowing BufferedReader.readLine() to grow without a limit. */
    private fun readBoundedLine(reader: java.io.BufferedReader, maxChars: Int): String? {
        val out = StringBuilder(minOf(256, maxChars))
        while (true) {
            val ch = reader.read()
            if (ch == -1) return if (out.isEmpty()) null else out.toString()
            if (ch == '\n'.code) {
                if (out.isNotEmpty() && out.last() == '\r') out.setLength(out.length - 1)
                return out.toString()
            }
            if (out.length >= maxChars) throw java.io.IOException("HTTP header line too long")
            out.append(ch.toChar())
        }
    }

    private fun serve(c: Socket, fonts: File) {
        c.use { sock ->
            try {
                sock.soTimeout = 10_000
                val inp = sock.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                val first = readBoundedLine(inp, MAX_HEADER_LINE) ?: return
                var headerBytes = first.length
                var headerCount = 0
                // Bound request header size/count so a local client cannot make the tile server allocate unbounded strings.
                while (true) {
                    val l = readBoundedLine(inp, MAX_HEADER_LINE) ?: break
                    headerBytes += l.length
                    headerCount++
                    if (headerBytes > MAX_HEADER_BYTES || headerCount > MAX_HEADER_COUNT) return
                    if (l.isEmpty()) break
                }
                val path = first.split(' ').getOrNull(1)?.substringBefore('?') ?: ""
                val body: ByteArray? = when {
                    path.startsWith("/t/") -> {
                        val z = path.removePrefix("/t/").removeSuffix(".pbf").split('/')
                        if (z.size == 3) pm?.tile(z[0].toInt(), z[1].toInt(), z[2].toInt()) else null
                    }
                    path.startsWith("/f/") -> {
                        val parts = path.removePrefix("/f/").split('/')
                        if (parts.size == 2) {
                            val stacks = java.net.URLDecoder.decode(parts[0], "UTF-8").split(',').map { it.trim() }
                            val range = parts[1]
                            if (!range.matches(Regex("[0-9]+-[0-9]+\\.pbf"))) null
                            else stacks.asSequence().map { File(File(fonts, it.replace("/", "")), range) }.firstOrNull { it.isFile && it.canonicalPath.startsWith(fonts.canonicalPath) }?.readBytes()
                        } else null
                    }
                    else -> null
                }
                val out = sock.getOutputStream()
                if (body == null) {
                    // no tile there (sea, or outside the area): an empty answer, which the map draws as nothing
                    out.write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                } else {
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/x-protobuf\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray())
                    out.write(body)
                }
                out.flush()
            } catch (e: Exception) { }
        }
    }
}

/**
 * Reads tiles from a PMTiles (version 3) file: a header, a directory of tile ids (Hilbert-curve order, compressed) and the tiles
 * themselves (usually gzip-compressed). Tiles are returned uncompressed. Directories read are kept in memory (they're small).
 */
class PmTiles(file: File) {
    private val raf = RandomAccessFile(file, "r")
    private val rootOff: Long; private val rootLen: Long
    private val leafOff: Long; private val dataOff: Long
    private val internalComp: Int; private val tileComp: Int
    val maxZoom: Int
    private class Entry(val tileId: Long, val offset: Long, val length: Int, val runLength: Int)
    private val dirCache = object : LinkedHashMap<Long, List<Entry>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<Entry>>?) = size > 64
    }

    init {
        val h = ByteArray(127)
        synchronized(raf) { raf.seek(0); raf.readFully(h) }
        if (String(h, 0, 7, Charsets.US_ASCII) != "PMTiles" || h[7].toInt() != 3) throw IllegalArgumentException("not PMTiles v3")
        fun u64(o: Int): Long { var v = 0L; for (i in 7 downTo 0) v = (v shl 8) or (h[o + i].toLong() and 0xFF); return v }
        rootOff = u64(8); rootLen = u64(16); leafOff = u64(40); dataOff = u64(56)
        internalComp = h[97].toInt(); tileComp = h[98].toInt()
        maxZoom = (h[101].toInt() and 0xFF).coerceIn(0, 22)
    }

    fun close() { synchronized(raf) { raf.close() } }

    private fun read(off: Long, len: Int): ByteArray {
        val b = ByteArray(len)
        synchronized(raf) { raf.seek(off); raf.readFully(b) }
        return b
    }

    private fun decompress(b: ByteArray, comp: Int): ByteArray = when (comp) {
        // 0 unknown, 1 none, 2 gzip (brotli and zstd aren't used by the packs)
        2 -> GZIPInputStream(b.inputStream()).use { g -> val o = ByteArrayOutputStream(b.size * 3); g.copyTo(o); o.toByteArray() }
        else -> b
    }

    private fun directory(off: Long, len: Long): List<Entry> {
        synchronized(dirCache) { dirCache[off]?.let { return it } }
        val d = decompress(read(off, len.toInt()), internalComp)
        var p = 0
        fun varint(): Long {
            var v = 0L; var shift = 0
            while (true) { val b = d[p++].toInt() and 0xFF; v = v or ((b and 0x7F).toLong() shl shift); if (b < 0x80) return v; shift += 7 }
        }
        val n = varint().toInt()
        val ids = LongArray(n); var last = 0L
        for (i in 0 until n) { last += varint(); ids[i] = last }
        val runs = IntArray(n) { varint().toInt() }
        val lens = IntArray(n) { varint().toInt() }
        val offs = LongArray(n)
        for (i in 0 until n) {
            val v = varint()
            offs[i] = if (v == 0L && i > 0) offs[i - 1] + lens[i - 1] else v - 1
        }
        val list = (0 until n).map { Entry(ids[it], offs[it], lens[it], runs[it]) }
        synchronized(dirCache) { dirCache[off] = list }
        return list
    }

    private fun find(entries: List<Entry>, id: Long): Entry? {
        var lo = 0; var hi = entries.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = entries[mid].tileId
            if (c < id) lo = mid + 1 else if (c > id) hi = mid - 1 else return entries[mid]
        }
        // the entry just before: a run of identical tiles, or a leaf directory covering this id
        if (hi >= 0) {
            val e = entries[hi]
            if (e.runLength == 0) return e
            if (id - e.tileId < e.runLength) return e
        }
        return null
    }

    /** The tile at z/x/y (uncompressed), or null when there's none. */
    fun tile(z: Int, x: Int, y: Int): ByteArray? {
        if (z < 0 || z > maxZoom || x < 0 || y < 0 || x >= (1 shl z) || y >= (1 shl z)) return null
        val id = tileId(z, x, y)
        var dOff = rootOff; var dLen = rootLen
        for (depth in 0 until 4) {
            val e = find(directory(dOff, dLen), id) ?: return null
            if (e.runLength > 0) return decompress(read(dataOff + e.offset, e.length), tileComp)
            dOff = leafOff + e.offset; dLen = e.length.toLong()
        }
        return null
    }

    companion object {
        /** The PMTiles tile id: tiles of all lower zooms first, then the Hilbert-curve position at this zoom. */
        fun tileId(z: Int, x: Int, y: Int): Long {
            var acc = 0L
            for (t in 0 until z) acc += (1L shl t) * (1L shl t)
            var xx = x.toLong(); var yy = y.toLong()
            var d = 0L
            var s = (1L shl z) / 2
            while (s > 0) {
                val rx = if ((xx and s) > 0) 1L else 0L
                val ry = if ((yy and s) > 0) 1L else 0L
                d += s * s * ((3 * rx) xor ry)
                if (ry == 0L) {
                    if (rx == 1L) { xx = s - 1 - xx; yy = s - 1 - yy }
                    val t = xx; xx = yy; yy = t
                }
                s /= 2
            }
            return acc + d
        }
    }
}
