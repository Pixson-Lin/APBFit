package com.pixson.apbfit.ui.util

import androidx.annotation.StringRes
import com.pixson.apbfit.R
import com.pixson.apbfit.data.model.RunStatus

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
fun segmentStatusLabelRes(success: Boolean): Int = if (success) {
    R.string.segment_status_success
} else {
    R.string.segment_status_failed
}
