package com.haira.ai

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import com.haira.ai.ai.LocalInference
import com.haira.ai.ai.ModelCatalog
import com.haira.ai.ai.ModelDownloader
import com.haira.ai.data.*
import com.haira.ai.voice.VoiceOutput
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val Night=Color(0xFF050914)
private val Panel=Color(0xFF101B2B)
private val Cyan=Color(0xFF45D9F5)
private val Muted=Color(0xFF9BB3C8)
private val Warning=Color(0xFFFFB66D)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{HairaApp()}}
}

@Composable
fun HairaApp(){
 val context= LocalContext.current
 val scope= rememberCoroutineScope()
 val db= remember(context){Room.databaseBuilder(context,HairaDatabase::class.java,"haira-local.db").build()}
 val dao= remember(db){db.dao()}
 val prefs= remember(context){HairaPreferences(context)}
 val models by dao.models().collectAsState(initial=emptyList())
 val notes by dao.notes().collectAsState(initial=emptyList())
 val memories by dao.memories().collectAsState(initial=emptyList())
 val chats by dao.conversations().collectAsState(initial=emptyList())
 val memoryEnabled by prefs.memoryEnabled.collectAsState(initial=true)
 val style by prefs.style.collectAsState(initial="Friendly")
 var tab by remember{mutableStateOf("HAIRA")}
 var chatId by remember{mutableLongStateOf(0L)}
 var engineStatus by remember{mutableStateOf("Initializing")}
 var engineError by remember{mutableStateOf<String?>(null)}
 var isGenerating by remember{mutableStateOf(false)}
 val backendAvailable = remember { LocalInference.backendAvailable }
 var addMemory by remember{mutableStateOf(false)}
 var memoryDraft by remember{mutableStateOf("")}
 val voice= remember(context){VoiceOutput(context)}
 DisposableEffect(db, voice) {
  onDispose {
   LocalInference.unload()
   voice.close()
   db.close()
  }
 }

 LaunchedEffect(Unit){
  engineError=null
  try {
   engineStatus="Installing bundled SmolLM2"
   val bundled=withContext(Dispatchers.IO){BundledModelInstaller(context).install()}
   engineStatus="Verifying bundled model"
   dao.saveModel(bundled)
   if(!backendAvailable) error("llama.cpp JNI library is unavailable for this device.")
   engineStatus="Loading AI engine"
   val loaded=withContext(Dispatchers.Default){LocalInference.load(bundled.path,2048)}
   if(!loaded) error("SmolLM2 model file was installed and verified, but llama.cpp could not initialize it.")
   engineStatus="Model ready"
  } catch (e: Exception) {
   engineError=e.message ?: e.javaClass.simpleName
   engineStatus="Loading failed"
   android.util.Log.e("HAIRA-ENGINE","Automatic model initialization failed",e)
   Toast.makeText(context,"HAIRA model setup failed: "+engineError,Toast.LENGTH_LONG).show()
  }
 }


 val modelPicker= rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri:Uri?->if(uri!=null)scope.launch{
  runCatching{context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);ModelImporter(context).import(uri)}
   .onSuccess{dao.saveModel(it);Toast.makeText(context,"GGUF header verified; model imported. Load status is shown separately.",Toast.LENGTH_LONG).show()}
   .onFailure{Toast.makeText(context,it.message?: "Import failed",Toast.LENGTH_LONG).show()}
 }}
 val speechResult= rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
  val phrase=result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
  if(!phrase.isNullOrBlank())scope.launch{if(chatId==0L)chatId=dao.addConversation(ConversationEntity(title="Voice chat"));dao.addMessage(MessageEntity(conversationId=chatId,role="user",content=phrase))}
 }
 val micPermission= rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
  if(granted){val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault());putExtra(RecognizerIntent.EXTRA_PROMPT,"Speak to HAIRA")};runCatching{speechResult.launch(i)}.onFailure{Toast.makeText(context,"Speech service unavailable",Toast.LENGTH_SHORT).show()}}
  else Toast.makeText(context,"Microphone permission denied",Toast.LENGTH_SHORT).show()
 }

 MaterialTheme(colorScheme=darkColorScheme(background=Night,surface=Panel,primary=Cyan)){
  Scaffold(containerColor=Night,bottomBar={NavigationBar(containerColor=Panel){listOf("HAIRA","Studio","Actions","Builder","System").forEach{item->
   NavigationBarItem(selected=tab==item,onClick={tab=item},icon={Text(item.take(1),color=Cyan,fontWeight=FontWeight.Bold)},label={Text(item,fontSize=10.sp)})
  }}}){padding->
   when(tab){
    "HAIRA"->ChatScreen(Modifier.padding(padding),chats,chatId,dao.messages(chatId).collectAsState(initial=emptyList()).value,
     engineStatus=engineStatus,engineError=engineError,isGenerating=isGenerating,
     onPick={chatId=it},onNew={scope.launch{chatId=dao.addConversation(ConversationEntity(title="Chat "+(chats.size+1)))}},
     onSend={text->
      if(!isGenerating){
       scope.launch{
        isGenerating=true
        try {
         if(engineStatus!="Model ready") error(engineError ?: "The local AI model is not ready yet.")
         val activeId=if(chatId==0L)dao.addConversation(ConversationEntity(title=text.take(32))) else chatId
         chatId=activeId
         dao.addMessage(MessageEntity(conversationId=activeId,role="user",content=text))
         val memoryContext=if(memoryEnabled)memories.joinToString("\n"){it.content}.take(3000)else ""
         val prompt="Assistant style: "+style+"\nUser-approved memory:\n"+memoryContext+"\nUser: "+text+"\nAssistant:"
         val answer=withContext(Dispatchers.Default){LocalInference.generate(prompt)}
         if(!answer.isNullOrBlank())dao.addMessage(MessageEntity(conversationId=activeId,role="assistant",content=answer.trim()))
         else error("The model returned an empty response.")
        } catch(e:Exception) {
         android.util.Log.e("HAIRA-CHAT","Message generation failed",e)
         Toast.makeText(context,e.message ?: "AI response failed.",Toast.LENGTH_LONG).show()
        } finally { isGenerating=false }
       }
      }
     },onMic={micPermission.launch(Manifest.permission.RECORD_AUDIO)},onSpeak={voice.speak(it)})
    "Studio"->StudioScreen(Modifier.padding(padding),notes,{t,b->scope.launch{dao.saveNote(NoteEntity(title=t,body=b))}},{n->scope.launch{dao.deleteNote(n)}})
    "Actions"->ActionsScreen(Modifier.padding(padding),{context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))},{val i=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"HAIRA AI")};context.startActivity(Intent.createChooser(i,"Share"))})
    "Builder"->StatusScreen(Modifier.padding(padding),"BUILDER","Project generation is not implemented in this build yet.")
    else->SystemScreen(Modifier.padding(padding),models,memories,memoryEnabled,style,engineStatus,backendAvailable,
     onImport={modelPicker.launch(arrayOf("application/octet-stream","application/*"))},
     onDownload={item->runCatching{ModelDownloader(context).start(item)}.onSuccess{Toast.makeText(context,"Download started. Android Download Manager will show progress.",Toast.LENGTH_LONG).show()}.onFailure{Toast.makeText(context,"Download could not start: "+it.message,Toast.LENGTH_LONG).show()}},
     onLoad={model->scope.launch{val ok=withContext(Dispatchers.Default){LocalInference.load(model.path)};Toast.makeText(context,if(ok)"Model loaded" else "Native llama.cpp engine is not packaged or the model could not load.",Toast.LENGTH_LONG).show()}},
     onRemove={m->scope.launch{dao.deleteModel(m)}},
     onMemory={scope.launch{prefs.setMemoryEnabled(it)}},onStyle={scope.launch{prefs.setStyle(it)}},
     onAddMemory={addMemory=true},onDeleteMemory={m->scope.launch{dao.deleteMemory(m)}},onClearMemory={scope.launch{dao.clearMemories()}})
  }
 }
 }
 if(addMemory)AlertDialog(onDismissRequest={addMemory=false},title={Text("Save a memory")},text={OutlinedTextField(memoryDraft,{memoryDraft=it},placeholder={Text("Only save information you approve")})},
  confirmButton={TextButton(onClick={if(memoryDraft.isNotBlank()&&memoryEnabled)scope.launch{dao.addMemory(MemoryEntity(content=memoryDraft.trim()))};memoryDraft="";addMemory=false}){Text("Save")}},
  dismissButton={TextButton(onClick={addMemory=false}){Text("Cancel")}})
}

