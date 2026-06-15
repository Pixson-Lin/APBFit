package com.pixson.apbfit.service

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.pixson.apbfit.R
import com.pixson.apbfit.data.db.entity.SegmentRecordEntity
import com.pixson.apbfit.data.model.IntensityLevel
import com.pixson.apbfit.data.model.RunStatus
import com.pixson.apbfit.data.repository.RunRepository
import com.pixson.apbfit.domain.fit.FitWriter
import com.pixson.apbfit.domain.fit.SegmentData
import com.pixson.apbfit.domain.fit.SegmentGenerator
import com.pixson.apbfit.domain.fit.seedForAccount
import java.util.UUID

class AccountRunContext(
    private val runId: String,
    private val account: GoogleSignInAccount,
    private val coordinator: SessionCoordinator,
    private val runRepository: RunRepository,
    private val fitWriter: FitWriter,
    private val appContext: Context,
    private val callbacks: Callbacks,
    private val forceFailNextWrite: Boolean,
) {
    interface Callbacks {
        suspend fun onProgress(runId: String, totalSteps: Int, segmentsWritten: Int)
        suspend fun onFinalize(
            runId: String,
            status: RunStatus,
            totalStepsWritten: Int,
            errorMessage: String?,
        )
    }

    suspend fun executeRunLoop() {
        Log.d(TAG, "executeRunLoop begin runId=$runId account=${account.email}")
        val run = runRepository.getRunById(runId)
            ?: return callbacks.onFinalize(
                runId,
                RunStatus.FAILED,
                0,
                appContext.getString(R.string.error_run_not_found),
            )
        if (run.durationMinutes <= 0) {
            return callbacks.onFinalize(
                runId,
                RunStatus.FAILED,
                0,
                appContext.getString(R.string.error_zero_duration),
            )
        }
        val intensity = IntensityLevel.valueOf(run.intensityLevel)
        val segmentGenerator = SegmentGenerator(seedForAccount(coordinator.sessionId, run.accountId))

        val ensureResult = fitWriter.ensureDataSources(account)
        if (ensureResult.isFailure) {
            return callbacks.onFinalize(
                runId,
                RunStatus.FAILED,
                0,
                ensureResult.exceptionOrNull()?.message
                    ?: appContext.getString(R.string.error_datasource_setup_failed),
            )
        }

        val queue = ArrayDeque<SegmentData>()
        var nextSegmentStart = run.startTime
        var segmentIndex = 0
        var totalStepsWritten = 0
        var segmentsWritten = 0
        var pendingForceFail = forceFailNextWrite

        while (!coordinator.isStopRequested() && !coordinator.isPastEnd()) {
            if (nextSegmentStart >= coordinator.sessionEndMillis) break

            val durationSec = segmentGenerator.nextDurationSeconds()
            if (delayUntilStopOrElapsed(durationSec * 1_000L)) break

            if (nextSegmentStart >= coordinator.sessionEndMillis) break

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
                    account = account,
                    batch = queue.toList(),
                    forceFail = pendingForceFail,
                )
                pendingForceFail = false
                if (batchResult.isFailure) {
                    return callbacks.onFinalize(
                        runId,
                        RunStatus.FAILED,
                        totalStepsWritten,
                        batchResult.exceptionOrNull()?.message
                            ?: appContext.getString(R.string.error_write_failed),
                    )
                }
                totalStepsWritten += batchResult.getOrThrow()
                segmentsWritten += queue.size
                queue.clear()
                callbacks.onProgress(runId, totalStepsWritten, segmentsWritten)
            }
        }

        if (queue.isNotEmpty()) {
            val batchResult = writeBatch(
                account = account,
                batch = queue.toList(),
                forceFail = pendingForceFail,
            )
            if (batchResult.isFailure) {
                return callbacks.onFinalize(
                    runId,
                    RunStatus.FAILED,
                    totalStepsWritten,
                    batchResult.exceptionOrNull()?.message
                        ?: appContext.getString(R.string.error_write_failed),
                )
            }
            totalStepsWritten += batchResult.getOrThrow()
            segmentsWritten += queue.size
            queue.clear()
            callbacks.onProgress(runId, totalStepsWritten, segmentsWritten)
        }

        val finalStatus = if (coordinator.isStopRequested()) RunStatus.STOPPED else RunStatus.COMPLETED
        Log.d(TAG, "executeRunLoop finished runId=$runId status=$finalStatus steps=$totalStepsWritten")
        callbacks.onFinalize(runId, finalStatus, totalStepsWritten, null)
    }

    private suspend fun writeBatch(
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

    private suspend fun delayUntilStopOrElapsed(totalMillis: Long): Boolean {
        var remaining = totalMillis
        while (remaining > 0 && !coordinator.isStopRequested() && !coordinator.isPastEnd()) {
            val chunk = minOf(remaining, STOP_POLL_INTERVAL_MS)
            kotlinx.coroutines.delay(chunk)
            remaining -= chunk
        }
        return coordinator.isStopRequested() || coordinator.isPastEnd()
    }

    companion object {
        private const val TAG = "APBFit_Run"
        private const val STOP_POLL_INTERVAL_MS = 500L
    }
}
