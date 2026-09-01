package com.pixsonlin.apbfit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RunSessionPowerEstimateTest {

    @Test
    fun avgSegmentDuration_matchesUniform25to35() {
        assertEquals(30, RunSessionPowerEstimate.avgSegmentDurationSec)
    }

    @Test
    fun twoHourRun_batch3_vs_batch6_workload() {
        val batch3 = RunSessionPowerEstimate.estimateSessionWorkload(
            durationMinutes = 120,
            batchSize = 3,
        )
        val batch6 = RunSessionPowerEstimate.estimateSessionWorkload(
            durationMinutes = 120,
            batchSize = 6,
        )

        assertEquals(240, batch3.segmentCount)
        assertEquals(240, batch6.segmentCount)

        assertEquals(14_400L, batch3.pollWakeCount)
        assertEquals(14_400L, batch6.pollWakeCount)

        assertEquals(80, batch3.batchWriteCount)
        assertEquals(40, batch6.batchWriteCount)

        assertEquals(80, batch3.healthConnectInsertCalls)
        assertEquals(40, batch6.healthConnectInsertCalls)

        assertEquals(80, batch3.progressNotificationUpdates)
        assertEquals(40, batch6.progressNotificationUpdates)
    }

    @Test
    fun perHourRates_forTwoHourRun() {
        val batch3 = RunSessionPowerEstimate.estimateSessionWorkload(120, 3)
        val batch6 = RunSessionPowerEstimate.estimateSessionWorkload(120, 6)

        assertEquals(40, batch3.batchWriteCount / 2)
        assertEquals(20, batch6.batchWriteCount / 2)
        assertEquals(40, batch3.healthConnectInsertCalls / 2)
        assertEquals(20, batch6.healthConnectInsertCalls / 2)
        assertEquals(7_200L, batch3.pollWakeCount / 2)
        assertEquals(7_200L, batch6.pollWakeCount / 2)
    }

    @Test
    fun radioActiveTime_batch3_vs_batch6_atThreeSecondsPerBatch() {
        val batch3Seconds = RunSessionPowerEstimate.expectedRadioActiveSeconds(
            batchWriteCount = 80,
            secondsPerBatch = 3,
        )
        val batch6Seconds = RunSessionPowerEstimate.expectedRadioActiveSeconds(
            batchWriteCount = 40,
            secondsPerBatch = 3,
        )

        assertEquals(240, batch3Seconds)
        assertEquals(120, batch6Seconds)
    }
}
