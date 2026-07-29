package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shows the originally uploaded schedule photo as a thumbnail; tapping it expands to
 * a fullscreen view. Android-only (photos only ever come from the Android capture
 * flow); the iOS actual renders nothing.
 */
@Composable
expect fun CampSchedulePhotoThumbnail(photoBytes: ByteArray, modifier: Modifier = Modifier)
