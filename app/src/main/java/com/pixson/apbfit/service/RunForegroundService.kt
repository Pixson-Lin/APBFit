package com.pixson.apbfit.service

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.pixson.apbfit.R
import com.pixson.apbfit.data.model.IntensityLevel
import com.pixson.apbfit.data.model.RunStatus
import com.pixson.apbfit.data.repository.AccountRepository
import com.pixson.apbfit.data.repository.RunRepository
import com.pixson.apbfit.domain.fit.FitWriter
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.min

@AndroidEntryPoint
class RunForegroundService : LifecycleService() {
    @Inject @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var runRepository: RunRepository
    @Inject lateinit var accountRepository: AccountRepository
    @Inject lateinit var fitWriter: FitWriter
    @Inject lateinit var runSessionStateHolder: RunSessionStateHolder
    @Inject lateinit var notificationHelper: RunNotificationHelper

    private var coordinator: SessionCoordinator? = null
    private var activeSessionId: String? = null
    private val activeRunIds = mutableListOf<String>()
    private var sessionJob: Job? = null
    private val finalizedRuns = ConcurrentHashMap.newKeySet<String>()
    private var wakeLock: RunWakeLock? = null
    private var scheduler: SessionScheduler? = null
    private var screenOnReceiver: ScreenOnReceiver? = null
    private val sessionMutex = Mutex()
    private var forceFailRunId: String? = null
    private var forceFinalize = false
    private var recoveryMessage: String? = null
    private val forceFailConsumedRuns = ConcurrentHashMap.newKeySet<String>()

