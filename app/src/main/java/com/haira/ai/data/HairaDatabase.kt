package com.haira.ai.data
import androidx.room.Database
import androidx.room.RoomDatabase
@Database(entities=[ConversationEntity::class,MessageEntity::class,NoteEntity::class,MemoryEntity::class,ModelEntity::class],version=1,exportSchema=false)
abstract class HairaDatabase:RoomDatabase(){ abstract fun dao():HairaDao }
