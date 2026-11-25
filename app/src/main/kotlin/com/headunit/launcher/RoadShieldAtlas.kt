package com.headunit.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Small, disk-backed cache for TomTom's infrequently changing road-shield sprite atlas. */
object RoadShieldAtlas {
    private val memory = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 32
    }

    fun cached(atlas: String, reference: String): Bitmap? = synchronized(memory) { memory["$atlas|$reference"] }

    suspend fun load(context: Context, atlas: String, reference: String): Bitmap? = withContext(Dispatchers.IO) {
        if (!atlas.startsWith("https://") || reference.isBlank()) return@withContext null
        val key = "$atlas|$reference"
        synchronized(memory) { memory[key] }?.let { return@withContext it }
        try {
            val dir = File(context.cacheDir, "road_shields").apply { mkdirs() }
            val prefix = Integer.toHexString(atlas.hashCode())
            val jsonFile = File(dir, "$prefix.json")
            val pngFile = File(dir, "$prefix.png")
            fun download(url: String, file: File, maxBytes: Int) {
                if (file.exists() && file.length() in 1..maxBytes.toLong()) return
                val c = URL(url).also { Net.check() }.openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 12000
                try {
                    if (c.responseCode !in 200..299) throw java.io.IOException("Road shield atlas unavailable")
                    val bytes = c.inputStream.use { it.readBytes() }
                    if (bytes.isEmpty() || bytes.size > maxBytes) throw java.io.IOException("Road shield atlas too large")
                    val tmp = File(file.parentFile, file.name + ".tmp")
                    tmp.writeBytes(bytes)
                    if (!tmp.renameTo(file)) { tmp.delete(); throw java.io.IOException("Couldn't cache road shield atlas") }
                } finally { c.disconnect() }
            }
            val base = atlas.trimEnd('/') + "/"
            download(base + "sprite.json", jsonFile, 2_000_000)
            val meta = JSONObject(jsonFile.readText()).optJSONObject(reference) ?: return@withContext null
            val x = meta.optInt("x", -1); val y = meta.optInt("y", -1)
            val w = meta.optInt("width", 0); val h = meta.optInt("height", 0)
            if (x < 0 || y < 0 || w <= 0 || h <= 0) return@withContext null
            download(base + "sprite.png", pngFile, 12_000_000)
            val atlasBitmap = BitmapFactory.decodeFile(pngFile.path) ?: return@withContext null
            if (x + w > atlasBitmap.width || y + h > atlasBitmap.height) return@withContext null
            val cropped = Bitmap.createBitmap(atlasBitmap, x, y, w, h)
            atlasBitmap.recycle()
            synchronized(memory) { memory[key] = cropped }
            cropped
        } catch (_: Exception) { null }
    }
}
