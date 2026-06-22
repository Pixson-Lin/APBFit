package com.pixson.apbfit.ui.viewmodel

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.pixson.apbfit.data.model.IntensityLevel
import com.pixson.apbfit.data.model.RunAlreadyActiveException
import com.pixson.apbfit.data.model.RunSessionConfig
import com.pixson.apbfit.data.prefs.EnabledAccountsPrefs
import com.pixson.apbfit.data.prefs.RunConfigPrefs
import com.pixson.apbfit.data.repository.AccountRepository
import com.pixson.apbfit.data.repository.RunRepository
import com.pixson.apbfit.domain.CheckStatus
import com.pixson.apbfit.domain.CompactEnvironmentState
import com.pixson.apbfit.domain.EnvironmentChecker
import com.pixson.apbfit.domain.PreflightException
import com.pixson.apbfit.domain.SessionPreflight
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class HomeUiState(
    val enabledAccounts: List<EnabledAccountSummary> = emptyList(),
    val accountEditItems: List<AccountEditItem> = emptyList(),
    val environmentIcons: CompactEnvironmentState = CompactEnvironmentState(
        battery = CheckStatus.WARN,
        fit = CheckStatus.WARN,
        notifications = CheckStatus.WARN,
    ),
    val selectedIntensity: IntensityLevel = IntensityLevel.BRISK_WALK,
    val durationMinutes: Int = HomeViewModel.DEFAULT_DURATION_MINUTES,
    val batchSize: Int = HomeViewModel.DEFAULT_BATCH_SIZE,
    val statusMessage: String? = null,
    val isBusy: Boolean = false,
    val canStartRun: Boolean = false,
    val startBlockedReason: String? = null,
    val isSessionActive: Boolean = false,
    val isConfigLocked: Boolean = false,
    val showAccountEditSheet: Boolean = false,
)

data class EnabledAccountSummary(
    val id: String,
    val email: String,
)

