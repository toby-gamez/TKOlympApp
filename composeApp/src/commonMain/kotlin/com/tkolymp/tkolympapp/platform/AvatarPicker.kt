package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable

/** Opens the device's own camera app or photo picker; the chosen picture arrives via the callback of [rememberAvatarPicker]. */
class AvatarPicker(val takePhoto: () -> Unit, val pickFromGallery: () -> Unit)

/**
 * Launchers for taking or choosing a profile photo, delivered as JPEG bytes already downscaled for upload.
 * Returns null where this isn't supported (iOS), so callers can hide the option.
 */
@Composable
expect fun rememberAvatarPicker(onPicked: (ByteArray) -> Unit): AvatarPicker?
