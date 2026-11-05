package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGImageAlphaInfo
import platform.UIKit.UIImage
import platform.UIKit.UIImageWriteToSavedPhotosAlbum

@Composable
actual fun rememberSaveImageToGalleryCallback(fileBaseName: String): suspend (ImageBitmap) -> Boolean {
    return { imageBitmap ->
        withContext(Dispatchers.Main) { saveToPhotoAlbum(imageBitmap) }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun saveToPhotoAlbum(bitmap: ImageBitmap): Boolean {
    val width = bitmap.width
    val height = bitmap.height
    if (width == 0 || height == 0) return false

    val pixelMap = bitmap.toPixelMap()
    val bytes = ByteArray(width * height * 4)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val c = pixelMap[x, y]
            val base = (y * width + x) * 4
            bytes[base + 0] = (c.red * 255f).toInt().coerceIn(0, 255).toByte()
            bytes[base + 1] = (c.green * 255f).toInt().coerceIn(0, 255).toByte()
            bytes[base + 2] = (c.blue * 255f).toInt().coerceIn(0, 255).toByte()
            bytes[base + 3] = (c.alpha * 255f).toInt().coerceIn(0, 255).toByte()
        }
    }

    return try {
        bytes.usePinned { pinned ->
            val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return@usePinned false
            val ctx = CGBitmapContextCreate(
                data = pinned.addressOf(0),
                width = width.toULong(),
                height = height.toULong(),
                bitsPerComponent = 8u,
                bytesPerRow = (width * 4).toULong(),
                space = colorSpace,
                bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
            ) ?: return@usePinned false
            val cgImage = CGBitmapContextCreateImage(ctx) ?: return@usePinned false
            UIImageWriteToSavedPhotosAlbum(UIImage(cGImage = cgImage), null, null, null)
            true
        }
    } catch (_: Exception) {
        false
    }
}
