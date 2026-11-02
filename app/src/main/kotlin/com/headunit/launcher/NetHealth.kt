package com.headunit.launcher

/**
 * Why connections fail when there seems to be signal. Many head units keep the wrong date and time (often years out after the
 * battery's been off). Then every secure connection is refused (the servers' certificates look out of date or not yet valid),
 * and the map, search, routes and updates all fail as if there were no signal at all.
 */
object NetHealth {
    /** When a secure connection last failed its certificate check (0 = never). */
    @Volatile var tlsFailAt = 0L

    /** Looks through [e] (and what caused it) for a failed secure connection, and notes it. */
    fun note(e: Throwable?) {
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
}
