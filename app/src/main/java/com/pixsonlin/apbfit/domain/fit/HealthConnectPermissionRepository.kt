package com.pixsonlin.apbfit.domain.fit

import javax.inject.Inject
import javax.inject.Singleton
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_AVAILABLE

@Singleton
class HealthConnectPermissionRepository @Inject constructor(
    private val clientProvider: HealthConnectClientProvider,
) {
    fun isSdkAvailable(): Boolean = clientProvider.sdkStatus() == SDK_AVAILABLE

    suspend fun getMissingPermissions(): Set<String> {
        if (!isSdkAvailable()) {
            return HealthConnectPermissions.requestPermissions
        }
        val client = clientProvider.getClient()
        val granted = client.permissionController.getGrantedPermissions()
        return HealthConnectPermissions.requestPermissions - granted
    }

    suspend fun hasAllPermissions(): Boolean = getMissingPermissions().isEmpty()
}
