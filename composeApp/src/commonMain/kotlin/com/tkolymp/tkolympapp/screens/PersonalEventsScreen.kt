package com.tkolymp.tkolympapp.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun formatEventTime(ev: PersonalEvent): String {
    return try {
        val s = Instant.parse(ev.startIso).toLocalDateTime(TimeZone.currentSystemDefault())
        val e = Instant.parse(ev.endIso).toLocalDateTime(TimeZone.currentSystemDefault())
        val startTime = "${s.time.hour.toString().padStart(2,'0')}:${s.time.minute.toString().padStart(2,'0')}"
        val endTime = "${e.time.hour.toString().padStart(2,'0')}:${e.time.minute.toString().padStart(2,'0')}"
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
    val state by vm.state.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showFab by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadAll(); showFab = true }

    Scaffold(
        floatingActionButton = {
            onCreatePersonalEvent?.let { handler ->
                AnimatedVisibility(
                    visible = showFab,
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(tween(200))
                ) {
                    FloatingActionButton(onClick = handler) {
                        Icon(imageVector = Icons.Filled.FitnessCenter, contentDescription = AppStrings.current.personalEvents.newTraining)
                    }
                }
            }
        },
        topBar = {
        TopAppBar(
            title = { Text(AppStrings.current.personalEvents.myTrainings) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } }
        )
    }) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding).padding(bottom = bottomPadding)) {
            items(state.events, key = { ev -> ev.id }) { ev: PersonalEvent ->
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .animateItem()
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
                            Icon(imageVector = Icons.Filled.Delete, contentDescription = AppStrings.current.commonActions.delete)
                        }
                    }
                }
            }
            item {
                Text(
                    text = AppStrings.current.personalEvents.savedLocallyNote,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )
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
                    }) { Text(AppStrings.current.commonActions.delete) }
                },
                dismissButton = { TextButton(onClick = { showConfirm = false; selectedId = null }) { Text(AppStrings.current.commonActions.cancel) } },
                title = { Text(AppStrings.current.commonActions.confirm) },
                text = { Text(AppStrings.current.personalEvents.confirmDelete) }
            )
        }
        }
    }

