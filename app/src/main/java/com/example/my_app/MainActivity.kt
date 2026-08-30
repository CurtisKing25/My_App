package com.example.my_app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.*
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
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

@Serializable
data class Reminder(
    val id: String,
    val label: String,
    val durationSeconds: Long,
    val targetEpochMilli: Long,
    val isRecurring: Boolean,
    val recurrenceValue: Int = 1,
    val recurrenceUnit: String = "Days",
    val dayOfWeek: Int? = null // 1 = Monday, ..., 7 = Sunday
)

enum class Screen { Home, All, Settings }

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            My_AppTheme {
                var currentScreen by remember { mutableStateOf(Screen.Home) }
                var showAddDialog by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }
                val prefs = remember { context.getSharedPreferences("reminder_list_prefs", Context.MODE_PRIVATE) }
                
                // Initialize reminders from persistent storage (JSON)
                var reminders by remember {
                    val savedJson = prefs.getString("reminders_json", "") ?: ""
                    val initialList = try {
                        if (savedJson.isEmpty()) emptyList() else Json.decodeFromString<List<Reminder>>(savedJson)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    mutableStateOf(initialList)
                }

                // File Picker Launchers
                val exportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    uri?.let {
                        val jsonString = Json.encodeToString(reminders)
                        context.contentResolver.openOutputStream(it)?.use { stream ->
                            stream.write(jsonString.toByteArray())
                        }
                        scope.launch {
                            snackbarHostState.showSnackbar("Data exported successfully")
                        }
                    }
                }

                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        context.contentResolver.openInputStream(it)?.use { stream ->
                            val jsonString = stream.bufferedReader().readText()
                            try {
                                val importedList = Json.decodeFromString<List<Reminder>>(jsonString)
                                reminders = importedList
                                scope.launch {
                                    snackbarHostState.showSnackbar("Data imported successfully")
                                }
                            } catch (e: Exception) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Import failed: Invalid file format")
                                }
                            }
                        }
                    }
                }

                // Save reminders whenever the list changes
                LaunchedEffect(reminders) {
                    prefs.edit { putString("reminders_json", Json.encodeToString(reminders)) }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        if (currentScreen != Screen.Settings) {
                            TopAppBar(
                                title = { 
                                    Text(if (currentScreen == Screen.Home) "Due Soon" else "All Reminders") 
                                },
                                actions = {
                                    IconButton(onClick = { currentScreen = Screen.Settings }) {
                                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                                    }
                                }
                            )
                        } else {
                            TopAppBar(
                                title = { Text("Settings") },
                                navigationIcon = {
                                    IconButton(onClick = { currentScreen = Screen.Home }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            )
                        }
                    },
                    bottomBar = {
                        if (currentScreen != Screen.Settings) {
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
                        }
                    },
                    floatingActionButton = {
                        if (currentScreen != Screen.Settings) {
                            FloatingActionButton(onClick = { showAddDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Reminder")
                            }
                        }
                    }
                ) { innerPadding ->
                    when (currentScreen) {
                        Screen.Settings -> {
                            SettingsScreen(
                                onExport = { exportLauncher.launch("reminders_backup.json") },
                                onImport = { importLauncher.launch(arrayOf("application/json", "application/octet-stream")) },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        else -> {
                            val filteredReminders = if (currentScreen == Screen.Home) {
                                reminders.filter { 
                                    val remaining = Duration.between(Instant.now(), Instant.ofEpochMilli(it.targetEpochMilli))
                                    remaining.toHours() < 24
                                }.sortedBy { it.targetEpochMilli }
                            } else {
                                reminders.sortedBy { it.targetEpochMilli }
                            }

                            ReminderList(
                                title = "", // Title moved to TopAppBar
                                reminders = filteredReminders,
                                onDelete = { id -> reminders = reminders.filter { it.id != id } },
                                onReset = { id -> 
                                    reminders = reminders.mapNotNull { 
                                        if (it.id == id) {
                                            if (it.isRecurring) {
                                                val nextTarget = calculateNextOccurrence(it)
                                                it.copy(targetEpochMilli = nextTarget)
                                            } else {
                                                null 
                                            }
                                        } else it
                                    }
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }

                    if (showAddDialog) {
                        AddReminderDialog(
                            onDismiss = { showAddDialog = false },
                            onConfirm = { label, isRecurring, value, unit, dow, fixedTarget ->
                                val target = fixedTarget ?: run {
                                    val now = ZonedDateTime.now()
                                    if (isRecurring && unit == "Weeks" && dow != null) {
                                        var next = now.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.of(dow)))
                                        if (next.isBefore(now)) next = next.plusWeeks(1)
                                        next.toInstant().toEpochMilli()
                                    } else if (isRecurring && unit == "Months") {
                                        now.plusMonths(value.toLong()).toInstant().toEpochMilli()
                                    } else {
                                        now.toInstant().toEpochMilli()
                                    }
                                }
                                reminders = reminders + createReminder(label, isRecurring, value, unit, dow, target)
                                showAddDialog = false
                            }
                        )
                    }
                }
            }
        }
    }

    private fun calculateNextOccurrence(reminder: Reminder): Long {
        val now = ZonedDateTime.now()
        val currentTarget = Instant.ofEpochMilli(reminder.targetEpochMilli).atZone(ZoneId.systemDefault())
        val base = if (currentTarget.isBefore(now)) now else currentTarget

        return when (reminder.recurrenceUnit) {
            "Minutes" -> base.plusMinutes(reminder.recurrenceValue.toLong())
            "Hours" -> base.plusHours(reminder.recurrenceValue.toLong())
            "Days" -> base.plusDays(reminder.recurrenceValue.toLong())
            "Weeks" -> base.plusWeeks(reminder.recurrenceValue.toLong())
            "Months" -> base.plusMonths(reminder.recurrenceValue.toLong())
            else -> base.plusDays(reminder.recurrenceValue.toLong())
        }.toInstant().toEpochMilli()
    }

    private fun createReminder(
        label: String, 
        isRecurring: Boolean, 
        value: Int, 
        unit: String, 
        dow: Int?, 
        target: Long
    ): Reminder {
        return Reminder(
            id = UUID.randomUUID().toString(),
            label = label,
            durationSeconds = 0,
            targetEpochMilli = target,
            isRecurring = isRecurring,
            recurrenceValue = value,
            recurrenceUnit = unit,
            dayOfWeek = dow
        )
    }
}

@Composable
fun SettingsScreen(
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Data Management", style = MaterialTheme.typography.titleLarge)
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Export Data",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Save all your tasks to a JSON file on your device. You can choose any folder in the next step.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Backup to JSON")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Import Data",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Load tasks from a previously exported JSON file. This will replace your current list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Restore from JSON")
                }
            }
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
                        imageVector = if (reminder.isRecurring) Icons.Default.Refresh else Icons.Default.Event,
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
    onConfirm: (String, Boolean, Int, String, Int?, Long?) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Recurring, 1 = Due Date
    
    // Recurring state
    var amount by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("Days") }
    var selectedDow by remember { mutableStateOf<Int?>(null) }
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
                        Text("Recurring", modifier = Modifier.padding(8.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("Due Date", modifier = Modifier.padding(8.dp))
                    }
                }

                if (selectedTab == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    listOf("Minutes", "Hours", "Days", "Weeks", "Months").forEach { selection ->
                                        DropdownMenuItem(
                                            text = { Text(selection) },
                                            onClick = { unit = selection; expanded = false }
                                        )
                                    }
                                }
                            }
                        }

                        if (unit == "Weeks") {
                            Text("On:", style = MaterialTheme.typography.labelMedium)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { index, day ->
                                    val dow = index + 1
                                    FilterChip(
                                        selected = selectedDow == dow,
                                        onClick = { selectedDow = dow },
                                        label = { Text(day) }
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
                        val value = amount.toIntOrNull() ?: 0
                        if (value > 0) {
                            onConfirm(label, true, value, unit, selectedDow, null)
                        }
                    } else {
                        val date = selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        val time = selectedTime ?: LocalTime.MIDNIGHT
                        if (date != null) {
                            val target = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            onConfirm(label, false, 0, "", null, target)
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

