package com.pixsonlin.apbfit.service

import com.pixsonlin.apbfit.data.model.RunStatus

class AccountRunContext {
    interface Callbacks {
        suspend fun onProgress(runId: String, totalSteps: Int, segmentsWritten: Int)
        suspend fun onFinalize(
            runId: String,
            status: RunStatus,
            totalStepsWritten: Int,
            errorMessage: String?,
        )
    }
}
