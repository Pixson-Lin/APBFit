package com.pixsonlin.apbfit.di

import com.pixsonlin.apbfit.domain.fit.FitWriter
import com.pixsonlin.apbfit.domain.fit.GoogleFitWriter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FitModule {
    @Binds
    @Singleton
    abstract fun bindFitWriter(impl: GoogleFitWriter): FitWriter
}
