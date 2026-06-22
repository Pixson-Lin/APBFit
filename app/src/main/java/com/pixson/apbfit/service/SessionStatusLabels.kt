package com.pixson.apbfit.service

import com.pixson.apbfit.data.model.RunStatus

/** Pure helpers for session-level status copy (Traditional Chinese). */
object SessionStatusLabels {
    fun runningStatus(runningCount: Int, totalCount: Int): String =
        if (totalCount > 0) "$runningCount/$totalCount 進行中" else ""

    fun finishedStatus(accounts: List<AccountRunUiState>): String {
        val failedCount = accounts.count { it.status == RunStatus.FAILED }
        return if (failedCount > 0) "已完成（$failedCount 失敗）" else "已完成"
    }

    fun sessionStatusLabel(
        isActive: Boolean,
        accounts: List<AccountRunUiState>,
    ): String {
        if (accounts.isEmpty()) return ""
        return if (isActive) {
            runningStatus(
                accounts.count { it.status == RunStatus.RUNNING },
                accounts.size,
            )
        } else {
            finishedStatus(accounts)
        }
    }
}
