package com.pixson.apbfit.service

import com.pixson.apbfit.data.db.entity.SegmentRecordEntity
import com.pixson.apbfit.domain.fit.SegmentData

internal fun SegmentRecordEntity.toSegmentData(): SegmentData = SegmentData(
    segmentIndex = segmentIndex,
    startTimeMillis = startTime,
    endTimeMillis = endTime,
    steps = steps,
    distanceMeters = distanceMeters,
)
