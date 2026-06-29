package com.pixsonlin.apbfit.service

import com.pixsonlin.apbfit.data.model.IntensityLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

class SessionCoordinator(
    val sessionId: String,
    val startTimeMillis: Long,
    val sessionEndMillis: Long,
    val intensity: IntensityLevel,
    val batchSize: Int,
) {
    private val _stopRequested = MutableStateFlow(false)
    val stopRequested: StateFlow<Boolean> = _stopRequested.asStateFlow()

    private val activeJobs = AtomicInteger(0)

    fun initJobCount(count: Int) {
        activeJobs.set(count)
    }

    fun requestStop() {
        _stopRequested.value = true
    }

    fun isStopRequested(): Boolean = _stopRequested.value

    fun isPastEnd(): Boolean = System.currentTimeMillis() >= sessionEndMillis

    /** @return true when the last account run has completed. */
    fun onJobCompleted(): Boolean = activeJobs.decrementAndGet() == 0
}
