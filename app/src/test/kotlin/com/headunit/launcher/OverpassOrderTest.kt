package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class OverpassOrderTest {
    private val now = 1_000_000L

    @Test fun serverThatAnsweredLastGoesFirst() {
        assertEquals(listOf(2, 3, 0, 1), Overpass.order(2, longArrayOf(0, 0, 0, 0), now))
    }

    @Test fun serversThatJustFailedGoLast() {
        assertEquals(listOf(2, 3, 0, 1), Overpass.order(0, longArrayOf(now - 2000, now - 1000, 0, 0), now))
    }

    @Test fun aFailureIsForgottenAfterAWhile() {
        assertEquals(listOf(0, 1, 2, 3), Overpass.order(0, longArrayOf(now - 200_000, 0, 0, 0), now))
    }

    @Test fun withAllDownTheOneThatFailedLongestAgoGoesFirst() {
        assertEquals(listOf(3, 2, 0, 1), Overpass.order(0, longArrayOf(now - 2000, now - 1000, now - 5000, now - 9000), now))
    }
}
