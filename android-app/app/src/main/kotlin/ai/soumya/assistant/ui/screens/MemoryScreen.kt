package ai.soumya.assistant.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.soumya.assistant.data.MemoryEntity
import ai.soumya.assistant.data.MemoryRepository
import ai.soumya.assistant.data.SecretFilter
import ai.soumya.assistant.ui.theme.CardBg
import ai.soumya.assistant.ui.theme.TextSecondary
import ai.soumya.assistant.ui.theme.Violet500
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryScreen(repo: MemoryRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var memories by remember { mutableStateOf<List<MemoryEntity>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MemoryEntity?>(null) }
    var showClearAll by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch { memories = repo.search(query) }
    }
    LaunchedEffect(query) { refresh() }

    Column(Modifier.fillMaxSize().background(Color(0xFF070B1A)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Memory Center", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add memory", tint = Color.White)
            }
            IconButton(onClick = {
                val text = repo.exportText(memories)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "SOUMYA AI memories")
                    putExtra(Intent.EXTRA_TEXT, text.ifBlank { "No memories saved yet." })
                }
                context.startActivity(Intent.createChooser(send, "Export memories"))
            }) {
                Icon(Icons.Filled.Share, contentDescription = "Export memories", tint = Color.White)
            }
        }
        Text(
            "Long-term memory. Passwords, OTPs and secrets are never saved here.",
            fontSize = 13.sp, color = TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search memories…") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (notice.isNotBlank()) {
            Text(notice, color = Color(0xFFFFB020), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(memories) { m ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 15.sp)
                                Text(
                                    "${m.category} • ${SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(m.createdAt))}",
                                    fontSize = 11.sp, color = TextSecondary
                                )
                            }
                            IconButton(onClick = { editing = m }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Color.White)
                            }
                            IconButton(onClick = { scope.launch { repo.delete(m); refresh() } }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
                            }
                        }
                        Text(m.content, fontSize = 14.sp, color = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { showClearAll = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Clear all memories") }
    }

    if (showAdd || editing != null) {
        MemoryEditorDialog(
            existing = editing,
            onDismiss = { showAdd = false; editing = null },
            onSave = { title, content, category ->
                scope.launch {
                    if (SecretFilter.looksLikeSecret(content) || SecretFilter.looksLikeSecret(title)) {
                        notice = "Not saved — that looks like a password, OTP or secret."
                    } else if (editing != null) {
                        repo.update(editing!!.copy(title = title, content = content, category = category))
                        notice = ""
                    } else {
                        repo.save(title, content, category)
                        notice = ""
                    }
                    refresh()
                }
                showAdd = false; editing = null
            }
        )
    }
    if (showClearAll) {
        AlertDialog(
            onDismissRequest = { showClearAll = false },
            title = { Text("Clear all memories?") },
            text = { Text("This deletes every saved memory. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.deleteAll(); refresh() }
                    showClearAll = false
                }) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAll = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun MemoryEditorDialog(
    existing: MemoryEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var content by remember { mutableStateOf(existing?.content ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "general") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add memory" else "Edit memory") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Content") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = content.isNotBlank(),
                onClick = { onSave(title, content, category.ifBlank { "general" }) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
