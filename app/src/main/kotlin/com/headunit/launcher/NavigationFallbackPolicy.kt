package com.headunit.launcher

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Decides whether an online route failure is likely to be a connection problem that merits offline fallback. */
object NavigationFallbackPolicy {
    fun shouldUseOffline(cause: Throwable?): Boolean {
        var current = cause
        val seen = HashSet<Throwable>()
        while (current != null && seen.add(current)) {
            if (current is IOException || current is UnknownHostException || current is SocketTimeoutException ||
                current is ConnectException || current is NoRouteToHostException) return true
            val message = current.message.orEmpty().lowercase()
            if (listOf("no signal", "no network", "network unavailable", "network error", "connection", "timed out", "timeout", "unable to resolve host", "failed to connect").any(message::contains)) return true
            current = current.cause
        }
        return false
    }
}
