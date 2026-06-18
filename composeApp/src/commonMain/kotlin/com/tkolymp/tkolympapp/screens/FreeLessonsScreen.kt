package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.utils.formatTimesWithDayOfWeek
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import com.tkolymp.shared.viewmodels.FreeLessonResult
import com.tkolymp.shared.viewmodels.FreeLessonsViewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.tkolympapp.components.RenderEventContent
import com.tkolymp.tkolympapp.util.StaggeredItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeLessonsScreen(
    onBack: () -> Unit = {},
    onOpenEvent: (Long) -> Unit = {}
) {
    val viewModel = viewModel<FreeLessonsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val expandedCancelledIds = remember { mutableStateMapOf<String, Boolean>() }
    var showScoring by remember { mutableStateOf(false) }
    var cardsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }
    LaunchedEffect(state.isLoading) { if (!state.isLoading) cardsVisible = true }

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
                        Text(state.error?.message ?: "", style = MaterialTheme.typography.bodyMedium)
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        AppStrings.current.freeLessons.screenSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    // Section: Nahradit za zrušené
                    if (state.hasCancelledToShow) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            AppStrings.current.freeLessons.replaceCancelledLabel,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        state.cancelledMineInstances.forEachIndexed { i, cancelled ->
                            val idStr = cancelled.id.toString()
                            val expanded = expandedCancelledIds[idStr] ?: true
                            StaggeredItem(index = i, visible = cardsVisible, baseDelayMs = 60) {
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
                    }

                    // Section: Doporučeno pro tebe
                    if (state.bestFinds.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            AppStrings.current.freeLessons.bestLabel,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        state.bestFinds.forEachIndexed { i, result ->
                            StaggeredItem(index = i, visible = cardsVisible, baseDelayMs = 50) {
                                FreeLessonCard(result = result, onOpenEvent = onOpenEvent, isTopPick = i == 0)
                            }
                        }
                    }

                    // Section: Další možnosti (only when there are NO cancelled to show)
                    if (!state.hasCancelledToShow && state.otherFinds.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            AppStrings.current.freeLessons.otherLabel,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        state.otherFinds.forEachIndexed { i, result ->
                            StaggeredItem(index = i, visible = cardsVisible, baseDelayMs = 50) {
                                FreeLessonCard(result = result, onOpenEvent = onOpenEvent)
                            }
                        }
                    }

                    // When cancelled shown, merge other into best section
                    if (state.hasCancelledToShow && state.otherFinds.isNotEmpty()) {
                        state.otherFinds.forEachIndexed { i, result ->
                            StaggeredItem(index = i, visible = cardsVisible, baseDelayMs = 50) {
                                FreeLessonCard(result = result, onOpenEvent = onOpenEvent)
                            }
                        }
                    }

                    if (state.bestFinds.isEmpty() && state.otherFinds.isEmpty() && !state.isLoading) {
                        Spacer(modifier = Modifier.height(48.dp))
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.padding(bottom = 12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
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
                    listOf(
                        AppStrings.current.freeLessons.scoringBase,
                        AppStrings.current.freeLessons.scoringDay,
                        AppStrings.current.freeLessons.scoringNoTraining,
                        AppStrings.current.freeLessons.scoringSameLocation,
                    ).filter { it.isNotBlank() }.forEach { Text(it) }
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
    compact: Boolean = false,
    isTopPick: Boolean = false
) {
    val inst = result.instance
    val eventId = (inst.event?.id as? Number)?.toLong() ?: return

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
            if (isTopPick && !compact) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        AppStrings.current.freeLessons.topPickLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            RenderEventContent(item = inst, tip = result.tip, showType = false, showDayOfWeek = true, modifier = Modifier.fillMaxWidth())
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
