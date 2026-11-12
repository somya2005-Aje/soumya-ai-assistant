package ai.soumya.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ai.soumya.assistant.ai.LocalCommands
import ai.soumya.assistant.ai.ToolExecutor
import ai.soumya.assistant.data.MemoryRepository
import ai.soumya.assistant.data.Prefs
import ai.soumya.assistant.speech.SpeechManager
import ai.soumya.assistant.speech.VoiceState
import ai.soumya.assistant.ui.screens.AboutScreen
import ai.soumya.assistant.ui.screens.AutomationScreen
import ai.soumya.assistant.ui.screens.CharacterSelectScreen
import ai.soumya.assistant.ui.screens.ChatEngine
import ai.soumya.assistant.ui.screens.ChatScreen
import ai.soumya.assistant.ui.screens.HomeScreen
import ai.soumya.assistant.ui.screens.MemoryScreen
import ai.soumya.assistant.ui.screens.PermissionsScreen
import ai.soumya.assistant.ui.screens.SettingsScreen
import ai.soumya.assistant.ui.screens.SplashScreen
import ai.soumya.assistant.ui.theme.SoumyaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var memoryRepo: MemoryRepository
    private lateinit var speech: SpeechManager
    private lateinit var executor: ToolExecutor
    private lateinit var local: LocalCommands

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        memoryRepo = MemoryRepository(this)
        speech = SpeechManager(this, prefs)
        executor = ToolExecutor(this, memoryRepo)
        local = LocalCommands(this, executor, memoryRepo)
        speech.initTts()

        setContent {
            SoumyaTheme {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                var voiceState by remember { mutableStateOf(VoiceState.IDLE) }
                var chatDraft by remember { mutableStateOf("") }
                var quickResult by remember { mutableStateOf("") }
                var recentMemories by remember { mutableStateOf(emptyList<ai.soumya.assistant.data.MemoryEntity>()) }
                var hasKey by remember { mutableStateOf(prefs.hasGeminiKey()) }

                val engine = remember {
                    ChatEngine(prefs, memoryRepo, executor, local, speech)
                }

                fun refreshHome() {
                    scope.launch {
                        recentMemories = withContext(Dispatchers.IO) { memoryRepo.all().take(3) }
                        hasKey = prefs.hasGeminiKey()
                    }
                }

                /**
                 * Home talk flow: tap the big mic → listen → send to Gemini /
                 * on-device commands → speak the reply aloud, all on Home.
                 * Tap again while listening/speaking to stop.
                 */
                fun beginHomeListening() {
                    voiceState = VoiceState.LISTENING
                    speech.onSpeechResult = { heard ->
                        scope.launch { engine.send(heard) { voiceState = it } }
                    }
                    speech.onSpeechError = { friendly ->
                        voiceState = VoiceState.IDLE
                        quickResult = friendly
                    }
                    speech.onStateChange = { voiceState = it }
                    speech.startListening()
                }

                val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        beginHomeListening()
                    } else {
                        voiceState = VoiceState.IDLE
                        quickResult = "Baat karne ke liye mic permission chahiye — Settings me allow karo."
                    }
                }

                fun startVoiceInput() {
                    if (speech.isListening()) {
                        speech.stopListening()
                        voiceState = VoiceState.IDLE
                        return
                    }
                    if (voiceState == VoiceState.SPEAKING) {
                        speech.stopSpeaking()
                        voiceState = VoiceState.IDLE
                        return
                    }
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                        beginHomeListening()
                    } else {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                NavHost(navController = navController, startDestination = "splash") {
                    composable("splash") {
                        SplashScreen(
                            characterChosen = prefs.characterChosen,
                            onDone = { chosen ->
                                navController.navigate(if (chosen) "home" else "select") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("select") {
                        CharacterSelectScreen(prefs) {
                            navController.navigate("home") {
                                popUpTo("select") { inclusive = true }
                            }
                        }
                    }
                    composable("home") {
                        androidx.compose.runtime.LaunchedEffect(Unit) { refreshHome() }
                        HomeScreen(
                            prefs = prefs,
                            voiceState = voiceState,
                            hasGeminiKey = hasKey,
                            recentMemories = recentMemories,
                            engine = engine,
                            onMic = ::startVoiceInput,
                            onOpenChat = { text ->
                                chatDraft = text
                                navController.navigate("chat")
                            },
                            onNavigate = { navController.navigate(it) },
                            onQuickAction = { action ->
                                scope.launch(Dispatchers.IO) {
                                    val message = when (action) {
                                        "__battery__" -> executor.batteryStatus().message
                                        else -> executor.openApp(action).message
                                    }
                                    withContext(Dispatchers.Main) { quickResult = message }
                                }
                            },
                            quickResult = quickResult
                        )
                    }
                    composable("chat") {
                        ChatScreen(
                            prefs = prefs,
                            engine = engine,
                            speech = speech,
                            voiceState = voiceState,
                            onVoiceState = { voiceState = it },
                            initialDraft = chatDraft,
                            onDraftConsumed = { chatDraft = "" },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("memory") {
                        MemoryScreen(repo = memoryRepo, onBack = { navController.popBackStack() })
                    }
                    composable("automation") {
                        AutomationScreen(executor = executor, onBack = { navController.popBackStack() })
                    }
                    composable("permissions") {
                        PermissionsScreen(onBack = { navController.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(
                            prefs = prefs,
                            onBack = { navController.popBackStack() },
                            onOpenAbout = { navController.navigate("about") },
                            onOpenPermissions = { navController.navigate("permissions") }
                        )
                    }
                    composable("about") {
                        AboutScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        speech.shutdown()
        super.onDestroy()
    }
}
