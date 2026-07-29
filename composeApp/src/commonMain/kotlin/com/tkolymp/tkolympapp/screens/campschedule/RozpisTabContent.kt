package com.tkolymp.tkolympapp.screens.campschedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.campschedule.NoteCategory
import com.tkolymp.shared.campschedule.ScheduleEntry
import com.tkolymp.shared.campschedule.categorizeNote
import com.tkolymp.shared.campschedule.effectiveSearchNames
import com.tkolymp.shared.campschedule.isGroupMatch
import com.tkolymp.shared.campschedule.myMatchedColumn
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.getLocalizedDayName
import com.tkolymp.shared.viewmodels.CampScheduleViewModel
import com.tkolymp.tkolympapp.components.QuantityInput
import com.tkolymp.tkolympapp.platform.CampSchedulePhotoThumbnail
import com.tkolymp.tkolympapp.platform.CampScheduleUploadButton
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject

private val entryTimeRegex = Regex("""(\d{1,2}):(\d{2})""")

/** Minutes since midnight from an entry's leading "HH:mm", or null if unparseable. */
private fun parseTimeMinutes(text: String): Int? {
    val m = entryTimeRegex.find(text) ?: return null
    val (h, min) = m.destructured
    val hour = h.toIntOrNull() ?: return null
    val minute = min.toIntOrNull() ?: return null
    return hour * 60 + minute
}

/**
 * The camp-only "Rozpis" tab: pick a camp day (by weekday name), upload its schedule
 * photo, and see a simple list of your own entries for that day — your name and gender
 * come straight from your account (used to resolve which table name/partner surname to
 * search for), only the group number is chosen manually since it can't be derived from
 * the account. There is no raw grid/table view — matching by name/group content rather
 * than by grid position also makes this robust to OCR cells landing in the "wrong"
 * column/row from photo perspective distortion. Everything (schedule, group, photo) is
 * saved locally and restored as-is.
 */
@Composable
fun RozpisTabContent(
    eventId: Long,
    instances: List<JsonObject>,
    eventName: String = "",
    onOpenReminders: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val viewModel = viewModel<CampScheduleViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showReminderDialog by remember { mutableStateOf(false) }
    val languageCode = AppStrings.currentLanguage.code
    val tz = remember { TimeZone.currentSystemDefault() }
    val now = remember { kotlin.time.Clock.System.now().toLocalDateTime(tz) }
    val today = now.date

    LaunchedEffect(eventId) { viewModel.load(eventId, instances, eventName) }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        if (state.campDates.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.campDates.forEach { date ->
                    FilterChip(
                        selected = date == state.selectedDate,
                        onClick = { scope.launch { viewModel.selectDate(date) } },
                        label = { Text(getLocalizedDayName(date.dayOfWeek, languageCode)) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        val selectedDate = state.selectedDate
        val tomorrow = today.plus(DatePeriod(days = 1))
        // A day's schedule is normally only photographed the evening before, so
        // tomorrow unlocks from 21:00 today onward instead of staying locked until
        // midnight — applies every day, not just a one-off date.
        val canUpload = selectedDate != null &&
            (selectedDate <= today || (selectedDate == tomorrow && now.hour >= 21))
        val dayLabel = selectedDate?.let { getLocalizedDayName(it.dayOfWeek, languageCode) }.orEmpty()

        val day = state.day
        if (day == null) {
            // Empty state: explain what this tab is for, then let the user upload a photo —
            // everything centered (including vertically in the remaining space below the
            // day chips), icon on top since there's no list content to align with yet.
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(AppStrings.current.campSchedule.rozpisTab, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        AppStrings.current.campSchedule.headerDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    CampScheduleUploadButton(
                        dayLabel = dayLabel,
                        enabled = canUpload,
                        onScheduleBuilt = { builtDay, bytes -> scope.launch { viewModel.saveDay(builtDay, bytes) } }
                    )
                }
            }
        } else {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // A schedule is already loaded — skip the icon/title/description intro and
                // get straight to the day's content; re-uploading lives below the photo.
                if (state.myGroupNumber == null && state.availableGroupNumbers.isNotEmpty()) {
                    Text(AppStrings.current.campSchedule.selectGroupsTitle, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.availableGroupNumbers.forEach { number ->
                            FilterChip(
                                selected = false,
                                onClick = { scope.launch { viewModel.setGroupNumber(number) } },
                                label = { Text(number.toString()) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (state.myGroupNumber != null && state.myTableName.isNotBlank()) {
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

                    val (searchName, searchPartner) = effectiveSearchNames(state.myGender, state.myTableName, state.myPartnerName)
                    val showNowLine = selectedDate == today
                    val nowMinutes = now.hour * 60 + now.minute
                    var nowLineShown = false

                    @Composable
                    fun NowLine() {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                            Text(
                                "${AppStrings.current.campSchedule.nowLabel} ${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    state.mySchedule.forEach { entry ->
                        if (showNowLine && !nowLineShown) {
                            val entryMinutes = parseTimeMinutes(entry.time)
                            if (entryMinutes != null && entryMinutes >= nowMinutes) {
                                nowLineShown = true
                                NowLine()
                            }
                        }
                        // Every card follows the same time + subtitle layout and a shared
                        // minimum height, regardless of whether an entry has a subtitle —
                        // otherwise 1-line and 2-line entries make the list look inconsistent.
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    when (entry) {
                                        is ScheduleEntry.Note -> {
                                            Text(entry.time, style = MaterialTheme.typography.labelLarge)
                                            val text = entry.text
                                            if (!text.isNullOrBlank()) Text(text, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        is ScheduleEntry.Lesson -> {
                                            Text(entry.time, style = MaterialTheme.typography.labelLarge)
                                            val block = entry.block
                                            // Only badge the block when it's the actual reason this
                                            // lesson matched — an individual lesson can match purely
                                            // by name while its block names an unrelated group that
                                            // simply runs at the same time, and showing that group's
                                            // label there would look like a group mismatch.
                                            if (block != null && isGroupMatch(entry, state.myGroupNumber)) {
                                                Text(block, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                            }
                                            val matchedColumn = myMatchedColumn(entry, searchName, searchPartner)
                                            if (matchedColumn != null) {
                                                Text(matchedColumn, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                if (entry is ScheduleEntry.Note) {
                                    when (categorizeNote(entry.text)) {
                                        NoteCategory.MEAL -> Icon(
                                            Icons.Filled.Restaurant,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.alpha(0.4f)
                                        )
                                        NoteCategory.SLEEP -> Icon(
                                            Icons.Filled.Bedtime,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.alpha(0.4f)
                                        )
                                        NoteCategory.OTHER -> {}
                                    }
                                }
                            }
                        }
                    }
                    if (showNowLine && !nowLineShown) NowLine()

                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { showReminderDialog = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(AppStrings.current.notifications.remindMe)
                        }
                        if (onOpenReminders != null) {
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = onOpenReminders) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = AppStrings.current.notifications.reminderTab)
                            }
                        }
                    }
                }

                state.photoBytes?.let { bytes ->
                    Spacer(Modifier.height(16.dp))
                    CampSchedulePhotoThumbnail(photoBytes = bytes, modifier = Modifier.fillMaxWidth())
                }

                // Re-uploading a fresh photo lives here, below the current one, rather than
                // at the top — the day's content is what matters once a schedule exists.
                Spacer(Modifier.height(16.dp))
                CampScheduleUploadButton(
                    dayLabel = dayLabel,
                    enabled = canUpload,
                    onScheduleBuilt = { builtDay, bytes -> scope.launch { viewModel.saveDay(builtDay, bytes) } }
                )
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
