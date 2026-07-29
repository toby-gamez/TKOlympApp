package com.tkolymp.tkolympapp.campschedule

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val CELL_PADDING_PX = 6

// A crop shorter than this is upscaled before OCR — ML Kit's text recognizer becomes
// unreliable on very short crops (e.g. a merged note row from a low-resolution photo,
// only ~20-30px tall) regardless of aspect ratio, seemingly because such a short input
// falls below the detector's practical working size. Upscaling doesn't add real detail,
// but it reliably pushes short-but-legible crops back into a size the model handles.
private const val MIN_OCR_HEIGHT_PX = 64

/**
 * Runs ML Kit text recognition on every cell of [grid] independently (never on the
 * full [bitmap] at once), so OCR text can never be misattributed to the wrong
 * row/column. Empty/whitespace-only results become `null`.
 */
suspend fun ocrCells(bitmap: Bitmap, grid: List<List<Rect>>): List<List<String?>> {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        return grid.map { row -> row.map { rect -> ocrOneCell(recognizer, bitmap, rect) } }
    } finally {
        recognizer.close()
    }
}

private suspend fun ocrOneCell(recognizer: TextRecognizer, bitmap: Bitmap, rect: Rect): String? {
    val left = (rect.left - CELL_PADDING_PX).coerceAtLeast(0)
    val top = (rect.top - CELL_PADDING_PX).coerceAtLeast(0)
    val right = (rect.right + CELL_PADDING_PX).coerceAtMost(bitmap.width)
    val bottom = (rect.bottom + CELL_PADDING_PX).coerceAtMost(bitmap.height)
    val width = right - left
    val height = bottom - top
    if (width <= 0 || height <= 0) return null

    val cropped = Bitmap.createBitmap(bitmap, left, top, width, height)
    val scaled = if (height < MIN_OCR_HEIGHT_PX) {
        val factor = MIN_OCR_HEIGHT_PX.toFloat() / height
        Bitmap.createScaledBitmap(cropped, (width * factor).toInt().coerceAtLeast(1), MIN_OCR_HEIGHT_PX, true)
    } else {
        cropped
    }
    val image = InputImage.fromBitmap(scaled, 0)
    val text = suspendCancellableCoroutine<String?> { cont ->
        recognizer.process(image)
            .addOnSuccessListener { result -> cont.resume(result.text) }
            .addOnFailureListener { cont.resume(null) }
    }
    return text?.trim()?.ifEmpty { null }
}
