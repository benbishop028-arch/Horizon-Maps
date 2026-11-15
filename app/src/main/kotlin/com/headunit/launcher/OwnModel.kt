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

    /** The saved file's version, to tell when it has changed. */
    fun stamp(prefs: Prefs): String = prefs.s(PreferenceKeys.OWN_MODEL, "").substringBefore('|')

    /**
     * The model's longest side, from the corners glTF stores for each mesh (no geometry is read), or null when the file isn't a
     * glTF 2.0 binary. Node scaling isn't counted: most exported models have none at the top.
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
            val lo = doubleArrayOf(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)
            val hi = doubleArrayOf(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE)
            var any = false
            for (m in 0 until meshes.length()) {
                val prims = meshes.optJSONObject(m)?.optJSONArray("primitives") ?: continue
                for (k in 0 until prims.length()) {
                    val pos = prims.optJSONObject(k)?.optJSONObject("attributes")?.optInt("POSITION", -1) ?: -1
                    val a = acc.optJSONObject(pos) ?: continue
                    val mn = a.optJSONArray("min") ?: continue
                    val mx = a.optJSONArray("max") ?: continue
                    for (i in 0 until 3) { lo[i] = minOf(lo[i], mn.optDouble(i, 0.0)); hi[i] = maxOf(hi[i], mx.optDouble(i, 0.0)) }
                    any = true
                }
            }
            if (!any) return null
            val side = maxOf(hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2])
            return if (side > 1e-6 && side.isFinite()) side else null
        }
    }
}
