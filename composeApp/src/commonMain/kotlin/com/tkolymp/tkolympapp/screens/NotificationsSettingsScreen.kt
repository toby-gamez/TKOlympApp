package com.tkolymp.tkolympapp.screens

import com.tkolymp.tkolympapp.components.cardLineBorder
import com.tkolymp.tkolympapp.components.surfaceCardColors
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import com.tkolymp.tkolympapp.components.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.tkolymp.tkolympapp.components.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
import com.tkolymp.tkolympapp.components.AppTopBar
import com.tkolymp.tkolympapp.components.SegmentedToggle
import com.tkolymp.tkolympapp.components.accentColor
import com.tkolymp.tkolympapp.components.SectionLabel
import com.tkolymp.tkolympapp.components.skeleton.SkeletonRows
import com.tkolymp.tkolympapp.ui.theme.Spacing
import androidx.compose.runtime.Composable
import com.tkolymp.tkolympapp.util.cozyItem
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.notification.EventReminder
import com.tkolymp.shared.notification.FilterType
import com.tkolymp.shared.notification.NotificationRule
import com.tkolymp.shared.notification.NotificationSettings
import com.tkolymp.shared.utils.formatTimeAgo
import com.tkolymp.shared.viewmodels.NotificationsSettingsViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.platform.NotificationExportImportButton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.tkolymp.tkolympapp.components.QuantityInput
import com.tkolymp.shared.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import com.tkolymp.tkolympapp.components.EmptyState
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Rule

