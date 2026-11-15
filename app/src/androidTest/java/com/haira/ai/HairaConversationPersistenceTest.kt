package com.haira.ai

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.haira.ai.data.ConversationEntity
import com.haira.ai.data.HairaDatabase
import com.haira.ai.data.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HairaConversationPersistenceTest {
    @Test
    fun conversationHistorySurvivesReopenAndCanBeRenamedAndDeleted() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "haira-history-test-\${System.currentTimeMillis()}.db"
        var database = Room.databaseBuilder(context, HairaDatabase::class.java, dbName).build()
        try {
            val dao = database.dao()
            val id = dao.addConversation(ConversationEntity(title = "Original"))
            dao.addMessage(MessageEntity(conversationId = id, role = "user", content = "Remember this"))
            dao.renameConversation(id, "Renamed")
            assertEquals("Renamed", dao.conversations().first().single().title)
            database.close()

            database = Room.databaseBuilder(context, HairaDatabase::class.java, dbName).build()
            val reopenedDao = database.dao()
            assertEquals("Remember this", reopenedDao.messages(id).first().single().content)
            assertEquals("Renamed", reopenedDao.conversations().first().single().title)

            reopenedDao.deleteConversation(id)
            assertTrue(reopenedDao.conversations().first().isEmpty())
            assertTrue(reopenedDao.messages(id).first().isEmpty())
        } finally {
            database.close()
            context.deleteDatabase(dbName)
        }
    }
}
