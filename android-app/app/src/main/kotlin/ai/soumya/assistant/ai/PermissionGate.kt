package ai.soumya.assistant.ai

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ai.soumya.assistant.service.SoumyaAccessibilityService

/**
 * Central permission gate for the whole app.
 *
 * RULE (user order): the app may do ALL work — background, notifications,
 * gallery, files, contacts, calls, settings — but ONLY when the user has
 * granted that permission. Before ANY Gemini tool (or on-device command)
 * runs, [missingForTool] is checked. If anything is missing, the tool does
 * NOT run, does NOT claim success, and the AI replies in Hindi asking for
 * that exact permission, with an in-app prompt to grant it.
 *
 * Nothing here ever throws for a missing grant — it just reports it.
 */
object PermissionGate {

    const val MICROPHONE = "microphone"
    const val CONTACTS = "contacts"
    const val CALL = "call"
    const val GALLERY = "gallery"
    const val FILES = "files"
    const val NOTIFICATIONS = "notifications"
    const val NOTIFICATION_LISTENER = "notification_listener"
    const val ALARMS = "alarms"
    const val LOCATION = "location"
    const val CAMERA = "camera"
    const val BLUETOOTH = "bluetooth"
    const val BACKGROUND = "background"
    const val ACCESSIBILITY = "accessibility"

    /** One missing grant: what it is called in Hindi and what the AI asks. */
    data class Requirement(
        val key: String,
        val hindiName: String,
        val askMessage: String
    )

    /** Every permission the app can ask for, for the dashboard. */
    val ALL_KEYS = listOf(
        MICROPHONE, CONTACTS, CALL, GALLERY, FILES, NOTIFICATIONS,
        NOTIFICATION_LISTENER, ALARMS, LOCATION, CAMERA, BLUETOOTH,
        BACKGROUND, ACCESSIBILITY
    )

    private val REQUIREMENTS = mapOf(
        MICROPHONE to Requirement(
            MICROPHONE, "Microphone",
            "Mic ki permission nahi hai, allow karo tab main sun paunga."
        ),
        CONTACTS to Requirement(
            CONTACTS, "Contacts",
            "Mere paas tumhare contacts ka access nahi hai, allow karo tab main kar dunga."
        ),
        CALL to Requirement(
            CALL, "Phone calls",
            "Call karne ki permission nahi hai, allow karo tab main call kar dunga."
        ),
        GALLERY to Requirement(
            GALLERY, "Gallery (photos/videos)",
            "Gallery dekhne ki permission nahi hai, allow karo tab main photos dikha dunga."
        ),
        FILES to Requirement(
            FILES, "Files (system picker)",
            "Files ke liye tumhe system picker se khud chunna hoga — main bina tumhare chune kuch nahi kholta."
        ),
        NOTIFICATIONS to Requirement(
            NOTIFICATIONS, "Notifications",
            "Notifications ki permission nahi hai, allow karo tab main reminders aur alerts bhej paunga."
        ),
        NOTIFICATION_LISTENER to Requirement(
            NOTIFICATION_LISTENER, "Notification Access",
            "Notification padhne ka access nahi hai — Settings me Notification Access on karo, tab main summary de paunga."
        ),
        ALARMS to Requirement(
            ALARMS, "Exact alarms",
            "Exact reminder ke liye alarm ki permission nahi hai — Settings me allow karo, tab main time par yaad dilaunga."
        ),
        LOCATION to Requirement(
            LOCATION, "Location",
            "Location ki permission nahi hai, allow karo tab main location wale kaam kar paunga."
        ),
        CAMERA to Requirement(
            CAMERA, "Camera",
            "Camera ki permission nahi hai, allow karo tab main photo le paunga."
        ),
        BLUETOOTH to Requirement(
            BLUETOOTH, "Bluetooth",
            "Bluetooth ki permission nahi hai, allow karo tab main connect kar paunga."
        ),
        BACKGROUND to Requirement(
            BACKGROUND, "Background (battery optimization)",
            "Background me chalne ke liye battery optimization off karni hogi — Settings me allow karo, tab main background me kaam kar paunga."
        ),
        ACCESSIBILITY to Requirement(
            ACCESSIBILITY, "Accessibility",
            "Accessibility service on nahi hai — Settings me on karo, tab main automation kar paunga."
        )
    )

    fun info(key: String): Requirement =
        REQUIREMENTS[key] ?: Requirement(key, key, "Is kaam ke liye permission chahiye, allow karo tab main kar dunga.")

    fun askMessage(key: String): String = info(key).askMessage

    /** Which Gemini tools need which grants. Tools not listed need none. */
    fun missingForTool(context: Context, toolName: String): List<Requirement> {
        val keys = when (toolName) {
            "make_call" -> listOf(CONTACTS, CALL)
            "list_recent_photos" -> listOf(GALLERY)
            "create_reminder" -> listOf(ALARMS)
            else -> emptyList()
        }
        return keys.filter { !isGranted(context, it) }.map { info(it) }
    }

    /** Live state of one grant. */
    fun isGranted(context: Context, key: String): Boolean {
        return when (key) {
            MICROPHONE -> has(context, Manifest.permission.RECORD_AUDIO)
            CONTACTS -> has(context, Manifest.permission.READ_CONTACTS)
            CALL -> has(context, Manifest.permission.CALL_PHONE)
            GALLERY -> if (Build.VERSION.SDK_INT >= 33) {
                has(context, Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                has(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            // Files go through the system picker (ACTION_OPEN_DOCUMENT):
            // the user consents on every pick, so there is no grant to hold.
            FILES -> true
            NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) {
                has(context, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                true
            }
            NOTIFICATION_LISTENER ->
                NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            } else {
                true
            }
            LOCATION -> has(context, Manifest.permission.ACCESS_FINE_LOCATION)
            CAMERA -> has(context, Manifest.permission.CAMERA)
            BLUETOOTH -> if (Build.VERSION.SDK_INT >= 31) {
                has(context, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                true
            }
            BACKGROUND ->
                (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .isIgnoringBatteryOptimizations(context.packageName)
            ACCESSIBILITY ->
                Settings.Secure.getString(
                    context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )?.contains(SoumyaAccessibilityService::class.java.name) == true
            else -> true
        }
    }

    private fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Runtime permissions to request for [key], or null when the grant lives
     * only in system settings (then use [settingsIntent]).
     */
    fun runtimePermissions(key: String): Array<String>? = when (key) {
        MICROPHONE -> arrayOf(Manifest.permission.RECORD_AUDIO)
        CONTACTS -> arrayOf(Manifest.permission.READ_CONTACTS)
        CALL -> arrayOf(Manifest.permission.CALL_PHONE)
        GALLERY -> if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            null
        }
        LOCATION -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        CAMERA -> arrayOf(Manifest.permission.CAMERA)
        BLUETOOTH -> if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            null
        }
        else -> null
    }

    /** System-settings deep link for grants that cannot be asked at runtime. */
    fun settingsIntent(context: Context, key: String): Intent? = when (key) {
        NOTIFICATION_LISTENER -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:${context.packageName}"))
        } else {
            null
        }
        BACKGROUND -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
        ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        else -> null
    }
}
