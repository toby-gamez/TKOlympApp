package com.tkolymp.tkolympapp.Screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    var availableTrainers by remember { mutableStateOf<List<String>>(emptyList()) }
    val availableTypes = listOf("CAMP", "LESSON", "GROUP", "RESERVATION", "HOLIDAY")
    val rules = remember { mutableStateListOf<NotificationRule>() }
    var globalEnabled by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }
    var editRule by remember { mutableStateOf<NotificationRule?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            try {
                val svc = ServiceLocator.notificationService
                val settings = svc.getSettings()
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
                        if (trimmed.isNotBlank()) trimmed else null
                    }
                }.distinct()
            } catch (_: Throwable) { /* ignore */ }

        } finally {
            loading = false
        }
    }

    fun persist() {
        scope.launch {
            try {
                val svc = ServiceLocator.notificationService
                val settings =
                    NotificationSettings(globalEnabled = globalEnabled, rules = rules.toList())
                svc.updateSettings(settings)
            } catch (_: UninitializedPropertyAccessException) {
                // ignore
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Nastavení notifikací") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editRule = null; showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Přidat")
            }
        }
    ) { inner ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(inner)) {
            if (loading) {
                Text("Načítání…", modifier = Modifier.padding(16.dp))
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
                                    val desc = when {
                                        r.locations.isNotEmpty() -> "Místa: ${r.locations.joinToString(", ")}" 
                                        r.trainers.isNotEmpty() -> "Trenéři: ${r.trainers.joinToString(", ")}" 
                                        r.types.isNotEmpty() -> "Typy: ${r.types.joinToString(", ")}" 
                                        else -> when (r.filterType) {
                                            FilterType.ALL -> "Všechny události"
                                            FilterType.BY_LOCATION -> "Místo: ${r.filterValue ?: "(nezadáno)"}"
                                            FilterType.BY_TRAINER -> "Trenér: ${r.filterValue ?: "(nezadáno)"}"
                                            FilterType.BY_TYPE -> "Typ: ${r.filterValue ?: "(nezadáno)"}"
                                        }
                                    }
                                    Text(text = desc)
                                    Text(text = "Časy: ${r.timesBeforeMinutes.joinToString(", ")} min před", modifier = Modifier.padding(top = 6.dp))
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
                                        rules.removeAll { it.id == r.id }
                                        persist()
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

    if (showDialog) {
        val dialogExisting = editRule
        var selType by remember(dialogExisting) { mutableStateOf(dialogExisting?.filterType ?: FilterType.ALL) }
        var value by remember(dialogExisting) { mutableStateOf( when {
            dialogExisting?.locations?.isNotEmpty() == true -> dialogExisting.locations.joinToString(",")
            dialogExisting?.trainers?.isNotEmpty() == true -> dialogExisting.trainers.joinToString(",")
            dialogExisting?.types?.isNotEmpty() == true -> dialogExisting.types.joinToString(",")
            else -> dialogExisting?.filterValue ?: ""
        }) }
        var timesText by remember(dialogExisting) { mutableStateOf(dialogExisting?.timesBeforeMinutes?.joinToString(",") ?: "60,5") }

        val selectedLocations = remember(dialogExisting) { mutableStateListOf<String>().apply { dialogExisting?.locations?.let { addAll(it) } } }
        val selectedTrainers = remember(dialogExisting) { mutableStateListOf<String>().apply { dialogExisting?.trainers?.let { addAll(it) } } }
        val selectedTypes = remember(dialogExisting) { mutableStateListOf<String>().apply { dialogExisting?.types?.let { addAll(it) } } }

        Dialog(onDismissRequest = { showDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(title = { Text(if (dialogExisting == null) "Přidat pravidlo" else "Upravit pravidlo") })
                    Column(modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)) {
                        // Simple selector via clickable texts
                        Text("Typ filtru:")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(FilterType.ALL, FilterType.BY_LOCATION, FilterType.BY_TRAINER, FilterType.BY_TYPE).forEach { t ->
                                val label = when (t) { FilterType.ALL -> "Vše"; FilterType.BY_LOCATION -> "Místo"; FilterType.BY_TRAINER -> "Trenér"; FilterType.BY_TYPE -> "Typ" }
                                Text(label, modifier = Modifier.clickable { selType = t }.padding(6.dp))
                            }
                        }
                        OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Hodnota filtru (čárkou oddělené hodnoty)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        OutlinedTextField(value = timesText, onValueChange = { timesText = it }, label = { Text("Časy před (minuty, čárkou oddělené)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

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
                                        Row(modifier = Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = selectedTrainers.contains(tr), onCheckedChange = {
                                                if (it) selectedTrainers.add(tr) else selectedTrainers.remove(tr)
                                            })
                                            Text(tr)
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
                            // parse times
                            val times = timesText.split(',').mapNotNull { it.trim().toIntOrNull() }.ifEmpty { listOf(60,5) }
                            val typedLists = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                            val existing = dialogExisting

                            // prefer selected checkboxes when present, otherwise fall back to typed value
                            val finalLocations = if (selectedLocations.isNotEmpty()) selectedLocations.toList() else typedLists
                            val finalTrainers = if (selectedTrainers.isNotEmpty()) selectedTrainers.toList() else typedLists
                            val finalTypes = if (selectedTypes.isNotEmpty()) selectedTypes.toList() else typedLists

                            if (existing == null) {
                                val nr = when (selType) {
                                    FilterType.BY_LOCATION -> NotificationRule(id = UUID.randomUUID().toString(), enabled = true, filterType = selType, locations = finalLocations, timesBeforeMinutes = times)
                                    FilterType.BY_TRAINER -> NotificationRule(id = UUID.randomUUID().toString(), enabled = true, filterType = selType, trainers = finalTrainers, timesBeforeMinutes = times)
                                    FilterType.BY_TYPE -> NotificationRule(id = UUID.randomUUID().toString(), enabled = true, filterType = selType, types = finalTypes, timesBeforeMinutes = times)
                                    else -> NotificationRule(id = UUID.randomUUID().toString(), enabled = true, filterType = selType, filterValue = value.ifBlank { null }, timesBeforeMinutes = times)
                                }
                                rules.add(nr)
                            } else {
                                val idx = rules.indexOfFirst { it.id == existing.id }
                                if (idx >= 0) {
                                    val updated = when (selType) {
                                        FilterType.BY_LOCATION -> existing.copy(filterType = selType, locations = finalLocations, trainers = emptyList(), types = emptyList(), filterValue = null, timesBeforeMinutes = times)
                                        FilterType.BY_TRAINER -> existing.copy(filterType = selType, trainers = finalTrainers, locations = emptyList(), types = emptyList(), filterValue = null, timesBeforeMinutes = times)
                                        FilterType.BY_TYPE -> existing.copy(filterType = selType, types = finalTypes, locations = emptyList(), trainers = emptyList(), filterValue = null, timesBeforeMinutes = times)
                                        else -> existing.copy(filterType = selType, filterValue = value.ifBlank { null }, locations = emptyList(), trainers = emptyList(), types = emptyList(), timesBeforeMinutes = times)
                                    }
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
