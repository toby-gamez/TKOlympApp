package com.tkolymp.tkolympapp.platform

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.request.ImageRequest
import com.tkolymp.shared.ServiceLocator

internal class AuthedImage(val loader: ImageLoader, val model: Any)

private var pinnedLoader: ImageLoader? = null
private var plainLoader: ImageLoader? = null

/**
 * One loader per kind for the whole app, so avatars share Coil's memory and disk cache.
 * Cache headers are ignored: profile photos rarely change and the server may send no-store.
 * Authenticated requests go through the certificate-pinned client; never follow redirects with the token.
 */
private fun sharedLoader(context: Context, pinned: Boolean): ImageLoader = synchronized(AuthedImage::class) {
    (if (pinned) pinnedLoader else plainLoader) ?: ImageLoader.Builder(context)
        .respectCacheHeaders(false)
        .apply { if (pinned) okHttpClient(com.tkolymp.shared.pinnedOkHttpClient()) }
        .build()
        .also { if (pinned) pinnedLoader = it else plainLoader = it }
}

/**
 * Coil loader + request for [url]. Uploaded files on our own host (/f/...) need the user's login,
 * like in HtmlText. Returns null until the token lookup is done so the first request isn't fired
 * unauthenticated.
 */
@Composable
internal fun rememberAuthedImage(url: String): AuthedImage? {
    val context = LocalContext.current
    val needsAuth = remember(url) {
        val uri = Uri.parse(url)
        uri.scheme == "https" && uri.host == "tkolymp.cz" && uri.path?.startsWith("/f/") == true
    }
    // null = still loading; "" = no token available
    val tokenState by produceState<String?>(initialValue = if (needsAuth) null else "", url) {
        value = if (needsAuth) try { ServiceLocator.tokenStorage.getToken() ?: "" } catch (_: Exception) { "" } else ""
    }
    val loader = remember(needsAuth) { sharedLoader(context.applicationContext, needsAuth) }
    val state = tokenState ?: return null
    val token = state.takeIf { it.isNotEmpty() }
    val model: Any = remember(url, state) {
        if (needsAuth && token != null) {
            ImageRequest.Builder(context)
                .data(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Cookie", "rozpisovnik=$token")
                .build()
        } else url
    }
    return AuthedImage(loader, model)
}
