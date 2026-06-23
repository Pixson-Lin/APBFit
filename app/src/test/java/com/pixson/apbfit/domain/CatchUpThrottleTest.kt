package com.pixson.apbfit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CatchUpThrottleTest {
    @Test
    fun batchesPerAccount_dividesGlobalBudget() {
        assertEquals(3, CatchUpThrottle.batchesPerAccount(1))
        assertEquals(2, CatchUpThrottle.batchesPerAccount(2))
        assertEquals(1, CatchUpThrottle.batchesPerAccount(3))
        assertEquals(1, CatchUpThrottle.batchesPerAccount(4))
    }
}
