package ai.soumya.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ai.soumya.assistant.MainActivity
import ai.soumya.assistant.R
import ai.soumya.assistant.ai.LocalCommands
import ai.soumya.assistant.ai.PermissionGate
import ai.soumya.assistant.ai.ToolExecutor
import ai.soumya.assistant.data.MemoryRepository
import ai.soumya.assistant.data.Prefs
import ai.soumya.assistant.speech.SpeechManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Background Assistant (foreground service).
 *
 * Wake word: honest SpeechRecognizer-based listening loop — when enabled, the
 * service listens in short windows and matches "hey soumya" / "soumya" /
 * "hello soumya" in recognised text, then handles the following command.
 * Continuous always-on hotword detection is platform/manufacturer-limited:
 * Android does not offer a system hotword API to third-party apps, so this
 * uses repeated recognition sessions while the service is running. The
 * persistent notification always shows the listening state, with
 * Pause / Resume / Disable actions. Nothing is recorded or uploaded.
 */
class AssistantService : Service() {

    companion object {
        const val CHANNEL_ID = "soumya_assistant"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE = "ai.soumya.assistant.PAUSE"
        const val ACTION_RESUME = "ai.soumya.assistant.RESUME"
        const val ACTION_DISABLE = "ai.soumya.assistant.DISABLE"

        fun start(context: Context) {
            val intent = Intent(context, AssistantService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AssistantService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: Prefs
    private lateinit var speech: SpeechManager
    private var paused = false
    private var listeningLoopActive = false

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        speech = SpeechManager(this, prefs)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> { paused = true; updateNotification(); return START_STICKY }
            ACTION_RESUME -> { paused = false; updateNotification(); maybeStartListening(); return START_STICKY }
            ACTION_DISABLE -> {
                prefs.backgroundModeEnabled = false
                prefs.wakeWordEnabled = false
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIFICATION_ID, buildNotification("SOUMYA AI is ready"))
        maybeStartListening()
        return START_STICKY
    }

    private fun maybeStartListening() {
        if (!prefs.wakeWordEnabled || paused || listeningLoopActive) return
        // PERMISSION GATE: without the mic grant the service must not listen
        // (and must not spin in an error loop) — it says so honestly instead.
        if (!PermissionGate.isGranted(this, PermissionGate.MICROPHONE)) {
            updateNotification("Mic permission nahi hai — app kholo aur mic allow karo, tab main sun paunga")
            return
        }
        listeningLoopActive = true
        speech.onSpeechResult = { heard ->
            listeningLoopActive = false
            val lower = heard.lowercase()
            val wakeWords = listOf("hey soumya", "hello soumya", "soumya")
            val hit = wakeWords.firstOrNull { lower.contains(it) }
            if (hit != null) {
                val command = heard.substringAfter(hit, "").trim()
                if (command.isNotEmpty()) handleHeardCommand(command) else {
                    speech.speak("Yes? How can I help?")
                    updateNotification("Listening… say a command after \"Hey Soumya\"")
                }
            }
            // No wake word: keep the loop going silently.
            if (prefs.wakeWordEnabled && !paused) {
                listeningLoopActive = false
                maybeStartListening()
            }
        }
        speech.onSpeechError = {
            listeningLoopActive = false
            if (prefs.wakeWordEnabled && !paused) maybeStartListening()
        }
        speech.startListening()
    }

    private fun handleHeardCommand(command: String) {
        scope.launch(Dispatchers.IO) {
            val executor = ToolExecutor(this@AssistantService, MemoryRepository(this@AssistantService))
            val local = LocalCommands(this@AssistantService, executor, MemoryRepository(this@AssistantService))
            val reply = local.handle(command) ?: "I heard \"$command\". Open the app to chat with Gemini about it."
            speech.speak(reply)
            updateNotification("Last command: $command")
            if (prefs.wakeWordEnabled && !paused) {
                listeningLoopActive = false
                maybeStartListening()
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "SOUMYA AI Assistant", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Background assistant status and controls" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseResume = if (paused) ACTION_RESUME else ACTION_PAUSE
        val pauseResumeLabel = if (paused) "Resume" else "Pause"
        val pauseIntent = PendingIntent.getService(
            this, 1, Intent(this, AssistantService::class.java).setAction(pauseResume),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disableIntent = PendingIntent.getService(
            this, 2, Intent(this, AssistantService::class.java).setAction(ACTION_DISABLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SOUMYA AI Assistant")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, pauseResumeLabel, pauseIntent)
            .addAction(0, "Disable", disableIntent)
            .build()
    }

    private fun updateNotification(text: String = if (paused) "Paused — tap Resume to listen again" else "Listening for \"Hey Soumya\"") {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        speech.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
