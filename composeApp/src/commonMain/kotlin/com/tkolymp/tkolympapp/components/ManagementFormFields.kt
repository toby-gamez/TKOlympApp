package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.management.ManagementOption
import com.tkolymp.shared.utils.formatShortDate
import com.tkolymp.tkolympapp.ui.theme.AppTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Read-only text field that runs [onClick] when tapped (used to open pickers). */
@Composable
fun PickerField(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            leadingIcon = { Icon(icon, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (enabled) {
            Box(Modifier.matchParentSize().clickable(role = Role.Button, onClickLabel = label, onClick = onClick))
        }
    }
}

/** Date + time pickers for one point in time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeFields(
    label: String,
    value: LocalDateTime?,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = AppStrings.current.management
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PickerField(
            label = "$label – ${strings.date}",
            value = value?.date?.let { formatShortDate(it) }.orEmpty(),
            icon = Icons.Filled.Event,
            onClick = { showDate = true },
            modifier = Modifier.weight(1.4f),
        )
        PickerField(
            label = label,
            value = value?.let { formatTime(it.hour, it.minute) }.orEmpty(),
            icon = Icons.Filled.AccessTime,
            onClick = { showTime = true },
            modifier = Modifier.weight(1f),
        )
    }

    if (showDate) {
        // DatePicker works in UTC-midnight milliseconds.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = value?.date?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        onDateChange(Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date)
                    }
                    showDate = false
                }) { Text(AppStrings.current.commonActions.ok) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(AppStrings.current.commonActions.cancel) } },
        ) { DatePicker(state = pickerState) }
    }

    if (showTime) {
        val timeState = rememberTimePickerState(initialHour = value?.hour ?: 0, initialMinute = value?.minute ?: 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(timeState.hour, timeState.minute)
                    showTime = false
                }) { Text(AppStrings.current.commonActions.ok) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(AppStrings.current.commonActions.cancel) } },
            text = { TimePicker(state = timeState) },
        )
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Multi-select chips; when [enabled] is false only the selected options are shown. */
@Composable
fun OptionChips(
    label: String,
    options: List<ManagementOption>,
    selectedIds: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hint: String? = null,
) {
    val visible = if (enabled) options else options.filter { it.id in selectedIds }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            visible.forEach { option ->
                val selected = option.id in selectedIds
                FilterChip(
                    selected = selected,
                    onClick = { onToggle(option.id) },
                    enabled = enabled,
                    label = { Text(option.name) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else null,
                )
            }
        }
    }
}

internal fun formatTime(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

@Preview(name = "Management form fields — Light")
@Composable
private fun ManagementFormFieldsPreviewLight() {
    AppTheme(darkTheme = false) { ManagementFormFieldsPreviewContent() }
}

@Preview(name = "Management form fields — Dark")
@Composable
private fun ManagementFormFieldsPreviewDark() {
    AppTheme(darkTheme = true) { ManagementFormFieldsPreviewContent() }
}

@Composable
private fun ManagementFormFieldsPreviewContent() {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DateTimeFields(
            label = "Start",
            value = LocalDateTime(2026, 9, 26, 17, 30),
            onDateChange = {},
            onTimeChange = { _, _ -> },
        )
        SwitchRow(label = "Visible to members", checked = true, onCheckedChange = {})
        OptionChips(
            label = "Trainers",
            options = listOf(ManagementOption("1", "Jana Nováková"), ManagementOption("2", "Petr Svoboda")),
            selectedIds = listOf("1"),
            onToggle = {},
        )
    }
}
