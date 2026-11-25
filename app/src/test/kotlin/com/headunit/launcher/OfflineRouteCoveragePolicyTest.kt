package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineRouteCoveragePolicyTest {
    @Test fun sameDownloadedAreaUsesOnePack() { assertEquals(OfflineRouteCoveragePolicy.Leg.SAME_AREA, OfflineRouteCoveragePolicy.leg("fr", "fr")) }
    @Test fun differentDownloadedAreasUseBorderJoin() { assertEquals(OfflineRouteCoveragePolicy.Leg.DIFFERENT_AREAS, OfflineRouteCoveragePolicy.leg("fr", "de")) }
    @Test fun uncoveredDestinationStopsAtCoverageEdge() { assertEquals(OfflineRouteCoveragePolicy.Leg.DESTINATION_UNCOVERED, OfflineRouteCoveragePolicy.leg("fr", null)) }
    @Test fun uncoveredStartingLocationIsRejected() { assertEquals(OfflineRouteCoveragePolicy.Leg.START_UNCOVERED, OfflineRouteCoveragePolicy.leg(null, "de")) }
}
