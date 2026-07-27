package com.tkolymp.tkolympapp.screens.campschedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.campschedule.ScheduleEntry
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.viewmodels.CampScheduleViewModel
import com.tkolymp.tkolympapp.components.QuantityInput
import com.tkolymp.tkolympapp.platform.CampScheduleUploadButton
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * The camp-only "Rozpis" tab: pick a camp day, upload its schedule photo, pick your
 * group number, and see a simple list of your own entries for that day (meals always
 * included, group lessons matched by number, individual lessons matched by name).
 * There is no raw grid/table view — matching by name/group content rather than by
 * grid position is also what makes this robust to OCR cells landing in the "wrong"
 * column/row from photo perspective distortion.
 */
@Composable
fun RozpisTabContent(eventId: Long, instances: List<JsonObject>, modifier: Modifier = Modifier) {
    val viewModel = viewModel<CampScheduleViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showReminderDialog by remember { mutableStateOf(false) }

    LaunchedEffect(eventId) { viewModel.load(eventId, instances) }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        if (state.campDates.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.campDates.forEach { date ->
                    FilterChip(
                        selected = date == state.selectedDate,
                        onClick = { scope.launch { viewModel.selectDate(date) } },
                        label = { Text("${date.day}.${date.monthNumber}.") }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        val dayLabel = state.selectedDate?.dayOfWeek?.name.orEmpty()
        CampScheduleUploadButton(
            dayLabel = dayLabel,
            onScheduleBuilt = { day -> scope.launch { viewModel.saveDay(day) } },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        var myNameInput by remember(state.myTableName) { mutableStateOf(state.myTableName) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = myNameInput,
                onValueChange = { myNameInput = it },
                label = { Text(AppStrings.current.campSchedule.myNameLabel) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { scope.launch { viewModel.setMyTableName(myNameInput) } }) {
                Text(AppStrings.current.commonActions.save)
            }
        }

        val day = state.day
        if (day == null) {
            Spacer(Modifier.height(8.dp))
            Text(AppStrings.current.campSchedule.noScheduleYet, style = MaterialTheme.typography.bodyMedium)
        } else {
            Spacer(Modifier.height(16.dp))

            if (state.availableGroupNumbers.isNotEmpty()) {
                Text(AppStrings.current.campSchedule.selectGroupsTitle, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.availableGroupNumbers.forEach { number ->
                        FilterChip(
                            selected = number == state.myGroupNumber,
                            onClick = { scope.launch { viewModel.setGroupNumber(number) } },
                            label = { Text(number.toString()) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    AppStrings.current.campSchedule.mySchedule,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showReminderDialog = true }) {
                    Icon(Icons.Default.Notifications, contentDescription = AppStrings.current.notifications.remindMe)
                }
            }

            state.mySchedule.forEach { entry ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        when (entry) {
                            is ScheduleEntry.Note -> {
                                Text(entry.time, style = MaterialTheme.typography.labelLarge)
                                val text = entry.text
                                if (!text.isNullOrBlank()) Text(text, style = MaterialTheme.typography.bodyMedium)
                            }
                            is ScheduleEntry.Lesson -> {
                                Row {
                                    Text(entry.time, style = MaterialTheme.typography.labelLarge)
                                    val block = entry.block
                                    if (block != null) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(block, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                entry.entries.forEach { (name, value) ->
                                    if (value != null) Text("$name: $value", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReminderDialog) {
        var reminderValue by remember(showReminderDialog) { mutableIntStateOf(state.reminderMinutes) }
        AlertDialog(
            onDismissRequest = { showReminderDialog = false },
            title = { Text(AppStrings.current.notifications.reminderDialogTitle) },
            text = {
                QuantityInput(
                    value = reminderValue,
                    onValueChange = { v, _ -> reminderValue = v },
                    units = listOf("min"),
                    defaultUnit = "min",
                    label = AppStrings.current.campSchedule.reminderMinutesLabel,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch { viewModel.setReminderMinutes(reminderValue) }
                    showReminderDialog = false
                }) { Text(AppStrings.current.commonActions.save) }
            },
            dismissButton = {
                TextButton(onClick = { showReminderDialog = false }) { Text(AppStrings.current.commonActions.cancel) }
            }
        )
    }
}
