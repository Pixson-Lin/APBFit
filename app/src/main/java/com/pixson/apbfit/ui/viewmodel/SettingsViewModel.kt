package com.pixson.apbfit.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixson.apbfit.data.prefs.HistoryAccountPrefs
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

data class SettingsUiState(
    val accounts: List<HistoryAccountOption> = emptyList(),
    val selectedAccountId: String? = null,
    val selectedAccountEmail: String = "",
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
    private val historyAccountPrefs: HistoryAccountPrefs,
    private val environmentChecker: EnvironmentChecker,
    private val uiStrings: UiStrings,
) : ViewModel() {
    private val selectedAccountId = MutableStateFlow<String?>(null)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val showClearHistoryConfirm = MutableStateFlow(false)
    private val showRecoverOrphanConfirm = MutableStateFlow(false)
    private val orphanRunningCount = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            accountRepository.accountRevision,
            selectedAccountId,
            runSessionStateHolder.state.map { it.session.isActive },
            orphanRunningCount,
        ) { _, accountId, runActive, orphanCount ->
            AccountSnapshot(accountId, runActive, orphanCount)
        },
        combine(
            statusMessage,
            showClearHistoryConfirm,
            showRecoverOrphanConfirm,
        ) { status, showClearConfirm, showRecoverConfirm ->
            DialogSnapshot(status, showClearConfirm, showRecoverConfirm)
        },
    ) { accountSnapshot, dialogSnapshot ->
        val accountOptions = accountRepository.getKnownAccounts().map { account ->
            HistoryAccountOption(
                id = account.id.orEmpty(),
                email = account.email.orEmpty(),
            )
        }
        val selectedEmail = accountOptions.firstOrNull { it.id == accountSnapshot.accountId }?.email.orEmpty()
        SettingsUiState(
            accounts = accountOptions,
            selectedAccountId = accountSnapshot.accountId,
            selectedAccountEmail = selectedEmail,
            showRecoverOrphanButton = accountSnapshot.orphanCount > 0 && !accountSnapshot.runActive,
            statusMessage = dialogSnapshot.status,
            showClearHistoryConfirm = dialogSnapshot.showClearConfirm,
            showRecoverOrphanConfirm = dialogSnapshot.showRecoverConfirm,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            resolveSelectedAccount()
            accountRepository.accountRevision.collect {
                resolveSelectedAccount()
            }
        }
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

    fun selectAccount(accountId: String) {
        selectedAccountId.value = accountId
        historyAccountPrefs.setSelectedAccountId(accountId)
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
        if (selectedAccountId.value == null) {
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
            val accountId = selectedAccountId.value ?: return@launch
            runRepository.clearForAccount(accountId)
            showClearHistoryConfirm.value = false
            statusMessage.value = uiStrings.historyCleared
        }
    }

    fun batteryOptimizationIntent(): Intent = environmentChecker.batteryOptimizationIntent()

    fun appDetailsIntent(): Intent = environmentChecker.appDetailsIntent()

    fun notificationSettingsIntent(): Intent = environmentChecker.notificationSettingsIntent()

    fun googleFitIntent(): Intent = environmentChecker.googleFitIntent()

    private fun resolveSelectedAccount() {
        val known = accountRepository.getKnownAccounts()
        val knownIds = known.mapNotNull { it.id }.toSet()
        val current = selectedAccountId.value
        val saved = historyAccountPrefs.getSelectedAccountId()
        val resolved = when {
            current != null && current in knownIds -> current
            saved != null && saved in knownIds -> saved
            knownIds.isNotEmpty() -> knownIds.first()
            else -> null
        }
        if (resolved != current) {
            selectedAccountId.value = resolved
        }
        if (resolved != null) {
            historyAccountPrefs.setSelectedAccountId(resolved)
        } else {
            historyAccountPrefs.clearSelectedAccountId()
        }
    }

    private data class AccountSnapshot(
        val accountId: String?,
        val runActive: Boolean,
        val orphanCount: Int,
    )

    private data class DialogSnapshot(
        val status: String?,
        val showClearConfirm: Boolean,
        val showRecoverConfirm: Boolean,
    )
}
