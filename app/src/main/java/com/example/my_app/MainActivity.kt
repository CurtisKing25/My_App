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
import java.util.Locale
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
    val dayOfWeek: Int? = null, // 1 = Monday, ..., 7 = Sunday
    val targetHour: Int? = null,
    val targetMinute: Int? = null,
    val timerStartEpochMilli: Long? = null,
    val lastAttemptMillis: Long? = null,
    val longestAttemptMillis: Long? = null
)

enum class Screen { Home, Overdue, All, Settings }

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            My_AppTheme {
                var currentScreen by remember { mutableStateOf(Screen.Home) }
                var editingReminder by remember { mutableStateOf<Reminder?>(null) }
                var showAddDialog by remember { mutableStateOf(false) }
                var searchQuery by remember { mutableStateOf("") }
                var isSearchActive by remember { mutableStateOf(false) }
                
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
                                    if (isSearchActive) {
                                        TextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            placeholder = { Text("Filter tasks...") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                disabledContainerColor = Color.Transparent,
                                            )
                                        )
                                    } else {
                                        Text(
                                            when (currentScreen) {
                                                Screen.Home -> "Due Soon"
                                                Screen.Overdue -> "Overdue"
                                                else -> "All Reminders"
                                            }
                                        )
                                    }
                                },
                                actions = {
                                    if (isSearchActive) {
                                        IconButton(onClick = { 
                                            isSearchActive = false
                                            searchQuery = "" 
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close Search")
                                        }
                                    } else {
                                        IconButton(onClick = { isSearchActive = true }) {
                                            Icon(Icons.Default.Search, contentDescription = "Search")
                                        }
                                    }
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
                                    icon = { 
                                        BadgedBox(
                                            badge = {
                                                val overdueCount = reminders.count { 
                                                    Instant.ofEpochMilli(it.targetEpochMilli).isBefore(Instant.now())
                                                }
                                                if (overdueCount > 0) {
                                                    Badge { Text(overdueCount.toString()) }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Warning, contentDescription = "Overdue")
                                        }
                                    },
                                    label = { Text("Overdue") },
                                    selected = currentScreen == Screen.Overdue,
                                    onClick = { currentScreen = Screen.Overdue }
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
                            val baseFiltered = when (currentScreen) {
                                Screen.Home -> {
                                    reminders.filter { 
                                        val remaining = Duration.between(Instant.now(), Instant.ofEpochMilli(it.targetEpochMilli))
                                        remaining.toHours() < 24
                                    }.sortedBy { it.targetEpochMilli }
                                }
                                Screen.Overdue -> {
                                    reminders.filter { 
                                        Instant.ofEpochMilli(it.targetEpochMilli).isBefore(Instant.now())
                                    }.sortedBy { it.targetEpochMilli }
                                }
                                else -> reminders.sortedBy { it.targetEpochMilli }
                            }

                            val filteredReminders = applySearchFilter(baseFiltered, searchQuery)

                            ReminderList(
                                title = "", // Title moved to TopAppBar
                                reminders = filteredReminders,
                                showCapacityMeter = currentScreen == Screen.Home,
                                allReminders = reminders, // Pass all for capacity calculation
                                onDelete = { id -> reminders = reminders.filter { it.id != id } },
                                onEdit = { reminder -> editingReminder = reminder },
                                onToggleTimer = { id ->
                                    reminders = reminders.map { 
                                        if (it.id == id) {
                                            if (it.timerStartEpochMilli == null) {
                                                it.copy(timerStartEpochMilli = Instant.now().toEpochMilli())
                                            } else {
                                                val elapsed = Instant.now().toEpochMilli() - it.timerStartEpochMilli
                                                it.copy(
                                                    timerStartEpochMilli = null,
                                                    lastAttemptMillis = elapsed,
                                                    longestAttemptMillis = maxOf(elapsed, it.longestAttemptMillis ?: 0L)
                                                )
                                            }
                                        } else it
                                    }
                                },
                                onReset = { id -> 
                                    reminders = reminders.mapNotNull { 
                                        if (it.id == id) {
                                            val now = Instant.now().toEpochMilli()
                                            var updated = it
                                            if (it.timerStartEpochMilli != null) {
                                                val elapsed = now - it.timerStartEpochMilli
                                                updated = it.copy(
                                                    timerStartEpochMilli = null,
                                                    lastAttemptMillis = elapsed,
                                                    longestAttemptMillis = maxOf(elapsed, it.longestAttemptMillis ?: 0L)
                                                )
                                            }

                                            if (updated.isRecurring) {
                                                val nextTarget = calculateNextOccurrence(updated)
                                                updated.copy(targetEpochMilli = nextTarget)
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

                    if (showAddDialog || editingReminder != null) {
                        AddReminderDialog(
                            initialReminder = editingReminder,
                            onDismiss = { 
                                showAddDialog = false
                                editingReminder = null
                            },
                            onConfirm = { label, isRecurring, value, unit, dow, targetTime, fixedTarget ->
                                val target = fixedTarget ?: run {
                                    val now = ZonedDateTime.now()
                                    var initial = now
                                    if (isRecurring && unit == "Weeks" && dow != null) {
                                        initial = now.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.of(dow)))
                                        if (initial.isBefore(now)) initial = initial.plusWeeks(1)
                                    }
                                    
                                    if (targetTime != null) {
                                        initial = initial.withHour(targetTime.hour).withMinute(targetTime.minute).withSecond(0)
                                        if (initial.isBefore(now)) {
                                            initial = when (unit) {
                                                "Days" -> initial.plusDays(1)
                                                "Weeks" -> initial.plusWeeks(1)
                                                "Months" -> initial.plusMonths(1)
                                                else -> initial
                                            }
                                        }
                                    }
                                    initial.toInstant().toEpochMilli()
                                }

                                val updatedReminder = if (editingReminder != null) {
                                    editingReminder!!.copy(
                                        label = label,
                                        isRecurring = isRecurring,
                                        recurrenceValue = value,
                                        recurrenceUnit = unit,
                                        dayOfWeek = dow,
                                        targetHour = targetTime?.hour,
                                        targetMinute = targetTime?.minute,
                                        targetEpochMilli = target
                                    )
                                } else {
                                    createReminder(
                                        label, isRecurring, value, unit, dow, 
                                        targetTime?.hour, targetTime?.minute, target
                                    )
                                }

                                if (editingReminder != null) {
                                    reminders = reminders.map { if (it.id == updatedReminder.id) updatedReminder else it }
                                } else {
                                    reminders = reminders + updatedReminder
                                }
                                
                                showAddDialog = false
                                editingReminder = null
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
        
        var next = currentTarget
        val value = reminder.recurrenceValue.toLong()

        do {
            next = when (reminder.recurrenceUnit) {
                "Minutes" -> next.plusMinutes(value)
                "Hours" -> next.plusHours(value)
                "Days" -> next.plusDays(value)
                "Weeks" -> next.plusWeeks(value)
                "Months" -> next.plusMonths(value)
                else -> next.plusDays(value)
            }
            
            // Re-apply the time constraint if it exists
            if (reminder.targetHour != null && reminder.targetMinute != null) {
                next = next.withHour(reminder.targetHour).withMinute(reminder.targetMinute).withSecond(0)
            }
        } while (!next.isAfter(now))

        return next.toInstant().toEpochMilli()
    }

    private fun createReminder(
        label: String, 
        isRecurring: Boolean, 
        value: Int, 
        unit: String, 
        dow: Int?, 
        hour: Int?,
        minute: Int?,
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
            dayOfWeek = dow,
            targetHour = hour,
            targetMinute = minute,
            timerStartEpochMilli = null,
            lastAttemptMillis = null,
            longestAttemptMillis = null
        )
    }

    private fun applySearchFilter(reminders: List<Reminder>, query: String): List<Reminder> {
        if (query.isBlank()) return reminders

        val trimmedQuery = query.trim()

        // 1. Exact Substring Match (contains)
        val exactMatches = reminders.filter { it.label.contains(trimmedQuery, ignoreCase = true) }
        if (exactMatches.isNotEmpty()) return exactMatches

        // 2. Fuzzy Match (characters appear in order)
        return reminders.filter { fuzzyMatch(it.label, trimmedQuery) }
    }

    private fun fuzzyMatch(text: String, query: String): Boolean {
        var tIdx = 0
        var qIdx = 0
        val t = text.lowercase()
        val q = query.lowercase()
        while (tIdx < t.length && qIdx < q.length) {
            if (t[tIdx] == q[qIdx]) {
                qIdx++
            }
            tIdx++
        }
        return qIdx == q.length
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
    modifier: Modifier = Modifier,
    showCapacityMeter: Boolean = false,
    allReminders: List<Reminder> = emptyList(),
    onDelete: (String) -> Unit,
    onEdit: (Reminder) -> Unit,
    onToggleTimer: (String) -> Unit,
    onReset: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (title.isNotEmpty()) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
        }

        if (showCapacityMeter) {
            CapacityMeter(allReminders)
        }
        
        reminders.forEach { reminder ->
            ReminderCard(
                reminder = reminder,
                onDelete = { onDelete(reminder.id) },
                onEdit = { onEdit(reminder) },
                onToggleTimer = { onToggleTimer(reminder.id) },
                onReset = { onReset(reminder.id) }
            )
        }
    }
}

@Composable
fun CapacityMeter(reminders: List<Reminder>) {
    val endOfDay = ZonedDateTime.now().with(LocalTime.MAX).toInstant().toEpochMilli()
    
    val tasksToday = reminders.filter {
        it.targetEpochMilli <= endOfDay
    }
    
    val usedMillis = tasksToday.sumOf { 
        it.longestAttemptMillis ?: (15 * 60 * 1000L) // Default 15 mins if no data
    }
    
    val totalAvailableMillis = 16 * 60 * 60 * 1000L // 16 productive hours
    val progress = (usedMillis.toFloat() / totalAvailableMillis).coerceIn(0f, 1f)
    val freeMillis = maxOf(0, totalAvailableMillis - usedMillis)
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Free Time Today",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    formatShortDuration(freeMillis),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (freeMillis < 3600000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (progress > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Used ${formatShortDuration(usedMillis)} of 16h capacity",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun ReminderCard(
    reminder: Reminder,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onToggleTimer: () -> Unit,
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
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (reminder.isRecurring) Icons.Default.Refresh else Icons.Default.Event,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = reminder.label, style = MaterialTheme.typography.titleLarge)
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (isExpired) "Overdue by ${formatDuration(remaining, true)}" else formatDuration(remaining, false),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isExpired) Color.Red else Color.Unspecified
            )

            // Timer / Stopwatch UI for recurring tasks
            if (reminder.isRecurring) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Show statistics if they exist
                if (reminder.lastAttemptMillis != null || reminder.longestAttemptMillis != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        reminder.lastAttemptMillis?.let {
                            Text(
                                "Last: ${formatStopwatchDuration(it)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        reminder.longestAttemptMillis?.let {
                            Text(
                                "Longest: ${formatStopwatchDuration(it)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onToggleTimer,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (reminder.timerStartEpochMilli != null) 
                                MaterialTheme.colorScheme.errorContainer 
                            else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (reminder.timerStartEpochMilli != null)
                                MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        val isRunning = reminder.timerStartEpochMilli != null
                        Icon(
                            if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isRunning) "Stop" else "Start Timer")
                    }

                    if (reminder.timerStartEpochMilli != null) {
                        val elapsed = currentTime.toEpochMilli() - reminder.timerStartEpochMilli
                        Text(
                            text = formatStopwatchDuration(elapsed),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
            
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
    initialReminder: Reminder? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean, Int, String, Int?, LocalTime?, Long?) -> Unit
) {
    var label by remember { mutableStateOf(initialReminder?.label ?: "") }
    var selectedTab by remember { mutableIntStateOf(if (initialReminder?.isRecurring == false) 1 else 0) }
    
    // Recurring state
    var amount by remember { mutableStateOf(initialReminder?.recurrenceValue?.toString() ?: "") }
    var unit by remember { mutableStateOf(initialReminder?.recurrenceUnit ?: "Days") }
    var selectedDow by remember { mutableStateOf<Int?>(initialReminder?.dayOfWeek) }
    var expanded by remember { mutableStateOf(false) }
    var showRecurringTimePicker by remember { mutableStateOf(false) }
    var recurringTime by remember { 
        mutableStateOf<LocalTime?>(
            if (initialReminder?.targetHour != null) 
                LocalTime.of(initialReminder.targetHour, initialReminder.targetMinute ?: 0)
            else null
        ) 
    }
    
    // Due Date state
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (initialReminder?.isRecurring == false) initialReminder.targetEpochMilli else null
    )
    val timePickerState = rememberTimePickerState(
        initialHour = if (initialReminder?.isRecurring == false) {
            Instant.ofEpochMilli(initialReminder.targetEpochMilli).atZone(ZoneId.systemDefault()).hour
        } else 0,
        initialMinute = if (initialReminder?.isRecurring == false) {
            Instant.ofEpochMilli(initialReminder.targetEpochMilli).atZone(ZoneId.systemDefault()).minute
        } else 0
    )
    
    var selectedDateMillis by remember { 
        mutableStateOf<Long?>(if (initialReminder?.isRecurring == false) initialReminder.targetEpochMilli else null) 
    }
    var selectedTime by remember { 
        mutableStateOf<LocalTime?>(
            if (initialReminder?.isRecurring == false) 
                Instant.ofEpochMilli(initialReminder.targetEpochMilli).atZone(ZoneId.systemDefault()).toLocalTime()
            else null
        ) 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialReminder == null) "New Reminder" else "Edit Reminder") },
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

                        // Time constraint for recurring tasks
                        OutlinedButton(
                            onClick = { showRecurringTimePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val timeText = recurringTime?.let { 
                                it.format(DateTimeFormatter.ofPattern("hh:mm a"))
                            } ?: "Set Preferred Time (Optional)"
                            Text(timeText)
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
                            onConfirm(label, true, value, unit, selectedDow, recurringTime, null)
                        }
                    } else {
                        val date = selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        val time = selectedTime ?: LocalTime.MIDNIGHT
                        if (date != null) {
                            val target = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            onConfirm(label, false, 0, "", null, time, target)
                        }
                    }
                }
            ) {
                Text(if (initialReminder == null) "Add" else "Save")
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

    // Reuse existing time picker logic for both tabs
    if (showTimePicker || showRecurringTimePicker) {
        val activePickerState = rememberTimePickerState()
        AlertDialog(
            onDismissRequest = { 
                showTimePicker = false
                showRecurringTimePicker = false
            },
            confirmButton = {
                TextButton(onClick = {
                    val time = LocalTime.of(activePickerState.hour, activePickerState.minute)
                    if (showTimePicker) selectedTime = time
                    if (showRecurringTimePicker) recurringTime = time
                    showTimePicker = false
                    showRecurringTimePicker = false
                }) { Text("OK") }
            },
            text = { TimePicker(state = activePickerState) }
        )
    }
}

// Helper function to format the duration into a readable string
fun formatDuration(duration: Duration, isOverdue: Boolean): String {
    val totalSeconds = duration.abs().seconds
    val days = totalSeconds / 86400
    val hours = (totalSeconds / 3600) % 24
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    
    return buildString {
        if (days > 0) append("${days}d ")
        if (hours > 0 || (days > 0)) append("${hours}h ")
        append("${minutes}m ${seconds}s")
        if (!isOverdue) append(" remaining")
    }
}

fun formatStopwatchDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60))
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

fun formatShortDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds / 60) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

