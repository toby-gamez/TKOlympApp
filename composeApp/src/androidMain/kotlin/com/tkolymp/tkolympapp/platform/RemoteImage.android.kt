package com.tkolymp.tkolympapp.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage

@Composable
actual fun RemoteImage(url: String?, modifier: Modifier, keepAspectRatio: Boolean, fallback: @Composable () -> Unit) {
    val image = url?.let { rememberAuthedImage(it) }
    if (image == null) {
        Box(modifier) { fallback() }
        return
    }
    SubcomposeAsyncImage(
        model = image.model,
        imageLoader = image.loader,
        contentDescription = null,
        contentScale = if (keepAspectRatio) ContentScale.Fit else ContentScale.Crop,
        modifier = modifier,
        loading = { fallback() },
        error = { fallback() },
    )
}
