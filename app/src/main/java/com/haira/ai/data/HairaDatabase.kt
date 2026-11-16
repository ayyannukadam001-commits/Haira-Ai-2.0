package com.haira.ai.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        NoteEntity::class,
        MemoryEntity::class,
        ModelEntity::class,
        ConnectedRepositoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class HairaDatabase : RoomDatabase() {
    abstract fun dao(): HairaDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE connected_repositories ADD COLUMN sourceExcerpt TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS connected_repositories (
                        fullName TEXT NOT NULL PRIMARY KEY,
                        htmlUrl TEXT NOT NULL,
                        defaultBranch TEXT NOT NULL,
                        description TEXT NOT NULL,
                        readmeExcerpt TEXT NOT NULL,
                        syncedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
