package com.pixson.apbfit.data.repository

import com.pixson.apbfit.data.db.dao.RunDao
import com.pixson.apbfit.data.db.dao.SegmentRecordDao
import com.pixson.apbfit.data.db.entity.RunEntity
import com.pixson.apbfit.data.db.entity.SegmentRecordEntity
import com.pixson.apbfit.data.model.IntensityLevel
import com.pixson.apbfit.data.model.RunAlreadyActiveException
import com.pixson.apbfit.data.model.RunConfig
import com.pixson.apbfit.data.model.RunSessionConfig
import com.pixson.apbfit.data.model.RunSessionStartResult
import com.pixson.apbfit.data.model.RunStartEntry
import com.pixson.apbfit.data.model.RunStatus
import com.pixson.apbfit.data.model.SegmentWriteStatus
import com.pixson.apbfit.data.model.ValidationResult
import com.pixson.apbfit.domain.SegmentPlanner
import com.pixson.apbfit.domain.fit.SegmentGenerator
import com.pixson.apbfit.domain.fit.seedForAccount
import com.pixson.apbfit.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunRepository @Inject constructor(
    private val runDao: RunDao,
    private val segmentRecordDao: SegmentRecordDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val segmentPlanner = SegmentPlanner()

    fun observeRuns(accountId: String): Flow<List<RunEntity>> = runDao.observeRuns(accountId)

    fun observeSegments(runId: String): Flow<List<SegmentRecordEntity>> =
        segmentRecordDao.observeSegments(runId)

    fun observeActiveRun(): Flow<RunEntity?> = runDao.observeActiveRun()

    fun observeActiveRuns(): Flow<List<RunEntity>> = runDao.observeActiveRuns()

    suspend fun getActiveRun(): RunEntity? = withContext(ioDispatcher) {
        runDao.getActiveRun()
    }

    suspend fun getAllActiveRuns(): List<RunEntity> = withContext(ioDispatcher) {
        runDao.getAllActiveRuns()
    }

    suspend fun hasRunningRows(): Boolean = withContext(ioDispatcher) {
        runDao.getAllActiveRuns().isNotEmpty()
    }

    suspend fun getOrphanSessionIds(): List<String> = withContext(ioDispatcher) {
        runDao.getAllActiveRuns()
            .map { it.sessionId }
            .distinct()
    }

    suspend fun startRun(config: RunConfig): String = withContext(ioDispatcher) {
        val existing = runDao.getAllActiveRuns()
        if (existing.isNotEmpty()) throw RunAlreadyActiveException()
        val runId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        runDao.insert(
            RunEntity(
                id = runId,
                sessionId = runId,
                accountId = config.accountId,
                startTime = now,
                endTime = null,
                durationMinutes = config.durationMinutes,
                intensityLevel = config.intensityLevel.name,
                batchSize = config.batchSize,
                status = RunStatus.RUNNING.name,
                totalStepsWritten = 0,
                validationResult = null,
                validationStepCount = null,
                validationTime = null,
                errorMessage = null,
            ),
        )
        runId
    }

    suspend fun startSession(
        config: RunSessionConfig,
        accountIds: List<String>,
    ): RunSessionStartResult = withContext(ioDispatcher) {
        require(accountIds.isNotEmpty()) { "At least one account is required to start a session." }
        if (runDao.getAllActiveRuns().isNotEmpty()) throw RunAlreadyActiveException()
        accountIds.forEach { accountId ->
            if (runDao.getActiveRunForAccount(accountId) != null) {
                throw RunAlreadyActiveException()
            }
        }

        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val entries = accountIds.map { accountId ->
            val runId = UUID.randomUUID().toString()
            runDao.insert(
                RunEntity(
                    id = runId,
                    sessionId = sessionId,
                    accountId = accountId,
                    startTime = now,
                    endTime = null,
                    durationMinutes = config.durationMinutes,
                    intensityLevel = config.intensityLevel.name,
                    batchSize = config.batchSize,
                    status = RunStatus.RUNNING.name,
                    totalStepsWritten = 0,
                    validationResult = null,
                    validationStepCount = null,
                    validationTime = null,
                    errorMessage = null,
                ),
            )
            RunStartEntry(runId = runId, accountId = accountId)
        }
        RunSessionStartResult(sessionId = sessionId, runs = entries)
    }

    suspend fun planSegmentsForSession(sessionId: String) = withContext(ioDispatcher) {
        val runs = runDao.getRunsBySessionId(sessionId)
        runs.forEach { run ->
            if (segmentRecordDao.countSegments(run.id) > 0) return@forEach
            val sessionEndMillis = run.startTime + run.durationMinutes * 60_000L
            val intensity = IntensityLevel.valueOf(run.intensityLevel)
            val generator = SegmentGenerator(seedForAccount(sessionId, run.accountId))
            val planned = segmentPlanner.planAllSegments(
                runStartMillis = run.startTime,
                sessionEndMillis = sessionEndMillis,
                intensity = intensity,
                generator = generator,
            )
            val entities = planned.map { segment ->
                SegmentRecordEntity(
                    id = UUID.randomUUID().toString(),
                    runId = run.id,
                    segmentIndex = segment.segmentIndex,
                    startTime = segment.startTimeMillis,
                    endTime = segment.endTimeMillis,
                    steps = segment.steps,
                    distanceMeters = segment.distanceMeters,
                    writeTime = 0L,
                    writeStatus = SegmentWriteStatus.PLANNED.name,
                    success = false,
                    errorMessage = null,
                )
            }
            if (entities.isNotEmpty()) {
                segmentRecordDao.insertAll(entities)
            }
        }
    }

    suspend fun abandonRun(runId: String, message: String) = withContext(ioDispatcher) {
        finalizeRun(
            runId = runId,
            status = RunStatus.FAILED,
            endTime = System.currentTimeMillis(),
            totalStepsWritten = 0,
            errorMessage = message,
        )
    }

    /**
     * Force-finalize orphan session rows (Settings manual recovery).
     * Does not write to Google Fit; service should run catch-up before calling this when possible.
     */
    suspend fun finalizeOrphanSessionInDb(
        sessionId: String,
        recoveryMessage: String,
    ): Int = withContext(ioDispatcher) {
        val runs = runDao.getRunsBySessionId(sessionId)
            .filter { it.status == RunStatus.RUNNING.name }
        if (runs.isEmpty()) return@withContext 0
        val now = System.currentTimeMillis()
        runs.forEach { run ->
            segmentRecordDao.markAllPlannedSkipped(run.id)
            val stepsWritten = segmentRecordDao.sumSuccessfulSteps(run.id)
            runDao.update(
                run.copy(
                    status = RunStatus.STOPPED.name,
                    endTime = now,
                    totalStepsWritten = stepsWritten,
                    errorMessage = recoveryMessage,
                ),
            )
        }
        runs.size
    }

    suspend fun insertRun(run: RunEntity) = withContext(ioDispatcher) {
        runDao.insert(run)
    }

    suspend fun updateRun(run: RunEntity) = withContext(ioDispatcher) {
        runDao.update(run)
    }

    suspend fun getRunById(id: String): RunEntity? = withContext(ioDispatcher) {
        runDao.getById(id)
    }

    suspend fun getRunsBySessionId(sessionId: String): List<RunEntity> = withContext(ioDispatcher) {
        runDao.getRunsBySessionId(sessionId)
    }

    suspend fun getDuePlannedSegments(
        runId: String,
        now: Long,
        limit: Int,
    ): List<SegmentRecordEntity> = withContext(ioDispatcher) {
        segmentRecordDao.getDuePlanned(runId, now, limit)
    }

    suspend fun getNextPlannedBatch(runId: String, batchSize: Int): List<SegmentRecordEntity> =
        withContext(ioDispatcher) {
            segmentRecordDao.getNextPlannedBatch(runId, batchSize)
        }

    suspend fun computeNextBatchDeadlineMillis(
        runId: String,
        batchSize: Int,
        now: Long,
    ): Long? = withContext(ioDispatcher) {
        val batch = segmentRecordDao.getNextPlannedBatch(runId, batchSize)
        if (batch.isEmpty()) return@withContext null
        val deadline = batch.maxOf { it.endTime }
        if (deadline <= now) now else deadline
    }

    suspend fun countPlannedSegments(runId: String): Int = withContext(ioDispatcher) {
        segmentRecordDao.countPlannedSegments(runId)
    }

    suspend fun countWrittenSegments(runId: String): Int = withContext(ioDispatcher) {
        segmentRecordDao.countWrittenSegments(runId)
    }

    suspend fun countAllSegments(runId: String): Int = withContext(ioDispatcher) {
        segmentRecordDao.countSegments(runId)
    }

    suspend fun sumSuccessfulSteps(runId: String): Int = withContext(ioDispatcher) {
        segmentRecordDao.sumSuccessfulSteps(runId)
    }

    suspend fun markAllPlannedSkipped(runId: String) = withContext(ioDispatcher) {
        segmentRecordDao.markAllPlannedSkipped(runId)
    }

    suspend fun updateSegments(records: List<SegmentRecordEntity>) = withContext(ioDispatcher) {
        segmentRecordDao.updateAll(records)
    }

    suspend fun deleteOlderThan(cutoffMillis: Long): Int = withContext(ioDispatcher) {
        runDao.deleteOlderThan(cutoffMillis)
    }

    suspend fun clearForAccount(accountId: String) = withContext(ioDispatcher) {
        runDao.clearForAccount(accountId)
    }

    suspend fun finalizeRun(
        runId: String,
        status: RunStatus,
        endTime: Long,
        totalStepsWritten: Int,
        errorMessage: String? = null,
    ) = withContext(ioDispatcher) {
        val run = runDao.getById(runId) ?: return@withContext
        runDao.update(
            run.copy(
                status = status.name,
                endTime = endTime,
                totalStepsWritten = totalStepsWritten,
                errorMessage = errorMessage,
            ),
        )
    }

    suspend fun logValidation(
        runId: String,
        result: ValidationResult,
        stepCount: Int?,
        validationTime: Long,
    ) = withContext(ioDispatcher) {
        val run = runDao.getById(runId) ?: return@withContext
        runDao.update(
            run.copy(
                validationResult = result.name,
                validationStepCount = stepCount,
                validationTime = validationTime,
            ),
        )
    }
}