@Composable
private fun ChatScreen(modifier:Modifier,chats:List<ConversationEntity>,chatId:Long,messages:List<MessageEntity>,engineStatus:String,engineError:String?,isGenerating:Boolean,onPick:(Long)->Unit,onNew:()->Unit,onSend:(String)->Unit,onMic:()->Unit,onSpeak:(String)->Unit){
 var draft by remember{mutableStateOf("")}
 Column(modifier.fillMaxSize().imePadding().background(Brush.verticalGradient(listOf(Night,Color(0xFF0A1D32)))).padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("HAIRA AI",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold);Text("LOCAL PERSONAL ASSISTANT",color=Muted,fontSize=9.sp,letterSpacing=2.sp)};TextButton(onClick=onNew){Text("+ New",color=Cyan)}}
  if(chats.isNotEmpty())LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=8.dp)){items(chats,key={it.id}){chat->FilterChip(selected=chat.id==chatId,onClick={onPick(chat.id)},label={Text(chat.title.ifBlank{"Untitled"}.take(24),maxLines=1)})}}
  Card(Modifier.fillMaxWidth().padding(top=4.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(12.dp)){Text("ENGINE STATUS",color=Cyan,fontSize=10.sp,fontWeight=FontWeight.Bold);Text(engineStatus,color=if(engineStatus=="Model ready")Cyan else Warning,fontSize=12.sp);if(engineError!=null)Text(engineError,color=Warning,fontSize=11.sp)}}
  LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   if(messages.isEmpty())item{Text("Chat history is stored locally. AI replies are only generated when a real local model is loaded.",color=Muted,fontSize=13.sp)}
   items(messages,key={it.id}){m->Card(colors=CardDefaults.cardColors(containerColor=if(m.role=="user")Color(0xFF12344A)else Panel)){Column(Modifier.padding(12.dp)){Text(m.role.uppercase(),color=Cyan,fontSize=9.sp,fontWeight=FontWeight.Bold);Text(m.content,color=Color.White);if(m.role=="assistant")TextButton(onClick={onSpeak(m.content)}){Text("Read aloud")}}}}
   if(isGenerating)item{Text("HAIRA is thinking…",color=Cyan,fontSize=13.sp)}
  }
  Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onMic){Text("MIC",color=Cyan)};OutlinedTextField(draft,{draft=it},Modifier.weight(1f),placeholder={Text("Message HAIRA")},keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Sentences),maxLines=4,enabled=!isGenerating);TextButton(enabled=!isGenerating,onClick={val v=draft.trim();if(v.isNotEmpty()){onSend(v);draft=""}}){Text(if(isGenerating)"…" else "SEND",color=Cyan)}}
  Text("Offline speech recognition depends on installed Android language packs.",color=Muted,fontSize=9.sp)
 }
}

