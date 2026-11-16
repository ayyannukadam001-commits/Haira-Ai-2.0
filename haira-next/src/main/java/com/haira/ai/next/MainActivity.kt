package com.haira.ai.next

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.room.Room
import com.haira.ai.ai.*
import com.haira.ai.data.*
import com.haira.ai.voice.VoiceOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

private val Ink = Color(0xFF07090F)
private val Panel = Color(0xFF121A27)
private val Panel2 = Color(0xFF1A2433)
private val Red = Color(0xFFFF3657)
private val Blue = Color(0xFF55A7FF)
private val Muted = Color(0xFF9AA8BA)
private val Txt = Color(0xFFF3F6FC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7,9,15)
        window.navigationBarColor = android.graphics.Color.rgb(7,9,15)
        setContent { HairaNextApp() }
    }
}

private data class RepoHit(val id: String, val downloads: Long)
private data class GgufHit(val repo: String, val path: String, val size: Long)

private object HuggingFace {
    suspend fun search(query: String): List<RepoHit> = withContext(Dispatchers.IO) {
        require(query.trim().length >= 2) { "Enter at least two characters." }
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val c = (URL("https://huggingface.co/api/models?search=$q&filter=gguf&limit=15&sort=downloads&direction=-1").openConnection() as HttpURLConnection)
        c.connectTimeout = 12000; c.readTimeout = 20000
        try {
            val a = JSONArray(c.inputStream.bufferedReader().use { it.readText().take(1500000) })
            buildList { for (i in 0 until a.length()) { val o=a.optJSONObject(i) ?: continue; val id=o.optString("id"); if(id.isNotBlank()) add(RepoHit(id,o.optLong("downloads"))) } }
        } finally { c.disconnect() }
    }
    suspend fun files(repo: String): List<GgufHit> = withContext(Dispatchers.IO) {
        val safe=repo.split('/').joinToString("/") { URLEncoder.encode(it,"UTF-8") }
        val c=(URL("https://huggingface.co/api/models/$safe/tree/main?recursive=true&expand=false").openConnection() as HttpURLConnection)
        c.connectTimeout=12000; c.readTimeout=25000
        try {
            val a=JSONArray(c.inputStream.bufferedReader().use { it.readText().take(2000000) })
            buildList { for(i in 0 until a.length()) { val o=a.optJSONObject(i) ?: continue; val p=o.optString("path"); val n=o.optLong("size"); if(p.endsWith(".gguf",true) && n>1048576L && n<=3000000000L) add(GgufHit(repo,p,n)) } }.sortedBy { it.size }.take(30)
        } finally { c.disconnect() }
    }
    suspend fun download(hit: GgufHit, context: android.content.Context): ModelEntity = withContext(Dispatchers.IO) {
        val dir=File(context.getExternalFilesDir(null),"HAIRA_Models"); check(dir.exists() || dir.mkdirs())
        val r=hit.repo.split('/').joinToString("/") { URLEncoder.encode(it,"UTF-8") }
        val p=hit.path.split('/').joinToString("/") { URLEncoder.encode(it,"UTF-8").replace("+","%20") }
        val c=(URL("https://huggingface.co/$r/resolve/main/$p").openConnection() as HttpURLConnection)
        c.connectTimeout=20000; c.readTimeout=120000; c.instanceFollowRedirects=true
        val name=hit.path.substringAfterLast('/').filter { it.isLetterOrDigit() || it in "._-" }.take(100).ifBlank { "model.gguf" }
        val target=File(dir,"${System.currentTimeMillis()}-$name"); val tmp=File(dir,target.name+".partial")
        try {
            val status=c.responseCode; require(status in 200..299) { "Download failed: HTTP $status" }
            val length=c.contentLengthLong; require(length in 1048576L..3000000000L) { "Unknown or unsupported model size." }
            require(android.os.StatFs(dir.absolutePath).availableBytes>length+100000000L) { "Not enough free storage." }
            c.inputStream.use { input -> tmp.outputStream().buffered().use { output -> input.copyTo(output) } }
            require(tmp.length()==length) { "Downloaded size mismatch." }
            val magic=ByteArray(4); tmp.inputStream().use { require(it.read(magic)==4 && magic.contentEquals(byteArrayOf(0x47,0x47,0x55,0x46))) { "Invalid GGUF file." } }
            val digest=MessageDigest.getInstance("SHA-256")
            tmp.inputStream().buffered().use { input -> val b=ByteArray(1024*1024); while(true){val n=input.read(b);if(n<0)break;digest.update(b,0,n)} }
            val hash=digest.digest().joinToString("") { "%02x".format(it) }
            check(tmp.renameTo(target)) { "Could not finalize model file." }
            ModelEntity(target.absolutePath,name,target.length(),hash)
        } finally { c.disconnect(); if(tmp.exists())tmp.delete() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HairaNextApp() {
    val ctx=LocalContext.current
    val scope= rememberCoroutineScope()
    val db= remember(ctx) { Room.databaseBuilder(ctx,HairaDatabase::class.java,"haira-next.db").addMigrations(HairaDatabase.MIGRATION_1_2,HairaDatabase.MIGRATION_2_3).build() }
    val dao= remember(db){db.dao()}
    val chats by dao.conversations().collectAsState(initial=emptyList())
    val models by dao.models().collectAsState(initial=emptyList())
    val memories by dao.memories().collectAsState(initial=emptyList())
    val store= remember(ctx){SecureProviderStore(ctx)}
    var provider by remember{mutableStateOf<RemoteProviderConfig?>(null)}
    var page by remember{mutableStateOf("Home")}
    var chatId by remember{mutableLongStateOf(0L)}
    var work by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var live by remember{mutableStateOf("")}
    var failure by remember{mutableStateOf<String?>(null)}
    var selectedPath by remember{mutableStateOf(ctx.getSharedPreferences("haira_next",0).getString("model_path",null))}
    var engine by remember{mutableStateOf("No local model loaded")}
    var image by remember{mutableStateOf<Uri?>(null)}
    var attachedText by remember{mutableStateOf<String?>(null)}
    var attachedName by remember{mutableStateOf<String?>(null)}
    var speech by remember{mutableStateOf<String?>(null)}
    var drawer by remember{mutableStateOf(false)}
    var voiceReady by remember{mutableStateOf(false)}
    val voice= remember(ctx){VoiceOutput(ctx){voiceReady=it}}
    DisposableEffect(db,voice){onDispose{LocalInference.unload();voice.close();db.close()}}
    LaunchedEffect(Unit){
        provider=withContext(Dispatchers.IO){store.load()}
        selectedPath?.let { path -> if(File(path).isFile){engine="Loading local model…";engine=if(withContext(Dispatchers.Default){LocalInference.load(path,2048)})"Local model ready" else "Local model failed"} }
    }

    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->image=uri;if(uri!=null)attachedName="Image attached"}
    val filePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{
        runCatching{ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText().take(120000)}?:error("Cannot read file.")}
            .onSuccess{attachedText=it;attachedName=uri.lastPathSegment?: "Text file"}
            .onFailure{failure=it.message}
    }}
    val modelPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{
        runCatching{ctx.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);ModelImporter(ctx).import(uri)}
            .onSuccess{dao.saveModel(it)}
            .onFailure{failure=it.message}
    }}
    val speechPicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->speech=result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()}
    val mic=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted){
        val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_PROMPT,"Speak to HAIRA")}
        runCatching{speechPicker.launch(intent)}.onFailure{failure="Speech service unavailable."}
    }else failure="Microphone permission denied."}

    MaterialTheme(colorScheme=darkColorScheme(primary=Red,secondary=Blue,background=Ink,surface=Panel,onSurface=Txt,onBackground=Txt)){
        ModalNavigationDrawer(
            drawerState=rememberDrawerState(if(drawer)DrawerValue.Open else DrawerValue.Closed),
            drawerContent={
                ModalDrawerSheet(drawerContainerColor=Panel){
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){BrandMark(44);Spacer(Modifier.width(12.dp));Column{Text("HAIRA",fontSize=22.sp,fontWeight=FontWeight.Black,letterSpacing=2.sp);Text("AI NEXT • 2.1",fontSize=10.sp,color=Muted,letterSpacing=2.sp)}}
                    listOf("New Chat","Images","Library","Customize","Projects","Remote","Explore","Models","Work Mode").forEach{label->
                        NavigationDrawerItem(label={Text(label)},icon={Icon(when(label){"New Chat"->Icons.Default.Add;"Images"->Icons.Default.Image;"Library"->Icons.Default.Folder;"Customize"->Icons.Default.Tune;"Projects"->Icons.Default.Workspaces;"Remote"->Icons.Default.Devices;"Explore"->Icons.Default.Explore;"Models"->Icons.Default.Memory;else->Icons.Default.Work},null)},selected=page==label,onClick={
                            page=when(label){"New Chat"->"Chat";"Work Mode"->{work=true;"Chat"};else->label}
                            if(label=="New Chat")scope.launch{chatId=dao.addConversation(ConversationEntity("New conversation"))}
                            drawer=false
                        },modifier=Modifier.padding(horizontal=10.dp))
                    }
                    HorizontalDivider(Modifier.padding(vertical=10.dp),color=Color.White.copy(.08f))
                    Text("RECENT CHATS",fontSize=10.sp,color=Muted,letterSpacing=1.5.sp,modifier=Modifier.padding(horizontal=18.dp))
                    chats.take(8).forEach{item->NavigationDrawerItem(label={Text(item.title,maxLines=1)},selected=chatId==item.id,onClick={chatId=item.id;page="Chat";drawer=false},modifier=Modifier.padding(horizontal=10.dp))}
                }
            }
        ){
            Scaffold(
                containerColor=Ink,
                topBar={TopAppBar(
                    title={Column{Text("HAIRA",fontWeight=FontWeight.Black,letterSpacing=2.sp);Text(if(provider?.enabled==true)"ONLINE • ${provider?.model}" else engine,fontSize=10.sp,color=Muted,maxLines=1)}},
                    navigationIcon={IconButton(onClick={drawer=true}){Icon(Icons.Default.Menu,"Menu")}},
                    actions={if(page=="Chat")IconButton(onClick={work=!work}){Icon(Icons.Default.Work,"Work mode",tint=if(work)Blue else Muted)};IconButton(onClick={page="Settings"}){Icon(Icons.Default.Settings,"Settings")}},
                    colors=TopAppBarDefaults.topAppBarColors(containerColor=Ink,titleContentColor=Txt,navigationIconContentColor=Txt,actionIconContentColor=Txt)
                )},
                bottomBar={NavigationBar(containerColor=Panel){listOf("Home" to Icons.Default.Home,"Chat" to Icons.Default.Chat,"Studio" to Icons.Default.AutoAwesome,"Work" to Icons.Default.Work,"System" to Icons.Default.Settings).forEach{(label,icon)->
                    NavigationBarItem(selected=page==label||(label=="System"&&page in listOf("Settings","Models")),onClick={
                        page=when(label){"System"->"Settings";"Work"->{work=true;"Chat"};else->label}
                        if(label=="Chat"&&chatId==0L)scope.launch{chatId=dao.addConversation(ConversationEntity("New conversation"))}
                    },icon={Icon(icon,null)},label={Text(label,fontSize=10.sp)})
                }}}
            ){padding->
                when(page){
                    "Home"->HomePage(Modifier.padding(padding),provider?.model?:"No online model selected",engine,chats.size,
                        {page="Chat";if(chatId==0L)scope.launch{chatId=dao.addConversation(ConversationEntity("New conversation"))}},
                        {work=true;page="Chat";if(chatId==0L)scope.launch{chatId=dao.addConversation(ConversationEntity("Work session"))}},
                        {page="Models"})
                    "Chat"->{
                        if(chatId==0L)LaunchedEffect(Unit){chatId=dao.addConversation(ConversationEntity("New conversation"))}
                        val messages by dao.messages(chatId).collectAsState(initial=emptyList())
                        ConversationPage(Modifier.padding(padding),messages,busy,live,failure,work,attachedName,speech,
                            onWork={work=it},onImage={imagePicker.launch(arrayOf("image/*"))},onFile={filePicker.launch(arrayOf("text/plain","text/markdown","application/json","text/csv"))},
                            onMic={mic.launch(Manifest.permission.RECORD_AUDIO)},onSpeak={voice.speak(it)},onClear={image=null;attachedText=null;attachedName=null},onSpeech={speech=null},
                            onSend={prompt->
                                if(!busy&&(prompt.isNotBlank()||image!=null||attachedText!=null))scope.launch{
                                    busy=true;live="";failure=null
                                    val imageToSend=image;val fileToSend=attachedText;val name=attachedName
                                    image=null;attachedText=null;attachedName=null
                                    try{
                                        val id=if(chatId==0L)dao.addConversation(ConversationEntity(prompt.take(36).ifBlank{"Image conversation"}))else chatId;chatId=id
                                        val userText=buildString{append(prompt);if(fileToSend!=null)append("\n\n[Attached file: $name]\n").append(fileToSend);if(imageToSend!=null)append("\n[Image attached]")}
                                        dao.addMessage(MessageEntity(conversationId=id,role="user",content=userText))
                                        val history=dao.messages(id).first().takeLast(10)
                                        val system="You are HAIRA, an honest and helpful AI assistant. Be concise but complete. Never claim an action succeeded unless it did. "+if(work)"WORK MODE: break complex goals into steps, track progress and assumptions, and ask before external or irreversible actions." else "NORMAL MODE: help with everyday questions and tasks."+"\nUser-approved memory:\n"+memories.joinToString("\n"){it.content}.take(1500)
                                        val remote=provider?.takeIf{it.enabled}
                                        val answer=if(remote!=null){
                                            if(imageToSend!=null)OpenAiCompatibleClient.completeWithImage(remote,prompt.ifBlank{"Describe this image."},ImageAttachmentEncoder.toJpegDataUrl(ctx,imageToSend))
                                            else OpenAiCompatibleClient.stream(remote,listOf(RemoteChatMessage("system",system))+history.map{RemoteChatMessage(it.role,it.content.take(6000))}){live+=it}
                                        }else{
                                            require(imageToSend==null){"Image understanding requires a connected vision-capable online model."}
                                            check(LocalInference.isLoaded){"No AI is ready. Connect an API provider or install and load a local GGUF model."}
                                            withContext(Dispatchers.Default){LocalInference.generate("<|im_start|>system\n$system\n<|im_end|>\n"+history.joinToString("\n"){"<|im_start|>${it.role}\n${it.content.take(3000)}\n<|im_end|>"}+"\n<|im_start|>assistant\n")}?:error("Local model returned an empty response.")
                                        }
                                        require(answer.isNotBlank()){"Model returned an empty response."}
                                        dao.addMessage(MessageEntity(conversationId=id,role="assistant",content=answer.trim()))
                                    }catch(e:Exception){failure=e.message?: "Request failed."}finally{busy=false;live=""}
                                }
                            })
                    }
                    "Models"->ModelPage(Modifier.padding(padding),models,selectedPath,engine,{modelPicker.launch(arrayOf("*/*"))},
                        {q,done->scope.launch{runCatching{HuggingFace.search(q)}.onSuccess{done(it,null)}.onFailure{done(emptyList(),it.message)}}},
                        {r,done->scope.launch{runCatching{HuggingFace.files(r)}.onSuccess{done(it,null)}.onFailure{done(emptyList(),it.message)}}},
                        {f->scope.launch{engine="Downloading model…";runCatching{HuggingFace.download(f,ctx)}.onSuccess{dao.saveModel(it);engine="Model downloaded and verified"}.onFailure{engine="Download failed";failure=it.message}}},
                        {m->scope.launch{engine="Loading ${m.displayName}…";val ok=withContext(Dispatchers.Default){LocalInference.load(m.path,2048)};if(ok){selectedPath=m.path;ctx.getSharedPreferences("haira_next",0).edit().putString("model_path",m.path).apply();engine="Local model ready"}else engine="Local model failed to load"}},
                        {m->scope.launch{if(selectedPath==m.path){LocalInference.unload();selectedPath=null;ctx.getSharedPreferences("haira_next",0).edit().remove("model_path").apply()};File(m.path).delete();dao.deleteModel(m)}})
                    "Settings"->SettingsPage(Modifier.padding(padding),provider,{config->scope.launch{runCatching{withContext(Dispatchers.IO){store.save(config)}}.onSuccess{provider=config}.onFailure{failure=it.message}}},
                        {config->scope.launch{runCatching{OpenAiCompatibleClient.complete(config,listOf(RemoteChatMessage("user","Reply OK only.")))}.onSuccess{failure="Connection test passed."}.onFailure{failure=it.message}}},{page="Models"})
                    "Studio"->InfoPage(Modifier.padding(padding),"HAIRA STUDIO","Image understanding uses a vision-capable provider. Voice input and Android text-to-speech are available in Chat. Text, Markdown, JSON and CSV attachments can be added to conversations.")
                    "Images"->InfoPage(Modifier.padding(padding),"IMAGE GALLERY","Generated and attached image history will be shown here when a provider supports image generation. Image understanding is available from the chat attachment button.")
                    "Library"->InfoPage(Modifier.padding(padding),"YOUR LIBRARY","Files and resources attached to chats are available in their conversation history. Saved project library management is planned for a later verified module.")
                    "Customize"->InfoPage(Modifier.padding(padding),"CUSTOMIZE","HAIRA uses its original H-Core / Quantum H identity. Avatar, theme and voice preferences can be expanded here.")
                    "Projects"->InfoPage(Modifier.padding(padding),"PROJECT WORKSPACES","Work Mode keeps task instructions and conversation context together. Separate project file spaces are planned.")
                    "Remote"->InfoPage(Modifier.padding(padding),"REMOTE ACTIONS","Remote and device actions are not silently enabled. Any future connected action will require explicit permission.")
                    "Explore"->InfoPage(Modifier.padding(padding),"EXPLORE CAPABILITIES","Connect an API provider or install a GGUF model. Model capability depends on the provider, selected model and device.")
                    else->HomePage(Modifier.padding(padding),provider?.model?:"No model",engine,chats.size,{page="Chat"},{work=true;page="Chat"},{page="Models"})
                }
            }
        }
    }
}

