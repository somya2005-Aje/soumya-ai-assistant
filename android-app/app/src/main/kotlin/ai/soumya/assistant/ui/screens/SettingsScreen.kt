package ai.soumya.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.soumya.assistant.data.Prefs
import ai.soumya.assistant.service.AssistantService
import ai.soumya.assistant.ui.theme.CardBg
import ai.soumya.assistant.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    prefs: Prefs,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    val context = LocalContext.current
    var character by remember { mutableStateOf(prefs.character) }
    var language by remember { mutableStateOf(prefs.language) }
    var rate by remember { mutableStateOf(prefs.speechRate) }
    var pitch by remember { mutableStateOf(prefs.speechPitch) }
    var wakeWord by remember { mutableStateOf(prefs.wakeWordEnabled) }
    var backgroundMode by remember { mutableStateOf(prefs.backgroundModeEnabled) }
    var speakReplies by remember { mutableStateOf(prefs.speakReplies) }
    var apiKey by remember { mutableStateOf(prefs.geminiApiKey ?: "") }
    var keyVisible by remember { mutableStateOf(false) }
    var keySaved by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF070B1A)).padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Section("Character")
        RadioRow("Male Soumya", character == Prefs.Character.MALE) {
            character = Prefs.Character.MALE; prefs.character = Prefs.Character.MALE
        }
        RadioRow("Female Soumya", character == Prefs.Character.FEMALE) {
            character = Prefs.Character.FEMALE; prefs.character = Prefs.Character.FEMALE
        }

        Section("Language")
        RadioRow("Hinglish (Hindi + English)", language == "hinglish") { language = "hinglish"; prefs.language = "hinglish" }
        RadioRow("Hindi", language == "hi") { language = "hi"; prefs.language = "hi" }
        RadioRow("English", language == "en") { language = "en"; prefs.language = "en" }

        Section("Voice")
        Text("Speed: ${"%.2f".format(rate)}x", color = Color.White, fontSize = 14.sp)
        Slider(value = rate, onValueChange = { rate = it; prefs.speechRate = it }, valueRange = 0.5f..2.0f)
        Text("Pitch: ${"%.2f".format(pitch)}", color = Color.White, fontSize = 14.sp)
        Slider(value = pitch, onValueChange = { pitch = it; prefs.speechPitch = it }, valueRange = 0.5f..2.0f)
        SwitchRow("Speak replies aloud", speakReplies) { speakReplies = it; prefs.speakReplies = it }

        Section("Background assistant & wake word")
        SwitchRow("Background assistant", backgroundMode) { enabled ->
            backgroundMode = enabled
            prefs.backgroundModeEnabled = enabled
            if (enabled) AssistantService.start(context) else AssistantService.stop(context)
        }
        SwitchRow("Wake word — \"Hey Soumya\"", wakeWord) { enabled ->
            wakeWord = enabled
            prefs.wakeWordEnabled = enabled
            if (enabled && !backgroundMode) {
                backgroundMode = true
                prefs.backgroundModeEnabled = true
                AssistantService.start(context)
            }
        }
        Text(
            "Wake word uses on-device speech recognition while the background service runs. " +
                "Continuous always-on hotword on the lock screen is platform/manufacturer-limited — " +
                "on some phones it only works while the screen is on. The service shows a persistent " +
                "notification with Pause / Resume / Disable.",
            fontSize = 12.sp, color = TextSecondary
        )

        Section("Gemini API key")
        Text(
            "Your free key from Google AI Studio (aistudio.google.com). Stored encrypted on this device only — never uploaded, never in the app code.",
            fontSize = 12.sp, color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; keySaved = false },
            label = { Text("Gemini API key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation()
        )
        Row {
            Button(onClick = {
                prefs.geminiApiKey = apiKey
                keySaved = true
            }) { Text("Save key") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { apiKey = ""; prefs.geminiApiKey = null; keySaved = false }) {
                Text("Clear key")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { keyVisible = !keyVisible }) {
                Text(if (keyVisible) "Hide" else "Show")
            }
        }
        if (keySaved) Text("Key saved securely.", color = Color(0xFF38E6B5), fontSize = 13.sp)

        Section("Privacy & permissions")
        Text(
            "Memories never include passwords or OTPs. Conversations stay on this device. " +
                "Notification reading and automation need your explicit grant in system settings.",
            fontSize = 13.sp, color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenPermissions, modifier = Modifier.fillMaxWidth()) { Text("Open Permissions dashboard") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) { Text("About & feature status") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Spacer(Modifier.height(6.dp))
}

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val statuses = listOf(
        "AI chat (Gemini, with function calling)" to "Implemented — needs your free API key in Settings",
        "Voice chat (STT + TTS, Hindi/English/Hinglish)" to "Implemented",
        "Male / Female Soumya characters" to "Implemented — animated portraits (v1)",
        "Real-time 3D human + full lip-sync" to "Pending (v2) — needs a Unity project; NOT in v1",
        "Long-term memory (Room)" to "Implemented — secrets are never saved",
        "Open apps / settings / battery / device info" to "Implemented",
        "Reminders (exact alarms + WorkManager)" to "Implemented — permission-dependent",
        "Background assistant (foreground service)" to "Implemented — Pause/Resume/Disable",
        "Wake word \"Hey Soumya\"" to "Implemented (recognition loop) — platform-restricted on lock screen",
        "Notification reading & summary" to "Permission-dependent — grant Notification Access",
        "Accessibility automation" to "Permission-dependent — optional, disabled by default",
        "File browse / create folder / rename" to "Implemented via system picker + app storage",
        "Permissions dashboard" to "Implemented"
    )
    Column(
        Modifier.fillMaxSize().background(Color(0xFF070B1A)).padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("About SOUMYA AI", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Text("SOUMYA AI Assistant v1.0.0 — \"Your Personal AI. Your World.\"", fontSize = 14.sp, color = Color.White)
        Spacer(Modifier.height(12.dp))
        Text(
            "Honest limits in v1:\n\n" +
                "• True Unity/Unreal real-time 3D human models with full lip-sync are NOT in v1. " +
                "That needs a separate Unity project and heavy GPU work. v1 characters are high-quality " +
                "generated portraits animated with Compose (breathing, speaking overlay, expression states).\n\n" +
                "• Always-on lock-screen wake word is platform-limited. Android gives third-party apps no " +
                "system hotword API, and manufacturers restrict background microphone use. The wake word " +
                "works while the background service runs; on some phones only with the screen on.\n\n" +
                "• An ordinary app cannot silently control every setting or third-party app — actions go " +
                "through official Android APIs and intents, and the app reports verified success or failure.",
            fontSize = 14.sp, color = TextSecondary
        )
        Spacer(Modifier.height(16.dp))
        Text("Feature status", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        statuses.forEach { (feature, status) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(feature, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
                    Text(status, fontSize = 13.sp, color = TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
