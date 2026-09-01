package com.pixsonlin.apbfit.domain

enum class CheckStatus {
    PASS,
    WARN,
}

data class CompactEnvironmentState(
    val battery: CheckStatus,
    val fit: CheckStatus,
    val notifications: CheckStatus,
    val alarms: CheckStatus,
)
