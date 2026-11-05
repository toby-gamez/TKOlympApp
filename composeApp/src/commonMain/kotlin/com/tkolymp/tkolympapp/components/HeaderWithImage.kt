package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tkolymp.tkolympapp.platform.RemoteImage
import com.tkolymp.tkolympapp.ui.theme.AppTheme

/** Picture shape of a [HeaderWithImage]: circle for people, rounded rectangle for everything else. */
enum class HeaderImageShape { Circle, RoundedRect }

/**
 * Screen header with the title on the left and the picture on the right.
 * [fallback] is drawn inside the picture bounds while the image loads or when there is none.
 * [content] goes under the title (chips, subtitle, …).
 * With [imageAtStart] the picture is on the left and the whole group is centered.
 */
@Composable
fun HeaderWithImage(
    title: String,
    imageUrl: String?,
    shape: HeaderImageShape,
    modifier: Modifier = Modifier,
    keepImageRatio: Boolean = false,
    imageAtStart: Boolean = false,
    fallback: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val imageModifier = when (shape) {
        HeaderImageShape.Circle -> Modifier.size(HeaderImageHeight).clip(CircleShape)
        HeaderImageShape.RoundedRect ->
            (if (keepImageRatio) Modifier.widthIn(max = HeaderImageWidth).heightIn(max = HeaderImageWidth)
            else Modifier.size(width = HeaderImageWidth, height = HeaderImageHeight))
                .clip(RoundedCornerShape(16.dp))
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = if (imageAtStart) Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            else Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val image = @Composable {
            RemoteImage(url = imageUrl, modifier = imageModifier, keepAspectRatio = keepImageRatio, fallback = fallback)
        }
        if (imageAtStart) image()
        Column(modifier = Modifier.weight(1f, fill = !imageAtStart), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            content()
        }
        if (!imageAtStart) image()
    }
}

private val HeaderImageHeight = 88.dp
private val HeaderImageWidth = 120.dp

/** Neutral placeholder for [HeaderImageShape.RoundedRect] headers without a picture; [square] makes it 1:1. */
@Composable
fun HeaderIconFallback(icon: ImageVector, modifier: Modifier = Modifier, square: Boolean = false) {
    Box(
        modifier = modifier.size(width = if (square) HeaderImageHeight else HeaderImageWidth, height = HeaderImageHeight)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun HeaderWithImagePreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        HeaderWithImage(
            title = "Ing. Jana Nováková",
            imageUrl = null,
            shape = HeaderImageShape.Circle,
            fallback = { InitialsAvatar(name = "Jana Nováková", size = HeaderImageHeight, fontSize = MaterialTheme.typography.headlineMedium.fontSize) }
        )
        HeaderWithImage(
            title = "Letní soustředění",
            imageUrl = null,
            shape = HeaderImageShape.RoundedRect,
            fallback = { HeaderIconFallback(Icons.Outlined.Event) }
        )
        HeaderWithImage(
            title = "Důležité upozornění pro všechny členy",
            imageUrl = null,
            shape = HeaderImageShape.RoundedRect,
            fallback = { HeaderIconFallback(Icons.Outlined.Campaign) }
        )
    }
}

@Preview(name = "HeaderWithImage — Light")
@Composable
private fun HeaderWithImagePreviewLight() {
    AppTheme(darkTheme = false) { HeaderWithImagePreviewContent() }
}

@Preview(name = "HeaderWithImage — Dark")
@Composable
private fun HeaderWithImagePreviewDark() {
    AppTheme(darkTheme = true) { HeaderWithImagePreviewContent() }
}
