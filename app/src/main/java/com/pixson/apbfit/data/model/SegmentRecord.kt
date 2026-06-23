package com.pixson.apbfit.data.model

import com.pixson.apbfit.data.db.entity.SegmentRecordEntity

data class SegmentRecord(
    val id: String,
    val runId: String,
    val segmentIndex: Int,
    val startTime: Long,
    val endTime: Long,
    val steps: Int,
    val distanceMeters: Float,
    val writeTime: Long,
    val writeStatus: SegmentWriteStatus,
    val success: Boolean,
    val errorMessage: String?,
)

fun SegmentRecordEntity.toModel(): SegmentRecord = SegmentRecord(
    id = id,
    runId = runId,
    segmentIndex = segmentIndex,
    startTime = startTime,
    endTime = endTime,
    steps = steps,
    distanceMeters = distanceMeters,
    writeTime = writeTime,
    writeStatus = runCatching { SegmentWriteStatus.valueOf(writeStatus) }
        .getOrDefault(if (success) SegmentWriteStatus.WRITTEN else SegmentWriteStatus.FAILED),
    success = success,
    errorMessage = errorMessage,
)

fun SegmentRecord.toEntity(): SegmentRecordEntity = SegmentRecordEntity(
    id = id,
    runId = runId,
    segmentIndex = segmentIndex,
    startTime = startTime,
    endTime = endTime,
    steps = steps,
    distanceMeters = distanceMeters,
    writeTime = writeTime,
    writeStatus = writeStatus.name,
    success = success,
    errorMessage = errorMessage,
)
