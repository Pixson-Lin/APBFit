package com.pixson.apbfit.domain.fit

import com.pixson.apbfit.data.model.IntensityLevel
import kotlin.math.max
import kotlin.random.Random

class SegmentGenerator(
    private val random: Random = Random.Default,
) {
    fun nextDurationSeconds(): Int = random.nextInt(MIN_DURATION_SEC, MAX_DURATION_SEC_EXCLUSIVE)

    fun generate(
        index: Int,
        startMillis: Long,
        level: IntensityLevel,
        durationSec: Int = nextDurationSeconds(),
    ): SegmentData {
        val baseSteps = level.cadenceSpm / 60.0 * durationSec
        val steps = max(MIN_STEPS, gaussianRound(mean = baseSteps, sigma = STEP_SIGMA, random = random))
        val distance = (steps * level.strideMeters).roundToTwoDecimals().toFloat()
        val endMillis = startMillis + durationSec * MILLIS_PER_SECOND
        return SegmentData(
            segmentIndex = index,
            startTimeMillis = startMillis,
            endTimeMillis = endMillis,
            steps = steps,
            distanceMeters = distance,
        )
    }

    companion object {
        const val MIN_DURATION_SEC = 25
        const val MAX_DURATION_SEC_EXCLUSIVE = 36
        const val MIN_STEPS = 1
        const val STEP_SIGMA = 5.0
        const val ACTIVITY_TYPE_RUNNING = 8
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
