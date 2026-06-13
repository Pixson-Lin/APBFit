package com.pixson.apbfit.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pixson.apbfit.data.db.entity.SegmentRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SegmentRecordDao {
    @Insert
    suspend fun insert(record: SegmentRecordEntity)

    @Insert
    suspend fun insertAll(records: List<SegmentRecordEntity>)

    @Query("SELECT * FROM segment_records WHERE runId = :runId ORDER BY segmentIndex")
    fun observeSegments(runId: String): Flow<List<SegmentRecordEntity>>

    @Query(
        "SELECT COALESCE(SUM(steps), 0) FROM segment_records " +
            "WHERE runId = :runId AND success = 1",
    )
    suspend fun sumSuccessfulSteps(runId: String): Int
}
