package com.pixsonlin.apbfit.service

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.pixsonlin.apbfit.R
import com.pixsonlin.apbfit.data.db.entity.RunEntity
import com.pixsonlin.apbfit.data.db.entity.SegmentRecordEntity
import com.pixsonlin.apbfit.data.model.RunStatus
import com.pixsonlin.apbfit.data.model.SegmentWriteStatus
import com.pixsonlin.apbfit.data.repository.RunRepository
import com.pixsonlin.apbfit.domain.CatchUpThrottle
import com.pixsonlin.apbfit.domain.fit.FitWriter
import kotlinx.coroutines.delay

class AccountRunWriter(
    private val runRepository: RunRepository,
    private val fitWriter: FitWriter,
    private val appContext: Context,
) {
    suspend fun ensureDataSources(account: GoogleSignInAccount): Result<Unit> =
        fitWriter.ensureDataSources(account)

    suspend fun writePlannedBatch(
        runId: String,
        account: GoogleSignInAccount,
        batch: List<SegmentRecordEntity>,
        forceFail: Boolean,
    ): Result<Int> {
        if (batch.isEmpty()) return Result.success(0)
        val segmentData = batch.map { it.toSegmentData() }
        val writeTime = System.currentTimeMillis()
        val result = if (forceFail) {
            Result.failure(
                IllegalStateException(appContext.getString(R.string.error_injected_write_failure)),
            )
        } else {
            fitWriter.writeSegments(account, segmentData)
        }
        val writeStatus = if (result.isSuccess) {
            SegmentWriteStatus.WRITTEN
        } else {
            SegmentWriteStatus.FAILED
        }
        val updated = batch.map { entity ->
            entity.copy(
                writeTime = if (writeStatus == SegmentWriteStatus.WRITTEN) writeTime else writeTime,
                writeStatus = writeStatus.name,
                success = result.isSuccess,
                errorMessage = result.exceptionOrNull()?.message,
            )
        }
        runRepository.updateSegments(updated)
        return result.map { batch.sumOf { it.steps } }
    }
}

class CatchUpEngine(
    private val runRepository: RunRepository,
    private val accountRunWriter: AccountRunWriter,
    private val wakeLock: RunWakeLock,
    private val coordinator: SessionCoordinator,
    private val callbacks: AccountRunContext.Callbacks,
) {
    private val failedRunIds = mutableSetOf<String>()

    fun isRunFailed(runId: String): Boolean = failedRunIds.contains(runId)

    suspend fun runCatchUpUntilIdle(
        runs: List<RunEntity>,
        accountsByRunId: Map<String, GoogleSignInAccount>,
        writeDeadlineMillis: Long,
        forceFailRunId: String?,
        forceFailConsumedRuns: MutableSet<String>,
    ): Boolean {
        val activeRuns = runs.filter {
            it.status == RunStatus.RUNNING.name && !failedRunIds.contains(it.id)
        }
        if (activeRuns.isEmpty()) return false

        val batchesPerAccount = CatchUpThrottle.batchesPerAccount(activeRuns.size)
        var anyFailure = false

        while (!coordinator.isStopRequested()) {
            val roundStart = System.currentTimeMillis()
            var wroteInRound = false
            var segmentsInRound = 0

            for (run in activeRuns) {
                if (coordinator.isStopRequested()) break
                val account = accountsByRunId[run.id] ?: continue
                var batchesLeft = batchesPerAccount

                while (
                    batchesLeft > 0 &&
                    segmentsInRound < CatchUpThrottle.MAX_SEGMENTS_PER_CATCH_UP &&
                    !coordinator.isStopRequested()
                ) {
                    val now = System.currentTimeMillis()
                    val effectiveNow = minOf(now, writeDeadlineMillis)
                    val batch = runRepository.getDuePlannedSegments(
                        runId = run.id,
                        now = effectiveNow,
                        limit = run.batchSize,
                    )
                    if (batch.isEmpty()) break

                    val forceFail = run.id == forceFailRunId && forceFailConsumedRuns.add(run.id)

                    wakeLock.acquireForWrite()
                    val result = try {
                        accountRunWriter.writePlannedBatch(
                            runId = run.id,
                            account = account,
                            batch = batch,
                            forceFail = forceFail,
                        )
                    } finally {
                        wakeLock.releaseWrite()
                    }

                    wroteInRound = true
                    segmentsInRound += batch.size
                    batchesLeft--

                    val totalSteps = runRepository.sumSuccessfulSteps(run.id)
                    val writtenCount = runRepository.countWrittenSegments(run.id)
                    callbacks.onProgress(run.id, totalSteps, writtenCount)

                    if (result.isFailure) {
                        failedRunIds += run.id
                        anyFailure = true
                        callbacks.onFinalize(
                            runId = run.id,
                            status = RunStatus.FAILED,
                            totalStepsWritten = totalSteps,
                            errorMessage = result.exceptionOrNull()?.message,
                        )
                        break
                    }

                    if (batchesLeft > 0) {
                        delay(CatchUpThrottle.DELAY_BETWEEN_BATCHES_MS)
                    }
                }
            }

            if (!wroteInRound) break
            if (System.currentTimeMillis() - roundStart < CatchUpThrottle.MAX_CATCH_UP_WALL_CLOCK_MS) {
                if (!hasDueSegments(activeRuns, writeDeadlineMillis)) break
            }
        }
        return anyFailure
    }

    suspend fun skipRemainingPlanned(runs: List<RunEntity>) {
        runs.forEach { run ->
            if (run.status == RunStatus.RUNNING.name) {
                runRepository.markAllPlannedSkipped(run.id)
            }
        }
    }

    private suspend fun hasDueSegments(
        runs: List<RunEntity>,
        writeDeadlineMillis: Long,
    ): Boolean {
        val now = minOf(System.currentTimeMillis(), writeDeadlineMillis)
        return runs.any { run ->
            run.status == RunStatus.RUNNING.name &&
                !failedRunIds.contains(run.id) &&
                runRepository.getDuePlannedSegments(run.id, now, 1).isNotEmpty()
        }
    }
}