/** (date "27. 7.", time "17:15") in the device's local time zone, or ("", "") if unparseable. */
private fun formatReminderDateTime(iso: String): Pair<String, String> = try {
    val local = Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault())
    val date = "${local.day}. ${local.monthNumber}."
    val time = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    date to time
} catch (e: Exception) {
    if (e is CancellationException) throw e
    "" to ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSettingsScreen(onBack: () -> Unit = {}, initialTab: Int = 0, onEditRule: (String?) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val json = remember { Json { prettyPrint = true; ignoreUnknownKeys = true } }
    val availableTypes = listOf("CAMP", "LESSON", "GROUP", "RESERVATION", "HOLIDAY", "PERSONAL_TRAINING")
    val typeLabels = mapOf(
        "LESSON" to AppStrings.current.events.eventTypeLesson,
        "CAMP" to AppStrings.current.events.eventTypeCamp,
        "GROUP" to AppStrings.current.events.eventTypeGroup,
        "RESERVATION" to AppStrings.current.events.eventTypeReservation,
        "HOLIDAY" to AppStrings.current.events.eventTypeHoliday,
        "PERSONAL_TRAINING" to AppStrings.current.notifications.eventTypePersonalTraining
    )
    val viewModel = viewModel<NotificationsSettingsViewModel>()
    val vmState by viewModel.state.collectAsStateWithLifecycle()
    var deletingRuleId by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            kotlinx.coroutines.coroutineScope {
                val s1 = async { viewModel.loadSettings() }
                val s2 = async { viewModel.loadUiData() }
                val s3 = async { viewModel.loadClubData() }
                val s4 = async { viewModel.loadReminders() }
                try { s1.await() } catch (e: Exception) { if (e is CancellationException) throw e; Logger.w("NotificationsSettings", "loadSettings failed: ${e.message}") }
                try { s2.await() } catch (e: Exception) { if (e is CancellationException) throw e; Logger.w("NotificationsSettings", "loadUiData failed: ${e.message}") }
                try { s3.await() } catch (e: Exception) { if (e is CancellationException) throw e; Logger.w("NotificationsSettings", "loadClubData failed: ${e.message}") }
                try { s4.await() } catch (e: Exception) { if (e is CancellationException) throw e; Logger.w("NotificationsSettings", "loadReminders failed: ${e.message}") }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.w("NotificationsSettings", "initial load failed: ${e.message}") }
    }

    // The rule editor is a separate screen with its own ViewModel, so reload when coming back to it.
    LifecycleResumeEffect(Unit) {
        scope.launch { viewModel.loadSettings() }
        onPauseOrDispose {}
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }

    // Most actions on this screen (toggling a rule, deleting a reminder, saving settings) are
    // transient edits to an otherwise-still-usable list — a snackbar surfaces a save failure
    // without hiding the content the user was just looking at.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(vmState.error) {
        vmState.error?.let { snackbarHostState.showSnackbar(it.message) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { AppTopBar(title = AppStrings.current.otherScreen.notificationSettings, onBack = onBack, actions = {
            if (selectedTab == 0) {
                NotificationExportImportButton(
                    onGetExportJson = {
                        json.encodeToString(NotificationSettings(globalEnabled = vmState.globalEnabled, rules = vmState.rules))
                    },
                    onImportJson = { jsonStr ->
                        val imported = json.decodeFromString<NotificationSettings>(jsonStr)
                        scope.launch { viewModel.importSettings(imported) }
                    },
                    onMessage = {}
                )
            }
        }) },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = { onEditRule(null) }) {
                    Icon(Icons.Default.Add, contentDescription = AppStrings.current.notifications.addRule)
                }
            }
        }
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            SwipeToReload(
                isRefreshing = vmState.isLoading,
                onRefresh = { scope.launch { viewModel.loadSettings() } },
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val tabs = listOf(AppStrings.current.notifications.notificationsRules, AppStrings.current.notifications.reminderTab, AppStrings.current.notifications.fromCoach)
                    SegmentedToggle(
                        labels = tabs,
                        selectedIndex = selectedTab,
                        onSelect = { selectedTab = it },
                        fillWidth = true,
                        modifier = Modifier.fillMaxWidth().padding(start = Spacing.default, end = Spacing.default, top = Spacing.small)
                    )
                    LaunchedEffect(selectedTab) {
                        if (selectedTab == 1) {
                            scope.launch { viewModel.loadReminders() }
                        } else if (selectedTab == 2) {
                            scope.launch { viewModel.loadUiData() }
                        }
                    }
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.default)) {
                        if (selectedTab == 0) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = Spacing.medium, bottom = Spacing.xs),
                                shape = RoundedCornerShape(16.dp),
                                colors = surfaceCardColors(),
                                border = cardLineBorder()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = Spacing.small),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.Notifications, contentDescription = null, tint = accentColor(), modifier = Modifier.size(20.dp))
                                    }
                                    Text(
                                        AppStrings.current.notifications.globallyEnabled,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Switch(
                                        checked = vmState.globalEnabled,
                                        onCheckedChange = { scope.launch { viewModel.setGlobalEnabled(it) } })
                                }
                            }
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            if (selectedTab == 2) {
                                val myGroups = vmState.availableGroups.filter { vmState.myCohortIds.contains(it.first) }
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    item {
                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                            SectionLabel(AppStrings.current.notifications.channelsListTitle, horizontalPadding = Spacing.none)
                                            if (myGroups.isEmpty()) {
                                                Text(AppStrings.current.people.noGroupsToShow, style = MaterialTheme.typography.bodySmall)
                                            } else {
                                                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = surfaceCardColors(), border = cardLineBorder()) {
                                                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(imageVector = Icons.Filled.Notifications, contentDescription = AppStrings.current.people.trainingSpaces, modifier = Modifier.size(28.dp))
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Text(text = AppStrings.current.notifications.generalLabel, style = MaterialTheme.typography.bodyLarge)
                                                    }
                                                }
                                                myGroups.forEach { g ->
                                                    Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.small), verticalAlignment = Alignment.CenterVertically) {
                                                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = surfaceCardColors(), border = cardLineBorder()) {
                                                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(imageVector = Icons.Filled.Notifications, contentDescription = AppStrings.current.people.trainingSpaces, modifier = Modifier.size(28.dp))
                                                                Spacer(modifier = Modifier.width(12.dp))
                                                                Text(text = g.second, style = MaterialTheme.typography.bodyLarge)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    item {
                                        SectionLabel(AppStrings.current.notifications.fromCoach, horizontalPadding = Spacing.none)
                                    }
                                    if (vmState.coachMessages.isEmpty()) {
                                        item {
                                            EmptyState(title = AppStrings.current.notifications.noNotificationsFromCoach, icon = Icons.Outlined.NotificationsNone)
                                        }
                                    } else {
                                        items(vmState.coachMessages, key = { it.id }) { msg ->
                                            Card(modifier = cozyItem().fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(12.dp), colors = surfaceCardColors(), border = cardLineBorder()) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text(text = msg.title ?: AppStrings.current.dialogs.noName)
                                                    val source = remember(vmState.availableGroups, msg.topic) {
                                                        val t = msg.topic ?: "all"
                                                        if (t == "all") AppStrings.current.notifications.generalLabel else vmState.availableGroups.find { it.first == t }?.second ?: t
                                                    }
                                                    Text(text = msg.body ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                                                    Row(modifier = Modifier.padding(top = 8.dp)) {
                                                        Text(text = formatTimeAgo(msg.epochMs), style = MaterialTheme.typography.bodySmall)
                                                        Text(text = ", $source", style = MaterialTheme.typography.bodySmall)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (selectedTab == 1) {
                                var editingReminder by remember { mutableStateOf<EventReminder?>(null) }
                                var deletingReminder by remember { mutableStateOf<EventReminder?>(null) }
                                if (vmState.reminders.isEmpty() && vmState.isLoading) {
                                    SkeletonRows(modifier = Modifier.padding(top = Spacing.small))
                                } else if (vmState.reminders.isEmpty()) {
                                    EmptyState(title = AppStrings.current.notifications.noReminders, icon = Icons.Outlined.NotificationsOff, fullPage = true)
                                } else {
                                    // Group every reminder sharing an event id under one card —
                                    // one bold header for the (big) event, one compact
                                    // time/name/minutes-before row per (small) sub-reminder.
                                    val groupedReminders = remember(vmState.reminders) {
                                        vmState.reminders.groupBy { it.eventId }.values
                                            .map { group -> group.sortedBy { it.eventStartIso } }
                                    }
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(groupedReminders, key = { it.first().eventId }) { group ->
                                            Card(modifier = cozyItem().fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp), colors = surfaceCardColors(), border = cardLineBorder()) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                        Text(
                                                            group.first().eventName.ifBlank { AppStrings.current.dialogs.noName },
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        val headerDate = formatReminderDateTime(group.first().eventStartIso).first
                                                        if (headerDate.isNotBlank()) {
                                                            Text(
                                                                headerDate,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    var lastDate = ""
                                                    group.forEachIndexed { i, reminder ->
                                                        val (date, time) = formatReminderDateTime(reminder.eventStartIso)
                                                        if (i > 0) HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                                                        if (date.isNotBlank() && date != lastDate) {
                                                            lastDate = date
                                                            Text(
                                                                date,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                                                            )
                                                        }
                                                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    reminder.subLabel?.takeIf { it.isNotBlank() } ?: reminder.eventName.ifBlank { AppStrings.current.dialogs.noName },
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    fontWeight = FontWeight.Medium
                                                                )
                                                                if (time.isNotBlank()) {
                                                                    Text(time, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                                                }
                                                                Text(
                                                                    AppStrings.current.notifications.remindMeBefore.replace("{0}", reminder.minutesBefore.toString()),
                                                                    style = MaterialTheme.typography.bodySmall
                                                                )
                                                            }
                                                            IconButton(onClick = { editingReminder = reminder }, modifier = Modifier.size(32.dp)) {
                                                                Icon(Icons.Default.Edit, contentDescription = AppStrings.current.commonActions.edit, modifier = Modifier.size(18.dp))
                                                            }
                                                            Spacer(Modifier.width(4.dp))
                                                            IconButton(onClick = { deletingReminder = reminder }, modifier = Modifier.size(32.dp)) {
                                                                Icon(Icons.Default.Delete, contentDescription = AppStrings.current.commonActions.delete, modifier = Modifier.size(18.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                deletingReminder?.let { toDelete ->
                                    AlertDialog(
                                        onDismissRequest = { deletingReminder = null },
                                        title = { Text(AppStrings.current.notifications.deleteRuleConfirmTitle) },
                                        text = { Text(AppStrings.current.notifications.deleteRuleConfirmText) },
                                        confirmButton = {
                                            Button(onClick = {
                                                scope.launch { viewModel.deleteReminder(toDelete.id) }
                                                deletingReminder = null
                                            }) { Text(AppStrings.current.commonActions.delete) }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { deletingReminder = null }) { Text(AppStrings.current.commonActions.cancel) }
                                        }
                                    )
                                }
                                editingReminder?.let { editing ->
                                    var editUnit by remember(editing) { mutableStateOf(if (editing.minutesBefore >= 60 && editing.minutesBefore % 60 == 0) "h" else "min") }
                                    var editValue by remember(editing) { mutableStateOf(if (editing.minutesBefore >= 60 && editing.minutesBefore % 60 == 0) editing.minutesBefore / 60 else editing.minutesBefore) }
                                    AlertDialog(
                                        onDismissRequest = { editingReminder = null },
                                        title = { Text(AppStrings.current.notifications.reminderDialogTitle) },
                                        text = {
                                            QuantityInput(
                                                value = editValue,
                                                onValueChange = { v, u -> editValue = v; editUnit = u },
                                                units = listOf("min", "h"),
                                                defaultUnit = editUnit,
                                                label = AppStrings.current.notifications.timeAheadLabel,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        },
                                        confirmButton = {
                                            Button(onClick = {
                                                val minutes = if (editUnit == "h") editValue * 60 else editValue
                                                scope.launch { viewModel.updateReminderMinutes(editing, minutes) }
                                                editingReminder = null
                                            }) { Text(AppStrings.current.commonActions.save) }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { editingReminder = null }) { Text(AppStrings.current.commonActions.cancel) }
                                        }
                                    )
                                }
                            }
                            if (selectedTab == 0) {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(vmState.rules, key = { it.id }) { r ->
                                        Card(modifier = cozyItem().fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(16.dp), colors = surfaceCardColors(), border = cardLineBorder()) {
                                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    val title = if (r.name.isNotBlank()) r.name else if (r.filterType == FilterType.ALL) AppStrings.current.notifications.allEventsFilter else AppStrings.current.misc.rule
                                                    Text(text = title)
                                                    if (r.filterType == FilterType.ALL) {
                                                        Text(text = AppStrings.current.notifications.allEventsFilter, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                                    } else {
                                                        when (r.filterType) {
                                                            FilterType.BY_LOCATION -> Text(text = "${AppStrings.current.filters.filterPlace}: ${if (r.locations.isNotEmpty()) r.locations.joinToString(", ") else AppStrings.current.notifications.allLocations}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                                            FilterType.BY_TRAINER -> {
                                                                val trainerDisplay = if (r.trainers.isNotEmpty()) r.trainers.map { t -> if (t.contains("::")) t.substringAfter("::") else t }.joinToString(", ") else AppStrings.current.notifications.allTrainers
                                                                Text(text = "${AppStrings.current.filters.filterTrainer}: $trainerDisplay", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                                            }
                                                            FilterType.BY_TYPE -> Text(text = "${AppStrings.current.filters.filterType}: ${if (r.types.isNotEmpty()) r.types.map { typeLabels[it] ?: it }.joinToString(", ") else AppStrings.current.notifications.allTypes}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                                            else -> {}
                                                        }
                                                    }
                                                    val beforeSuffix = AppStrings.current.notifications.minutesBefore.substringAfter(" ")
                                                    val timeDisplay = r.timesBeforeMinutes.joinToString(", ") { m ->
                                                        if (m >= 60 && m % 60 == 0) "${m / 60} ${AppStrings.current.notifications.hoursUnit} $beforeSuffix"
                                                        else "$m ${AppStrings.current.notifications.minutesBefore}"
                                                    }
                                                    Text(text = timeDisplay, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                                                }
                                                Row {
                                                    Switch(checked = r.enabled, onCheckedChange = { new ->
                                                        scope.launch { viewModel.toggleRule(r.id, new) }
                                                    })
                                                    IconButton(onClick = { onEditRule(r.id) }) {
                                                        Icon(Icons.Default.Edit, contentDescription = AppStrings.current.commonActions.edit)
                                                    }
                                                    IconButton(onClick = { deletingRuleId = r.id; showDeleteConfirm = true }) {
                                                        Icon(Icons.Default.Delete, contentDescription = AppStrings.current.commonActions.delete)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (vmState.rules.isEmpty() && vmState.isLoading) {
                                    SkeletonRows(modifier = Modifier.padding(top = Spacing.small))
                                } else if (vmState.rules.isEmpty()) {
                                    EmptyState(
                                        title = AppStrings.current.notifications.noRules,
                                        subtitle = AppStrings.current.notifications.noRulesDescription,
                                        icon = Icons.Outlined.Rule,
                                        fullPage = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showDeleteConfirm) {
            val toDeleteId = deletingRuleId
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false; deletingRuleId = null },
                title = { Text(AppStrings.current.notifications.deleteRuleConfirmTitle) },
                text = { Text(AppStrings.current.notifications.deleteRuleConfirmText) },
                confirmButton = {
                    Button(onClick = {
                        if (toDeleteId != null) scope.launch { viewModel.deleteRule(toDeleteId) }
                        showDeleteConfirm = false; deletingRuleId = null
                    }) { Text(AppStrings.current.commonActions.delete) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false; deletingRuleId = null }) { Text(AppStrings.current.commonActions.cancel) }
                }
            )
        }
    }
}
