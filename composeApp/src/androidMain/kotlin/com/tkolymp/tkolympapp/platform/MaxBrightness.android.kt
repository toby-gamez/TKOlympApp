package com.tkolymp.tkolympapp.platform

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun MaxScreenBrightness() {
    val context = LocalContext.current
    val activity = context as? Activity
    DisposableEffect(Unit) {
        val attrs = activity?.window?.attributes
        val original = attrs?.screenBrightness ?: -1f
        if (attrs != null) {
            attrs.screenBrightness = 1.0f
            activity.window.attributes = attrs
        }
        onDispose {
            val a = activity?.window?.attributes
            if (a != null) {
                a.screenBrightness = original
                activity.window.attributes = a
            }
        }
    }
}
