package com.pixson.apbfit.service

import com.pixson.apbfit.data.model.RunStatus

data class SessionUiState(
    val sessionId: String? = null,
    val intensityName: String = "",
    val startTimeMillis: Long = 0L,
    val durationMinutes: Int = 0,
    val elapsedMillis: Long = 0L,
    val remainingMillis: Long = 0L,
    val sessionStatusLabel: String = "",
    val isActive: Boolean = false,
)

data class AccountRunUiState(
    val runId: String,
    val accountEmail: String,
    val totalSteps: Int = 0,
    val segmentsWritten: Int = 0,
    val status: RunStatus = RunStatus.RUNNING,
    val errorMessage: String? = null,
)

data class RunSessionUiState(
    val session: SessionUiState = SessionUiState(),
    val accounts: List<AccountRunUiState> = emptyList(),
) {
    fun withCurrentTiming(): RunSessionUiState {
        val sessionState = session
        if (!sessionState.isActive || sessionState.startTimeMillis <= 0L) return this
        val now = System.currentTimeMillis()
        val elapsed = (now - sessionState.startTimeMillis).coerceAtLeast(0L)
        val remaining = (sessionState.durationMinutes * 60_000L - elapsed).coerceAtLeast(0L)
        val statusLabel = SessionStatusLabels.sessionStatusLabel(
            isActive = true,
            accounts = accounts,
        )
        return copy(
            session = sessionState.copy(
                elapsedMillis = elapsed,
                remainingMillis = remaining,
                sessionStatusLabel = statusLabel,
            ),
        )
    }
}
