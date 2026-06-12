package com.pixson.apbfit.service

import com.pixson.apbfit.data.model.RunStatus

data class RunUiState(
    val runId: String? = null,
    val status: RunStatus = RunStatus.RUNNING,
    val intensityName: String = "",
    val startTimeMillis: Long = 0L,
    val durationMinutes: Int = 0,
    val elapsedMillis: Long = 0L,
    val remainingMillis: Long = 0L,
    val totalSteps: Int = 0,
    val segmentsWritten: Int = 0,
    val errorMessage: String? = null,
) {
    val isActive: Boolean = runId != null && status == RunStatus.RUNNING

    fun withCurrentTiming(): RunUiState {
        if (!isActive || startTimeMillis <= 0L) return this
        val now = System.currentTimeMillis()
        val elapsed = (now - startTimeMillis).coerceAtLeast(0L)
        val remaining = (durationMinutes * 60_000L - elapsed).coerceAtLeast(0L)
        return copy(elapsedMillis = elapsed, remainingMillis = remaining)
    }
}
