package com.pixsonlin.apbfit.domain.fit

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_AVAILABLE
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

interface HealthConnectClientProvider {
    fun sdkStatus(): Int

    suspend fun getClient(): HealthConnectClient
}

@Singleton
class DefaultHealthConnectClientProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthConnectClientProvider {

    override fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PACKAGE)

    override suspend fun getClient(): HealthConnectClient {
        require(sdkStatus() == SDK_AVAILABLE) {
            "Health Connect is not available on this device (status=${sdkStatus()})."
        }
        return HealthConnectClient.getOrCreate(context)
    }
}
