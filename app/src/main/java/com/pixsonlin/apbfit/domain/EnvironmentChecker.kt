package com.pixsonlin.apbfit.domain

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.fitness.FitnessOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnvironmentChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val packageName: String = context.packageName

    fun evaluate(
        account: GoogleSignInAccount?,
        fitnessOptions: FitnessOptions,
    ): List<EnvironmentCheck> = listOf(
        EnvironmentCheck(
            id = EnvironmentCheckId.BATTERY_OPTIMIZATION,
            status = if (isBatteryOptimizationDisabled()) CheckStatus.PASS else CheckStatus.WARN,
        ),
        EnvironmentCheck(
            id = EnvironmentCheckId.GOOGLE_FIT_INSTALLED,
            status = if (isGoogleFitInstalledInternal()) CheckStatus.PASS else CheckStatus.WARN,
        ),
        EnvironmentCheck(
            id = EnvironmentCheckId.FITNESS_PERMISSIONS,
            status = if (account != null && GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                CheckStatus.PASS
            } else {
                CheckStatus.WARN
            },
        ),
        EnvironmentCheck(
            id = EnvironmentCheckId.NOTIFICATIONS,
            status = if (areNotificationsEnabled()) CheckStatus.PASS else CheckStatus.WARN,
        ),
        EnvironmentCheck(
            id = EnvironmentCheckId.EXACT_ALARMS,
            status = if (canScheduleExactAlarms()) CheckStatus.PASS else CheckStatus.WARN,
        ),
    )

    fun evaluateCompact(
        enabledAccounts: List<GoogleSignInAccount>,
        fitnessOptions: FitnessOptions,
    ): CompactEnvironmentState {
        val fitPass = isGoogleFitInstalledInternal() &&
            enabledAccounts.isNotEmpty() &&
            enabledAccounts.all { GoogleSignIn.hasPermissions(it, fitnessOptions) }
        return CompactEnvironmentState(
            battery = if (isBatteryOptimizationDisabled()) CheckStatus.PASS else CheckStatus.WARN,
            fit = if (fitPass) CheckStatus.PASS else CheckStatus.WARN,
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

    fun googleFitIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$GOOGLE_FIT_PACKAGE")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun isGoogleFitInstalled(): Boolean = isGoogleFitInstalledInternal()

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

    private fun isGoogleFitInstalledInternal(): Boolean {
        val packageManager = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    GOOGLE_FIT_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(GOOGLE_FIT_PACKAGE, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            packageManager.getLaunchIntentForPackage(GOOGLE_FIT_PACKAGE) != null
        }
    }

    private fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    companion object {
        const val GOOGLE_FIT_PACKAGE = "com.google.android.apps.fitness"
        const val FITNESS_PERMISSIONS_REQUEST_CODE = 1001
    }
}
