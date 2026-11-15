package com.haira.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.haira.ai.data.ConnectedRepositoryEntity
import com.haira.ai.data.HairaDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HairaDatabaseMigrationTest {
    @Test
    fun versionOneDatabaseMigratesWithoutLosingExistingChats() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "haira-migration-${System.currentTimeMillis()}.db"
        val legacy = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        try {
            legacy.execSQL("CREATE TABLE conversations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            legacy.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, conversationId INTEGER NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            legacy.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
            legacy.execSQL("CREATE TABLE memories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, content TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            legacy.execSQL("CREATE TABLE models (path TEXT NOT NULL, displayName TEXT NOT NULL, sizeBytes INTEGER NOT NULL, sha256 TEXT NOT NULL, importedAt INTEGER NOT NULL, PRIMARY KEY(path))")
            legacy.execSQL("INSERT INTO conversations (title, createdAt) VALUES ('Legacy chat', 123)")
            legacy.version = 1
        } finally {
            legacy.close()
        }

        val upgraded = Room.databaseBuilder(context, HairaDatabase::class.java, name)
            .addMigrations(HairaDatabase.MIGRATION_1_2)
            .build()
        try {
            val dao = upgraded.dao()
            assertEquals("Legacy chat", dao.conversations().first().single().title)

            dao.saveConnectedRepository(
                ConnectedRepositoryEntity(
                    fullName = "octocat/Hello-World",
                    htmlUrl = "https://github.com/octocat/Hello-World",
                    defaultBranch = "main",
                    description = "Example",
                    readmeExcerpt = "README"
                )
            )
            assertEquals("octocat/Hello-World", dao.connectedRepositories().first().single().fullName)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }
}
