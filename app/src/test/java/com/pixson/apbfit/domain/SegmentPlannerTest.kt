package com.pixson.apbfit.domain

import com.pixson.apbfit.data.model.IntensityLevel
import com.pixson.apbfit.domain.fit.SegmentGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentPlannerTest {
    private val planner = SegmentPlanner()

    @Test
    fun planAllSegments_respectsSessionEnd() {
        val start = 1_000_000L
        val sessionEnd = start + 120 * 60_000L
        val generator = SegmentGenerator(kotlin.random.Random(42))
        val segments = planner.planAllSegments(
            runStartMillis = start,
            sessionEndMillis = sessionEnd,
            intensity = IntensityLevel.BRISK_WALK,
            generator = generator,
        )
        assertTrue(segments.isNotEmpty())
        segments.forEach { segment ->
            assertTrue(segment.endTimeMillis <= sessionEnd)
            assertTrue(segment.startTimeMillis >= start)
        }
    }

    @Test
    fun planAllSegments_tenMinuteRun_hasReasonableCount() {
        val start = 0L
        val sessionEnd = 10 * 60_000L
        val generator = SegmentGenerator(kotlin.random.Random(7))
        val segments = planner.planAllSegments(
            runStartMillis = start,
            sessionEndMillis = sessionEnd,
            intensity = IntensityLevel.JOG,
            generator = generator,
        )
        assertTrue(segments.size in 17..25)
    }
}
