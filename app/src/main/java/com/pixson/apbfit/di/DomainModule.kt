package com.pixson.apbfit.di

import com.pixson.apbfit.domain.fit.SegmentGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    @Singleton
    fun provideSegmentGenerator(): SegmentGenerator = SegmentGenerator()
}
