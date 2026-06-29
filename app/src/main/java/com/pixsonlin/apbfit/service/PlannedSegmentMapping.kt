package com.pixsonlin.apbfit.service

import com.pixsonlin.apbfit.data.db.entity.SegmentRecordEntity
import com.pixsonlin.apbfit.domain.fit.SegmentData

internal fun SegmentRecordEntity.toSegmentData(): SegmentData = SegmentData(
    segmentIndex = segmentIndex,
    startTimeMillis = startTime,
    endTimeMillis = endTime,
    steps = steps,
    distanceMeters = distanceMeters,
)
