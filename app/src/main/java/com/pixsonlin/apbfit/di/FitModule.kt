package com.pixsonlin.apbfit.di

import com.pixsonlin.apbfit.BuildConfig
import com.pixsonlin.apbfit.domain.fit.FitWriter
import com.pixsonlin.apbfit.domain.fit.GoogleFitWriter
import com.pixsonlin.apbfit.domain.fit.HealthConnectWriter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FitModule {
  /**
   * Binds [HealthConnectWriter] when [BuildConfig.USE_HEALTH_CONNECT_WRITER] is true (default).
   * [GoogleFitWriter] remains as a deprecated fallback when the flag is false.
   */
    @Provides
    @Singleton
    fun provideFitWriter(
        googleFitWriter: GoogleFitWriter,
        healthConnectWriter: HealthConnectWriter,
    ): FitWriter = if (BuildConfig.USE_HEALTH_CONNECT_WRITER) {
        healthConnectWriter
    } else {
        @Suppress("DEPRECATION")
        googleFitWriter
    }
}
