package com.headunit.launcher

import android.content.Intent
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/** A destination handed over by another app: exact coordinates, or text to search for. */
class SharedTarget(val pos: LL?, val name: String, val query: String?)

/**
 * Reads destinations shared from other apps: geo: links, google.navigation: links, Google Maps web links
 * (including short maps.app.goo.gl links, which are followed to the full address) and plain shared text.
 */
object ExternalLinks {
    private val coordRe = Regex("(-?\\d{1,2}\\.\\d{3,})\\s*,\\s*(-?\\d{1,3}\\.\\d{3,})")
    private val placeRe = Regex("!3d(-?\\d+\\.\\d+)!4d(-?\\d+\\.\\d+)")
    private val atRe = Regex("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)")
    private val urlRe = Regex("https?://\\S+")

    /** Text or link carried by an intent, or null when it is not a destination. */
    fun textOf(i: Intent?): String? {
        if (i == null) return null
        return when (i.action) {
            Intent.ACTION_VIEW -> i.dataString
            Intent.ACTION_SEND -> listOfNotNull(i.getStringExtra(Intent.EXTRA_SUBJECT), i.getStringExtra(Intent.EXTRA_TEXT)).joinToString("\n").ifBlank { null }
            else -> null
        }
    }

    private fun dec(s: String) = try { URLDecoder.decode(s, "UTF-8") } catch (e: Exception) { s }

    private fun param(u: String, name: String): String? {
        val q = u.substringAfter('?', "").substringBefore('#')
        for (part in q.split('&')) {
            val k = part.substringBefore('=')
            if (k.equals(name, true)) return dec(part.substringAfter('=', "")).ifBlank { null }
        }
        return null
    }

    private fun ll(s: String?): LL? {
        val m = coordRe.find(s ?: return null) ?: return null
        val a = m.groupValues[1].toDoubleOrNull() ?: return null
        val b = m.groupValues[2].toDoubleOrNull() ?: return null
        if (a !in -90.0..90.0 || b !in -180.0..180.0 || (a == 0.0 && b == 0.0)) return null
        return LL(a, b)
    }

    /** Follows a short link's redirects to the full Google Maps address (blocking, needs signal). */
    private fun expand(url: String): String {
        var u = url
        repeat(5) {
            val host = try { URL(u).host.lowercase() } catch (e: Exception) { return u }
            if (!(host.endsWith("goo.gl") || host == "g.co")) return u
            val c = URL(u).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) HorizonNav/1.0")
            val loc = c.getHeaderField("Location")
            c.disconnect()
            if (loc.isNullOrBlank()) return u
            u = if (loc.startsWith("http")) loc else URL(URL(u), loc).toString()
        }
        return u
    }

    /** Works out where to go (blocking: may follow a short link over the network). */
    fun parse(raw: String): SharedTarget? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val lower = text.lowercase()
        if (lower.startsWith("geo:")) {
            val q = param(text, "q")
            val label = q?.let { Regex("\\(([^)]*)\\)").find(it)?.groupValues?.get(1) }.orEmpty()
            val qp = ll(q)
            if (qp != null) return SharedTarget(qp, label.ifBlank { "Shared location" }, null)
            if (!q.isNullOrBlank()) return SharedTarget(null, q, q.replace(Regex("\\([^)]*\\)"), "").trim())
            val p = ll(text.removePrefix("geo:").substringBefore('?'))
            return if (p != null) SharedTarget(p, "Shared location", null) else null
        }
        if (lower.startsWith("google.navigation:")) {
            val q = param(text.replaceFirst(":", ":?"), "q") ?: text.substringAfter(':').substringAfter("q=").substringBefore('&').let { dec(it) }
            val p = ll(q)
            return if (p != null) SharedTarget(p, "Shared location", null) else if (q.isNotBlank()) SharedTarget(null, q, q) else null
        }
        // a link (on its own, or inside shared text such as "Place name\nAddress\nhttps://maps.app.goo.gl/…")
        val link = urlRe.find(text)?.value
        val before = if (link != null) text.substringBefore(link).trim().lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty() else ""
        if (link != null) {
            val u = try { expand(link) } catch (e: Exception) { link }
            val name = Regex("/place/([^/@]+)").find(u)?.groupValues?.get(1)?.let { dec(it).replace('+', ' ') }.orEmpty().ifBlank { before }
            placeRe.find(u)?.let { m -> return SharedTarget(LL(m.groupValues[1].toDouble(), m.groupValues[2].toDouble()), name.ifBlank { "Shared location" }, null) }
            for (k in listOf("daddr", "destination", "q", "query", "ll", "center")) {
                val v = param(u, k) ?: continue
                val p = ll(v)
                if (p != null) return SharedTarget(p, name.ifBlank { "Shared location" }, null)
                if (k != "ll" && k != "center") return SharedTarget(null, name.ifBlank { v }, v)
            }
            atRe.find(u)?.let { m -> return SharedTarget(LL(m.groupValues[1].toDouble(), m.groupValues[2].toDouble()), name.ifBlank { "Shared location" }, null) }
            if (name.isNotBlank()) return SharedTarget(null, name, name)
            if (before.isNotBlank()) return SharedTarget(null, before, before)
            return null
        }
        ll(text)?.let { return SharedTarget(it, "Shared location", null) }
        // plain text: search for it (the first line is usually the name, the rest the address)
        val q = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(", ")
        return if (q.length >= 2) SharedTarget(null, q, q.take(120)) else null
    }
}
