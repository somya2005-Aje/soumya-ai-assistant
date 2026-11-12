package ai.soumya.assistant.ai

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.database.Cursor
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import ai.soumya.assistant.data.MemoryRepository
import ai.soumya.assistant.service.ReminderReceiver
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Executes Gemini function calls through real Android APIs and reports
 * verified success/failure — never claims success without checking.
 */
class ToolExecutor(
    private val context: Context,
    private val memoryRepo: MemoryRepository
) {

    /**
     * Outcome of a tool run. [needsPermission] is the PermissionGate key the
     * user must grant before this can run (null = no grant was missing).
     * The message is always friendly Hindi — never a raw error.
     */
    data class ToolOutcome(
        val success: Boolean,
        val message: String,
        val needsPermission: String? = null
    )

    /**
     * Last permission key that blocked a tool (null when the last call was
     * not blocked). The chat layer reads this to show the grant prompt.
     * Cleared at the start of every [execute] call.
     */
    var lastBlockedPermission: String? = null
        private set

    /** Clears [lastBlockedPermission] (chat layer does this before each local command). */
    fun clearBlockedPermission() {
        lastBlockedPermission = null
    }

    /** Builds the blocked outcome for a missing grant — tool does NOT run. */
    private fun blocked(requirement: PermissionGate.Requirement): ToolOutcome {
        lastBlockedPermission = requirement.key
        return ToolOutcome(false, requirement.askMessage, needsPermission = requirement.key)
    }

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
        lastBlockedPermission = null
        // CENTRAL GATE: no tool runs without its permissions. A blocked tool
        // never claims success — it returns the Hindi ask instead.
        val missing = PermissionGate.missingForTool(context, name)
        if (missing.isNotEmpty()) return blocked(missing.first())
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
                "make_call" -> makeCall(args.optString("target"))
                "list_recent_photos" -> listRecentPhotos(args.optDouble("count", 5.0).toInt())
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

    /**
     * Calls a contact by name or dials a number directly.
     * PERMISSION-GATED: needs Contacts (to find the number) + Phone calls.
     * Without either grant nothing happens — the Hindi ask is returned.
     */
    fun makeCall(target: String): ToolOutcome {
        val missing = PermissionGate.missingForTool(context, "make_call")
        if (missing.isNotEmpty()) return blocked(missing.first())
        val t = target.trim()
        if (t.isBlank()) return ToolOutcome(false, "Kisko call karna hai, naam ya number bolo.")
        // Direct number (digits, +, spaces, dashes) — no contact lookup needed.
        val number: String
        val display: String
        if (t.matches(Regex("^[+0-9][0-9 ()-]{5,}$"))) {
            number = t.replace(" ", "")
            display = t
        } else {
            val found = findContactNumber(t)
                ?: return ToolOutcome(
                    false,
                    "\"$t\" naam contacts me nahi mila — sahi naam ya number bolo."
                )
            number = found.second
            display = found.first
        }
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolOutcome(true, "$display ko call laga raha hoon ✓")
        } catch (_: SecurityException) {
            // Permission was revoked between check and call — ask again.
            blocked(PermissionGate.info(PermissionGate.CALL))
        } catch (_: Exception) {
            ToolOutcome(false, "$display ko call nahi lag paya, phir try karo.")
        }
    }

    /** Finds a contact's display name + number by (partial) name, or null. */
    private fun findContactNumber(name: String): Pair<String, String>? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val displayName = cursor.getString(0) ?: name
                val number = cursor.getString(1) ?: return null
                displayName to number
            } else {
                null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            try { cursor?.close() } catch (_: Exception) { }
        }
    }

    /**
     * Lists recent gallery photos (names + dates only).
     * PERMISSION-GATED: needs Gallery. Without it nothing is read.
     */
    fun listRecentPhotos(count: Int): ToolOutcome {
        val missing = PermissionGate.missingForTool(context, "list_recent_photos")
        if (missing.isNotEmpty()) return blocked(missing.first())
        val n = count.coerceIn(1, 10)
        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN
        )
        val names = mutableListOf<String>()
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                while (cursor.moveToNext() && names.size < n) {
                    val name = cursor.getString(nameIdx) ?: continue
                    val date = SimpleDateFormat("d MMM", Locale.getDefault())
                        .format(Date(cursor.getLong(dateIdx)))
                    names.add("$name ($date)")
                }
            }
        } catch (_: SecurityException) {
            return blocked(PermissionGate.info(PermissionGate.GALLERY))
        } catch (_: Exception) {
            return ToolOutcome(false, "Gallery dekh nahi paya, phir try karo.")
        }
        return ToolOutcome(
            true,
            if (names.isEmpty()) "Gallery me koi photo nahi mili."
            else "Recent photos:\n" + names.joinToString("\n")
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
        } catch (_: SecurityException) {
            blocked(PermissionGate.info(PermissionGate.ALARMS))
        }
    }
}
