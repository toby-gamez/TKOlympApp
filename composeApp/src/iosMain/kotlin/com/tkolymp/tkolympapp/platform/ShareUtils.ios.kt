package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIColor
import platform.UIKit.UIRectFill
import platform.darwin.UInt8

@Composable
actual fun rememberShareStatsCallback(): suspend (ImageBitmap) -> Unit {
    return { imageBitmap ->
        withContext(Dispatchers.Main) {
            shareImageBitmapOnMainThread(imageBitmap)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun shareImageBitmapOnMainThread(bitmap: ImageBitmap) {
    val width = bitmap.width
    val height = bitmap.height
    if (width == 0 || height == 0) return

    // Build a raw RGBA byte array from the Compose pixel map
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

    try {
        bytes.usePinned { pinned ->
            val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return
            val ctx = CGBitmapContextCreate(
                data = pinned.addressOf(0),
                width = width.toULong(),
                height = height.toULong(),
                bitsPerComponent = 8u,
                bytesPerRow = (width * 4).toULong(),
                space = colorSpace,
                bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
            ) ?: return
            val cgImage = CGBitmapContextCreateImage(ctx) ?: return
            val uiImage = UIImage(cGImage = cgImage)

            UIGraphicsBeginImageContextWithOptions(
                CGSizeMake(width.toDouble(), height.toDouble()), true, 1.0
            )
            UIColor.whiteColor.setFill()
            UIRectFill(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
            uiImage.drawAtPoint(platform.CoreGraphics.CGPointMake(0.0, 0.0))
            val finalImage = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()

            val pngData = UIImagePNGRepresentation(finalImage ?: uiImage) ?: return
            val tmpDir = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory, NSUserDomainMask, true
            ).firstOrNull() as? String ?: return
            val filePath = "$tmpDir/stats_share.png"
            pngData.writeToFile(filePath, atomically = true)
            val fileUrl = NSURL.fileURLWithPath(filePath)

            val activityVc = UIActivityViewController(
                activityItems = listOf(fileUrl),
                applicationActivities = null
            )
            UIApplication.sharedApplication.keyWindow?.rootViewController
                ?.presentViewController(activityVc, animated = true, completion = null)
        }
    } catch (_: Exception) {}
}
