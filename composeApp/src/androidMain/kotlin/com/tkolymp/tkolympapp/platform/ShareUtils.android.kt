package com.tkolymp.tkolympapp.platform

import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberShareStatsCallback(): suspend (ImageBitmap) -> Unit {
    val context = LocalContext.current
    return { imageBitmap ->
        val uri = withContext(Dispatchers.IO) {
            val captured = imageBitmap.asAndroidBitmap()
            Log.d("ShareStats", "bitmap ${captured.width}x${captured.height} config=${captured.config} isRecycled=${captured.isRecycled}")

            if (captured.width == 0 || captured.height == 0) {
                Log.e("ShareStats", "Bitmap is 0x0 — layer was not captured; aborting share")
                return@withContext null
            }

            // Draw captured bitmap onto a white software-backed canvas so the
            // result is always opaque (hardware bitmaps and transparent layers both
            // produce black/empty PNGs when compressed directly).
            val softBitmap = Bitmap.createBitmap(captured.width, captured.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(softBitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            val src = if (captured.config == Bitmap.Config.HARDWARE) {
                captured.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                captured
            }
            canvas.drawBitmap(src, 0f, 0f, null)

            val shareDir = File(context.cacheDir, "share")
            shareDir.mkdirs()
            val file = File(shareDir, "stats_share.png")
            file.outputStream().use { out ->
                val ok = softBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                Log.d("ShareStats", "compress ok=$ok fileSize=${file.length()} bytes")
            }
            softBitmap.recycle()

            if (file.length() == 0L) {
                Log.e("ShareStats", "Compressed file is empty — aborting share")
                return@withContext null
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }

        if (uri != null) withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Sdílet statistiky").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }
}
