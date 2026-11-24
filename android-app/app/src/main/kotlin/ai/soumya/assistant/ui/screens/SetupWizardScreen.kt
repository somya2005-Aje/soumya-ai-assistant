package ai.soumya.assistant.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.soumya.assistant.ai.GeminiClient
import ai.soumya.assistant.ai.GroqClient
import ai.soumya.assistant.ai.PermissionGate
import ai.soumya.assistant.data.Prefs
import ai.soumya.assistant.ui.theme.CardBg
import ai.soumya.assistant.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * First-run Setup Wizard (v1.2.0, LIA edition) — the same steps the LIA
 * tutorial walks through, plus Auto-Send: Play Protect, Restricted Settings,
 * Accessibility, Overlay + Background, API keys (Gemini + Groq, with the
 * Error 400 fix), Auto-Send Permission (one-time, default OFF),
 * Personality / Voice / Model.
 *
 * Shown after Splash on first run (while !prefs.setupDone) and reopenable
 * from Home ("LIA Setup") and Settings. Skipping is always allowed —
 * nothing here is forced; each step can be finished later from Settings
 * or the Permissions dashboard.
 */
@Composable
fun SetupWizardScreen(prefs: Prefs, onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(0) }
    var refreshTick by remember { mutableStateOf(0) }
    @Suppress("UNUSED_VARIABLE")
    val tick = refreshTick // re-reads live permission state on return from Settings

    // Returning from a system settings screen re-checks the live state.
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshTick++ }

    fun openSettings(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        settingsLauncher.launch(intent)
    }

    // ---- Step 5 (API keys) state -----------------------------------------
    var geminiKey by remember { mutableStateOf(prefs.geminiApiKey ?: "") }
    var geminiVisible by remember { mutableStateOf(false) }
    var geminiNote by remember { mutableStateOf("") }
    var geminiNoteOk by remember { mutableStateOf(true) }
    var geminiChecking by remember { mutableStateOf(false) }
    var groqKey by remember { mutableStateOf(prefs.groqApiKey ?: "") }
    var groqVisible by remember { mutableStateOf(false) }
    var groqNote by remember { mutableStateOf("") }
    var groqNoteOk by remember { mutableStateOf(true) }
    var groqChecking by remember { mutableStateOf(false) }

    fun testGeminiKey(key: String) {
        val cleaned = Prefs.cleanApiKey(key)
        if (cleaned.isBlank()) {
            geminiNoteOk = false
            geminiNote = "Pehle key paste karo."
            return
        }
        prefs.geminiApiKey = cleaned
        geminiKey = cleaned
        geminiChecking = true
        geminiNote = ""
        scope.launch(Dispatchers.IO) {
            val result = GeminiClient(cleaned, prefs.geminiModel).testConnection()
            withContext(Dispatchers.Main) {
                geminiChecking = false
                result.onSuccess { model ->
                    prefs.keyVerified = true
                    geminiNoteOk = true
                    geminiNote = "Connected ✓ (model: $model)"
                }.onFailure {
                    val failure = it as? GeminiClient.GeminiFailure
                    if (failure?.kind == GeminiClient.FailureKind.INVALID_KEY ||
                        failure?.kind == GeminiClient.FailureKind.PERMISSION) {
                        prefs.keyVerified = false
                    }
                    geminiNoteOk = false
                    geminiNote = it.message ?: "Key check nahi ho payi, phir try karo."
                }
            }
        }
    }

    fun testGroqKey(key: String) {
        val cleaned = Prefs.cleanApiKey(key)
        if (cleaned.isBlank()) {
            groqNoteOk = false
            groqNote = "Pehle key paste karo."
            return
        }
        prefs.groqApiKey = cleaned
        groqKey = cleaned
        groqChecking = true
        groqNote = ""
        scope.launch(Dispatchers.IO) {
            val result = GroqClient(cleaned, prefs.groqModel).testConnection()
            withContext(Dispatchers.Main) {
                groqChecking = false
                result.onSuccess { model ->
                    prefs.groqKeyVerified = true
                    groqNoteOk = true
                    groqNote = "Connected ✓ (model: $model)"
                }.onFailure {
                    val failure = it as? GroqClient.GroqFailure
                    if (failure?.kind == GeminiClient.FailureKind.INVALID_KEY ||
                        failure?.kind == GeminiClient.FailureKind.PERMISSION) {
                        prefs.groqKeyVerified = false
                    }
                    groqNoteOk = false
                    groqNote = it.message ?: "Key check nahi ho payi, phir try karo."
                }
            }
        }
    }

    // ---- Step 6 (auto-send) state ----------------------------------------
    var autoWa by remember { mutableStateOf(prefs.autoSendWhatsApp) }
    var autoFb by remember { mutableStateOf(prefs.autoSendFacebook) }

    // ---- Step 7 state ------------------------------------------------------
    var personality by remember { mutableStateOf(prefs.personality) }
    var provider by remember { mutableStateOf(prefs.aiProvider) }
    var geminiModel by remember { mutableStateOf(prefs.geminiModel) }
    var groqModel by remember { mutableStateOf(prefs.groqModel) }
    var rate by remember { mutableStateOf(prefs.speechRate) }
    var pitch by remember { mutableStateOf(prefs.speechPitch) }

    val accessibilityOn = PermissionGate.isGranted(context, PermissionGate.ACCESSIBILITY)
    val overlayOn = PermissionGate.isGranted(context, PermissionGate.OVERLAY)
    val backgroundOn = PermissionGate.isGranted(context, PermissionGate.BACKGROUND)

    Column(
        Modifier.fillMaxSize().background(Color(0xFF070B1A)).padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) {
                IconButton(onClick = { step-- }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
            Text(
                "Setup Wizard — Step ${step + 1} / 7",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White
            )
        }
        Spacer(Modifier.height(8.dp))

        when (step) {
            0 -> {
                Text("1. Play Protect", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    "SOUMYA AI Play Store se install nahi hua, isliye Play Protect pehli baar " +
                        "install karte waqt warning dikha sakta hai. Ye normal hai — app me koi " +
                        "virus nahi hai, bas ye Google ke store ke bahar se aayi hai.\n\n" +
                        "• Play Store kholo → profile icon → Play Protect.\n" +
                        "• Agar warning aaye to \"Install anyway\" / \"Still install\" dabao.\n" +
                        "• \"Harmful app blocked\" dikhe to Play Protect settings me " +
                        "\"Scan apps with Play Protect\" ko install ke liye band karke phir try karo.\n\n" +
                        "App ek baar install ho jaye to Play Protect dobara pareshan nahi karta.",
                    fontSize = 14.sp, color = TextSecondary
                )
            }
            1 -> {
                Text("2. Restricted Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android 13/14 me sideload ki gayi apps ki kuch settings band rehti hain " +
                        "(\"Restricted setting\" likha aata hai). Accessibility on karne se pehle " +
                        "ye allow karna zaroori hai, warna Accessibility ka toggle grey dikhega.\n\n" +
                        "• Neeche wala button dabao — app ki Settings khulegi.\n" +
                        "• Upar right corner me 3-dot menu (⋮) dabao.\n" +
                        "• \"Allow restricted settings\" chuno aur confirm karo.\n\n" +
                        "Ye ek baar karna hai — sirf is app ke liye, poore phone ke liye nahi.",
                    fontSize = 14.sp, color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        openSettings(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.parse("package:${context.packageName}"))
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("App Settings kholo") }
            }
            2 -> {
                Text("3. Accessibility", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Accessibility service se assistant app ke andar automation kar pata hai " +
                        "(jaise screen dekhna, tap/scroll me madad). Ye OPTIONAL hai aur default " +
                        "off rehta hai — bina iske bhi chat, voice aur saare tools chalte hain.\n\n" +
                        "Kabhi bhi passwords, OTP ya payment screens par assistant kuch nahi karega.",
                    fontSize = 14.sp, color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                StatusLine("Accessibility", accessibilityOn)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (accessibilityOn) "Accessibility Settings kholo" else "Accessibility on karo") }
                if (!accessibilityOn) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Settings me \"SOUMYA AI Assistant\" dhoondh ke on karo. " +
                            "Agar toggle grey hai to pehle Step 2 (Restricted Settings) poora karo.",
                        fontSize = 13.sp, color = TextSecondary
                    )
                }
            }
            3 -> {
                Text("4. Overlay + Background", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    "• Overlay (\"Draw over other apps\"): dusri apps ke upar floating assistant " +
                        "dikhane ke liye.\n" +
                        "• Background (battery optimization off): assistant background me band " +
                        "na ho, wake word aur reminders time par chalein.",
                    fontSize = 14.sp, color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                StatusLine("Overlay (dusri apps ke upar dikhao)", overlayOn)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        openSettings(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                .setData(Uri.parse("package:${context.packageName}"))
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (overlayOn) "Overlay Settings kholo" else "Overlay allow karo") }
                Spacer(Modifier.height(16.dp))
                StatusLine("Background (battery optimization off)", backgroundOn)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        openSettings(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                .setData(Uri.parse("package:${context.packageName}"))
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (backgroundOn) "Battery Settings kholo" else "Background allow karo") }
            }
            4 -> {
                Text("5. API Keys", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    "AI chat ke liye ek free key chahiye — Gemini ya Groq, koi ek kaafi hai. " +
                        "Dono daal do to Auto mode pehle Gemini try karega, phir Groq. " +
                        "Keys sirf is phone par encrypted save hoti hain, kahin upload nahi hoti.",
                    fontSize = 14.sp, color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                Text("Gemini key (Google AI Studio — aistudio.google.com)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it; geminiNote = "" },
                    label = { Text("Gemini API key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (geminiVisible) VisualTransformation.None else PasswordVisualTransformation()
                )
                Row(Modifier.fillMaxWidth()) {
                    Button(enabled = !geminiChecking, onClick = { testGeminiKey(geminiKey) }) {
                        Text(if (geminiChecking) "Checking…" else "Save + Test")
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { geminiVisible = !geminiVisible }) {
                        Text(if (geminiVisible) "Hide" else "Show")
                    }
                }
                if (geminiNote.isNotBlank()) Text(
                    geminiNote,
                    color = if (geminiNoteOk) Color(0xFF38E6B5) else Color(0xFFFFB020),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
                Text("Groq key (console.groq.com — free, koi card nahi chahiye)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                OutlinedTextField(
                    value = groqKey,
                    onValueChange = { groqKey = it; groqNote = "" },
                    label = { Text("Groq API key (gsk_…)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (groqVisible) VisualTransformation.None else PasswordVisualTransformation()
                )
                Row(Modifier.fillMaxWidth()) {
                    Button(enabled = !groqChecking, onClick = { testGroqKey(groqKey) }) {
                        Text(if (groqChecking) "Checking…" else "Save + Test")
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { groqVisible = !groqVisible }) {
                        Text(if (groqVisible) "Hide" else "Show")
                    }
                }
                if (groqNote.isNotBlank()) Text(
                    groqNote,
                    color = if (groqNoteOk) Color(0xFF38E6B5) else Color(0xFFFFB020),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
                Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Error 400 aaye to kya karein?", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Text(
                            "• Key ko AI Studio / Groq console se DOBARA copy karo — poori key, bina space ke, bina quotes ke.\n" +
                                "• Key paste karte waqt aage-peeche space na ho — app khud saaf kar deta hai, phir bhi check karo.\n" +
                                "• Gemini me AI Studio par \"Create API key\" se nayi UNRESTRICTED key banao (koi app restriction na lagao).\n" +
                                "• Groq free tier par hai — naya account banao to key turant milti hai, card nahi lagta.\n" +
                                "• Ek key fail ho to dusri try karo — Auto mode khud switch kar leta hai.",
                            fontSize = 13.sp, color = TextSecondary
                        )
                    }
                }
            }
            5 -> {
                Text("6. Auto-Send Permission (ek baar)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    "⚠️ ON karne par tumhari awaaz par WhatsApp message / Facebook post " +
                        "tumhare bina tap kiye bhej diya jayega. Sirf tab ON karo jab tum ye chahte ho.\n\n" +
                        "• Auto-send sirf TUMHARE khud ke account se, sirf TUMHARE bole hue message par, " +
                        "aur sirf jab ye toggle ON ho — tabhi kaam karta hai.\n" +
                        "• Iske liye Accessibility service chahiye (Step 3) — wahi service Send/Post button dabati hai.\n" +
                        "• OFF hai to kuch nahi badlega: composer khulega aur tum khud Send/Post dabaoge.\n" +
                        "• Kabhi bhi ek tap me band kar sakte ho — Settings, Automation Center ya Home se.",
                    fontSize = 14.sp, color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                StatusLine("Accessibility service", accessibilityOn)
                if (!accessibilityOn) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Auto-Send ke liye pehle Accessibility on karo (Step 3 par wapas jao, ya neeche button dabao).",
                        fontSize = 13.sp, color = Color(0xFFFFB020)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Accessibility on karo") }
                }
                Spacer(Modifier.height(12.dp))
                SwitchRow("WhatsApp Auto-Send", autoWa) { enabled ->
                    autoWa = enabled; prefs.autoSendWhatsApp = enabled
                }
                Text(
                    "ON: \"Ravi ko WhatsApp karo …\" bolne par message khud bhej diya jayega, dobara permission nahi maangi jayegi.",
                    fontSize = 12.sp, color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow("Facebook Auto-Post", autoFb) { enabled ->
                    autoFb = enabled; prefs.autoSendFacebook = enabled
                }
                Text(
                    "ON: \"Facebook par post karo …\" bolne par post khud publish ho jayega, dobara permission nahi maangi jayegi.",
                    fontSize = 12.sp, color = TextSecondary
                )
            }
            6 -> {
                Text("7. Personality, Voice & Model", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Section("Personality")
                RadioRow("JARVIS style (default)", personality == "jarvis") { personality = "jarvis"; prefs.personality = "jarvis" }
                RadioRow("Helpful — seedha, clear", personality == "helpful") { personality = "helpful"; prefs.personality = "helpful" }
                RadioRow("Funny — halka-phulka, jokes", personality == "funny") { personality = "funny"; prefs.personality = "funny" }
                RadioRow("Formal — professional", personality == "formal") { personality = "formal"; prefs.personality = "formal" }
                Section("AI Provider")
                RadioRow("Auto — Gemini pehle, phir Groq (default)", provider == "auto") { provider = "auto"; prefs.aiProvider = "auto" }
                RadioRow("Gemini only", provider == "gemini") { provider = "gemini"; prefs.aiProvider = "gemini" }
                RadioRow("Groq only", provider == "groq") { provider = "groq"; prefs.aiProvider = "groq" }
                Section("Gemini model")
                GeminiClient.MODEL_PREFERENCE.forEach { model ->
                    RadioRow(model, geminiModel == model) { geminiModel = model; prefs.geminiModel = model }
                }
                Section("Groq model")
                GroqClient.MODELS.forEach { model ->
                    RadioRow(model, groqModel == model) { groqModel = model; prefs.groqModel = model }
                }
                Section("Voice")
                Text("Speed: ${"%.2f".format(rate)}x", color = Color.White, fontSize = 14.sp)
                Slider(value = rate, onValueChange = { rate = it; prefs.speechRate = it }, valueRange = 0.5f..2.0f)
                Text("Pitch: ${"%.2f".format(pitch)}", color = Color.White, fontSize = 14.sp)
                Slider(value = pitch, onValueChange = { pitch = it; prefs.speechPitch = it }, valueRange = 0.5f..2.0f)
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { prefs.setupDone = true; onFinish() }) { Text("Skip") }
            Spacer(Modifier.weight(1f))
            if (step < 6) {
                Button(onClick = { step++ }) { Text("Next") }
            } else {
                Button(onClick = { prefs.setupDone = true; onFinish() }) { Text("Finish — Shuru karo") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusLine(label: String, granted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            if (granted) "ON ✓" else "OFF",
            color = if (granted) Color(0xFF38E6B5) else Color(0xFFFFB020),
            fontSize = 14.sp, fontWeight = FontWeight.Bold
        )
    }
}
