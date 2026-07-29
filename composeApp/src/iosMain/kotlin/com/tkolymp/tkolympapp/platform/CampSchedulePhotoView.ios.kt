package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** No photos ever exist on iOS since capture is Android-only. */
@Composable
actual fun CampSchedulePhotoThumbnail(photoBytes: ByteArray, modifier: Modifier) {
}
