package com.pixsonlin.apbfit.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pixsonlin.apbfit.data.db.dao.RunDao
import com.pixsonlin.apbfit.data.db.dao.SegmentRecordDao
import com.pixsonlin.apbfit.data.db.entity.RunEntity
import com.pixsonlin.apbfit.data.db.entity.SegmentRecordEntity

@Database(
    entities = [RunEntity::class, SegmentRecordEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao
    abstract fun segmentRecordDao(): SegmentRecordDao
}
