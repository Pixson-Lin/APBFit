package com.pixsonlin.apbfit.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pixsonlin.apbfit.data.db.entity.RunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Insert
    suspend fun insert(run: RunEntity)

    @Update
    suspend fun update(run: RunEntity)

    @Query("SELECT * FROM runs WHERE accountId = :accountId ORDER BY startTime DESC")
    fun observeRuns(accountId: String): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun getById(id: String): RunEntity?

    @Query("SELECT * FROM runs WHERE status = 'RUNNING' LIMIT 1")
    suspend fun getActiveRun(): RunEntity?

    @Query("SELECT * FROM runs WHERE status = 'RUNNING' LIMIT 1")
    fun observeActiveRun(): Flow<RunEntity?>

    @Query("SELECT * FROM runs WHERE status = 'RUNNING'")
    suspend fun getAllActiveRuns(): List<RunEntity>

    @Query("SELECT * FROM runs WHERE status = 'RUNNING'")
    fun observeActiveRuns(): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE sessionId = :sessionId")
    suspend fun getRunsBySessionId(sessionId: String): List<RunEntity>

    @Query("SELECT * FROM runs WHERE accountId = :accountId AND status = 'RUNNING' LIMIT 1")
    suspend fun getActiveRunForAccount(accountId: String): RunEntity?

    @Query("DELETE FROM runs WHERE startTime < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM runs WHERE accountId = :accountId")
    suspend fun clearForAccount(accountId: String)
}