    override fun onCreate() {
        super.onCreate()
        notificationHelper.ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_SESSION -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY
                forceFailRunId = intent.getStringExtra(EXTRA_FORCE_FAIL_RUN_ID)
                forceFinalize = false
                recoveryMessage = null
                Log.d(TAG, "ACTION_START_SESSION sessionId=$sessionId")
                promoteForegroundImmediate()
                startSessionExecution(sessionId)
            }
            ACTION_FINALIZE_ORPHAN -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY
                forceFailRunId = null
                forceFinalize = true
                recoveryMessage = intent.getStringExtra(EXTRA_RECOVERY_MESSAGE)
                Log.d(TAG, "ACTION_FINALIZE_ORPHAN sessionId=$sessionId")
                promoteForegroundImmediate()
                startSessionExecution(sessionId)
            }
            ACTION_SCHEDULE_TICK -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_STICKY
                if (sessionId == activeSessionId) {
                    lifecycleScope.launch(Dispatchers.Default) {
                        runSessionTick("alarm")
                    }
                }
            }
            ACTION_STOP_SESSION, ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP_SESSION received")
                coordinator?.requestStop()
            }
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        Log.w(TAG, "onTimeout startId=$startId fgsType=$fgsType — finalizing session")
        recoveryMessage = appContext.getString(R.string.error_fgs_timeout)
        coordinator?.requestStop()
        lifecycleScope.launch(Dispatchers.Default) {
            val sessionId = activeSessionId ?: return@launch
            sessionMutex.withLock {
                finalizeActiveSession(
                    sessionId = sessionId,
                    manualStop = true,
                    applyRecoveryMessage = true,
                )
            }
        }
    }

    private fun promoteForegroundImmediate() {
        val notification = notificationHelper.buildPlaceholderNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                RunNotificationHelper.SUMMARY_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(RunNotificationHelper.SUMMARY_NOTIFICATION_ID, notification)
        }
    }

    private fun startSessionExecution(sessionId: String) {
        sessionJob?.cancel()
        finalizedRuns.clear()
        forceFailConsumedRuns.clear()
        coordinator = null
        activeSessionId = sessionId
        activeRunIds.clear()

        sessionJob = lifecycleScope.launch(Dispatchers.Default) {
            try {
                executeSession(sessionId)
            } catch (e: CancellationException) {
                Log.d(TAG, "Session cancelled sessionId=$sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Session crashed sessionId=$sessionId", e)
                failSession(sessionId, appContext.getString(R.string.error_unexpected_service))
            }
        }
    }

    private suspend fun executeSession(sessionId: String) {
        val runs = runRepository.getRunsBySessionId(sessionId)
        if (runs.isEmpty()) {
            Log.e(TAG, "No runs found for sessionId=$sessionId")
            shutdownService()
            return
        }
        val first = runs.first()
        val intensity = IntensityLevel.valueOf(first.intensityLevel)
        val sessionEndMillis = first.startTime + first.durationMinutes * 60_000L
        val sessionCoordinator = SessionCoordinator(
            sessionId = sessionId,
            startTimeMillis = first.startTime,
            sessionEndMillis = sessionEndMillis,
            intensity = intensity,
            batchSize = first.batchSize,
        )
        coordinator = sessionCoordinator
        activeRunIds.clear()
        activeRunIds.addAll(runs.map { it.id })

        wakeLock = RunWakeLock(appContext).also { lock ->
            if (!canScheduleExactAlarms()) {
                lock.acquireSession()
            }
        }
        scheduler = SessionScheduler(appContext, sessionId, wakeLock!!)

        val accountStates = runs.map { run ->
            val account = accountRepository.getAccountById(run.accountId)
            AccountRunUiState(
                runId = run.id,
                accountEmail = account?.email ?: run.accountId,
                segmentsPlanned = runRepository.countAllSegments(run.id),
            )
        }

        runSessionStateHolder.beginSession(
            sessionId = sessionId,
            intensityName = intensity.displayName,
            startTimeMillis = first.startTime,
            durationMinutes = first.durationMinutes,
            accounts = accountStates,
        )
        promoteForeground()
        Log.d(TAG, "Session started sessionId=$sessionId accounts=${accountStates.size}")

        screenOnReceiver = ScreenOnReceiver.register(appContext, lifecycleScope) {
            runSessionTick("screen_on")
        }

        sessionCoordinator.initJobCount(runs.size)

        val callbacks = buildCallbacks()
        val catchUpEngine = buildCatchUpEngine(sessionCoordinator, callbacks)
        val accountsByRunId = buildAccountsByRunId(runs)

        for ((runId, account) in accountsByRunId) {
            val ensureResult = AccountRunWriter(runRepository, fitWriter, appContext)
                .ensureDataSources(account)
            if (ensureResult.isFailure) {
                finalizeAccountRun(
                    runId = runId,
                    status = RunStatus.FAILED,
                    totalStepsWritten = 0,
                    errorMessage = ensureResult.exceptionOrNull()?.message
                        ?: appContext.getString(R.string.error_datasource_setup_failed),
                )
            }
        }

        if (forceFinalize || sessionCoordinator.isPastEnd()) {
            sessionMutex.withLock {
                finalizeActiveSession(
                    sessionId = sessionId,
                    manualStop = false,
                    applyRecoveryMessage = forceFinalize,
                )
            }
            return
        }

        while (!sessionCoordinator.isStopRequested() && !sessionCoordinator.isPastEnd()) {
            runCatchUpPass(sessionId, catchUpEngine, accountsByRunId)

            if (allAccountsFinalized()) break

            val nextDeadline = computeSessionNextDeadline(sessionId, sessionEndMillis)
                ?: sessionEndMillis
            val now = System.currentTimeMillis()
            if (nextDeadline <= now) continue

            scheduler?.scheduleNext(nextDeadline)
            awaitUntil(min(nextDeadline, sessionEndMillis), sessionCoordinator)
        }

        sessionMutex.withLock {
            finalizeActiveSession(
                sessionId = sessionId,
                manualStop = sessionCoordinator.isStopRequested(),
                applyRecoveryMessage = false,
            )
        }
    }

    private suspend fun runSessionTick(source: String) {
        val sessionId = activeSessionId ?: return
        Log.d(TAG, "Schedule tick from $source sessionId=$sessionId")
        sessionMutex.withLock {
            runCatchUpPass(sessionId, buildCatchUpEngine(coordinator!!, buildCallbacks()), buildAccountsByRunId(
                runRepository.getRunsBySessionId(sessionId),
            ))
            if (!coordinator!!.isStopRequested() && !coordinator!!.isPastEnd()) {
                val next = computeSessionNextDeadline(sessionId, coordinator!!.sessionEndMillis)
                if (next != null && next > System.currentTimeMillis()) {
                    scheduler?.scheduleNext(next)
                }
            }
        }
    }

    private suspend fun runCatchUpPass(
        sessionId: String,
        catchUpEngine: CatchUpEngine,
        accountsByRunId: Map<String, com.google.android.gms.auth.api.signin.GoogleSignInAccount>,
    ) {
        val runs = runRepository.getRunsBySessionId(sessionId)
        val deadline = min(System.currentTimeMillis(), coordinator?.sessionEndMillis ?: Long.MAX_VALUE)
        catchUpEngine.runCatchUpUntilIdle(
            runs = runs,
            accountsByRunId = accountsByRunId,
            writeDeadlineMillis = deadline,
            forceFailRunId = forceFailRunId,
            forceFailConsumedRuns = forceFailConsumedRuns,
        )
    }

    private suspend fun finalizeActiveSession(
        sessionId: String,
        manualStop: Boolean,
        applyRecoveryMessage: Boolean,
    ) {
        val coordinator = coordinator ?: return
        val runs = runRepository.getRunsBySessionId(sessionId)
        val accountsByRunId = buildAccountsByRunId(runs)
        val catchUpEngine = buildCatchUpEngine(coordinator, buildCallbacks())
        val writeDeadline = if (manualStop) {
            System.currentTimeMillis()
        } else {
            coordinator.sessionEndMillis
        }
        catchUpEngine.runCatchUpUntilIdle(
            runs = runs,
            accountsByRunId = accountsByRunId,
            writeDeadlineMillis = writeDeadline,
            forceFailRunId = forceFailRunId,
            forceFailConsumedRuns = forceFailConsumedRuns,
        )
        catchUpEngine.skipRemainingPlanned(runs)

        runs.forEach { run ->
            if (finalizedRuns.contains(run.id)) return@forEach
            val fresh = runRepository.getRunById(run.id) ?: return@forEach
            if (fresh.status != RunStatus.RUNNING.name) return@forEach
            val steps = runRepository.sumSuccessfulSteps(run.id)
            val status = when {
                manualStop -> RunStatus.STOPPED
                else -> RunStatus.COMPLETED
            }
            finalizeAccountRun(
                runId = run.id,
                status = status,
                totalStepsWritten = steps,
                errorMessage = if (applyRecoveryMessage) recoveryMessage else null,
            )
        }
    }

    private suspend fun computeSessionNextDeadline(sessionId: String, sessionEndMillis: Long): Long? {
        val now = System.currentTimeMillis()
        val runs = runRepository.getRunsBySessionId(sessionId)
            .filter { it.status == RunStatus.RUNNING.name && !finalizedRuns.contains(it.id) }
        if (runs.isEmpty()) return null
        val deadlines = runs.mapNotNull { run ->
            runRepository.computeNextBatchDeadlineMillis(run.id, run.batchSize, now)
        }
        if (deadlines.isEmpty()) return null
        return min(deadlines.min(), sessionEndMillis)
    }

    private suspend fun awaitUntil(deadlineMillis: Long, coordinator: SessionCoordinator) {
        while (!coordinator.isStopRequested() && System.currentTimeMillis() < deadlineMillis) {
            val remaining = deadlineMillis - System.currentTimeMillis()
            delay(min(remaining, 5_000L))
        }
    }

    private fun allAccountsFinalized(): Boolean =
        finalizedRuns.size >= activeRunIds.size

    private suspend fun buildAccountsByRunId(
        runs: List<com.pixson.apbfit.data.db.entity.RunEntity>,
    ): Map<String, com.google.android.gms.auth.api.signin.GoogleSignInAccount> {
        return runs.mapNotNull { run ->
            val account = accountRepository.getAccountById(run.accountId) ?: return@mapNotNull null
            run.id to account
        }.toMap()
    }

    private fun buildCallbacks(): AccountRunContext.Callbacks = object : AccountRunContext.Callbacks {
        override suspend fun onProgress(runId: String, totalSteps: Int, segmentsWritten: Int) {
            runSessionStateHolder.updateAccountProgress(runId, totalSteps, segmentsWritten)
            updateSessionNotifications()
        }

        override suspend fun onFinalize(
            runId: String,
            status: RunStatus,
            totalStepsWritten: Int,
            errorMessage: String?,
        ) {
            finalizeAccountRun(runId, status, totalStepsWritten, errorMessage)
        }
    }

    private fun buildCatchUpEngine(
        sessionCoordinator: SessionCoordinator,
        callbacks: AccountRunContext.Callbacks,
    ): CatchUpEngine = CatchUpEngine(
        runRepository = runRepository,
        accountRunWriter = AccountRunWriter(runRepository, fitWriter, appContext),
        wakeLock = wakeLock ?: RunWakeLock(appContext),
        coordinator = sessionCoordinator,
        callbacks = callbacks,
    )

    private suspend fun failSession(sessionId: String, message: String) {
        val runs = runRepository.getRunsBySessionId(sessionId)
        runs.forEach { run ->
            if (run.status == RunStatus.RUNNING.name) {
                finalizeAccountRun(run.id, RunStatus.FAILED, 0, message)
            }
        }
    }

    private suspend fun finalizeAccountRun(
        runId: String,
        status: RunStatus,
        totalStepsWritten: Int,
        errorMessage: String?,
    ) {
        if (!finalizedRuns.add(runId)) return
        Log.d(TAG, "finalizeAccountRun runId=$runId status=$status steps=$totalStepsWritten")
        runRepository.finalizeRun(
            runId = runId,
            status = status,
            endTime = System.currentTimeMillis(),
            totalStepsWritten = totalStepsWritten,
            errorMessage = errorMessage,
        )
        runSessionStateHolder.markAccountFinished(runId, status, errorMessage)
        updateSessionNotifications()

        val sessionCoordinator = coordinator
        if (sessionCoordinator != null && sessionCoordinator.onJobCompleted()) {
            runSessionStateHolder.clear()
            shutdownService()
        }
    }

    private suspend fun shutdownService() {
        Log.d(TAG, "Session shutdown complete")
        scheduler?.cancel()
        scheduler = null
        ScreenOnReceiver.unregister(appContext, screenOnReceiver)
        screenOnReceiver = null
        wakeLock?.releaseAll()
        wakeLock = null
        val sessionId = activeSessionId
        val runIds = activeRunIds.toList()
        coordinator = null
        activeSessionId = null
        activeRunIds.clear()
        forceFailConsumedRuns.clear()
        if (sessionId != null) {
            notificationHelper.cancelSessionNotifications(runIds)
        }
        withContext(Dispatchers.Main) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun promoteForeground() {
        val sessionId = activeSessionId ?: return
        val notification = notificationHelper.buildSummaryNotification(
            sessionId,
            runSessionStateHolder.state.value.withCurrentTiming(),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                RunNotificationHelper.SUMMARY_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(RunNotificationHelper.SUMMARY_NOTIFICATION_ID, notification)
        }
        updateSessionNotifications()
    }

    private fun updateSessionNotifications() {
        val sessionId = activeSessionId ?: return
        notificationHelper.updateSessionNotifications(
            sessionId,
            runSessionStateHolder.state.value.withCurrentTiming(),
        )
    }

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    override fun onDestroy() {
        sessionJob?.cancel()
        sessionJob = null
        scheduler?.cancel()
        ScreenOnReceiver.unregister(appContext, screenOnReceiver)
        wakeLock?.releaseAll()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "APBFit_Run"

        const val ACTION_START_SESSION = "com.pixson.apbfit.action.START_SESSION"
        const val ACTION_FINALIZE_ORPHAN = "com.pixson.apbfit.action.FINALIZE_ORPHAN"
        const val ACTION_SCHEDULE_TICK = "com.pixson.apbfit.action.SCHEDULE_TICK"
        const val ACTION_STOP_SESSION = "com.pixson.apbfit.action.STOP_SESSION"
        /** @deprecated Use [ACTION_STOP_SESSION]. */
        const val ACTION_STOP = ACTION_STOP_SESSION
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_FORCE_FAIL_RUN_ID = "extra_force_fail_run_id"
        const val EXTRA_RECOVERY_MESSAGE = "extra_recovery_message"
    }
}
