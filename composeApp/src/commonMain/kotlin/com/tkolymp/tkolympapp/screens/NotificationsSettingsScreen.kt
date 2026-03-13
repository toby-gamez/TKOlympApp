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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tkolymp.shared.Logger
import com.tkolymp.tkolympapp.platform.NotificationExportImportButton
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.notification.FilterType
import com.tkolymp.shared.notification.NotificationRule
import com.tkolymp.shared.notification.NotificationSettings
import com.tkolymp.shared.viewmodels.NotificationsSettingsViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
        "LESSON" to AppStrings.current.eventTypeLesson,
        "CAMP" to AppStrings.current.eventTypeCamp,
        "GROUP" to AppStrings.current.eventTypeGroup,
        "RESERVATION" to AppStrings.current.eventTypeReservation,
        "HOLIDAY" to AppStrings.current.eventTypeHoliday
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
        topBar = { TopAppBar(title = { Text(AppStrings.current.notificationSettings) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.back) }
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
                Icon(Icons.Default.Add, contentDescription = AppStrings.current.addRule)
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
                        Text(AppStrings.current.globallyEnabled)
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
                                            if (r.name.isNotBlank()) r.name else if (r.filterType == FilterType.ALL) AppStrings.current.allEventsFilter else AppStrings.current.rule
                                        Text(text = title)
                                        if (r.filterType == FilterType.ALL) {
                                            Text(
                                                text = AppStrings.current.allEventsFilter,
                                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        } else {
                                            Text(
                                                text = "Místa: ${
                                                    if (r.locations.isNotEmpty()) r.locations.joinToString(
                                                        ", "
                                                    ) else AppStrings.current.allLocations
                                                }",
                                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                            val trainerDisplay =
                                                if (r.trainers.isNotEmpty()) r.trainers.map { t ->
                                                    if (t.contains("::")) t.substringAfter("::") else t
                                                }.joinToString(", ") else AppStrings.current.allTrainers
                                            Text(
                                                text = "Trenéři: $trainerDisplay",
                                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                            Text(
                                                text = "Typy: ${
                                                    if (r.types.isNotEmpty()) r.types.map { typeLabels[it] ?: it }
                                                        .joinToString(", ") else AppStrings.current.allTypes
                                                }",
                                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = "${r.timesBeforeMinutes.joinToString(", ")} ${AppStrings.current.minutesBefore}",
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
                                                contentDescription = AppStrings.current.edit
                                            )
                                        }
                                        IconButton(onClick = {
                                            deletingRuleId = r.id
                                            showDeleteConfirm = true
                                        }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = AppStrings.current.delete
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
                            Text(AppStrings.current.noRules, style = MaterialTheme.typography.titleMedium)
                            Text(
                                AppStrings.current.noRulesDescription,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    } // end Box

                    Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
                        Text(AppStrings.current.back)
                    }
                }
            }
        }

        if (showDeleteConfirm) {
            val toDeleteId = deletingRuleId
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false; deletingRuleId = null },
                title = { Text(AppStrings.current.deleteRuleConfirmTitle) },
                text = { Text(AppStrings.current.deleteRuleConfirmText) },
                confirmButton = {
                    Button(onClick = {
                        if (toDeleteId != null) {
                            rules.removeAll { it.id == toDeleteId }
                            persist()
                        }
                        showDeleteConfirm = false
                        deletingRuleId = null
                    }) { Text(AppStrings.current.delete) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false; deletingRuleId = null
                    }) { Text(AppStrings.current.cancel) }
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
            // removed single text filterValue input; selection is via checkboxes

            val selectedLocations = remember { mutableStateListOf<String>() }
            val selectedTrainers = remember { mutableStateListOf<String>() }
            val selectedTypes = remember(dialogExisting) {
                mutableStateListOf<String>().apply {
                    dialogExisting?.types?.let {
                        addAll(it)
                    }
                }
            }

            // When editing, try to prefill selections. Resolve trainer ids when club data is available.
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
                            if (matched != null) selectedTrainers.add("${matched.first}::${s}") else selectedTrainers.add(
                                s
                            )
                        }
                    }
                }
            }

            // single time value + unit state (moved outside inner Column so save button can access)
            var timeValue by remember(dialogExisting) {
                mutableStateOf(
                    dialogExisting?.timesBeforeMinutes?.firstOrNull()?.toString() ?: "60"
                )
            }
            var timeUnitExpanded by remember { mutableStateOf(false) }
            var isHours by remember(dialogExisting) { mutableStateOf(false) }
            var ruleName by remember(dialogExisting) { mutableStateOf(dialogExisting?.name ?: "") }

            Dialog(
                onDismissRequest = { showDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopAppBar(title = { Text(if (dialogExisting == null) AppStrings.current.addRule else AppStrings.current.editRuleTitle) })
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(16.dp)
                        ) {
                            OutlinedTextField(
                                value = ruleName,
                                onValueChange = { ruleName = it },
                                label = { Text(AppStrings.current.ruleNameLabel) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // Filter type selector using chips
                            Text(AppStrings.current.filterTypeLabel)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    FilterType.BY_LOCATION,
                                    FilterType.BY_TRAINER,
                                    FilterType.BY_TYPE
                                ).forEach { t ->
                                    val label = when (t) {
                                        FilterType.BY_LOCATION -> AppStrings.current.place
                                        FilterType.BY_TRAINER -> AppStrings.current.trainer
                                        FilterType.BY_TYPE -> AppStrings.current.eventType
                                        else -> t.name
                                    }
                                    FilterChip(
                                        selected = (selType == t),
                                        onClick = { selType = t },
                                        label = { Text(label) })
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = timeValue,
                                    onValueChange = { timeValue = it },
                                    label = { Text(AppStrings.current.timeAheadLabel) },
                                    modifier = Modifier.weight(1f)
                                )
                                Box {
                                    Button(onClick = { timeUnitExpanded = true }) { Text(if (isHours) AppStrings.current.hoursUnit else AppStrings.current.minutesUnit) }
                                    DropdownMenu(
                                        expanded = timeUnitExpanded,
                                        onDismissRequest = { timeUnitExpanded = false }) {
                                        DropdownMenuItem(
                                            text = { Text(AppStrings.current.minutesUnit) },
                                            onClick = { isHours = false; timeUnitExpanded = false })
                                        DropdownMenuItem(
                                            text = { Text(AppStrings.current.hoursUnit) },
                                            onClick = { isHours = true; timeUnitExpanded = false })
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(AppStrings.current.orPickFromValues)
                            when (selType) {
                                FilterType.BY_LOCATION -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    ) {
                                        items(availableLocations) { loc ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = selectedLocations.contains(loc),
                                                    onCheckedChange = {
                                                        if (it) selectedLocations.add(loc) else selectedLocations.remove(
                                                            loc
                                                        )
                                                    })
                                                Text(loc)
                                            }
                                        }
                                    }
                                }

                                FilterType.BY_TRAINER -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
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
                                                                if (sel.contains("::")) sel.substringBefore(
                                                                    "::"
                                                                ) == trId else sel == trName
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
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    ) {
                                        items(availableTypes) { tp ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = selectedTypes.contains(tp),
                                                    onCheckedChange = {
                                                        if (it) selectedTypes.add(tp) else selectedTypes.remove(
                                                            tp
                                                        )
                                                    })
                                                Text(typeLabels[tp] ?: tp)
                                            }
                                        }
                                    }
                                }

                                else -> { /* nothing */
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showDialog = false }) { Text(AppStrings.current.cancel) }
                            Spacer(modifier = Modifier.padding(6.dp))
                            Button(onClick = {
                                // parse single time + unit
                                val tv = timeValue.trim().toIntOrNull() ?: 60
                                val minutes = if (isHours) tv * 60 else tv
                                val times = listOf(minutes)
                                val existing = dialogExisting

                                // prefer selected checkboxes when present, otherwise empty = match all
                                val finalLocations =
                                    if (selectedLocations.isNotEmpty()) selectedLocations.toList() else emptyList()
                                val finalTrainers =
                                    if (selectedTrainers.isNotEmpty()) selectedTrainers.toList() else emptyList()
                                val finalTypes =
                                    if (selectedTypes.isNotEmpty()) selectedTypes.toList() else emptyList()

                                if (existing == null) {
                                    val nr = NotificationRule(
                                        id = Clock.System.now().toEpochMilliseconds().toString(),
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
                            }) { Text(AppStrings.current.save) }
                        }
                    }
                }
            }
        }
    }}
