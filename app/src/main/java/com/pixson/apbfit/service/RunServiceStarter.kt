package com.pixson.apbfit.service

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunServiceStarter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun startRun(runId: String, forceFailNextWrite: Boolean = false) {
        val intent = Intent(context, RunForegroundService::class.java).apply {
            action = RunForegroundService.ACTION_START
            putExtra(RunForegroundService.EXTRA_RUN_ID, runId)
            putExtra(RunForegroundService.EXTRA_FORCE_FAIL_NEXT_WRITE, forceFailNextWrite)
        }
        context.startForegroundService(intent)
    }

    fun stopRun() {
        val intent = Intent(context, RunForegroundService::class.java).apply {
            action = RunForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }
}
