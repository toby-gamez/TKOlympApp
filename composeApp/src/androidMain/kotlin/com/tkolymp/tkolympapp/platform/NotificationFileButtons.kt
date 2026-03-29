package com.tkolymp.tkolympapp.platform

import android.util.Log
import kotlinx.coroutines.CancellationException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tkolymp.shared.language.AppStrings
import kotlinx.coroutines.launch

@Composable
actual fun NotificationExportImportButton(
    onGetExportJson: () -> String,
    onImportJson: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val jsonStr = onGetExportJson()
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(jsonStr.toByteArray(Charsets.UTF_8))
                    }
                    onMessage(AppStrings.current.importExport.exportSuccessful)
                } catch (e: CancellationException) { throw e } catch (t: Exception) {
                    Log.e("NotificationsSettings", "Export failed", t)
                    onMessage(AppStrings.current.importExport.exportFailed)
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val jsonStr = context.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    if (jsonStr != null) {
                        onImportJson(jsonStr)
                    }
                } catch (e: CancellationException) { throw e } catch (t: Exception) {
                    Log.e("NotificationsSettings", "Import failed", t)
                    onMessage("${AppStrings.current.importExport.importFailed}: ${t.message}")
                }
            }
        }
    }

    IconButton(onClick = { menuExpanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = "Více")
    }
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(
            text = { Text(AppStrings.current.importExport.exportJson) },
            onClick = { menuExpanded = false; exportLauncher.launch("notification_settings.json") }
        )
        DropdownMenuItem(
            text = { Text(AppStrings.current.importExport.importJson) },
            onClick = { menuExpanded = false; importLauncher.launch(arrayOf("application/json", "text/plain")) }
        )
    }
}
