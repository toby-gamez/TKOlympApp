package com.tkolymp.tkolympapp.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.tutorial.TutorialManager
import com.tkolymp.tkolympapp.LocalBottomBarPadding
import com.tkolymp.tkolympapp.TutorialHighlight
import ui.theme.BrandLightPrimary

@Composable
fun TutorialOverlay(
    isActive: Boolean,
    step: Int,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Clear spotlight rect when step changes or tutorial is dismissed
    LaunchedEffect(step) { TutorialHighlight.rect = null }
    LaunchedEffect(isActive) { if (!isActive) TutorialHighlight.rect = null }

    // Read in composable scope so drawBehind recompose triggers correctly
    val highlightRect = TutorialHighlight.rect
    val overlayOffset = TutorialHighlight.overlayOffset

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { TutorialHighlight.overlayOffset = it.positionInRoot() }
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawBehind {
                        // Full-screen scrim
                        drawRect(Color.Black.copy(alpha = 0.55f))
                        // Cut out spotlight around the highlighted element
                        val hl = highlightRect
                        if (hl != null) {
                            val dx = overlayOffset.x
                            val dy = overlayOffset.y
                            val pad = 14f
                            drawRoundRect(
                                color = Color.Transparent,
                                topLeft = Offset(hl.left - dx - pad, hl.top - dy - pad),
                                size = Size(hl.width + pad * 2, hl.height + pad * 2),
                                cornerRadius = CornerRadius(16.dp.toPx()),
                                blendMode = BlendMode.Clear
                            )
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
            )
        }

        val bottomPadding = LocalBottomBarPadding.current
        val strings = AppStrings.current.tutorial

        val (title, description) = when (step) {
            0  -> strings.overviewUpcomingTitle to strings.overviewUpcomingDesc
            1  -> strings.overviewBoardTitle    to strings.overviewBoardDesc
            2  -> strings.overviewCampsTitle    to strings.overviewCampsDesc
            3  -> strings.overviewBirthdaysTitle to strings.overviewBirthdaysDesc
            4  -> strings.overviewStatsTitle    to strings.overviewStatsDesc
            5  -> strings.calendarMineTitle     to strings.calendarMineDesc
            6  -> strings.calendarAllTitle      to strings.calendarAllDesc
            7  -> strings.calendarFilterTitle   to strings.calendarFilterDesc
            8  -> strings.boardListTitle        to strings.boardListDesc
            9  -> strings.boardSearchTitle      to strings.boardSearchDesc
            10 -> strings.eventsPlannedTitle    to strings.eventsPlannedDesc
            11 -> strings.eventsPastTitle       to strings.eventsPastDesc
            else -> strings.otherTitle          to strings.otherDesc
        }

        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(250)) { it / 2 }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = bottomPadding + 8.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${step + 1} ${strings.stepOf} ${TutorialManager.stepCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onSkip) {
                            Text(strings.skip, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { (step + 1).toFloat() / TutorialManager.stepCount.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = BrandLightPrimary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (step > 0) {
                            OutlinedButton(onClick = onPrevious) {
                                Text(strings.previous)
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        Button(
                            onClick = onNext,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandLightPrimary)
                        ) {
                            Text(if (step == TutorialManager.stepCount - 1) strings.done else strings.next)
                        }
                    }
                }
            }
        }
    }
}
