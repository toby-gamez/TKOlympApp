package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Platform-specific HTML content rendering composable.
 * On Android: renders using a TextView with Html.fromHtml and clickable links.
 * On other platforms: renders plain text after stripping HTML tags.
 */
@Composable
expect fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    linkColor: Color = Color.Unspecified,
    textSizeSp: Float = 14f,
    selectable: Boolean = false
)