data class AccountEditItem(
    val id: String,
    val email: String,
    val isEnabled: Boolean,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val runRepository: RunRepository,
    private val runServiceStarter: RunServiceStarter,
    private val runSessionStateHolder: RunSessionStateHolder,
    private val enabledAccountsPrefs: EnabledAccountsPrefs,
    private val runConfigPrefs: RunConfigPrefs,
    private val fitWriter: FitWriter,
    private val segmentGenerator: SegmentGenerator,
    private val environmentChecker: EnvironmentChecker,
    private val sessionPreflight: SessionPreflight,
    private val uiStrings: UiStrings,
) : ViewModel() {
    private val statusMessage = MutableStateFlow<String?>(null)
    private val isBusy = MutableStateFlow(false)
    private val selectedIntensity = MutableStateFlow(IntensityLevel.BRISK_WALK)
    private val durationMinutes = MutableStateFlow(DEFAULT_DURATION_MINUTES)
    private val batchSize = MutableStateFlow(DEFAULT_BATCH_SIZE)
    private val enabledAccountIds = MutableStateFlow(emptySet<String>())
    private val showAccountEditSheet = MutableStateFlow(false)
    private val environmentIcons = MutableStateFlow(
        CompactEnvironmentState(
            battery = CheckStatus.WARN,
            fit = CheckStatus.WARN,
            notifications = CheckStatus.WARN,
        ),
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            accountRepository.accountRevision,
            enabledAccountIds,
            statusMessage,
            isBusy,
            showAccountEditSheet,
        ) { _, enabledIds, status, busy, showSheet ->
            AccountSnapshotPartial(enabledIds, status, busy, showSheet)
        },
        combine(
            environmentIcons,
            selectedIntensity,
            durationMinutes,
            batchSize,
            runSessionStateHolder.state.map { it.session.isActive },
        ) { envIcons, intensity, duration, batch, sessionActive ->
            ConfigSnapshot(intensity, duration, batch, sessionActive, envIcons)
        },
    ) { accountPartial, config ->
        val knownAccounts = accountRepository.getKnownAccounts()
        val enabledSummaries = knownAccounts
            .filter { it.id in accountPartial.enabledIds }
            .map { EnabledAccountSummary(it.id.orEmpty(), it.email.orEmpty()) }
        val editItems = knownAccounts.map { account ->
            AccountEditItem(
                id = account.id.orEmpty(),
                email = account.email.orEmpty(),
                isEnabled = account.id in accountPartial.enabledIds,
            )
        }
        val envReady = isEnvironmentReadyForRun(config.envIcons)
        val canStart = enabledSummaries.isNotEmpty() &&
            !accountPartial.busy &&
            !config.sessionActive &&
            envReady
        HomeUiState(
            enabledAccounts = enabledSummaries,
            accountEditItems = editItems,
            environmentIcons = config.envIcons,
            selectedIntensity = config.intensity,
            durationMinutes = config.duration,
            batchSize = config.batch,
            statusMessage = accountPartial.status,
            isBusy = accountPartial.busy,
            canStartRun = canStart,
            startBlockedReason = when {
                config.sessionActive -> uiStrings.get(com.pixson.apbfit.R.string.start_run_blocked_session_active)
                enabledSummaries.isEmpty() -> uiStrings.get(com.pixson.apbfit.R.string.error_no_enabled_accounts)
                !envReady -> uiStrings.get(com.pixson.apbfit.R.string.start_run_blocked)
                else -> null
            },
            isSessionActive = config.sessionActive,
            isConfigLocked = config.sessionActive,
            showAccountEditSheet = accountPartial.showSheet,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            migrateEnabledAccountsIfNeeded()
            enabledAccountIds.value = enabledAccountsPrefs.getEnabledAccountIds()
            loadSavedRunConfig()
            refreshEnvironmentChecks()
            recoverStaleRunIfNeeded(showMessage = false)
        }
    }

    fun refreshEnvironmentChecks() {
        environmentIcons.value = environmentChecker.evaluateCompact(
            enabledAccounts = getEnabledAccountObjects(),
            fitnessOptions = accountRepository.fitnessOptions,
        )
    }

    fun setIntensity(level: IntensityLevel) {
        if (uiState.value.isConfigLocked) return
        selectedIntensity.value = level
        persistRunConfig()
    }

    fun snapDurationFromSlider(value: Float) {
        if (uiState.value.isConfigLocked) return
        val snapped = ((value / DURATION_STEP_MINUTES).roundToInt() * DURATION_STEP_MINUTES)
            .coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
        durationMinutes.value = snapped
        persistRunConfig()
    }

    fun snapBatchFromSlider(value: Float) {
        if (uiState.value.isConfigLocked) return
        batchSize.value = value.roundToInt().coerceIn(MIN_BATCH_SIZE, MAX_BATCH_SIZE)
        persistRunConfig()
    }

    fun openAccountEditSheet() {
        if (uiState.value.isConfigLocked) return
        showAccountEditSheet.value = true
    }

    fun dismissAccountEditSheet() {
        showAccountEditSheet.value = false
    }

    fun setAccountEnabled(accountId: String, enabled: Boolean) {
        if (uiState.value.isConfigLocked) return
        if (!enabled && accountId in enabledAccountIds.value && enabledAccountIds.value.size <= 1) {
            statusMessage.value = uiStrings.get(com.pixson.apbfit.R.string.error_cannot_disable_last_account)
            return
        }
        val updated = enabledAccountIds.value.toMutableSet()
        if (enabled) {
            updated.add(accountId)
        } else {
            updated.remove(accountId)
        }
        persistEnabledAccounts(updated)
    }

    fun signOutAccount(accountId: String) {
        if (uiState.value.isSessionActive) {
            statusMessage.value = uiStrings.cannotSignOutDuringRun
            return
        }
        viewModelScope.launch {
            val result = accountRepository.signOutAccount(accountId)
            result.onSuccess {
                val updated = enabledAccountIds.value.toMutableSet()
                updated.remove(accountId)
                persistEnabledAccounts(updated)
                statusMessage.value = uiStrings.signedOut
                refreshEnvironmentChecks()
            }.onFailure {
                statusMessage.value = it.message ?: uiStrings.accountNotAvailable
            }
        }
    }

    fun launchAddAccount(launchIntent: (Intent) -> Unit) {
        viewModelScope.launch {
            isBusy.value = true
            runCatching {
                accountRepository.getAddAccountIntent()
            }.onSuccess { intent ->
                launchIntent(intent)
            }.onFailure {
                statusMessage.value = it.message ?: uiStrings.signInFailed
            }
            isBusy.value = false
        }
    }

    fun onAddAccountResult(data: Intent?) {
        viewModelScope.launch {
            val result = accountRepository.handleSignInResult(data)
            result.onSuccess { account ->
                account.id?.let { id ->
                    val updated = enabledAccountIds.value.toMutableSet()
                    updated.add(id)
                    persistEnabledAccounts(updated)
                }
                statusMessage.value = uiStrings.addedAccount(account.email.orEmpty())
            }.onFailure {
                statusMessage.value = it.message ?: uiStrings.signInFailed
            }
            refreshEnvironmentChecks()
        }
    }

    fun startRun() {
        viewModelScope.launch {
            isBusy.value = true
            runCatching {
                val accounts = getEnabledAccountObjects()
                if (accounts.isEmpty()) {
                    throw IllegalStateException(
                        uiStrings.get(com.pixson.apbfit.R.string.error_no_enabled_accounts),
                    )
                }
                Log.d(TAG, "Start session requested for ${accounts.size} account(s)")
                sessionPreflight.ensureAll(accounts).getOrThrow()
                val accountIds = accounts.mapNotNull { it.id }
                val result = runRepository.startSession(
                    RunSessionConfig(
                        durationMinutes = durationMinutes.value,
                        intensityLevel = selectedIntensity.value,
                        batchSize = batchSize.value,
                    ),
                    accountIds,
                )
                Log.d(TAG, "Session rows created sessionId=${result.sessionId}, starting foreground service")
                runServiceStarter.startSession(result.sessionId)
            }.onSuccess {
                statusMessage.value = null
                Log.d(TAG, "Foreground service start requested successfully")
            }.onFailure { error ->
                handleStartSessionFailure(error, null)
            }
            isBusy.value = false
        }
    }

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

    fun onFitIconTap(
        launchSignIn: (Intent) -> Unit,
        launchExternal: (Intent) -> Unit,
    ) {
        if (!environmentChecker.isGoogleFitInstalled()) {
            launchExternal(environmentChecker.googleFitIntent())
        } else {
            launchSignIn(accountRepository.getFitnessPermissionsIntent())
        }
    }

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
            val accounts = getEnabledAccountObjects()
            if (accounts.isEmpty()) {
                statusMessage.value = uiStrings.get(com.pixson.apbfit.R.string.error_no_enabled_accounts)
                isBusy.value = false
                return@launch
            }
            val result = sessionPreflight.ensureAll(accounts)
            statusMessage.value = result.fold(
                onSuccess = { uiStrings.dataSourcesReady },
                onFailure = {
                    when (it) {
                        is PreflightException -> uiStrings.preflightFailed(it.accountEmail, it.message)
                        else -> it.message ?: uiStrings.dataSourceSetupFailed
                    }
                },
            )
            isBusy.value = false
        }
    }

    fun writeTestBatch() {
        viewModelScope.launch {
            isBusy.value = true
            val account = getEnabledAccountObjects().firstOrNull()
                ?: run {
                    statusMessage.value = uiStrings.get(com.pixson.apbfit.R.string.error_no_enabled_accounts)
                    isBusy.value = false
                    return@launch
                }
            val now = System.currentTimeMillis()
            val segment = segmentGenerator.generate(
                index = 0,
                startMillis = now - 30_000L,
                level = IntensityLevel.BRISK_WALK,
            )
            val result = fitWriter.writeSegments(account, listOf(segment))
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
                sessionPreflight.ensureAll(accounts).getOrThrow()
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
            }.onFailure { error ->
                handleStartSessionFailure(error, null)
            }
            isBusy.value = false
        }
    }

    private suspend fun handleStartSessionFailure(error: Throwable, createdSessionId: String?) {
        Log.e(TAG, "Start session failed: ${error.message}", error)
        when (error) {
            is RunAlreadyActiveException -> {
                val recovered = recoverStaleRunIfNeeded(showMessage = true)
                if (!recovered) {
                    statusMessage.value = uiStrings.runAlreadyActive
                }
            }
            is PreflightException -> {
                statusMessage.value = uiStrings.preflightFailed(error.accountEmail, error.message)
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

    fun testInjectedFailure() {
        viewModelScope.launch {
            isBusy.value = true
            val account = getEnabledAccountObjects().firstOrNull()
            if (account == null) {
                statusMessage.value = uiStrings.get(com.pixson.apbfit.R.string.error_no_enabled_accounts)
                isBusy.value = false
                return@launch
            }
            val segment = segmentGenerator.generate(
                index = 0,
                startMillis = System.currentTimeMillis() - 30_000L,
                level = IntensityLevel.JOG,
            )
            val result = FailingFitWriter().writeSegments(account, listOf(segment))
            statusMessage.value = result.fold(
                onSuccess = { uiStrings.unexpectedSuccess },
                onFailure = { uiStrings.injectedFailure(it.message) },
            )
            isBusy.value = false
        }
    }

    private fun persistRunConfig() {
        runConfigPrefs.save(
            RunConfigPrefs.SavedRunConfig(
                intensityLevel = selectedIntensity.value,
                durationMinutes = durationMinutes.value,
                batchSize = batchSize.value,
            ),
        )
    }

    private fun loadSavedRunConfig() {
        val saved = runConfigPrefs.load() ?: return
        selectedIntensity.value = saved.intensityLevel
        durationMinutes.value = saved.durationMinutes.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
        batchSize.value = saved.batchSize.coerceIn(MIN_BATCH_SIZE, MAX_BATCH_SIZE)
    }

    private fun migrateEnabledAccountsIfNeeded() {
        if (enabledAccountsPrefs.getEnabledAccountIds().isEmpty()) {
            val allIds = accountRepository.getKnownAccounts().mapNotNull { it.id }.toSet()
            if (allIds.isNotEmpty()) {
                enabledAccountsPrefs.setEnabledAccountIds(allIds)
            }
        }
    }

    private fun persistEnabledAccounts(ids: Set<String>) {
        enabledAccountIds.value = ids
        enabledAccountsPrefs.setEnabledAccountIds(ids)
        refreshEnvironmentChecks()
    }

    private fun getEnabledAccountObjects(): List<GoogleSignInAccount> {
        val enabledIds = enabledAccountIds.value
        return accountRepository.getKnownAccounts().filter { it.id in enabledIds }
    }

    private fun isEnvironmentReadyForRun(icons: CompactEnvironmentState): Boolean {
        return icons.fit == CheckStatus.PASS
    }

    private data class AccountSnapshotPartial(
        val enabledIds: Set<String>,
        val status: String?,
        val busy: Boolean,
        val showSheet: Boolean,
    )

    private data class ConfigSnapshot(
        val intensity: IntensityLevel,
        val duration: Int,
        val batch: Int,
        val sessionActive: Boolean,
        val envIcons: CompactEnvironmentState,
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
