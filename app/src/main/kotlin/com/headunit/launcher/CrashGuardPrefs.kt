package com.headunit.launcher

/** Stable keys for startup-crash and ANR recovery state. Keep the stored names unchanged. */
internal object CrashGuardPrefs {
    const val FILE = "crashguard"
    const val START_FAILURES = "startfails"
    const val LAST_ANR_SEEN = "anrseen"
}
