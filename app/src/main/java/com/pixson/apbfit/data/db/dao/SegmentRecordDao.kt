package com.pixson.apbfit.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pixson.apbfit.data.db.entity.SegmentRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SegmentRecordDao {
    @Insert
    suspend fun insert(record: SegmentRecordEntity)

    @Insert
    suspend fun insertAll(records: List<SegmentRecordEntity>)

    @Update
    suspend fun updateAll(records: List<SegmentRecordEntity>)

    @Query("SELECT * FROM segment_records WHERE runId = :runId ORDER BY segmentIndex")
    fun observeSegments(runId: String): Flow<List<SegmentRecordEntity>>

    @Query(
        "SELECT * FROM segment_records WHERE runId = :runId AND writeStatus = 'PLANNED' " +
            "AND endTime <= :now ORDER BY segmentIndex ASC LIMIT :limit",
    )
    suspend fun getDuePlanned(runId: String, now: Long, limit: Int): List<SegmentRecordEntity>

    @Query(
        "SELECT * FROM segment_records WHERE runId = :runId AND writeStatus = 'PLANNED' " +
            "ORDER BY segmentIndex ASC LIMIT :limit",
    )
    suspend fun getNextPlannedBatch(runId: String, limit: Int): List<SegmentRecordEntity>

    @Query("SELECT COUNT(*) FROM segment_records WHERE runId = :runId")
    suspend fun countSegments(runId: String): Int

    @Query(
        "SELECT COUNT(*) FROM segment_records WHERE runId = :runId AND writeStatus = 'PLANNED'",
    )
    suspend fun countPlannedSegments(runId: String): Int

    @Query(
        "SELECT COUNT(*) FROM segment_records WHERE runId = :runId AND writeStatus = 'WRITTEN'",
    )
    suspend fun countWrittenSegments(runId: String): Int

    @Query(
        "UPDATE segment_records SET writeStatus = 'SKIPPED' " +
            "WHERE runId = :runId AND writeStatus = 'PLANNED'",
    )
    suspend fun markAllPlannedSkipped(runId: String)

    @Query(
        "SELECT COALESCE(SUM(steps), 0) FROM segment_records " +
            "WHERE runId = :runId AND writeStatus = 'WRITTEN'",
    )
    suspend fun sumSuccessfulSteps(runId: String): Int
}
