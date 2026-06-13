package com.pixson.apbfit.data.repository

import com.pixson.apbfit.data.db.dao.RunDao
import com.pixson.apbfit.data.db.dao.SegmentRecordDao
import com.pixson.apbfit.data.db.entity.RunEntity
import com.pixson.apbfit.data.db.entity.SegmentRecordEntity
import com.pixson.apbfit.data.model.RunAlreadyActiveException
import com.pixson.apbfit.data.model.RunConfig
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

    suspend fun getActiveRun(): RunEntity? = withContext(ioDispatcher) {
        runDao.getActiveRun()
    }

    suspend fun startRun(config: RunConfig): String = withContext(ioDispatcher) {
        val existing = runDao.getActiveRun()
        if (existing != null) throw RunAlreadyActiveException()
        val runId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        runDao.insert(
            RunEntity(
                id = runId,
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

    suspend fun abandonRun(runId: String, message: String) = withContext(ioDispatcher) {
        finalizeRun(
            runId = runId,
            status = RunStatus.FAILED,
            endTime = System.currentTimeMillis(),
            totalStepsWritten = 0,
            errorMessage = message,
        )
    }

    /** Finalize any RUNNING row left after a process/service crash (no foreground run in memory). */
    suspend fun recoverOrphanedRuns(recoveryMessage: String): Int = withContext(ioDispatcher) {
        val orphan = runDao.getActiveRun() ?: return@withContext 0
        val stepsWritten = segmentRecordDao.sumSuccessfulSteps(orphan.id)
        runDao.update(
            orphan.copy(
                status = RunStatus.STOPPED.name,
                endTime = System.currentTimeMillis(),
                totalStepsWritten = stepsWritten,
                errorMessage = recoveryMessage,
            ),
        )
        1
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
