package com.tkolymp.tkolympapp.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.tkolymp.shared.calendar.WeekVibesData
import kotlin.math.max

@Composable
fun WeekPersonaBadge(
    vibes: WeekVibesData,
    modifier: Modifier = Modifier,
    personaLabel: String,
    dayLabels: List<String>
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(vibes) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 },
        modifier = modifier
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(48.dp)
                ) {
                    Text(
                        text = vibes.persona.emoji,
                        fontSize = 22.sp
                    )
                    Text(
                        text = personaLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        lineHeight = 13.sp,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                    DensityBarsRow(
                        densities = vibes.dailyDensity.map { it.count },
                        maxDensity = vibes.maxDensity,
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        dayLabels.take(7).forEach { label ->
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DensityBarsRow(
    densities: List<Int>,
    maxDensity: Int,
    accentColor: Color
) {
    var animated by remember { mutableStateOf(false) }
    LaunchedEffect(densities) { animated = false; animated = true }
    val animatedFraction by animateFloatAsState(
        targetValue = if (animated) 1f else 0f,
        animationSpec = tween(600),
        label = "densityBars"
    )

    if (maxDensity == 0) {
        Box(modifier = Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            densities.forEachIndexed { i, count ->
                val targetWeight = count.toFloat() / max(densities.max(), 1).toFloat()
                val animatedWeight = targetWeight * animatedFraction
                val barAlpha = 0.3f + 0.7f * targetWeight

                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .fillMaxHeight(animatedWeight.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor.copy(alpha = barAlpha.coerceIn(0.3f, 1f)))
                    )
                }
            }
        }
    }
}
