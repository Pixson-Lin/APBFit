package com.pixson.apbfit.data.model

import com.pixson.apbfit.data.db.entity.RunEntity

data class Run(
    val id: String,
    val accountId: String,
    val startTime: Long,
    val endTime: Long?,
    val durationMinutes: Int,
    val intensityLevel: IntensityLevel,
    val batchSize: Int,
    val status: RunStatus,
    val totalStepsWritten: Int,
    val validationResult: ValidationResult?,
    val validationStepCount: Int?,
    val validationTime: Long?,
    val errorMessage: String?,
)

fun RunEntity.toModel(): Run = Run(
    id = id,
    accountId = accountId,
    startTime = startTime,
    endTime = endTime,
    durationMinutes = durationMinutes,
    intensityLevel = IntensityLevel.valueOf(intensityLevel),
    batchSize = batchSize,
    status = RunStatus.valueOf(status),
    totalStepsWritten = totalStepsWritten,
    validationResult = validationResult?.let { ValidationResult.valueOf(it) },
    validationStepCount = validationStepCount,
    validationTime = validationTime,
    errorMessage = errorMessage,
)

fun Run.toEntity(): RunEntity = RunEntity(
    id = id,
    accountId = accountId,
    startTime = startTime,
    endTime = endTime,
    durationMinutes = durationMinutes,
    intensityLevel = intensityLevel.name,
    batchSize = batchSize,
    status = status.name,
    totalStepsWritten = totalStepsWritten,
    validationResult = validationResult?.name,
    validationStepCount = validationStepCount,
    validationTime = validationTime,
    errorMessage = errorMessage,
)
