package com.headunit.launcher

import java.net.URI

/** Pure validation rules for update metadata, kept outside Android I/O to make them easy to test. */
internal object UpdaterPolicy {
    /** Accept only a SHA-256 hex digest, normalizing harmless surrounding whitespace and case. */
    fun parseSha256(text: String): String? {
        val value = text.trim().lowercase()
        return value.takeIf { it.length == 64 && it.all { ch -> ch in '0'..'9' || ch in 'a'..'f' } }
    }

    /** Validate structured release metadata without relying on mutable release titles or prose. */
    fun isValidReleaseMetadata(version: String, build: Int, versionCode: Int): Boolean =
        build >= 0 && versionCode == 100 + build && version == "1.0.$build"

    /** Update assets must be HTTPS URLs with a real host; malformed URLs fail closed. */
    fun isHttpsUrl(address: String): Boolean = try {
        val uri = URI(address)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    } catch (_: Exception) { false }
}
