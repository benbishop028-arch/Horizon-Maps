package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterPolicyTest {
    @Test fun acceptsSha256HexAndNormalizesCaseAndWhitespace() {
        val digest = "ABCDEF0123456789".repeat(4)
        assertEquals(digest.lowercase(), UpdaterPolicy.parseSha256("  $digest\n"))
    }

    @Test fun rejectsMissingOrMalformedSha256() {
        assertNull(UpdaterPolicy.parseSha256(""))
        assertNull(UpdaterPolicy.parseSha256("0".repeat(63)))
        assertNull(UpdaterPolicy.parseSha256("0".repeat(65)))
        assertNull(UpdaterPolicy.parseSha256("g".repeat(64)))
        assertNull(UpdaterPolicy.parseSha256("0".repeat(63) + " "))
    }

    @Test fun validatesStructuredReleaseMetadata() {
        assertTrue(UpdaterPolicy.isValidReleaseMetadata("1.0.123", 123, 223))
        assertFalse(UpdaterPolicy.isValidReleaseMetadata("1.0.124", 123, 223))
        assertFalse(UpdaterPolicy.isValidReleaseMetadata("1.0.123", 123, 224))
        assertFalse(UpdaterPolicy.isValidReleaseMetadata("release 1.0.123", 123, 223))
        assertFalse(UpdaterPolicy.isValidReleaseMetadata("1.0.-1", -1, 99))
    }

    @Test fun acceptsOnlyHttpsUrlsWithHosts() {
        assertTrue(UpdaterPolicy.isHttpsUrl("https://github.com/releases/download/app.apk"))
        assertFalse(UpdaterPolicy.isHttpsUrl("http://github.com/app.apk"))
        assertFalse(UpdaterPolicy.isHttpsUrl("file:///tmp/app.apk"))
        assertFalse(UpdaterPolicy.isHttpsUrl("not a url"))
        assertFalse(UpdaterPolicy.isHttpsUrl("https:///missing-host"))
    }
}
