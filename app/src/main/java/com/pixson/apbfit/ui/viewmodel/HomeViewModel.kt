package com.pixson.apbfit.ui.viewmodel

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixson.apbfit.data.model.IntensityLevel
import com.pixson.apbfit.data.model.RunConfig
import com.pixson.apbfit.data.repository.AccountRepository
import com.pixson.apbfit.data.repository.RunRepository
import com.pixson.apbfit.domain.EnvironmentCheck
import com.pixson.apbfit.domain.EnvironmentChecker
import com.pixson.apbfit.domain.fit.FailingFitWriter
import com.pixson.apbfit.domain.fit.FitWriter
import com.pixson.apbfit.domain.fit.SegmentGenerator
import com.pixson.apbfit.service.RunServiceStarter
import com.pixson.apbfit.service.RunStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class HomeUiState(
    val activeAccountEmail: String? = null,
    val activeAccountId: String? = null,
    val knownAccounts: List<AccountSummary> = emptyList(),
    val environmentChecks: List<EnvironmentCheck> = emptyList(),
    val selectedIntensity: IntensityLevel = IntensityLevel.BRISK_WALK,
    val durationMinutes: Int = HomeViewModel.DEFAULT_DURATION_MINUTES,
    val batchSize: Int = HomeViewModel.DEFAULT_BATCH_SIZE,
    val statusMessage: String? = null,
    val isBusy: Boolean = false,
    val canStartRun: Boolean = false,
)

