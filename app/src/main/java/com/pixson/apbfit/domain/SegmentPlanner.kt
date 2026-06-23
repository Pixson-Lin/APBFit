package com.pixson.apbfit.domain

import com.pixson.apbfit.data.model.IntensityLevel
import com.pixson.apbfit.domain.fit.SegmentData
import com.pixson.apbfit.domain.fit.SegmentGenerator

class SegmentPlanner {
    fun planAllSegments(
        runStartMillis: Long,
        sessionEndMillis: Long,
        intensity: IntensityLevel,
        generator: SegmentGenerator,
    ): List<SegmentData> {
        val segments = mutableListOf<SegmentData>()
        var nextStart = runStartMillis
        var index = 0
        while (nextStart < sessionEndMillis) {
            val durationSec = generator.nextDurationSeconds()
            val endMillis = nextStart + durationSec * MILLIS_PER_SECOND
            if (endMillis > sessionEndMillis) break
            val segment = generator.generate(
                index = index,
                startMillis = nextStart,
                level = intensity,
                durationSec = durationSec,
            )
            segments += segment
            index++
            nextStart = segment.endTimeMillis
        }
        return segments
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
