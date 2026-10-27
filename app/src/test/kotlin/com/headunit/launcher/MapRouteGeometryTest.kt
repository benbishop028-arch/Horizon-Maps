package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapRouteGeometryTest {
    @Test fun projectsOntoNearestSegment() {
        val points = listOf(LL(50.0, -1.0), LL(50.0, -0.999), LL(50.001, -0.999))
        val projected = MapRouteGeometry.projectOn(points, LL(50.0001, -0.9995))
        assertEquals(0, projected?.first)
        assertEquals(50.0, projected?.second?.lat ?: 0.0, 0.00001)
        assertEquals(-0.9995, projected?.second?.lon ?: 0.0, 0.00001)
    }

    @Test fun laneShiftPointSnapsOnlyWithinTenMetres() {
        val shift = listOf(LL(50.0, -1.0), LL(50.0, -0.9995), LL(50.0, -0.999))
        val snapped = MapRouteGeometry.shiftPoint(LL(50.00002, -0.9995), shift)
        assertEquals(50.0, snapped.lat, 0.000001)
        assertEquals(-0.9995, snapped.lon, 0.000001)
        val distant = LL(50.001, -0.9995)
        assertEquals(distant, MapRouteGeometry.shiftPoint(distant, shift))
    }

    @Test fun spliceShiftKeepsOriginalIndexMapping() {
        val points = listOf(LL(50.0, -1.0), LL(50.0, -0.9995), LL(50.0, -0.999), LL(50.0, -0.9985))
        val shift = listOf(LL(50.00002, -0.9998), LL(50.00002, -0.9995), LL(50.00002, -0.9992))
        val (spliced, indices) = MapRouteGeometry.spliceShift(points, shift)
        assertTrue(spliced.size > points.size)
        assertEquals(spliced.size, indices?.size ?: -1)
        assertEquals(0, indices?.first() ?: -1)
        assertEquals(points.lastIndex, indices?.last() ?: -1)
    }

    @Test fun rejectsPointsOutsideExistingFortyMetreTolerance() {
        assertNull(MapRouteGeometry.projectOn(listOf(LL(50.0, -1.0), LL(50.0, -0.999)), LL(50.001, -0.9995)))
    }

    @Test fun rejectsRoutesWithoutSegments() {
        assertNull(MapRouteGeometry.projectOn(emptyList(), LL(50.0, -1.0)))
        assertNull(MapRouteGeometry.projectOn(listOf(LL(50.0, -1.0)), LL(50.0, -1.0)))
    }
}
