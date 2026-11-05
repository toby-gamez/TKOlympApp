package com.tkolymp.tkolympapp.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.tkolymp.shared.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_EDGE_PX = 1024
private const val JPEG_QUALITY = 85

private fun captureFile(context: Context): File =
    File(context.cacheDir, "avatar/capture.jpg").apply { parentFile?.mkdirs() }

private fun captureUri(context: Context): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile(context))

/** Decodes [uri] (EXIF rotation applied by the decoder), shrinks it to [MAX_EDGE_PX] and re-encodes as JPEG. */
private fun decodeForUpload(context: Context, uri: Uri): ByteArray {
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = max(info.size.width, info.size.height)
        if (longest > MAX_EDGE_PX) {
            val scale = MAX_EDGE_PX.toFloat() / longest
            decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
        }
    }
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        out.toByteArray()
    }
}

@Composable
actual fun rememberAvatarPicker(onPicked: (ByteArray) -> Unit): AvatarPicker? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnPicked = rememberUpdatedState(onPicked)

    fun deliver(uri: Uri) {
        scope.launch {
            val bytes = try {
                withContext(Dispatchers.IO) { decodeForUpload(context, uri) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Logger.w("AvatarPicker", "could not read picked image: ${e.message}")
                null
            }
            if (bytes != null) latestOnPicked.value(bytes)
        }
    }

    // Both delegate to the device's own apps (default camera / system photo picker): no extra permissions needed.
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) deliver(captureUri(context))
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) deliver(uri)
    }

    return remember(cameraLauncher, galleryLauncher) {
        AvatarPicker(
            takePhoto = { cameraLauncher.launch(captureUri(context)) },
            pickFromGallery = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}
