package com.pixson.apbfit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE runs ADD COLUMN sessionId TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE runs SET sessionId = id")
    }
}
