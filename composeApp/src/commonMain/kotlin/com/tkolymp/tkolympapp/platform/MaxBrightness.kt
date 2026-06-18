package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable

/** Maximizes screen brightness while the composable is in the composition; no-op on iOS. */
@Composable
expect fun MaxScreenBrightness()
