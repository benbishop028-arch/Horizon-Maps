package com.headunit.launcher

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Offline routing, stage 1: gets a region's routing data onto the device, starts the Valhalla engine on it and reports what happened.
 * The engine is driven by reflection for now, so this stays safe to build while its exact classes are confirmed on the real device.
 */
object OfflineRouter {
    private fun base(ctx: Context): File = ctx.getExternalFilesDir(null) ?: ctx.filesDir

    /** The folder that held the single pack before several areas could be kept (moved into [packsRoot] the first time round). */
    fun legacyDir(ctx: Context): File = File(base(ctx), "offline")

    /** Every downloaded area has its own folder in here, named by its area id. */
    fun packsRoot(ctx: Context): File = File(base(ctx), "packs").also { it.mkdirs() }

    fun packDir(ctx: Context, id: String): File = File(packsRoot(ctx), id)

    @Volatile private var active: String? = null
    @Volatile private var activeLoaded = false

    /** The area in use (the one you're in, picked automatically), or null when none is downloaded. */
    fun activeId(ctx: Context): String? {
        if (!activeLoaded) {
            active = try { File(packsRoot(ctx), "active.txt").takeIf { it.exists() }?.readText()?.trim()?.ifBlank { null } } catch (e: Exception) { null }
            activeLoaded = true
        }
        val a = active
        if (a != null && a in installedIds(ctx)) return a
        // the active one was deleted or never set: any area that's here
        val any = installedIds(ctx).firstOrNull()
        if (any != a) setActive(ctx, any)
        return any
    }

    fun setActive(ctx: Context, id: String?) {
        active = id; activeLoaded = true; fileFlags.clear()
        try { File(packsRoot(ctx), "active.txt").writeText(id ?: "") } catch (e: Exception) { }
    }

    /** The folder of the area in use (the old single-pack folder when nothing has been moved yet). */
    fun dir(ctx: Context): File {
        val a = activeId(ctx)
        return if (a != null) packDir(ctx, a) else legacyDir(ctx).also { it.mkdirs() }
    }

    fun installedIn(d: File): Boolean = File(d, "tiles.tar").let { it.exists() && it.length() > 100_000 } && File(d, "valhalla.json").exists()

    fun installed(ctx: Context): Boolean = installedIn(dir(ctx))

    // the list of areas and which files they have are asked for many times a second (the map's lamps, lights and speed limits):
    // they're remembered for a few seconds, and forgotten at once whenever an area is downloaded, switched or deleted
    @Volatile private var idsCache: List<String>? = null
    @Volatile private var idsAt = 0L
    private val fileFlags = java.util.concurrent.ConcurrentHashMap<String, Long>()   // path -> when it was seen (negative: missing)

    /** Forgets the remembered areas and files (after a download, switch or delete). */
    fun invalidate() { idsCache = null; fileFlags.clear() }

    /** The areas downloaded on this unit. */
    fun installedIds(ctx: Context): List<String> {
        val now = android.os.SystemClock.uptimeMillis()
        idsCache?.let { if (now - idsAt < 15_000L) return it }
        val ids = (packsRoot(ctx).listFiles() ?: emptyArray()).filter { it.isDirectory && installedIn(it) }.map { it.name }.sorted()
        idsCache = ids; idsAt = now
        return ids
    }

    /** Whether [d] has the file [name] bigger than [min] bytes (remembered for 15 s). */
    fun hasFile(d: File, name: String, min: Long = 8): Boolean {
        val key = d.path + "/" + name
        val now = android.os.SystemClock.uptimeMillis()
        fileFlags[key]?.let { at -> if (now - Math.abs(at) < 15_000L) return at > 0 }
        val ok = File(d, name).let { it.exists() && it.length() > min }
        fileFlags[key] = if (ok) now else -now
        return ok
    }

    /** Moves a pack from before several areas could be kept into its own area folder (instant: it's a rename on the same storage). */
    fun migrateLegacy(ctx: Context, id: String) {
        val old = legacyDir(ctx)
        if (!installedIn(old)) return
        val dst = packDir(ctx, id)
        if (dst.exists() && installedIn(dst)) return
        dst.deleteRecursively()
        closeEngine()
        val moved = old.renameTo(dst)
        invalidate()
        if (moved && activeId(ctx) == null) setActive(ctx, id)
        if (active == null) setActive(ctx, id)
    }

    /**
     * The area an area covers, as south, west, north, east: read from the map file's header (and remembered), or a rough box for
     * the UK packs that may have no map. Null when it can't be told.
     */
    private val boundsCache = java.util.concurrent.ConcurrentHashMap<String, DoubleArray>()

    fun bounds(ctx: Context, id: String): DoubleArray? {
        boundsCache[id]?.let { return it }
        return boundsOnDisk(ctx, id)?.also { boundsCache[id] = it }
    }

    private fun boundsOnDisk(ctx: Context, id: String): DoubleArray? {
        val d = packDir(ctx, id)
        val cache = File(d, "bounds.txt")
        try { if (cache.exists()) cache.readText().trim().split(',').map { it.toDouble() }.takeIf { it.size == 4 }?.let { return it.toDoubleArray() } } catch (e: Exception) { }
        var b: DoubleArray? = null
        try {
            val f = File(d, "map.pmtiles")
            if (f.exists() && f.length() > 127) java.io.RandomAccessFile(f, "r").use { raf ->
                val h = ByteArray(127); raf.readFully(h)
                if (String(h, 0, 7, Charsets.US_ASCII) == "PMTiles" && h[7].toInt() == 3) {
                    fun i32(o: Int) = (h[o].toInt() and 255) or ((h[o + 1].toInt() and 255) shl 8) or ((h[o + 2].toInt() and 255) shl 16) or (h[o + 3].toInt() shl 24)
                    val w = i32(102) / 1e7; val s0 = i32(106) / 1e7; val e = i32(110) / 1e7; val n = i32(114) / 1e7
                    if (n > s0 && e > w) b = doubleArrayOf(s0, w, n, e)
                }
            }
        } catch (e: Exception) { }
        if (b == null) b = when (id) {
            "gb" -> doubleArrayOf(49.8, -8.7, 60.9, 1.8)
            "ew" -> doubleArrayOf(49.8, -6.5, 55.9, 1.8)
            else -> null
        }
        b?.let { try { cache.writeText(it.joinToString(",")) } catch (e: Exception) { } }
        return b
    }

