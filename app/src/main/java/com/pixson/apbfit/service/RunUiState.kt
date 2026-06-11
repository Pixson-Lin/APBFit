package com.pixson.apbfit.service

import com.pixson.apbfit.data.model.RunStatus

data class RunUiState(
    val runId: String? = null,
    val status: RunStatus = RunStatus.RUNNING,
    val intensityName: String = "",
    val elapsedMillis: Long = 0L,
    val remainingMillis: Long = 0L,
    val totalSteps: Int = 0,
    val segmentsWritten: Int = 0,
    val errorMessage: String? = null,
) {
    val isActive: Boolean = runId != null && status == RunStatus.RUNNING
}
