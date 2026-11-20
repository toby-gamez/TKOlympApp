package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkolymp.tkolympapp.platform.RemoteImage
import com.tkolymp.tkolympapp.ui.theme.AppTheme
import com.tkolymp.tkolympapp.ui.theme.Spacing

private val PersonHeaderAvatarSize = 80.dp

/**
 * Header for a person: avatar on the left, name with optional [subtitle] (e.g. login)
 * and a single plain-text line of [details] (trainer, dance class, …) next to it.
 */
@Composable
fun PersonHeader(
    rawName: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    details: List<String> = emptyList(),
) {
    // Composed names can carry a leading/trailing space (empty title parts), which shifts the name off the text edge.
    val name = rawName.trim()
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.default, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RemoteImage(
            url = imageUrl,
            modifier = Modifier.size(PersonHeaderAvatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            fallback = { InitialsAvatar(name = name, size = PersonHeaderAvatarSize, fontSize = 28.sp) }
        )
        Column(modifier = Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Header for a couple: the overlapping pair avatar next to the pair name and a single plain-text line of [details] (age group, period, …); same layout as [PersonHeader]. */
@Composable
fun CoupleHeader(
    name: String,
    manName: String?,
    womanName: String?,
    modifier: Modifier = Modifier,
    manImageUrl: String? = null,
    womanImageUrl: String? = null,
    details: List<String> = emptyList(),
    meta: List<String> = emptyList(),
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.default, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoupleAvatar(womanName = womanName, manName = manName, size = 56.dp, fontSize = 20.sp, womanImageUrl = womanImageUrl, manImageUrl = manImageUrl)
        Column(modifier = Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name.trim(), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (meta.isNotEmpty()) {
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CoupleHeaderPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.default)) {
        CoupleHeader(name = "Adam Baďura - Veronika Bokůvková", manName = "Adam Baďura", womanName = "Veronika Bokůvková", details = listOf("Dospělí", "17. 12. 2024 –"), meta = listOf("Aktivní", "🇨🇿 ČSTS 69899"))
    }
}

@Preview(name = "CoupleHeader — Light")
@Composable
private fun CoupleHeaderPreviewLight() {
    AppTheme(darkTheme = false) { CoupleHeaderPreviewContent() }
}

@Preview(name = "CoupleHeader — Dark")
@Composable
private fun CoupleHeaderPreviewDark() {
    AppTheme(darkTheme = true) { CoupleHeaderPreviewContent() }
}

@Composable
private fun PersonHeaderPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.default)) {
        PersonHeader(rawName = "Ing. Jana Nováková, Ph.D.", imageUrl = null, subtitle = "jnovakova", details = listOf("Trenér", "STT/LAT A/B"))
    }
}

@Preview(name = "PersonHeader — Light")
@Composable
private fun PersonHeaderPreviewLight() {
    AppTheme(darkTheme = false) { PersonHeaderPreviewContent() }
}

@Preview(name = "PersonHeader — Dark")
@Composable
private fun PersonHeaderPreviewDark() {
    AppTheme(darkTheme = true) { PersonHeaderPreviewContent() }
}
