package com.pixson.apbfit.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class SessionScheduler(
    private val context: Context,
    private val sessionId: String,
    private val wakeLock: RunWakeLock,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleNext(triggerAtMillis: Long) {
        val intent = Intent(context, RunForegroundService::class.java).apply {
            action = RunForegroundService.ACTION_SCHEDULE_TICK
            putExtra(RunForegroundService.EXTRA_SESSION_ID, sessionId)
        }
        val pendingIntent = PendingIntent.getForegroundService(
            context,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        cancelPending(pendingIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            wakeLock.acquireSession()
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
            Log.d(TAG, "Scheduled inexact alarm at $triggerAtMillis sessionId=$sessionId")
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
            Log.d(TAG, "Scheduled exact alarm at $triggerAtMillis sessionId=$sessionId")
        }
    }

    fun cancel() {
        val intent = Intent(context, RunForegroundService::class.java).apply {
            action = RunForegroundService.ACTION_SCHEDULE_TICK
            putExtra(RunForegroundService.EXTRA_SESSION_ID, sessionId)
        }
        val pendingIntent = PendingIntent.getForegroundService(
            context,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        cancelPending(pendingIntent)
    }

    private fun cancelPending(pendingIntent: PendingIntent) {
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        private const val TAG = "APBFit_Scheduler"
    }
}
