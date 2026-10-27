package com.headunit.launcher

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Update check for the shareable app: looks at the "app" release on GitHub (published by the build's "publish" option) and,
 * when it holds a newer build, downloads the APK and hands it to Android's installer. The personal build has no update repo,
 * so it never checks (its APK is never published).
 */
object Updater {
    class Update(val version: String, val build: Int, val url: String, val sha256: String)
    private const val MAX_RELEASE_JSON_BYTES = 1024 * 1024
    private const val MAX_CHECKSUM_BYTES = 1024
    private const val DOWNLOAD_POLL_INTERVAL_MS = 700L
    // DownloadManager can resume after connectivity drops, but should never leave this call waiting forever.
    private const val MAX_DOWNLOAD_WAIT_MS = 30L * 60L * 1000L
    private const val MAX_APK_BYTES = 500L * 1024L * 1024L

    val enabled: Boolean get() = BuildConfig.UPDATE_REPO.isNotBlank()

    /** The build number of this app (each GitHub build counts up; versionCode is 100 + build). */
    val thisBuild: Int get() = BuildConfig.VERSION_CODE - 100

    /** Asks GitHub for the published app. Returns it when it's newer than this one, null when it isn't (or can't be checked). */
    fun check(): Update? {
        if (!enabled) return null
        val c = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/tags/app").openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "HorizonNav/${BuildConfig.VERSION_NAME}")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode !in 200..299) return null
            // GitHub release metadata should be small; cap it to avoid unbounded memory use on malformed responses.
            val bytes = c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var total = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_RELEASE_JSON_BYTES) throw java.io.IOException("Release metadata is too large")
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            val j = JSONObject(String(bytes, Charsets.UTF_8))
            val assets = j.optJSONArray("assets") ?: return null
            var url = ""
            var checksumUrl = ""
            var metadataUrl = ""
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                when (a.optString("name")) {
                    BuildConfig.UPDATE_APK -> url = a.optString("browser_download_url")
                    BuildConfig.UPDATE_APK + ".sha256" -> checksumUrl = a.optString("browser_download_url")
                    "version.json" -> metadataUrl = a.optString("browser_download_url")
                }
            }
            if (url.isBlank() || checksumUrl.isBlank()) return null
            if (!UpdaterPolicy.isHttpsUrl(url) || !UpdaterPolicy.isHttpsUrl(checksumUrl)) return null

            // Prefer machine-readable metadata, but don't hide an update solely because version.json
            // is missing or malformed on the fixed release. The publish workflow also writes the
            // build into the release title (for example, "Horizon Nav (1.0.123)").
            var version = ""
            var build = -1
            if (metadataUrl.isNotBlank() && UpdaterPolicy.isHttpsUrl(metadataUrl)) {
                try {
                    val metadata = JSONObject(readSmallText(metadataUrl))
                    val candidateVersion = metadata.optString("version")
                    val candidateBuild = metadata.optInt("build", -1)
                    val candidateVersionCode = metadata.optInt("versionCode", -1)
                    if (UpdaterPolicy.isValidReleaseMetadata(candidateVersion, candidateBuild, candidateVersionCode)) {
                        version = candidateVersion
                        build = candidateBuild
                    }
                } catch (_: Exception) {
                    // Fall back to the version embedded in the release title below.
                }
            }
            if (build < 0) {
                val releaseText = j.optString("name") + "\n" + j.optString("body")
                val match = Regex("1\\.0\\.(\\d+)").find(releaseText)
                build = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
                if (build >= 0) version = "1.0.$build"
            }
            if (build < 0 || version != "1.0.$build" || build <= thisBuild) return null
            // Only offer updates that include the checksum generated by the release workflow.
            val checksum = UpdaterPolicy.parseSha256(readSmallText(checksumUrl)) ?: return null
            return Update(version, build, url, checksum)
        } finally { c.disconnect() }
    }

    private fun readSmallText(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "HorizonNav/${BuildConfig.VERSION_NAME}")
        try {
            if (connection.responseCode !in 200..299) throw java.io.IOException("Checksum download failed")
            val bytes = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(256)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > MAX_CHECKSUM_BYTES) throw java.io.IOException("Checksum file is too large")
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            return String(bytes, Charsets.UTF_8)
        } finally { connection.disconnect() }
    }

    /** True when Android will let this app open the installer (on Android 8 and later it's a per-app setting). */
    fun canInstall(ctx: Context): Boolean = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** Opens the setting where you allow Horizon Nav to install updates. */
    fun openInstallSetting(ctx: Context) {
        try {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Opens the download in the browser instead (for head units without Android's download service). */
    fun openInBrowser(ctx: Context, u: Update) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Downloads the new APK with Android's download service (it carries on in the background and copes with signal drops),
     * waiting here until it's done, then opens the installer. [progress] gets short status lines. Throws when it can't.
     */
    fun downloadAndInstall(ctx: Context, u: Update, progress: (String) -> Unit) {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: throw IllegalStateException("No download service")
        val dir = ctx.getExternalFilesDir(null) ?: throw IllegalStateException("No storage")
        require(UpdaterPolicy.isHttpsUrl(u.url)) { "Update URL must use HTTPS" }
        require(UpdaterPolicy.parseSha256(u.sha256) != null) { "Update checksum is invalid" }
        val name = "HorizonNav-${u.version}.apk"
        val req = DownloadManager.Request(Uri.parse(u.url))
            .setTitle("Horizon Nav ${u.version}")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(ctx, null, name)
        val id = dm.enqueue(req)
        val startedAt = android.os.SystemClock.elapsedRealtime()
        progress("Downloading…")
        try {
          while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Update download interrupted")
            if (android.os.SystemClock.elapsedRealtime() - startedAt > MAX_DOWNLOAD_WAIT_MS) {
                dm.remove(id)
                throw IllegalStateException("Download timed out; please try again")
            }
            Thread.sleep(DOWNLOAD_POLL_INTERVAL_MS)
            dm.query(DownloadManager.Query().setFilterById(id)).use { cur ->
                if (cur == null || !cur.moveToFirst()) throw IllegalStateException("Download was cancelled")
                val status = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val done = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val uri = dm.getUriForDownloadedFile(id) ?: throw IllegalStateException("Download missing")
                        progress("Verifying download…")
                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                        var bytesRead = 0L
                        ctx.contentResolver.openInputStream(uri)?.use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                bytesRead += n
                                if (bytesRead > MAX_APK_BYTES) throw IllegalStateException("Downloaded APK is unexpectedly large")
                                digest.update(buffer, 0, n)
                            }
                        } ?: throw IllegalStateException("Could not read downloaded APK")
                        val actualSha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                        if (!actualSha256.equals(u.sha256, ignoreCase = true)) {
                            dm.remove(id)
                            throw IllegalStateException("APK checksum verification failed; download removed")
                        }
                        // Keep the previous APK until this download has passed verification.
                        dir.listFiles()?.filter { it.name.startsWith("HorizonNav-") && it.name.endsWith(".apk") && it.name != name }?.forEach { it.delete() }
                        progress("Opening the installer…")
                        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(i)
                        return
                    }
                    DownloadManager.STATUS_FAILED -> { dm.remove(id); throw IllegalStateException("Download failed") }
                    else -> if (total > 0) progress("Downloading… ${done * 100 / total}%")
                }
            }
          }
        } catch (e: InterruptedException) {
            dm.remove(id)
            Thread.currentThread().interrupt()
            throw IllegalStateException("Update download cancelled", e)
        } catch (e: Exception) {
            // Avoid leaving partial/failed downloads behind when querying or verification fails.
            dm.remove(id)
            throw e
        }
    }

    /** The file the last update was saved as (cleared once a newer app is running). */
    fun cleanUp(ctx: Context) {
        val dir = ctx.getExternalFilesDir(null) ?: return
        dir.listFiles()?.filter { it.name.startsWith("HorizonNav-") && it.name.endsWith(".apk") }?.forEach { f ->
            val b = Regex("1\\.0\\.(\\d+)").find(f.name)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
            if (b <= thisBuild) f.delete()
        }
    }
}
