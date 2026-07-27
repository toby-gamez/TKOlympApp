package com.tkolymp.tkolympapp.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.tkolymp.shared.campschedule.ScheduleDay
import com.tkolymp.shared.campschedule.buildJson
import com.tkolymp.shared.campschedule.parseScheduleDay
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.tkolympapp.campschedule.GridDetector
import com.tkolymp.tkolympapp.campschedule.ocrCells
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed class CaptureStep {
    data object Idle : CaptureStep()
    data object Processing : CaptureStep()
}

private fun captureFile(context: Context): File =
    File(context.cacheDir, "camp_schedule/capture.jpg").apply { parentFile?.mkdirs() }

private fun captureUri(context: Context) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile(context))

/**
 * No manual grid-review step: OCR results go straight into the built [ScheduleDay].
 * Matching "my" entries is done by content (name/group number), not grid position, so
 * a cell landing in the "wrong" row/column from photo perspective distortion is still
 * found correctly — a full editable grid to fix cell-by-cell isn't needed.
 */
@Composable
actual fun CampScheduleUploadButton(
    dayLabel: String,
    onScheduleBuilt: (ScheduleDay) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<CaptureStep>(CaptureStep.Idle) }

    // Delegates to the device's own default camera app rather than a custom in-app
    // preview — simpler and more reliable across devices/camera apps.
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val bitmap = BitmapFactory.decodeFile(captureFile(context).absolutePath)
            if (bitmap != null) {
                step = CaptureStep.Processing
                scope.launch { buildAndDeliver(bitmap, dayLabel, onScheduleBuilt) { step = CaptureStep.Idle } }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            if (bitmap != null) {
                step = CaptureStep.Processing
                scope.launch { buildAndDeliver(bitmap, dayLabel, onScheduleBuilt) { step = CaptureStep.Idle } }
            }
        }
    }

    when (step) {
        is CaptureStep.Idle -> {
            Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { cameraLauncher.launch(captureUri(context)) }) {
                    Text(AppStrings.current.campSchedule.takePhoto)
                }
                Button(onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(AppStrings.current.campSchedule.pickFromGallery)
                }
            }
        }

        is CaptureStep.Processing -> {
            Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 32.dp))
                Text(AppStrings.current.campSchedule.processingPhoto, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

private suspend fun buildAndDeliver(bitmap: Bitmap, dayLabel: String, onScheduleBuilt: (ScheduleDay) -> Unit, onDone: () -> Unit) {
    val day = withContext(Dispatchers.Default) {
        val grid = GridDetector.detectGrid(bitmap)
        val cells = ocrCells(bitmap, grid)
        val columns = cells.firstOrNull()?.drop(1)?.map { it?.trim().orEmpty() } ?: emptyList()
        val bodyCells = cells.drop(1)
        parseScheduleDay(buildJson(dayLabel, columns, bodyCells))
    }
    onScheduleBuilt(day)
    onDone()
}
