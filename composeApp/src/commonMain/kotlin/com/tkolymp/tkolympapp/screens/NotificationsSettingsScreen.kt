package com.tkolymp.tkolympapp.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSettingsScreen(onBack: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val json = remember { Json { prettyPrint = true; ignoreUnknownKeys = true } }
    val availableTypes = listOf("CAMP", "LESSON", "GROUP", "RESERVATION", "HOLIDAY")
    val typeLabels = mapOf(
        "LESSON" to AppStrings.current.events.eventTypeLesson,
        "CAMP" to AppStrings.current.events.eventTypeCamp,
        "GROUP" to AppStrings.current.events.eventTypeGroup,
        "RESERVATION" to AppStrings.current.events.eventTypeReservation,
        "HOLIDAY" to AppStrings.current.events.eventTypeHoliday
    )
    val viewModel = viewModel<NotificationsSettingsViewModel>()
    val vmState by viewModel.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editRule by remember { mutableStateOf<NotificationRule?>(null) }
    var deletingRuleId by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            kotlinx.coroutines.coroutineScope {
                val s1 = async { viewModel.loadSettings() }
                val s2 = async { viewModel.loadUiData() }
                val s3 = async { viewModel.loadClubData() }
                val s4 = async { viewModel.loadReminders() }
                try { s1.await() } catch (_: Exception) {}
                try { s2.await() } catch (_: Exception) {}
                try { s3.await() } catch (_: Exception) {}
                try { s4.await() } catch (_: Exception) {}
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {}
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(AppStrings.current.otherScreen.notificationSettings) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back) }
        }, actions = {
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
                FloatingActionButton(onClick = { editRule = null; showDialog = true }) {
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
                    PrimaryTabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                        tabs.forEachIndexed { i, t ->
                            Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t) })
                        }
                    }
                    LaunchedEffect(selectedTab) {
                        if (selectedTab == 1) {
                            scope.launch { viewModel.loadReminders() }
                        } else if (selectedTab == 2) {
                            scope.launch { viewModel.loadUiData() }
                        }
                    }
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedTab == 0) {
                                Text(AppStrings.current.notifications.globallyEnabled)
                                Switch(
                                    checked = vmState.globalEnabled,
                                    onCheckedChange = { scope.launch { viewModel.setGlobalEnabled(it) } })
                            }
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            if (selectedTab == 2) {
                                val myGroups = vmState.availableGroups.filter { vmState.myCohortIds.contains(it.first) }
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    item {
                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                            Text(AppStrings.current.notifications.channelsListTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            if (myGroups.isEmpty()) {
                                                Text(AppStrings.current.people.noGroupsToShow, style = MaterialTheme.typography.bodySmall)
                                            } else {
                                                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), shape = RoundedCornerShape(16.dp)) {
                                                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(imageVector = Icons.Filled.Notifications, contentDescription = AppStrings.current.people.trainingSpaces, modifier = Modifier.size(28.dp))
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Text(text = AppStrings.current.notifications.generalLabel, style = MaterialTheme.typography.bodyLarge)
                                                    }
                                                }
                                                myGroups.forEach { g ->
                                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), shape = RoundedCornerShape(16.dp)) {
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
                                        Text(AppStrings.current.notifications.fromCoach, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                    if (vmState.coachMessages.isEmpty()) {
                                        item {
                                            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(AppStrings.current.notifications.noNotificationsFromCoach, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    } else {
                                        items(vmState.coachMessages, key = { it.id }) { msg ->
                                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(12.dp)) {
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
                                if (vmState.reminders.isEmpty()) {
                                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(AppStrings.current.notifications.noReminders, style = MaterialTheme.typography.titleMedium)
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(vmState.reminders, key = { it.id }) { reminder ->
                                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp)) {
                                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(reminder.eventName.ifBlank { AppStrings.current.dialogs.noName }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                                        Text(AppStrings.current.notifications.remindMeBefore.replace("{0}", reminder.minutesBefore.toString()), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                                    }
                                                    IconButton(onClick = { editingReminder = reminder }) {
                                                        Icon(Icons.Default.Edit, contentDescription = AppStrings.current.commonActions.edit)
                                                    }
                                                    IconButton(onClick = { deletingReminder = reminder }) {
                                                        Icon(Icons.Default.Delete, contentDescription = AppStrings.current.commonActions.delete)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (deletingReminder != null) {
                                    val toDelete = deletingReminder!!
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
                                if (editingReminder != null) {
                                    val editing = editingReminder!!
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
                                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(16.dp)) {
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
                                                    IconButton(onClick = { editRule = r; showDialog = true }) {
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
                                if (vmState.rules.isEmpty()) {
                                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(AppStrings.current.notifications.noRules, style = MaterialTheme.typography.titleMedium)
                                        Text(AppStrings.current.notifications.noRulesDescription, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                    }
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

        if (showDialog) {
            val dialogExisting = editRule
            var selType by remember(dialogExisting) {
                mutableStateOf(when (dialogExisting?.filterType) {
                    FilterType.ALL, null -> FilterType.BY_LOCATION
                    else -> dialogExisting.filterType
                })
            }
            val selectedLocations = remember { mutableStateListOf<String>() }
            val selectedTrainers = remember { mutableStateListOf<String>() }
            val selectedTypes = remember(dialogExisting) {
                mutableStateListOf<String>().apply { dialogExisting?.types?.let { addAll(it) } }
            }
            LaunchedEffect(dialogExisting, vmState.availableTrainers) {
                selectedLocations.clear()
                selectedTrainers.clear()
                dialogExisting?.locations?.let { selectedLocations.addAll(it) }
                dialogExisting?.trainers?.let { trList ->
                    trList.forEach { s ->
                        if (s.contains("::")) {
                            selectedTrainers.add(s)
                        } else {
                            val matched = vmState.availableTrainers.find { it.second == s }
                            if (matched != null) selectedTrainers.add("${matched.first}::$s") else selectedTrainers.add(s)
                        }
                    }
                }
            }
            var timeValue by remember(dialogExisting) { mutableStateOf(dialogExisting?.timesBeforeMinutes?.firstOrNull()?.toString() ?: "60") }
            var isHours by remember(dialogExisting) { mutableStateOf(dialogExisting?.timesBeforeMinutes?.firstOrNull()?.let { it % 60 == 0 && it != 0 } ?: false) }
            var ruleName by remember(dialogExisting) { mutableStateOf(dialogExisting?.name ?: "") }
            Dialog(onDismissRequest = { showDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize().padding(32.dp), shape = RoundedCornerShape(24.dp), tonalElevation = 8.dp, shadowElevation = 16.dp) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        Text(text = if (dialogExisting == null) AppStrings.current.notifications.addRule else AppStrings.current.notifications.editRuleTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
                        OutlinedTextField(value = ruleName, onValueChange = { ruleName = it }, label = { Text(AppStrings.current.notifications.ruleNameLabel) }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(12.dp))
                        var timeUnit by remember { mutableStateOf(if (isHours) "h" else "min") }
                        val unitOptions = listOf("min", "h")
                        QuantityInput(
                            value = (if (timeUnit == "h") (timeValue.toIntOrNull() ?: 1) else (timeValue.toIntOrNull() ?: 60)),
                            onValueChange = { v, u ->
                                timeUnit = u
                                timeValue = v.toString()
                                isHours = (u == "h")
                            },
                            units = unitOptions,
                            defaultUnit = if (isHours) "h" else "min",
                            label = AppStrings.current.notifications.timeAheadLabel,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(AppStrings.current.notifications.filterTypeLabel, style = MaterialTheme.typography.labelLarge)
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(FilterType.BY_LOCATION, FilterType.BY_TRAINER, FilterType.BY_TYPE).forEach { t ->
                                val label = when (t) {
                                    FilterType.BY_LOCATION -> AppStrings.current.filters.filterPlace
                                    FilterType.BY_TRAINER -> AppStrings.current.filters.filterTrainer
                                    FilterType.BY_TYPE -> AppStrings.current.filters.filterType
                                    else -> t.name
                                }
                                FilterChip(selected = (selType == t), onClick = { selType = t }, label = { Text(label) })
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(AppStrings.current.notifications.orPickFromValues, style = MaterialTheme.typography.labelLarge)
                        Box(modifier = Modifier.weight(1f)) {
                            when (selType) {
                                FilterType.BY_LOCATION -> {
                                    LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
                                        items(vmState.availableLocations) { loc ->
                                            Row(modifier = Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = selectedLocations.contains(loc), onCheckedChange = { if (it) selectedLocations.add(loc) else selectedLocations.remove(loc) })
                                                Text(loc)
                                            }
                                        }
                                    }
                                }
                                FilterType.BY_TRAINER -> {
                                    LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
                                        items(vmState.availableTrainers) { tr ->
                                            val trId = tr.first; val trName = tr.second
                                            Row(modifier = Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                val isChecked = selectedTrainers.any { sel -> if (sel.contains("::")) sel.substringBefore("::") == trId else sel == trName }
                                                Checkbox(checked = isChecked, onCheckedChange = { checked ->
                                                    if (checked) selectedTrainers.add("${trId}::${trName}")
                                                    else selectedTrainers.removeAll { sel -> if (sel.contains("::")) sel.substringBefore("::") == trId else sel == trName }
                                                })
                                                Text(trName)
                                            }
                                        }
                                    }
                                }
                                FilterType.BY_TYPE -> {
                                    LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
                                        items(availableTypes) { tp ->
                                            Row(modifier = Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = selectedTypes.contains(tp), onCheckedChange = { if (it) selectedTypes.add(tp) else selectedTypes.remove(tp) })
                                                Text(typeLabels[tp] ?: tp)
                                            }
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showDialog = false }) { Text(AppStrings.current.commonActions.cancel) }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    val tv = timeValue.trim().toIntOrNull() ?: 60
                                    val minutes = if (isHours) tv * 60 else tv
                                    val finalLocations = selectedLocations.toList()
                                    val finalTrainers = selectedTrainers.toList()
                                    val finalTypes = selectedTypes.toList()
                                    val rule = if (dialogExisting == null) {
                                        NotificationRule(
                                            id = kotlin.time.Clock.System.now().toEpochMilliseconds().toString(),
                                            name = ruleName, enabled = true,
                                            filterType = selType,
                                            locations = finalLocations, trainers = finalTrainers, types = finalTypes,
                                            timesBeforeMinutes = listOf(minutes)
                                        )
                                    } else {
                                        dialogExisting.copy(name = ruleName, filterType = selType, locations = finalLocations, trainers = finalTrainers, types = finalTypes, timesBeforeMinutes = listOf(minutes))
                                    }
                                    scope.launch { viewModel.addOrUpdateRule(rule) }
                                    showDialog = false
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(48.dp)
                            ) { Text(AppStrings.current.commonActions.save) }
                        }
                    }
                }
            }
        }
    }
}
