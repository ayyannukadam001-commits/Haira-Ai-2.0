package com.haira.ai.data
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
private val Context.hairaDataStore by preferencesDataStore("haira_settings")
class HairaPreferences(private val context:Context){
 private val memory=booleanPreferencesKey("memory_enabled")
 private val styleKey=stringPreferencesKey("assistant_style")
 val memoryEnabled:Flow<Boolean> = context.hairaDataStore.data.map{it[memory]?:true}
 val style:Flow<String> = context.hairaDataStore.data.map{it[styleKey]?:"Friendly"}
 suspend fun setMemoryEnabled(value:Boolean){context.hairaDataStore.edit{it[memory]=value}}
 suspend fun setStyle(value:String){context.hairaDataStore.edit{it[styleKey]=value}}
}
