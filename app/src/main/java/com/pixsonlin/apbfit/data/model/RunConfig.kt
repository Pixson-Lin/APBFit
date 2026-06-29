package com.pixsonlin.apbfit.data.model

data class RunConfig(
    val accountId: String,
    val durationMinutes: Int,
    val intensityLevel: IntensityLevel,
    val batchSize: Int,
)
