package com.pixsonlin.apbfit.domain.fit

data class SegmentData(
    val segmentIndex: Int,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val steps: Int,
    val distanceMeters: Float,
)
