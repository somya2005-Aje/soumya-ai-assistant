package ai.soumya.assistant.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gemini REST client with function declarations.
 * The API key comes only from the user's Settings field (or a build-time
 * baked-in key) — never hardcoded.
 *
 * MODEL RESOLUTION (v1.1.1): Google retired gemini-2.0-flash / 2.5-flash
 * for API calls (404 "no longer available"), which made a perfectly good
 * key look broken. So the client now resolves the model dynamically:
 * it lists the models available to this key (GET /v1beta/models) and
 * picks the first one from the preference order below; if a generate
 * call still 404s on the chosen model it retries the same request with
 * the next model before failing. The user never sees a model-404 error.
 *
 * FRIENDLY-ERROR RULE: no raw HTTP code, response body, exception message
 * or stack trace ever reaches the user. Every failure is mapped to a
 * short Hindi message the user can act on. Failures are typed
 * ([GeminiFailure.kind]) so callers can react (e.g. clear keyVerified
 * on auth failures).
 */
class GeminiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class FunctionCall(val name: String, val args: JSONObject)
    data class GeminiResult(val text: String, val functionCall: FunctionCall?)

    /** Model that served the last successful generate call (for "Connected ✓ (model: …)"). */
    var lastModelUsed: String? = null
        private set

    enum class FailureKind { INVALID_KEY, PERMISSION, QUOTA, NETWORK, SERVICE, UNKNOWN }

    class GeminiFailure(val kind: FailureKind, message: String) : Exception(message)

    private fun toolsJson(): JSONObject {
        val functions = JSONArray()
        fun fn(name: String, desc: String, props: JSONObject, required: List<String>) {
            val parameters = JSONObject()
                .put("type", "OBJECT")
                .put("properties", props)
                .put("required", JSONArray(required))
            functions.put(
                JSONObject()
                    .put("name", name)
                    .put("description", desc)
                    .put("parameters", parameters)
            )
        }
        fun props(vararg pairs: Pair<String, String>): JSONObject {
            val o = JSONObject()
            for ((k, v) in pairs) o.put(k, JSONObject().put("type", v))
            return o
        }
        fn("open_application", "Launch an installed app by its display name.",
            props("app_name" to "STRING"), listOf("app_name"))
        fn("get_battery_status", "Read the current battery level and charging state.",
            props(), emptyList())
        fn("get_device_info", "Read device model, Android version and manufacturer.",
            props(), emptyList())
        fn("open_system_settings", "Open a system settings screen.",
            props("screen" to "STRING"), listOf("screen"))
        fn("create_reminder", "Create a reminder for a time in minutes from now.",
            props("text" to "STRING", "minutes_from_now" to "NUMBER"), listOf("text", "minutes_from_now"))
        fn("make_call", "Call a contact by name or dial a phone number directly. Only works if the user granted Contacts and Phone permissions.",
            props("target" to "STRING"), listOf("target"))
        fn("list_recent_photos", "List recent photos from the gallery (names and dates only). Only works if the user granted Gallery permission.",
            props("count" to "NUMBER"), emptyList())
        fn("search_memory", "Search saved long-term memories.",
            props("query" to "STRING"), emptyList())
        fn("save_memory", "Save a user-approved fact or preference to long-term memory.",
            props("title" to "STRING", "content" to "STRING"), listOf("title", "content"))
        return JSONObject().put("functionDeclarations", functions)
    }

    /**
     * Silent connectivity check used by the Settings "Test connection"
     * button and the save-key check. Success returns the model that
     * answered; failure returns the specific friendly diagnosis.
     */
    fun testConnection(): Result<String> {
        return generate("Reply with just OK.", emptyList(), "hi")
            .map { lastModelUsed ?: "Gemini" }
    }

    /**
     * One chat turn. [systemPrompt] carries character + language + memory context.
     * [history] is alternating user/model plain-text turns (latest last).
     * Retries across models on 404; every other failure maps to a typed
     * [GeminiFailure] with a friendly Hindi message.
     */
    fun generate(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        functionResponse: JSONObject? = null,
        functionName: String? = null
    ): Result<GeminiResult> {
        return try {
            val contents = JSONArray()
            for ((role, text) in history) {
                contents.put(
                    JSONObject()
                        .put("role", if (role == "user") "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", text)))
                )
            }
            val userParts = JSONArray().put(JSONObject().put("text", userMessage))
            if (functionResponse != null && functionName != null) {
                userParts.put(
                    JSONObject().put("functionResponse", JSONObject()
                        .put("name", functionName)
                        .put("response", functionResponse))
                )
            }
            contents.put(JSONObject().put("role", "user").put("parts", userParts))

            val body = JSONObject()
                .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
                .put("contents", contents)
                .put("tools", JSONArray().put(toolsJson()))
                .put("generationConfig", JSONObject()
                    .put("temperature", 0.8)
                    .put("maxOutputTokens", 1024))

            // Try the resolved model first, then the rest of the preference
            // order — a 404 on one model moves to the next, never to the user.
            val candidates = modelCandidates()
            var lastFailure: GeminiFailure? = null
            for (model in candidates) {
                val outcome = client.newCall(
                    Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                        .post(body.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        parseSuccess(raw)
                    } else if (response.code == 404) {
                        null // try the next model
                    } else {
                        Result.failure(failureForError(response.code, raw))
                    }
                }
                if (outcome != null) {
                    return outcome.onSuccess { lastModelUsed = model }
                }
                lastFailure = GeminiFailure(
                    FailureKind.SERVICE,
                    "AI service abhi nahi mil rahi, thodi der baad phir try karo."
                )
            }
            Result.failure(
                lastFailure ?: GeminiFailure(
                    FailureKind.SERVICE,
                    "AI service abhi nahi mil rahi, thodi der baad phir try karo."
                )
            )
        } catch (e: java.io.IOException) {
            // Network failure (no internet, DNS, timeout) — never show the details.
            Result.failure(
                GeminiFailure(
                    FailureKind.NETWORK,
                    "Internet se connect nahi ho paya — connection check karke phir try karo."
                )
            )
        } catch (e: GeminiFailure) {
            Result.failure(e)
        } catch (_: Exception) {
            Result.failure(
                GeminiFailure(FailureKind.UNKNOWN, "Kuch gadbad ho gayi, phir try karo.")
            )
        }
    }

    /** Ordered model candidates: the dynamically resolved model first, then the preference order. */
    private fun modelCandidates(): List<String> {
        val resolved = resolveModel()
        val ordered = MODEL_PREFERENCE.toMutableList()
        ordered.remove(resolved)
        return listOf(resolved) + ordered
    }

    /**
     * Resolves the best available model for this key: lists
     * GET /v1beta/models and picks the first preference-order model the
     * key can actually use (exact name, or a "-latest" variant). Cached
     * per app session. On any list failure, falls back to the top
     * preference so generate() can still try the order directly.
     */
    private fun resolveModel(): String {
        cachedModel?.let { return it }
        val resolved = try {
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey&pageSize=100")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val raw = response.body?.string().orEmpty()
                val models = JSONObject(raw).optJSONArray("models") ?: return@use null
                val available = (0 until models.length())
                    .mapNotNull { models.optJSONObject(it)?.optString("name") }
                    .map { it.removePrefix("models/") }
                    .toSet()
                MODEL_PREFERENCE.firstOrNull { pref ->
                    pref in available || "$pref-latest" in available
                }
            }
        } catch (_: Exception) {
            null
        } ?: MODEL_PREFERENCE.first()
        cachedModel = resolved
        return resolved
    }

    private fun parseSuccess(raw: String): Result<GeminiResult> {
        val json = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return Result.failure(
                GeminiFailure(FailureKind.UNKNOWN, "AI se jawab nahi mila, phir try karo.")
            )
        }
        val candidates = json.optJSONArray("candidates") ?: return Result.failure(
            GeminiFailure(FailureKind.UNKNOWN, "AI se jawab nahi mila, phir try karo.")
        )
        val content = candidates.getJSONObject(0).optJSONObject("content")
        val parts = content?.optJSONArray("parts") ?: JSONArray()
        var text = ""
        var call: FunctionCall? = null
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (part.has("text")) text += part.optString("text")
            if (part.has("functionCall")) {
                val fc = part.getJSONObject("functionCall")
                call = FunctionCall(fc.optString("name"), fc.optJSONObject("args") ?: JSONObject())
            }
        }
        return Result.success(GeminiResult(text.trim(), call))
    }

    companion object {
        /**
         * Model preference order — newest first. gemini-2.0-flash and
         * 2.5-flash were retired for API calls (404), so 3.x leads.
         */
        val MODEL_PREFERENCE = listOf(
            "gemini-3.6-flash",
            "gemini-3.7-flash",
            "gemini-2.5-flash",
            "gemini-2.0-flash"
        )

        @Volatile
        private var cachedModel: String? = null

        /**
         * Maps an HTTP error (code + parsed error body) to a typed,
         * friendly Hindi diagnosis. Never exposes codes, bodies or
         * exception text to the user.
         */
        fun failureForError(code: Int, rawBody: String): GeminiFailure {
            val err = try {
                JSONObject(rawBody).optJSONObject("error")
            } catch (_: Exception) {
                null
            }
            val status = err?.optString("status").orEmpty()
            val message = err?.optString("message").orEmpty()
            return when {
                code == 400 && (status == "INVALID_ARGUMENT" || message.contains("API_KEY_INVALID", ignoreCase = true)) ->
                    GeminiFailure(
                        FailureKind.INVALID_KEY,
                        "Ye key galat lag rahi hai — AI Studio se key dobara copy karke paste karo (poori key, bina space ke)."
                    )
                code == 400 ->
                    GeminiFailure(
                        FailureKind.INVALID_KEY,
                        "Key ya request me kuch galat hai — key dobara paste karke phir try karo."
                    )
                code == 401 || code == 403 ->
                    GeminiFailure(
                        FailureKind.PERMISSION,
                        "Key pehchan me aa gayi lekin is project/model ki permission nahi hai — AI Studio me nayi unrestricted key banao."
                    )
                code == 429 ->
                    GeminiFailure(
                        FailureKind.QUOTA,
                        "Abhi bahut requests hain, thodi der baad phir try karo."
                    )
                code == 404 ->
                    GeminiFailure(
                        FailureKind.SERVICE,
                        "AI service abhi nahi mil rahi, thodi der baad phir try karo."
                    )
                code in 500..599 ->
                    GeminiFailure(
                        FailureKind.SERVICE,
                        "AI service abhi busy hai, thodi der baad phir try karo."
                    )
                else ->
                    GeminiFailure(
                        FailureKind.UNKNOWN,
                        "AI se jawab nahi mila, phir try karo."
                    )
            }
        }

        /** Backwards-compatible helper for callers that only need a message. */
        fun friendlyMessageForCode(code: Int): String =
            failureForError(code, "").message
                ?: "AI se jawab nahi mila, phir try karo."
    }
}
