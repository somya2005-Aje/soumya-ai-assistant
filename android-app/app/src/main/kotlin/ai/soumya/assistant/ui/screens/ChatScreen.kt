package ai.soumya.assistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ai.soumya.assistant.ai.GeminiClient
import ai.soumya.assistant.ai.LocalCommands
import ai.soumya.assistant.ai.ToolExecutor
import ai.soumya.assistant.data.MemoryRepository
import ai.soumya.assistant.data.Prefs
import ai.soumya.assistant.speech.SpeechManager
import ai.soumya.assistant.speech.VoiceState
import ai.soumya.assistant.ui.theme.CardBg
import ai.soumya.assistant.ui.theme.Cyan400
import ai.soumya.assistant.ui.theme.TextSecondary
import ai.soumya.assistant.ui.theme.Violet500
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ChatMessage(val role: String, val text: String, val isError: Boolean = false)

const val GEMINI_SETUP_GUIDE = "Abhi main on-device mode me hoon — bina key ke bhi kaam kar sakta hoon.\n\n" +
    "Full AI chat ke liye:\n" +
    "1. Browser me Google AI Studio kholo (aistudio.google.com).\n" +
    "2. Free API key banao.\n" +
    "3. Settings → Gemini API key me paste karo.\n\n" +
    "Tab tak try karo: \"YouTube kholo\", \"battery status\", ya \"remember that I prefer Hindi\"."

class ChatEngine(
    private val prefs: Prefs,
    private val memoryRepo: MemoryRepository,
    private val executor: ToolExecutor,
    private val local: LocalCommands,
    private val speech: SpeechManager
) {
    val messages = mutableStateListOf<ChatMessage>()
    var busy by mutableStateOf(false)

    private fun systemPrompt(memories: List<String>): String {
        val lang = when (prefs.language) {
            "hi" -> "Reply in Hindi (Devanagari is fine)."
            "en" -> "Reply in English."
            else -> "Reply in natural Hinglish (Hindi + English mix), the way people speak in India."
        }
        val character = if (prefs.character == Prefs.Character.MALE) "Male Soumya" else "Female Soumya"
        return "You are $character, a friendly personal AI assistant called SOUMYA AI. " +
            "Be warm, helpful and concise. $lang " +
            "Use the provided tools for device actions and verify results before claiming success. " +
            "Never claim an action succeeded unless the tool reported success. " +
            "Never ask for or store passwords, OTPs or secrets. " +
            "Saved memories:\n" + memories.joinToString("\n").ifBlank { "(none)" }
    }

    suspend fun send(userText: String, onState: (VoiceState) -> Unit) {
        val text = userText.trim()
        if (text.isBlank() || busy) return
        busy = true
        messages.add(ChatMessage("user", text))
        onState(VoiceState.THINKING)

        val localReply = withContext(Dispatchers.IO) { local.handle(text) }
        if (localReply != null) {
            reply(localReply, onState)
            busy = false
            return
        }

        // Key resolution: user's own Settings key first, then a baked-in
        // build-time key (normally empty — never hardcoded).
        val key = prefs.effectiveGeminiKey()
        if (key == null) {
            reply(GEMINI_SETUP_GUIDE, onState)
            busy = false
            return
        }

        val result = withContext(Dispatchers.IO) {
            runCatching {
                val memories = memoryRepo.all().take(5).map { "${it.title}: ${it.content}" }
                val client = GeminiClient(key)
                val history = messages.dropLast(1).takeLast(10)
                    .map { it.role to it.text }
                val first = client.generate(systemPrompt(memories), history, text)
                    .getOrElse { throw it }
                val call = first.functionCall
                if (call == null) {
                    first.text.ifBlank { "I could not think of a reply. Please try again." }
                } else {
                    val outcome = executor.execute(call.name, call.args)
                    val toolJson = JSONObject()
                        .put("success", outcome.success)
                        .put("result", outcome.message)
                    val second = client.generate(
                        systemPrompt(memories), history, text,
                        functionResponse = toolJson, functionName = call.name
                    ).getOrElse { throw it }
                    val modelText = second.text.ifBlank { outcome.message }
                    if (outcome.success) modelText else "$modelText\n\n(Tool note: ${outcome.message})"
                }
            }
        }
        result.onSuccess { reply(it, onState) }
            .onFailure {
                // Friendly Hindi only — GeminiClient never returns raw codes or
                // exception text. On-device commands keep working regardless.
                val friendly = it.message
                    ?: "AI se jawab nahi mila, phir try karo."
                reply(
                    "$friendly\n\nTab tak on-device kaam chal rahe hain — \"YouTube kholo\" ya \"battery status\" bolo.",
                    onState,
                    isError = true
                )
            }
        busy = false
    }

    private fun reply(text: String, onState: (VoiceState) -> Unit, isError: Boolean = false) {
        messages.add(ChatMessage("model", text, isError))
        speech.speak(text)
        onState(VoiceState.IDLE)
    }
}

@Composable
fun ChatScreen(
    prefs: Prefs,
    engine: ChatEngine,
    speech: SpeechManager,
    voiceState: VoiceState,
    onVoiceState: (VoiceState) -> Unit,
    initialDraft: String,
    onDraftConsumed: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(initialDraft) }
    val listState = rememberLazyListState()

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) speech.startListening() else onVoiceState(VoiceState.ERROR)
    }

    LaunchedEffect(engine.messages.size) {
        if (engine.messages.isNotEmpty()) listState.animateScrollToItem(engine.messages.size - 1)
    }
    LaunchedEffect(Unit) {
        onDraftConsumed()
        speech.onSpeechResult = { heard ->
            input = heard
            scope.launch { engine.send(heard, onVoiceState) }
        }
        speech.onSpeechError = { onVoiceState(VoiceState.ERROR) }
        speech.onStateChange = onVoiceState
    }

    fun sendNow() {
        val text = input.trim()
        if (text.isEmpty()) return
        input = ""
        scope.launch { engine.send(text, onVoiceState) }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF070B1A))) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Chat with Soumya", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                if (prefs.speakReplies) { prefs.speakReplies = false; speech.stopSpeaking() }
                else prefs.speakReplies = true
            }) {
                Icon(
                    if (prefs.speakReplies) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = "Toggle voice replies",
                    tint = Color.White
                )
            }
        }
        if (engine.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Say hello, or try:\n\"open YouTube\"\n\"battery status\"\n\"remember that I prefer Hindi\"",
                    color = TextSecondary, fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(engine.messages) { msg ->
                    val isUser = msg.role == "user"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUser) Violet500 else CardBg
                            ),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Text(
                                msg.text,
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Message Soumya…") },
                modifier = Modifier.weight(1f),
                singleLine = false,
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                        speech.startListening()
                        onVoiceState(VoiceState.LISTENING)
                    } else {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.size(48.dp).background(Violet500, CircleShape)
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "Voice input", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = ::sendNow,
                modifier = Modifier.size(48.dp).background(Cyan400, CircleShape)
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color(0xFF070B1A))
            }
        }
        if (voiceState == VoiceState.LISTENING) {
            Text("Listening… tap the mic again to stop", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        }
        Spacer(Modifier.height(4.dp))
    }
}
