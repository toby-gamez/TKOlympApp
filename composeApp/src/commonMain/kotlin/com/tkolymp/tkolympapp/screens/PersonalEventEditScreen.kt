package com.tkolymp.tkolympapp.screens

// rememberDatePickerState imported above
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.personalevents.PersonalEvent
import com.tkolymp.shared.personalevents.TrainingType
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.launch

private fun formatLocalDate(d: kotlinx.datetime.LocalDate?): String {
    return d?.let { "${it.dayOfMonth}. ${it.monthNumber}. ${it.year}" } ?: ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalEventEditScreen(eventId: String? = null, onSaved: () -> Unit = {}, onBack: () -> Unit = {}, bottomPadding: Dp = 0.dp) {
    val service = ServiceLocator.personalEventService
    val title = remember(eventId) { mutableStateOf(if (eventId.isNullOrBlank()) AppStrings.current.personalEvents.defaultTitle else "") }
    val startIso = remember { mutableStateOf(kotlin.time.Clock.System.now().toString()) }
    val endIso = remember { mutableStateOf(kotlin.time.Clock.System.now().plus(1.hours).toString()) }
    val trainingType = remember { mutableStateOf(TrainingType.GENERAL) }
    val isRecurring = remember { mutableStateOf(true) }
    val weekday = remember { mutableStateOf<Int?>(null) }
    val recurrenceStartIso = remember { mutableStateOf("") }
    val recurrenceEndIso = remember { mutableStateOf("") }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showRecurrenceStartDatePicker by remember { mutableStateOf(false) }
    var showRecurrenceEndDatePicker by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(eventId) {
        if (!eventId.isNullOrBlank()) {
            val ev = service.getAll().find { it.id == eventId }
            if (ev != null) {
                title.value = ev.title
                trainingType.value = ev.type
                startIso.value = ev.startIso
                endIso.value = ev.endIso
                weekday.value = ev.recurrenceDayOfWeek
                recurrenceStartIso.value = ev.recurrenceStartIso ?: ""
                recurrenceEndIso.value = ev.recurrenceEndIso ?: ""
                isRecurring.value = ev.recurrenceDayOfWeek != null
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (eventId.isNullOrBlank()) AppStrings.current.personalEvents.newTraining else AppStrings.current.personalEvents.editTraining) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null) } }
        )
    }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val focusManager = LocalFocusManager.current
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title.value,
                        onValueChange = { title.value = it },
                        label = { Text(AppStrings.current.personalEvents.trainingTitle) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(imageVector = Icons.Default.Event, contentDescription = null) }
                    )

                    Text(AppStrings.current.personalEvents.trainingTypeLabel, style = MaterialTheme.typography.bodyMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val types = listOf(TrainingType.STT, TrainingType.LAT, TrainingType.GENERAL)
                        val labels = listOf(
                            AppStrings.current.personalEvents.trainingTypeSTT,
                            AppStrings.current.personalEvents.trainingTypeLAT,
                            AppStrings.current.personalEvents.trainingTypeGeneral
                        )
                        types.forEachIndexed { index, t ->
                            SegmentedButton(
                                selected = (trainingType.value == t),
                                onClick = { trainingType.value = t },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                                icon = {}
                            ) { Text(labels[index], style = MaterialTheme.typography.labelSmall) }
                        }
                    }

                    OutlinedTextField(
                        value = try {
                            val tm = Instant.parse(startIso.value).toLocalDateTime(TimeZone.currentSystemDefault()).time
                            "%02d:%02d".format(tm.hour, tm.minute)
                        } catch (_: Exception) { "" },
                        onValueChange = {},
                        label = { Text(AppStrings.current.personalEvents.startTime) },
                        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) { focusManager.clearFocus(); showStartTimePicker = true } },
                        readOnly = true,
                        leadingIcon = { Icon(imageVector = Icons.Default.AccessTime, contentDescription = null) }
                    )
                    if (showStartTimePicker) {
                        val t = try { kotlin.time.Instant.parse(startIso.value).toLocalDateTime(TimeZone.currentSystemDefault()).time } catch (_: Exception) { LocalTime(0,0) }
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

                    OutlinedTextField(
                        value = try {
                            val tm2 = Instant.parse(endIso.value).toLocalDateTime(TimeZone.currentSystemDefault()).time
                            "%02d:%02d".format(tm2.hour, tm2.minute)
                        } catch (_: Exception) { "" },
                        onValueChange = {},
                        label = { Text(AppStrings.current.personalEvents.endTime) },
                        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) { focusManager.clearFocus(); showEndTimePicker = true } },
                        readOnly = true,
                        leadingIcon = { Icon(imageVector = Icons.Default.AccessTime, contentDescription = null) }
                    )
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

                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = isRecurring.value, onCheckedChange = { isRecurring.value = it })
                        Text(AppStrings.current.personalEvents.repeatWeekly, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            if (isRecurring.value) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(AppStrings.current.personalEvents.weekday)
                        // show only Monday..Friday and highlight selection with background
                        val dayNames = listOf("Po","Út","St","Čt","Pá")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            dayNames.forEachIndexed { idx, d ->
                                val sel = idx + 1
                                FilterChip(
                                    selected = (weekday.value == sel),
                                    onClick = {
                                        weekday.value = sel
                                        if (recurrenceStartIso.value.isBlank()) {
                                            val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
                                            recurrenceStartIso.value = LocalDateTime(today, LocalTime(0,0)).toInstant(TimeZone.currentSystemDefault()).toString()
                                        }
                                        if (recurrenceEndIso.value.isBlank()) {
                                            val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
                                            var juneYear = today.year
                                            var juneLast = LocalDate(juneYear, 6, 30)
                                            if (juneLast < today) juneYear += 1
                                            juneLast = LocalDate(juneYear, 6, 30)
                                            val targetDow = when (sel) {
                                                1 -> DayOfWeek.MONDAY
                                                2 -> DayOfWeek.TUESDAY
                                                3 -> DayOfWeek.WEDNESDAY
                                                4 -> DayOfWeek.THURSDAY
                                                5 -> DayOfWeek.FRIDAY
                                                else -> DayOfWeek.MONDAY
                                            }
                                            var dEpoch = LocalDateTime(juneLast, LocalTime(0,0)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                                            val dayMillis = 24L * 60L * 60L * 1000L
                                            var candidate = Instant.fromEpochMilliseconds(dEpoch).toLocalDateTime(TimeZone.currentSystemDefault()).date
                                            while (candidate.dayOfWeek != targetDow) {
                                                dEpoch -= dayMillis
                                                candidate = Instant.fromEpochMilliseconds(dEpoch).toLocalDateTime(TimeZone.currentSystemDefault()).date
                                            }
                                            recurrenceEndIso.value = LocalDateTime(candidate, LocalTime(0,0)).toInstant(TimeZone.currentSystemDefault()).toString()
                                        }
                                    },
                                    modifier = Modifier.padding(6.dp),
                                    label = { Text(d) },
                                    colors = FilterChipDefaults.filterChipColors()
                                )
                            }
                        }
                        Divider()

                        val recStartDate = try { if (recurrenceStartIso.value.isBlank()) null else Instant.parse(recurrenceStartIso.value).toLocalDateTime(TimeZone.currentSystemDefault()).date } catch (_: Exception) { null }
                        val recStartMillis = recStartDate?.let { LocalDateTime(it, LocalTime(0,0)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds() }
                        val recStartState = rememberDatePickerState(initialSelectedDateMillis = recStartMillis)
                        LaunchedEffect(recStartState.selectedDateMillis) {
                            val selMillis = recStartState.selectedDateMillis
                            if (selMillis != null) {
                                val sel = Instant.fromEpochMilliseconds(selMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date
                                recurrenceStartIso.value = LocalDateTime(sel, LocalTime(0,0)).toInstant(TimeZone.currentSystemDefault()).toString()
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = try {
                                    if (recurrenceStartIso.value.isBlank()) "" else formatLocalDate(Instant.parse(recurrenceStartIso.value).toLocalDateTime(TimeZone.currentSystemDefault()).date)
                                } catch (_: Exception) { "" },
                                onValueChange = {},
                                label = { Text(AppStrings.current.personalEvents.recurrenceStart) },
                                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) { focusManager.clearFocus(); showRecurrenceStartDatePicker = true } },
                                readOnly = true,
                                leadingIcon = { Icon(imageVector = Icons.Default.Event, contentDescription = null) }
                            )
                        }
                        if (showRecurrenceStartDatePicker) {
                            DatePickerDialog(onDismissRequest = { showRecurrenceStartDatePicker = false }, confirmButton = {
                                Button(onClick = {
                                    val selMillis = recStartState.selectedDateMillis
                                    if (selMillis != null) {
                                        val sel = Instant.fromEpochMilliseconds(selMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date
                                        recurrenceStartIso.value = LocalDateTime(sel, LocalTime(0,0)).toInstant(TimeZone.currentSystemDefault()).toString()
                                    }
                                    showRecurrenceStartDatePicker = false
                                }) { Text(AppStrings.current.ok) }
                            }) { DatePicker(state = recStartState) }
                        }

                        val recEndDate = try { if (recurrenceEndIso.value.isBlank()) null else Instant.parse(recurrenceEndIso.value).toLocalDateTime(TimeZone.currentSystemDefault()).date } catch (_: Exception) { null }
                        val recEndMillis = recEndDate?.let { LocalDateTime(it, LocalTime(0,0)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds() }
                        val recEndState = rememberDatePickerState(initialSelectedDateMillis = recEndMillis)
                        LaunchedEffect(recEndState.selectedDateMillis) {
                            val selMillis = recEndState.selectedDateMillis
                            if (selMillis != null) {
                                val sel = Instant.fromEpochMilliseconds(selMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date
                                recurrenceEndIso.value = LocalDateTime(sel, LocalTime(0,0)).toInstant(TimeZone.currentSystemDefault()).toString()
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = try {
                                    if (recurrenceEndIso.value.isBlank()) "" else formatLocalDate(Instant.parse(recurrenceEndIso.value).toLocalDateTime(TimeZone.currentSystemDefault()).date)
                                } catch (_: Exception) { "" },
                                onValueChange = {},
                                label = { Text(AppStrings.current.personalEvents.recurrenceEnd) },
                                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) { focusManager.clearFocus(); showRecurrenceEndDatePicker = true } },
                                readOnly = true,
                                leadingIcon = { Icon(imageVector = Icons.Default.Event, contentDescription = null) }
                            )
                        }
                        if (showRecurrenceEndDatePicker) {
                            DatePickerDialog(onDismissRequest = { showRecurrenceEndDatePicker = false }, confirmButton = {
                                Button(onClick = {
                                    val selMillis = recEndState.selectedDateMillis
                                    if (selMillis != null) {
                                        val sel = Instant.fromEpochMilliseconds(selMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date
                                        recurrenceEndIso.value = LocalDateTime(sel, LocalTime(0,0)).toInstant(TimeZone.currentSystemDefault()).toString()
                                    }
                                    showRecurrenceEndDatePicker = false
                                }) { Text(AppStrings.current.ok) }
                            }) { DatePicker(state = recEndState) }
                        }
                    }
                }
            }

            Button(onClick = {
                val id = eventId ?: (kotlin.time.Clock.System.now().toEpochMilliseconds().toString() + "_${(0..Int.MAX_VALUE).random()}")
                val ev = PersonalEvent(
                    id = id,
                    title = title.value,
                    type = trainingType.value,
                    startIso = startIso.value,
                    endIso = endIso.value,
                    recurrenceDayOfWeek = if (isRecurring.value) weekday.value else null,
                    recurrenceStartIso = recurrenceStartIso.value.ifBlank { null },
                    recurrenceEndIso = recurrenceEndIso.value.ifBlank { null }
                )
                // save asynchronously to avoid blocking composition
                coroutineScope.launch {
                    kotlin.runCatching { service.save(ev) }
                    onSaved()
                }
            }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(AppStrings.current.personalEvents.saveTraining)
            }
        }
    }
}