@Composable
private fun BrandMark(size:Int){
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF2B111C),Color(0xFF101D32)))),contentAlignment=Alignment.Center){
        Canvas(Modifier.fillMaxSize().padding(3.dp)){
            val w=this.size.width;val h=this.size.height
            val p=Path().apply{moveTo(w*.5f,h*.04f);lineTo(w*.94f,h*.28f);lineTo(w*.94f,h*.72f);lineTo(w*.5f,h*.96f);lineTo(w*.06f,h*.72f);lineTo(w*.06f,h*.28f);close()}
            drawPath(p,Red,style=Stroke(w*.055f));drawPath(p,Blue.copy(.8f),style=Stroke(w*.025f))
            drawLine(Txt,androidx.compose.ui.geometry.Offset(w*.28f,h*.3f),androidx.compose.ui.geometry.Offset(w*.28f,h*.7f),w*.09f)
            drawLine(Txt,androidx.compose.ui.geometry.Offset(w*.72f,h*.3f),androidx.compose.ui.geometry.Offset(w*.72f,h*.7f),w*.09f)
            drawLine(Txt,androidx.compose.ui.geometry.Offset(w*.28f,h*.5f),androidx.compose.ui.geometry.Offset(w*.72f,h*.5f),w*.09f)
            drawCircle(Blue,w*.055f,androidx.compose.ui.geometry.Offset(w*.5f,h*.5f))
        }
    }
}

