package ai.soumya.assistant.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * App settings (plain prefs) + Gemini API key (EncryptedSharedPreferences, Android Keystore).
 * No secret is ever hardcoded or written to logs.
 */
class Prefs(context: Context) {

    private val appContext = context.applicationContext

    private val settings: SharedPreferences =
        appContext.getSharedPreferences("soumya_settings", Context.MODE_PRIVATE)

    private val secure: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "soumya_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * The two companion characters. Internal enum stays MALE/FEMALE; the
     * user-visible names are JARVIS (male) and ALEXA (female) — the app
     * itself is still "SOUMYA AI ASSISTANT".
     */
    enum class Character(val displayName: String, val wakePhrase: String) {
        MALE("Jarvis", "Hey Jarvis"),
        FEMALE("Alexa", "Hey Alexa")
    }

    val companionName: String get() = character.displayName

    var character: Character
        get() = if (settings.getString("character", "MALE") == "FEMALE") Character.FEMALE else Character.MALE
        set(value) { settings.edit().putString("character", value.name).apply() }

    var characterChosen: Boolean
        get() = settings.getBoolean("character_chosen", false)
        set(value) { settings.edit().putBoolean("character_chosen", value).apply() }

    /** "hi" | "en" | "hinglish" */
    var language: String
        get() = settings.getString("language", "hinglish") ?: "hinglish"
        set(value) { settings.edit().putString("language", value).apply() }

    var speechRate: Float
        get() = settings.getFloat("speech_rate", 1.0f)
        set(value) { settings.edit().putFloat("speech_rate", value).apply() }

    var speechPitch: Float
        get() = settings.getFloat("speech_pitch", 1.0f)
        set(value) { settings.edit().putFloat("speech_pitch", value).apply() }

    var wakeWordEnabled: Boolean
        get() = settings.getBoolean("wake_word_enabled", false)
        set(value) { settings.edit().putBoolean("wake_word_enabled", value).apply() }

    var backgroundModeEnabled: Boolean
        get() = settings.getBoolean("background_mode_enabled", false)
        set(value) { settings.edit().putBoolean("background_mode_enabled", value).apply() }

    var speakReplies: Boolean
        get() = settings.getBoolean("speak_replies", true)
        set(value) { settings.edit().putBoolean("speak_replies", value).apply() }

    var geminiApiKey: String?
        get() = secure.getString("gemini_api_key", null)?.takeIf { it.isNotBlank() }
        set(value) {
            val cleaned = cleanApiKey(value ?: "")
            val changed = cleaned != (geminiApiKey ?: "")
            if (cleaned.isBlank()) secure.edit().remove("gemini_api_key").apply()
            else secure.edit().putString("gemini_api_key", cleaned).apply()
            // Any key change invalidates the previous verification — the new
            // key has to prove itself with a real successful Gemini call.
            if (changed) keyVerified = false
        }

    /**
     * True only after a real generateContent call succeeded with the
     * currently stored key. Set by ChatEngine / the Settings key check;
     * cleared whenever the key changes/is cleared or a call fails with
     * 400/401/403. Home shows "Gemini connected ✓" only when this is true.
     */
    var keyVerified: Boolean
        get() = settings.getBoolean("key_verified", false)
        set(value) { settings.edit().putBoolean("key_verified", value).apply() }

    companion object {
        /**
         * Users sometimes paste a key wrapped in quotes ("AIza...").
         * Strip surrounding whitespace AND surrounding quote characters.
         */
        fun cleanApiKey(raw: String): String =
            raw.trim().trim('"').trim('\'').trim()
    }

    /** True when the user has saved their own key in Settings. */
    fun hasUserKey(): Boolean = geminiApiKey != null

    /**
     * Key actually used for Gemini calls. Resolution order:
     * 1. the user's own key from Settings (any AI Studio / free key works),
     * 2. a baked-in BuildConfig key, if one was supplied at build time
     *    (normally empty — no key is ever hardcoded or committed),
     * 3. null = on-device only + setup guide.
     * The key value is never shown in UI and never logged.
     */
    fun effectiveGeminiKey(): String? {
        geminiApiKey?.let { return it }
        val bakedIn = try {
            ai.soumya.assistant.BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }.trim()
        return bakedIn.ifBlank { null }
    }

    fun hasGeminiKey(): Boolean = effectiveGeminiKey() != null
}
