package ai.soumya.assistant.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gemini REST client (gemini-2.0-flash) with function declarations.
 * The API key comes only from the user's Settings field (or a build-time
 * baked-in key) — never hardcoded.
 *
 * FRIENDLY-ERROR RULE: no raw HTTP code, response body, exception message or
 * stack trace ever reaches the user. Every failure is mapped to a short
 * Hindi message the user can act on.
 */
class GeminiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class FunctionCall(val name: String, val args: JSONObject)
    data class GeminiResult(val text: String, val functionCall: FunctionCall?)

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
        fn("search_memory", "Search saved long-term memories.",
            props("query" to "STRING"), listOf("query"))
        fn("save_memory", "Save a user-approved fact or preference to long-term memory.",
            props("title" to "STRING", "content" to "STRING"), listOf("title", "content"))
        return JSONObject().put("functionDeclarations", functions)
    }

    /**
     * One chat turn. [systemPrompt] carries character + language + memory context.
     * [history] is alternating user/model plain-text turns (latest last).
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

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // Never surface the HTTP code or the raw error body.
                    return Result.failure(Exception(friendlyMessageForCode(response.code)))
                }
                val json = try {
                    JSONObject(raw)
                } catch (_: Exception) {
                    return Result.failure(Exception("AI se jawab nahi mila, phir try karo."))
                }
                val candidates = json.optJSONArray("candidates") ?: return Result.failure(
                    Exception("AI se jawab nahi mila, phir try karo.")
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
                Result.success(GeminiResult(text.trim(), call))
            }
        } catch (e: java.io.IOException) {
            // Network failure (no internet, DNS, timeout) — never show the details.
            Result.failure(Exception("Internet se connect nahi ho paya — connection check karke phir try karo."))
        } catch (_: Exception) {
            Result.failure(Exception("Kuch gadbad ho gayi, phir try karo."))
        }
    }

    companion object {
        /**
         * Maps an HTTP status to a friendly Hindi message.
         * No codes, bodies or exception text are ever shown to the user.
         */
        fun friendlyMessageForCode(code: Int): String = when (code) {
            400, 401, 403 -> "Key check nahi ho payi — Settings me key dobara daalo, ya AI Studio se nayi free key banao."
            404 -> "AI service nahi mili, phir try karo."
            429 -> "Abhi bahut requests hain, thodi der baad phir try karo."
            in 500..599 -> "AI service abhi busy hai, thodi der baad phir try karo."
            else -> "AI se jawab nahi mila, phir try karo."
        }
    }
}
