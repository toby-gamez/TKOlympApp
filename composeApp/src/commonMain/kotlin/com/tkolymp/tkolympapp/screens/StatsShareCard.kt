package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tkolymp.shared.utils.roundTo1dp
import com.tkolymp.shared.viewmodels.SeasonSelection
import com.tkolymp.shared.viewmodels.WeekStats
import com.tkolymp.tkolympapp.platform.AppLogo
import com.tkolymp.tkolympapp.platform.rememberShareStatsCallback
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ─── Share preview dialog ─────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ShareStatsDialog(
    season: SeasonSelection,
    totalSessions: Int,
    totalMinutes: Long,
    avgPerWeek: Double,
    currentStreak: Int,
    weeklyData: List<WeekStats>,
    onDismiss: () -> Unit
) {
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val shareCallback = rememberShareStatsCallback()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Card wrapped with graphics-layer capture ──────────────────
                Box(
                    modifier = Modifier.drawWithContent {
                        graphicsLayer.record(
                            density = this,
                            layoutDirection = layoutDirection,
                            size = IntSize(
                                size.width.roundToInt(),
                                size.height.roundToInt()
                            )
                        ) {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(graphicsLayer)
                    }
                ) {
                    StatsShareCard(
                        season = season,
                        totalSessions = totalSessions,
                        totalMinutes = totalMinutes,
                        avgPerWeek = avgPerWeek,
                        currentStreak = currentStreak,
                        weeklyData = weeklyData
                    )
                }

                Spacer(Modifier.height(4.dp))

                // ── Buttons ───────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) { Text("Zrušit") }

                    Button(
                        onClick = {
                            scope.launch {
                                val bitmap = graphicsLayer.toImageBitmap()
                                shareCallback(bitmap)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(2f)
                    ) { Text("Sdílet") }
                }
            }
        }
    }
}

// ─── The actual visual share card ─────────────────────────────────────────────

@Composable
internal fun StatsShareCard(
    season: SeasonSelection,
    totalSessions: Int,
    totalMinutes: Long,
    avgPerWeek: Double,
    currentStreak: Int,
    weeklyData: List<WeekStats>,
    modifier: Modifier = Modifier
) {
    val hours = (totalMinutes / 60.0).roundTo1dp()
    val avgFmt = avgPerWeek.roundTo1dp()
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(primary, primaryContainer)
                )
            )
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // ── Header: logo + season chip ────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppLogo(size = 20.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "TKOlymp",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = onPrimary,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = onPrimary.copy(alpha = 0.15f)
                ) {
                    Text(
                        "Sezóna ${season.label}",
                        fontSize = 11.sp,
                        color = onPrimary,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Focal number ──────────────────────────────────────────────────
            if (currentStreak > 0) {
                Text("🔥", fontSize = 48.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$currentStreak",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    color = tertiary,
                    lineHeight = 64.sp
                )
                Text(
                    text = "týdnů v řadě",
                    fontSize = 15.sp,
                    color = onPrimary.copy(alpha = 0.65f)
                )
            } else {
                Text("🏅", fontSize = 48.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$totalSessions",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    color = onPrimary,
                    lineHeight = 64.sp
                )
                Text(
                    text = "tréninků",
                    fontSize = 15.sp,
                    color = onPrimary.copy(alpha = 0.65f)
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Stats pills ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentStreak > 0) {
                    ShareStatPill(modifier = Modifier.weight(1f), value = "$totalSessions", label = "tréninků")
                }
                ShareStatPill(modifier = Modifier.weight(1f), value = "$hours h", label = "celkem hodin")
                ShareStatPill(modifier = Modifier.weight(1f), value = "$avgFmt", label = "trén./týden")
            }

            // ── Mini weekly bar chart ─────────────────────────────────────────
            if (weeklyData.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Týdenní aktivita",
                    fontSize = 10.sp,
                    color = onPrimary.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(6.dp))
                ShareMiniBarChart(weeklyData = weeklyData.takeLast(16))
            }

            Spacer(Modifier.height(20.dp))

            // ── Footer branding ───────────────────────────────────────────────
            Text(
                "tkolymp.cz",
                fontSize = 10.sp,
                color = onPrimary.copy(alpha = 0.3f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Helper: stat pill ────────────────────────────────────────────────────────

@Composable
private fun ShareStatPill(modifier: Modifier = Modifier, value: String, label: String) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(onPrimary.copy(alpha = 0.1f))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = onPrimary,
                maxLines = 1
            )
            Text(
                text = label,
                fontSize = 9.sp,
                color = onPrimary.copy(alpha = 0.55f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Helper: mini bar chart ───────────────────────────────────────────────────

@Composable
private fun ShareMiniBarChart(weeklyData: List<WeekStats>) {
    val maxCount = weeklyData.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val highlightIdx = weeklyData.indexOfFirst { it.isCurrent }
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        weeklyData.forEachIndexed { idx, week ->
            val fraction = week.count.toFloat() / maxCount.toFloat()
            val isHighlight = idx == highlightIdx
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction.coerceAtLeast(0.06f))
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(
                        when {
                            isHighlight -> onPrimary
                            week.count > 0 -> onPrimary.copy(alpha = 0.38f)
                            else -> onPrimary.copy(alpha = 0.08f)
                        }
                    )
            )
        }
    }
}
