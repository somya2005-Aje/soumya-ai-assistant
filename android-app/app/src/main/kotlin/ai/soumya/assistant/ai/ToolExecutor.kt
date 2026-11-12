package ai.soumya.assistant.ai

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
        } catch (e: Exception) {
            ToolOutcome(false, "Failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Finds an installed app by display name (fuzzy) and launches it. */
    fun openApp(appName: String): ToolOutcome {
        if (appName.isBlank()) return ToolOutcome(false, "No app name given.")
        val pm: PackageManager = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val query = appName.trim().lowercase()
        val match = apps.firstOrNull {
            pm.getApplicationLabel(it).toString().lowercase() == query
        } ?: apps.firstOrNull {
            pm.getApplicationLabel(it).toString().lowercase().contains(query)
        } ?: apps.firstOrNull {
            it.packageName.lowercase().contains(query.replace(" ", ""))
        } ?: return ToolOutcome(false, "I could not find an installed app named \"$appName\".")
        val label = pm.getApplicationLabel(match).toString()
        val intent = pm.getLaunchIntentForPackage(match.packageName)
            ?: return ToolOutcome(false, "\"$label\" is installed but has no launch screen I can open.")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ToolOutcome(true, "Opened $label.")
        } catch (e: Exception) {
            ToolOutcome(false, "Could not open $label: ${e.message}")
        }
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
        } catch (e: Exception) {
            ToolOutcome(false, "Could not open $screen settings: ${e.message}")
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
