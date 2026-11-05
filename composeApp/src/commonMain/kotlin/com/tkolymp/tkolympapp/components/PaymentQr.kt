package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import qrcode.QRCode

private const val QUIET_ZONE_MODULES = 4
private const val PIXELS_PER_MODULE = 12

/**
 * Renders [payload] as a black-on-white QR code (with the mandatory quiet zone) so it can be
 * shown, shared or saved. Returns null if the payload cannot be encoded.
 */
fun renderQrImageBitmap(payload: String): ImageBitmap? = try {
    val modules = QRCode.ofSquares().build(payload).rawData
    val count = modules.size
    val sizePx = (count + 2 * QUIET_ZONE_MODULES) * PIXELS_PER_MODULE
    val bitmap = ImageBitmap(sizePx, sizePx)
    val canvas = Canvas(bitmap)
    val white = Paint().apply { color = Color.White }
    val black = Paint().apply { color = Color.Black }
    canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), white)
    for (row in 0 until count) {
        for (col in 0 until modules[row].size) {
            if (modules[row][col].dark) {
                val left = ((col + QUIET_ZONE_MODULES) * PIXELS_PER_MODULE).toFloat()
                val top = ((row + QUIET_ZONE_MODULES) * PIXELS_PER_MODULE).toFloat()
                canvas.drawRect(left, top, left + PIXELS_PER_MODULE, top + PIXELS_PER_MODULE, black)
            }
        }
    }
    bitmap
} catch (_: Exception) {
    null
}

@Composable
fun PaymentQrImage(bitmap: ImageBitmap, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier.size(size)
    )
}
