package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable

/** True when the user asked the OS to reduce or disable animations. */
@Composable
expect fun rememberReduceMotion(): Boolean
