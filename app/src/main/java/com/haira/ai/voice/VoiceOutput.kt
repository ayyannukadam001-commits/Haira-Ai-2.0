package com.haira.ai.voice
import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
class VoiceOutput(context:Context):TextToSpeech.OnInitListener{
 private var tts:TextToSpeech?=TextToSpeech(context.applicationContext,this)
 @Volatile private var ready=false
 override fun onInit(status:Int){ready=status==TextToSpeech.SUCCESS}
 fun speak(text:String){if(ready&&text.isNotBlank())tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"haira-tts")}
 fun stop(){tts?.stop()}
 fun language(locale:Locale){if(ready)tts?.language=locale}
 fun close(){tts?.stop();tts?.shutdown();tts=null}
}
