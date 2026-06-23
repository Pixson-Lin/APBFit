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
    fun startSession(sessionId: String, forceFailRunId: String? = null) {
        val intent = Intent(context, RunForegroundService::class.java).apply {
            action = RunForegroundService.ACTION_START_SESSION
            putExtra(RunForegroundService.EXTRA_SESSION_ID, sessionId)
            if (forceFailRunId != null) {
                putExtra(RunForegroundService.EXTRA_FORCE_FAIL_RUN_ID, forceFailRunId)
            }
        }
        context.startForegroundService(intent)
    }

    fun resumeOrphanSession(sessionId: String) {
        startSession(sessionId)
    }

    fun finalizeOrphanSession(sessionId: String, recoveryMessage: String) {
        val intent = Intent(context, RunForegroundService::class.java).apply {
            action = RunForegroundService.ACTION_FINALIZE_ORPHAN
            putExtra(RunForegroundService.EXTRA_SESSION_ID, sessionId)
            putExtra(RunForegroundService.EXTRA_RECOVERY_MESSAGE, recoveryMessage)
        }
        context.startForegroundService(intent)
    }

    fun stopSession() {
        val intent = Intent(context, RunForegroundService::class.java).apply {
            action = RunForegroundService.ACTION_STOP_SESSION
        }
        context.startService(intent)
    }
}
