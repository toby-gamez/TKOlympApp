package com.tkolymp.tkolympapp.platform

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import com.tkolymp.shared.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberSaveImageToGalleryCallback(fileBaseName: String): suspend (ImageBitmap) -> Boolean {
    val context = LocalContext.current
    return { imageBitmap ->
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$fileBaseName.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/TKOlymp")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext false
                val ok = resolver.openOutputStream(uri)?.use { out ->
                    imageBitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: false
                if (ok) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } else {
                    resolver.delete(uri, null, null)
                }
                ok
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("SaveImage", "Saving to gallery failed: ${e.message}")
                false
            }
        }
    }
}
