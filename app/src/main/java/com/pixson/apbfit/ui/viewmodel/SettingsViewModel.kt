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
    val statusMessage: String? = null,
    val showClearHistoryConfirm: Boolean = false,
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

    val uiState: StateFlow<SettingsUiState> = combine(
        accountRepository.activeAccount,
        runSessionStateHolder.state.map { it.session.isActive },
        statusMessage,
        showClearHistoryConfirm,
    ) { active, runActive, status, showConfirm ->
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
            statusMessage = status,
            showClearHistoryConfirm = showConfirm,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun getSignInIntent(): Intent = accountRepository.getSignInIntent()

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
