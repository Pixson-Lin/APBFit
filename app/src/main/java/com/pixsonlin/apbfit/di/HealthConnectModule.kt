package com.pixsonlin.apbfit.di

import com.pixsonlin.apbfit.domain.fit.DefaultHealthConnectClientProvider
import com.pixsonlin.apbfit.domain.fit.HealthConnectClientProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthConnectModule {
    @Binds
    @Singleton
    abstract fun bindHealthConnectClientProvider(
        impl: DefaultHealthConnectClientProvider,
    ): HealthConnectClientProvider
}