@Composable
private fun HomePage(modifier:Modifier,model:String,engine:String,chats:Int,onChat:()->Unit,onWork:()->Unit,onModels:()->Unit){
    val anim= rememberInfiniteTransition(label="core")
    val glow by anim.animateFloat(.3f,.85f,infiniteRepeatable(tween(1700),RepeatMode.Reverse),label="glow")
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Spacer(Modifier.height(12.dp))
        Box(Modifier.size(218.dp),contentAlignment=Alignment.Center){
            Canvas(Modifier.fillMaxSize()){drawCircle(Blue.copy(alpha=glow*.10f));drawCircle(Red.copy(alpha=glow*.18f),style=Stroke(2.dp.toPx()),radius=size.minDimension*.39f);drawCircle(Blue.copy(.65f),style=Stroke(1.dp.toPx()),radius=size.minDimension*.31f);drawCircle(Color(0xFF17263C),radius=size.minDimension*.23f)}
            BrandMark(92)
        }
        Text("Your AI. Your way.",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Txt)
        Text("PERSONAL INTELLIGENCE SYSTEM",fontSize=10.sp,color=Muted,letterSpacing=2.sp)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
            StatusTile("ONLINE MODEL",model,Icons.Default.Cloud,Modifier.weight(1f))
            StatusTile("LOCAL ENGINE",engine,Icons.Default.Memory,Modifier.weight(1f))
        }
        Spacer(Modifier.height(9.dp));StatusTile("CHAT HISTORY","$chats saved conversations",Icons.Default.Forum,Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Button(onClick=onChat,modifier=Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(17.dp)){Icon(Icons.Default.Chat,null);Spacer(Modifier.width(8.dp));Text("Start a conversation",fontWeight=FontWeight.Bold)}
        Spacer(Modifier.height(9.dp))
        OutlinedButton(onClick=onWork,modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(17.dp)){Icon(Icons.Default.Work,null);Spacer(Modifier.width(8.dp));Text("Enter Work Mode")}
        TextButton(onClick=onModels){Icon(Icons.Default.Memory,null);Spacer(Modifier.width(6.dp));Text("Explore local models")}
    }
}
@Composable
private fun StatusTile(title:String,value:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier){
    Card(modifier,colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(17.dp)){Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Blue);Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Text(title,fontSize=9.sp,color=Muted,letterSpacing=1.sp);Text(value.ifBlank{"Not configured"},fontSize=11.sp,color=Txt,maxLines=2)}}}
}

