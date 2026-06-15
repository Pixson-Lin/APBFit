package com.pixson.apbfit.service

import com.pixson.apbfit.data.model.RunStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunSessionStateHolder @Inject constructor() {
    private val _state = MutableStateFlow(RunSessionUiState())
    val state: StateFlow<RunSessionUiState> = _state.asStateFlow()

    val isActive: Boolean get() = _state.value.session.isActive

    fun beginSession(
        sessionId: String,
        intensityName: String,
        startTimeMillis: Long,
        durationMinutes: Int,
        accounts: List<AccountRunUiState>,
    ) {
        val runningCount = accounts.count { it.status == RunStatus.RUNNING }
        val totalCount = accounts.size
        val statusLabel = if (totalCount > 0) "$runningCount/$totalCount 進行中" else ""
        _state.value = RunSessionUiState(
            session = SessionUiState(
                sessionId = sessionId,
                intensityName = intensityName,
                startTimeMillis = startTimeMillis,
                durationMinutes = durationMinutes,
                elapsedMillis = 0L,
                remainingMillis = durationMinutes * 60_000L,
                sessionStatusLabel = statusLabel,
                isActive = true,
            ),
            accounts = accounts,
        )
    }

    fun updateAccountProgress(runId: String, totalSteps: Int, segmentsWritten: Int) {
        val current = _state.value
        val updatedAccounts = current.accounts.map { account ->
            if (account.runId == runId) {
                account.copy(totalSteps = totalSteps, segmentsWritten = segmentsWritten)
            } else {
                account
            }
        }
        _state.value = current.copy(accounts = updatedAccounts).withCurrentTiming()
    }

    fun markAccountFinished(runId: String, status: RunStatus, errorMessage: String? = null) {
        val current = _state.value
        val updatedAccounts = current.accounts.map { account ->
            if (account.runId == runId) {
                account.copy(status = status, errorMessage = errorMessage)
            } else {
                account
            }
        }
        val runningCount = updatedAccounts.count { it.status == RunStatus.RUNNING }
        val totalCount = updatedAccounts.size
        val statusLabel = if (totalCount > 0) "$runningCount/$totalCount 進行中" else ""
        val sessionStillActive = runningCount > 0
        _state.value = RunSessionUiState(
            session = current.session.copy(
                sessionStatusLabel = statusLabel,
                isActive = sessionStillActive,
                remainingMillis = if (sessionStillActive) current.session.remainingMillis else 0L,
            ),
            accounts = updatedAccounts,
        ).withCurrentTiming()
    }

    fun clear() {
        _state.value = RunSessionUiState()
    }
}
