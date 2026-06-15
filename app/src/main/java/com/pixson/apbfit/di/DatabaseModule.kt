package com.pixson.apbfit.di

import android.content.Context
import androidx.room.Room
import com.pixson.apbfit.data.db.AppDatabase
import com.pixson.apbfit.data.db.MIGRATION_1_2
import com.pixson.apbfit.data.db.dao.RunDao
import com.pixson.apbfit.data.db.dao.SegmentRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DATABASE_NAME,
        ).addMigrations(MIGRATION_1_2).build()
    }

    @Provides
    fun provideRunDao(database: AppDatabase): RunDao = database.runDao()

    @Provides
    fun provideSegmentRecordDao(database: AppDatabase): SegmentRecordDao =
        database.segmentRecordDao()

    private const val DATABASE_NAME = "apbfit.db"
}
