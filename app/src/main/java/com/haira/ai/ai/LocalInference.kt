package com.haira.ai.ai
import java.io.File
object LocalInference{
 private var loaded=false
 val backendAvailable:Boolean get()=try{System.loadLibrary("haira_llama");true}catch(_:Throwable){false}
 fun load(path:String,context:Int=2048):Boolean{if(!backendAvailable||!File(path).isFile)return false;return runCatching{nativeLoad(path,context).also{loaded=it}}.getOrDefault(false)}
 fun generate(prompt:String):String?=if(loaded)runCatching{nativeGenerate(prompt,256)}.getOrNull()else null
 fun unload(){if(loaded)runCatching{nativeUnload()};loaded=false}
 private external fun nativeLoad(path:String,context:Int):Boolean
 private external fun nativeGenerate(prompt:String,maxTokens:Int):String
 private external fun nativeUnload()
}
