package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.management.ManagedAnnouncementStatus
import com.tkolymp.shared.viewmodels.AnnouncementEditViewModel
import com.tkolymp.tkolympapp.components.ErrorBanner
import com.tkolymp.tkolympapp.components.OptionChips
import com.tkolymp.tkolympapp.components.RichHtmlEditor
import com.tkolymp.tkolympapp.components.rememberRichHtmlEditorState
import com.tkolymp.tkolympapp.components.SwitchRow

/** Trainer / admin form for creating ([announcementId] null) or editing an announcement. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementEditScreen(
    announcementId: Long?,
    initialSticky: Boolean,
    onSaved: (Long) -> Unit,
    onBack: () -> Unit,
    bottomPadding: Dp = 0.dp,
) {
    val viewModel = viewModel(key = "announcement_edit_${announcementId ?: "new"}") { AnnouncementEditViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strings = AppStrings.current.management
    val currentOnSaved by rememberUpdatedState(onSaved)

    val bodyEditor = rememberRichHtmlEditorState()

    LaunchedEffect(announcementId) { viewModel.load(announcementId, sticky = initialSticky) }
    // Seed the editor once the stored body has been loaded.
    LaunchedEffect(state.isLoaded) {
        if (state.isLoaded) bodyEditor.loadOnce(state.draft.body)
    }
    LaunchedEffect(state.savedAnnouncementId) { state.savedAnnouncementId?.let { currentOnSaved(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) strings.editAnnouncement else strings.newAnnouncement) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                    }
                },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { viewModel.save(bodyEditor.currentHtml()) }, enabled = !state.isLoading) {
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
                        value = draft.title,
                        onValueChange = { v -> viewModel.updateDraft { it.copy(title = v) } },
                        label = { Text(strings.title) },
                        singleLine = true,
                        isError = state.validationError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    RichHtmlEditor(state = bodyEditor, label = strings.body, minLines = 8)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.status, style = MaterialTheme.typography.titleSmall)
                    val statuses = ManagedAnnouncementStatus.entries
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        statuses.forEachIndexed { index, status ->
                            SegmentedButton(
                                selected = draft.status == status,
                                onClick = { viewModel.updateDraft { it.copy(status = status) } },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = statuses.size),
                            ) {
                                Text(
                                    when (status) {
                                        ManagedAnnouncementStatus.PUBLISHED -> strings.statusPublished
                                        ManagedAnnouncementStatus.DRAFT -> strings.statusDraft
                                        ManagedAnnouncementStatus.ARCHIVED -> strings.statusArchived
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                    SwitchRow(strings.isSticky, draft.isSticky, { v -> viewModel.updateDraft { it.copy(isSticky = v) } })
                }
            }

            if (state.options.cohorts.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    OptionChips(
                        label = strings.audience,
                        options = state.options.cohorts,
                        selectedIds = draft.cohortIds,
                        onToggle = viewModel::toggleCohort,
                        enabled = !state.isEditing,
                        hint = if (state.isEditing) strings.audienceEditHint else strings.audienceHint,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}
