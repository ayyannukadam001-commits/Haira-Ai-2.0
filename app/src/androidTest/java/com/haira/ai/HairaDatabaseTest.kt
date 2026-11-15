package com.haira.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.haira.ai.data.ConversationEntity
import com.haira.ai.data.HairaDatabase
import com.haira.ai.data.MessageEntity
import com.haira.ai.data.ConnectedRepositoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HairaDatabaseTest {
    private lateinit var database: HairaDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, HairaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun conversationRenameAndDeleteKeepMessagesConsistent() = runBlocking {
        val dao = database.dao()
        val id = dao.addConversation(ConversationEntity(title = "First chat"))
        dao.addMessage(MessageEntity(conversationId = id, role = "user", content = "Hello"))
        dao.renameConversation(id, "Renamed chat")

        assertEquals("Renamed chat", dao.conversations().first().single().title)
        assertEquals(1, dao.messages(id).first().size)

        dao.deleteConversation(id)

        assertTrue(dao.conversations().first().isEmpty())
        assertTrue(dao.messages(id).first().isEmpty())
    }

    @Test
    fun messagesRemainSeparatedByConversation() = runBlocking {
        val dao = database.dao()
        val first = dao.addConversation(ConversationEntity(title = "One"))
        val second = dao.addConversation(ConversationEntity(title = "Two"))
        dao.addMessage(MessageEntity(conversationId = first, role = "user", content = "First"))
        dao.addMessage(MessageEntity(conversationId = second, role = "user", content = "Second"))

        assertEquals("First", dao.messages(first).first().single().content)
        assertEquals("Second", dao.messages(second).first().single().content)
    }

    @Test
    fun connectedPublicRepositoryCanBeStoredAndRemoved() = runBlocking {
        val dao = database.dao()
        val repository = ConnectedRepositoryEntity(
            fullName = "octocat/Hello-World",
            htmlUrl = "https://github.com/octocat/Hello-World",
            defaultBranch = "main",
            description = "Example repository",
            readmeExcerpt = "# Hello World"
        )

        dao.saveConnectedRepository(repository)
        assertEquals(repository.fullName, dao.connectedRepositories().first().single().fullName)
        assertEquals("# Hello World", dao.connectedRepositories().first().single().readmeExcerpt)

        dao.deleteConnectedRepository(repository.fullName)
        assertTrue(dao.connectedRepositories().first().isEmpty())
    }
}
