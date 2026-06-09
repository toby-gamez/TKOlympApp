package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

@Composable
fun BackgroundPluses(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    rows: Int = 8,
    cols: Int = 5
) {
    Canvas(modifier = modifier) {
        val spacingX = size.width / cols.toFloat()
        val spacingY = size.height / rows.toFloat()
        val plusHalf = minOf(spacingX, spacingY) * 0.12f
        val stroke = plusHalf * 0.45f
        val drawColor = color.copy(alpha = 0.12f)

        for (i in 0 until cols) {
            for (j in 0 until rows) {
                val cx = spacingX * (i + 0.5f)
                val cy = spacingY * (j + 0.5f)
                val centerRadius = stroke * 0.55f
                val gap = centerRadius * 1.1f

                drawLine(
                    color = drawColor,
                    start = Offset(cx - plusHalf, cy),
                    end = Offset(cx - gap, cy),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = drawColor,
                    start = Offset(cx + gap, cy),
                    end = Offset(cx + plusHalf, cy),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = drawColor,
                    start = Offset(cx, cy - plusHalf),
                    end = Offset(cx, cy - gap),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = drawColor,
                    start = Offset(cx, cy + gap),
                    end = Offset(cx, cy + plusHalf),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawCircle(color = drawColor, radius = centerRadius, center = Offset(cx, cy))
            }
        }
    }
}
