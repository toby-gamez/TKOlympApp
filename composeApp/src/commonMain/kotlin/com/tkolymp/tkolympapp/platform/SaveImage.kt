package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Returns a suspend callback that saves the given [ImageBitmap] as a PNG into the device's photo
 * gallery. The callback returns true on success. [fileBaseName] names the saved image (no extension).
 */
@Composable
expect fun rememberSaveImageToGalleryCallback(fileBaseName: String): suspend (ImageBitmap) -> Boolean
