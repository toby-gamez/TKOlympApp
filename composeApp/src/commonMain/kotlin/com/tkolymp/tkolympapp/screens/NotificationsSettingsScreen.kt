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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.notification.FilterType
import com.tkolymp.shared.notification.NotificationRule
import com.tkolymp.shared.notification.NotificationSettings
import com.tkolymp.shared.viewmodels.NotificationsSettingsViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.platform.NotificationExportImportButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.tkolymp.tkolympapp.components.QuantityInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSettingsScreen(onBack: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val json = remember { Json { prettyPrint = true; ignoreUnknownKeys = true } }
    var availableLocations by remember { mutableStateOf<List<String>>(emptyList()) }
    var availableTrainers by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val availableTypes = listOf("CAMP", "LESSON", "GROUP", "RESERVATION", "HOLIDAY")
    // display labels for types
    val typeLabels = mapOf(
        "LESSON" to AppStrings.current.events.eventTypeLesson,
        "CAMP" to AppStrings.current.events.eventTypeCamp,
        "GROUP" to AppStrings.current.events.eventTypeGroup,
        "RESERVATION" to AppStrings.current.events.eventTypeReservation,
        "HOLIDAY" to AppStrings.current.events.eventTypeHoliday
    )
    var rules = remember { mutableStateListOf<NotificationRule>() }
    var globalEnabled by remember { mutableStateOf(true) }
    val viewModel = viewModel<NotificationsSettingsViewModel>()
    val vmState by viewModel.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editRule by remember { mutableStateOf<NotificationRule?>(null) }
    var deletingRuleId by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // load vm settings and UI data in parallel
        try {
            viewModel.loadSettings()
        } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        try {
            val svc = ServiceLocator.notificationService
            val settings = try { svc.getSettings() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            settings?.rules?.forEach { rules.add(it) }
            globalEnabled = settings?.globalEnabled ?: true
        } catch (_: UninitializedPropertyAccessException) {
            // no service
        }

        // load club data (locations/trainers)
            try {
            val club = withContext(Dispatchers.IO) { ServiceLocator.clubService.fetchClubData() }
            // exclude placeholder/removed location entries (e.g. "ZRUŠENO")
            availableLocations = club.locations.mapNotNull { it.name?.trim() }
                .filter { it.isNotBlank() && !it.equals("ZRUŠENO", ignoreCase = true) }
                .distinct()
            availableTrainers = club.trainers.mapNotNull { t ->
                t.person?.let { p ->
                    val name = listOfNotNull(p.firstName, p.lastName).joinToString(" ")
                    val trimmed = name.trim()
                    val id = p.id ?: return@mapNotNull null
                    if (trimmed.isNotBlank()) Pair(id, trimmed) else null
                }
            }.distinct()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { /* ignore */ }
    }

    fun persist() {
        scope.launch {
            try {
                val svc = ServiceLocator.notificationService
                val settings = NotificationSettings(globalEnabled = globalEnabled, rules = rules.toList())
                try {
                    svc.updateSettings(settings)
                } catch (e: CancellationException) { throw e } catch (t: Exception) {
                    Logger.d("NotificationsSettings", "Failed to update settings: ${t.message}")
                }
            } catch (e: UninitializedPropertyAccessException) {
                Logger.d("NotificationsSettings", "notificationService not initialized: ${e.message}")
            } catch (e: CancellationException) { throw e } catch (t: Exception) {
                Logger.d("NotificationsSettings", "Unexpected error persisting settings: ${t.message}")
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(AppStrings.current.otherScreen.notificationSettings) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back) }
        }, actions = {
            NotificationExportImportButton(
                onGetExportJson = {
                    json.encodeToString(NotificationSettings(globalEnabled = globalEnabled, rules = rules.toList()))
                },
                onImportJson = { jsonStr ->
                    val imported = json.decodeFromString<NotificationSettings>(jsonStr)
                    rules.clear()
                    rules.addAll(imported.rules)
                    globalEnabled = imported.globalEnabled
                    persist()
                },
                onMessage = {}
            )
        }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editRule = null; showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = AppStrings.current.notifications.addRule)
            }
        }
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            SwipeToReload(
                isRefreshing = vmState.isLoading,
                onRefresh = { scope.launch { viewModel.loadSettings() } },
                modifier = Modifier.fillMaxSize()
            ) {

                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(AppStrings.current.notifications.globallyEnabled)
                        Switch(
                            checked = globalEnabled,
                            onCheckedChange = { globalEnabled = it; persist() })
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(rules, key = { it.id }) { r ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                shape = RoundedCornerShape(16.dp),
                                
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val title =
                                            if (r.name.isNotBlank()) r.name else if (r.filterType == FilterType.ALL) AppStrings.current.notifications.allEventsFilter else AppStrings.current.misc.rule
                                        Text(text = title)
                                        if (r.filterType == FilterType.ALL) {
                                            Text(
                                                text = AppStrings.current.notifications.allEventsFilter,
                                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        } else {
                                            Text(
                                                text = "Místa: ${
                                                    if (r.locations.isNotEmpty()) r.locations.joinToString(
                                                        ", "
                                                    ) else AppStrings.current.notifications.allLocations
                                                }",
                                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                            val trainerDisplay =
                                                if (r.trainers.isNotEmpty()) r.trainers.map { t ->
                                                    if (t.contains("::")) t.substringAfter("::") else t
                                                }.joinToString(", ") else AppStrings.current.notifications.allTrainers
                                            Text(
                                                text = "Trenéři: $trainerDisplay",
                                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                            Text(
                                                text = "Typy: ${
                                                    if (r.types.isNotEmpty()) r.types.map { typeLabels[it] ?: it }
                                                        .joinToString(", ") else AppStrings.current.notifications.allTypes
                                                }",
                                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = "${r.timesBeforeMinutes.joinToString(", ")} ${AppStrings.current.notifications.minutesBefore}",
                                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(top = 6.dp)
                                        )
                                    }

                                    Row {
                                        Switch(checked = r.enabled, onCheckedChange = { new ->
                                            val idx = rules.indexOfFirst { it.id == r.id }
                                            if (idx >= 0) {
                                                rules[idx] = r.copy(enabled = new)
                                                persist()
                                            }
                                        })
                                        IconButton(onClick = {
                                            editRule = r; showDialog = true
                                        }) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = AppStrings.current.commonActions.edit
                                            )
                                        }
                                        IconButton(onClick = {
                                            deletingRuleId = r.id
                                            showDeleteConfirm = true
                                        }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = AppStrings.current.commonActions.delete
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (rules.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(AppStrings.current.notifications.noRules, style = MaterialTheme.typography.titleMedium)
                            Text(
                                AppStrings.current.notifications.noRulesDescription,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    } // end Box

                    Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
                        Text(AppStrings.current.commonActions.back)
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
                        if (toDeleteId != null) {
                            rules.removeAll { it.id == toDeleteId }
                            persist()
                        }
                        showDeleteConfirm = false
                        deletingRuleId = null
                    }) { Text(AppStrings.current.commonActions.delete) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false; deletingRuleId = null
                    }) { Text(AppStrings.current.commonActions.cancel) }
                }
            )
        }

        if (showDialog) {
            val dialogExisting = editRule
            var selType by remember(dialogExisting) {
                mutableStateOf(
                    when (dialogExisting?.filterType) {
                        FilterType.ALL, null -> FilterType.BY_LOCATION
                        else -> dialogExisting.filterType
                    }
                )
            }
            val selectedLocations = remember { mutableStateListOf<String>() }
            val selectedTrainers = remember { mutableStateListOf<String>() }
            val selectedTypes = remember(dialogExisting) {
                mutableStateListOf<String>().apply {
                    dialogExisting?.types?.let { addAll(it) }
                }
            }
            LaunchedEffect(dialogExisting, availableTrainers) {
                selectedLocations.clear()
                selectedTrainers.clear()
                dialogExisting?.locations?.let { selectedLocations.addAll(it) }
                dialogExisting?.trainers?.let { trList ->
                    trList.forEach { s ->
                        if (s.contains("::")) {
                            selectedTrainers.add(s)
                        } else {
                            val matched = availableTrainers.find { it.second == s }
                            if (matched != null) selectedTrainers.add("${matched.first}::${s}") else selectedTrainers.add(s)
                        }
                    }
                }
            }
            var timeValue by remember(dialogExisting) {
                mutableStateOf(dialogExisting?.timesBeforeMinutes?.firstOrNull()?.toString() ?: "60")
            }
            var isHours by remember(dialogExisting) {
                mutableStateOf(dialogExisting?.timesBeforeMinutes?.firstOrNull()?.let { it % 60 == 0 && it != 0 } ?: false)
            }
            var ruleName by remember(dialogExisting) { mutableStateOf(dialogExisting?.name ?: "") }
            var timeUnitExpanded by remember { mutableStateOf(false) }
            var lastIsHours by remember { mutableStateOf(isHours) }
            Dialog(
                onDismissRequest = { showDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = if (dialogExisting == null) AppStrings.current.notifications.addRule else AppStrings.current.notifications.editRuleTitle,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        OutlinedTextField(
                            value = ruleName,
                            onValueChange = { ruleName = it },
                            label = { Text(AppStrings.current.notifications.ruleNameLabel) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        var timeUnit by remember { mutableStateOf(if (isHours) "h" else "min") }
                        val unitOptions = listOf("min", "h")
                        QuantityInput(
                            value = (if (timeUnit == "h") (timeValue.toIntOrNull() ?: 1) else (timeValue.toIntOrNull() ?: 60)),
                            onValueChange = { v, u ->
                                timeUnit = u
                                if (u == "h") {
                                    timeValue = v.toString()
                                    isHours = true
                                } else {
                                    timeValue = v.toString()
                                    isHours = false
                                }
                            },
                            units = unitOptions,
                            defaultUnit = if (isHours) "h" else "min",
                            label = AppStrings.current.notifications.timeAheadLabel,
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Filter type moved below time input
                        Text(AppStrings.current.notifications.filterTypeLabel, style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                FilterType.BY_LOCATION,
                                FilterType.BY_TRAINER,
                                FilterType.BY_TYPE
                            ).forEach { t ->
                                val label = when (t) {
                                    FilterType.BY_LOCATION -> AppStrings.current.filters.filterPlace
                                    FilterType.BY_TRAINER -> AppStrings.current.filters.filterTrainer
                                    FilterType.BY_TYPE -> AppStrings.current.filters.filterType
                                    else -> t.name
                                }
                                FilterChip(
                                    selected = (selType == t),
                                    onClick = { selType = t },
                                    label = { Text(label) })
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(AppStrings.current.notifications.orPickFromValues, style = MaterialTheme.typography.labelLarge)
                        when (selType) {
                            FilterType.BY_LOCATION -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
                                ) {
                                    items(availableLocations) { loc ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = selectedLocations.contains(loc),
                                                onCheckedChange = {
                                                    if (it) selectedLocations.add(loc) else selectedLocations.remove(loc)
                                                })
                                            Text(loc)
                                        }
                                    }
                                }
                            }
                            FilterType.BY_TRAINER -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
                                ) {
                                    items(availableTrainers) { tr ->
                                        val trId = tr.first
                                        val trName = tr.second
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val isChecked = selectedTrainers.any { sel ->
                                                if (sel.contains("::")) sel.substringBefore("::") == trId else sel == trName
                                            }
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    if (checked) {
                                                        selectedTrainers.add("${trId}::${trName}")
                                                    } else {
                                                        selectedTrainers.removeAll { sel ->
                                                            if (sel.contains("::")) sel.substringBefore("::") == trId else sel == trName
                                                        }
                                                    }
                                                })
                                            Text(trName)
                                        }
                                    }
                                }
                            }
                            FilterType.BY_TYPE -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
                                ) {
                                    items(availableTypes) { tp ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = selectedTypes.contains(tp),
                                                onCheckedChange = {
                                                    if (it) selectedTypes.add(tp) else selectedTypes.remove(tp)
                                                })
                                            Text(typeLabels[tp] ?: tp)
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showDialog = false }) { Text(AppStrings.current.commonActions.cancel) }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    val tv = timeValue.trim().toIntOrNull() ?: 60
                                    val minutes = if (isHours) tv * 60 else tv
                                    val times = listOf(minutes)
                                    val existing = dialogExisting
                                    val finalLocations = if (selectedLocations.isNotEmpty()) selectedLocations.toList() else emptyList()
                                    val finalTrainers = if (selectedTrainers.isNotEmpty()) selectedTrainers.toList() else emptyList()
                                    val finalTypes = if (selectedTypes.isNotEmpty()) selectedTypes.toList() else emptyList()
                                    if (existing == null) {
                                        val nr = NotificationRule(
                                            id = kotlin.time.Clock.System.now().toEpochMilliseconds().toString(),
                                            name = ruleName,
                                            enabled = true,
                                            filterType = selType,
                                            locations = finalLocations,
                                            trainers = finalTrainers,
                                            types = finalTypes,
                                            timesBeforeMinutes = times
                                        )
                                        rules.add(nr)
                                    } else {
                                        val idx = rules.indexOfFirst { it.id == existing.id }
                                        if (idx >= 0) {
                                            val updated = existing.copy(
                                                name = ruleName,
                                                filterType = selType,
                                                locations = finalLocations,
                                                trainers = finalTrainers,
                                                types = finalTypes,
                                                timesBeforeMinutes = times
                                            )
                                            rules[idx] = updated
                                        }
                                    }
                                    persist()
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
    }}
