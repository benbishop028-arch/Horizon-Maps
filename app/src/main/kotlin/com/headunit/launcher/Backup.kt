package com.headunit.launcher

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The backup file: settings and saved places (as before), and now the GPX tracks too (bijindas). Tracks go under one extra entry,
 * "__gpx", which older versions of the app don't know; everything else is the same flat list as before, so an old backup still
 * restores here. Also the automatic backup: once a day, to a folder you picked, either replacing the one file or keeping a week.
 */
object Backup {
    private const val GPX = "__gpx"
    /** Your saved routes (My routes, #321), the same way as the tracks. */
    private const val MINE = "__myroutes"
    /** Settings that belong to this device, never carried over: the automatic backup's folder (its permission is for this unit) and when it ran. */
    private val LOCAL = setOf(PreferenceKeys.AUTO_BACKUP_DIR, PreferenceKeys.AUTO_BACKUP_AT)

    /** The whole backup as text. (Off the main thread: with tracks it can be a couple of megabytes.) */
    fun export(ctx: Context, prefs: Prefs): String {
        val o = JSONObject(prefs.exportJson(prefs.b(PreferenceKeys.BACKUP_KEYS, false)))
        for (k in LOCAL) o.remove(k)
        val arr = JSONArray()
        val dir = File(ctx.filesDir, "gpx")
        (dir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()).forEach { f ->
            try { arr.put(JSONObject().put("id", f.nameWithoutExtension).put("track", JSONObject(f.readText()))) } catch (e: Exception) { }
        }
        if (arr.length() > 0) o.put(GPX, arr)
        val mine = JSONArray()
        (File(ctx.filesDir, "myroutes").listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()).forEach { f ->
            try { mine.put(JSONObject().put("id", f.nameWithoutExtension).put("route", JSONObject(f.readText()))) } catch (e: Exception) { }
        }
        if (mine.length() > 0) o.put(MINE, mine)
        return o.toString(1)
    }

    /** Restores a backup: the tracks first (each one checked), then the settings. Returns how many settings and tracks came back. */
    fun restore(ctx: Context, prefs: Prefs, text: String): Pair<Int, Int> {
        require(text.isNotBlank() && text.length <= 24 * 1024 * 1024) { "Backup is empty or too large" }
        val o = JSONObject(text)
        val tracks = o.optJSONArray(GPX)
        o.remove(GPX)
        val mine = o.optJSONArray(MINE)
        o.remove(MINE)
        for (k in LOCAL) o.remove(k)
        // the settings are checked in full before any are changed (Prefs.importJson); tracks only after that check has passed
        val n = prefs.importJson(o.toString())
        var t = 0
        if (tracks != null) {
            val dir = File(ctx.filesDir, "gpx").apply { mkdirs() }
            for (i in 0 until tracks.length()) {
                val e = tracks.optJSONObject(i) ?: continue
                val id = e.optString("id")
                val tr = e.optJSONObject("track") ?: continue
                // (a name that can only be a file in the tracks folder, and a track that has points)
                if (!Regex("^[A-Za-z0-9_-]{1,80}$").matches(id) || tr.optJSONArray("pts") == null) continue
                try { File(dir, "$id.json").writeText(tr.toString()); t++ } catch (x: Exception) { }
            }
            if (t > 0) GpxStore.reload(ctx)
        }
        if (mine != null) {
            val dir = File(ctx.filesDir, "myroutes").apply { mkdirs() }
            var m = 0
            for (i in 0 until mine.length()) {
                val e = mine.optJSONObject(i) ?: continue
                val id = e.optString("id")
                val r = e.optJSONObject("route") ?: continue
                if (!Regex("^[A-Za-z0-9_-]{1,80}$").matches(id) || r.optJSONArray("pts") == null) continue
                try { File(dir, "$id.json").writeText(r.toString()); m++ } catch (x: Exception) { }
            }
            if (m > 0) MyRoutes.reload(ctx)
        }
        return n to t
    }

    // ------------------------------------------------------------------ automatic backup

    /** The folder's name to show (the last part of its path), or "" if none. */
    fun folderName(dir: String): String = try {
        DocumentsContract.getTreeDocumentId(Uri.parse(dir)).substringAfter(':').substringAfterLast('/').ifBlank { "Internal storage" }
    } catch (e: Exception) { "" }

    /** Once a day (when the app is opened), on a thread of its own. [force]: now, whenever it last ran (the folder was just picked). */
    fun maybeRun(ctx: Context, prefs: Prefs, force: Boolean = false) {
        val dir = prefs.s(PreferenceKeys.AUTO_BACKUP_DIR, "")
        if (dir.isBlank()) return
        val last = prefs.s(PreferenceKeys.AUTO_BACKUP_AT, "0").toLongOrNull() ?: 0L
        if (!force && System.currentTimeMillis() - last < 20 * 3600_000L) return
        val app = ctx.applicationContext
        Thread {
            val ok = try { write(app, prefs, Uri.parse(dir), prefs.s(PreferenceKeys.AUTO_BACKUP_KEEP, "one") == "week") } catch (e: Throwable) { false }
            // (a failure isn't retried until tomorrow either way; the time is only recorded when it worked, so Settings can say so)
            if (ok) prefs.put(PreferenceKeys.AUTO_BACKUP_AT, System.currentTimeMillis().toString())
            else prefs.put(PreferenceKeys.AUTO_BACKUP_AT + "fail", System.currentTimeMillis().toString())
        }.apply { isDaemon = true; name = "auto-backup" }.start()
    }

    private fun write(ctx: Context, prefs: Prefs, tree: Uri, week: Boolean): Boolean {
        val cr = ctx.contentResolver
        val text = export(ctx, prefs).toByteArray()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        // what's in the folder now: name to document
        val files = HashMap<String, String>()
        cr.query(kids, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cur ->
            while (cur.moveToNext()) files[cur.getString(1) ?: continue] = cur.getString(0) ?: continue
        }
        val name = if (week) "horizon-nav-backup-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".json"
            else "horizon-nav-backup.json"
        // leftovers from a write that was cut short before
        files.filterKeys { it.startsWith("horizon-nav-backup") && it.contains(".tmp") }.values.forEach { id ->
            try { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, id)) } catch (e: Exception) { }
        }
        // written to a new file first, then the old one of that name removed and the new one renamed: an interrupted write can
        // never leave a half-written backup in place of the last good one
        val tmp = DocumentsContract.createDocument(cr, parent, "application/octet-stream", "horizon-nav-backup.tmp") ?: return false
        try { cr.openOutputStream(tmp, "w")?.use { it.write(text) } ?: throw java.io.IOException("no stream") }
        catch (e: Exception) { try { DocumentsContract.deleteDocument(cr, tmp) } catch (x: Exception) { }; return false }
        files[name]?.let { old -> try { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, old)) } catch (e: Exception) { } }
        val renamed = try { DocumentsContract.renameDocument(cr, tmp, name) != null } catch (e: Exception) { false }
        if (!renamed) {
            // (a folder that can't rename files: written straight to the right name instead)
            try { DocumentsContract.deleteDocument(cr, tmp) } catch (e: Exception) { }
            val doc = DocumentsContract.createDocument(cr, parent, "application/json", name) ?: return false
            cr.openOutputStream(doc, "w")?.use { it.write(text) } ?: return false
        }
        if (week) {
            // a week of them: the oldest beyond seven go
            files.keys.filter { it.startsWith("horizon-nav-backup-") && it.endsWith(".json") && it != name }.sortedDescending().drop(6).forEach { n ->
                try { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, files[n]!!)) } catch (e: Exception) { }
            }
        }
        return true
    }
}
