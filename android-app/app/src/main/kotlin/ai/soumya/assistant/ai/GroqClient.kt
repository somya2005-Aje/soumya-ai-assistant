package ai.soumya.assistant.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Groq REST client (OpenAI-compatible chat completions API).
 * Free tier at console.groq.com — the key comes only from the user's
 * Settings field, stored in EncryptedSharedPreferences. Never hardcoded.
 *
 * Same friendly-error rule as [GeminiClient]: no raw HTTP code, response
 * body, exception message or stack trace ever reaches the user. Every
 * failure is mapped to a short Hindi message, typed with
 * [GeminiClient.FailureKind] so callers can react the same way they do
 * for Gemini failures (e.g. clear groqKeyVerified on auth failures).
 *
 * The tool list mirrors [GeminiClient]'s function declarations, written
 * in OpenAI's {"type":"function","function":{...}} format, so the same
 * [ToolExecutor] tools work through either provider.
 */
class GroqClient(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Model that served the last successful call (for "Connected ✓ (model: …)"). */
    var lastModelUsed: String? = null
        private set

    class GroqFailure(val kind: GeminiClient.FailureKind, message: String) : Exception(message)

    private fun toolsJson(): JSONArray {
        val tools = JSONArray()
        fun fn(name: String, desc: String, props: JSONObject, required: List<String>) {
            val parameters = JSONObject()
                .put("type", "object")
                .put("properties", props)
                .put("required", JSONArray(required))
            tools.put(
                JSONObject()
                    .put("type", "function")
                    .put("function", JSONObject()
                        .put("name", name)
                        .put("description", desc)
                        .put("parameters", parameters))
            )
        }
        fun props(vararg pairs: Pair<String, String>): JSONObject {
            val o = JSONObject()
            for ((k, v) in pairs) o.put(k, JSONObject().put("type", v))
            return o
        }
        fn("open_application", "Launch an installed app by its display name.",
            props("app_name" to "string"), listOf("app_name"))
        fn("get_battery_status", "Read the current battery level and charging state.",
            props(), emptyList())
        fn("get_device_info", "Read device model, Android version and manufacturer.",
            props(), emptyList())
        fn("open_system_settings", "Open a system settings screen.",
            props("screen" to "string"), listOf("screen"))
        fn("create_reminder", "Create a reminder for a time in minutes from now.",
            props("text" to "string", "minutes_from_now" to "number"), listOf("text", "minutes_from_now"))
        fn("make_call", "Call a contact by name or dial a phone number directly. Only works if the user granted Contacts and Phone permissions.",
            props("target" to "string"), listOf("target"))
        fn("list_recent_photos", "List recent photos from the gallery (names and dates only). Only works if the user granted Gallery permission.",
            props("count" to "number"), emptyList())
        fn("search_memory", "Search saved long-term memories.",
            props("query" to "string"), emptyList())
        fn("save_memory", "Save a user-approved fact or preference to long-term memory.",
            props("title" to "string", "content" to "string"), listOf("title", "content"))
        fn("send_whatsapp_message", "Open WhatsApp chat with a contact (by name) or a phone number, with the message prefilled. HONEST LIMIT: the chat opens with the message typed — the USER taps Send. Android cannot silently auto-send a WhatsApp message.",
            props("target" to "string", "message" to "string"), listOf("target", "message"))
        fn("play_youtube_music", "Play a song on YouTube Music (falls back to YouTube app, then browser).",
            props("query" to "string"), listOf("query"))
        fn("play_youtube_video", "Play/search a video on YouTube.",
            props("query" to "string"), listOf("query"))
        fn("create_facebook_post", "Open the Facebook composer with the post text prefilled. HONEST LIMIT: the composer opens — the USER taps Post. Android cannot silently auto-post to Facebook.",
            props("text" to "string"), listOf("text"))
        return tools
    }

    /** Silent connectivity check used by Settings / Setup Wizard "Test" buttons. */
    fun testConnection(): Result<String> {
        return generate("Reply with just OK.", emptyList(), "hi")
            .map { lastModelUsed ?: model }
    }

    /**
     * One chat turn, same shape as [GeminiClient.generate] so ChatEngine
     * can use either provider interchangeably. [functionResponse] /
     * [functionName] carry a tool outcome back to the model as a "tool"
     * message. On a 404 for the chosen model, retries with the fallback
     * model before failing.
     */
    fun generate(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        functionResponse: JSONObject? = null,
        functionName: String? = null
    ): Result<GeminiClient.GeminiResult> {
        return try {
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
            for ((role, text) in history) {
                messages.put(
                    JSONObject()
                        .put("role", if (role == "user") "user" else "assistant")
                        .put("content", text)
                )
            }
            messages.put(JSONObject().put("role", "user").put("content", userMessage))
            if (functionResponse != null && functionName != null) {
                messages.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", "call_$functionName")
                        .put("name", functionName)
                        .put("content", functionResponse.toString())
                )
            }

            val candidates = listOf(model) + MODEL_FALLBACKS.filter { it != model }
            var lastFailure: GroqFailure? = null
            for (candidate in candidates) {
                val body = JSONObject()
                    .put("model", candidate)
                    .put("messages", messages)
                    .put("tools", toolsJson())
                    .put("tool_choice", "auto")
                    .put("temperature", 0.8)
                    .put("max_tokens", 1024)
                val outcome = client.newCall(
                    Request.Builder()
                        .url("https://api.groq.com/openai/v1/chat/completions")
                        .header("Authorization", "Bearer $apiKey")
                        .post(body.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        parseSuccess(raw)
                    } else if (response.code == 404) {
                        null // model not available for this key — try the next one
                    } else {
                        Result.failure(failureForError(response.code, raw))
                    }
                }
                if (outcome != null) {
                    return outcome.onSuccess { lastModelUsed = candidate }
                }
                lastFailure = GroqFailure(
                    GeminiClient.FailureKind.SERVICE,
                    "Groq service abhi nahi mil rahi, thodi der baad phir try karo."
                )
            }
            Result.failure(
                lastFailure ?: GroqFailure(
                    GeminiClient.FailureKind.SERVICE,
                    "Groq service abhi nahi mil rahi, thodi der baad phir try karo."
                )
            )
        } catch (e: java.io.IOException) {
            Result.failure(
                GroqFailure(
                    GeminiClient.FailureKind.NETWORK,
                    "Internet se connect nahi ho paya — connection check karke phir try karo."
                )
            )
        } catch (e: GroqFailure) {
            Result.failure(e)
        } catch (_: Exception) {
            Result.failure(
                GroqFailure(GeminiClient.FailureKind.UNKNOWN, "Kuch gadbad ho gayi, phir try karo.")
            )
        }
    }

    private fun parseSuccess(raw: String): Result<GeminiClient.GeminiResult> {
        val json = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return Result.failure(
                GroqFailure(GeminiClient.FailureKind.UNKNOWN, "AI se jawab nahi mila, phir try karo.")
            )
        }
        val choices = json.optJSONArray("choices") ?: return Result.failure(
            GroqFailure(GeminiClient.FailureKind.UNKNOWN, "AI se jawab nahi mila, phir try karo.")
        )
        if (choices.length() == 0) return Result.failure(
            GroqFailure(GeminiClient.FailureKind.UNKNOWN, "AI se jawab nahi mila, phir try karo.")
        )
        val message = choices.getJSONObject(0).optJSONObject("message") ?: JSONObject()
        val text = message.optString("content").orEmpty().trim()
        var call: GeminiClient.FunctionCall? = null
        val toolCalls = message.optJSONArray("tool_calls")
        if (toolCalls != null && toolCalls.length() > 0) {
            val fn = toolCalls.getJSONObject(0).optJSONObject("function") ?: JSONObject()
            val args = try {
                JSONObject(fn.optString("arguments").orEmpty())
            } catch (_: Exception) {
                JSONObject()
            }
            call = GeminiClient.FunctionCall(fn.optString("name"), args)
        }
        return Result.success(GeminiClient.GeminiResult(text, call))
    }

    companion object {
        const val DEFAULT_MODEL = "llama-3.3-70b-versatile"
        val MODEL_FALLBACKS = listOf("llama-3.1-8b-instant")
        val MODELS = listOf(DEFAULT_MODEL, "llama-3.1-8b-instant")

        /**
         * Maps a Groq HTTP error to a typed, friendly Hindi diagnosis.
         * Never exposes codes, bodies or exception text to the user.
         */
        fun failureForError(code: Int, rawBody: String): GroqFailure {
            val err = try {
                JSONObject(rawBody).optJSONObject("error")
            } catch (_: Exception) {
                null
            }
            val message = err?.optString("message").orEmpty()
            return when {
                code == 400 && message.contains("invalid_api_key", ignoreCase = true) ->
                    GroqFailure(
                        GeminiClient.FailureKind.INVALID_KEY,
                        "Ye key galat lag rahi hai — console.groq.com se key dobara copy karke paste karo (poori key, bina space ke)."
                    )
                code == 400 ->
                    GroqFailure(
                        GeminiClient.FailureKind.INVALID_KEY,
                        "Key ya request me kuch galat hai — key dobara paste karke phir try karo. " +
                            "Groq key console.groq.com → API Keys se banti hai, free tier par koi card nahi chahiye."
                    )
                code == 401 || code == 403 ->
                    GroqFailure(
                        GeminiClient.FailureKind.PERMISSION,
                        "Groq key pehchan me aa gayi lekin iski permission nahi hai — console.groq.com me nayi key banao."
                    )
                code == 429 ->
                    GroqFailure(
                        GeminiClient.FailureKind.QUOTA,
                        "Groq par abhi bahut requests hain (free tier limit) — thodi der baad phir try karo."
                    )
                code in 500..599 ->
                    GroqFailure(
                        GeminiClient.FailureKind.SERVICE,
                        "Groq service abhi busy hai, thodi der baad phir try karo."
                    )
                else ->
                    GroqFailure(
                        GeminiClient.FailureKind.UNKNOWN,
                        "Groq se jawab nahi mila, phir try karo."
                    )
            }
        }
    }
}
