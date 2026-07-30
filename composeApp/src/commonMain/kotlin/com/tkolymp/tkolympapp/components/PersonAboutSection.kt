package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.people.PersonDetails
import com.tkolymp.tkolympapp.components.icons.FacebookLogo
import com.tkolymp.tkolympapp.components.icons.InstagramLogo
import com.tkolymp.tkolympapp.components.icons.TikTokLogo
import com.tkolymp.tkolympapp.platform.HtmlText

private data class SocialLink(val label: String, val url: String?, val icon: ImageVector, val brandColored: Boolean)

private fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
}

// Guards against non-URL text (e.g. a plain name typed into the field) producing a broken
// link like "https://Filip Canibal" that browsers can't open and fall back to a web search.
private fun looksLikeUrl(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isBlank() || trimmed.any { it.isWhitespace() }) return false
    val host = trimmed.removePrefix("https://").removePrefix("http://").substringBefore("/")
    return host.contains('.') && !host.startsWith(".") && !host.endsWith(".")
}

private fun buildSocialLinks(person: PersonDetails): List<SocialLink> {
    val links = mutableListOf<SocialLink>()
    person.instagramUsername?.takeIf { it.isNotBlank() }?.let {
        links += SocialLink("@$it", "https://www.instagram.com/$it", InstagramLogo, brandColored = true)
    }
    person.tiktokUsername?.takeIf { it.isNotBlank() }?.let {
        links += SocialLink("@$it", "https://www.tiktok.com/@$it", TikTokLogo, brandColored = true)
    }
    // Facebook doesn't have a reliable handle/URL convention for personal profiles, so this
    // is shown as the plain name/text the person entered — not a clickable link.
    person.facebookUrl?.takeIf { it.isNotBlank() }?.let {
        links += SocialLink(it.trim(), null, FacebookLogo, brandColored = true)
    }
    person.websiteUrl?.takeIf { it.isNotBlank() && looksLikeUrl(it) }?.let {
        val normalized = normalizeUrl(it)
        val displayHost = normalized.removePrefix("https://").removePrefix("http://").removeSuffix("/")
        links += SocialLink(displayHost, normalized, Icons.Default.Language, brandColored = false)
    }
    return links
}

@Composable
fun SocialLinksRow(person: PersonDetails, modifier: Modifier = Modifier) {
    val links = buildSocialLinks(person)
    if (links.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        links.forEach { link ->
            SuggestionChip(
                onClick = { link.url?.let { url -> try { uriHandler.openUri(url) } catch (_: Exception) {} } },
                label = { Text(link.label) },
                icon = {
                    Icon(
                        link.icon,
                        contentDescription = null,
                        tint = if (link.brandColored) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors()
            )
        }
    }
}

@Composable
fun PersonNoteCard(note: String?, modifier: Modifier = Modifier) {
    if (note.isNullOrBlank()) return
    Card(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(AppStrings.current.profile.note, style = MaterialTheme.typography.labelLarge)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            HtmlText(
                html = note,
                modifier = Modifier.fillMaxWidth(),
                textColor = MaterialTheme.colorScheme.onSurface,
                linkColor = MaterialTheme.colorScheme.primary,
                textSizeSp = MaterialTheme.typography.bodyMedium.fontSize.value
            )
        }
    }
}
