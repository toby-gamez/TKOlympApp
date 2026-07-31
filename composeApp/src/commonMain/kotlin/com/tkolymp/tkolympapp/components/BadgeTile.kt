package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.tkolympapp.ui.theme.AppTheme

/**
 * A single achievement badge: an icon in a circle (earned = full color, locked = dimmed),
 * a label, and an optional "3/5"-style progress hint for locked counter badges.
 */
@Composable
fun BadgeTile(
    icon: String,
    label: String,
    earned: Boolean,
    modifier: Modifier = Modifier,
    progressText: String? = null,
    isNew: Boolean = false,
    onClick: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(96.dp)
            .padding(8.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (earned) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            ) {
                Text(
                    text = icon,
                    fontSize = 26.sp,
                    modifier = Modifier.alpha(if (earned) 1f else 0.35f)
                )
            }
            // Outside the circle-clipped box above — a pill inside it gets sliced into a
            // ribbon-shaped wedge by the CircleShape clip instead of reading as a corner badge.
            if (isNew) {
                NewIndicatorPill(modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = if (earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (progressText != null) {
            Text(
                text = progressText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Small "NEW" pill shown on badges/diplomas earned since the achievements screen was last opened. */
@Composable
fun NewIndicatorPill(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = AppStrings.current.achievements.newPillLabel,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onError,
        )
    }
}

@Preview(name = "BadgeTile — Light")
@Composable
private fun BadgeTilePreviewLight() {
    AppTheme(darkTheme = false) {
        Column {
            BadgeTile(icon = "🏕️", label = "First camp completed", earned = true, isNew = true)
            BadgeTile(icon = "🔥", label = "8-week streak", earned = false, progressText = "3/8")
        }
    }
}

@Preview(name = "BadgeTile — Dark")
@Composable
private fun BadgeTilePreviewDark() {
    AppTheme(darkTheme = true) {
        Column {
            BadgeTile(icon = "🏕️", label = "First camp completed", earned = true, isNew = true)
            BadgeTile(icon = "🔥", label = "8-week streak", earned = false, progressText = "3/8")
        }
    }
}
