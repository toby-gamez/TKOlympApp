package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.viewmodels.ContentManagementEffect
import com.tkolymp.shared.viewmodels.ContentManagementViewModel
import com.tkolymp.tkolympapp.ui.theme.AppTheme
import kotlinx.serialization.json.JsonObject

/** The shared trainer/admin view model of the current navigation entry. */
@Composable
fun rememberContentManagementViewModel(): ContentManagementViewModel {
    val vm = viewModel { ContentManagementViewModel() }
    LaunchedEffect(vm) { vm.refreshPermissions() }
    return vm
}

/** Top-bar "+" shown only to trainers and administrators. */
@Composable
fun ManageCreateAction(label: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    if (onClick == null) return
    val vm = rememberContentManagementViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.permissions.canManage) {
        IconButton(onClick = onClick, modifier = modifier) {
            Icon(Icons.Filled.Add, contentDescription = label)
        }
    }
}

/**
 * Overflow menu with edit / cancel-restore / delete for an event the user may manage.
 * [eventJson] is the loaded `eventInstance`; nothing is shown when the user may not edit it.
 */
@Composable
fun EventManageActions(
    eventId: Long,
    eventJson: JsonObject?,
    isCancelled: Boolean,
    onEdit: ((Long) -> Unit)?,
    onDeleted: () -> Unit,
    onMessage: (String) -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = rememberContentManagementViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val strings = AppStrings.current.management
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    val currentOnMessage by rememberUpdatedState(onMessage)
    val currentOnChanged by rememberUpdatedState(onChanged)
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                ContentManagementEffect.EventDeleted -> {
                    currentOnMessage(strings.eventDeleted)
                    currentOnDeleted()
                }
                is ContentManagementEffect.EventCancelledChanged -> {
                    currentOnMessage(if (effect.cancelled) strings.eventCancelled else strings.eventRestored)
                    currentOnChanged()
                }
                ContentManagementEffect.AnnouncementDeleted -> Unit
            }
        }
    }

    // Reading state.permissions keeps this recomposing when permissions arrive.
    if (!state.permissions.canManage || !vm.canEditEvent(eventJson)) return

    ManageOverflowMenu(
        isCancelled = isCancelled,
        enabled = !state.isWorking,
        onEdit = onEdit?.let { { it(eventId) } },
        onToggleCancelled = { vm.setEventCancelled(eventId, !isCancelled) },
        onDelete = { confirmDelete = true },
        modifier = modifier,
    )

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = strings.confirmDeleteEventTitle,
            text = strings.confirmDeleteEventText,
            onConfirm = { confirmDelete = false; vm.deleteEvent(eventId) },
            onDismiss = { confirmDelete = false },
        )
    }
    ManagementErrorDialog(message = state.error?.message, onDismiss = vm::clearError)
}

/** Edit and delete icons for an announcement the user may manage. */
@Composable
fun AnnouncementManageActions(
    announcementId: Long,
    authorId: String?,
    onEdit: ((Long) -> Unit)?,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = rememberContentManagementViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val strings = AppStrings.current.management
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            if (effect == ContentManagementEffect.AnnouncementDeleted) currentOnDeleted()
        }
    }

    if (!state.permissions.canEditAnnouncement(authorId)) return

    Row(modifier = modifier) {
        if (onEdit != null) {
            IconButton(onClick = { onEdit(announcementId) }, enabled = !state.isWorking) {
                Icon(Icons.Filled.Edit, contentDescription = strings.edit)
            }
        }
        IconButton(onClick = { confirmDelete = true }, enabled = !state.isWorking) {
            Icon(Icons.Filled.Delete, contentDescription = strings.delete)
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = strings.confirmDeleteAnnouncementTitle,
            text = strings.confirmDeleteAnnouncementText,
            onConfirm = { confirmDelete = false; vm.deleteAnnouncement(announcementId) },
            onDismiss = { confirmDelete = false },
        )
    }
    ManagementErrorDialog(message = state.error?.message, onDismiss = vm::clearError)
}

/** "Trainer mode" / "Administrator mode" label, shown only to users who can manage content. */
@Composable
fun ManagementModeBadge(modifier: Modifier = Modifier) {
    val vm = rememberContentManagementViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.permissions.canManage) {
        ManagementModeLabel(isAdmin = state.permissions.isAdmin, modifier = modifier)
    }
}

@Composable
fun ManagementModeLabel(isAdmin: Boolean, modifier: Modifier = Modifier) {
    val strings = AppStrings.current.management
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = if (isAdmin) strings.adminMode else strings.trainerMode,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Stateless overflow menu used by [EventManageActions]. */
@Composable
fun ManageOverflowMenu(
    isCancelled: Boolean,
    enabled: Boolean,
    onEdit: (() -> Unit)?,
    onToggleCancelled: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    val strings = AppStrings.current.management
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Filled.MoreVert, contentDescription = strings.manage)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onEdit != null) {
                DropdownMenuItem(
                    text = { Text(strings.edit) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { expanded = false; onEdit() },
                )
            }
            DropdownMenuItem(
                text = { Text(if (isCancelled) strings.restoreEvent else strings.cancelEvent) },
                leadingIcon = { Icon(if (isCancelled) Icons.Filled.Restore else Icons.Filled.EventBusy, contentDescription = null) },
                onClick = { expanded = false; onToggleCancelled() },
            )
            DropdownMenuItem(
                text = { Text(strings.delete, color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

@Composable
fun ConfirmDeleteDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(AppStrings.current.management.delete, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppStrings.current.commonActions.cancel) } },
    )
}

@Composable
private fun ManagementErrorDialog(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.current.commonActions.error) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(AppStrings.current.commonActions.ok) } },
    )
}

@Preview(name = "ManageOverflowMenu — Light")
@Composable
private fun ManageOverflowMenuPreviewLight() {
    AppTheme(darkTheme = false) {
        ManageOverflowMenu(isCancelled = false, enabled = true, onEdit = {}, onToggleCancelled = {}, onDelete = {}, initiallyExpanded = true)
    }
}

@Preview(name = "ManageOverflowMenu — Dark")
@Composable
private fun ManageOverflowMenuPreviewDark() {
    AppTheme(darkTheme = true) {
        ManageOverflowMenu(isCancelled = true, enabled = true, onEdit = {}, onToggleCancelled = {}, onDelete = {}, initiallyExpanded = true)
    }
}

@Preview(name = "ConfirmDeleteDialog — Light")
@Composable
private fun ConfirmDeleteDialogPreviewLight() {
    AppTheme(darkTheme = false) {
        ConfirmDeleteDialog(
            title = AppStrings.current.management.confirmDeleteEventTitle,
            text = AppStrings.current.management.confirmDeleteEventText,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "ConfirmDeleteDialog — Dark")
@Composable
private fun ConfirmDeleteDialogPreviewDark() {
    AppTheme(darkTheme = true) {
        ConfirmDeleteDialog(
            title = AppStrings.current.management.confirmDeleteAnnouncementTitle,
            text = AppStrings.current.management.confirmDeleteAnnouncementText,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "ManagementModeLabel — Light")
@Composable
private fun ManagementModeLabelPreviewLight() {
    AppTheme(darkTheme = false) { ManagementModeLabel(isAdmin = false) }
}

@Preview(name = "ManagementModeLabel — Dark")
@Composable
private fun ManagementModeLabelPreviewDark() {
    AppTheme(darkTheme = true) { ManagementModeLabel(isAdmin = true) }
}
