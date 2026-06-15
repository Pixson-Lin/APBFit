package com.pixson.apbfit.data.model

data class RunSessionConfig(
    val durationMinutes: Int,
    val intensityLevel: IntensityLevel,
    val batchSize: Int,
)

data class RunSessionStartResult(
    val sessionId: String,
    val runs: List<RunStartEntry>,
)

data class RunStartEntry(
    val runId: String,
    val accountId: String,
)
