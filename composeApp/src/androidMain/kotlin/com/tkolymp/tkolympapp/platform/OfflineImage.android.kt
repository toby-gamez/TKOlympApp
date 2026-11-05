package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun PersistOwnImageOffline(url: String?) {
    val context = LocalContext.current.applicationContext
    LaunchedEffect(url) {
        if (url == null) OfflineAvatarStore.clear(context) else OfflineAvatarStore.save(context, url)
    }
}
