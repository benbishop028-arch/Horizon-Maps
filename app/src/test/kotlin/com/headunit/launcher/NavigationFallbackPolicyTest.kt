package com.headunit.launcher

import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationFallbackPolicyTest {
    @Test fun connectionFailuresUseOfflineRoute() {
        assertTrue(NavigationFallbackPolicy.shouldUseOffline(UnknownHostException("DNS failed")))
        assertTrue(NavigationFallbackPolicy.shouldUseOffline(IOException("connection reset")))
        assertTrue(NavigationFallbackPolicy.shouldUseOffline(IllegalStateException("Network unavailable")))
    }

    @Test fun unrelatedFailuresDoNotSilentlySwitchRouteProvider() {
        assertFalse(NavigationFallbackPolicy.shouldUseOffline(IllegalArgumentException("Invalid route options")))
        assertFalse(NavigationFallbackPolicy.shouldUseOffline(IllegalStateException("API key rejected")))
        assertFalse(NavigationFallbackPolicy.shouldUseOffline(null))
    }

    @Test fun wrappedConnectionFailureIsDetected() {
        assertTrue(NavigationFallbackPolicy.shouldUseOffline(IllegalStateException("routing failed", IOException("socket closed"))))
    }
}
