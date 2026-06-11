package com.tkolymp.tkolympapp.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.tutorial.TutorialManager
import com.tkolymp.tkolympapp.TutorialHighlight
import kotlinx.coroutines.launch

@Composable
fun TutorialOverlay(
    isActive: Boolean,
    step: Int,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(isActive) { if (!isActive) TutorialHighlight.rect = null }

    val highlightRect = TutorialHighlight.rect
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Animated spotlight — four edges + scale (0=closed, 1=open)
    val animLeft   = remember { Animatable(0f) }
    val animTop    = remember { Animatable(0f) }
    val animRight  = remember { Animatable(0f) }
    val animBottom = remember { Animatable(0f) }
    val animScale  = remember { Animatable(0f) }

    LaunchedEffect(highlightRect) {
        if (highlightRect == null) {
            animScale.animateTo(0f, tween(180))
        } else {
            if (animScale.value >= 0.95f) {
                // Spotlight is stably open on the same screen — animate position
                val spec = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                launch { animLeft.animateTo(highlightRect.left, spec) }
                launch { animTop.animateTo(highlightRect.top, spec) }
                launch { animRight.animateTo(highlightRect.right, spec) }
                launch { animBottom.animateTo(highlightRect.bottom, spec) }
            } else {
                // Closed, closing, or mid-transition — cancel current animation,
                // snap to the correct position, then open
                animLeft.snapTo(highlightRect.left)
                animTop.snapTo(highlightRect.top)
                animRight.snapTo(highlightRect.right)
                animBottom.snapTo(highlightRect.bottom)
                animScale.animateTo(1f, tween(220))
            }
        }
    }

    // Read animated values in composable scope so recomposition/redraw is triggered
    val hl = animLeft.value
    val ht = animTop.value
    val hr = animRight.value
    val hb = animBottom.value
    val hs = animScale.value

    // Dynamic card positioning for overview (1–5) and Other screen (13–15); tabs screens (6–12)
    // always keep the card at the bottom to avoid covering the tab row.
    var overlayHeightPx by remember { mutableStateOf(0f) }
    val spotlightCenterY = highlightRect?.let { (it.top + it.bottom) / 2f } ?: 0f
    val cardAtTop = (step in 1..5 || step in 13..15) && overlayHeightPx > 0f && spotlightCenterY > overlayHeightPx * 0.52f

    val strings = AppStrings.current.tutorial

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayHeightPx = it.size.height.toFloat() },
        contentAlignment = if (cardAtTop) Alignment.TopCenter else Alignment.BottomCenter
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawBehind {
                        drawRect(Color.Black.copy(alpha = 0.55f))
                        if (hs > 0.01f) {
                            val pad = 10.dp.toPx()
                            val radius = 16.dp.toPx()
                            val cx = (hl + hr) / 2f
                            val cy = (ht + hb) / 2f
                            val hw = (hr - hl) / 2f + pad
                            val hh = (hb - ht) / 2f + pad
                            drawRoundRect(
                                color = Color.Transparent,
                                topLeft = Offset(cx - hw * hs, cy - hh * hs),
                                size = Size(hw * hs * 2, hh * hs * 2),
                                cornerRadius = CornerRadius(radius),
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

        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { if (cardAtTop) -it / 2 else it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(250)) { if (cardAtTop) -it / 2 else it / 2 }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .then(
                        if (cardAtTop)
                            Modifier.padding(top = topPadding + 8.dp)
                        else
                            Modifier.padding(bottom = bottomPadding + 8.dp)
                    ),
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
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Slide title + description when step changes
                    AnimatedContent(
                        targetState = step,
                        transitionSpec = {
                            val forward = targetState > initialState
                            val enter = slideInHorizontally(tween(420)) { if (forward) it / 3 else -it / 3 } +
                                fadeIn(tween(350))
                            val exit = slideOutHorizontally(tween(420)) { if (forward) -it / 3 else it / 3 } +
                                fadeOut(tween(320))
                            enter togetherWith exit
                        },
                        label = "stepContent"
                    ) { s ->
                        val (title, description) = when (s) {
                            0  -> strings.introTitle             to strings.introDesc
                            1  -> strings.overviewUpcomingTitle  to strings.overviewUpcomingDesc
                            2  -> strings.overviewBoardTitle     to strings.overviewBoardDesc
                            3  -> strings.overviewCampsTitle     to strings.overviewCampsDesc
                            4  -> strings.overviewBirthdaysTitle to strings.overviewBirthdaysDesc
                            5  -> strings.overviewStatsTitle     to strings.overviewStatsDesc
                            6  -> strings.calendarMineTitle      to strings.calendarMineDesc
                            7  -> strings.calendarAllTitle       to strings.calendarAllDesc
                            8  -> strings.calendarFilterTitle    to strings.calendarFilterDesc
                            9  -> strings.boardListTitle         to strings.boardListDesc
                            10 -> strings.boardStickyTitle       to strings.boardStickyDesc
                            11 -> strings.eventsPlannedTitle     to strings.eventsPlannedDesc
                            12 -> strings.eventsPastTitle        to strings.eventsPastDesc
                            13 -> strings.otherAccountTitle      to strings.otherAccountDesc
                            14 -> strings.otherQrTitle           to strings.otherQrDesc
                            15 -> strings.otherPeopleTitle       to strings.otherPeopleDesc
                            else -> strings.otherTitle           to strings.otherDesc
                        }
                        Column {
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
                        }
                    }

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
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(if (step == TutorialManager.stepCount - 1) strings.done else strings.next)
                        }
                    }
                }
            }
        }
    }
}
