package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable

/**
 * Platform-specific MoreVert icon button with Export/Import dropdown.
 * On Android: uses ActivityResultContracts to open file pickers via SAF.
 * [onGetExportJson] is called to get the JSON string to be exported.
 * [onImportJson] is called with the imported JSON string.
 * [onMessage] delivers toast/snackbar text to be shown to the user.
 */
@Composable
expect fun NotificationExportImportButton(
    onGetExportJson: () -> String,
    onImportJson: (String) -> Unit,
    onMessage: (String) -> Unit
)
