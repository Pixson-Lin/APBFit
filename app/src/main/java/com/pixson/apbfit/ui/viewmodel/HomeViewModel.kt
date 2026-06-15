package com.pixson.apbfit.ui.viewmodel

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixson.apbfit.data.model.IntensityLevel
import com.pixson.apbfit.data.model.RunAlreadyActiveException
import com.pixson.apbfit.data.model.RunSessionConfig
import com.pixson.apbfit.data.repository.AccountRepository
import com.pixson.apbfit.data.repository.RunRepository
import com.pixson.apbfit.domain.CheckStatus
import com.pixson.apbfit.domain.EnvironmentCheck
import com.pixson.apbfit.domain.EnvironmentCheckId
import com.pixson.apbfit.domain.EnvironmentChecker
import com.pixson.apbfit.domain.fit.FailingFitWriter
import com.pixson.apbfit.domain.fit.FitWriter
import com.pixson.apbfit.domain.fit.SegmentGenerator
import com.pixson.apbfit.service.RunServiceStarter
import com.pixson.apbfit.service.RunSessionStateHolder
import com.pixson.apbfit.ui.util.UiStrings
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
    val startBlockedReason: String? = null,
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
    private val runSessionStateHolder: RunSessionStateHolder,
    private val fitWriter: FitWriter,
    private val segmentGenerator: SegmentGenerator,
    private val environmentChecker: EnvironmentChecker,
    private val uiStrings: UiStrings,
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
        val envReady = isEnvironmentReadyForRun(config.checks)
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
            canStartRun = active != null && !busy && envReady,
            startBlockedReason = if (active != null && !envReady) {
                uiStrings.get(com.pixson.apbfit.R.string.start_run_blocked)
            } else {
                null
            },
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
            statusMessage.value = result.exceptionOrNull()?.message ?: uiStrings.switchedAccount
            refreshEnvironmentChecks()
        }
    }

    fun addAccountIntent(): Intent = accountRepository.getSignInIntent()

    fun onAddAccountResult(data: Intent?) {
        viewModelScope.launch {
            val result = accountRepository.handleSignInResult(data)
            statusMessage.value = result.fold(
                onSuccess = { uiStrings.addedAccount(it.email.orEmpty()) },
                onFailure = { it.message ?: uiStrings.signInFailed },
            )
            refreshEnvironmentChecks()
        }
    }

    fun startRun() {
        viewModelScope.launch {
            isBusy.value = true
            var createdSessionId: String? = null
            runCatching {
                val account = accountRepository.requireActiveAccount()
                Log.d(TAG, "Start session requested for account=${account.email}")
                val result = runRepository.startSession(
                    RunSessionConfig(
                        durationMinutes = durationMinutes.value,
                        intensityLevel = selectedIntensity.value,
                        batchSize = batchSize.value,
                    ),
                    listOf(account.id!!),
                )
                createdSessionId = result.sessionId
                Log.d(TAG, "Session rows created sessionId=${result.sessionId}, starting foreground service")
                runServiceStarter.startSession(result.sessionId)
            }.onSuccess {
                statusMessage.value = null
                Log.d(TAG, "Foreground service start requested successfully")
            }.onFailure { error ->
                Log.e(TAG, "Start session failed: ${error.message}", error)
                when (error) {
                    is RunAlreadyActiveException -> {
                        val recovered = recoverStaleRunIfNeeded(showMessage = true)
                        if (!recovered) {
                            statusMessage.value = uiStrings.runAlreadyActive
                        }
                    }
                    else -> {
                        createdSessionId?.let { sessionId ->
                            runRepository.getRunsBySessionId(sessionId).forEach { run ->
                                runRepository.abandonRun(
                                    run.id,
                                    error.message ?: uiStrings.failedStartRun,
                                )
                            }
                        }
                        statusMessage.value = error.message ?: uiStrings.failedStartRun
                    }
                }
            }
            isBusy.value = false
        }
    }

    /** DB says RUNNING but no in-memory active session → service died mid-run; finalize as STOPPED. */
    private suspend fun recoverStaleRunIfNeeded(showMessage: Boolean): Boolean {
        val dbActive = runRepository.getAllActiveRuns()
        if (dbActive.isEmpty()) return false
        if (runSessionStateHolder.isActive) return false
        runRepository.recoverOrphanedSessions(uiStrings.recoveredAfterRestart)
        runSessionStateHolder.clear()
        Log.w(TAG, "Recovered ${dbActive.size} stale RUNNING run(s)")
        if (showMessage) {
            statusMessage.value = uiStrings.recoveredRun
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
                onSuccess = { uiStrings.fitPermissionsUpdated },
                onFailure = {
                    when {
                        it is IllegalStateException -> uiStrings.fitPermissionsIncomplete
                        else -> it.message ?: uiStrings.fitPermissionCancelled
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
                onSuccess = { uiStrings.dataSourcesReady },
                onFailure = { it.message ?: uiStrings.dataSourceSetupFailed },
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
                onSuccess = { uiStrings.testBatchWritten(segment.steps) },
                onFailure = { it.message ?: uiStrings.writeFailed },
            )
            isBusy.value = false
        }
    }

    fun startDebugRun(forceFailNextWrite: Boolean = false) {
        viewModelScope.launch {
            isBusy.value = true
            runCatching {
                val accounts = accountRepository.getKnownAccounts().take(DEBUG_SESSION_ACCOUNT_COUNT)
                if (accounts.size < DEBUG_SESSION_ACCOUNT_COUNT) {
                    throw IllegalStateException(uiStrings.debugRequiresTwoAccounts)
                }
                val accountIds = accounts.mapNotNull { it.id }
                if (accountIds.size < DEBUG_SESSION_ACCOUNT_COUNT) {
                    throw IllegalStateException(uiStrings.debugRequiresTwoAccounts)
                }
                val result = runRepository.startSession(
                    RunSessionConfig(
                        durationMinutes = DEBUG_RUN_DURATION_MINUTES,
                        intensityLevel = IntensityLevel.BRISK_WALK,
                        batchSize = 1,
                    ),
                    accountIds,
                )
                val forceFailRunId = if (forceFailNextWrite) result.runs.first().runId else null
                runServiceStarter.startSession(result.sessionId, forceFailRunId)
            }.onSuccess {
                statusMessage.value = uiStrings.debugRunStarted
            }.onFailure {
                statusMessage.value = it.message ?: uiStrings.failedStartRun
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
                onSuccess = { uiStrings.unexpectedSuccess },
                onFailure = { uiStrings.injectedFailure(it.message) },
            )
            isBusy.value = false
        }
    }

    private fun isEnvironmentReadyForRun(checks: List<EnvironmentCheck>): Boolean {
        val required = setOf(
            EnvironmentCheckId.GOOGLE_FIT_INSTALLED,
            EnvironmentCheckId.FITNESS_PERMISSIONS,
        )
        return checks.filter { it.id in required }.all { it.status == CheckStatus.PASS }
    }

    private data class ConfigSnapshot(
        val intensity: IntensityLevel,
        val duration: Int,
        val batch: Int,
        val checks: List<EnvironmentCheck>,
    )

    companion object {
        private const val TAG = "APBFit_Run"
        const val MIN_DURATION_MINUTES = 5
        const val MAX_DURATION_MINUTES = 360
        const val DURATION_STEP_MINUTES = 5
        const val DEFAULT_DURATION_MINUTES = 30
        const val MIN_BATCH_SIZE = 1
        const val MAX_BATCH_SIZE = 10
        const val DEFAULT_BATCH_SIZE = 3
        private const val DEBUG_RUN_DURATION_MINUTES = 5
        private const val DEBUG_SESSION_ACCOUNT_COUNT = 2
    }
}
