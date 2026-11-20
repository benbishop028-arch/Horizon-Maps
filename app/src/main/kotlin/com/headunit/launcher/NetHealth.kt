package com.headunit.launcher

/**
 * Why connections fail when there seems to be signal. Many head units keep the wrong date and time (often years out after the
 * battery's been off). Then every secure connection is refused (the servers' certificates look out of date or not yet valid),
 * and the map, search, routes and updates all fail as if there were no signal at all.
 */
object NetHealth {
    /** When a secure connection last failed its certificate check (0 = never). */
    @Volatile var tlsFailAt = 0L

    /** The app's context, for keeping the last few connection problems (set when the app starts). */
    @Volatile var appCtx: android.content.Context? = null
    private const val KEY = "net_log"

    /**
     * Looks through [e] (and what caused it) for a failed secure connection, and notes it. It also keeps the last few connection
     * problems ([what] failed, and Android's own reason) for the problem report: every one of them shows as "no signal" on screen,
     * even when the phone has signal (GitHub #269), so the report needs the real reason (a timeout, a refused connection, a
     * certificate, a name that couldn't be looked up). Never the address itself, which can hold a key.
     */
    fun note(e: Throwable?, what: String = "") {
        if (e != null && what.isNotBlank()) log(what, e)
        var c = e
        var n = 0
        while (c != null && n++ < 8) {
            if (c is javax.net.ssl.SSLException || c is java.security.cert.CertificateException || c is java.security.cert.CertPathValidatorException) {
                tlsFailAt = System.currentTimeMillis(); return
            }
            c = c.cause
        }
    }

    /** The device's clock is plainly wrong: earlier than this version of the app could have been made (1 January 2026). */
    fun clockLooksWrong(): Boolean = System.currentTimeMillis() < 1_767_225_600_000L

    /** A sentence to add to a "no signal" message when the clock is the likely cause, or "" when it isn't. */
    fun clockHint(): String =
        if (clockLooksWrong() || (tlsFailAt > 0L && System.currentTimeMillis() - tlsFailAt < 15 * 60_000L))
            " If there is signal, check the head unit's date and time: when they're wrong, secure connections fail and it looks like no signal."
        else ""

    private fun log(what: String, e: Throwable) {
        try {
            val ctx = appCtx ?: return
            // the deepest cause says most (an SSLHandshakeException inside an IOException, say)
            var c: Throwable = e; var n = 0
            while (c.cause != null && c.cause !== c && n++ < 6) c = c.cause!!
            val why = (c.javaClass.simpleName + (c.message?.let { ": " + it.replace(Regex("(key|access_token)=[^&\\s]+"), "$1=…").take(120) } ?: ""))
            val online = try {
                val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
                when {
                    caps == null -> "no network"
                    caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) "wifi" else "mobile data"
                    else -> "network not working"
                }
            } catch (x: Throwable) { "?" }
            val t = java.text.SimpleDateFormat("dd MMM HH:mm:ss", java.util.Locale.UK).format(java.util.Date())
            val sp = ctx.getSharedPreferences("diag", android.content.Context.MODE_PRIVATE)
            val old = sp.getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }
            val line = "$t · $what · $why · $online"
            sp.edit().putString(KEY, (old + line).takeLast(6).joinToString("\n")).apply()
        } catch (x: Throwable) { }
    }

    /** The last few connection problems, for the problem report ("" when there have been none). */
    fun text(ctx: android.content.Context): String =
        ctx.getSharedPreferences("diag", android.content.Context.MODE_PRIVATE).getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }
            .joinToString("\n").let { if (it.isBlank()) "" else it + "\n" }
}
