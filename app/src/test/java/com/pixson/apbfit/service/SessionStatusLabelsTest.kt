package com.pixson.apbfit.service

import com.pixson.apbfit.data.model.RunStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStatusLabelsTest {
    @Test
    fun runningStatus_formatsFraction() {
        assertEquals("2/3 進行中", SessionStatusLabels.runningStatus(2, 3))
    }

    @Test
    fun runningStatus_emptyWhenNoAccounts() {
        assertEquals("", SessionStatusLabels.runningStatus(0, 0))
    }

    @Test
    fun finishedStatus_allCompleted() {
        val accounts = listOf(
            account(RunStatus.COMPLETED),
            account(RunStatus.STOPPED),
        )
        assertEquals("已完成", SessionStatusLabels.finishedStatus(accounts))
    }

    @Test
    fun finishedStatus_withFailures() {
        val accounts = listOf(
            account(RunStatus.COMPLETED),
            account(RunStatus.FAILED),
            account(RunStatus.FAILED),
        )
        assertEquals("已完成（2 失敗）", SessionStatusLabels.finishedStatus(accounts))
    }

    @Test
    fun sessionStatusLabel_activeVsFinished() {
        val accounts = listOf(
            account(RunStatus.RUNNING),
            account(RunStatus.COMPLETED),
        )
        assertEquals("1/2 進行中", SessionStatusLabels.sessionStatusLabel(isActive = true, accounts))
        assertEquals("已完成", SessionStatusLabels.sessionStatusLabel(isActive = false, accounts))
    }

    private fun account(status: RunStatus) = AccountRunUiState(
        runId = "run-${status.name}",
        accountEmail = "test@example.com",
        status = status,
    )
}
