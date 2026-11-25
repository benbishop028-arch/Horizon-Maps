package com.headunit.launcher

/** Removes routing-service markers for intermediate shaping points on a recorded My Route. */
object RouteGuidancePolicy {
    fun guidanceSteps(steps: List<Step>, isMyRoute: Boolean): List<Step> {
        if (!isMyRoute) return steps
        return steps.filterNot { step ->
            when (step.maneuver.uppercase()) {
                "WAYPOINT_REACHED", "WAYPOINT_LEFT" -> true
                else -> false
            }
        }
    }
}