    private fun contains(b: DoubleArray, p: LL) = p.lat >= b[0] && p.lat <= b[2] && p.lon >= b[1] && p.lon <= b[3]

    /** The smallest downloaded area that covers all of [pts] (the most detailed choice where areas overlap), or null. */
    fun areaFor(ctx: Context, vararg pts: LL): String? = areaWith(ctx, null, *pts)

    /** Like [areaFor], but only areas that have the file [need] (an area downloaded before that file existed doesn't count). */
    fun areaWith(ctx: Context, need: String?, vararg pts: LL): String? {
        var best: String? = null; var bestA = Double.MAX_VALUE
        for (id in installedIds(ctx)) {
            if (need != null && !hasFile(packDir(ctx, id), need)) continue
            val b = bounds(ctx, id) ?: continue
            if (pts.all { contains(b, it) }) {
                val a = (b[2] - b[0]) * (b[3] - b[1])
                if (a < bestA) { bestA = a; best = id }
            }
        }
        return best
    }

    /** Fetches one file of the pack. Returns false if the server does not have it. A file already here with the same size is kept, so an interrupted download can be repeated cheaply. */
    private fun fetch(d: File, base: String, name: String, progress: (String) -> Unit): Boolean {
        val c = URL("$base/$name").openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 60000; c.instanceFollowRedirects = true
        if (c.responseCode !in 200..299) { c.disconnect(); return false }
        val total = c.contentLengthLong
        val dst = File(d, name)
        // already here (same size): kept, and marked as checked now, so the "new map data" check doesn't keep offering it
        if (total > 0 && dst.exists() && dst.length() == total) { c.disconnect(); dst.setLastModified(System.currentTimeMillis()); progress("Already have $name"); return true }
        progress("Downloading $name…")
        val tmp = File(d, "$name.part")
        c.inputStream.use { i ->
            tmp.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var done = 0L; var lastMb = -1L
                while (true) {
                    val n = i.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n); done += n
                    val mb = done / 8_000_000
                    if (mb != lastMb) { lastMb = mb; progress("Downloading $name… ${done / 1_048_576} MB" + if (total > 0) " of ${total / 1_048_576} MB" else "") }
                }
            }
        }
        dst.delete(); tmp.renameTo(dst)
        return true
    }

    /** A file that is either whole on the server or (when too big for one release file) in parts name.part00, .part01 … that are joined here. */
    private fun fetchJoined(d: File, base: String, name: String, progress: (String) -> Unit): Boolean {
        if (fetch(d, base, name, progress)) return true
        // already joined last time? the parts are deleted after joining, so compare the parts' total size with the file we have
        val have = File(d, name)
        if (have.exists()) {
            var sum = 0L; var k = 0
            while (true) {
                val c = URL("$base/$name.part%02d".format(k)).openConnection() as HttpURLConnection
                c.requestMethod = "HEAD"; c.connectTimeout = 20000; c.readTimeout = 20000; c.instanceFollowRedirects = true
                val ok = c.responseCode in 200..299
                val len = c.contentLengthLong
                c.disconnect()
                if (!ok) break
                sum += len; k++
            }
            if (k > 0 && sum == have.length()) { have.setLastModified(System.currentTimeMillis()); progress("Already have $name"); return true }
        }
        var n = 0
        while (fetch(d, base, "$name.part%02d".format(n), progress)) n++
        if (n == 0) return false
        progress("Joining $name…")
        val joined = File(d, "$name.joining")
        joined.outputStream().use { o ->
            for (i in 0 until n) {
                val part = File(d, "$name.part%02d".format(i))
                part.inputStream().use { it.copyTo(o) }
                part.delete()
            }
        }
        val dst = File(d, name)
        dst.delete(); joined.renameTo(dst)
        return true
    }

    /** Downloads the pack's files from [baseUrl] (for example a GitHub release's download folder). The map, fonts and search list are optional. */
    fun download(ctx: Context, baseUrl: String, id: String, extrasUrl: String = baseUrl, progress: (String) -> Unit) {
        val base = baseUrl.trim().trimEnd('/')
        val extras = extrasUrl.trim().trimEnd('/')
        val d = packDir(ctx, id).also { it.mkdirs() }
        // updating the area in use: let go of its routing file while it's replaced
        if (d.path == dir(ctx).path) closeEngine()
        File(d, "bounds.txt").delete(); boundsCache.remove(id); invalidate()
        for (name in listOf("valhalla.json", "admins.sqlite")) if (!fetch(d, base, name, progress)) throw java.io.IOException("$name not found at that address")
        if (!fetchJoined(d, base, "tiles.tar", progress)) throw java.io.IOException("tiles.tar not found at that address")
        if (!fetchJoined(d, base, "places.tsv", progress)) progress("No places.tsv: streets and shops will not be searchable offline")
        if (fetchJoined(d, base, "fonts.zip", progress)) OfflineMap.unpackFonts(File(d, "fonts.zip"), File(d, "fonts"))
        if (!fetchJoined(d, base, "map.pmtiles", progress)) progress("No map.pmtiles in this pack")
        if (!fetchJoined(d, base, "postcodes.tsv", progress)) progress("No postcodes.tsv in this pack")
        if (!fetchJoined(d, base, "addresses.tsv", progress)) progress("No addresses.tsv: house numbers will not be searchable offline")
        if (fetchJoined(d, base, "names.idx", progress)) progress("Search index ready")
        if (!fetchJoined(d, base, "lamps.tsv", progress)) progress("No lamps.tsv: night map will use estimated lamps")
        if (!fetchJoined(d, base, "signals.tsv", progress)) progress("No signals.tsv: traffic lights will be fetched online instead")
        // speed limits and cameras (added to packs later, so they may live in a separate release for the older UK packs)
        for (name in listOf("limits.bin", "cameras.tsv")) {
            if (!fetch(d, extras, name, progress) && (extras == base || !fetch(d, base, name, progress)))
                progress("No $name yet: ${if (name == "limits.bin") "speed limits" else "speed cameras"} will be looked up online")
        }
        invalidate()
        progress("Download finished")
    }

    /** The build machine's valhalla.json points at its own folders: rewrite those paths for this device. */
    private fun deviceConfig(d: File): String {
        val j = JSONObject(File(d, "valhalla.json").readText())
        val m = j.getJSONObject("mjolnir")
        m.put("tile_extract", File(d, "tiles.tar").absolutePath)
        m.put("tile_dir", File(d, "tiles").absolutePath)
        m.put("admin", File(d, "admins.sqlite").absolutePath)
        m.remove("timezone")
        val out = File(d, "valhalla_device.json")
        out.writeText(j.toString())
        return out.absolutePath
    }

    private fun sig(m: java.lang.reflect.Method) =
        m.name + m.parameterTypes.joinToString(", ", "(", ")") { it.simpleName } + ": " + m.returnType.simpleName

    /** Starts the engine on the downloaded pack, asks it for a short route and returns a plain-text report of everything it found. */
    fun diagnose(ctx: Context, from: LL, to: LL): String {
        val sb = StringBuilder()
        sb.appendLine("Pack folder: ${dir(ctx)}")
        sb.appendLine(if (installed(ctx)) "Pack files: found (tiles.tar ${"%.0f".format(File(dir(ctx), "tiles.tar").length() / 1048576.0)} MB)" else "Pack files: NOT found (need tiles.tar and valhalla.json)")
        // which engine classes are in the app
        try {
            val names = ArrayList<String>()
            @Suppress("DEPRECATION")
            val df = dalvik.system.DexFile(ctx.packageCodePath)
            val en = df.entries()
            while (en.hasMoreElements()) {
                val n = en.nextElement()
                if (n.startsWith("com.valhalla") || n.startsWith("io.github.rallista")) names += n
            }
            sb.appendLine("Engine classes in the app: ${names.size}")
            names.filter { !it.contains('$') }.sorted().take(120).forEach { sb.appendLine("  $it") }
        } catch (e: Throwable) { sb.appendLine("Could not list classes: ${e.javaClass.simpleName} ${e.message}") }
        val cls = try { Class.forName("com.valhalla.valhalla.Valhalla") } catch (e: Throwable) {
            sb.appendLine("Engine class not found: ${e.javaClass.simpleName} ${e.message}"); return sb.toString()
        }
        sb.appendLine("Valhalla constructors:")
        cls.constructors.forEach { sb.appendLine("  $it") }
        sb.appendLine("Valhalla methods:")
        cls.methods.filter { it.declaringClass == cls }.forEach { sb.appendLine("  ${sig(it)}") }
        if (!installed(ctx)) return sb.toString()
        try {
            val cfg = deviceConfig(dir(ctx))
            val ctor = cls.constructors.firstOrNull { it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java }
            if (ctor == null) { sb.appendLine("No (String) constructor: need the other form"); return sb.toString() }
            val t0 = System.currentTimeMillis()
            val engine = ctor.newInstance(cfg)
            sb.appendLine("Engine started in ${System.currentTimeMillis() - t0} ms")
            try {
                val req = """{"locations":[{"lat":${from.lat},"lon":${from.lon}},{"lat":${to.lat},"lon":${to.lon}}],"costing":"auto"}"""
                val routeM = cls.methods.firstOrNull { it.name == "route" && it.parameterTypes.size == 1 }
                if (routeM == null) sb.appendLine("No route(request) method found")
                else {
                    val reqType = routeM.parameterTypes[0]
                    sb.appendLine("route() takes ${reqType.name}")
                    // build the typed request from JSON with Moshi, which the engine already depends on
                    val builderC = Class.forName("com.squareup.moshi.Moshi\$Builder")
                    val b = builderC.getDeclaredConstructor().newInstance()
                    val fac = Class.forName("com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory").getDeclaredConstructor().newInstance()
                    builderC.getMethod("add", Class.forName("com.squareup.moshi.JsonAdapter\$Factory")).invoke(b, fac)
                    val moshi = builderC.getMethod("build").invoke(b)
                    val adapter = moshi.javaClass.getMethod("adapter", Class::class.java).invoke(moshi, reqType)
                    val typed = adapter.javaClass.getMethod("fromJson", String::class.java).invoke(adapter, req)
                    val t1 = System.currentTimeMillis()
                    val resp = routeM.invoke(engine, typed)
                    sb.appendLine("Route answered in ${System.currentTimeMillis() - t1} ms")
                    sb.appendLine("Response type: ${resp?.javaClass?.name}")
                    sb.appendLine((resp?.toString() ?: "null").take(700))
                }
            } catch (e: Throwable) {
                val c = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
                sb.appendLine("Route test failed: ${c.javaClass.simpleName}: ${c.message}")
            }
            (engine as? java.io.Closeable)?.close()
        } catch (e: Throwable) {
            val c = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
            sb.appendLine("Engine failed: ${c.javaClass.simpleName}: ${c.message}")
        }
        return sb.toString()
    }


    fun sizeOf(f: File): Long = if (!f.exists()) 0L else if (f.isFile) f.length() else (f.listFiles()?.sumOf { sizeOf(it) } ?: 0L)

    /** Removes the whole pack (routing data, map, search list, fonts) and frees the space. */
    @Synchronized
    fun deleteAll(ctx: Context) {
        closeEngine()
        dir(ctx).deleteRecursively(); invalidate()
    }

    /** Removes one downloaded area and frees its space; another area (if any) takes over. */
    @Synchronized
    fun deleteArea(ctx: Context, id: String) {
        closeEngine()
        packDir(ctx, id).deleteRecursively(); boundsCache.remove(id); invalidate()
        if (active == id) setActive(ctx, installedIds(ctx).firstOrNull())
    }

    @Synchronized
    fun closeEngine() {
        try { engine?.close() } catch (e: Exception) { }
        engine = null; engineStamp = ""
    }

    // ---- offline search over the pack's own streets, shops and places ----
    fun hasPlaces(ctx: Context): Boolean = File(dir(ctx), "places.tsv").let { it.exists() && it.length() > 1000 }

    private fun lowerB(b: Byte): Byte = if (b in 65..90) (b + 32).toByte() else b

    /** ASCII lower-casing as a lookup table (the search loops call it for every byte, so it has to be cheap). */
    private val LOWER = ByteArray(256) { i -> (if (i in 65..90) i + 32 else i).toByte() }
    private fun low(b: Byte): Byte = LOWER[b.toInt() and 255]
    private fun wordGap(b: Byte): Boolean = b == 32.toByte() || b == 45.toByte() || b == 40.toByte() || b == 47.toByte() || b == 39.toByte() || b == 46.toByte() || b == 44.toByte() || b == 38.toByte()

    /** True when every word in [toks] (lower case, UTF-8 bytes) starts a word in buf[from, to): "sout" finds "Southampton" and "Great South Road". */
    private fun nameMatches(buf: ByteArray, from: Int, to: Int, toks: List<ByteArray>): Boolean {
        for (t in toks) {
            if (t.isEmpty()) continue
            val first = t[0]
            var found = false
            var i = from
            val last = to - t.size
            while (i <= last) {
                if (low(buf[i]) == first && (i == from || wordGap(buf[i - 1]))) {
                    var k = 1
                    while (k < t.size && low(buf[i + k]) == t[k]) k++
                    if (k == t.size) { found = true; break }
                }
                i++
            }
            if (!found) return false
        }
        return true
    }

    /** The same word-start test on text, for words with accented letters (the byte test only lower-cases plain letters). */
    private fun wordStartIn(name: String, w: String): Boolean {
        var i = name.indexOf(w)
        while (i >= 0) {
            if (i == 0 || !name[i - 1].isLetterOrDigit()) return true
            i = name.indexOf(w, i + 1)
        }
        return false
    }

    private class Hit(val score: Double, val place: Place)

    /** What the last search matched, so typing more letters only re-checks those lines instead of the whole list. */
    private class PlaceCache(val path: String, val stamp: Long, val words: List<String>, val offs: LongArray, val n: Int)
    // one per list (keyed by its file)
    private val placeCaches = java.util.concurrent.ConcurrentHashMap<String, PlaceCache>()

    /**
     * Looks the longest word you typed up in the pack's word index (names.idx: every word of every name with the place's line in
     * places.tsv, sorted), and returns those lines in file order. Null when there's no index, or the word is too common to help.
     */
    private fun indexLookup(idx: File, words: List<String>, alive: () -> Boolean): LongArray? {
        if (!idx.exists() || idx.length() < 100) return null
        val w = words.maxByOrNull { it.length } ?: return null
        if (w.length < 2) return null
        val t = w.toByteArray(Charsets.UTF_8)
        var out = LongArray(1024); var n = 0; var tooMany = false
        try {
            java.io.RandomAccessFile(idx, "r").use { raf ->
                scanBlockRaw(raf, lowerBound(raf, t), t, 60_001, alive) { buf, ls, le ->
                    var tb = ls
                    while (tb < le && buf[tb] != '\t'.code.toByte()) tb++
                    if (tb < le) {
                        var v = 0L; var k = tb + 1
                        while (k < le) { val c = buf[k] - 48; if (c in 0..9) v = v * 10 + c; k++ }
                        if (n == out.size) { if (n >= 60_000) tooMany = true else out = out.copyOf(n * 2) }
                        if (!tooMany) out[n++] = v
                    }
                }
            }
        } catch (e: Throwable) { return null }
        if (tooMany || !alive()) return null
        val r = out.copyOf(n)
        r.sort()
        // a name with the word twice is listed twice: keep each line once
        var m = 0
        for (i in r.indices) if (i == 0 || r[i] != r[i - 1]) r[m++] = r[i]
        return r.copyOf(m)
    }

    /** True when every earlier word is the start of the new word in the same place (so the new matches are among the old ones). */
    private fun refines(old: List<String>, new: List<String>): Boolean =
        new.size >= old.size && old.indices.all { new[it].startsWith(old[it]) }

    /**
     * Every word you typed must start a word in the name. Names starting with it come first, then the closest.
     * The list is big (millions of lines for a large country), so it is read in chunks and never held in memory. Each search
     * remembers which lines matched, so as you type more letters only those are checked again. [alive] is asked every so often,
     * and a search that has been replaced by a newer one stops straight away.
     */
    fun searchPlaces(ctx: Context, q: String, near: LL?, alive: () -> Boolean = { true }, d: File = dir(ctx)): List<Place> {
        val words = q.lowercase().split(' ', ',').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val f = File(d, "places.tsv")
        if (!f.exists()) return emptyList()
        val ascii = words.filter { w -> w.all { it.code < 128 } }.map { it.toByteArray(Charsets.UTF_8) }
        val accented = words.filter { w -> w.any { it.code >= 128 } }
        val first = words[0].toByteArray(Charsets.UTF_8)
        val firstAscii = words[0].all { it.code < 128 }
        val top = ArrayList<Hit>()
        var offs = LongArray(4096); var n = 0; var overflow = false
        val tab = '\t'.code.toByte()

        // one line: buf[ls, le) starting at file offset [off]
        fun consider(buf: ByteArray, ls: Int, le: Int, off: Long) {
            var t = ls
            while (t < le && buf[t] != tab) t++
            if (t >= le) return
            if (!nameMatches(buf, ls, t, ascii)) return
            if (accented.isNotEmpty()) { val nm = String(buf, ls, t - ls, Charsets.UTF_8).lowercase(); if (!accented.all { wordStartIn(nm, it) }) return }
            if (!overflow) {
                if (n == offs.size) { if (n >= 60_000) overflow = true else offs = offs.copyOf(n * 2) }
                if (!overflow) offs[n++] = off
            }
            // kind, lat, lon after the name
            var t2 = t + 1; while (t2 < le && buf[t2] != tab) t2++
            var t3 = t2 + 1; while (t3 < le && buf[t3] != tab) t3++
            if (t2 >= le || t3 >= le) return
            val la = String(buf, t2 + 1, t3 - t2 - 1, Charsets.US_ASCII).toDoubleOrNull() ?: return
            val lo = String(buf, t3 + 1, le - t3 - 1, Charsets.US_ASCII).trim().toDoubleOrNull() ?: return
            val pos = LL(la, lo)
            val starts = if (firstAscii && t - ls >= first.size && (first.indices).all { k -> low(buf[ls + k]) == first[k] }) 0.0
                         else if (!firstAscii && String(buf, ls, t - ls, Charsets.UTF_8).lowercase().startsWith(words[0])) 0.0 else 1.0
            val far = if (near != null) minOf(Geo.dist(near, pos) / 1000.0, 999.0) else 0.0
            val score = starts * 1e6 + far
            if (top.size < 8 || score < top.last().score) {
                top += Hit(score, Place(String(buf, ls, t - ls, Charsets.UTF_8), String(buf, t + 1, t2 - t - 1, Charsets.UTF_8) + " · offline", pos))
                top.sortBy { it.score }
                if (top.size > 8) top.removeAt(top.size - 1)
            }
        }

        val prev = placeCaches[f.path]
        val stamp = f.lastModified()
        // checks just the lines at [lines] (file offsets, in file order, so reads go forward through the file); false if replaced
        fun scanLines(lines: LongArray, count: Int): Boolean {
            java.io.RandomAccessFile(f, "r").use { raf ->
                val buf = ByteArray(1 shl 16)
                var bStart = -1L; var bLen = 0
                for (j in 0 until count) {
                    if (j and 511 == 0 && !alive()) return false
                    val o = lines[j]
                    if (bStart < 0 || o < bStart || o + 1024 > bStart + bLen) {
                        raf.seek(o); bStart = o; bLen = maxOf(0, raf.read(buf, 0, buf.size))
                    }
                    val ls = (o - bStart).toInt()
                    var le = ls
                    while (le < bLen && buf[le] != '\n'.code.toByte()) le++
                    consider(buf, ls, le, o)
                }
            }
            return true
        }
        try {
            val indexed = if (prev != null && prev.path == f.path && prev.stamp == stamp && refines(prev.words, words)) null else indexLookup(File(d, "names.idx"), words, alive)
            if (prev != null && prev.path == f.path && prev.stamp == stamp && refines(prev.words, words)) {
                // only the lines the shorter search found
                if (!scanLines(prev.offs, prev.n)) return emptyList()
            } else if (indexed != null) {
                // the pack's word index says which lines have a word starting with what you typed: only those are read
                if (!scanLines(indexed, indexed.size)) return emptyList()
            } else f.inputStream().use { ins ->
                val buf = ByteArray(1 shl 20)
                var have = 0
                var base = 0L // file offset of buf[0]
                val nl = '\n'.code.toByte()
                while (true) {
                    if (!alive()) return emptyList()
                    val r = ins.read(buf, have, buf.size - have)
                    val end = if (r < 0) have else have + r
                    var i = 0
                    while (i < end) {
                        var e = i
                        while (e < end && buf[e] != nl) e++
                        if (e >= end && r >= 0) break // incomplete last line: finish it after the next read
                        consider(buf, i, e, base + i)
                        i = e + 1
                    }
                    if (r < 0) break
                    // keep the unfinished last line at the front of the buffer
                    val keep = if (i < end) end - i else 0
                    if (keep > 0) System.arraycopy(buf, i, buf, 0, keep)
                    base += i
                    have = keep
                    if (have >= buf.size) { base += have; have = 0 } // a line longer than the buffer cannot be real: drop it
                }
            }
        } catch (e: Throwable) { return top.map { it.place } }
        if (!overflow) placeCaches[f.path] = PlaceCache(f.path, stamp, words, offs, n) else placeCaches.remove(f.path)
        return top.map { it.place }
    }

    // ---- offline house numbers and postcodes (sorted files, searched with a binary search so they are fast even when huge) ----
    fun hasAddresses(ctx: Context): Boolean = File(dir(ctx), "addresses.tsv").let { it.exists() && it.length() > 1000 }

    /** Compares the lower-cased text before the first tab at buf[off..) with [target]: negative when it sorts before, 0 when it starts with it, positive after. */
    private fun cmpKey(buf: ByteArray, off: Int, end: Int, target: ByteArray): Int {
        var kl = 0
        while (off + kl < end && buf[off + kl] != '\t'.code.toByte()) kl++
        val n = minOf(kl, target.size)
        for (i in 0 until n) {
            val a = lowerB(buf[off + i]).toInt() and 0xff
            val b = target[i].toInt() and 0xff
            if (a != b) return a - b
        }
        return if (kl < target.size) -1 else 0
    }

    /** Start of the first line whose key is not before [target]. */
    private fun lowerBound(raf: java.io.RandomAccessFile, target: ByteArray): Long {
        var lo = 0L
        var hi = raf.length()
        val buf = ByteArray(1024)
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            // find the start of the line that contains mid
            val from = maxOf(0L, mid - 512)
            raf.seek(from)
            val got = raf.read(buf, 0, (mid - from).toInt().coerceAtLeast(0))
            var ls = from
            for (k in 0 until maxOf(got, 0)) if (buf[k] == '\n'.code.toByte()) ls = from + k + 1
            raf.seek(ls)
            val n = raf.read(buf, 0, buf.size)
            if (n <= 0) { hi = ls; continue }
            var e = 0
            while (e < n && buf[e] != '\n'.code.toByte()) e++
            val c = cmpKey(buf, 0, e, target)
            if (c < 0) lo = ls + e + 1 else hi = ls
        }
        return lo
    }

    /** Reads lines from [start] while their key still starts with [target], handing each to [onLine] as text columns. Stops after [maxLines]. */
    private fun scanBlock(raf: java.io.RandomAccessFile, start: Long, target: ByteArray, maxLines: Int, onLine: (List<String>) -> Unit) {
        raf.seek(start)
        val buf = ByteArray(1 shl 16)
        var have = 0
        var lines = 0
        while (true) {
            val r = raf.read(buf, have, buf.size - have)
            val end = if (r < 0) have else have + r
            var i = 0
            while (i < end) {
                var e = i
                while (e < end && buf[e] != '\n'.code.toByte()) e++
                if (e >= end && r >= 0) break
                if (cmpKey(buf, i, e, target) != 0) return
                onLine(String(buf, i, e - i, Charsets.UTF_8).split('\t'))
                if (++lines >= maxLines) return
                i = e + 1
            }
            if (r < 0) return
            val keep = if (i < end) end - i else 0
            if (keep > 0) System.arraycopy(buf, i, buf, 0, keep)
            have = keep
            if (have >= buf.size) have = 0
        }
    }

    /** Where the pack is published by the build workflow, so the Download button works without typing anything. */
    const val DEFAULT_PACK = "https://github.com/benbishop028-arch/HeadUnitOrbis/releases/download/pack-ew"
    /** The Great Britain pack (England, Wales and Scotland), built by the same workflow with the "pack-gb" release name. */
    const val DEFAULT_PACK_GB = "https://github.com/benbishop028-arch/HeadUnitOrbis/releases/download/pack-gb"

    fun hasSignals(ctx: Context): Boolean = hasFile(dir(ctx), "signals.tsv", 20)

    fun hasLamps(ctx: Context): Boolean = hasFile(dir(ctx), "lamps.tsv", 100)

    /** Real street lamps (OSM highway=street_lamp) inside a box, as [lat, lon]. The file is sorted by zoom-15 tile so each tile is a quick lookup. */
    fun lamps(ctx: Context, south: Double, west: Double, north: Double, east: Double, file: String = "lamps.tsv", d: File = dir(ctx), maxTiles: Int = 30): List<DoubleArray> {
        val out = ArrayList<DoubleArray>()
        val f = File(d, file)
        if (!f.exists()) return out
        fun tx(lon: Double) = Math.floor((lon + 180.0) / 360.0 * 32768.0).toInt().coerceIn(0, 32767)
        fun ty(lat: Double): Int {
            val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
            return Math.floor((1.0 - Math.log(Math.tan(r) + 1.0 / Math.cos(r)) / Math.PI) / 2.0 * 32768.0).toInt().coerceIn(0, 32767)
        }
        val x0 = tx(west); val x1 = tx(east); val y0 = ty(north); val y1 = ty(south)
        if ((x1 - x0 + 1) * (y1 - y0 + 1) > maxTiles) return out
        try {
            java.io.RandomAccessFile(f, "r").use { raf ->
                for (y in y0..y1) for (x in x0..x1) {
                    val t = "%05d%05d".format(y, x).toByteArray()
                    scanBlock(raf, lowerBound(raf, t), t, 4000) { c ->
                        val la = c.getOrNull(1)?.toDoubleOrNull(); val lo = c.getOrNull(2)?.toDoubleOrNull()
                        // (cameras.tsv has a 4th column: which way the camera checks, in degrees, when it's known)
                        val dd = c.getOrNull(3)?.toDoubleOrNull()
                        if (la != null && lo != null && la in south..north && lo in west..east) out.add(if (dd != null) doubleArrayOf(la, lo, dd) else doubleArrayOf(la, lo))
                    }
                }
            }
        } catch (e: Throwable) { }
        return out
    }

    /** Like [scanBlock], but hands over the raw bytes of each line (buf, start, end), so lines can be skipped without decoding them. */
    private fun scanBlockRaw(raf: java.io.RandomAccessFile, start: Long, target: ByteArray, maxLines: Int, alive: () -> Boolean, onLine: (ByteArray, Int, Int) -> Unit) {
        raf.seek(start)
        val buf = ByteArray(1 shl 18)
        var have = 0
        var lines = 0
        while (true) {
            if (!alive()) return
            val r = raf.read(buf, have, buf.size - have)
            val end = if (r < 0) have else have + r
            var i = 0
            while (i < end) {
                var e = i
                while (e < end && buf[e] != '\n'.code.toByte()) e++
                if (e >= end && r >= 0) break
                if (cmpKey(buf, i, e, target) != 0) return
                onLine(buf, i, e)
                if (++lines >= maxLines) return
                i = e + 1
            }
            if (r < 0) return
            val keep = if (i < end) end - i else 0
            if (keep > 0) System.arraycopy(buf, i, buf, 0, keep)
            have = keep
            if (have >= buf.size) have = 0
        }
    }

    private val numRe = Regex("^\\d{1,4}[a-zA-Z]?$")
    private val pcRe = Regex("^[a-zA-Z]{1,2}\\d[a-zA-Z\\d]?\\d?[a-zA-Z]{0,2}$")

    /** "12 high street" style searches: the number plus the start of a street name. Also finds postcodes like "SO14 2AB". */
    fun searchAddresses(ctx: Context, q: String, near: LL?, alive: () -> Boolean = { true }, d: File = dir(ctx)): List<Place> {
        val out = ArrayList<Place>()
        try {
            val toks = q.trim().split(Regex("[ ,]+")).filter { it.isNotEmpty() }
            // postcode
            val pc = q.replace(" ", "")
            val pf = File(d, "postcodes.tsv")
            if (pc.length >= 3 && pc.any { it.isDigit() } && pcRe.matches(pc) && pf.exists()) {
                val hits = ArrayList<Pair<Double, Place>>()
                java.io.RandomAccessFile(pf, "r").use { raf ->
                    val t = pc.lowercase().toByteArray(Charsets.UTF_8)
                    scanBlock(raf, lowerBound(raf, t), t, 3000) { p ->
                        val la = p.getOrNull(2)?.toDoubleOrNull(); val lo = p.getOrNull(3)?.toDoubleOrNull()
                        if (la != null && lo != null) {
                            val pos = LL(la, lo)
                            hits += (if (near != null) Geo.dist(near, pos) else 0.0) to Place(p[1], "Postcode · offline", pos)
                        }
                    }
                }
                out += hits.sortedBy { it.first }.take(5).map { it.second }
            }
            // house number + street
            val ni = toks.indexOfFirst { numRe.matches(it) }
            val af = File(d, "addresses.tsv")
            if (toks.size >= 2 && ni >= 0 && af.exists()) {
                val num = toks[ni].lowercase()
                val street = toks.filterIndexed { i, _ -> i != ni }.joinToString(" ").lowercase()
                if (street.length >= 2) {
                    val hits = ArrayList<Pair<Double, Place>>()
                    java.io.RandomAccessFile(af, "r").use { raf ->
                        val t = street.toByteArray(Charsets.UTF_8)
                        // a common street name ("high street") has tens of thousands of lines across a country: the house number is
                        // compared on the raw bytes, and only the lines that match are turned into text
                        val nb = num.toByteArray(Charsets.UTF_8)
                        val tab = '\t'.code.toByte()
                        scanBlockRaw(raf, lowerBound(raf, t), t, 2_000_000, alive) { buf, ls, le ->
                            // columns: street, number, lat, lon, postcode
                            var a = ls
                            while (a < le && buf[a] != tab) a++
                            var b = a + 1
                            while (b < le && buf[b] != tab) b++
                            if (b < le && b - a - 1 == nb.size && nb.indices.all { k -> low(buf[a + 1 + k]) == nb[k] }) {
                                val p = String(buf, ls, le - ls, Charsets.UTF_8).split('\t')
                                val la = p.getOrNull(2)?.toDoubleOrNull(); val lo = p.getOrNull(3)?.toDoubleOrNull()
                                if (la != null && lo != null) {
                                    val pos = LL(la, lo)
                                    hits += (if (near != null) Geo.dist(near, pos) else 0.0) to Place(p[1] + " " + p[0], (p.getOrNull(4)?.takeIf { it.isNotBlank() } ?: "Address") + " · offline", pos)
                                }
                            }
                        }
                    }
                    out += hits.sortedBy { it.first }.take(8).map { it.second }
                }
            }
        } catch (e: Throwable) { }
        return out
    }

    // ---- real routing (stage 2) ----
    private var engine: com.valhalla.valhalla.Valhalla? = null
    private var engineStamp = ""

    /** One engine, kept open and reused. It is rebuilt only if the pack file changes. */
    @Synchronized
    private fun engine(d: File): com.valhalla.valhalla.Valhalla {
        val tf = File(d, "tiles.tar")
        val stamp = tf.path + "@" + tf.lastModified()
        val cur = engine
        if (cur != null && stamp == engineStamp) return cur
        try { cur?.close() } catch (e: Exception) { }
        val e = com.valhalla.valhalla.Valhalla(deviceConfig(d))
        engine = e; engineStamp = stamp
        return e
    }

    private fun decode6(s: String): List<LL> {
        val out = ArrayList<LL>()
        var i = 0; var lat = 0; var lon = 0
        while (i < s.length) {
            var sh = 0; var r = 0; var b: Int
            do { b = s[i++].code - 63; r = r or ((b and 31) shl sh); sh += 5 } while (b >= 32)
            lat += if (r and 1 != 0) (r shr 1).inv() else r shr 1
            sh = 0; r = 0
            do { b = s[i++].code - 63; r = r or ((b and 31) shl sh); sh += 5 } while (b >= 32)
            lon += if (r and 1 != 0) (r shr 1).inv() else r shr 1
            out += LL(lat / 1e6, lon / 1e6)
        }
        return out
    }

    private fun manText(type: Int, exits: Int): String? = when (type) {
        1, 2, 3 -> "DEPART"
        4 -> "ARRIVE"; 5 -> "ARRIVE_RIGHT"; 6 -> "ARRIVE_LEFT"
        7, 8 -> "FOLLOW"
        9 -> "BEAR_RIGHT"; 10 -> "TURN_RIGHT"; 11 -> "SHARP_RIGHT"
        12, 13 -> "MAKE_UTURN"
        14 -> "SHARP_LEFT"; 15 -> "TURN_LEFT"; 16 -> "BEAR_LEFT"
        17, 22 -> "STRAIGHT"
        18, 20 -> "MOTORWAY_EXIT_RIGHT"; 19, 21 -> "MOTORWAY_EXIT_LEFT"
        23 -> "KEEP_RIGHT"; 24 -> "KEEP_LEFT"
        25, 37, 38 -> "FOLLOW"
        26 -> when { exits <= 1 -> "ROUNDABOUT_LEFT"; exits == 2 -> "ROUNDABOUT_CROSS"; exits == 3 -> "ROUNDABOUT_RIGHT"; else -> "ROUNDABOUT_BACK" }
        28 -> "TAKE_FERRY"
        else -> null // 0 none and 27 roundabout exit (the roundabout step already says which exit)
    }

    /** Areas the offline route must keep out of, as closed rings (clean air zones, a road you asked to avoid). */
    @Volatile var exclude: List<List<LL>> = emptyList()

    /** Routes on the device with no network. Blocking: call it off the main thread. */
    @Synchronized
    fun routes(ctx: Context, from: LL, to: LL, via: List<LL> = emptyList(), headingDeg: Double = -1.0): List<Route> {
        // the area that covers the whole trip (the start and the end): the normal case
        val whole = areaFor(ctx, *(listOf(from, to) + via).toTypedArray())
        if (whole != null) return listOf(routeIn(packDir(ctx, whole), from, to, via, headingDeg))
        val a = areaFor(ctx, from); val b = areaFor(ctx, to)
        // the start and the end are in two different downloaded areas: a route in each, joined at the border
        if (a != null && b != null && a != b && via.isEmpty()) return listOf(acrossTwo(ctx, a, b, from, to, headingDeg))
        return listOf(routeIn(a?.let { packDir(ctx, it) } ?: b?.let { packDir(ctx, it) } ?: dir(ctx), from, to, via, headingDeg))
    }

    private fun lerp(a: LL, b: LL, t: Double) = LL(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)

    /** The point [off] metres along [r], with the index of the route point just before it. */
    private fun pointOn(r: Route, off: Double): Pair<LL, Int> {
        val c = r.cum
        var i = 0
        while (i < c.size - 2 && c[i + 1] < off) i++
        val seg = c[i + 1] - c[i]
        val t = if (seg > 0) ((off - c[i]) / seg).coerceIn(0.0, 1.0) else 0.0
        return lerp(r.pts[i], r.pts[i + 1], t) to i
    }

    /**
     * A trip from area [a] into area [b] with no signal. The two packs are each cut at their border, so neither can route the whole
     * way. Instead: a route in [a] towards the destination as far as [a]'s roads go (the downloaded areas overlap a little at the
     * border, so that point is on a road [b] has too), then a route in [b] from there on, joined into one route with one set of
     * directions. If [b] doesn't have the road at that point, the join is tried a little further back.
     */
    private fun acrossTwo(ctx: Context, a: String, b: String, from: LL, to: LL, headingDeg: Double): Route {
        val da = packDir(ctx, a); val db = packDir(ctx, b)
        val ba = bounds(ctx, a); val bb = bounds(ctx, b)
        fun inBox(x: DoubleArray?, p: LL) = x != null && p.lat >= x[0] && p.lat <= x[2] && p.lon >= x[1] && p.lon <= x[3]
        // along the straight line: the last point still inside [a]'s box, and the first inside [b]'s (the border is between)
        var tExit = 1.0; var tEntry = 0.0
        for (k in 100 downTo 0) { if (inBox(ba, lerp(from, to, k / 100.0))) { tExit = k / 100.0; break } }
        for (k in 0..100) { if (inBox(bb, lerp(from, to, k / 100.0))) { tEntry = k / 100.0; break } }
        val dist = Geo.dist(from, to)
        val stepT = (10_000.0 / maxOf(dist, 1.0)).coerceIn(0.01, 0.2)
        // aim for the destination; [a] snaps the end to its nearest road, which is near its edge facing the destination. Points far
        // beyond [a]'s roads can't be snapped at all, so step back along the line until one can
        var ra: Route? = null
        var t = tExit
        val tStop = minOf(tEntry, tExit) - 0.1
        while (ra == null && t > tStop && t > 0.0) {
            ra = try { routeIn(da, from, lerp(from, to, t), emptyList(), headingDeg) } catch (e: Exception) { null }
            t -= stepT
        }
        if (ra == null) throw IllegalStateException("No offline route from here towards the border of the downloaded area")
        val lenA = ra.cum.last()
        for (back in doubleArrayOf(0.0, 1000.0, 3000.0, 6000.0, 12_000.0)) {
            val cut = lenA - back
            if (cut < lenA * 0.3) break
            val (pt, idx) = pointOn(ra, cut)
            val rb = try { routeIn(db, pt, to, emptyList(), -1.0) } catch (e: Exception) { null } ?: continue
            // the second area has to start on the same road (not snap to one somewhere else)
            if (Geo.dist(rb.pts.first(), pt) > 60.0) continue
            return join(ra, cut, pt, idx, rb)
        }
        throw IllegalStateException("Couldn't join the two downloaded areas at the border with no signal")
    }

    private fun join(ra: Route, cut: Double, pt: LL, idx: Int, rb: Route): Route {
        val pts = ArrayList<LL>(ra.pts.subList(0, idx + 1))
        if (Geo.dist(pts.last(), pt) > 0.5) pts += pt
        val joinAt = pts.size - 1
        for (p in rb.pts) if (Geo.dist(pts.last(), p) > 0.5) pts += p
        val cum = DoubleArray(pts.size)
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + Geo.dist(pts[i - 1], pts[i])
        val base = cum[joinAt]
        val steps = ArrayList<Step>()
        for (st in ra.steps) if (st.off < cut - 1.0 && !st.maneuver.startsWith("ARRIVE")) steps += st
        for (st in rb.steps) if (st.maneuver != "DEPART") steps += st.copy(off = st.off + base)
        val lenA = ra.cum.last()
        val time = (if (lenA > 0) ra.time * (cut / lenA) else 0.0) + rb.time
        return Route(pts, cum, steps, cum.last(), time, 0.0)
    }

    /** A route inside one downloaded area (the folder [pd]). */
    private fun routeIn(pd: File, from: LL, to: LL, via: List<LL>, headingDeg: Double): Route {
        if (!installedIn(pd)) throw IllegalStateException("No offline pack installed")
        val locs = org.json.JSONArray()
        locs.put(JSONObject().put("lat", from.lat).put("lon", from.lon).apply { if (headingDeg >= 0) { put("heading", headingDeg.toInt()); put("heading_tolerance", 60) } })
        for (v in via) locs.put(JSONObject().put("lat", v.lat).put("lon", v.lon).put("type", "through"))
        locs.put(JSONObject().put("lat", to.lat).put("lon", to.lon))
        val req = JSONObject().put("locations", locs).put("costing", "auto")
            .put("directions_options", JSONObject().put("units", "kilometers").put("language", "en-GB"))
        val ex = exclude
        if (ex.isNotEmpty()) {
            val polys = org.json.JSONArray()
            for (ring in ex) { val r = org.json.JSONArray(); for (p in ring) r.put(org.json.JSONArray().put(p.lon).put(p.lat)); polys.put(r) }
            req.put("exclude_polygons", polys)
        }
        var raw = engine(pd).routeRaw(req.toString())
        var o = JSONObject(raw)
        if (ex.isNotEmpty() && o.has("error") && !o.has("trip")) {
            // no way round the areas: route normally instead
            req.remove("exclude_polygons")
            raw = engine(pd).routeRaw(req.toString()); o = JSONObject(raw)
        }
        if (o.has("error") && !o.has("trip")) throw IllegalStateException("No offline route here (${o.optString("error")}). Is it inside the downloaded area?")
        val trip = o.getJSONObject("trip")
        val legs = trip.getJSONArray("legs")
        val pts = ArrayList<LL>()
        data class M(val base: Int, val m: JSONObject)
        val mans = ArrayList<M>()
        for (li in 0 until legs.length()) {
            val leg = legs.getJSONObject(li)
            val shape = decode6(leg.getString("shape"))
            val base = if (pts.isEmpty()) 0 else pts.size - 1
            if (pts.isEmpty()) pts.addAll(shape) else pts.addAll(shape.drop(1))
            val ms = leg.optJSONArray("maneuvers")
            if (ms != null) for (i in 0 until ms.length()) mans += M(base, ms.getJSONObject(i))
        }
        if (pts.size < 2) throw IllegalStateException("No offline route found")
        val cum = DoubleArray(pts.size)
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + Geo.dist(pts[i - 1], pts[i])
        val steps = ArrayList<Step>()
        for ((base, m) in mans) {
            val exits = m.optInt("roundabout_exit_count", 0)
            val man = manText(m.optInt("type", 0), exits) ?: continue
            val gi = (base + m.optInt("begin_shape_index", 0)).coerceIn(0, pts.size - 1)
            val names = m.optJSONArray("street_names")
            val street = if (names != null && names.length() > 0) names.optString(0) else ""
            val sign = m.optJSONObject("sign")
            val exitNo = sign?.optJSONArray("exit_number_elements")?.optJSONObject(0)?.optString("text").orEmpty()
            val toward = sign?.optJSONArray("exit_toward_elements")?.optJSONObject(0)?.optString("text").orEmpty()
            steps += Step(cum[gi], man, street, m.optString("instruction"), if (man.startsWith("ROUNDABOUT")) exits else 0, exitNo, toward)
        }
        val sum = trip.optJSONObject("summary")
        val len = (sum?.optDouble("length", 0.0) ?: 0.0) * 1000.0
        return Route(pts, cum, steps, if (len > 0) len else cum.last(), sum?.optDouble("time", 0.0) ?: 0.0, 0.0)
    }
}
