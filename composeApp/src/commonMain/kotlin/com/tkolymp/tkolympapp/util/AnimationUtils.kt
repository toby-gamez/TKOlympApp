package com.tkolymp.tkolympapp.util

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.min

/**
 * Staggered fade-in that keeps layout stable (graphicsLayer alpha only, no movement).
 * Each index delays by baseDelayMs, capped at maxDelayMs.
 */
@Composable
fun StaggeredItem(
    index: Int,
    visible: Boolean,
    baseDelayMs: Int = 40,
    maxDelayMs: Int = 280,
    durationMs: Int = 200,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val delay = min(index * baseDelayMs, maxDelayMs)
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMs, delayMillis = delay),
        label = "staggerAlpha"
    )
    Box(modifier = modifier.graphicsLayer { this.alpha = alpha }) {
        content()
    }
}

fun tabContentTransitionSpec(): ContentTransform =
    fadeIn(tween(180)) togetherWith fadeOut(tween(120))

@Composable
fun EmptyStateFade(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(150))
    ) {
        content()
    }
}

@Composable
fun Modifier.pressScaleEffect(targetScale: Float = 0.96f): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pressScale"
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}
