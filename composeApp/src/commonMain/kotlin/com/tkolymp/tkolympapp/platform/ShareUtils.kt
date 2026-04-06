package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Returns a suspend callback that shares the given [ImageBitmap] via the platform share sheet.
 * Grab the callback once in a composable and call it from a coroutine.
 */
@Composable
expect fun rememberShareStatsCallback(): suspend (ImageBitmap) -> Unit
