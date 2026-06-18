package com.tkolymp.tkolympapp

import androidx.compose.runtime.Composable

// Integrity check is not performed on iOS (App Store code signing provides the equivalent guarantee).
@Composable
actual fun IntegrityBlockedScreen() = Unit
