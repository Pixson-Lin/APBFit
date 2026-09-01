package com.pixsonlin.apbfit.domain.fit

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.testing.FakePermissionController

internal class FakeHealthConnectTestHarness private constructor(
    val client: FakeHealthConnectClient,
    val clientProvider: HealthConnectClientProvider,
) {
    companion object {
        fun createWithGrantedPermissions(): FakeHealthConnectTestHarness {
            val permissionController = FakePermissionController(grantAll = false).apply {
                grantPermissions(HealthConnectPermissions.requestPermissions)
            }
            val client = FakeHealthConnectClient(permissionController = permissionController)
            return FakeHealthConnectTestHarness(client, providerFor(client))
        }

        fun createWithoutPermissions(): FakeHealthConnectTestHarness {
            val permissionController = FakePermissionController(grantAll = false)
            val client = FakeHealthConnectClient(permissionController = permissionController)
            return FakeHealthConnectTestHarness(client, providerFor(client))
        }

        private fun providerFor(client: FakeHealthConnectClient): HealthConnectClientProvider =
            object : HealthConnectClientProvider {
                override fun sdkStatus(): Int = HealthConnectClient.SDK_AVAILABLE

                override suspend fun getClient(): HealthConnectClient = client
            }
    }
}
