package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tkolymp.tkolympapp.ui.theme.AppTheme

/**
 * Unified "nothing here" message: a tinted circular icon above a centered title
 * (and optional subtitle). Use [fullPage] when the message replaces the whole screen/tab
 * content; the compact variant fits inside a section or list.
 *
 * Inside a LazyColumn, pass `Modifier.fillParentMaxSize()` together with `fullPage = true`
 * to center it in the viewport while keeping the list scrollable (pull-to-refresh).
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    subtitle: String? = null,
    fullPage: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    StatusMessage(
        title = title,
        icon = icon,
        subtitle = subtitle,
        fullPage = fullPage,
        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
        action = action,
    )
}

/** Shared layout for [EmptyState] and [ErrorState] so both look identical apart from colors. */
@Composable
internal fun StatusMessage(
    title: String,
    icon: ImageVector,
    subtitle: String?,
    fullPage: Boolean,
    iconContainerColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = if (fullPage) modifier.fillMaxSize() else modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (fullPage) 12.dp else 8.dp),
            modifier = Modifier.padding(
                horizontal = 32.dp,
                vertical = if (fullPage) 32.dp else 20.dp
            )
        ) {
            Surface(
                shape = CircleShape,
                color = iconContainerColor,
                modifier = Modifier.size(if (fullPage) 72.dp else 56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(if (fullPage) 36.dp else 28.dp),
                        tint = iconColor
                    )
                }
            }
            Text(
                text = title,
                style = if (fullPage) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            action?.invoke()
        }
    }
}

@Preview(name = "EmptyState — Light")
@Composable
private fun EmptyStatePreviewLight() {
    AppTheme(darkTheme = false) {
        Column(Modifier.background(MaterialTheme.colorScheme.background)) {
            EmptyState(title = "Žádné naplánované akce")
            EmptyState(title = "Žádné naplánované akce", subtitle = "Zkuste to později", fullPage = true)
        }
    }
}

@Preview(name = "EmptyState — Dark")
@Composable
private fun EmptyStatePreviewDark() {
    AppTheme(darkTheme = true) {
        Column(Modifier.background(MaterialTheme.colorScheme.background)) {
            EmptyState(title = "Žádné naplánované akce")
            EmptyState(title = "Žádné naplánované akce", subtitle = "Zkuste to později", fullPage = true)
        }
    }
}
