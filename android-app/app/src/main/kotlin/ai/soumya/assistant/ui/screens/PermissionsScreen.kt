package ai.soumya.assistant.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ai.soumya.assistant.service.SoumyaAccessibilityService
import ai.soumya.assistant.ui.theme.CardBg
import ai.soumya.assistant.ui.theme.TextSecondary

enum class PermState { GRANTED, DENIED, NOT_GRANTED, UNAVAILABLE }

data class PermRow(
    val name: String,
    val purpose: String,
    val state: PermState,
    val onRequest: () -> Unit,
    val actionLabel: String
)

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refreshTick by remember { mutableStateOf(0) }
    @Suppress("UNUSED_VARIABLE")
    val tick = refreshTick

    val multiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshTick++ }

    fun runtimeState(permission: String): PermState =
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
            PermState.GRANTED else PermState.DENIED

    fun openSystemSettings(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        refreshTick++
    }

    val notificationListenerGranted =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    val accessibilityGranted = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )?.contains(SoumyaAccessibilityService::class.java.name) == true

    val exactAlarmGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    } else true

    val batteryOptIgnored =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)

    val rows = listOf(
        PermRow(
            "Microphone", "Voice input and wake-word listening.",
            runtimeState(Manifest.permission.RECORD_AUDIO),
            { multiLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) },
            "Grant"
        ),
        PermRow(
            "Notifications", "Show assistant and reminder notifications.",
            if (Build.VERSION.SDK_INT >= 33) runtimeState(Manifest.permission.POST_NOTIFICATIONS) else PermState.GRANTED,
            {
                if (Build.VERSION.SDK_INT >= 33) multiLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                else openSystemSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            },
            "Grant"
        ),
        PermRow(
            "Notification Access", "Read notifications to summarise them. Enable manually in system settings.",
            if (notificationListenerGranted) PermState.GRANTED else PermState.NOT_GRANTED,
            { openSystemSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            "Open settings"
        ),
        PermRow(
            "Accessibility", "Optional automation (tap/scroll). Disabled by default; enable manually.",
            if (accessibilityGranted) PermState.GRANTED else PermState.NOT_GRANTED,
            { openSystemSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            "Open settings"
        ),
        PermRow(
            "Exact alarms", "Fire reminders at the exact time you asked for.",
            if (exactAlarmGranted) PermState.GRANTED else PermState.NOT_GRANTED,
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    openSystemSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:${context.packageName}")))
                }
            },
            "Open settings"
        ),
        PermRow(
            "Ignore battery optimisations", "Keep the background assistant running reliably.",
            if (batteryOptIgnored) PermState.GRANTED else PermState.NOT_GRANTED,
            {
                openSystemSettings(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${context.packageName}"))
                )
            },
            "Open settings"
        ),
        PermRow(
            "Location", "Navigation and location-aware answers.",
            runtimeState(Manifest.permission.ACCESS_FINE_LOCATION),
            { multiLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            "Grant"
        ),
        PermRow(
            "Camera", "Taking photos inside the assistant when you ask.",
            runtimeState(Manifest.permission.CAMERA),
            { multiLauncher.launch(arrayOf(Manifest.permission.CAMERA)) },
            "Grant"
        ),
        PermRow(
            "Bluetooth", "Connect to Bluetooth devices on request.",
            if (Build.VERSION.SDK_INT >= 31) runtimeState(Manifest.permission.BLUETOOTH_CONNECT) else PermState.GRANTED,
            {
                if (Build.VERSION.SDK_INT >= 31) multiLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            },
            "Grant"
        ),
        PermRow(
            "Media & files", "Browse files you pick through the system picker.",
            if (Build.VERSION.SDK_INT >= 33) runtimeState(Manifest.permission.READ_MEDIA_IMAGES) else runtimeState(Manifest.permission.READ_EXTERNAL_STORAGE),
            {
                if (Build.VERSION.SDK_INT >= 33) multiLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO))
                else multiLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            },
            "Grant"
        )
    )

    Column(
        Modifier.fillMaxSize().background(Color(0xFF070B1A)).padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Permissions", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Text(
            "Permissions are asked only when a feature needs them — never all at first launch. " +
                "Some access (Notification Access, Accessibility) can only be granted in system settings.",
            fontSize = 13.sp, color = TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        rows.forEach { row ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.name, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 15.sp)
                            Text(row.purpose, fontSize = 13.sp, color = TextSecondary)
                        }
                        Text(
                            when (row.state) {
                                PermState.GRANTED -> "Granted"
                                PermState.DENIED -> "Denied"
                                PermState.NOT_GRANTED -> "Not granted"
                                PermState.UNAVAILABLE -> "Unavailable"
                            },
                            fontSize = 13.sp,
                            color = if (row.state == PermState.GRANTED) Color(0xFF38E6B5) else Color(0xFFFFB020),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = row.onRequest) { Text(row.actionLabel) }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
