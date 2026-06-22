package com.pixson.apbfit.domain

enum class CheckStatus {
    PASS,
    WARN,
}

enum class EnvironmentCheckId {
    BATTERY_OPTIMIZATION,
    GOOGLE_FIT_INSTALLED,
    FITNESS_PERMISSIONS,
    NOTIFICATIONS,
}

data class EnvironmentCheck(
    val id: EnvironmentCheckId,
    val status: CheckStatus,
)

data class CompactEnvironmentState(
    val battery: CheckStatus,
    val fit: CheckStatus,
    val notifications: CheckStatus,
)
