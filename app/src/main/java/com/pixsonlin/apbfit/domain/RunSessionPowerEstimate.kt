package com.pixsonlin.apbfit.domain

import com.pixsonlin.apbfit.domain.fit.SegmentGenerator

/**
 * Analytical estimates for run-session wake frequency and related workload.
 * See [docs/APBFit_2hr_Batch_Power_Estimate.md] for power-model discussion.
 */
object RunSessionPowerEstimate {
    /** Matches [com.pixsonlin.apbfit.service.AccountRunContext] stop-poll chunk size. */
    const val STOP_POLL_INTERVAL_MS = 500L

    /** Matches three [com.pixsonlin.apbfit.domain.fit.GoogleFitWriter.writeSegments] calls per batch. */
    const val FIT_INSERT_CALLS_PER_BATCH = 3

    /** Mean of uniform [SegmentGenerator.MIN_DURATION_SEC, SegmentGenerator.MAX_DURATION_SEC_EXCLUSIVE). */
    val avgSegmentDurationSec: Int =
        (SegmentGenerator.MIN_DURATION_SEC + SegmentGenerator.MAX_DURATION_SEC_EXCLUSIVE - 1) / 2

    fun expectedSegmentCount(
        durationMinutes: Int,
        avgSegmentSec: Int = avgSegmentDurationSec,
    ): Int = (durationMinutes * 60) / avgSegmentSec

    fun expectedBatchWriteCount(segmentCount: Int, batchSize: Int): Int =
        (segmentCount + batchSize - 1) / batchSize

    fun expectedPollWakeCount(
        segmentCount: Int,
        avgSegmentSec: Int = avgSegmentDurationSec,
        pollIntervalMs: Long = STOP_POLL_INTERVAL_MS,
    ): Long {
        val pollsPerSegment = (avgSegmentSec * 1_000L) / pollIntervalMs
        return segmentCount.toLong() * pollsPerSegment
    }

    fun expectedFitInsertDataCalls(batchWriteCount: Int): Int =
        batchWriteCount * FIT_INSERT_CALLS_PER_BATCH

    fun expectedRadioActiveSeconds(
        batchWriteCount: Int,
        secondsPerBatch: Int,
    ): Int = batchWriteCount * secondsPerBatch

    data class SessionWorkload(
        val segmentCount: Int,
        val batchWriteCount: Int,
        val pollWakeCount: Long,
        val fitInsertDataCalls: Int,
        val progressNotificationUpdates: Int,
    )

    fun estimateSessionWorkload(
        durationMinutes: Int,
        batchSize: Int,
        avgSegmentSec: Int = avgSegmentDurationSec,
        pollIntervalMs: Long = STOP_POLL_INTERVAL_MS,
    ): SessionWorkload {
        val segmentCount = expectedSegmentCount(durationMinutes, avgSegmentSec)
        val batchWriteCount = expectedBatchWriteCount(segmentCount, batchSize)
        return SessionWorkload(
            segmentCount = segmentCount,
            batchWriteCount = batchWriteCount,
            pollWakeCount = expectedPollWakeCount(segmentCount, avgSegmentSec, pollIntervalMs),
            fitInsertDataCalls = expectedFitInsertDataCalls(batchWriteCount),
            progressNotificationUpdates = batchWriteCount,
        )
    }
}
