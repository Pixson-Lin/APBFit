package com.pixson.apbfit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixson.apbfit.data.model.IntensityLevel
import com.pixson.apbfit.data.repository.AccountRepository
import com.pixson.apbfit.domain.fit.FailingFitWriter
import com.pixson.apbfit.domain.fit.FitWriter
import com.pixson.apbfit.domain.fit.SegmentGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val activeAccountEmail: String? = null,
    val activeAccountId: String? = null,
    val knownAccounts: List<AccountSummary> = emptyList(),
    val hasFitnessPermissions: Boolean = false,
    val statusMessage: String? = null,
    val isBusy: Boolean = false,
)

data class AccountSummary(
    val id: String,
    val email: String,
    val isActive: Boolean,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val fitWriter: FitWriter,
    private val segmentGenerator: SegmentGenerator,
) : ViewModel() {
    private val statusMessage = MutableStateFlow<String?>(null)
    private val isBusy = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        accountRepository.activeAccount,
        statusMessage,
        isBusy,
    ) { active, status, busy ->
        val known = accountRepository.getKnownAccounts().map { account ->
            AccountSummary(
                id = account.id.orEmpty(),
                email = account.email.orEmpty(),
                isActive = account.id == active?.id,
            )
        }
        HomeUiState(
            activeAccountEmail = active?.email,
            activeAccountId = active?.id,
            knownAccounts = known,
            hasFitnessPermissions = active?.let { accountRepository.hasFitnessPermissions(it) } ?: false,
            statusMessage = status,
            isBusy = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun switchAccount(accountId: String) {
        viewModelScope.launch {
            val result = accountRepository.switchAccount(accountId)
            statusMessage.value = result.exceptionOrNull()?.message ?: "Switched account."
        }
    }

    fun addAccountIntent(): android.content.Intent = accountRepository.getSignInIntent()

    fun onAddAccountResult(data: android.content.Intent?) {
        viewModelScope.launch {
            val result = accountRepository.handleSignInResult(data)
            statusMessage.value = result.fold(
                onSuccess = { "Added ${it.email}" },
                onFailure = { it.message ?: "Sign-in failed." },
            )
        }
    }

    fun ensureDataSources() {
        viewModelScope.launch {
            isBusy.value = true
            val account = runCatching { accountRepository.requireActiveAccount() }
            val result = account.fold(
                onSuccess = { fitWriter.ensureDataSources(it) },
                onFailure = { Result.failure(it) },
            )
            statusMessage.value = result.fold(
                onSuccess = { "DataSources ready (cached or created)." },
                onFailure = { it.message ?: "DataSource setup failed." },
            )
            isBusy.value = false
        }
    }

    fun writeTestBatch() {
        viewModelScope.launch {
            isBusy.value = true
            val account = runCatching { accountRepository.requireActiveAccount() }
            val now = System.currentTimeMillis()
            val segment = segmentGenerator.generate(
                index = 0,
                startMillis = now - 30_000L,
                level = IntensityLevel.BRISK_WALK,
            )
            val result = account.fold(
                onSuccess = { fitWriter.writeSegments(it, listOf(segment)) },
                onFailure = { Result.failure(it) },
            )
            statusMessage.value = result.fold(
                onSuccess = { "Test batch written (${segment.steps} steps)." },
                onFailure = { it.message ?: "Write failed." },
            )
            isBusy.value = false
        }
    }

    fun testInjectedFailure() {
        viewModelScope.launch {
            isBusy.value = true
            val account = runCatching { accountRepository.requireActiveAccount() }
            val segment = segmentGenerator.generate(
                index = 0,
                startMillis = System.currentTimeMillis() - 30_000L,
                level = IntensityLevel.JOG,
            )
            val result = account.fold(
                onSuccess = { FailingFitWriter().writeSegments(it, listOf(segment)) },
                onFailure = { Result.failure(it) },
            )
            statusMessage.value = result.fold(
                onSuccess = { "Unexpected success." },
                onFailure = { "Injected failure: ${it.message}" },
            )
            isBusy.value = false
        }
    }
}
