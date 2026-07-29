package com.tkolymp.tkolympapp.platform

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun CampSchedulePhotoThumbnail(photoBytes: ByteArray, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap = remember(photoBytes) { BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size) }
    var expanded by remember { mutableStateOf(false) }
    // FullscreenImageViewer (shared with EventScreen's photo viewer, with proper
    // pinch-zoom/pan) takes an image URL rather than raw bytes, so the photo is written
    // to a cache file once and referenced by its file:// URI.
    var fileUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(photoBytes) {
        val file = File(context.cacheDir, "camp_schedule/view_photo.jpg")
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeBytes(photoBytes)
        }
        fileUri = "file://${file.absolutePath}"
    }

    if (bitmap == null) return

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable { expanded = true }
    )

    if (expanded) {
        fileUri?.let { uri ->
            FullscreenImageViewer(imageUrl = uri, onDismiss = { expanded = false })
        }
    }
}
