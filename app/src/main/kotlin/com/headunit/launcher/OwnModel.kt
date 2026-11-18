package com.headunit.launcher

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File

/**
 * Your own 3D model for the arrow (Settings > Your arrow > Real 3D model > Your model): a .glb file, checked when it's picked
 * (a real glTF 2.0 binary, no bigger than 8 MB), copied into the app's own storage and measured, so a model built at any scale
 * comes out about the size of the built-in car before the size slider is applied.
 */
object OwnModel {
    const val MAX_BYTES = 8L * 1024 * 1024
    /** Over this a warning is shown: big models can make the map stutter on slower head units. */
    const val HEAVY_BYTES = 3L * 1024 * 1024
    private const val PREFIX = "own_model_"

    /** The saved model, or null when there's none. */
    fun file(ctx: Context): File? = ctx.filesDir.listFiles()?.filter { it.name.startsWith(PREFIX) && it.name.endsWith(".glb") }?.maxByOrNull { it.lastModified() }

    /** "file://…" for the map, or null. */
    fun uri(ctx: Context): String? = file(ctx)?.let { "file://" + it.absolutePath }

    /**
     * Checks and saves the picked file. Returns null when it worked, or a short reason it didn't. Runs off the main thread.
     * Each save gets a new file name, so the map never shows an old copy it kept.
     */
    fun save(ctx: Context, src: Uri, prefs: Prefs): String? {
        val cr = ctx.contentResolver
        val name = try {
            cr.query(src, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) { null } ?: "model.glb"
        val tmp = File(ctx.filesDir, "own_model.tmp")
        var total = 0L
        try {
            (cr.openInputStream(src) ?: return "unreadable").use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) return "too big"
                        out.write(buf, 0, n)
                    }
                }
            }
            val extent = measure(tmp) ?: return "not a glb"
            val stamp = System.currentTimeMillis()
            val dest = File(ctx.filesDir, "$PREFIX$stamp.glb")
            if (!tmp.renameTo(dest)) { tmp.copyTo(dest, true) }
            // the older copies go
            ctx.filesDir.listFiles()?.filter { it.name.startsWith(PREFIX) && it.name.endsWith(".glb") && it != dest }?.forEach { it.delete() }
            prefs.put(PreferenceKeys.OWN_MODEL, "$stamp|$total|$extent|$name")
            prefs.put(SIDE_KEY, "$stamp:$extent")
            return null
        } catch (e: Exception) {
            return e.message ?: "failed"
        } finally {
            try { tmp.delete() } catch (e: Exception) { }
        }
    }

    /** What was saved: (shown name, size in bytes, longest side in model units). */
    fun info(prefs: Prefs): Triple<String, Long, Double>? {
        val p = prefs.s(PreferenceKeys.OWN_MODEL, "").split('|')
        if (p.size < 4) return null
        return Triple(p.drop(3).joinToString("|"), p[1].toLongOrNull() ?: 0L, p[2].toDoubleOrNull() ?: 4.4)
    }

    /** The measured size, by the way the model is really drawn (with its nodes' scaling): "<stamp>:<longest side>". */
    private const val SIDE_KEY = "ownmodelside2"

    /**
     * The model's longest side as the map will draw it. Models measured before the nodes' scaling was counted are measured again
     * once (Matattack99, #186: a model exported with a 1/100 scale on its top node came out a hundredth of the size, too small
     * to see).
     */
    fun side(ctx: Context, prefs: Prefs): Double {
        val stamp = stamp(prefs)
        val v = prefs.s(SIDE_KEY, "")
        if (stamp.isNotBlank() && v.substringBefore(':') == stamp) v.substringAfter(':').toDoubleOrNull()?.let { return it }
        val s = try { file(ctx)?.let { measure(it) } } catch (e: Exception) { null } ?: info(prefs)?.third ?: 4.4
        if (stamp.isNotBlank()) prefs.put(SIDE_KEY, "$stamp:$s")
        return s
    }

    /** The saved file's version, to tell when it has changed. */
    fun stamp(prefs: Prefs): String = prefs.s(PreferenceKeys.OWN_MODEL, "").substringBefore('|')

    /**
     * The model's longest side, from the corners glTF stores for each mesh (no geometry is read), placed, turned and scaled by
     * the model's nodes as the map draws them; null when the file isn't a glTF 2.0 binary.
     */
    private fun measure(f: File): Double? {
        java.io.RandomAccessFile(f, "r").use { raf ->
            if (raf.length() < 20) return null
            fun le32(): Long {
                val b = ByteArray(4); raf.readFully(b)
                return (b[0].toLong() and 0xff) or ((b[1].toLong() and 0xff) shl 8) or ((b[2].toLong() and 0xff) shl 16) or ((b[3].toLong() and 0xff) shl 24)
            }
            if (le32() != 0x46546C67L) return null          // "glTF"
            if (le32() != 2L) return null                   // version 2
            le32()                                           // total length
            val jsonLen = le32()
            if (le32() != 0x4E4F534AL) return null          // "JSON"
            if (jsonLen <= 0 || jsonLen > raf.length()) return null
            val jb = ByteArray(jsonLen.toInt()); raf.readFully(jb)
            val j = JSONObject(String(jb, Charsets.UTF_8))
            val acc = j.optJSONArray("accessors") ?: return null
            val meshes = j.optJSONArray("meshes") ?: return null
            val nodes = j.optJSONArray("nodes")
            val lo = doubleArrayOf(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)
            val hi = doubleArrayOf(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE)
            var any = false
            // a mesh's corners, moved by [m] (the node's place, turn and scale in the whole model)
            fun addMesh(mi: Int, m: DoubleArray) {
                val prims = meshes.optJSONObject(mi)?.optJSONArray("primitives") ?: return
                for (k in 0 until prims.length()) {
                    val pos = prims.optJSONObject(k)?.optJSONObject("attributes")?.optInt("POSITION", -1) ?: -1
                    val a = acc.optJSONObject(pos) ?: continue
                    val mn = a.optJSONArray("min") ?: continue
                    val mx = a.optJSONArray("max") ?: continue
                    for (c in 0 until 8) {
                        val x = if (c and 1 == 0) mn.optDouble(0, 0.0) else mx.optDouble(0, 0.0)
                        val y = if (c and 2 == 0) mn.optDouble(1, 0.0) else mx.optDouble(1, 0.0)
                        val z = if (c and 4 == 0) mn.optDouble(2, 0.0) else mx.optDouble(2, 0.0)
                        val p = doubleArrayOf(m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13], m[2] * x + m[6] * y + m[10] * z + m[14])
                        for (i in 0 until 3) { lo[i] = minOf(lo[i], p[i]); hi[i] = maxOf(hi[i], p[i]) }
                    }
                    any = true
                }
            }
            // the scene's nodes, each with its parents' placing (glTF's node tree); with no nodes, the meshes as they are
            if (nodes != null && nodes.length() > 0) {
                fun walk(ni: Int, parent: DoubleArray, depth: Int) {
                    if (depth > 64) return
                    val n = nodes.optJSONObject(ni) ?: return
                    val m = mul(parent, local(n))
                    if (n.has("mesh")) addMesh(n.optInt("mesh"), m)
                    n.optJSONArray("children")?.let { ch -> for (i in 0 until ch.length()) walk(ch.optInt(i), m, depth + 1) }
                }
                val scenes = j.optJSONArray("scenes")
                val roots = scenes?.optJSONObject(j.optInt("scene", 0))?.optJSONArray("nodes")
                val id = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
                if (roots != null) for (i in 0 until roots.length()) walk(roots.optInt(i), id, 0)
                else {
                    // no scene listed: every node that isn't another's child
                    val child = HashSet<Int>()
                    for (i in 0 until nodes.length()) nodes.optJSONObject(i)?.optJSONArray("children")?.let { c -> for (k in 0 until c.length()) child += c.optInt(k) }
                    for (i in 0 until nodes.length()) if (i !in child) walk(i, id, 0)
                }
            }
            if (!any) {
                val id = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
                for (mi in 0 until meshes.length()) addMesh(mi, id)
            }
            if (!any) return null
            val side = maxOf(hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2])
            return if (side > 1e-6 && side.isFinite()) side else null
        }
    }

    /** A node's own placing as a 4×4 matrix (glTF's order: column by column): its "matrix", or translation × rotation × scale. */
    private fun local(n: JSONObject): DoubleArray {
        n.optJSONArray("matrix")?.let { a -> if (a.length() == 16) return DoubleArray(16) { a.optDouble(it, 0.0) } }
        val t = n.optJSONArray("translation"); val r = n.optJSONArray("rotation"); val sc = n.optJSONArray("scale")
        val tx = t?.optDouble(0, 0.0) ?: 0.0; val ty = t?.optDouble(1, 0.0) ?: 0.0; val tz = t?.optDouble(2, 0.0) ?: 0.0
        val x = r?.optDouble(0, 0.0) ?: 0.0; val y = r?.optDouble(1, 0.0) ?: 0.0; val z = r?.optDouble(2, 0.0) ?: 0.0; val w = r?.optDouble(3, 1.0) ?: 1.0
        val sx = sc?.optDouble(0, 1.0) ?: 1.0; val sy = sc?.optDouble(1, 1.0) ?: 1.0; val sz = sc?.optDouble(2, 1.0) ?: 1.0
        return doubleArrayOf(
            (1 - 2 * (y * y + z * z)) * sx, (2 * (x * y + z * w)) * sx, (2 * (x * z - y * w)) * sx, 0.0,
            (2 * (x * y - z * w)) * sy, (1 - 2 * (x * x + z * z)) * sy, (2 * (y * z + x * w)) * sy, 0.0,
            (2 * (x * z + y * w)) * sz, (2 * (y * z - x * w)) * sz, (1 - 2 * (x * x + y * y)) * sz, 0.0,
            tx, ty, tz, 1.0,
        )
    }

    private fun mul(a: DoubleArray, b: DoubleArray): DoubleArray = DoubleArray(16) { i ->
        val col = i / 4; val row = i % 4
        var s = 0.0
        for (k in 0 until 4) s += a[k * 4 + row] * b[col * 4 + k]
        s
    }
}
