package com.haira.ai.next

import android.os.Build
import android.os.StatFs
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haira.ai.data.ConversationEntity
import com.haira.ai.data.MemoryEntity
import com.haira.ai.data.NoteEntity
import java.io.File
import java.text.DateFormat
import java.util.Date

private val FeatureBg = Color(0xFF07090F)
private val FeaturePanel = Color(0xFF121A27)
private val FeatureBlue = Color(0xFF55DFFF)
private val FeatureRed = Color(0xFFFF4968)
private val FeatureText = Color(0xFFF3F6FC)
private val FeatureMuted = Color(0xFF9AA8BA)

@Composable
fun MemoryCenterPage(
    modifier: Modifier,
    memories: List<MemoryEntity>,
    chats: List<ConversationEntity>,
    notes: List<NoteEntity>,
    onAddMemory: (String) -> Unit,
    onDeleteMemory: (MemoryEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("All") }
    val filtered = memories.filter { it.content.contains(query, true) }
    Column(modifier.fillMaxSize().padding(15.dp)) {
        FeatureHeading("MEMORY CENTER", "Your saved context, under your control")
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("All", "Memories", "Chats", "Notes").forEach { item ->
                FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item) })
            }
        }
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search memories…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        if (tab == "All" || tab == "Memories") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SAVED MEMORIES", color = FeatureBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${filtered.size} items", color = FeatureMuted, fontSize = 10.sp)
            }
            OutlinedTextField(draft, { draft = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add a memory you want HAIRA to use…") }, minLines = 2)
            Button(onClick = { if (draft.isNotBlank()) { onAddMemory(draft.trim()); draft = "" } },
                enabled = draft.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("  Save memory") }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(filtered, key = { it.id }) { memory ->
                    Card(colors = CardDefaults.cardColors(containerColor = FeaturePanel)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, null, tint = FeatureBlue)
                            Spacer(Modifier.width(9.dp))
                            Text(memory.content, color = FeatureText, modifier = Modifier.weight(1f), fontSize = 13.sp)
                            IconButton(onClick = { onDeleteMemory(memory) }) { Icon(Icons.Default.DeleteOutline, "Delete memory", tint = FeatureRed) }
                        }
                    }
                }
            }
        } else {
            val count = if (tab == "Chats") chats.size else notes.size
            Text("$count saved $tab", color = FeatureMuted, modifier = Modifier.padding(top = 16.dp))
            LazyColumn(Modifier.weight(1f)) {
                if (tab == "Chats") items(chats, key = { it.id }) { Text(it.title, color = FeatureText, modifier = Modifier.fillMaxWidth().padding(12.dp)) }
                else items(notes, key = { it.id }) { Text(it.title, color = FeatureText, modifier = Modifier.fillMaxWidth().padding(12.dp)) }
            }
        }
        Text("Only memories you add here are injected into HAIRA's chat context. Delete any item to stop using it.",
            color = FeatureMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable
fun SkillsHubPage(
    modifier: Modifier,
    onVoice: () -> Unit,
    onMemory: () -> Unit,
    onNotes: () -> Unit,
    onModels: () -> Unit,
    onDevice: () -> Unit,
    onChat: () -> Unit
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(15.dp)) {
        FeatureHeading("SKILLS HUB", "Tools that are connected and ready")
        Text("Tap a tool to open its working screen. Unavailable integrations are not shown as active.",
            color = FeatureMuted, fontSize = 11.sp)
        val tools = listOf(
            Triple("HAIRA Chat", Icons.Default.Chat, onChat),
            Triple("Voice Assistant", Icons.Default.Mic, onVoice),
            Triple("Memory Center", Icons.Default.Psychology, onMemory),
            Triple("Notes", Icons.Default.Notes, onNotes),
            Triple("Model Manager", Icons.Default.Memory, onModels),
            Triple("Device Info", Icons.Default.PhoneAndroid, onDevice)
        )
        tools.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                pair.forEach { (label, icon, action) ->
                    Card(onClick = action, modifier = Modifier.weight(1f).height(112.dp),
                        colors = CardDefaults.cardColors(containerColor = FeaturePanel),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FeatureBlue.copy(alpha = .25f))) {
                        Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, null, tint = FeatureBlue, modifier = Modifier.size(27.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(label, color = FeatureText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun VoiceAssistantPage(
    modifier: Modifier,
    transcript: String?,
    onMic: () -> Unit,
    onSpeak: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        FeatureHeading("VOICE ASSISTANT", "Speech recognition + Android text-to-speech")
        Box(Modifier.size(190.dp).clip(RoundedCornerShape(100.dp))
            .background(FeatureBlue.copy(alpha = .08f))
            .border(2.dp, FeatureBlue.copy(alpha = .65f), RoundedCornerShape(100.dp)),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Mic, null, tint = FeatureBlue, modifier = Modifier.size(78.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(if (transcript.isNullOrBlank()) "Ready to listen" else "Recognized speech",
            color = FeatureText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(transcript ?: "Tap the microphone and speak. Recognition availability depends on the speech service installed on your phone.",
            color = FeatureMuted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        Button(onClick = onMic, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Mic, null); Text("  Start voice input") }
        OutlinedButton(onClick = { if (!transcript.isNullOrBlank()) onSpeak(transcript) },
            enabled = !transcript.isNullOrBlank(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.VolumeUp, null); Text("  Read transcript aloud")
        }
        TextButton(onClick = onClear) { Text("Clear transcript") }
    }
}

@Composable
fun NotesCenterPage(
    modifier: Modifier,
    notes: List<NoteEntity>,
    onSave: (NoteEntity) -> Unit,
    onDelete: (NoteEntity) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(15.dp)) {
        FeatureHeading("NOTES", "Create and keep notes on this device")
        OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
        OutlinedTextField(body, { body = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Note") }, minLines = 3)
        Button(onClick = { if (title.isNotBlank() || body.isNotBlank()) {
            onSave(NoteEntity(title = title.ifBlank { "Untitled note" }, body = body)); title = ""; body = ""
        } }, enabled = title.isNotBlank() || body.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Save, null); Text("  Save note")
        }
        Text("SAVED NOTES • ${notes.size}", color = FeatureBlue, fontSize = 10.sp, modifier = Modifier.padding(top = 14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(notes, key = { it.id }) { note ->
                Card(colors = CardDefaults.cardColors(containerColor = FeaturePanel)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(note.title, color = FeatureText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onDelete(note) }) { Icon(Icons.Default.DeleteOutline, "Delete note", tint = FeatureRed) }
                        }
                        Text(note.body, color = FeatureMuted, fontSize = 12.sp)
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(note.updatedAt)),
                            color = FeatureMuted, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceInfoPage(modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val stat = remember { runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull() }
    val free = stat?.availableBytes?.div(1024L * 1024L)
    val total = stat?.totalBytes?.div(1024L * 1024L)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        FeatureHeading("DEVICE INFO", "Runtime details reported by Android")
        DeviceRow("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
        DeviceRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        DeviceRow("App", "HAIRA AI Next")
        DeviceRow("Storage available to app", free?.let { "$it MB free" } ?: "Unavailable")
        DeviceRow("App data partition", total?.let { "$it MB total" } ?: "Unavailable")
        Text("Storage values describe the app's internal files area, not total phone storage or RAM.",
            color = FeatureMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun FeatureHeading(title: String, subtitle: String) {
    Text(title, color = FeatureBlue, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
    Text(subtitle, color = FeatureText, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
}

@Composable
private fun DeviceRow(label: String, value: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = FeaturePanel)) {
        Column(Modifier.padding(13.dp)) {
            Text(label, color = FeatureMuted, fontSize = 10.sp)
            Text(value, color = FeatureText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}


@Composable
fun HomeDashboardPage(
    modifier: Modifier,
    model: String,
    engine: String,
    online: Boolean,
    memoryCount: Int,
    recentActivity: String,
    onChat: () -> Unit,
    onVoice: () -> Unit,
    onSkills: () -> Unit,
    onModels: () -> Unit,
    onSettings: () -> Unit
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("HAIRA AI 2.0", color = FeatureText, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("YOUR PERSONAL AI ASSISTANT", color = FeatureMuted, fontSize = 9.sp, letterSpacing = 1.4.sp)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, "Settings", tint = FeatureBlue, modifier = Modifier.size(28.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            StatusPanel("STATUS", if (online) "Online" else "Offline", online, Modifier.weight(1f))
            StatusPanel("MEMORY", "$memoryCount saved", true, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPanel("MODEL", model, online, Modifier.weight(1f))
            StatusPanel("SKILLS", "6 tools", true, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(345.dp).clip(RoundedCornerShape(26.dp))
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF0D2440), Color(0xFF080F1C), FeatureBg)))
            .border(1.dp, FeatureBlue.copy(alpha = .55f), RoundedCornerShape(26.dp))) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height * .43f)
                val radius = size.minDimension * .40f
                drawCircle(FeatureBlue.copy(alpha = .10f), radius, center)
                drawCircle(FeatureBlue.copy(alpha = .65f), radius * .84f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                drawCircle(FeatureRed.copy(alpha = .60f), radius * .94f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                drawArc(FeatureBlue, 25f, 105f, false, androidx.compose.ui.geometry.Offset(center.x-radius*.84f,center.y-radius*.84f), androidx.compose.ui.geometry.Size(radius*1.68f,radius*1.68f), style=androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                drawArc(FeatureRed, 205f, 50f, false, androidx.compose.ui.geometry.Offset(center.x-radius*.94f,center.y-radius*.94f), androidx.compose.ui.geometry.Size(radius*1.88f,radius*1.88f), style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(width = 194.dp, height = 220.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 182.dp, height = 200.dp).clip(RoundedCornerShape(90.dp))
                        .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFF235F8D), Color(0xFF0C1D33), Color.Transparent)))
                        .border(1.dp, FeatureBlue.copy(alpha = .55f), RoundedCornerShape(90.dp)))
                    Box(Modifier.offset(y = (-14).dp).size(width = 122.dp, height = 148.dp).clip(RoundedCornerShape(topStart = 62.dp, topEnd = 62.dp, bottomStart = 42.dp, bottomEnd = 42.dp))
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF173D60), Color(0xFF091626))))
                        .border(2.dp, FeatureBlue.copy(alpha = .85f), RoundedCornerShape(topStart = 62.dp, topEnd = 62.dp, bottomStart = 42.dp, bottomEnd = 42.dp))) {
                        Column(Modifier.fillMaxSize().padding(top = 57.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                Box(Modifier.size(width = 18.dp, height = 6.dp).clip(RoundedCornerShape(50)).background(FeatureBlue))
                                Box(Modifier.size(width = 18.dp, height = 6.dp).clip(RoundedCornerShape(50)).background(FeatureBlue))
                            }
                            Spacer(Modifier.height(22.dp))
                            Text("H", color = FeatureBlue, fontSize = 25.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).size(width = 174.dp, height = 58.dp)
                        .clip(RoundedCornerShape(topStart = 70.dp, topEnd = 70.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1B456B), Color(0xFF091A2B))))
                        .border(1.dp, FeatureBlue.copy(alpha = .7f), RoundedCornerShape(topStart = 70.dp, topEnd = 70.dp, bottomStart = 20.dp, bottomEnd = 20.dp)))
                }
                Text("HAIRA", color = FeatureText, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Text("HOLOGRAPHIC CORE • ACTIVE", color = FeatureBlue, fontSize = 9.sp, letterSpacing = 1.sp)
            }
            Text("H-CORE 2.0", color = FeatureBlue, fontSize = 9.sp, modifier = Modifier.align(Alignment.TopStart).padding(13.dp))
            Text("SECURE • READY", color = FeatureRed, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(13.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onChat, modifier = Modifier.weight(1f).height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = FeatureRed)) {
                Icon(Icons.Default.Chat, null); Spacer(Modifier.width(6.dp)); Text("Quick Chat")
            }
            Button(onClick = onVoice, modifier = Modifier.size(58.dp), shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF103B60))) {
                Icon(Icons.Default.Mic, "Voice assistant", tint = FeatureBlue, modifier = Modifier.size(25.dp))
            }
            OutlinedButton(onClick = onSkills, modifier = Modifier.weight(1f).height(54.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = FeatureBlue); Spacer(Modifier.width(6.dp)); Text("Skills", color = FeatureText)
            }
        }
        Card(Modifier.fillMaxWidth().padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = FeaturePanel),
            border = androidx.compose.foundation.BorderStroke(1.dp, FeatureBlue.copy(alpha = .2f))) {
            Column(Modifier.padding(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RECENT ACTIVITY", color = FeatureText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("LOCAL", color = FeatureMuted, fontSize = 9.sp)
                }
                Text(recentActivity.ifBlank { "No recent conversation yet." }, color = FeatureMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
            }
        }
        OutlinedButton(onClick = onModels, modifier = Modifier.fillMaxWidth().padding(top = 9.dp)) {
            Icon(Icons.Default.Memory, null, tint = FeatureBlue); Text("  MODEL MANAGER • $engine", color = FeatureText, fontSize = 10.sp)
        }
    }
}

@Composable
private fun StatusPanel(title: String, value: String, active: Boolean, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = FeaturePanel),
        border = androidx.compose.foundation.BorderStroke(1.dp, (if (active) FeatureBlue else FeatureRed).copy(alpha = .35f))) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(title, color = FeatureMuted, fontSize = 9.sp, letterSpacing = 1.sp)
            Text(value, color = if (active) FeatureText else FeatureRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
