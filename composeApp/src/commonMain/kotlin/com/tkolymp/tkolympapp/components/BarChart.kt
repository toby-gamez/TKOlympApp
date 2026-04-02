package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * A simple cross-platform bar chart drawn with Canvas.
 *
 * @param data          List of (label, value) pairs. Labels are shown below every [labelEvery] bar.
 * @param modifier      Applied to the whole composable.
 * @param highlightIndex Index of the bar to highlight (e.g. current week). -1 = none.
 * @param barColor      Default bar colour.
 * @param highlightColor Colour for the highlighted bar.
 * @param dimColor      Colour for bars other than the highlighted one when a highlight is active.
 * @param labelEvery    Show an x-axis label below every Nth bar. Default 4.
 */
@Composable
fun BarChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    highlightIndex: Int = -1,
    barColor: Color = Color.Unspecified,
    highlightColor: Color = Color.Unspecified,
    dimColor: Color = Color.Unspecified,
    labelEvery: Int = 4,
    // Rotate x-axis labels by degrees (0 = none). Use 90f for vertical labels.
    labelRotationDegrees: Float = 0f,
    // Maximum lines for x-axis labels when wrapping (set >1 to allow multi-line labels).
    labelMaxLines: Int = 1,
    // Show x-axis labels (dates) under the chart. Set to false to hide them.
    showLabels: Boolean = true,
    onBarClick: ((index: Int, label: String, value: Int) -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme

    val effectiveBar = if (barColor == Color.Unspecified) scheme.primary else barColor
    val effectiveHighlight = if (highlightColor == Color.Unspecified) scheme.primary else highlightColor
    val effectiveDim = if (dimColor == Color.Unspecified) scheme.primaryContainer else dimColor

    val maxValue = data.maxOfOrNull { it.second } ?: 0

    Column(modifier = modifier) {
        // Values above bars (for quick readability)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            data.forEachIndexed { i, (_, value) ->
                val weight = 1f / max(1, data.size)
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(weight),
                    maxLines = 1
                )
            }
        }

        Box {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                if (maxValue == 0) return@Canvas

                val n = data.size
                val totalWidth = size.width
                val totalHeight = size.height
                val barPadding = 3.dp.toPx()
                val barWidth = (totalWidth - barPadding * (n - 1)) / n

                data.forEachIndexed { i, (_, value) ->
                    val barHeightPx = (value.toFloat() / maxValue.toFloat()) * (totalHeight - 4.dp.toPx())
                    val x = i * (barWidth + barPadding)
                    val y = totalHeight - barHeightPx

                    val color = when {
                        highlightIndex < 0 -> effectiveBar
                        i == highlightIndex -> effectiveHighlight
                        else -> effectiveDim
                    }

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeightPx),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }
            }

            // Transparent clickable strips overlayed to detect taps per-bar
            Row(modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                data.forEachIndexed { i, (label, value) ->
                    val weight = 1f / max(1, data.size)
                    Box(modifier = Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .clickable(enabled = onBarClick != null) {
                            onBarClick?.invoke(i, label, value)
                        }
                    )
                }
            }
        }

        // X-axis labels shown every [labelEvery] bars (can be hidden via `showLabels`)
        if (showLabels) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                data.forEachIndexed { i, (label, _) ->
                    val show = (i % labelEvery == 0) || (i == data.lastIndex)
                    val weight = 1f / max(1, data.size)
                    Text(
                        text = if (show) label else "",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(weight).rotate(labelRotationDegrees),
                        maxLines = labelMaxLines,
                        softWrap = true
                    )
                }
            }
        }
    }
}