@Composable
private fun StudioScreen(modifier:Modifier,notes:List<NoteEntity>,onSave:(String,String)->Unit,onDelete:(NoteEntity)->Unit){
 var title by remember{mutableStateOf("")};var body by remember{mutableStateOf("")}
 Column(modifier.fillMaxSize().padding(18.dp)){Text("STUDIO",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold);Text("Local notes workspace",color=Muted)
  OutlinedTextField(title,{title=it},Modifier.fillMaxWidth().padding(top=12.dp),label={Text("Title")})
  OutlinedTextField(body,{body=it},Modifier.fillMaxWidth().padding(top=8.dp),label={Text("Note")},minLines=3)
  Button(onClick={if(title.isNotBlank()||body.isNotBlank()){onSave(title.ifBlank{"Untitled"},body);title="";body=""}},Modifier.padding(top=8.dp)){Text("Save note")}
  Text("SAVED NOTES",color=Cyan,modifier=Modifier.padding(top=14.dp))
  LazyColumn{items(notes,key={it.id}){n->Card(Modifier.fillMaxWidth().padding(vertical=4.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(n.title,color=Color.White,fontWeight=FontWeight.Bold);Text(n.body,color=Muted,maxLines=3)};TextButton(onClick={onDelete(n)}){Text("Delete")}}}}}
 }
}

