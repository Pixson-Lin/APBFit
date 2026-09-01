package com.pixsonlin.apbfit.ui.viewmodel

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.pixsonlin.apbfit.data.model.IntensityLevel
import com.pixsonlin.apbfit.data.model.RunAlreadyActiveException
import com.pixsonlin.apbfit.data.model.RunSessionConfig
import com.pixsonlin.apbfit.data.prefs.EnabledAccountsPrefs
import com.pixsonlin.apbfit.data.prefs.RunConfigPrefs
import com.pixsonlin.apbfit.data.repository.AccountRepository
import com.pixsonlin.apbfit.data.repository.RunRepository
import com.pixsonlin.apbfit.domain.CheckStatus
import com.pixsonlin.apbfit.domain.CompactEnvironmentState
import com.pixsonlin.apbfit.domain.EnvironmentChecker
import com.pixsonlin.apbfit.BuildConfig
import com.pixsonlin.apbfit.domain.PreflightException
import com.pixsonlin.apbfit.domain.SessionPreflight
import com.pixsonlin.apbfit.domain.fit.FailingFitWriter
import com.pixsonlin.apbfit.domain.fit.FitWriter
import com.pixsonlin.apbfit.domain.fit.HealthConnectDebugReadback
import com.pixsonlin.apbfit.domain.fit.HealthConnectPermissionRepository
import com.pixsonlin.apbfit.domain.fit.HealthConnectPermissions
import com.pixsonlin.apbfit.domain.fit.SegmentGenerator
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
import kotlin.math.roundToInt

