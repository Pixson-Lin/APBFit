package com.pixson.apbfit.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.pixson.apbfit.MainActivity
import com.pixson.apbfit.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun buildSessionNotification(state: RunSessionUiState): Notification {
        val session = state.session
        val totalSteps = state.accounts.sumOf { it.totalSteps }
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, RunForegroundService::class.java).apply {
                action = RunForegroundService.ACTION_STOP_SESSION
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val statusLine = session.sessionStatusLabel.ifEmpty { session.intensityName }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_run_title))
            .setContentText(statusLine)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        appendLine(statusLine)
                        appendLine(session.intensityName)
                        appendLine(
                            context.getString(
                                R.string.notification_steps_written,
                                totalSteps,
                            ),
                        )
                        append(
                            context.getString(
                                R.string.notification_remaining,
                                formatRemaining(session.remainingMillis),
                            ),
                        )
                    },
                ),
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.notification_stop),
                stopIntent,
            )
            .build()
    }

    fun showError(message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_run_title))
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatRemaining(remainingMillis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    companion object {
        const val CHANNEL_ID = "apbfit_run_channel"
        const val NOTIFICATION_ID = 1001
    }
}
