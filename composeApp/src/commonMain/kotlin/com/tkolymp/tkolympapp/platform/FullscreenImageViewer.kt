package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable

@Composable
expect fun FullscreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit
)
