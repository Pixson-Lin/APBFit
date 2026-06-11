package com.pixson.apbfit.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val startTime: Long,
    val endTime: Long?,
    val durationMinutes: Int,
    val intensityLevel: String,
    val batchSize: Int,
    val status: String,
    val totalStepsWritten: Int,
    val validationResult: String?,
    val validationStepCount: Int?,
    val validationTime: Long?,
    val errorMessage: String?,
)
