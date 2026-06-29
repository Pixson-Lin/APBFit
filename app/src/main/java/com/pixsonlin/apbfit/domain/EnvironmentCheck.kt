package com.pixsonlin.apbfit.domain

enum class CheckStatus {
    PASS,
    WARN,
}

enum class EnvironmentCheckId {
    BATTERY_OPTIMIZATION,
    GOOGLE_FIT_INSTALLED,
    FITNESS_PERMISSIONS,
    NOTIFICATIONS,
    EXACT_ALARMS,
}

data class EnvironmentCheck(
    val id: EnvironmentCheckId,
    val status: CheckStatus,
)

data class CompactEnvironmentState(
    val battery: CheckStatus,
    val fit: CheckStatus,
    val notifications: CheckStatus,
    val alarms: CheckStatus,
)
