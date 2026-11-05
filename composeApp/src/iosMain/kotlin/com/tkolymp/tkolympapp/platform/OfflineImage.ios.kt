package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable

// iOS has no image loader yet, so there is nothing to keep offline.
@Composable
actual fun PersistOwnImageOffline(url: String?) {}