@Composable
private fun ConversationPage(
    modifier:Modifier,messages:List<MessageEntity>,busy:Boolean,live:String,error:String?,work:Boolean,attachment:String?,speech:String?,
    onWork:(Boolean)->Unit,onImage:()->Unit,onFile:()->Unit,onMic:()->Unit,onSpeak:(String)->Unit,onClear:()->Unit,onSpeech:()->Unit,onSend:(String)->Unit
){
    var draft by remember{mutableStateOf("")};var menu by remember{mutableStateOf(false)}
    LaunchedEffect(speech){if(!speech.isNullOrBlank()){draft=(draft+" "+speech).trim();onSpeech()}}
    Column(modifier.fillMaxSize().imePadding()){
        if(work)Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=5.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF12243B)).padding(8.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Work,null,tint=Blue);Text("  WORK MODE ACTIVE",color=Blue,fontSize=10.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));TextButton(onClick={onWork(false)}){Text("Turn off")}}
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal=13.dp),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
            if(messages.isEmpty())item{Column(Modifier.fillMaxWidth().padding(top=45.dp),horizontalAlignment=Alignment.CenterHorizontally){BrandMark(62);Spacer(Modifier.height(14.dp));Text("What can I help you with?",color=Txt,fontSize=19.sp,fontWeight=FontWeight.SemiBold);Text("Ask a question, attach an image or add a file.",color=Muted,fontSize=12.sp)}}
            items(messages){m->Row(Modifier.fillMaxWidth(),horizontalArrangement=if(m.role=="user")Arrangement.End else Arrangement.Start){Column(Modifier.widthIn(max=310.dp).clip(RoundedCornerShape(17.dp)).background(if(m.role=="user")Color(0xFF30202B)else Panel).padding(13.dp)){Text(if(m.role=="user")"YOU" else "HAIRA",fontSize=9.sp,color=if(m.role=="user")Color(0xFFFF8295)else Blue,fontWeight=FontWeight.Bold,letterSpacing=1.2.sp);Spacer(Modifier.height(5.dp));Text(m.content,color=Txt,fontSize=14.sp);if(m.role=="assistant")TextButton(onClick={onSpeak(m.content)}){Icon(Icons.Default.VolumeUp,null);Text("Speak")}}}}
            if(busy)item{Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(13.dp)){Text(if(live.isBlank())"HAIRA IS THINKING…" else live,color=Txt,fontSize=14.sp);LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=8.dp),color=Red)}}
            if(!error.isNullOrBlank())item{Text(error,color=Color(0xFFFF91A2),fontSize=12.sp,modifier=Modifier.fillMaxWidth().background(Color(0xFF2B1119),RoundedCornerShape(12.dp)).padding(11.dp))}
        }
        Surface(color=Ink,shadowElevation=10.dp){Column(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=7.dp)){
            if(!attachment.isNullOrBlank())Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.AttachFile,null,tint=Blue);Text(attachment,color=Blue,fontSize=11.sp,modifier=Modifier.weight(1f));IconButton(onClick=onClear){Icon(Icons.Default.Close,"Remove",tint=Muted)}}
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).border(1.dp,Color.White.copy(.08f),RoundedCornerShape(22.dp)).padding(horizontal=3.dp,vertical=4.dp),verticalAlignment=Alignment.Bottom){
                Box{IconButton(onClick={menu=true}){Icon(Icons.Default.Add,"Add attachment",tint=Blue)};DropdownMenu(expanded=menu,onDismissRequest={menu=false}){DropdownMenuItem(text={Text("Add image")},leadingIcon={Icon(Icons.Default.Image,null)},onClick={menu=false;onImage()});DropdownMenuItem(text={Text("Add text / document")},leadingIcon={Icon(Icons.Default.Description,null)},onClick={menu=false;onFile()})}}
                TextField(value=draft,onValueChange={draft=it},modifier=Modifier.weight(1f).heightIn(min=48.dp,max=145.dp),placeholder={Text("Message HAIRA…",color=Muted)},minLines=1,maxLines=5,colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,cursorColor=Red,focusedTextColor=Txt,unfocusedTextColor=Txt))
                IconButton(onClick=onMic){Icon(Icons.Default.Mic,"Voice input",tint=Blue)}
                IconButton(enabled=!busy&&(draft.isNotBlank()||attachment!=null),onClick={onSend(draft);draft=""}){Icon(Icons.Default.Send,"Send",tint=Red)}
            }
            Text("Images require a vision-capable API model • Text files are included in the prompt",fontSize=9.sp,color=Muted,modifier=Modifier.padding(start=8.dp,top=4.dp))
        }}
    }
}

