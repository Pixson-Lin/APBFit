package com.pixsonlin.apbfit.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixsonlin.apbfit.data.repository.AccountRepository
import com.pixsonlin.apbfit.data.repository.RunRepository
import com.pixsonlin.apbfit.domain.EnvironmentChecker
import com.pixsonlin.apbfit.service.RunServiceStarter
import com.pixsonlin.apbfit.service.RunSessionStateHolder
import com.pixsonlin.apbfit.ui.util.UiStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val signedInEmail: String? = null,
    val showRecoverOrphanButton: Boolean = false,
    val statusMessage: String? = null,
    val showClearHistoryConfirm: Boolean = false,
    val showRecoverOrphanConfirm: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val runRepository: RunRepository,
    private val runSessionStateHolder: RunSessionStateHolder,
    private val runServiceStarter: RunServiceStarter,
    private val environmentChecker: EnvironmentChecker,
    private val uiStrings: UiStrings,
) : ViewModel() {
    private val statusMessage = MutableStateFlow<String?>(null)
    private val showClearHistoryConfirm = MutableStateFlow(false)
    private val showRecoverOrphanConfirm = MutableStateFlow(false)
    private val orphanRunningCount = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            accountRepository.accountRevision,
            runSessionStateHolder.state.map { it.session.isActive },
            orphanRunningCount,
        ) { _, runActive, orphanCount ->
            AccountSnapshot(runActive, orphanCount)
        },
        combine(
            statusMessage,
            showClearHistoryConfirm,
            showRecoverOrphanConfirm,
        ) { status, showClearConfirm, showRecoverConfirm ->
            DialogSnapshot(status, showClearConfirm, showRecoverConfirm)
        },
    ) { accountSnapshot, dialogSnapshot ->
        SettingsUiState(
            signedInEmail = accountRepository.activeAccount.value?.email,
            showRecoverOrphanButton = accountSnapshot.orphanCount > 0 && !accountSnapshot.runActive,
            statusMessage = dialogSnapshot.status,
            showClearHistoryConfirm = dialogSnapshot.showClearConfirm,
            showRecoverOrphanConfirm = dialogSnapshot.showRecoverConfirm,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refreshOrphanState()
    }

    fun refreshOrphanState() {
        viewModelScope.launch {
            orphanRunningCount.value = if (runSessionStateHolder.isActive) {
                0
            } else {
                runRepository.getAllActiveRuns().size
            }
        }
    }

    fun signOut() {
        if (runSessionStateHolder.isActive) {
            statusMessage.value = uiStrings.cannotSignOutDuringRun
            return
        }
        viewModelScope.launch {
            accountRepository.signOutCurrentAccount()
            statusMessage.value = uiStrings.signedOut
        }
    }

    fun requestRecoverOrphanConfirm() {
        showRecoverOrphanConfirm.value = true
    }

    fun dismissRecoverOrphanConfirm() {
        showRecoverOrphanConfirm.value = false
    }

    fun confirmRecoverOrphanSessions() {
        viewModelScope.launch {
            if (runSessionStateHolder.isActive) {
                showRecoverOrphanConfirm.value = false
                return@launch
            }
            val sessionIds = runRepository.getOrphanSessionIds()
            sessionIds.forEach { sessionId ->
                runServiceStarter.finalizeOrphanSession(
                    sessionId,
                    uiStrings.recoveredAfterRestart,
                )
            }
            showRecoverOrphanConfirm.value = false
            refreshOrphanState()
            statusMessage.value = if (sessionIds.isNotEmpty()) {
                uiStrings.recoveredRun
            } else {
                uiStrings.recoveredOrphanNone
            }
        }
    }

    fun requestClearHistoryConfirm() {
        if (accountRepository.getActiveAccountId() == null) {
            statusMessage.value = uiStrings.accountNotAvailable
            return
        }
        showClearHistoryConfirm.value = true
    }

    fun dismissClearHistoryConfirm() {
        showClearHistoryConfirm.value = false
    }

    fun confirmClearHistory() {
        viewModelScope.launch {
            val accountId = accountRepository.getActiveAccountId() ?: return@launch
            runRepository.clearForAccount(accountId)
            showClearHistoryConfirm.value = false
            statusMessage.value = uiStrings.historyCleared
        }
    }

    fun batteryOptimizationIntent(): Intent = environmentChecker.batteryOptimizationIntent()

    fun appDetailsIntent(): Intent = environmentChecker.appDetailsIntent()

    fun notificationSettingsIntent(): Intent = environmentChecker.notificationSettingsIntent()

    fun healthConnectSettingsIntent(): Intent = environmentChecker.healthConnectSettingsIntent()

    private data class AccountSnapshot(
        val runActive: Boolean,
        val orphanCount: Int,
    )

    private data class DialogSnapshot(
        val status: String?,
        val showClearConfirm: Boolean,
        val showRecoverConfirm: Boolean,
    )
}
