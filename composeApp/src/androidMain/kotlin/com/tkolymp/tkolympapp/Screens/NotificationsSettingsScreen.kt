package com.tkolymp.tkolympapp.Screens

import android.util.Log
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.tkolymp.shared.viewmodels.NotificationsSettingsViewModel
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
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.notification.FilterType
import com.tkolymp.shared.notification.NotificationRule
import com.tkolymp.shared.notification.NotificationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSettingsScreen(onBack: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var availableLocations by remember { mutableStateOf<List<String>>(emptyList()) }
    var availableTrainers by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val availableTypes = listOf("CAMP", "LESSON", "GROUP", "RESERVATION", "HOLIDAY")
    var rules = remember { mutableStateListOf<NotificationRule>() }
    var globalEnabled by remember { mutableStateOf(true) }
    val viewModel = remember { NotificationsSettingsViewModel() }
    val vmState by viewModel.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editRule by remember { mutableStateOf<NotificationRule?>(null) }
    var deletingRuleId by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // load vm settings and UI data in parallel
        try {
            viewModel.loadSettings()
        } catch (_: Throwable) {}
        try {
            val svc = ServiceLocator.notificationService
            val settings = try { svc.getSettings() } catch (_: Throwable) { null }
            settings?.rules?.forEach { rules.add(it) }
            globalEnabled = settings?.globalEnabled ?: true
        } catch (_: UninitializedPropertyAccessException) {
            // no service
        }

        // load club data (locations/trainers)
        try {
            val club = withContext(Dispatchers.IO) { ServiceLocator.clubService.fetchClubData() }
            availableLocations = club.locations.mapNotNull { it.name }.distinct()
            availableTrainers = club.trainers.mapNotNull { t ->
                t.person?.let { p ->
                    val name = listOfNotNull(p.firstName, p.lastName).joinToString(" ")
                    val trimmed = name.trim()
                    val id = p.id ?: return@mapNotNull null
                    if (trimmed.isNotBlank()) Pair(id, trimmed) else null
                }
            }.distinct()
        } catch (_: Throwable) { /* ignore */ }
    }

    fun persist() {
        scope.launch {
            try {
                val svc = ServiceLocator.notificationService
                val settings = NotificationSettings(globalEnabled = globalEnabled, rules = rules.toList())
                try {
                    svc.updateSettings(settings)
                } catch (t: Throwable) {
                    Log.e("NotificationsSettings", "Failed to update settings", t)
                }
            } catch (e: UninitializedPropertyAccessException) {
                Log.w("NotificationsSettings", "notificationService not initialized: ${e.message}")
            } catch (t: Throwable) {
                Log.e("NotificationsSettings", "Unexpected error persisting settings", t)
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Nastavení notifikací") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Zpět") }
        }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editRule = null; showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Přidat")
            }
        }
    ) { inner ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(inner)) {
            if (vmState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Box
            }

            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Globálně zapnuto")
                    Switch(checked = globalEnabled, onCheckedChange = { globalEnabled = it; persist() })
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(rules, key = { it.id }) { r ->
                        Card(modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)) {
                            Row(modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val title = if (r.name.isNotBlank()) r.name else if (r.filterType == FilterType.ALL) "Všechny události" else "Pravidlo"
                                        Text(text = title)
                                        if (r.filterType == FilterType.ALL) {
                                            Text(text = "Všechny události", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                        } else {
                                            Text(text = "Místa: ${if (r.locations.isNotEmpty()) r.locations.joinToString(", ") else "(vše)"}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                                val trainerDisplay = if (r.trainers.isNotEmpty()) r.trainers.map { t -> if (t.contains("::")) t.substringAfter("::") else t }.joinToString(", ") else "(vše)"
                                                Text(text = "Trenéři: $trainerDisplay", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                                            Text(text = "Typy: ${if (r.types.isNotEmpty()) r.types.joinToString(", ") else "(vše)"}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                                        }
                                        Text(text = "${r.timesBeforeMinutes.joinToString(", ")} min předem", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                                    }

                                Row {
                                    Switch(checked = r.enabled, onCheckedChange = { new ->
                                        val idx = rules.indexOfFirst { it.id == r.id }
                                        if (idx >= 0) {
                                            rules[idx] = r.copy(enabled = new)
                                            persist()
                                        }
                                    })
                                    IconButton(onClick = { editRule = r; showDialog = true }) { Icon(Icons.Default.Edit, contentDescription = "Upravit") }
                                    IconButton(onClick = {
                                        deletingRuleId = r.id
                                        showDeleteConfirm = true
                                    }) { Icon(Icons.Default.Delete, contentDescription = "Smazat") }
                                }
                            }
                        }
                    }
                }

                Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Zpět")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        val toDeleteId = deletingRuleId
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false; deletingRuleId = null },
            title = { Text("Smazat pravidlo") },
            text = { Text("Opravdu chcete smazat toto pravidlo?") },
            confirmButton = {
                Button(onClick = {
                    if (toDeleteId != null) {
                        rules.removeAll { it.id == toDeleteId }
                        persist()
                    }
                    showDeleteConfirm = false
                    deletingRuleId = null
                }) { Text("Smazat") }
            },
            dismissButton = {
                Button(onClick = { showDeleteConfirm = false; deletingRuleId = null }) { Text("Zrušit") }
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
        val selectedTypes = remember(dialogExisting) { mutableStateListOf<String>().apply { dialogExisting?.types?.let { addAll(it) } } }

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
                        if (matched != null) selectedTrainers.add("${matched.first}::${s}") else selectedTrainers.add(s)
                    }
                }
            }
        }

        // single time value + unit state (moved outside inner Column so save button can access)
        var timeValue by remember(dialogExisting) { mutableStateOf(dialogExisting?.timesBeforeMinutes?.firstOrNull()?.toString() ?: "60") }
        var timeUnitExpanded by remember { mutableStateOf(false) }
        val timeUnits = listOf("minuty", "hodiny")
        var timeUnit by remember(dialogExisting) { mutableStateOf("minuty") }
        var ruleName by remember(dialogExisting) { mutableStateOf(dialogExisting?.name ?: "") }

        Dialog(onDismissRequest = { showDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(title = { Text(if (dialogExisting == null) "Přidat pravidlo" else "Upravit pravidlo") })
                    Column(modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)) {
                        OutlinedTextField(value = ruleName, onValueChange = { ruleName = it }, label = { Text("Název pravidla") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                                        // Filter type selector using chips
                                        Text("Typ filtru:")
                                        Row(modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(FilterType.BY_LOCATION, FilterType.BY_TRAINER, FilterType.BY_TYPE).forEach { t ->
                                                val label = when (t) {
                                                    FilterType.BY_LOCATION -> "Místo"
                                                    FilterType.BY_TRAINER -> "Trenér"
                                                    FilterType.BY_TYPE -> "Typ"
                                                    else -> t.name
                                                }
                                                FilterChip(selected = (selType == t), onClick = { selType = t }, label = { Text(label) })
                                            }
                                        }

                                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(value = timeValue, onValueChange = { timeValue = it }, label = { Text("Čas předem") }, modifier = Modifier.weight(1f))
                                            Box {
                                                Button(onClick = { timeUnitExpanded = true }) { Text(timeUnit) }
                                                DropdownMenu(expanded = timeUnitExpanded, onDismissRequest = { timeUnitExpanded = false }) {
                                                    timeUnits.forEach { u ->
                                                        DropdownMenuItem(text = { Text(u) }, onClick = { timeUnit = u; timeUnitExpanded = false })
                                                    }
                                                }
                                            }
                                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Nebo vyber z dostupných hodnot:")
                        when (selType) {
                            FilterType.BY_LOCATION -> {
                                LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    items(availableLocations) { loc ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = selectedLocations.contains(loc), onCheckedChange = {
                                                if (it) selectedLocations.add(loc) else selectedLocations.remove(loc)
                                            })
                                            Text(loc)
                                        }
                                    }
                                }
                            }
                            FilterType.BY_TRAINER -> {
                                LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    items(availableTrainers) { tr ->
                                        val trId = tr.first
                                        val trName = tr.second
                                        Row(modifier = Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            val isChecked = selectedTrainers.any { sel ->
                                                if (sel.contains("::")) sel.substringBefore("::") == trId else sel == trName
                                            }
                                            Checkbox(checked = isChecked, onCheckedChange = { checked ->
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
                                LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    items(availableTypes) { tp ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = selectedTypes.contains(tp), onCheckedChange = {
                                                if (it) selectedTypes.add(tp) else selectedTypes.remove(tp)
                                            })
                                            Text(tp)
                                        }
                                    }
                                }
                            }
                            else -> { /* nothing */ }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                        Button(onClick = { showDialog = false }) { Text("Zrušit") }
                        Spacer(modifier = Modifier.padding(6.dp))
                        Button(onClick = {
                            // parse single time + unit
                            val tv = timeValue.trim().toIntOrNull() ?: 60
                            val minutes = if (timeUnit == "hodiny") tv * 60 else tv
                            val times = listOf(minutes)
                            val existing = dialogExisting

                            // prefer selected checkboxes when present, otherwise empty = match all
                            val finalLocations = if (selectedLocations.isNotEmpty()) selectedLocations.toList() else emptyList()
                            val finalTrainers = if (selectedTrainers.isNotEmpty()) selectedTrainers.toList() else emptyList()
                            val finalTypes = if (selectedTypes.isNotEmpty()) selectedTypes.toList() else emptyList()

                                if (existing == null) {
                                    val nr = NotificationRule(
                                        id = UUID.randomUUID().toString(),
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
                        }) { Text("Uložit") }
                    }
                }
            }
        }
    }
}
