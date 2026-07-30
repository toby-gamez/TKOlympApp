package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Returns a suspend callback that shares the given [ImageBitmap] via the platform share sheet.
 * [fileBaseName] names the exported PNG (no extension); [shareTitle] is used as the Android
 * share-sheet chooser title. Grab the callback once in a composable and call it from a coroutine.
 */
@Composable
expect fun rememberShareImageCallback(fileBaseName: String, shareTitle: String): suspend (ImageBitmap) -> Unit
