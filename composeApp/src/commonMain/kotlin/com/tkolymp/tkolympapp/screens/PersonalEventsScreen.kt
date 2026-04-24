package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.personalevents.PersonalEvent
import com.tkolymp.shared.viewmodels.PersonalEventsViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun formatEventTime(ev: PersonalEvent): String {
    return try {
        val s = Instant.parse(ev.startIso).toLocalDateTime(TimeZone.currentSystemDefault())
        val e = Instant.parse(ev.endIso).toLocalDateTime(TimeZone.currentSystemDefault())
        val startTime = "%02d:%02d".format(s.time.hour, s.time.minute)
        val endTime = "%02d:%02d".format(e.time.hour, e.time.minute)
        "$startTime–$endTime"
    } catch (_: Exception) {
        ev.startIso
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalEventsScreen(
    onBack: () -> Unit = {},
    onEdit: (String?) -> Unit = {},
    onCreatePersonalEvent: (() -> Unit)? = null,
    bottomPadding: Dp = 0.dp
) {
    val vm = viewModel<PersonalEventsViewModel>()
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.loadAll() }

    Scaffold(
        floatingActionButton = {
            onCreatePersonalEvent?.let {
                FloatingActionButton(onClick = it) {
                    Icon(imageVector = Icons.Filled.FitnessCenter, contentDescription = AppStrings.current.personalEvents.newTraining)
                }
            }
        },
        topBar = {
        TopAppBar(
            title = { Text(AppStrings.current.personalEvents.myTrainings) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null) } }
        )
    }) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding).padding(bottom = bottomPadding)) {
            items(state.events) { ev: PersonalEvent ->
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clickable { onEdit(ev.id) }) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = ev.title, style = MaterialTheme.typography.titleMedium)
                            Text(text = formatEventTime(ev), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            selectedId = ev.id
                            showConfirm = true
                        }) {
                            Icon(imageVector = Icons.Filled.Delete, contentDescription = "Smazat")
                        }
                    }
                }
            }
        }

        if (showConfirm) {
            AlertDialog(
                onDismissRequest = { showConfirm = false; selectedId = null },
                confirmButton = {
                    TextButton(onClick = {
                        val id = selectedId
                        showConfirm = false
                        selectedId = null
                        if (id != null) scope.launch { vm.delete(id) }
                    }) { Text("Smazat") }
                },
                dismissButton = { TextButton(onClick = { showConfirm = false; selectedId = null }) { Text("Zrušit") } },
                title = { Text("Potvrdit") },
                text = { Text("Opravdu chcete smazat tuto událost?") }
            )
        }
        }
    }

