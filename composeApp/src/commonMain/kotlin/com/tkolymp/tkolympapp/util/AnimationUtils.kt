package com.tkolymp.tkolympapp.util

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tkolymp.tkolympapp.platform.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlin.math.min

/** Shared motion specs so every screen settles, glides and presses the same way. */
object Motion {
    const val DampingSettle = 0.75f
    const val DampingPress = 0.5f
    const val FadeMs = 200
    const val CrossfadeMs = 320
    const val FadeOutMs = 120
    const val RiseDp = 20
    const val SettleStartScale = 0.96f
    const val PressedScale = 0.96f

    fun <T> settleSpring() = spring<T>(dampingRatio = DampingSettle, stiffness = Spring.StiffnessLow)

    fun <T> pressSpring() = spring<T>(dampingRatio = DampingPress, stiffness = Spring.StiffnessMedium)

    /** Placement spec for `Modifier.animateItem`, so inserts, removals and reorders glide instead of jump. */
    fun placementSpring() = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold
    )
}

/**
 * Cross-fades a view switch (e.g. My/All, Time/Trainers): the old content fades out, [switchTo] then applies the
 * change, and the new content fades in. The change is applied only after the fade-out because both views read the
 * same live state, so the old one can't be kept on screen any other way. Instant with reduced motion.
 */
class FadeSwitch internal constructor(private val instant: Boolean) {
    internal val alpha = Animatable(1f)

    suspend fun switchTo(apply: () -> Unit) {
        if (instant) {
            apply()
            return
        }
        alpha.animateTo(0f, tween(Motion.FadeOutMs))
        apply()
        alpha.animateTo(1f, tween(Motion.FadeMs))
    }
}

@Composable
fun rememberFadeSwitch(): FadeSwitch {
    val reduceMotion = rememberReduceMotion()
    return remember(reduceMotion) { FadeSwitch(reduceMotion) }
}

/** Applies the [FadeSwitch] opacity to the content that changes; leave the toggle itself outside of it. */
fun Modifier.fadeSwitch(switch: FadeSwitch): Modifier = graphicsLayer { alpha = switch.alpha.value }

/** Smoothly animates size changes (expand/collapse, content appearing) instead of jumping; static under reduced motion. */
@Composable
fun Modifier.cozyContentSize(): Modifier =
    if (rememberReduceMotion()) this
    else animateContentSize(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))

/** `Modifier.animateItem` with the shared glide spec; no movement animation under reduced motion. Needs a stable key. */
@Composable
fun LazyItemScope.cozyItem(): Modifier =
    if (rememberReduceMotion()) Modifier else Modifier.animateItem(placementSpec = Motion.placementSpring())

/**
 * Soft "settle in" entrance: fades, rises a little and scales up on a gentle spring.
 * Each index delays by baseDelayMs, capped at maxDelayMs. With reduced motion it only fades.
 */
@Composable
fun StaggeredItem(
    index: Int,
    visible: Boolean,
    baseDelayMs: Int = 40,
    maxDelayMs: Int = 280,
    durationMs: Int = Motion.FadeMs,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reduceMotion = rememberReduceMotion()
    var shown by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible && !shown) {
            delay(min(index * baseDelayMs, maxDelayMs).toLong())
            shown = true
        } else if (!visible) {
            shown = false
        }
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (reduceMotion) tween(durationMs) else Motion.settleSpring(),
        label = "staggerProgress"
    )
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.coerceIn(0f, 1f)
            if (!reduceMotion) {
                translationY = (1f - progress) * Motion.RiseDp.dp.toPx()
                val s = Motion.SettleStartScale + (1f - Motion.SettleStartScale) * progress
                scaleX = s
                scaleY = s
            }
        }
    ) {
        content()
    }
}

/**
 * Cross-fades between a skeleton and the loaded content (or error/empty state) instead of swapping instantly.
 * [targetState] should be a cheap phase key (e.g. `isLoading`), not the data itself. Instant with reduced motion.
 */
@Composable
fun <T> SkeletonCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    val reduceMotion = rememberReduceMotion()
    Crossfade(
        targetState = targetState,
        modifier = modifier,
        animationSpec = if (reduceMotion) snap() else tween(Motion.CrossfadeMs),
        label = "skeletonCrossfade"
    ) { state ->
        // Crossfade lays its content out in a Box; a Column keeps several sibling children stacked
        // vertically like they were before the content was wrapped (and keeps parent scrolling working).
        Column { content(state) }
    }
}

/**
 * For skeletons that sit above or beside content that arrives separately: the skeleton fades out and
 * collapses instead of vanishing, while the content settles in on its own. Instant with reduced motion.
 */
@Composable
fun SkeletonVisibility(visible: Boolean, content: @Composable () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(if (reduceMotion) snap() else tween(Motion.FadeMs)),
        exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(Motion.FadeMs)) + shrinkVertically(tween(Motion.CrossfadeMs))
    ) {
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
fun Modifier.pressScaleEffect(targetScale: Float = Motion.PressedScale): Modifier {
    val reduceMotion = rememberReduceMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reduceMotion) targetScale else 1f,
        animationSpec = Motion.pressSpring(),
        label = "pressScale"
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}
