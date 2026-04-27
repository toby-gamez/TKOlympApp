package com.tkolymp.tkolympapp.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.utils.formatTimesWithDate
import com.tkolymp.shared.utils.formatTimesWithDateAlways
import com.tkolymp.shared.utils.formatTimesWithDayOfWeek
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.tkolymp.shared.viewmodels.FreeLessonResult
import com.tkolymp.shared.viewmodels.FreeLessonsViewModel
import com.tkolymp.shared.language.AppStrings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeLessonsScreen(
    onBack: () -> Unit = {},
    onOpenEvent: (Long) -> Unit = {}
) {
    val viewModel = viewModel<FreeLessonsViewModel>()
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val expandedCancelledIds = remember { mutableStateMapOf<String, Boolean>() }
    var showScoring by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.current.freeLessons.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                    }
                }
                , actions = {
                    IconButton(onClick = { showScoring = true }) {
                        Icon(Icons.Default.Info, contentDescription = AppStrings.current.freeLessons.infoTitle)
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: "", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { scope.launch { viewModel.load() } }) {
                            Text(AppStrings.current.commonActions.retry)
                        }
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Section: Nahradit za zrušené
                    if (state.hasCancelledToShow) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            AppStrings.current.freeLessons.replaceCancelledLabel,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        state.cancelledMineInstances.forEach { cancelled ->
                            val idStr = cancelled.id.toString()
                            val expanded = expandedCancelledIds[idStr] ?: true
                            CancelledLessonBlock(
                                cancelled = cancelled,
                                replacements = state.replacementResults[idStr] ?: emptyList(),
                                expanded = expanded,
                                onToggleExpand = { expandedCancelledIds[idStr] = !expanded },
                                onDismiss = { scope.launch { viewModel.dismissCancelled(idStr) } },
                                onOpenEvent = onOpenEvent
                            )
                        }
                    }

                    // Section: Nejlepší nálezy
                    val bestLabel = AppStrings.current.freeLessons.bestLabel
                    if (state.bestFinds.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            bestLabel,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        state.bestFinds.forEach { result ->
                            FreeLessonCard(result = result, onOpenEvent = onOpenEvent)
                        }
                    }

                    // Section: Ostatní (only when there are NO cancelled to show)
                    if (!state.hasCancelledToShow && state.otherFinds.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            AppStrings.current.freeLessons.otherLabel,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        state.otherFinds.forEach { result ->
                            FreeLessonCard(result = result, onOpenEvent = onOpenEvent)
                        }
                    }

                    // When cancelled shown, merge other into best section
                    if (state.hasCancelledToShow && state.otherFinds.isNotEmpty()) {
                        state.otherFinds.forEach { result ->
                            FreeLessonCard(result = result, onOpenEvent = onOpenEvent)
                        }
                    }

                    if (state.bestFinds.isEmpty() && state.otherFinds.isEmpty() && !state.isLoading) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                AppStrings.current.freeLessons.noneFound,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    if (showScoring) {
        AlertDialog(
            onDismissRequest = { showScoring = false },
            title = { Text(AppStrings.current.freeLessons.infoTitle) },
            text = {
                Column {
                    Text(AppStrings.current.freeLessons.scoringHeader)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(AppStrings.current.freeLessons.scoringBase)
                    Text(AppStrings.current.freeLessons.scoringDay)
                    Text(AppStrings.current.freeLessons.scoringNoTraining)
                    Text(AppStrings.current.freeLessons.scoringSameLocation)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(AppStrings.current.freeLessons.scoringNote)
                }
            },
            confirmButton = {
                TextButton(onClick = { showScoring = false }) { Text(AppStrings.current.freeLessons.scoringOk) }
            }
        )
    }
}

@Composable
private fun CancelledLessonBlock(
    cancelled: EventInstance,
    replacements: List<FreeLessonResult>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onDismiss: () -> Unit,
    onOpenEvent: (Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        displayName(cancelled.event),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        formatTimesWithDayOfWeek(cancelled.since, cancelled.until),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        AppStrings.current.freeLessons.cancelledLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) AppStrings.current.freeLessons.hideReplacements else AppStrings.current.freeLessons.showReplacements
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                if (replacements.isEmpty()) {
                    Text(
                        AppStrings.current.freeLessons.noReplacements,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    replacements.forEach { result ->
                        FreeLessonCard(result = result, onOpenEvent = onOpenEvent, compact = true)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(AppStrings.current.freeLessons.dontBother)
                }
            }
        }
    }
}

@Composable
private fun FreeLessonCard(
    result: FreeLessonResult,
    onOpenEvent: (Long) -> Unit,
    compact: Boolean = false
) {
    val inst = result.instance
    val eventId = inst.event?.id ?: return
    val name = displayName(inst.event)
    val timeText = formatTimesWithDayOfWeek(inst.since, inst.until)
    val location = inst.event?.locationText?.takeIf { it.isNotBlank() }
        ?: inst.event?.location?.name?.takeIf { it.isNotBlank() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onOpenEvent(eventId) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 8.dp else 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    val tip = result.tip
                    if (!tip.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            tip,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "★ ${result.score}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(timeText, style = MaterialTheme.typography.bodySmall)
            if (!location.isNullOrBlank()) {
                Text(location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun displayName(event: com.tkolymp.shared.event.Event?): String {
    if (event == null) return AppStrings.current.dialogs.noName
    val isLesson = event.type?.equals("lesson", ignoreCase = true) == true
    return if (isLesson) {
        event.eventTrainersList.firstOrNull()?.takeIf { it.isNotBlank() } ?: event.name ?: "(bez názvu)"
    } else {
        event.name ?: AppStrings.current.dialogs.noName
    }
}
