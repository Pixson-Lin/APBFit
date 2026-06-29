package com.pixsonlin.apbfit.service

import org.junit.Assert.assertEquals
import org.junit.Test

class RunNotificationHelperTest {
    @Test
    fun childNotificationId_isStableForRunId() {
        val first = RunNotificationHelper.childNotificationId("run-abc")
        val second = RunNotificationHelper.childNotificationId("run-abc")
        assertEquals(first, second)
    }

    @Test
    fun groupKey_includesSessionId() {
        assertEquals(
            "apbfit_session_session-123",
            RunNotificationHelper.groupKey("session-123"),
        )
    }
}