data class AccountSummary(
    val id: String,
    val email: String,
    val isActive: Boolean,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val runRepository: RunRepository,
    private val runServiceStarter: RunServiceStarter,
    private val runStateHolder: RunStateHolder,
    private val fitWriter: FitWriter,
    private val segmentGenerator: SegmentGenerator,
    private val environmentChecker: EnvironmentChecker,
) : ViewModel() {
    private val statusMessage = MutableStateFlow<String?>(null)
    private val isBusy = MutableStateFlow(false)
    private val selectedIntensity = MutableStateFlow(IntensityLevel.BRISK_WALK)
    private val durationMinutes = MutableStateFlow(DEFAULT_DURATION_MINUTES)
    private val batchSize = MutableStateFlow(DEFAULT_BATCH_SIZE)
    private val environmentChecks = MutableStateFlow<List<EnvironmentCheck>>(emptyList())

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            accountRepository.activeAccount,
            statusMessage,
            isBusy,
        ) { active, status, busy -> Triple(active, status, busy) },
        combine(
            selectedIntensity,
            durationMinutes,
            batchSize,
            environmentChecks,
        ) { intensity, duration, batch, checks ->
            ConfigSnapshot(intensity, duration, batch, checks)
        },
    ) { (active, status, busy), config ->
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
            environmentChecks = config.checks,
            selectedIntensity = config.intensity,
            durationMinutes = config.duration,
            batchSize = config.batch,
            statusMessage = status,
            isBusy = busy,
            canStartRun = active != null && !busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refreshEnvironmentChecks()
        viewModelScope.launch {
            recoverStaleRunIfNeeded(showMessage = false)
        }
    }

    fun refreshEnvironmentChecks() {
        val account = accountRepository.activeAccount.value // StateFlow snapshot for checks
        environmentChecks.value = environmentChecker.evaluate(
            account = account,
            fitnessOptions = accountRepository.fitnessOptions,
        )
    }

    fun setIntensity(level: IntensityLevel) {
        selectedIntensity.value = level
    }

    fun setDurationMinutes(minutes: Int) {
        durationMinutes.value = minutes.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
    }

    fun snapDurationFromSlider(value: Float) {
        val snapped = ((value / DURATION_STEP_MINUTES).roundToInt() * DURATION_STEP_MINUTES)
            .coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
        durationMinutes.value = snapped
    }

    fun setBatchSize(size: Int) {
        batchSize.value = size.coerceIn(MIN_BATCH_SIZE, MAX_BATCH_SIZE)
    }

    fun switchAccount(accountId: String) {
        viewModelScope.launch {
            val result = accountRepository.switchAccount(accountId)
            statusMessage.value = result.exceptionOrNull()?.message ?: "Switched account."
            refreshEnvironmentChecks()
        }
    }

    fun addAccountIntent(): Intent = accountRepository.getSignInIntent()

    fun onAddAccountResult(data: Intent?) {
        viewModelScope.launch {
            val result = accountRepository.handleSignInResult(data)
            statusMessage.value = result.fold(
                onSuccess = { "Added ${it.email}" },
                onFailure = { it.message ?: "Sign-in failed." },
            )
            refreshEnvironmentChecks()
        }
    }

    fun startRun() {
        viewModelScope.launch {
            isBusy.value = true
            var createdRunId: String? = null
            runCatching {
                val account = accountRepository.requireActiveAccount()
                Log.d(TAG, "Start run requested for account=${account.email}")
                val runId = runRepository.startRun(
                    RunConfig(
                        accountId = account.id!!,
                        durationMinutes = durationMinutes.value,
                        intensityLevel = selectedIntensity.value,
                        batchSize = batchSize.value,
                    ),
                )
                createdRunId = runId
                Log.d(TAG, "Run row created runId=$runId, starting foreground service")
                runServiceStarter.startRun(runId)
            }.onSuccess {
                statusMessage.value = null
                Log.d(TAG, "Foreground service start requested successfully")
            }.onFailure { error ->
                Log.e(TAG, "Start run failed: ${error.message}", error)
                if (error.message == RUN_ALREADY_ACTIVE_MESSAGE) {
                    val recovered = recoverStaleRunIfNeeded(showMessage = true)
                    if (recovered) {
                        statusMessage.value = RECOVERED_RUN_MESSAGE
                    } else {
                        statusMessage.value = error.message
                    }
                } else {
                    createdRunId?.let { runId ->
                        runRepository.abandonRun(runId, error.message ?: "Failed to start run.")
                    }
                    statusMessage.value = error.message ?: "Failed to start run."
                }
            }
            isBusy.value = false
        }
    }

    /** DB says RUNNING but no in-memory active run → service died mid-run; finalize as STOPPED. */
    private suspend fun recoverStaleRunIfNeeded(showMessage: Boolean): Boolean {
        val dbActive = runRepository.getActiveRun() ?: return false
        if (runStateHolder.state.value.isActive) return false
        runRepository.recoverOrphanedRuns()
        runStateHolder.clear()
        Log.w(TAG, "Recovered stale RUNNING run id=${dbActive.id}")
        if (showMessage) {
            statusMessage.value = RECOVERED_RUN_MESSAGE
        }
        return true
    }

    fun batteryOptimizationIntent(): Intent = environmentChecker.batteryOptimizationIntent()

    fun googleFitIntent(): Intent = environmentChecker.googleFitIntent()

    fun notificationSettingsIntent(): Intent = environmentChecker.notificationSettingsIntent()

    fun getFitnessPermissionsIntent(): Intent = accountRepository.getFitnessPermissionsIntent()

    fun onFitnessPermissionResult(data: Intent?) {
        viewModelScope.launch {
            val result = accountRepository.handleFitnessPermissionResult(data)
            statusMessage.value = result.fold(
                onSuccess = { "Google Fit permissions updated." },
                onFailure = {
                    when {
                        it is IllegalStateException ->
                            "Google Fit permissions incomplete. Select your account again and allow all requested access."
                        else -> it.message ?: "Google Fit permission request was cancelled."
                    }
                },
            )
            refreshEnvironmentChecks()
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

    fun startDebugRun(forceFailNextWrite: Boolean = false) {
        viewModelScope.launch {
            isBusy.value = true
            runCatching {
                val account = accountRepository.requireActiveAccount()
                val runId = runRepository.startRun(
                    RunConfig(
                        accountId = account.id!!,
                        durationMinutes = DEBUG_RUN_DURATION_MINUTES,
                        intensityLevel = IntensityLevel.BRISK_WALK,
                        batchSize = 1,
                    ),
                )
                runServiceStarter.startRun(runId, forceFailNextWrite)
            }.onSuccess {
                statusMessage.value = "Debug run started."
            }.onFailure {
                statusMessage.value = it.message ?: "Failed to start run."
            }
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

    private data class ConfigSnapshot(
        val intensity: IntensityLevel,
        val duration: Int,
        val batch: Int,
        val checks: List<EnvironmentCheck>,
    )

    companion object {
        private const val TAG = "APBFit_Run"
        private const val RUN_ALREADY_ACTIVE_MESSAGE = "A run is already active."
        private const val RECOVERED_RUN_MESSAGE =
            "Previous run was interrupted and marked STOPPED. Press Start again."
        const val MIN_DURATION_MINUTES = 5
        const val MAX_DURATION_MINUTES = 360
        const val DURATION_STEP_MINUTES = 5
        const val DEFAULT_DURATION_MINUTES = 30
        const val MIN_BATCH_SIZE = 1
        const val MAX_BATCH_SIZE = 10
        const val DEFAULT_BATCH_SIZE = 3
        private const val DEBUG_RUN_DURATION_MINUTES = 5
    }
}