@Composable
private fun ActionsScreen(modifier:Modifier,onSettings:()->Unit,onShare:()->Unit){Column(modifier.fillMaxSize().padding(20.dp)){Text("ACTIONS",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold);Button(onClick=onSettings,Modifier.fillMaxWidth().padding(top=12.dp)){Text("Open Android Settings")};OutlinedButton(onClick=onShare,Modifier.fillMaxWidth()){Text("Share HAIRA")};Text("Uses Android intents; no security restrictions are bypassed.",color=Muted,fontSize=12.sp)}}

@Composable
private fun SystemScreen(modifier:Modifier,models:List<ModelEntity>,memories:List<MemoryEntity>,memoryEnabled:Boolean,style:String,engineStatus:String,backendAvailable:Boolean,onImport:()->Unit,onDownload:(com.haira.ai.ai.ModelItem)->Unit,onLoad:(ModelEntity)->Unit,onRemove:(ModelEntity)->Unit,onMemory:(Boolean)->Unit,onStyle:(String)->Unit,onAddMemory:()->Unit,onDeleteMemory:(MemoryEntity)->Unit,onClearMemory:()->Unit){
 Column(modifier.fillMaxSize().padding(16.dp)){
  Text("SYSTEM",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold)
  Card(Modifier.fillMaxWidth().padding(top=10.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(12.dp)){Text("ENGINE DIAGNOSTICS",color=Cyan,fontWeight=FontWeight.Bold);Text("JNI backend: "+if(backendAvailable)"Available" else "Unavailable",color=if(backendAvailable)Cyan else Warning);Text("Model state: "+engineStatus,color=if(engineStatus=="Model ready")Cyan else Warning)}}
  Card(Modifier.fillMaxWidth().padding(top=10.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(12.dp)){Text("MODEL MANAGER",color=Cyan,fontWeight=FontWeight.Bold);Button(onClick=onImport,Modifier.fillMaxWidth()){Text("Import GGUF file")}
   ModelCatalog.models.forEach{item->Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(item.name,color=Color.White);Text(item.quant+" • "+(item.sizeBytes/1_000_000)+" MB",color=Muted,fontSize=10.sp)};TextButton(onClick={onDownload(item)}){Text("Download")}}}
   models.forEach{m->Column(Modifier.fillMaxWidth().padding(vertical=6.dp)){Text(m.displayName,color=Color.White);Text((m.sizeBytes/1_000_000).toString()+" MB • SHA-256 "+m.sha256.take(12),color=Muted,fontSize=10.sp);Row{TextButton(onClick={onLoad(m)}){Text("Load")};TextButton(onClick={onRemove(m)}){Text("Remove")}}}}
   Text("The bundled SmolLM2 model is installed and loaded automatically at startup. Qwen3 remains optional.",color=Muted,fontSize=11.sp)
  }}
  Card(Modifier.fillMaxWidth().padding(top=10.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(12.dp)){Text("USER-CONTROLLED MEMORY",color=Cyan,fontWeight=FontWeight.Bold);Row(verticalAlignment=Alignment.CenterVertically){Text("Enable memory",Modifier.weight(1f),color=Color.White);Switch(memoryEnabled,onMemory)};Row(verticalAlignment=Alignment.CenterVertically){Text("Saved: "+memories.size,color=Muted,modifier=Modifier.weight(1f));TextButton(onClick=onAddMemory,enabled=memoryEnabled){Text("Add")};TextButton(onClick=onClearMemory){Text("Clear")}};memories.forEach{m->Row(verticalAlignment=Alignment.CenterVertically){Text(m.content,Modifier.weight(1f),color=Color.White,fontSize=12.sp);TextButton(onClick={onDeleteMemory(m)}){Text("Delete")}}}}}
  Card(Modifier.fillMaxWidth().padding(top=10.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(12.dp)){Text("PERSONALITY",color=Cyan,fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("Friendly","Professional","Concise","Technical").forEach{FilterChip(selected=style==it,onClick={onStyle(it)},label={Text(it,fontSize=10.sp)})}};Text("HAIRA AI v2.0.0 • No ad SDKs",color=Muted,fontSize=11.sp)}}
 }
}

@Composable
private fun StatusScreen(modifier:Modifier,title:String,detail:String){Column(modifier.fillMaxSize().padding(22.dp)){Text(title,color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold);Text(detail,color=Muted,modifier=Modifier.padding(top=12.dp))}}
