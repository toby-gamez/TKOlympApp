package com.tkolymp.tkolympapp.platform

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
import com.tkolymp.shared.language.AppStrings
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun NotificationExportImportButton(
    onGetExportJson: () -> String,
    onImportJson: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    IconButton(onClick = { menuExpanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = "Více")
    }
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(
            text = { Text(AppStrings.current.importExport.exportJson) },
            onClick = {
                menuExpanded = false
                scope.launch {
                    try {
                        val jsonStr = onGetExportJson()
                        val data = (jsonStr as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return@launch
                        val tmpDir = NSSearchPathForDirectoriesInDomains(
                            NSDocumentDirectory, NSUserDomainMask, true
                        ).firstOrNull() as? String ?: return@launch
                        val filePath = "$tmpDir/notification_settings.json"
                        data.writeToURL(
                            NSURL.fileURLWithPath(filePath),
                            atomically = true
                        )
                        val fileUrl = NSURL.fileURLWithPath(filePath)
                        val activityVc = UIActivityViewController(
                            activityItems = listOf(fileUrl),
                            applicationActivities = null
                        )
                        UIApplication.sharedApplication.keyWindow?.rootViewController
                            ?.presentViewController(activityVc, animated = true, completion = null)
                        onMessage(AppStrings.current.importExport.exportSuccessful)
                    } catch (_: Exception) {
                        onMessage(AppStrings.current.importExport.exportFailed)
                    }
                }
            }
        )
        DropdownMenuItem(
            text = { Text(AppStrings.current.importExport.importJson) },
            onClick = {
                menuExpanded = false
                val picker = UIDocumentPickerViewController(
                    forOpeningContentTypes = listOf(
                        platform.UniformTypeIdentifiers.UTTypeJSON
                    )
                )
                val delegate = object : platform.darwin.NSObject(),
                    platform.UIKit.UIDocumentPickerDelegateProtocol {
                    override fun documentPicker(
                        controller: UIDocumentPickerViewController,
                        didPickDocumentsAtURLs: List<*>
                    ) {
                        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
                        scope.launch {
                            try {
                                url.startAccessingSecurityScopedResource()
                                val data = NSData.create(contentsOfURL = url) ?: return@launch
                                val str = NSString.create(data, NSUTF8StringEncoding)?.toString() ?: return@launch
                                url.stopAccessingSecurityScopedResource()
                                onImportJson(str)
                            } catch (_: Exception) {
                                onMessage(AppStrings.current.importExport.importFailed)
                            }
                        }
                    }
                }
                picker.delegate = delegate
                UIApplication.sharedApplication.keyWindow?.rootViewController
                    ?.presentViewController(picker, animated = true, completion = null)
            }
        )
    }
}
