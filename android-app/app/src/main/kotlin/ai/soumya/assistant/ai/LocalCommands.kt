package ai.soumya.assistant.ai

import android.content.Context
import ai.soumya.assistant.data.MemoryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device command handling that works WITHOUT a Gemini key:
 * open apps, battery, device info, time/date, reminders, memory save/search.
 * Returns null when the text is not a local command (caller then uses Gemini).
 */
class LocalCommands(
    private val context: Context,
    private val executor: ToolExecutor,
    private val memoryRepo: MemoryRepository
) {

    suspend fun handle(text: String): String? {
        val t = text.trim()
        val lower = t.lowercase(Locale.getDefault())

        // Call: "call Mom" / "Mom ko call karo" / "call 9876543210"
        // (permission-gated in ToolExecutor — without Contacts/Phone grants
        // it only asks in Hindi and does nothing).
        val callMatch = Regex("(?i)(?:call|phone karo|call karo|call kar do)\\s+(.+)")
            .find(t)?.groupValues?.getOrNull(1)?.trim()
        if (callMatch != null && callMatch.isNotBlank()) {
            return executor.makeCall(callMatch).message
        }
        val koCallMatch = Regex("(?i)(.+)\\s+ko\\s+call\\s+(?:karo|kar do|lagao)\\s*$")
            .find(t)?.groupValues?.getOrNull(1)?.trim()
        if (koCallMatch != null && koCallMatch.length > 1) {
            return executor.makeCall(koCallMatch).message
        }

        // Open app: "open YouTube" / "youtube kholo" / "soumya, chrome kholo"
        val openMatch = Regex("(?i)(?:open|kholo|khol|launch|start)\\s+(.+)", RegexOption.IGNORE_CASE)
            .find(t)?.groupValues?.getOrNull(1)?.trim()
        if (openMatch != null) {
            val name = openMatch
                .removePrefix("the ").removeSuffix(" app").removeSuffix(" ko")
                .removePrefix("mera ").trim()
            val outcome = executor.openApp(name)
            return outcome.message
        }
        val kholoMatch = Regex("(?i)(.+)\\s+kholo\\s*$").find(t)?.groupValues?.getOrNull(1)?.trim()
        if (kholoMatch != null && kholoMatch.length > 1) {
            return executor.openApp(kholoMatch).message
        }

        if (lower.contains("battery")) return executor.batteryStatus().message
        if (lower.contains("device info") || lower.contains("phone model") || lower.contains("mere phone")) {
            return executor.deviceInfo().message
        }
        if (lower.contains("time") || lower.contains("samay") || lower.contains("baj rahe")) {
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            return "Abhi $time ho raha hai."
        }
        if (lower.contains("date") || lower.contains("tarikh") || lower.contains("aaj ka din")) {
            val date = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
            return "Aaj $date hai."
        }

        // Reminder: "remind me in 10 minutes to call mom" / "10 minute baad yaad dilana"
        val remindMatch = Regex("(?i)remind me in (\\d+)\\s*(minute|minutes|hour|hours|min)\\s*(?:to\\s+)?(.*)")
            .find(t)
        if (remindMatch != null) {
            val amount = remindMatch.groupValues[1].toDouble()
            val unit = remindMatch.groupValues[2].lowercase()
            val minutes = if (unit.startsWith("hour")) amount * 60 else amount
            val what = remindMatch.groupValues[3].ifBlank { "reminder" }
            return executor.createReminder(what, minutes).message
        }

        // Remember: "remember that I prefer Hindi" / "yaad rakho ..."
        val rememberMatch = Regex("(?i)(?:remember that|remember|yaad rakho(?: ki)?)\\s+(.+)").find(t)
        if (rememberMatch != null) {
            val content = rememberMatch.groupValues[1].trim()
            val saved = memoryRepo.save(content.take(40), content)
            return saved?.let { "Yaad rakh liya: $content" }
                ?: "I did not save that — it looked like a password, OTP or secret, and I never store those."
        }

        return null
    }
}
