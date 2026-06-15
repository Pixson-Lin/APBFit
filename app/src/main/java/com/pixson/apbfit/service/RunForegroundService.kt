package com.pixson.apbfit.service

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class RunForegroundService : LifecycleService() {
    @Inject @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var runRepository: RunRepository
    @Inject lateinit var accountRepository: AccountRepository
    @Inject lateinit var fitWriter: FitWriter
    @Inject lateinit var runSessionStateHolder: RunSessionStateHolder
    @Inject lateinit var notificationHelper: RunNotificationHelper

    private var coordinator: SessionCoordinator? = null
    private val accountJobs = mutableListOf<Job>()
    private val finalizedRuns = ConcurrentHashMap.newKeySet<String>()

    override fun onCreate() {
        super.onCreate()
        notificationHelper.ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_SESSION -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY
                val forceFailRunId = intent.getStringExtra(EXTRA_FORCE_FAIL_RUN_ID)
                Log.d(TAG, "ACTION_START_SESSION sessionId=$sessionId")
                startSessionLoop(sessionId, forceFailRunId)
            }
            ACTION_STOP_SESSION, ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP_SESSION received")
                coordinator?.requestStop()
            }
        }
        return START_STICKY
    }

    private fun startSessionLoop(sessionId: String, forceFailRunId: String?) {
        accountJobs.forEach { it.cancel() }
        accountJobs.clear()
        finalizedRuns.clear()
        coordinator = null

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                executeSession(sessionId, forceFailRunId)
            } catch (e: CancellationException) {
                Log.d(TAG, "Session loop cancelled sessionId=$sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Session loop crashed sessionId=$sessionId", e)
                failSession(sessionId, appContext.getString(R.string.error_unexpected_service))
            }
        }
    }

    private suspend fun executeSession(sessionId: String, forceFailRunId: String?) {
        val runs = runRepository.getRunsBySessionId(sessionId)
        if (runs.isEmpty()) {
            Log.e(TAG, "No runs found for sessionId=$sessionId")
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

        val accountStates = runs.map { run ->
            val account = accountRepository.getAccountById(run.accountId)
            AccountRunUiState(
                runId = run.id,
                accountEmail = account?.email ?: run.accountId,
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
        Log.d(TAG, "Session promoted to foreground sessionId=$sessionId accounts=${accountStates.size}")

        sessionCoordinator.initJobCount(runs.size)
        val callbacks = object : AccountRunContext.Callbacks {
            override suspend fun onProgress(runId: String, totalSteps: Int, segmentsWritten: Int) {
                runSessionStateHolder.updateAccountProgress(runId, totalSteps, segmentsWritten)
                updateForegroundNotification()
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

        runs.forEach { run ->
            val account = accountRepository.getAccountById(run.accountId)
            if (account == null) {
                lifecycleScope.launch(Dispatchers.Default) {
                    finalizeAccountRun(
                        runId = run.id,
                        status = RunStatus.FAILED,
                        totalStepsWritten = 0,
                        errorMessage = appContext.getString(R.string.error_account_not_available),
                    )
                }
                return@forEach
            }
            val job = lifecycleScope.launch(Dispatchers.Default) {
                try {
                    AccountRunContext(
                        runId = run.id,
                        account = account,
                        coordinator = sessionCoordinator,
                        runRepository = runRepository,
                        fitWriter = fitWriter,
                        appContext = appContext,
                        callbacks = callbacks,
                        forceFailNextWrite = run.id == forceFailRunId,
                    ).executeRunLoop()
                } catch (e: CancellationException) {
                    Log.d(TAG, "Account run cancelled runId=${run.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "Account run crashed runId=${run.id}", e)
                    finalizeAccountRun(
                        runId = run.id,
                        status = RunStatus.FAILED,
                        totalStepsWritten = 0,
                        errorMessage = appContext.getString(R.string.error_unexpected_service),
                    )
                }
            }
            accountJobs.add(job)
        }
    }

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
        updateForegroundNotification()

        val sessionCoordinator = coordinator
        if (sessionCoordinator != null && sessionCoordinator.onJobCompleted()) {
            runSessionStateHolder.clear()
            shutdownService()
        }
    }

    private suspend fun shutdownService() {
        Log.d(TAG, "Session shutdown complete")
        coordinator = null
        withContext(Dispatchers.Main) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun promoteForeground() {
        val notification = notificationHelper.buildSessionNotification(
            runSessionStateHolder.state.value.withCurrentTiming(),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                RunNotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(RunNotificationHelper.NOTIFICATION_ID, notification)
        }
    }

    private fun updateForegroundNotification() {
        val notification = notificationHelper.buildSessionNotification(
            runSessionStateHolder.state.value.withCurrentTiming(),
        )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(RunNotificationHelper.NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        accountJobs.forEach { it.cancel() }
        accountJobs.clear()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "APBFit_Run"
        const val ACTION_START_SESSION = "com.pixson.apbfit.action.START_SESSION"
        const val ACTION_STOP_SESSION = "com.pixson.apbfit.action.STOP_SESSION"
        /** @deprecated Use [ACTION_STOP_SESSION]. */
        const val ACTION_STOP = ACTION_STOP_SESSION
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_FORCE_FAIL_RUN_ID = "extra_force_fail_run_id"
    }
}
