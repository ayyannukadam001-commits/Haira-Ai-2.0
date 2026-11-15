package com.haira.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF050914)
private val Panel = Color(0xFF101B2B)
private val Cyan = Color(0xFF45D9F5)
private val Muted = Color(0xFF9BB3C8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HairaApp() }
    }
}

@Composable
fun HairaApp() {
    var section by remember { mutableStateOf("HAIRA") }
    MaterialTheme(colorScheme = darkColorScheme(background = Night, surface = Panel, primary = Cyan)) {
        Scaffold(
            containerColor = Night,
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    listOf("HAIRA", "Studio", "Actions", "Builder", "System").forEach { item ->
                        NavigationBarItem(
                            selected = section == item,
                            onClick = { section = item },
                            icon = { Icon(
                                when(item) {
                                    "HAIRA" -> Icons.Default.BubbleChart
                                    "Studio" -> Icons.Default.AutoAwesome
                                    "Actions" -> Icons.Default.Bolt
                                    "Builder" -> Icons.Default.Build
                                    else -> Icons.Default.Memory
                                }, contentDescription = item
                            ) },
                            label = { Text(item, fontSize = 10.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            when(section) {
                "HAIRA" -> Dashboard(Modifier.padding(padding))
                "Studio" -> InfoScreen("HAIRA Studio", "Writing, summarization and workspace tools will appear when their local engine is available.", Modifier.padding(padding))
                "Actions" -> InfoScreen("Actions", "Android actions will request permission before running.", Modifier.padding(padding))
                "Builder" -> InfoScreen("Builder", "Local project workspace.", Modifier.padding(padding))
                else -> InfoScreen("System", "Model engine: not installed\nInference: unavailable\nThis status is real; no model is bundled yet.", Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun Dashboard(modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Night, Color(0xFF0A1D32)))).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("HAIRA AI", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("PERSONAL INTELLIGENCE SYSTEM", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
            }
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Cyan)
        }
        Spacer(Modifier.height(26.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(190.dp).background(Brush.radialGradient(listOf(Color(0xFF164B68), Color(0xFF0A1A2A), Night)), RoundedCornerShape(100.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Hub, contentDescription = "HAIRA core", tint = Cyan, modifier = Modifier.size(100.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text("HAIRA CORE", color = Cyan, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text("Local AI engine not configured", color = Muted, fontSize = 12.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("SYSTEM STATUS", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                StatusLine("Inference engine", "Not installed")
                StatusLine("Offline model", "Not installed")
                StatusLine("Local memory", "Database setup pending")
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(value = message, onValueChange = { message = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Chat will be enabled after model setup") }, enabled = false, trailingIcon = { Icon(Icons.Default.Mic, contentDescription = "Voice") })
        Text("No simulated AI replies are shown. Install a compatible model to enable chat.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value, color = Color(0xFFFFB66D), fontSize = 12.sp)
    }
}

@Composable
private fun InfoScreen(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(22.dp)) {
        Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text(description, color = Muted, fontSize = 14.sp)
    }
}
