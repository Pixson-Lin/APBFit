package com.pixsonlin.apbfit.ui.util

import androidx.annotation.StringRes
import com.pixsonlin.apbfit.R
import com.pixsonlin.apbfit.data.model.RunStatus

@StringRes
fun RunStatus.labelRes(): Int = when (this) {
    RunStatus.RUNNING -> R.string.run_status_running
    RunStatus.COMPLETED -> R.string.run_status_completed
    RunStatus.STOPPED -> R.string.run_status_stopped
    RunStatus.FAILED -> R.string.run_status_failed
}

@StringRes
fun runStatusLabelRes(statusName: String): Int = runCatching {
    RunStatus.valueOf(statusName).labelRes()
}.getOrDefault(R.string.run_status_unknown)

@StringRes
fun validationResultLabelRes(resultName: String): Int = when (resultName) {
    "ACCEPTED" -> R.string.validation_accepted
    "REJECTED" -> R.string.validation_rejected
    else -> R.string.run_status_unknown
}

@StringRes
fun segmentStatusLabelRes(writeStatus: String): Int = when (writeStatus) {
    "PLANNED" -> R.string.segment_status_pending
    "SKIPPED" -> R.string.segment_status_skipped
    "FAILED" -> R.string.segment_status_failed
    else -> R.string.segment_status_success
}
