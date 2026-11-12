package ai.soumya.assistant.ai

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import ai.soumya.assistant.data.MemoryRepository
import ai.soumya.assistant.service.ReminderReceiver
import org.json.JSONObject

/**
 * Executes Gemini function calls through real Android APIs and reports
 * verified success/failure — never claims success without checking.
 */
class ToolExecutor(
    private val context: Context,
    private val memoryRepo: MemoryRepository
) {

    data class ToolOutcome(val success: Boolean, val message: String)

    /** One launchable app on the phone (launcher entry). */
    data class LaunchableApp(
        val label: String,
        val packageName: String,
        val icon: Drawable?
    )

    /**
     * ALL launchable installed apps, sorted by name. Uses the launcher
     * intent query, so every app the user can open from the home screen
     * shows up — no hardcoded short list. Requires the <queries> launcher
     * intent in AndroidManifest.xml (Android 11+ package visibility).
     */
    fun listLaunchableApps(): List<LaunchableApp> {
        val pm: PackageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos: List<ResolveInfo> = try {
            pm.queryIntentActivities(intent, 0)
        } catch (_: Exception) {
            emptyList()
        }
        return resolveInfos.mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            val label = try {
                info.loadLabel(pm).toString()
            } catch (_: Exception) {
                activityInfo.packageName
            }
            val icon = try {
                info.loadIcon(pm)
            } catch (_: Exception) {
                null
            }
            LaunchableApp(label, activityInfo.packageName, icon)
        }.sortedBy { it.label.lowercase() }
    }

    /** Launch an app directly by package name. Returns a verified outcome. */
    fun launchPackage(packageName: String): ToolOutcome {
        if (packageName.isBlank()) return ToolOutcome(false, "Koi app nahi chuni gayi.")
        val pm: PackageManager = context.packageManager
        val label = try {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
        val intent = pm.getLaunchIntentForPackage(packageName)
            ?: return ToolOutcome(false, "\"$label\" khul nahi paya — iska koi launch screen nahi hai.")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ToolOutcome(true, "$label khul gaya ✓")
        } catch (_: Exception) {
            ToolOutcome(false, "$label khul nahi paya, phir try karo.")
        }
    }

    suspend fun execute(name: String, args: JSONObject): ToolOutcome {
        return try {
            when (name) {
                "open_application" -> openApp(args.optString("app_name"))
                "get_battery_status" -> batteryStatus()
                "get_device_info" -> deviceInfo()
                "open_system_settings" -> openSettings(args.optString("screen"))
                "create_reminder" -> createReminder(
                    args.optString("text"),
                    args.optDouble("minutes_from_now", 60.0)
                )
                "search_memory" -> {
                    val hits = memoryRepo.search(args.optString("query"))
                    ToolOutcome(true, if (hits.isEmpty()) "No saved memories matched."
                    else hits.take(5).joinToString("\n") { "${it.title}: ${it.content}" })
                }
                "save_memory" -> {
                    val saved = memoryRepo.save(args.optString("title"), args.optString("content"))
                    if (saved == null) ToolOutcome(false, "I did not save that — it looked like a password, OTP or secret, and I never store those.")
                    else ToolOutcome(true, "Saved to memory: ${saved.title}")
                }
                else -> ToolOutcome(false, "Unknown tool: $name")
            }
        } catch (_: Exception) {
            ToolOutcome(false, "Wo kaam nahi ho paya, phir try karo.")
        }
    }

    /**
     * Finds an installed app by display name (fuzzy) and launches it.
     * Matching is case-insensitive and partial ("youtube" matches
     * "YouTube"), and common Hindi names map to their English labels
     * ("यूट्यूब" → YouTube). Always searches the full launcher list.
     */
    fun openApp(appName: String): ToolOutcome {
        if (appName.isBlank()) return ToolOutcome(false, "Kaunsi app kholni hai, naam bolo.")
        val query = normalizeAppQuery(appName)
        if (query.isBlank()) return ToolOutcome(false, "Kaunsi app kholni hai, naam bolo.")
        val apps = listLaunchableApps()
        val match = apps.firstOrNull {
            it.label.lowercase() == query
        } ?: apps.firstOrNull {
            it.label.lowercase().contains(query)
        } ?: apps.firstOrNull {
            it.packageName.lowercase().contains(query.replace(" ", ""))
        } ?: return ToolOutcome(
            false,
            "Ye app nahi mila — naam dobara check karke bolo, ya Automation Center me list se chuno."
        )
        return launchPackage(match.packageName)
    }

    /**
     * Normalizes a spoken/typed app name for matching: trims, lowercases,
     * strips filler words ("kholo", "ko", "app", "mera", "the") and maps
     * common Hindi names to their English labels.
     */
    private fun normalizeAppQuery(raw: String): String {
        var q = raw.trim().lowercase()
        for (suffix in listOf(" kholo", " khol", " ko", " app", " open karo", " chalao")) {
            if (q.endsWith(suffix)) q = q.removeSuffix(suffix)
        }
        for (prefix in listOf("mera ", "meri ", "the ")) {
            if (q.startsWith(prefix)) q = q.removePrefix(prefix)
        }
        q = q.trim()
        return HINDI_APP_NAMES[q] ?: q
    }

    companion object {
        /** Common Hindi names users speak → the app's English label. */
        private val HINDI_APP_NAMES = mapOf(
            "यूट्यूब" to "youtube",
            "व्हाट्सएप" to "whatsapp",
            "वॉट्सऐप" to "whatsapp",
            "क्रोम" to "chrome",
            "गूगल" to "google",
            "फेसबुक" to "facebook",
            "इंस्टाग्राम" to "instagram",
            "मैप्स" to "maps",
            "मैप" to "maps",
            "कैमरा" to "camera",
            "गैलरी" to "gallery",
            "सेटिंग्स" to "settings",
            "सेटिंग" to "settings",
            "फोन" to "phone",
            "मैसेज" to "messages",
            "संदेश" to "messages",
            "जीमेल" to "gmail",
            "मेल" to "gmail",
            "प्ले स्टोर" to "play store",
            "यूट्यूब म्यूजिक" to "youtube music",
            "टेलीग्राम" to "telegram",
            "स्पॉटिफाई" to "spotify",
            "नेटफ्लिक्स" to "netflix",
            "कैलकुलेटर" to "calculator",
            "घड़ी" to "clock",
            "कैलेंडर" to "calendar",
            "फोटो" to "photos",
            "फाइल" to "files",
            "फाइल्स" to "files",
            "नोट्स" to "notes"
        )
    }

    fun batteryStatus(): ToolOutcome {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        return ToolOutcome(
            true,
            "Battery is $level%${if (charging) " and charging" else " and not charging"}."
        )
    }

    fun deviceInfo(): ToolOutcome = ToolOutcome(
        true,
        "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})."
    )

    fun openSettings(screen: String): ToolOutcome {
        val action = when (screen.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "battery" -> "android.intent.action.POWER_USAGE_SUMMARY" // Settings.ACTION_POWER_USAGE_SUMMARY is @hide; use the literal action
            "apps", "applications" -> Settings.ACTION_APPLICATION_SETTINGS
            "notifications" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            }
            context.startActivity(intent)
            ToolOutcome(true, "Opened $screen settings.")
        } catch (_: Exception) {
            ToolOutcome(false, "$screen settings khul nahi payi, phir try karo.")
        }
    }

    fun createReminder(text: String, minutesFromNow: Double): ToolOutcome {
        if (text.isBlank()) return ToolOutcome(false, "No reminder text given.")
        val triggerAt = System.currentTimeMillis() + (minutesFromNow * 60_000).toLong()
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_text", text)
        }
        val pending = PendingIntent.getBroadcast(
            context, text.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarm.canScheduleExactAlarms()) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            ToolOutcome(true, "Reminder set for \"$text\" in ${minutesFromNow.toInt()} minutes.")
        } catch (e: SecurityException) {
            ToolOutcome(false, "Exact alarms are not allowed — please enable Alarms in Settings, then try again.")
        }
    }
}
