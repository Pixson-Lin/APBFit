package com.pixsonlin.apbfit.domain

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.health.connect.client.HealthConnectClient
import com.pixsonlin.apbfit.domain.fit.HEALTH_CONNECT_PACKAGE
import com.pixsonlin.apbfit.domain.fit.HealthConnectPermissionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnvironmentChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val healthConnectPermissionRepository: HealthConnectPermissionRepository,
) {
    private val packageName: String = context.packageName

    suspend fun evaluateCompact(hasSignedInAccount: Boolean): CompactEnvironmentState {
        val healthConnectPass = hasSignedInAccount &&
            healthConnectPermissionRepository.isSdkAvailable() &&
            healthConnectPermissionRepository.hasAllPermissions()
        return CompactEnvironmentState(
            battery = if (isBatteryOptimizationDisabled()) CheckStatus.PASS else CheckStatus.WARN,
            fit = if (healthConnectPass) CheckStatus.PASS else CheckStatus.WARN,
            notifications = if (areNotificationsEnabled()) CheckStatus.PASS else CheckStatus.WARN,
            alarms = if (canScheduleExactAlarms()) CheckStatus.PASS else CheckStatus.WARN,
        )
    }

    fun batteryOptimizationIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }

    fun exactAlarmIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            appDetailsIntent()
        }

    fun healthConnectSettingsIntent(): Intent =
        Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun healthConnectMarketIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun notificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun appDetailsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    private fun isBatteryOptimizationDisabled(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
