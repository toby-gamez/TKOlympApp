package com.tkolymp.tkolympapp.screens

// rememberDatePickerState imported above
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.personalevents.PersonalEvent
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalEventEditScreen(eventId: String? = null, onSaved: () -> Unit = {}, bottomPadding: Dp = 0.dp) {
    val service = ServiceLocator.personalEventService
    val title = remember(eventId) { mutableStateOf(if (eventId.isNullOrBlank()) AppStrings.current.personalEvents.defaultTitle else "") }
    val startIso = remember { mutableStateOf(kotlin.time.Clock.System.now().toString()) }
    val endIso = remember { mutableStateOf(kotlin.time.Clock.System.now().plus(1.hours).toString()) }
    val isRecurring = remember { mutableStateOf(false) }
    val weekday = remember { mutableStateOf<Int?>(null) }
    val recurrenceStartIso = remember { mutableStateOf("") }
    val recurrenceEndIso = remember { mutableStateOf("") }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showRecurrenceStartDatePicker by remember { mutableStateOf(false) }
    var showRecurrenceEndDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(eventId) {
        if (!eventId.isNullOrBlank()) {
            val ev = service.getAll().find { it.id == eventId }
            if (ev != null) {
                title.value = ev.title
                startIso.value = ev.startIso
                endIso.value = ev.endIso
                weekday.value = ev.recurrenceDayOfWeek
                recurrenceStartIso.value = ev.recurrenceStartIso ?: ""
                recurrenceEndIso.value = ev.recurrenceEndIso ?: ""
                isRecurring.value = ev.recurrenceDayOfWeek != null
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(if (eventId.isNullOrBlank()) AppStrings.current.personalEvents.newTraining else AppStrings.current.personalEvents.editTraining) }) }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(12.dp)) {
            OutlinedTextField(value = title.value, onValueChange = { title.value = it }, label = { Text(AppStrings.current.personalEvents.trainingTitle) }, modifier = Modifier.fillMaxWidth())
            // Start time (time picker)
            val focusManager = LocalFocusManager.current
            Row(modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                OutlinedTextField(
                    value = try { Instant.parse(startIso.value).toLocalDateTime(TimeZone.currentSystemDefault()).time.toString() } catch (_: Exception) { "" },
                    onValueChange = {},
                    label = { Text(AppStrings.current.personalEvents.startTime) },
                    modifier = Modifier.fillMaxWidth(0.85f),
                    readOnly = true
                )
                TextButton(onClick = { focusManager.clearFocus(); showStartTimePicker = true }) { Text("...") }
            }
            if (showStartTimePicker) {
                val t = try { Instant.parse(startIso.value).toLocalDateTime(TimeZone.currentSystemDefault()).time } catch (_: Exception) { LocalTime(0,0) }
                val timeState = rememberTimePickerState(initialHour = t.hour, initialMinute = t.minute)
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showStartTimePicker = false },
                    confirmButton = {
                        Button(onClick = {
                            val h = timeState.hour; val m = timeState.minute
                            val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
                            val iso = LocalDateTime(today, LocalTime(h,m)).toInstant(TimeZone.currentSystemDefault()).toString()
                            startIso.value = iso
                            showStartTimePicker = false
                        }) { Text(AppStrings.current.ok) }
                    },
                    text = { TimePicker(state = timeState) }
                )
            }

            // End time (time picker)
            Row(modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                OutlinedTextField(
                    value = try { Instant.parse(endIso.value).toLocalDateTime(TimeZone.currentSystemDefault()).time.toString() } catch (_: Exception) { "" },
                    onValueChange = {},
                    label = { Text(AppStrings.current.personalEvents.endTime) },
                    modifier = Modifier.fillMaxWidth(0.85f),
                    readOnly = true
                )
                TextButton(onClick = { focusManager.clearFocus(); showEndTimePicker = true }) { Text("...") }
            }
            if (showEndTimePicker) {
                val t2 = try { Instant.parse(endIso.value).toLocalDateTime(TimeZone.currentSystemDefault()).time } catch (_: Exception) { LocalTime(0,0) }
                val timeState2 = rememberTimePickerState(initialHour = t2.hour, initialMinute = t2.minute)
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showEndTimePicker = false },
                    confirmButton = {
                        Button(onClick = {
                            val h = timeState2.hour; val m = timeState2.minute
                            val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
                            val iso = LocalDateTime(today, LocalTime(h,m)).toInstant(TimeZone.currentSystemDefault()).toString()
                            endIso.value = iso
                            showEndTimePicker = false
                        }) { Text(AppStrings.current.ok) }
                    },
                    text = { TimePicker(state = timeState2) }
                )
            }

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                Checkbox(checked = isRecurring.value, onCheckedChange = { isRecurring.value = it })
                Text(AppStrings.current.personalEvents.repeatWeekly, modifier = Modifier.padding(start = 8.dp))
            }

            if (isRecurring.value) {
                Text(AppStrings.current.personalEvents.weekday, modifier = Modifier.padding(top = 8.dp))
                val dayNames = listOf("Po","Út","St","Čt","Pá","So","Ne")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    dayNames.forEachIndexed { idx, d ->
                        TextButton(onClick = { weekday.value = idx + 1 }) {
                            Text(text = if (weekday.value == idx + 1) "$d ✓" else d)
                        }
                    }
                }

                // recurrence start date (date picker)
                val recStartDate = try { if (recurrenceStartIso.value.isBlank()) null else Instant.parse(recurrenceStartIso.value).toLocalDateTime(TimeZone.currentSystemDefault()).date } catch (_: Exception) { null }
                val recStartMillis = recStartDate?.let { LocalDateTime(it, LocalTime(0,0)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds() }
                val recStartState = rememberDatePickerState(initialSelectedDateMillis = recStartMillis)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(value = recStartDate?.toString() ?: "", onValueChange = {}, label = { Text(AppStrings.current.personalEvents.recurrenceStart) }, modifier = Modifier.fillMaxWidth(0.85f), readOnly = true)
                    IconButton(onClick = { focusManager.clearFocus(); showRecurrenceStartDatePicker = true }) { Icon(imageVector = Icons.Default.CalendarToday, contentDescription = AppStrings.current.selectDate) }
                }
                if (showRecurrenceStartDatePicker) {
                    DatePickerDialog(onDismissRequest = { showRecurrenceStartDatePicker = false }, confirmButton = {
                        Button(onClick = {
                            val selMillis = recStartState.selectedDateMillis
                            if (selMillis != null) {
                                val sel = Instant.fromEpochMilliseconds(selMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date
                                recurrenceStartIso.value = sel.toString()
                            }
                            showRecurrenceStartDatePicker = false
                        }) { Text(AppStrings.current.ok) }
                    }) { DatePicker(state = recStartState) }
                    }

                // recurrence end date (date picker)
                val recEndDate = try { if (recurrenceEndIso.value.isBlank()) null else Instant.parse(recurrenceEndIso.value).toLocalDateTime(TimeZone.currentSystemDefault()).date } catch (_: Exception) { null }
                val recEndMillis = recEndDate?.let { LocalDateTime(it, LocalTime(0,0)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds() }
                val recEndState = rememberDatePickerState(initialSelectedDateMillis = recEndMillis)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(value = recEndDate?.toString() ?: "", onValueChange = {}, label = { Text(AppStrings.current.personalEvents.recurrenceEnd) }, modifier = Modifier.fillMaxWidth(0.85f), readOnly = true)
                    IconButton(onClick = { focusManager.clearFocus(); showRecurrenceEndDatePicker = true }) { Icon(imageVector = Icons.Default.CalendarToday, contentDescription = AppStrings.current.selectDate) }
                }
                if (showRecurrenceEndDatePicker) {
                    DatePickerDialog(onDismissRequest = { showRecurrenceEndDatePicker = false }, confirmButton = {
                        Button(onClick = {
                            val selMillis = recEndState.selectedDateMillis
                            if (selMillis != null) {
                                val sel = Instant.fromEpochMilliseconds(selMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date
                                recurrenceEndIso.value = sel.toString()
                            }
                            showRecurrenceEndDatePicker = false
                        }) { Text(AppStrings.current.ok) }
                    }) { DatePicker(state = recEndState) }
                }
            }

            Button(onClick = {
                val id = eventId ?: (kotlin.time.Clock.System.now().toEpochMilliseconds().toString() + "_${(0..Int.MAX_VALUE).random()}")
                val ev = PersonalEvent(
                    id = id,
                    title = title.value,
                    startIso = startIso.value,
                    endIso = endIso.value,
                    recurrenceDayOfWeek = if (isRecurring.value) weekday.value else null,
                    recurrenceStartIso = recurrenceStartIso.value.ifBlank { null },
                    recurrenceEndIso = recurrenceEndIso.value.ifBlank { null }
                )
                // save
                kotlinx.coroutines.runBlocking { kotlin.runCatching { service.save(ev) } }
                onSaved()
            }, modifier = Modifier.padding(top = 12.dp)) {
                Text(AppStrings.current.personalEvents.saveTraining)
            }
        }
    }
}
