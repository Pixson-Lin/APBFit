package com.pixson.apbfit.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixson.apbfit.data.repository.AccountRepository
import com.pixson.apbfit.data.repository.RunRepository
import com.pixson.apbfit.domain.EnvironmentChecker
import com.pixson.apbfit.service.RunSessionStateHolder
import com.pixson.apbfit.ui.util.UiStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsAccountItem(
    val id: String,
    val email: String,
    val isActive: Boolean,
)

data class SettingsUiState(
    val activeAccountEmail: String? = null,
    val accounts: List<SettingsAccountItem> = emptyList(),
    val canSwitchAccount: Boolean = true,
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
    private val environmentChecker: EnvironmentChecker,
    private val uiStrings: UiStrings,
) : ViewModel() {
    private val statusMessage = MutableStateFlow<String?>(null)
    private val showClearHistoryConfirm = MutableStateFlow(false)
    private val showRecoverOrphanConfirm = MutableStateFlow(false)
    private val orphanRunningCount = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            accountRepository.activeAccount,
            runSessionStateHolder.state.map { it.session.isActive },
            orphanRunningCount,
        ) { active, runActive, orphanCount -> Triple(active, runActive, orphanCount) },
        combine(
            statusMessage,
            showClearHistoryConfirm,
            showRecoverOrphanConfirm,
        ) { status, showClearConfirm, showRecoverConfirm ->
            Triple(status, showClearConfirm, showRecoverConfirm)
        },
    ) { (active, runActive, orphanCount), (status, showClearConfirm, showRecoverConfirm) ->
        val accounts = accountRepository.getKnownAccounts().map { account ->
            SettingsAccountItem(
                id = account.id.orEmpty(),
                email = account.email.orEmpty(),
                isActive = account.id == active?.id,
            )
        }
        SettingsUiState(
            activeAccountEmail = active?.email,
            accounts = accounts,
            canSwitchAccount = !runActive,
            showRecoverOrphanButton = orphanCount > 0 && !runActive,
            statusMessage = status,
            showClearHistoryConfirm = showClearConfirm,
            showRecoverOrphanConfirm = showRecoverConfirm,
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

    fun launchAddAccount(launchIntent: (Intent) -> Unit) {
        viewModelScope.launch {
            runCatching {
                accountRepository.getAddAccountIntent()
            }.onSuccess { intent ->
                launchIntent(intent)
            }.onFailure {
                statusMessage.value = it.message ?: uiStrings.signInFailed
            }
        }
    }

    fun onAddAccountResult(data: Intent?) {
        viewModelScope.launch {
            val result = accountRepository.handleSignInResult(data)
            statusMessage.value = result.fold(
                onSuccess = { uiStrings.addedAccount(it.email.orEmpty()) },
                onFailure = { it.message ?: uiStrings.signInFailed },
            )
        }
    }

    fun switchAccount(accountId: String) {
        if (!uiState.value.canSwitchAccount) {
            statusMessage.value = uiStrings.cannotSwitchDuringRun
            return
        }
        viewModelScope.launch {
            val result = accountRepository.switchAccount(accountId)
            statusMessage.value = result.exceptionOrNull()?.message ?: uiStrings.switchedAccount
        }
    }

    fun signOutCurrentAccount() {
        if (!uiState.value.canSwitchAccount) {
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
            val recovered = runRepository.recoverOrphanedSessions(uiStrings.recoveredAfterRestart)
            runSessionStateHolder.clear()
            showRecoverOrphanConfirm.value = false
            refreshOrphanState()
            statusMessage.value = if (recovered > 0) {
                uiStrings.recoveredRun
            } else {
                uiStrings.recoveredOrphanNone
            }
        }
    }

    fun requestClearHistoryConfirm() {
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

    fun googleFitIntent(): Intent = environmentChecker.googleFitIntent()
}
