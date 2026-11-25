package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteGuidancePolicyTest {
    private fun step(maneuver: String) = Step(100.0, maneuver, "", "")

    @Test fun ordinaryRoutesKeepProviderWaypointInstructions() {
        val steps = listOf(step("TURN_LEFT"), step("WAYPOINT_REACHED"), step("WAYPOINT_LEFT"))
        assertEquals(steps, RouteGuidancePolicy.guidanceSteps(steps, isMyRoute = false))
    }

    @Test fun savedMyRoutesHideOnlyIntermediateWaypointMarkers() {
        val steps = listOf(
            step("DEPART"),
            step("WAYPOINT_REACHED"),
            step("TURN_LEFT"),
            step("WAYPOINT_LEFT"),
            step("ARRIVE")
        )
        assertEquals(
            listOf("DEPART", "TURN_LEFT", "ARRIVE"),
            RouteGuidancePolicy.guidanceSteps(steps, isMyRoute = true).map { it.maneuver }
        )
    }

    @Test fun waypointMarkerMatchingIsCaseInsensitive() {
        val steps = listOf(step("waypoint_reached"), step("Waypoint_Left"), step("TURN_RIGHT"))
        assertEquals(
            listOf("TURN_RIGHT"),
            RouteGuidancePolicy.guidanceSteps(steps, isMyRoute = true).map { it.maneuver }
        )
    }
}
