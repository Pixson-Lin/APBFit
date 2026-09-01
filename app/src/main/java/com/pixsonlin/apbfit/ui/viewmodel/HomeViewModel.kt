package com.pixsonlin.apbfit.ui.viewmodel

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.pixsonlin.apbfit.BuildConfig
import com.pixsonlin.apbfit.data.model.IntensityLevel
import com.pixsonlin.apbfit.data.model.RunAlreadyActiveException
import com.pixsonlin.apbfit.data.model.RunSessionConfig
import com.pixsonlin.apbfit.data.prefs.RunConfigPrefs
import com.pixsonlin.apbfit.data.repository.AccountRepository
import com.pixsonlin.apbfit.data.repository.RunRepository
import com.pixsonlin.apbfit.domain.CheckStatus
import com.pixsonlin.apbfit.domain.CompactEnvironmentState
import com.pixsonlin.apbfit.domain.EnvironmentChecker
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
    val signedInEmail: String? = null,
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
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val runRepository: RunRepository,
    private val runServiceStarter: RunServiceStarter,
    private val runSessionStateHolder: RunSessionStateHolder,
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
            statusMessage,
            isBusy,
        ) { _, status, busy ->
            AccountSnapshotPartial(status, busy)
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
        val signedInEmail = accountRepository.activeAccount.value?.email
        val envReady = isEnvironmentReadyForRun(config.envIcons)
        val canStart = signedInEmail != null &&
            !accountPartial.busy &&
            !config.sessionActive &&
            envReady
        HomeUiState(
            signedInEmail = signedInEmail,
            environmentIcons = config.envIcons,
            selectedIntensity = config.intensity,
            durationMinutes = config.duration,
            batchSize = config.batch,
            statusMessage = accountPartial.status,
            isBusy = accountPartial.busy,
            canStartRun = canStart,
            startBlockedReason = when {
                config.sessionActive -> uiStrings.get(com.pixsonlin.apbfit.R.string.start_run_blocked_session_active)
                signedInEmail == null -> uiStrings.get(com.pixsonlin.apbfit.R.string.error_no_signed_in_account)
                !envReady -> uiStrings.get(com.pixsonlin.apbfit.R.string.start_run_blocked)
                else -> null
            },
            isSessionActive = config.sessionActive,
            isConfigLocked = config.sessionActive,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
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
        viewModelScope.launch {
            environmentIcons.value = environmentChecker.evaluateCompact(
                hasSignedInAccount = accountRepository.hasActiveAccount(),
            )
        }
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

    fun signOut() {
        if (uiState.value.isSessionActive) {
            statusMessage.value = uiStrings.cannotSignOutDuringRun
            return
        }
        viewModelScope.launch {
            accountRepository.signOutCurrentAccount()
            statusMessage.value = uiStrings.signedOut
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
            val account = requireActiveAccount()
            Log.d(TAG, "Start session requested for account=${account.email}")
            sessionPreflight.ensureAll(listOf(account)).getOrThrow()
            val accountIds = listOfNotNull(account.id)
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

    fun notificationSettingsIntent(): Intent = environmentChecker.notificationSettingsIntent()

    fun exactAlarmIntent(): Intent = environmentChecker.exactAlarmIntent()

    fun onHealthConnectIconTap(
        requestHealthConnectPermissions: (Set<String>) -> Unit,
        launchExternal: (Intent) -> Unit,
    ) {
        viewModelScope.launch {
            if (!healthConnectPermissionRepository.isSdkAvailable()) {
                launchExternal(environmentChecker.healthConnectMarketIntent())
                return@launch
            }
            if (!healthConnectPermissionRepository.hasAllPermissions()) {
                pendingHealthConnectPermissionAction = null
                statusMessage.value = uiStrings.healthConnectPermissionsPrompt
                requestHealthConnectPermissions(HealthConnectPermissions.requestPermissions)
            } else {
                launchExternal(environmentChecker.healthConnectSettingsIntent())
            }
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
        val account = getActiveAccountOrNull()
        if (account == null) {
            statusMessage.value = uiStrings.get(com.pixsonlin.apbfit.R.string.error_no_signed_in_account)
            return
        }
        val result = sessionPreflight.ensureAll(listOf(account))
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
        val account = getActiveAccountOrNull()
            ?: run {
                statusMessage.value = uiStrings.get(com.pixsonlin.apbfit.R.string.error_no_signed_in_account)
                return
            }
        testBatchWriteCounter++
        val maxSegmentDurationMs = (SegmentGenerator.MAX_DURATION_SEC_EXCLUSIVE - 1) * 1_000L
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
            val account = requireActiveAccount()
            sessionPreflight.ensureAll(listOf(account)).getOrThrow()
            val accountIds = listOfNotNull(account.id)
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
            Log.d(TAG, "Debug run started sessionId=${result.sessionId}")
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
            val account = getActiveAccountOrNull()
            if (account == null) {
                statusMessage.value = uiStrings.get(com.pixsonlin.apbfit.R.string.error_no_signed_in_account)
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

    private fun getActiveAccountOrNull(): GoogleSignInAccount? = accountRepository.activeAccount.value

    private fun requireActiveAccount(): GoogleSignInAccount =
        getActiveAccountOrNull()
            ?: throw IllegalStateException(
                uiStrings.get(com.pixsonlin.apbfit.R.string.error_no_signed_in_account),
            )

    private fun isEnvironmentReadyForRun(icons: CompactEnvironmentState): Boolean =
        icons.fit == CheckStatus.PASS

    private data class AccountSnapshotPartial(
        val status: String?,
        val busy: Boolean,
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
    }
}
