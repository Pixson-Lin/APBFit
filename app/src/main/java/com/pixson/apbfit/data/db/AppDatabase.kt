package com.pixson.apbfit.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pixson.apbfit.data.db.dao.RunDao
import com.pixson.apbfit.data.db.dao.SegmentRecordDao
import com.pixson.apbfit.data.db.entity.RunEntity
import com.pixson.apbfit.data.db.entity.SegmentRecordEntity

@Database(
    entities = [RunEntity::class, SegmentRecordEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao
    abstract fun segmentRecordDao(): SegmentRecordDao
}
