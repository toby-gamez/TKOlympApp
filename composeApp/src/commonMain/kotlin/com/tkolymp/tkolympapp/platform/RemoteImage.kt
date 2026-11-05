package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Loads [url] and fills its bounds (crop), or with [keepAspectRatio] sizes itself to the picture's ratio within the max bounds of [modifier]. [fallback] is drawn while loading, on failure
 * and when [url] is null, so callers always get a sensible picture.
 */
@Composable
expect fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    keepAspectRatio: Boolean = false,
    fallback: @Composable () -> Unit,
)
