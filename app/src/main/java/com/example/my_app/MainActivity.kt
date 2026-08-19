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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
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
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

data class Reminder(
    val id: String,
    val label: String,
    val durationSeconds: Long,
    val targetEpochMilli: Long,
    val isRecurring: Boolean
)

enum class Screen { Home, All }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            My_AppTheme {
                var currentScreen by remember { mutableStateOf(Screen.Home) }
                var showAddDialog by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val prefs = remember { context.getSharedPreferences("reminder_list_prefs", Context.MODE_PRIVATE) }
                
                // Initialize reminders from persistent storage
                var reminders by remember {
                    val savedString = prefs.getString("reminders", "") ?: ""
                    val initialList = if (savedString.isEmpty()) {
                        emptyList()
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
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                label = { Text("Home") },
                                selected = currentScreen == Screen.Home,
                                onClick = { currentScreen = Screen.Home }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.List, contentDescription = "All") },
                                label = { Text("All") },
                                selected = currentScreen == Screen.All,
                                onClick = { currentScreen = Screen.All }
                            )
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Reminder")
                        }
                    }
                ) { innerPadding ->
                    val filteredReminders = if (currentScreen == Screen.Home) {
                        reminders.filter { 
                            val remaining = Duration.between(Instant.now(), Instant.ofEpochMilli(it.targetEpochMilli))
                            remaining.toHours() < 24
                        }
                    } else {
                        reminders
                    }

                    ReminderList(
                        title = if (currentScreen == Screen.Home) "Due Soon (<24h)" else "All Reminders",
                        reminders = filteredReminders,
                        onDelete = { id -> reminders = reminders.filter { it.id != id } },
                        onReset = { id -> 
                            reminders = reminders.mapNotNull { 
                                if (it.id == id) {
                                    if (it.isRecurring) {
                                        it.copy(targetEpochMilli = Instant.now().plusSeconds(it.durationSeconds).toEpochMilli())
                                    } else {
                                        null // One-time tasks are removed when "Done"
                                    }
                                } else it
                            }
                        },
                        modifier = Modifier.padding(innerPadding)
                    )

                    if (showAddDialog) {
                        AddReminderDialog(
                            onDismiss = { showAddDialog = false },
                            onConfirm = { label, duration, isRecurring, fixedTarget ->
                                val target = fixedTarget ?: Instant.now().plusSeconds(duration.seconds).toEpochMilli()
                                reminders = reminders + createReminder(label, duration.seconds, isRecurring, target)
                                showAddDialog = false
                            }
                        )
                    }
                }
            }
        }
    }

    private fun createReminder(label: String, durationSeconds: Long, isRecurring: Boolean, target: Long): Reminder {
        return Reminder(
            id = UUID.randomUUID().toString(),
            label = label,
            durationSeconds = durationSeconds,
            targetEpochMilli = target,
            isRecurring = isRecurring
        )
    }

    private fun serializeReminders(list: List<Reminder>): String {
        return list.joinToString(";") { "${it.id}|${it.label}|${it.durationSeconds}|${it.targetEpochMilli}|${it.isRecurring}" }
    }

    private fun parseReminders(data: String): List<Reminder> {
        return data.split(";").filter { it.isNotEmpty() }.map {
            val parts = it.split("|")
            Reminder(parts[0], parts[1], parts[2].toLong(), parts[3].toLong(), parts[4].toBoolean())
        }
    }
}

@Composable
fun ReminderList(
    title: String,
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
        Text(title, style = MaterialTheme.typography.headlineMedium)
        
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (reminder.isRecurring) Icons.Default.Refresh else Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = reminder.label, style = MaterialTheme.typography.titleLarge)
                }
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
                Text(if (reminder.isRecurring) "Reset Timer" else "Mark Done")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Duration, Boolean, Long?) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Recurring, 1 = Due Date
    
    // Recurring state
    var amount by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("Days") }
    var expanded by remember { mutableStateOf(false) }
    
    // Due Date state
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()
    
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var selectedTime by remember { mutableStateOf<LocalTime?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Task Label") },
                    modifier = Modifier.fillMaxWidth()
                )

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        PaddingValues(8.dp)
                        Text("Recurring", modifier = Modifier.padding(8.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("Due Date", modifier = Modifier.padding(8.dp))
                    }
                }

                if (selectedTab == 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = { Text("Every") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(unit)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                listOf("Days", "Hours", "Minutes").forEach { selection ->
                                    DropdownMenuItem(
                                        text = { Text(selection) },
                                        onClick = { unit = selection; expanded = false }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val dateText = selectedDateMillis?.let {
                                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                            } ?: "Select Date"
                            Text(dateText)
                        }
                        OutlinedButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val timeText = selectedTime?.toString() ?: "Select Time"
                            Text(timeText)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (label.isBlank()) return@TextButton
                    
                    if (selectedTab == 0) {
                        val value = amount.toLongOrNull() ?: 0L
                        if (value > 0) {
                            val duration = when (unit) {
                                "Days" -> Duration.ofDays(value)
                                "Hours" -> Duration.ofHours(value)
                                else -> Duration.ofMinutes(value)
                            }
                            onConfirm(label, duration, true, null)
                        }
                    } else {
                        val date = selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        val time = selectedTime ?: LocalTime.MIDNIGHT
                        if (date != null) {
                            val target = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            onConfirm(label, Duration.ZERO, false, target)
                        }
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }
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

