package com.haira.ai.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val path: String,
    val displayName: String,
    val sizeBytes: Long,
    val sha256: String,
    val importedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "connected_repositories")
data class ConnectedRepositoryEntity(
    @PrimaryKey val fullName: String,
    val htmlUrl: String,
    val defaultBranch: String,
    val description: String,
    val readmeExcerpt: String,
    val sourceExcerpt: String = "",
    val syncedAt: Long = System.currentTimeMillis()
)