@Composable
private fun ModelPage(
    modifier:Modifier,models:List<ModelEntity>,selected:String?,status:String,onImport:()->Unit,
    onSearch:(String,(List<RepoHit>,String?)->Unit)->Unit,onFiles:(String,(List<GgufHit>,String?)->Unit)->Unit,
    onDownload:(GgufHit)->Unit,onLoad:(ModelEntity)->Unit,onDelete:(ModelEntity)->Unit
){
    var query by remember{mutableStateOf("Qwen GGUF")};var repos by remember{mutableStateOf(emptyList<RepoHit>())};var files by remember{mutableStateOf(emptyList<GgufHit>())};var repo by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};var info by remember{mutableStateOf<String?>(null)}
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(15.dp)){
        Text("LOCAL MODEL LAB",color=Blue,fontSize=10.sp,letterSpacing=2.sp);Text("Find your offline model",color=Txt,fontSize=23.sp,fontWeight=FontWeight.Bold)
        Text("Search Hugging Face GGUF repositories, inspect files, download and verify, or import a model from your phone.",color=Muted,fontSize=12.sp)
        Spacer(Modifier.height(12.dp));OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),label={Text("Search models")},singleLine=true)
        Button(onClick={busy=true;onSearch(query){r,e->repos=r;info=e?: "${r.size} repositories found";busy=false}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Search,null);Text("  Search Hugging Face")}
        OutlinedButton(onClick=onImport,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.FileOpen,null);Text("  Import GGUF from phone")}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        info?.let{Text(it,color=Muted,fontSize=11.sp)}
        repos.forEach{r->Card(Modifier.fillMaxWidth().padding(vertical=3.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(11.dp)){Text(r.id,color=Txt,fontWeight=FontWeight.SemiBold);Text("${r.downloads} downloads",color=Muted,fontSize=10.sp);TextButton(onClick={repo=r.id;busy=true;onFiles(r.id){f,e->files=f;info=e?: "${f.size} GGUF files found";busy=false}}){Text("Browse model files")}}}}
        if(repo!=null){Text("GGUF FILES • $repo",color=Blue,fontSize=10.sp);files.forEach{f->Card(Modifier.fillMaxWidth().padding(vertical=3.dp),colors=CardDefaults.cardColors(containerColor=Panel2)){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(f.path.substringAfterLast('/'),color=Txt,fontSize=12.sp);Text(String.format("%.1f MB",f.size/1000000.0),color=Muted,fontSize=10.sp)};TextButton(onClick={onDownload(f)}){Text("Download")}}}}}
        Spacer(Modifier.height(15.dp));Text("INSTALLED MODELS",color=Blue,fontSize=10.sp,letterSpacing=2.sp);Text(status,color=Muted,fontSize=11.sp)
        models.forEach{m->Card(Modifier.fillMaxWidth().padding(vertical=4.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(12.dp)){Text(m.displayName,color=Txt,fontWeight=FontWeight.Bold);Text(String.format("%.1f MB • SHA-256 %.12s…",m.sizeBytes/1000000.0,m.sha256),color=Muted,fontSize=10.sp);Row{TextButton(onClick={onLoad(m)}){Text(if(selected==m.path)"Selected" else "Load model")};TextButton(onClick={onDelete(m)}){Text("Delete",color=Red)}}}}}
        Text("Downloads start only after you tap Download. Large models may not fit the phone's RAM or storage.",color=Muted,fontSize=10.sp,modifier=Modifier.padding(vertical=12.dp))
    }
}

