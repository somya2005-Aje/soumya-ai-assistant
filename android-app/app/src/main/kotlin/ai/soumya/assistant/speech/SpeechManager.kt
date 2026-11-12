package ai.soumya.assistant.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import ai.soumya.assistant.data.Prefs
import java.util.Locale

/** Voice states shown on the Home character. */
enum class VoiceState { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

class SpeechManager(
    private val context: Context,
    private val prefs: Prefs
) {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    var onStateChange: ((VoiceState) -> Unit)? = null
    var onSpeechResult: ((String) -> Unit)? = null
    var onSpeechError: ((String) -> Unit)? = null
    var onTtsDone: (() -> Unit)? = null

    fun initTts(onReady: (() -> Unit)? = null) {
        if (tts != null) { onReady?.invoke(); return }
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                applyLanguage()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { onStateChange?.invoke(VoiceState.SPEAKING) }
                    override fun onDone(utteranceId: String?) {
                        onStateChange?.invoke(VoiceState.IDLE)
                        onTtsDone?.invoke()
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { onStateChange?.invoke(VoiceState.ERROR) }
                    override fun onError(utteranceId: String?, errorCode: Int) { onStateChange?.invoke(VoiceState.ERROR) }
                })
                onReady?.invoke()
            } else {
                onStateChange?.invoke(VoiceState.ERROR)
            }
        }
    }

    fun applyLanguage() {
        val tts = this.tts ?: return
        val locale = when (prefs.language) {
            "hi" -> Locale("hi", "IN")
            "en" -> Locale.ENGLISH
            else -> Locale("hi", "IN") // Hinglish: Hindi engine handles mixed speech best
        }
        tts.language = locale
        tts.setSpeechRate(prefs.speechRate)
        tts.setPitch(prefs.speechPitch)
        // Prefer a voice matching the selected character's gender when available.
        val wantFemale = prefs.character == Prefs.Character.FEMALE
        val voices = tts.voices?.filter { it.locale.language == locale.language } ?: emptyList()
        val gendered = voices.firstOrNull { voice ->
            val n = voice.name.lowercase()
            if (wantFemale) n.contains("female") || n.contains("fem") else n.contains("male")
        }
        if (gendered != null) tts.voice = gendered
    }

    fun speak(text: String) {
        if (!prefs.speakReplies) return
        initTts {
            if (!ttsReady) return@initTts
            applyLanguage()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "soumya-${System.currentTimeMillis()}")
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        onStateChange?.invoke(VoiceState.IDLE)
    }

    fun isListening(): Boolean = recognizer != null

    /** One-shot speech recognition. Call [stopListening] to end early. */
    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onSpeechError?.invoke("Is phone me voice recognition nahi hai — type karke bolo.")
            return
        }
        stopListening()
        val languageTag = when (prefs.language) {
            "en" -> "en-IN"
            else -> "hi-IN"
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        val rec = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onStateChange?.invoke(VoiceState.LISTENING) }
            override fun onBeginningOfSpeech() { onStateChange?.invoke(VoiceState.LISTENING) }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    onSpeechError?.invoke("Sun nahi paya, phir se bolo.")
                } else {
                    onStateChange?.invoke(VoiceState.IDLE)
                }
                recognizer = null
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                recognizer = null
                if (text.isNotBlank()) onSpeechResult?.invoke(text) else onStateChange?.invoke(VoiceState.IDLE)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try {
            rec.startListening(intent)
        } catch (_: Exception) {
            onSpeechError?.invoke("Mic se sun nahi paya, phir try karo.")
            recognizer = null
        }
    }

    fun stopListening() {
        try { recognizer?.cancel() } catch (_: Exception) { }
        recognizer = null
    }

    fun shutdown() {
        stopListening()
        try { tts?.shutdown() } catch (_: Exception) { }
        tts = null
    }
}
