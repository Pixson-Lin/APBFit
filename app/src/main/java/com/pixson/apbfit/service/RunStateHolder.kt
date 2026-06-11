package com.pixson.apbfit.service

import com.pixson.apbfit.data.model.RunStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunStateHolder @Inject constructor() {
    private val _state = MutableStateFlow(RunUiState())
    val state: StateFlow<RunUiState> = _state.asStateFlow()

    fun update(transform: (RunUiState) -> RunUiState) {
        _state.value = transform(_state.value)
    }

    fun setRunning(
        runId: String,
        intensityName: String,
        startTimeMillis: Long,
        durationMinutes: Int,
        totalSteps: Int,
        segmentsWritten: Int,
    ) {
        val now = System.currentTimeMillis()
        val elapsed = (now - startTimeMillis).coerceAtLeast(0L)
        val remaining = (durationMinutes * 60_000L - elapsed).coerceAtLeast(0L)
        _state.value = RunUiState(
            runId = runId,
            status = RunStatus.RUNNING,
            intensityName = intensityName,
            elapsedMillis = elapsed,
            remainingMillis = remaining,
            totalSteps = totalSteps,
            segmentsWritten = segmentsWritten,
            errorMessage = null,
        )
    }

    fun setFinished(status: RunStatus, errorMessage: String? = null) {
        _state.value = _state.value.copy(
            status = status,
            errorMessage = errorMessage,
            remainingMillis = 0L,
        )
    }

    fun clear() {
        _state.value = RunUiState()
    }
}
