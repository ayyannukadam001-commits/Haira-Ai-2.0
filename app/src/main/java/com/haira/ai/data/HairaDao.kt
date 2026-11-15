package com.haira.ai.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HairaDao {
    @Query("SELECT * FROM conversations ORDER BY createdAt DESC")
    fun conversations(): Flow<List<ConversationEntity>>

    @Insert
    suspend fun addConversation(item: ConversationEntity): Long

    @Query("UPDATE conversations SET title=:title WHERE id=:id")
    suspend fun renameConversation(id: Long, title: String)

    @Query("DELETE FROM messages WHERE conversationId=:id")
    suspend fun deleteConversationMessages(id: Long)

    @Query("DELETE FROM conversations WHERE id=:id")
    suspend fun deleteConversationRow(id: Long)

    @Transaction
    suspend fun deleteConversation(id: Long) {
        deleteConversationMessages(id)
        deleteConversationRow(id)
    }

    @Query("SELECT * FROM messages WHERE conversationId=:id ORDER BY createdAt")
    fun messages(id: Long): Flow<List<MessageEntity>>

    @Insert
    suspend fun addMessage(item: MessageEntity)

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun notes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNote(item: NoteEntity)

    @Delete
    suspend fun deleteNote(item: NoteEntity)

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun memories(): Flow<List<MemoryEntity>>

    @Insert
    suspend fun addMemory(item: MemoryEntity)

    @Delete
    suspend fun deleteMemory(item: MemoryEntity)

    @Query("DELETE FROM memories")
    suspend fun clearMemories()

    @Query("SELECT * FROM models ORDER BY importedAt DESC")
    fun models(): Flow<List<ModelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveModel(item: ModelEntity)

    @Delete
    suspend fun deleteModel(item: ModelEntity)

    @Query("SELECT * FROM connected_repositories ORDER BY syncedAt DESC")
    fun connectedRepositories(): Flow<List<ConnectedRepositoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConnectedRepository(item: ConnectedRepositoryEntity)

    @Query("DELETE FROM connected_repositories WHERE fullName=:fullName")
    suspend fun deleteConnectedRepository(fullName: String)
}