data class HomeUiState(
    val enabledAccounts: List<EnabledAccountSummary> = emptyList(),
    val accountEditItems: List<AccountEditItem> = emptyList(),
    val environmentIcons: CompactEnvironmentState = CompactEnvironmentState(
        battery = CheckStatus.WARN,
        fit = CheckStatus.WARN,
        notifications = CheckStatus.WARN,
        alarms = CheckStatus.WARN,
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
    private val healthConnectPermissionRepository: HealthConnectPermissionRepository,
    private val healthConnectDebugReadback: HealthConnectDebugReadback,
    private val uiStrings: UiStrings,
) : ViewModel() {
    private var pendingHealthConnectPermissionAction: (suspend () -> Unit)? = null
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
            alarms = CheckStatus.WARN,
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
                config.sessionActive -> uiStrings.get(com.pixsonlin.apbfit.R.string.start_run_blocked_session_active)
                enabledSummaries.isEmpty() -> uiStrings.get(com.pixsonlin.apbfit.R.string.error_no_enabled_accounts)
                !envReady -> uiStrings.get(com.pixsonlin.apbfit.R.string.start_run_blocked)
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
        viewModelScope.launch {
            runSessionStateHolder.state.collect { state ->
                if (!state.session.isActive) {
                    healthConnectDebugReadback.setDebugRunActive(false)
                }
            }
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
            statusMessage.value = uiStrings.get(com.pixsonlin.apbfit.R.string.error_cannot_disable_last_account)
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

    fun startRun(requestHealthConnectPermissions: (Set<String>) -> Unit = {}) {
        viewModelScope.launch {
            isBusy.value = true
            if (!launchHealthConnectPermissionsIfNeeded(requestHealthConnectPermissions) {
                    performStartRun()
                }
            ) {
                isBusy.value = false
                return@launch
            }
            isBusy.value = false
        }
    }

    private suspend fun performStartRun() {
        runCatching {
            val accounts = getEnabledAccountObjects()
            if (accounts.isEmpty()) {
                throw IllegalStateException(
                    uiStrings.get(com.pixsonlin.apbfit.R.string.error_no_enabled_accounts),
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
            runRepository.planSegmentsForSession(result.sessionId)
            Log.d(TAG, "Session rows created sessionId=${result.sessionId}, starting foreground service")
            runServiceStarter.startSession(result.sessionId)
        }.onSuccess {
            statusMessage.value = null
            Log.d(TAG, "Foreground service start requested successfully")
        }.onFailure { error ->
            handleStartSessionFailure(error, null)
        }
    }

    private suspend fun recoverStaleRunIfNeeded(showMessage: Boolean): Boolean {
        val sessionIds = runRepository.getOrphanSessionIds()
        if (sessionIds.isEmpty()) return false
        if (runSessionStateHolder.isActive) return false
        sessionIds.forEach { sessionId ->
            runServiceStarter.resumeOrphanSession(sessionId)
        }
        Log.w(TAG, "Resuming ${sessionIds.size} orphan session(s)")
        if (showMessage) {
            statusMessage.value = uiStrings.recoveredRun
        }
        return true
    }

    fun batteryOptimizationIntent(): Intent = environmentChecker.batteryOptimizationIntent()

    fun googleFitIntent(): Intent = environmentChecker.googleFitIntent()

    fun notificationSettingsIntent(): Intent = environmentChecker.notificationSettingsIntent()

    fun exactAlarmIntent(): Intent = environmentChecker.exactAlarmIntent()

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

    fun onHealthConnectPermissionResult(granted: Set<String>) {
        viewModelScope.launch {
            val required = HealthConnectPermissions.requestPermissions
            if (!granted.containsAll(required)) {
                statusMessage.value = uiStrings.healthConnectPermissionsIncomplete
                pendingHealthConnectPermissionAction = null
                isBusy.value = false
                return@launch
            }
            statusMessage.value = uiStrings.healthConnectPermissionsUpdated
            val action = pendingHealthConnectPermissionAction
            pendingHealthConnectPermissionAction = null
            if (action != null) {
                isBusy.value = true
                runCatching { action() }
                    .onFailure { error ->
                        healthConnectDebugReadback.setDebugRunActive(false)
                        handleStartSessionFailure(error, null)
                    }
                isBusy.value = false
            }
            refreshEnvironmentChecks()
        }
    }

    fun ensureDataSources(requestHealthConnectPermissions: (Set<String>) -> Unit = {}) {
        viewModelScope.launch {
            isBusy.value = true
            if (!launchHealthConnectPermissionsIfNeeded(requestHealthConnectPermissions) {
                    performEnsureDataSources()
                }
            ) {
                isBusy.value = false
                return@launch
            }
            isBusy.value = false
        }
    }

    private suspend fun performEnsureDataSources() {
        val accounts = getEnabledAccountObjects()
        if (accounts.isEmpty()) {
            statusMessage.value = uiStrings.get(com.pixsonlin.apbfit.R.string.error_no_enabled_accounts)
            return
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
    }

    fun writeTestBatch(requestHealthConnectPermissions: (Set<String>) -> Unit = {}) {
        viewModelScope.launch {
            isBusy.value = true
            if (!launchHealthConnectPermissionsIfNeeded(requestHealthConnectPermissions) {
                    performWriteTestBatch()
                }
            ) {
                isBusy.value = false
                return@launch
            }
            isBusy.value = false
        }
    }

    private var testBatchWriteCounter = 0

    private suspend fun performWriteTestBatch() {
        val account = getEnabledAccountObjects().firstOrNull()
            ?: run {
                statusMessage.value = uiStrings.get(com.pixsonlin.apbfit.R.string.error_no_enabled_accounts)
                return
            }
        testBatchWriteCounter++
        val maxSegmentDurationMs = (SegmentGenerator.MAX_DURATION_SEC_EXCLUSIVE - 1) * 1_000L
        // Segment duration is 25–35s; start must be far enough in the past so endTime <= now.
        // Shift each test write back by 2 minutes to avoid HC StepsRecord overlap (same dataOrigin).
        val pastOffsetMs = testBatchWriteCounter * 120_000L
        val startMillis = System.currentTimeMillis() - 5_000L - maxSegmentDurationMs - pastOffsetMs
        val segment = segmentGenerator.generate(
            index = 0,
            startMillis = startMillis,
            level = IntensityLevel.BRISK_WALK,
        )
        val result = fitWriter.writeSegments(account, listOf(segment))
        statusMessage.value = result.fold(
            onSuccess = { uiStrings.testBatchWritten(segment.steps) },
            onFailure = { it.message ?: uiStrings.writeFailed },
        )
    }

    fun startDebugRun(
        forceFailNextWrite: Boolean = false,
        requestHealthConnectPermissions: (Set<String>) -> Unit = {},
    ) {
        viewModelScope.launch {
            isBusy.value = true
            if (!launchHealthConnectPermissionsIfNeeded(requestHealthConnectPermissions) {
                    performStartDebugRun(forceFailNextWrite)
                }
            ) {
                isBusy.value = false
                return@launch
            }
            isBusy.value = false
        }
    }

    private suspend fun performStartDebugRun(forceFailNextWrite: Boolean) {
        runCatching {
            healthConnectDebugReadback.setDebugRunActive(true)
            val accounts = resolveDebugRunAccounts()
            sessionPreflight.ensureAll(accounts).getOrThrow()
            val accountIds = accounts.mapNotNull { it.id }
            val result = runRepository.startSession(
                RunSessionConfig(
                    durationMinutes = DEBUG_RUN_DURATION_MINUTES,
                    intensityLevel = IntensityLevel.BRISK_WALK,
                    batchSize = 1,
                ),
                accountIds,
            )
            val forceFailRunId = if (forceFailNextWrite) result.runs.first().runId else null
            runRepository.planSegmentsForSession(result.sessionId)
            runServiceStarter.startSession(result.sessionId, forceFailRunId)
            Log.d(TAG, "Debug run started sessionId=${result.sessionId} accounts=${accountIds.size}")
        }.onSuccess {
            statusMessage.value = if (BuildConfig.USE_HEALTH_CONNECT_WRITER) {
                uiStrings.debugHcRunStarted
            } else {
                uiStrings.debugRunStarted
            }
        }.onFailure { error ->
            healthConnectDebugReadback.setDebugRunActive(false)
            handleStartSessionFailure(error, null)
        }
    }

    /**
     * Google Fit debug path keeps the legacy two-account session.
     * Health Connect debug only needs one enabled account (device-scoped writes).
     */
    private suspend fun resolveDebugRunAccounts(): List<GoogleSignInAccount> {
        if (healthConnectPermissionRepository.isHealthConnectWriterActive()) {
            val enabled = getEnabledAccountObjects()
            if (enabled.isEmpty()) {
                throw IllegalStateException(
                    uiStrings.get(com.pixsonlin.apbfit.R.string.error_no_enabled_accounts),
                )
            }
            return listOf(enabled.first())
        }
        val accounts = accountRepository.getKnownAccounts().take(DEBUG_SESSION_ACCOUNT_COUNT)
        if (accounts.size < DEBUG_SESSION_ACCOUNT_COUNT) {
            throw IllegalStateException(uiStrings.debugRequiresTwoAccounts)
        }
        return accounts
    }

    private suspend fun launchHealthConnectPermissionsIfNeeded(
        requestHealthConnectPermissions: (Set<String>) -> Unit,
        deferredAction: suspend () -> Unit,
    ): Boolean {
        if (!healthConnectPermissionRepository.isHealthConnectWriterActive()) {
            deferredAction()
            return true
        }
        if (!healthConnectPermissionRepository.isSdkAvailable()) {
            statusMessage.value = uiStrings.healthConnectUnavailable
            return false
        }
        if (healthConnectPermissionRepository.hasAllPermissions()) {
            deferredAction()
            return true
        }
        pendingHealthConnectPermissionAction = deferredAction
        statusMessage.value = uiStrings.healthConnectPermissionsPrompt
        requestHealthConnectPermissions(HealthConnectPermissions.requestPermissions)
        return false
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
                statusMessage.value = uiStrings.get(com.pixsonlin.apbfit.R.string.error_no_enabled_accounts)
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
