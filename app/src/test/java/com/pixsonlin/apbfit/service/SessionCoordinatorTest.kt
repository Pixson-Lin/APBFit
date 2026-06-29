package com.pixsonlin.apbfit.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.pixsonlin.apbfit.data.model.IntensityLevel

class SessionCoordinatorTest {
    @Test
    fun onJobCompleted_returnsTrueOnlyForLastJob() {
        val coordinator = SessionCoordinator(
            sessionId = "session-1",
            startTimeMillis = System.currentTimeMillis(),
            sessionEndMillis = System.currentTimeMillis() + 60_000L,
            intensity = IntensityLevel.BRISK_WALK,
            batchSize = 3,
        )
        coordinator.initJobCount(2)
        assertFalse(coordinator.onJobCompleted())
        assertTrue(coordinator.onJobCompleted())
    }

    @Test
    fun requestStop_setsStopFlag() {
        val coordinator = SessionCoordinator(
            sessionId = "session-1",
            startTimeMillis = System.currentTimeMillis(),
            sessionEndMillis = System.currentTimeMillis() + 60_000L,
            intensity = IntensityLevel.BRISK_WALK,
            batchSize = 3,
        )
        assertFalse(coordinator.isStopRequested())
        coordinator.requestStop()
        assertTrue(coordinator.isStopRequested())
    }
}
