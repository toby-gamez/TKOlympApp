package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable

/** Photo capture/picking isn't wired up on iOS yet. */
@Composable
actual fun rememberAvatarPicker(onPicked: (ByteArray) -> Unit): AvatarPicker? = null
