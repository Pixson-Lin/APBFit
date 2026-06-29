package com.pixsonlin.apbfit.domain

import kotlin.math.ceil

object CatchUpThrottle {
    const val MAX_BATCHES_PER_CATCH_UP = 3
    const val DELAY_BETWEEN_BATCHES_MS = 1_000L
    const val MAX_SEGMENTS_PER_CATCH_UP = 20
    const val MAX_CATCH_UP_WALL_CLOCK_MS = 30_000L
    const val SCHEDULE_SLACK_MS = 5_000L

    fun batchesPerAccount(accountCount: Int): Int {
        require(accountCount > 0) { "accountCount must be positive" }
        return maxOf(1, ceil(MAX_BATCHES_PER_CATCH_UP.toDouble() / accountCount).toInt())
    }
}
