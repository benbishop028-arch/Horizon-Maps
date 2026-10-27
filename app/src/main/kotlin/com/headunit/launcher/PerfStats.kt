package com.headunit.launcher

/** Counters behind the on-screen performance monitor (Map settings > Performance). Cheap enough to leave running. */
object PerfStats {
    @Volatile var renders = 0          // map frames the renderer started
    @Volatile var ticks = 0            // arrow/camera updates done by the glide
    @Volatile var tickNsSum = 0L       // time those updates took on the main thread
    @Volatile var tickNsMax = 0L
    @Volatile var gapMsMax = 0L        // longest wait between two screen refreshes (main-thread stalls show up here)
    @Volatile var on = false
}
