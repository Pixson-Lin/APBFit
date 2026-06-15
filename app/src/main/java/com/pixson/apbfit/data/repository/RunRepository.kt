package com.pixson.apbfit.data.repository

import com.pixson.apbfit.data.db.dao.RunDao
import com.pixson.apbfit.data.db.dao.SegmentRecordDao
import com.pixson.apbfit.data.db.entity.RunEntity
import com.pixson.apbfit.data.db.entity.SegmentRecordEntity
import com.pixson.apbfit.data.model.RunAlreadyActiveException
import com.pixson.apbfit.data.model.RunConfig
import com.pixson.apbfit.data.model.RunSessionConfig
import com.pixson.apbfit.data.model.RunSessionStartResult
import com.pixson.apbfit.data.model.RunStartEntry
import com.pixson.apbfit.data.model.RunStatus
import com.pixson.apbfit.data.model.ValidationResult
import java.util.UUID
import com.pixson.apbfit.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunRepository @Inject constructor(
    private val runDao: RunDao,
    private val segmentRecordDao: SegmentRecordDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
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

    suspend fun abandonRun(runId: String, message: String) = withContext(ioDispatcher) {
        finalizeRun(
            runId = runId,
            status = RunStatus.FAILED,
            endTime = System.currentTimeMillis(),
            totalStepsWritten = 0,
            errorMessage = message,
        )
    }

    /** Finalize all RUNNING rows left after a process/service crash. */
    suspend fun recoverOrphanedSessions(recoveryMessage: String): Int = withContext(ioDispatcher) {
        val orphans = runDao.getAllActiveRuns()
        if (orphans.isEmpty()) return@withContext 0
        val now = System.currentTimeMillis()
        orphans.forEach { run ->
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
        orphans.size
    }

    /** Delegates to [recoverOrphanedSessions] for call-site compatibility during migration. */
    suspend fun recoverOrphanedRuns(recoveryMessage: String): Int =
        recoverOrphanedSessions(recoveryMessage)

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

    suspend fun insertSegment(record: SegmentRecordEntity) = withContext(ioDispatcher) {
        segmentRecordDao.insert(record)
    }

    suspend fun insertSegments(records: List<SegmentRecordEntity>) = withContext(ioDispatcher) {
        segmentRecordDao.insertAll(records)
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
