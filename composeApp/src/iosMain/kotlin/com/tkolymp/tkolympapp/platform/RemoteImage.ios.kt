package com.tkolymp.tkolympapp.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// No image loader on iOS yet: always show the fallback (initials / icon).
@Composable
actual fun RemoteImage(url: String?, modifier: Modifier, keepAspectRatio: Boolean, fallback: @Composable () -> Unit) {
    Box(modifier) { fallback() }
}
