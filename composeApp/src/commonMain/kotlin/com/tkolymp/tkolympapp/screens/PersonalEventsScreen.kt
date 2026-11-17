package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.text.font.FontWeight
import com.tkolymp.shared.utils.formatFullCalendarDate
import com.tkolymp.tkolympapp.components.SheetPrimaryButton
import com.tkolymp.tkolympapp.components.accentColor
import com.tkolymp.tkolympapp.components.cardLineBorder
import com.tkolymp.tkolympapp.components.pressClickable
import com.tkolymp.tkolympapp.components.surfaceCardColors
import com.tkolymp.tkolympapp.ui.theme.Spacing
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
import androidx.compose.foundation.layout.defaultMinSize
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
import com.tkolymp.tkolympapp.components.TopAppBar
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
import com.tkolymp.tkolympapp.components.ErrorBanner
import com.tkolymp.tkolympapp.components.ErrorState
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** "čt 19. 11. · 19:00–20:00" style line; falls back to the raw start when it can't be parsed. */
private fun formatEventWhen(ev: PersonalEvent): String {
    val date = try {
        Instant.parse(ev.startIso).toLocalDateTime(TimeZone.currentSystemDefault()).date
    } catch (_: Exception) { null }
    val time = formatEventTime(ev)
    return if (date == null) time else "${formatFullCalendarDate(date, AppStrings.currentLanguage.code, false)} · $time"
}

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
        topBar = {
        TopAppBar(
            title = { Text(AppStrings.current.personalEvents.myTrainings) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back) } }
        )
    }) { innerPadding ->
        val error = state.error
        if (error != null && state.events.isEmpty()) {
            ErrorState(
                error = error,
                modifier = Modifier.padding(innerPadding),
                onRetry = { scope.launch { vm.loadAll() } }
            )
        } else {
        LazyColumn(modifier = Modifier.padding(innerPadding).padding(bottom = bottomPadding)) {
            if (error != null) {
                item {
                    ErrorBanner(error = error, onRetry = { scope.launch { vm.loadAll() } })
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.default, end = Spacing.default, top = Spacing.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(
                        text = AppStrings.current.personalEvents.savedLocallyNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state.events, key = { ev -> ev.id }) { ev: PersonalEvent ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .padding(start = Spacing.default, end = Spacing.default, top = 10.dp)
                        .pressClickable(0.985f) { onEdit(ev.id) },
                    shape = RoundedCornerShape(16.dp),
                    colors = surfaceCardColors(),
                    border = cardLineBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = accentColor(), modifier = Modifier.size(22.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = ev.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(text = formatEventWhen(ev), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ev.location?.takeIf { it.isNotBlank() }?.let {
                                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = {
                            selectedId = ev.id
                            showConfirm = true
                        }) {
                            Icon(imageVector = Icons.Filled.Delete, contentDescription = AppStrings.current.commonActions.delete, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            onCreatePersonalEvent?.let { handler ->
                item {
                    SheetPrimaryButton(
                        text = AppStrings.current.personalEvents.newTraining,
                        onClick = handler,
                        icon = Icons.Filled.Add,
                        modifier = Modifier.padding(start = Spacing.default, end = Spacing.default, top = 14.dp, bottom = Spacing.default)
                    )
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
                    }) { Text(AppStrings.current.commonActions.delete) }
                },
                dismissButton = { TextButton(onClick = { showConfirm = false; selectedId = null }) { Text(AppStrings.current.commonActions.cancel) } },
                title = { Text(AppStrings.current.commonActions.confirm) },
                text = { Text(AppStrings.current.personalEvents.confirmDelete) }
            )
        }
        }
    }

