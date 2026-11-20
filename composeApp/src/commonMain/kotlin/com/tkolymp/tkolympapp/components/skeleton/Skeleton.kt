package com.tkolymp.tkolympapp.components.skeleton

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.tkolympapp.platform.rememberReduceMotion
import com.tkolymp.tkolympapp.ui.theme.AppTheme
import com.tkolymp.tkolympapp.ui.theme.Spacing

private const val ShimmerDurationMs = 1200
private const val ShimmerTravelPx = 1000f

/** Paints a moving highlight over the skeleton base color; static when the OS asks to reduce motion. */
fun Modifier.shimmer(): Modifier = composed {
    // Translucent neutral tints so the skeleton never picks up the brand/tinted surface colors.
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    if (rememberReduceMotion()) {
        background(base)
    } else {
        val progress = rememberInfiniteTransition(label = "skeletonShimmer").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(ShimmerDurationMs, easing = LinearEasing), RepeatMode.Restart),
            label = "skeletonShimmerProgress",
        )
        background(
            Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(progress.value * ShimmerTravelPx * 2 - ShimmerTravelPx, 0f),
                end = Offset(progress.value * ShimmerTravelPx * 2, ShimmerTravelPx / 2),
            )
        )
    }
}

@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.small) {
    Box(modifier = modifier.clip(shape).shimmer())
}

@Composable
fun SkeletonLine(modifier: Modifier = Modifier, widthFraction: Float = 1f, height: Dp = Spacing.default) {
    SkeletonBox(modifier = modifier.fillMaxWidth(widthFraction).height(height))
}

@Composable
fun SkeletonCircle(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    SkeletonBox(modifier = modifier.size(size), shape = CircleShape)
}

/** Wraps skeleton content: one "loading" announcement for screen readers, children hidden. */
@Composable
fun SkeletonContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val description = AppStrings.current.commonActions.loading
    Box(modifier = modifier.semantics(mergeDescendants = true) {}.clearAndSetSemantics { contentDescription = description }) {
        content()
    }
}

/** Fixed-width text placeholder; [height] approximates the glyph height, not the line height. */
@Composable
fun SkeletonText(width: Dp, height: Dp = 14.dp, modifier: Modifier = Modifier) {
    SkeletonBox(modifier = modifier.width(width).height(height))
}

@Preview(name = "Skeleton — Light")
@Composable
private fun SkeletonPreviewLight() {
    AppTheme(darkTheme = false) { SkeletonList(count = 4) }
}

@Preview(name = "Skeleton — Dark")
@Composable
private fun SkeletonPreviewDark() {
    AppTheme(darkTheme = true) { SkeletonList(count = 4) }
}
