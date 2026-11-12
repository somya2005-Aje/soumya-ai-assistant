package ai.soumya.assistant.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import ai.soumya.assistant.ai.ToolExecutor
import ai.soumya.assistant.ai.ToolExecutor.LaunchableApp
import ai.soumya.assistant.service.SoumyaNotificationListener
import ai.soumya.assistant.ui.theme.CardBg
import ai.soumya.assistant.ui.theme.Cyan400
import ai.soumya.assistant.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AutomationTask(val description: String, val status: String, val time: Long = System.currentTimeMillis())

@Composable
fun AutomationScreen(executor: ToolExecutor, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf<AutomationTask>() }
    var appName by remember { mutableStateOf("") }
    var reminderText by remember { mutableStateOf("") }
    var reminderMinutes by remember { mutableStateOf("30") }
    var folderName by remember { mutableStateOf("SoumyaAI") }
    var pickedFile by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<File?>(null) }
    var lastResult by remember { mutableStateOf("") }

    // Full launcher app list (every launchable app on the phone).
    var allApps by remember { mutableStateOf(emptyList<LaunchableApp>()) }
    var appSearch by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) { executor.listLaunchableApps() }
    }
    val filteredApps = remember(appSearch, allApps) {
        val q = appSearch.trim().lowercase()
        if (q.isBlank()) allApps
        else allApps.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q.replace(" ", ""))
        }
    }

    fun log(description: String, status: String) {
        history.add(0, AutomationTask(description, status))
        lastResult = status
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            pickedFile = uri.toString()
            log("Browse file: picked a document", "Picked: $uri")
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF070B1A)).padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Automation Center", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Text(
            "Tasks run through official Android APIs. Deletion always asks first.",
            fontSize = 13.sp, color = TextSecondary
        )
        if (lastResult.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(lastResult, fontSize = 13.sp, color = Color.White)
        }

        Spacer(Modifier.height(16.dp))
        Text("Open an app", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = appName, onValueChange = { appName = it },
                placeholder = { Text("App name, e.g. YouTube") },
                modifier = Modifier.weight(1f), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    val outcome = executor.openApp(appName)
                    launch(Dispatchers.Main) { log("Open app: $appName", outcome.message) }
                }
            }) { Text("Open") }
        }

        Spacer(Modifier.height(16.dp))
        Text("Saare apps (${allApps.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            "Phone ke saare apps — search karo, tap karo, khul jayega.",
            fontSize = 13.sp, color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = appSearch, onValueChange = { appSearch = it },
            placeholder = { Text("Search apps…") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        if (allApps.isEmpty()) {
            Text("Apps load ho rahe hain…", fontSize = 13.sp, color = TextSecondary)
        } else if (filteredApps.isEmpty()) {
            Text("Koi app nahi mila — naam dobara check karo.", fontSize = 13.sp, color = TextSecondary)
        } else {
            Box(Modifier.fillMaxWidth().height(420.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            modifier = Modifier.fillMaxWidth().clickable {
                                scope.launch(Dispatchers.IO) {
                                    val outcome = executor.launchPackage(app.packageName)
                                    launch(Dispatchers.Main) { log("Open app: ${app.label}", outcome.message) }
                                }
                            }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val bitmap = remember(app) {
                                    try {
                                        app.icon?.toBitmap(96, 96)?.asImageBitmap()
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = app.label,
                                        modifier = Modifier.size(40.dp)
                                    )
                                } else {
                                    Box(
                                        Modifier.size(40.dp).background(Cyan400, androidx.compose.foundation.shape.CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            app.label.firstOrNull()?.toString() ?: "?",
                                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp
                                        )
                                    }
                                }
                                Text(
                                    app.label,
                                    fontSize = 14.sp, color = Color.White,
                                    modifier = Modifier.padding(start = 12.dp).weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("System settings shortcuts", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        val shortcuts = listOf("Wi-Fi" to "wifi", "Bluetooth" to "bluetooth", "Display" to "display", "Sound" to "sound", "Location" to "location", "Battery" to "battery")
        shortcuts.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, screen) ->
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val outcome = executor.openSettings(screen)
                                launch(Dispatchers.Main) { log("Open $label settings", outcome.message) }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(label, fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))
        Text("Files", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("Browse files") }
            Button(onClick = {
                val dir = File(context.getExternalFilesDir(null), folderName.ifBlank { "SoumyaAI" })
                val created = if (dir.exists()) false else dir.mkdirs()
                log(
                    "Create folder: ${dir.name}",
                    if (created) "Created folder at ${dir.absolutePath}" else "Folder already exists at ${dir.absolutePath}"
                )
            }) { Text("Create folder") }
        }
        OutlinedTextField(
            value = folderName, onValueChange = { folderName = it },
            label = { Text("Folder name") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        if (pickedFile != null) Text("Picked: $pickedFile", fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val dir = File(context.getExternalFilesDir(null), folderName.ifBlank { "SoumyaAI" })
                if (dir.exists()) folderToDelete = dir else lastResult = "No folder named \"${dir.name}\" to delete."
            }) { Text("Delete folder…") }
            Button(onClick = {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://www.google.com/maps")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                log("Open Maps", "Opened Google Maps in the browser.")
            }) { Text("Open Maps") }
        }

        Spacer(Modifier.height(16.dp))
        Text("Create a reminder", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        OutlinedTextField(
            value = reminderText, onValueChange = { reminderText = it },
            placeholder = { Text("Reminder text") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = reminderMinutes, onValueChange = { reminderMinutes = it.filter { c -> c.isDigit() } },
                label = { Text("Minutes from now") },
                modifier = Modifier.weight(1f), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    val outcome = executor.createReminder(reminderText, reminderMinutes.toDoubleOrNull() ?: 30.0)
                    launch(Dispatchers.Main) { log("Reminder: $reminderText", outcome.message) }
                }
            }) { Text("Set") }
        }

        Spacer(Modifier.height(16.dp))
        Text("Recent notifications", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            "Shown only if Notification Access is granted (Permissions screen).",
            fontSize = 12.sp, color = TextSecondary
        )
        val recent = synchronized(SoumyaNotificationListener.recent) { SoumyaNotificationListener.recent.toList() }
        if (recent.isEmpty()) {
            Text("No notifications captured yet.", fontSize = 13.sp, color = TextSecondary)
        } else {
            recent.take(5).forEach { n ->
                Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(n.packageName, fontSize = 12.sp, color = TextSecondary)
                        Text(n.title ?: "(no title)", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(n.text ?: "", fontSize = 13.sp, color = Color.White)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Task history", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (history.isEmpty()) {
            Text("No tasks run yet this session.", fontSize = 13.sp, color = TextSecondary)
        } else {
            history.take(20).forEach { task ->
                Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(task.description, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(task.status, fontSize = 13.sp, color = TextSecondary)
                        Text(
                            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(task.time)),
                            fontSize = 11.sp, color = TextSecondary
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (folderToDelete != null) {
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete folder?") },
            text = { Text("Delete \"${folderToDelete!!.name}\" and everything inside it? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val ok = folderToDelete!!.deleteRecursively()
                    log("Delete folder: ${folderToDelete!!.name}", if (ok) "Deleted." else "Could not delete the folder.")
                    folderToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { folderToDelete = null }) { Text("Cancel") } }
        )
    }
}
