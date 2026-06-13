package com.pixson.apbfit.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.pixson.apbfit.R
import com.pixson.apbfit.data.db.entity.SegmentRecordEntity
import com.pixson.apbfit.data.model.IntensityLevel
import com.pixson.apbfit.data.model.RunStatus
import com.pixson.apbfit.data.repository.AccountRepository
import com.pixson.apbfit.data.repository.RunRepository
import com.pixson.apbfit.domain.fit.FitWriter
import com.pixson.apbfit.domain.fit.SegmentData
import com.pixson.apbfit.domain.fit.SegmentGenerator
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class RunForegroundService : LifecycleService() {
    @Inject @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var runRepository: RunRepository
    @Inject lateinit var accountRepository: AccountRepository
    @Inject lateinit var fitWriter: FitWriter
    @Inject lateinit var segmentGenerator: SegmentGenerator
    @Inject lateinit var runStateHolder: RunStateHolder
    @Inject lateinit var notificationHelper: RunNotificationHelper

    private var runJob: Job? = null
    private var manualStopRequested = false
    private var forceFailNextWrite = false

    override fun onCreate() {
        super.onCreate()
        notificationHelper.ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val runId = intent.getStringExtra(EXTRA_RUN_ID) ?: return START_NOT_STICKY
                forceFailNextWrite = intent.getBooleanExtra(EXTRA_FORCE_FAIL_NEXT_WRITE, false)
                manualStopRequested = false
                Log.d(TAG, "ACTION_START runId=$runId")
                startRunLoop(runId)
            }
            ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP received")
                manualStopRequested = true
            }
        }
        return START_STICKY
    }

    private fun startRunLoop(runId: String) {
        runJob?.cancel()
        runJob = lifecycleScope.launch(Dispatchers.Default) {
            try {
                executeRun(runId)
            } catch (e: CancellationException) {
                Log.d(TAG, "Run loop cancelled runId=$runId")
            } catch (e: Exception) {
                Log.e(TAG, "Run loop crashed runId=$runId", e)
                failRun(runId, appContext.getString(R.string.error_unexpected_service), 0)
            }
        }
    }

    private suspend fun executeRun(runId: String) {
        Log.d(TAG, "executeRun begin runId=$runId")
        val run = runRepository.getRunById(runId)
            ?: return failRun(runId, appContext.getString(R.string.error_run_not_found), 0)
        val account = accountRepository.getAccountById(run.accountId)
            ?: return failRun(runId, appContext.getString(R.string.error_account_not_available), 0)
        if (run.durationMinutes <= 0) {
            return failRun(runId, appContext.getString(R.string.error_zero_duration), 0)
        }
        val intensity = IntensityLevel.valueOf(run.intensityLevel)
        val runEndMillis = run.startTime + run.durationMinutes * 60_000L

        val ensureResult = fitWriter.ensureDataSources(account)
        if (ensureResult.isFailure) {
            return failRun(
                runId,
                ensureResult.exceptionOrNull()?.message
                    ?: appContext.getString(R.string.error_datasource_setup_failed),
                0,
            )
        }

        promoteForeground(intensity.displayName)
        runStateHolder.setRunning(
            runId = runId,
            intensityName = intensity.displayName,
            startTimeMillis = run.startTime,
            durationMinutes = run.durationMinutes,
            totalSteps = 0,
            segmentsWritten = 0,
        )
        Log.d(TAG, "Run promoted to foreground runId=$runId")

        val queue = ArrayDeque<SegmentData>()
        var nextSegmentStart = run.startTime
        var segmentIndex = 0
        var totalStepsWritten = 0
        var segmentsWritten = 0

        while (!manualStopRequested) {
            if (nextSegmentStart >= runEndMillis) break

            val durationSec = segmentGenerator.nextDurationSeconds()
            if (delayUntilStopOrElapsed(durationSec * 1_000L)) break

            if (nextSegmentStart >= runEndMillis) break

            val segment = segmentGenerator.generate(
                index = segmentIndex,
                startMillis = nextSegmentStart,
                level = intensity,
                durationSec = durationSec,
            )
            segmentIndex++
            nextSegmentStart = segment.endTimeMillis
            queue.addLast(segment)

            if (queue.size >= run.batchSize) {
                val batchResult = writeBatch(
                    runId = runId,
                    account = account,
                    batch = queue.toList(),
                    forceFail = forceFailNextWrite,
                )
                forceFailNextWrite = false
                if (batchResult.isFailure) {
                    return failRun(
                        runId,
                        batchResult.exceptionOrNull()?.message
                            ?: appContext.getString(R.string.error_write_failed),
                        totalStepsWritten,
                    )
                }
                totalStepsWritten += batchResult.getOrThrow()
                segmentsWritten += queue.size
                queue.clear()
                updateRunningState(
                    runId = runId,
                    intensityName = intensity.displayName,
                    startTimeMillis = run.startTime,
                    durationMinutes = run.durationMinutes,
                    totalSteps = totalStepsWritten,
                    segmentsWritten = segmentsWritten,
                )
            }
        }

        if (queue.isNotEmpty()) {
            val batchResult = writeBatch(
                runId = runId,
                account = account,
                batch = queue.toList(),
                forceFail = forceFailNextWrite,
            )
            if (batchResult.isFailure) {
                return failRun(
                    runId,
                    batchResult.exceptionOrNull()?.message
                        ?: appContext.getString(R.string.error_write_failed),
                    totalStepsWritten,
                )
            }
            totalStepsWritten += batchResult.getOrThrow()
            segmentsWritten += queue.size
            queue.clear()
        }

        val finalStatus = if (manualStopRequested) RunStatus.STOPPED else RunStatus.COMPLETED
        Log.d(TAG, "Run loop finished runId=$runId status=$finalStatus steps=$totalStepsWritten")
        finalizeRun(runId, finalStatus, totalStepsWritten, null)
    }

    private suspend fun writeBatch(
        runId: String,
        account: GoogleSignInAccount,
        batch: List<SegmentData>,
        forceFail: Boolean,
    ): Result<Int> {
        val writeTime = System.currentTimeMillis()
        val result = if (forceFail) {
            Result.failure(IllegalStateException(appContext.getString(R.string.error_injected_write_failure)))
        } else {
            fitWriter.writeSegments(account, batch)
        }

        val success = result.isSuccess
        val errorMessage = result.exceptionOrNull()?.message
        val records = batch.map { segment ->
            SegmentRecordEntity(
                id = UUID.randomUUID().toString(),
                runId = runId,
                segmentIndex = segment.segmentIndex,
                startTime = segment.startTimeMillis,
                endTime = segment.endTimeMillis,
                steps = segment.steps,
                distanceMeters = segment.distanceMeters,
                writeTime = writeTime,
                success = success,
                errorMessage = errorMessage,
            )
        }
        runRepository.insertSegments(records)

        return result.map { batch.sumOf { segment -> segment.steps } }
    }

    private suspend fun failRun(runId: String, message: String, totalStepsWritten: Int) {
        Log.e(TAG, "failRun runId=$runId message=$message")
        notificationHelper.showError(message)
        finalizeRun(runId, RunStatus.FAILED, totalStepsWritten, message)
    }

    private suspend fun finalizeRun(
        runId: String,
        status: RunStatus,
        totalStepsWritten: Int,
        errorMessage: String?,
    ) {
        runRepository.finalizeRun(
            runId = runId,
            status = status,
            endTime = System.currentTimeMillis(),
            totalStepsWritten = totalStepsWritten,
            errorMessage = errorMessage,
        )
        runStateHolder.setFinished(status, errorMessage)
        runStateHolder.clear()
        Log.d(TAG, "finalizeRun runId=$runId status=$status")
        withContext(Dispatchers.Main) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun promoteForeground(intensityName: String) {
        val notification = notificationHelper.buildNotification(
            RunUiState(intensityName = intensityName),
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

    /**
     * Sleeps in short chunks so [manualStopRequested] is observed within ~[STOP_POLL_INTERVAL_MS].
     * @return true if stop was requested before the full duration elapsed.
     */
    private suspend fun delayUntilStopOrElapsed(totalMillis: Long): Boolean {
        var remaining = totalMillis
        while (remaining > 0 && !manualStopRequested) {
            val chunk = minOf(remaining, STOP_POLL_INTERVAL_MS)
            delay(chunk)
            remaining -= chunk
        }
        return manualStopRequested
    }

    private fun updateRunningState(
        runId: String,
        intensityName: String,
        startTimeMillis: Long,
        durationMinutes: Int,
        totalSteps: Int,
        segmentsWritten: Int,
    ) {
        runStateHolder.setRunning(
            runId = runId,
            intensityName = intensityName,
            startTimeMillis = startTimeMillis,
            durationMinutes = durationMinutes,
            totalSteps = totalSteps,
            segmentsWritten = segmentsWritten,
        )
        val notification = notificationHelper.buildNotification(runStateHolder.state.value)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(RunNotificationHelper.NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        runJob?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "APBFit_Run"
        const val ACTION_START = "com.pixson.apbfit.action.START_RUN"
        const val ACTION_STOP = "com.pixson.apbfit.action.STOP_RUN"
        const val EXTRA_RUN_ID = "extra_run_id"
        const val EXTRA_FORCE_FAIL_NEXT_WRITE = "extra_force_fail_next_write"
        private const val STOP_POLL_INTERVAL_MS = 500L
    }
}
