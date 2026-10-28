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

    @Test fun projectsCorrectlyAroundASharpBend() {
        val route = listOf(LL(50.0, -1.0), LL(50.0, -0.9998), LL(50.0002, -0.9998))
        val projected = MapRouteGeometry.projectOn(route, LL(50.00005, -0.99982))
        assertEquals(1, projected?.first)
        assertEquals(50.00005, projected?.second?.lat ?: 0.0, 0.00001)
        assertEquals(-0.9998, projected?.second?.lon ?: 0.0, 0.00001)
    }

    @Test fun choosesNearestOfCloselySpacedParallelRoads() {
        val south = listOf(LL(50.0, -1.0), LL(50.0, -0.999))
        val north = listOf(LL(50.00015, -1.0), LL(50.00015, -0.999))
        val projected = MapRouteGeometry.projectOn(south + north, LL(50.00014, -0.9995))
        assertEquals(2, projected?.first)
        assertEquals(50.00015, projected?.second?.lat ?: 0.0, 0.000001)
    }

    @Test fun spliceRejectsReversedLaneShiftRatherThanReorderingRoute() {
        val route = listOf(LL(50.0, -1.0), LL(50.0, -0.9998), LL(50.0, -0.9996), LL(50.0, -0.9994))
        val reversedShift = listOf(LL(50.0, -0.9993), LL(50.00001, -0.9995), LL(50.0, -0.9997))
        val (result, indices) = MapRouteGeometry.spliceShift(route, reversedShift)
        assertEquals(route, result)
        assertNull(indices)
    }

    @Test fun shortLaneShiftDoesNotAlterRoutePoints() {
        val point = LL(50.0, -0.9995)
        assertEquals(point, MapRouteGeometry.shiftPoint(point, listOf(LL(50.0, -1.0), LL(50.0, -0.9995))))
        val route = listOf(LL(50.0, -1.0), LL(50.0, -0.999))
        val (result, indices) = MapRouteGeometry.spliceShift(route, listOf(LL(50.0, -1.0), LL(50.0, -0.999)))
        assertEquals(route, result)
        assertNull(indices)
    }

    @Test fun laneShiftNearJunctionPreservesIndexMapAndRouteTail() {
        val route = listOf(LL(50.0, -1.0), LL(50.0, -0.9998), LL(50.0, -0.9996), LL(50.0002, -0.9996))
        val shift = listOf(LL(50.00002, -0.9999), LL(50.00002, -0.9997), LL(50.00002, -0.9995))
        val (spliced, indices) = MapRouteGeometry.spliceShift(route, shift)
        assertTrue(spliced.size > route.size)
        assertEquals(spliced.size, indices?.size ?: -1)
        assertEquals(route.lastIndex, indices?.last() ?: -1)
        assertEquals(route.last(), spliced.last())
        assertTrue(indices!!.zipWithNext().all { (a, b) -> b >= a })
    }
}
