package ai.soumya.assistant.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.soumya.assistant.R
import ai.soumya.assistant.data.MemoryEntity
import ai.soumya.assistant.data.Prefs
import ai.soumya.assistant.speech.VoiceState
import ai.soumya.assistant.ui.CharacterView
import ai.soumya.assistant.ui.StatusChip
import ai.soumya.assistant.ui.theme.CardBg
import ai.soumya.assistant.ui.theme.Cyan400
import ai.soumya.assistant.ui.theme.TextSecondary
import ai.soumya.assistant.ui.theme.Violet500
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onDone: (Boolean) -> Unit, characterChosen: Boolean) {
    LaunchedEffect(Unit) {
        delay(1600)
        onDone(characterChosen)
    }
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF070B1A)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.soumya_logo),
            contentDescription = "SOUMYA AI logo",
            modifier = Modifier.size(180.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(20.dp))
        Text("SOUMYA AI ASSISTANT", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Your Personal AI. Your World.", fontSize = 14.sp, color = TextSecondary)
        Spacer(Modifier.height(28.dp))
        CircularProgressIndicator(color = Cyan400)
    }
}

@Composable
fun CharacterSelectScreen(prefs: Prefs, onSelected: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF070B1A))
            .padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Choose your Soumya", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            "Pick who you want to talk to. You can switch anytime in Settings.",
            fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(28.dp))
        CharacterCard(
            name = "Soumya — Male",
            desc = "Friendly, intelligent, caring and professional. Male voice.",
            drawable = R.drawable.soumya_male,
            onClick = { prefs.character = Prefs.Character.MALE; prefs.characterChosen = true; onSelected() }
        )
        Spacer(Modifier.height(16.dp))
        CharacterCard(
            name = "Soumya — Female",
            desc = "Caring, intelligent, funny and emotionally aware. Female voice.",
            drawable = R.drawable.soumya_female,
            onClick = { prefs.character = Prefs.Character.FEMALE; prefs.characterChosen = true; onSelected() }
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "v1 characters are high-quality portraits with Compose animation (blink, breathing, speaking). " +
                "True real-time 3D humans with lip-sync are a v2 goal — see About.",
            fontSize = 12.sp, color = TextSecondary
        )
    }
}

@Composable
fun CharacterCard(name: String, desc: String, drawable: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(drawable),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(180.dp).clip(CircleShape)
            )
            Spacer(Modifier.height(12.dp))
            Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(desc, fontSize = 13.sp, color = TextSecondary)
        }
    }
}

@Composable
fun HomeScreen(
    prefs: Prefs,
    voiceState: VoiceState,
    hasGeminiKey: Boolean,
    recentMemories: List<MemoryEntity>,
    onMic: () -> Unit,
    onOpenChat: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onQuickAction: (String) -> Unit,
    quickResult: String
) {
    var input by remember { mutableStateOf("") }
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF070B1A))
            .padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("SOUMYA AI", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            IconButton(onClick = { onNavigate("settings") }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
        Spacer(Modifier.height(8.dp))
        CharacterView(
            character = prefs.character,
            state = voiceState,
            mood = "$greeting! How can I help you today?"
        )
        Spacer(Modifier.height(12.dp))
        StatusChip(
            if (hasGeminiKey) "Gemini connected" else "Gemini key needed — on-device mode",
            hasGeminiKey
        )
        if (quickResult.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(quickResult, fontSize = 13.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Type a message…") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onMic,
                modifier = Modifier.size(52.dp).background(Violet500, CircleShape)
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "Speak", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { if (input.isNotBlank()) { onOpenChat(input); input = "" } },
                modifier = Modifier.size(52.dp).background(Cyan400, CircleShape)
            ) {
                Icon(Icons.Filled.Chat, contentDescription = "Send", tint = Color(0xFF070B1A))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Quick actions", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        QuickActionRow(
            listOf(
                Triple("Open YouTube", Icons.Filled.PlayArrow) { onQuickAction("YouTube") },
                Triple("Battery status", Icons.Filled.Notifications) { onQuickAction("__battery__") },
                Triple("Chat with Soumya", Icons.Filled.Chat) { onOpenChat("") },
                Triple("Memory Center", Icons.Filled.Settings) { onNavigate("memory") },
                Triple("Automation Center", Icons.Filled.PlayArrow) { onNavigate("automation") },
                Triple("Permissions", Icons.Filled.Settings) { onNavigate("permissions") }
            )
        )
        Spacer(Modifier.height(16.dp))
        Text("Recent memories", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        if (recentMemories.isEmpty()) {
            Text("Nothing saved yet. Say \"remember that…\" and I will keep it here.", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.fillMaxWidth())
        } else {
            recentMemories.take(3).forEach { m ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
                        Text(m.content, fontSize = 13.sp, color = TextSecondary)
                    }
                }
            }
            Button(
                onClick = { onNavigate("memory") },
                colors = ButtonDefaults.buttonColors(containerColor = Violet500)
            ) { Text("Open Memory Center") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun QuickActionRow(actions: List<Triple<String, ImageVector, () -> Unit>>) {
    Column {
        actions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, icon, onClick) ->
                    Card(
                        modifier = Modifier.weight(1f).clickable(onClick = onClick),
                        colors = CardDefaults.cardColors(containerColor = CardBg)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, contentDescription = null, tint = Cyan400)
                            Spacer(Modifier.width(8.dp))
                            Text(label, fontSize = 13.sp, color = Color.White)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
