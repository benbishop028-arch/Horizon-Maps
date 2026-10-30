package com.headunit.launcher

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sends a destination from the phone to the head unit, through ntfy.sh (a free public message relay, so there's no server of our
 * own). The head unit makes a pairing code and you type it on the phone. Both work out the same private "topic" and encryption
 * key from it, so the relay only ever sees scrambled text, and only someone with the code could read or send. ntfy keeps a
 * message for 12 hours, so a place sent in the evening is still waiting when the car starts in the morning (within 12 hours).
 * Network work: call off the main thread.
 */
object CarLink {
    private const val HOST = "https://ntfy.sh/"
    /** Letters and digits that can't be mixed up when typed (no 0/O, 1/I/L). */
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    /** A new pairing code, 9 characters, shown as "K7P-4QX-M2D". */
    fun newCode(): String {
        val r = SecureRandom()
        return (1..9).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("")
    }

    /** The code as typed: capitals, no dashes or spaces; null if it isn't a valid code. */
    fun clean(typed: String): String? {
        val c = typed.uppercase().filter { it.isLetterOrDigit() }.replace('0', 'O').replace('1', 'I')
        return if (c.length == 9 && c.all { it in ALPHABET }) c else null
    }

    fun pretty(code: String): String = code.chunked(3).joinToString("-")

    private fun sha(s: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
    private fun topic(code: String) = "hn-" + sha("horizon-topic:$code").take(16).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun key(code: String) = SecretKeySpec(sha("horizon-key:$code"), "AES")

    private fun encrypt(code: String, text: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(code), GCMParameterSpec(128, iv))
        return Base64.encodeToString(iv + c.doFinal(text.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(code: String, b64: String): String? = try {
        val all = Base64.decode(b64, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(code), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        String(c.doFinal(all.copyOfRange(12, all.size)))
    } catch (e: Exception) { null }

    /** Sends [p] to the car paired with [code]. Throws when it can't. */
    fun send(code: String, p: Place) {
        val body = JSONObject().put("n", p.name).put("s", p.sub).put("la", p.pos.lat).put("lo", p.pos.lon).put("t", System.currentTimeMillis()).toString()
        val c = URL(HOST + topic(code)).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 10000
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("User-Agent", "HorizonNav/${BuildConfig.VERSION_NAME}")
        try {
            c.outputStream.use { it.write(encrypt(code, body).toByteArray()) }
            if (c.responseCode !in 200..299) throw java.io.IOException(if (c.responseCode == 429) "Too many sends: try again in a minute" else "HTTP ${c.responseCode}")
        } finally { c.disconnect() }
    }

    class Sent(val id: String, val place: Place, val at: Long)

    /** The newest place sent since [lastId] (or in the last 12 hours), or null. */
    fun latest(code: String, lastId: String): Sent? {
        val since = if (lastId.isNotBlank()) lastId else "12h"
        val c = URL(HOST + topic(code) + "/json?poll=1&since=" + since).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 10000
        c.setRequestProperty("User-Agent", "HorizonNav/${BuildConfig.VERSION_NAME}")
        try {
            if (c.responseCode !in 200..299) return null
            var best: Sent? = null
            c.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val o = try { JSONObject(line) } catch (e: Exception) { continue }
                    if (o.optString("event") != "message") continue
                    val txt = decrypt(code, o.optString("message")) ?: continue
                    val j = try { JSONObject(txt) } catch (e: Exception) { continue }
                    val at = j.optLong("t", 0L)
                    if (System.currentTimeMillis() - at > 12 * 3600_000L) continue
                    val pl = Place(j.optString("n").ifBlank { "Destination" }, j.optString("s"), LL(j.optDouble("la"), j.optDouble("lo")))
                    if (pl.pos.lat.isNaN() || pl.pos.lon.isNaN()) continue
                    if (best == null || at >= best!!.at) best = Sent(o.optString("id"), pl, at)
                }
            }
            return best
        } finally { c.disconnect() }
    }
}
