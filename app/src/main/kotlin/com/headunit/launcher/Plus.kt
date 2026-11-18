package com.headunit.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Horizon Maps Plus (the Google Play edition only). Without it, the Play edition runs on OpenStreetMap: its map, its search and
 * routes planned on the phone from a downloaded country pack, with no traffic. With it, the full Mapbox map (3D, satellite),
 * Mapbox and TomTom routes, search and live traffic, using the developer's own accounts through the key server: nobody has to
 * make keys. The GitHub edition is unchanged (your own keys).
 *
 * Switched on only once the key server's address is in the build (BuildConfig.KEY_SERVER): until then the Play edition works
 * exactly as before, so the subscription can be set up without anything changing for testers.
 */
object Plus {
    /** Plus is part of this build: the Play edition, with the key server set up. */
    val offered: Boolean get() = BuildConfig.STORE == "play" && BuildConfig.KEY_SERVER.isNotBlank()

    /** A subscription is active (from Google Play; remembered, so a start with no signal keeps it). */
    var active by mutableStateOf(false)
    /** What Play offers, in the shop's own currency: monthly and yearly, each with its free trial when this person can have one. */
    var offers by mutableStateOf<List<Offer>>(emptyList())
    /** Play couldn't be reached, or has nothing on sale yet (shown on the subscribe screen). */
    var problem by mutableStateOf("")

    /** The subscription's receipt from Play (the key server checks it with Google). */
    @Volatile var purchaseToken = ""
    @Volatile var productId = ""

    class Offer(val plan: String, val price: String, val trialDays: Int, val handle: Any)

    /** True on the Play edition without Plus: the OpenStreetMap version. */
    val free: Boolean get() = offered && !active

    private const val PREF_ACTIVE = "plusactive"
    private const val PREF_TOKEN = "plustoken"
    private const val PREF_PRODUCT = "plusproduct"

    fun load(prefs: Prefs) {
        active = prefs.b(PREF_ACTIVE, false)
        purchaseToken = prefs.s(PREF_TOKEN, "")
        productId = prefs.s(PREF_PRODUCT, "")
    }

    /** Called by the billing code whenever Play says what this person has. */
    fun set(prefs: Prefs, isActive: Boolean, token: String, product: String) {
        val changed = isActive != active || token != purchaseToken
        active = isActive; purchaseToken = token; productId = product
        prefs.put(PREF_ACTIVE, isActive); prefs.put(PREF_TOKEN, token); prefs.put(PREF_PRODUCT, product)
        if (changed) KeyServer.refreshSoon()
    }
}

/**
 * The key server (see server/README.md): hands out a Mapbox token that lasts an hour (from the developer's Mapbox account) and
 * passes TomTom requests on with the developer's TomTom key, which never leaves the server. Plus subscribers get both; the free
 * OpenStreetMap version gets a token for the map engine only (Mapbox's engine needs one to start, even to draw OpenStreetMap).
 */
object KeyServer {
    /** Stands in for a TomTom key in the app: requests carrying it go through the key server instead. */
    const val TT = "PLUS-VIA-KEY-SERVER"

    /** The Mapbox token now (an hour's worth), as screen state so the map picks up a new one. */
    var mapbox by mutableStateOf("")
        private set
    /** TomTom through the server is available (Plus, and the server said yes). */
    @Volatile var tomtom = false
        private set
    @Volatile private var session = ""
    @Volatile private var until = 0L
    @Volatile private var failAt = 0L
    @Volatile private var appCtx: Context? = null
    @Volatile private var prefs: Prefs? = null
    private val lock = Any()
    private val handler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    /** Starts it (once, from the app and the car screen); fetches a token now and keeps one fresh. */
    fun start(ctx: Context, p: Prefs) {
        if (!Plus.offered) return
        appCtx = ctx.applicationContext; prefs = p
        Plus.load(p)
        refreshSoon()
    }

    fun refreshSoon() {
        val c = appCtx ?: return; val p = prefs ?: return
        Thread { try { refresh(c, p) } catch (e: Throwable) { } }.apply { isDaemon = true; name = "key-server" }.start()
    }

    private fun refresh(ctx: Context, p: Prefs) = synchronized(lock) {
        val now = System.currentTimeMillis()
        // (a fresh one is kept for 50 of its 60 minutes; after a failure, tried again in a minute)
        if (now < until - 10 * 60_000L && mapbox.isNotBlank() && tomtom == Plus.active) return
        if (now - failAt < 60_000L) { later(60_000L); return }
        try {
            val body = JSONObject()
                .put("install", installId(p))
                .put("purchaseToken", Plus.purchaseToken)
                .put("product", Plus.productId)
                .put("package", ctx.packageName)
                .put("version", BuildConfig.VERSION_NAME)
            val c = URL(BuildConfig.KEY_SERVER.trimEnd('/') + "/session").also { Net.check() }.openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            c.disconnect()
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            val o = JSONObject(text)
            val mb = o.optString("mapbox")
            if (mb.isBlank()) throw java.io.IOException("no token")
            session = o.optString("session")
            tomtom = o.optBoolean("plus", false) && session.isNotBlank()
            // the server's word on the subscription wins (a cancelled one stops here, even before Play tells the app)
            if (Plus.active && !o.optBoolean("plus", false) && o.optBoolean("checked", false)) handler.post { Plus.set(p, false, Plus.purchaseToken, Plus.productId) }
            val secs = o.optLong("expiresIn", 3600L).coerceIn(300L, 3600L)
            until = now + secs * 1000L
            handler.post { mapbox = mb }
            later((secs - 600L).coerceAtLeast(240L) * 1000L)
        } catch (e: Exception) {
            failAt = now
            later(60_000L)
        }
    }

    private val again = Runnable { refreshSoon() }
    private fun later(ms: Long) { handler.removeCallbacks(again); handler.postDelayed(again, ms) }

    private fun installId(p: Prefs): String {
        val id = p.s("plusinstall", "")
        if (id.isNotBlank()) return id
        val n = java.util.UUID.randomUUID().toString()
        p.put("plusinstall", n)
        return n
    }

    /**
     * A TomTom request carrying [TT] in place of a key, sent through the key server instead (which adds the real key): its new
     * address, and the header that proves this is a Plus subscriber. Null for any other request.
     */
    fun tomtomVia(url: String): Pair<String, String>? {
        if (!url.contains("key=$TT")) return null
        val path = url.removePrefix("https://api.tomtom.com/").replace("key=$TT&", "").replace("&key=$TT", "").replace("?key=$TT", "?")
        return BuildConfig.KEY_SERVER.trimEnd('/') + "/tomtom/" + path to session
    }
}
