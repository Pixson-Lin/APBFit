package com.pixsonlin.apbfit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE runs ADD COLUMN sessionId TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE runs SET sessionId = id")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE segment_records ADD COLUMN writeStatus TEXT NOT NULL DEFAULT 'WRITTEN'",
        )
        db.execSQL(
            "UPDATE segment_records SET writeStatus = 'FAILED' WHERE success = 0",
        )
    }
}
