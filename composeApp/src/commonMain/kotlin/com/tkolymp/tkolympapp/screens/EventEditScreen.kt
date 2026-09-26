package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.management.ManagedEventType
import com.tkolymp.shared.viewmodels.EventEditViewModel
import com.tkolymp.tkolympapp.components.DateTimeFields
import com.tkolymp.tkolympapp.components.ErrorBanner
import com.tkolymp.tkolympapp.components.OptionChips
import com.tkolymp.tkolympapp.components.SwitchRow

internal fun ManagedEventType.label(): String {
    val s = AppStrings.current.management
    return when (this) {
        ManagedEventType.LESSON -> s.typeLesson
        ManagedEventType.GROUP -> s.typeGroup
        ManagedEventType.CAMP -> s.typeCamp
        ManagedEventType.RESERVATION -> s.typeReservation
        ManagedEventType.HOLIDAY -> s.typeHoliday
    }
}

/** Trainer / admin form for creating ([instanceId] null) or editing a club event. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    instanceId: Long?,
    onSaved: (Long) -> Unit,
    onBack: () -> Unit,
    bottomPadding: Dp = 0.dp,
) {
    val viewModel = viewModel(key = "event_edit_${instanceId ?: "new"}") { EventEditViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strings = AppStrings.current.management
    val currentOnSaved by rememberUpdatedState(onSaved)

    LaunchedEffect(instanceId) { viewModel.load(instanceId) }
    LaunchedEffect(state.savedInstanceId) { state.savedInstanceId?.let { currentOnSaved(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) strings.editEvent else strings.newEvent) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                    }
                },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = viewModel::save, enabled = !state.isLoading) {
                            Icon(Icons.Filled.Check, contentDescription = AppStrings.current.commonActions.save)
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val draft = state.draft
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.validationError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            state.error?.let { ErrorBanner(error = it, onRetry = null) }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { v -> viewModel.updateDraft { it.copy(name = v) } },
                        label = { Text(strings.name) },
                        singleLine = true,
                        isError = state.validationError == strings.nameRequired,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(strings.type, style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ManagedEventType.entries.forEach { type ->
                            FilterChip(
                                selected = draft.type == type,
                                onClick = { viewModel.updateDraft { it.copy(type = type) } },
                                label = { Text(type.label()) },
                            )
                        }
                    }
                    DateTimeFields(
                        label = strings.start,
                        value = state.start,
                        onDateChange = viewModel::setStartDate,
                        onTimeChange = viewModel::setStartTime,
                    )
                    DateTimeFields(
                        label = strings.end,
                        value = state.end,
                        onDateChange = viewModel::setEndDate,
                        onTimeChange = viewModel::setEndTime,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    var locationExpanded by remember { mutableStateOf(false) }
                    val locationName = state.options.locations.firstOrNull { it.id == draft.locationId }?.name ?: strings.noLocation
                    ExposedDropdownMenuBox(expanded = locationExpanded, onExpandedChange = { locationExpanded = it }) {
                        OutlinedTextField(
                            value = locationName,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text(strings.location) },
                            leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = locationExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(expanded = locationExpanded, onDismissRequest = { locationExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(strings.noLocation) },
                                onClick = { viewModel.updateDraft { it.copy(locationId = null) }; locationExpanded = false },
                            )
                            state.options.locations.forEach { loc ->
                                DropdownMenuItem(
                                    text = { Text(loc.name) },
                                    onClick = { viewModel.updateDraft { it.copy(locationId = loc.id) }; locationExpanded = false },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = draft.locationText,
                        onValueChange = { v -> viewModel.updateDraft { it.copy(locationText = v) } },
                        label = { Text(strings.locationText) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.capacityText,
                        onValueChange = viewModel::setCapacityText,
                        label = { Text(strings.capacity) },
                        singleLine = true,
                        isError = state.validationError == strings.invalidCapacity,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val hint = if (state.isEditing) strings.trainersCohortsEditHint else null
                    OptionChips(
                        label = strings.trainers,
                        options = state.options.trainers,
                        selectedIds = draft.trainerPersonIds,
                        onToggle = viewModel::toggleTrainer,
                        enabled = !state.isEditing,
                        hint = hint,
                    )
                    OptionChips(
                        label = strings.cohorts,
                        options = state.options.cohorts,
                        selectedIds = draft.cohortIds,
                        onToggle = viewModel::toggleCohort,
                        enabled = !state.isEditing,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    SwitchRow(strings.isVisible, draft.isVisible, { v -> viewModel.updateDraft { it.copy(isVisible = v) } })
                    SwitchRow(strings.isPublic, draft.isPublic, { v -> viewModel.updateDraft { it.copy(isPublic = v) } })
                    SwitchRow(strings.isLocked, draft.isLocked, { v -> viewModel.updateDraft { it.copy(isLocked = v) } })
                    SwitchRow(strings.enableNotes, draft.enableNotes, { v -> viewModel.updateDraft { it.copy(enableNotes = v) } })
                    if (state.isEditing) {
                        SwitchRow(strings.isCancelled, draft.isCancelled, { v -> viewModel.updateDraft { it.copy(isCancelled = v) } })
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draft.summary,
                        onValueChange = { v -> viewModel.updateDraft { it.copy(summary = v) } },
                        label = { Text(strings.summary) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.descriptionText,
                        onValueChange = viewModel::setDescription,
                        label = { Text(strings.description) },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.descriptionIsRawHtml) {
                        Text(strings.rawHtmlHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
