package com.example.my_app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.example.my_app.ui.theme.My_AppTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class Reminder(
    val id: String,
    val label: String,
    val durationSeconds: Long,
    val targetEpochMilli: Long
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            My_AppTheme {
                var showAddDialog by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val prefs = remember { context.getSharedPreferences("reminder_list_prefs", Context.MODE_PRIVATE) }
                
                // Initialize reminders from persistent storage
                var reminders by remember {
                    val savedString = prefs.getString("reminders", "") ?: ""
                    val initialList = if (savedString.isEmpty()) {
                        // Default reminders if none saved
                        listOf(
                            createReminder("Hoover the stairs", Duration.ofDays(7).seconds),
                            createReminder("Do 100 pushups", Duration.ofDays(1).seconds),
                            createReminder("Drink water", Duration.ofHours(1).seconds)
                        )
                    } else {
                        parseReminders(savedString)
                    }
                    mutableStateOf(initialList)
                }

                // Save reminders whenever the list changes
                LaunchedEffect(reminders) {
                    prefs.edit { putString("reminders", serializeReminders(reminders)) }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Reminder")
                        }
                    }
                ) { innerPadding ->
                    ReminderList(
                        reminders = reminders,
                        onDelete = { id -> reminders = reminders.filter { it.id != id } },
                        onReset = { id -> 
                            reminders = reminders.map { 
                                if (it.id == id) {
                                    it.copy(targetEpochMilli = Instant.now().plusSeconds(it.durationSeconds).toEpochMilli())
                                } else it
                            }
                        },
                        modifier = Modifier.padding(innerPadding)
                    )

                    if (showAddDialog) {
                        AddReminderDialog(
                            onDismiss = { showAddDialog = false },
                            onConfirm = { label, duration ->
                                reminders = reminders + createReminder(label, duration.seconds)
                                showAddDialog = false
                            }
                        )
                    }
                }
            }
        }
    }

    private fun createReminder(label: String, durationSeconds: Long): Reminder {
        return Reminder(
            id = UUID.randomUUID().toString(),
            label = label,
            durationSeconds = durationSeconds,
            targetEpochMilli = Instant.now().plusSeconds(durationSeconds).toEpochMilli()
        )
    }

    private fun serializeReminders(list: List<Reminder>): String {
        return list.joinToString(";") { "${it.id}|${it.label}|${it.durationSeconds}|${it.targetEpochMilli}" }
    }

    private fun parseReminders(data: String): List<Reminder> {
        return data.split(";").filter { it.isNotEmpty() }.map {
            val parts = it.split("|")
            Reminder(parts[0], parts[1], parts[2].toLong(), parts[3].toLong())
        }
    }
}

@Composable
fun ReminderList(
    reminders: List<Reminder>,
    onDelete: (String) -> Unit,
    onReset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("My Reminders", style = MaterialTheme.typography.headlineMedium)
        
        reminders.forEach { reminder ->
            ReminderCard(
                reminder = reminder,
                onDelete = { onDelete(reminder.id) },
                onReset = { onReset(reminder.id) }
            )
        }
    }
}

@Composable
fun ReminderCard(
    reminder: Reminder,
    onDelete: () -> Unit,
    onReset: () -> Unit
) {
    var currentTime by remember { mutableStateOf(Instant.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Instant.now()
            delay(1000)
        }
    }

    val targetTime = Instant.ofEpochMilli(reminder.targetEpochMilli)
    val remaining = Duration.between(currentTime, targetTime)
    val isExpired = remaining.isNegative

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = reminder.label, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (isExpired) "Time to ${reminder.label}!" else formatDuration(remaining),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isExpired) Color.Red else Color.Unspecified
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset Timer")
            }
        }
    }
}

@Composable
fun AddReminderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Duration) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("Days") }
    var expanded by remember { mutableStateOf(false) }
    val units = listOf("Days", "Hours", "Minutes")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Task (e.g., Hoover)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(unit)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            units.forEach { selection ->
                                DropdownMenuItem(
                                    text = { Text(selection) },
                                    onClick = {
                                        unit = selection
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = amount.toLongOrNull() ?: 0L
                    val duration = when (unit) {
                        "Days" -> Duration.ofDays(value)
                        "Hours" -> Duration.ofHours(value)
                        else -> Duration.ofMinutes(value)
                    }
                    if (label.isNotBlank() && value > 0) {
                        onConfirm(label, duration)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Helper function to format the duration into a readable string
fun formatDuration(duration: Duration): String {
    val totalSeconds = duration.abs().seconds
    val days = totalSeconds / 86400
    val hours = (totalSeconds / 3600) % 24
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    
    return buildString {
        if (days > 0) append("${days}d ")
        if (hours > 0 || (days > 0)) append("${hours}h ")
        append("${minutes}m ${seconds}s remaining")
    }
}

