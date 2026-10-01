package com.tkolymp.tkolympapp.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tkolymp.shared.ServiceLocator
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close

@Composable
actual fun FullscreenImageViewer(imageUrl: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            var scale by remember { mutableStateOf(1f) }
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }

            val transformableState = rememberTransformableState { _centroid, zoomChange, panChange, _rotation ->
                scale = (scale * zoomChange).coerceIn(0.5f, 5f)
                offsetX += panChange.x
                offsetY += panChange.y
            }

            val context = LocalContext.current
            // Uploaded files on our own host (/f/...) need the user's login, like in HtmlText.
            val needsAuth = remember(imageUrl) {
                val uri = android.net.Uri.parse(imageUrl)
                uri.scheme == "https" && uri.host == "tkolymp.cz" && uri.path?.startsWith("/f/") == true
            }
            // null = still loading; "" = no token available
            val tokenState by produceState<String?>(initialValue = if (needsAuth) null else "", imageUrl) {
                value = if (needsAuth) try { ServiceLocator.tokenStorage.getToken() ?: "" } catch (_: Exception) { "" } else ""
            }
            val token = tokenState?.takeIf { it.isNotEmpty() }
            val model: Any = remember(imageUrl, tokenState) {
                val t = token
                if (needsAuth && t != null) {
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .addHeader("Authorization", "Bearer $t")
                        .addHeader("Cookie", "rozpisovnik=$t")
                        .build()
                } else imageUrl
            }
            AsyncImage(
                // wait for the token lookup so the first request isn't fired unauthenticated
                model = if (tokenState == null) null else model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(state = transformableState)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}