@Composable
private fun SettingsPage(modifier:Modifier,saved:RemoteProviderConfig?,onSave:(RemoteProviderConfig)->Unit,onTest:(RemoteProviderConfig)->Unit,onModels:()->Unit){
    var preset by remember(saved){mutableStateOf("FreeLLMAPI / Custom")};var url by remember(saved){mutableStateOf(saved?.baseUrl?:"")};var model by remember(saved){mutableStateOf(saved?.model?:"")};var key by remember(saved){mutableStateOf(saved?.apiKey?:"")};var enabled by remember(saved){mutableStateOf(saved?.enabled?:false)}
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)){
        Text("SYSTEM CONFIGURATION",color=Blue,fontSize=10.sp,letterSpacing=2.sp);Text("Online AI providers",color=Txt,fontSize=23.sp,fontWeight=FontWeight.Bold)
        Text("One OpenAI-compatible provider can be active at a time. Provider keys are encrypted with Android Keystore.",color=Muted,fontSize=12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("Groq","OpenRouter","Cloudflare AI","FreeLLMAPI","Custom").forEach{p->FilterChip(selected=preset==p,onClick={preset=p;url=when(p){"Groq"->"https://api.groq.com/openai/v1";"OpenRouter"->"https://openrouter.ai/api/v1";else->""}},label={Text(p)})}}
        OutlinedTextField(url,{url=it},modifier=Modifier.fillMaxWidth(),label={Text("Provider base URL")},singleLine=true)
        OutlinedTextField(model,{model=it},modifier=Modifier.fillMaxWidth(),label={Text("Model ID")},singleLine=true)
        OutlinedTextField(key,{key=it},modifier=Modifier.fillMaxWidth(),label={Text("API key")},singleLine=true,visualTransformation=PasswordVisualTransformation())
        Row(verticalAlignment=Alignment.CenterVertically){Text("Use online provider",color=Txt,modifier=Modifier.weight(1f));Switch(enabled,{enabled=it})}
        Button(onClick={onSave(RemoteProviderConfig(url.trim(),model.trim(),key,enabled))},modifier=Modifier.fillMaxWidth(),enabled=url.startsWith("https://")&&model.isNotBlank()&&key.isNotBlank()){Text("Save encrypted provider")}
        OutlinedButton(onClick={onTest(RemoteProviderConfig(url.trim(),model.trim(),key,true))},modifier=Modifier.fillMaxWidth(),enabled=url.startsWith("https://")&&model.isNotBlank()&&key.isNotBlank()){Text("Test connection")}
        HorizontalDivider(Modifier.padding(vertical=14.dp),color=Color.White.copy(.08f))
        Text("FreeLLMAPI",color=Blue,fontWeight=FontWeight.Bold);Text("Its exact endpoint and model IDs must be copied from the provider's current documentation. HAIRA intentionally does not invent them.",color=Muted,fontSize=12.sp)
        OutlinedButton(onClick=onModels,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Memory,null);Text("  Open Local Model Lab")}
    }
}

@Composable
private fun InfoPage(modifier:Modifier,title:String,body:String){
    Column(modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.Center){BrandMark(56);Spacer(Modifier.height(16.dp));Text(title,color=Txt,fontSize=23.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));Text(body,color=Muted,fontSize=13.sp)}
}
