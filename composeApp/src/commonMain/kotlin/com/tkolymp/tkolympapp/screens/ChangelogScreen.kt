package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.changelog.ChangelogViewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.tkolympapp.platform.HtmlText
import com.tkolymp.tkolympapp.util.StaggeredItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit = {}) {
    val vm = viewModel<ChangelogViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()

    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.current.otherScreen.changelog) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppStrings.current.commonActions.back
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = AppStrings.current.commonActions.retry
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.error != null && state.releases.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.error?.message ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp)
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                itemsIndexed(state.releases) { index, release ->
                    StaggeredItem(index = index, visible = contentVisible, baseDelayMs = 40) {
                        ReleaseCard(release = release)
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: com.tkolymp.shared.changelog.ChangelogRelease) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val linkColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = release.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = release.tagName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = release.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedColor,
                    fontWeight = FontWeight.Medium
                )
                release.publishedAt?.let { pubAt ->
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedColor
                    )
                    Text(
                        text = formatReleaseDate(pubAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedColor
                    )
                }
            }
            if (release.body.isNotBlank()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                HtmlText(
                    html = markdownToHtml(release.body),
                    modifier = Modifier.fillMaxWidth(),
                    textColor = textColor,
                    linkColor = linkColor,
                    textSizeSp = 14f,
                )
            }
        }
    }
}

private fun formatReleaseDate(iso: String): String {
    return try {
        val date = iso.substringBefore('T')
        val parts = date.split('-')
        if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else date
    } catch (_: Exception) {
        iso
    }
}

internal fun markdownToHtml(md: String): String {
    // Drop the first h1 line — it duplicates the release title shown in the card header
    val stripped = md.lines().dropWhile { it.trimEnd().startsWith("# ") && !it.trimEnd().startsWith("## ") }.joinToString("\n").trimStart()
    val lines = stripped.lines()
    val out = StringBuilder()
    var inUl = false
    var inOl = false
    var olCounter = 0

    fun closeLists() {
        if (inUl) { out.append("</ul>"); inUl = false }
        if (inOl) { out.append("</ol>"); inOl = false; olCounter = 0 }
    }

    fun inlineFormat(s: String): String {
        var r = s
        // bold+italic ***text*** / ___text___
        r = r.replace(Regex("\\*\\*\\*(.+?)\\*\\*\\*")) { "<b><i>${it.groupValues[1]}</i></b>" }
        r = r.replace(Regex("___(.+?)___")) { "<b><i>${it.groupValues[1]}</i></b>" }
        // bold **text** / __text__
        r = r.replace(Regex("\\*\\*(.+?)\\*\\*")) { "<b>${it.groupValues[1]}</b>" }
        r = r.replace(Regex("__(.+?)__")) { "<b>${it.groupValues[1]}</b>" }
        // italic *text* / _text_
        r = r.replace(Regex("(?<![*_])\\*(?![*\\s])(.+?)(?<![\\s*])\\*(?![*])")) { "<i>${it.groupValues[1]}</i>" }
        r = r.replace(Regex("(?<!_)_(?!_)(.+?)(?<!_)_(?!_)")) { "<i>${it.groupValues[1]}</i>" }
        // strikethrough ~~text~~
        r = r.replace(Regex("~~(.+?)~~")) { "<s>${it.groupValues[1]}</s>" }
        // inline code `code`
        r = r.replace(Regex("`([^`]+)`")) { "<code>${it.groupValues[1]}</code>" }
        // links [text](url)
        r = r.replace(Regex("\\[([^]]+)]\\(([^)]+)\\)")) { "<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>" }
        // auto-link bare URLs not already in an <a> tag
        r = r.replace(Regex("(?<!href=\")(https?://[^\\s<>\"]+)")) { "<a href=\"${it.groupValues[1]}\">${it.groupValues[1]}</a>" }
        return r
    }

    for (line in lines) {
        val trimmed = line.trimEnd()
        when {
            // Setext-style headings (=== or ---) are not handled; only ATX (##)
            trimmed.startsWith("### ") -> {
                closeLists()
                out.append("<h3 style=\"margin:0;font-size:15px\">${inlineFormat(trimmed.removePrefix("### ").trim())}</h3>")
            }
            trimmed.startsWith("## ") -> {
                closeLists()
                out.append("<h2 style=\"margin:0;font-size:16px\">${inlineFormat(trimmed.removePrefix("## ").trim())}</h2>")
            }
            trimmed.startsWith("# ") -> {
                closeLists()
                out.append("<h1 style=\"margin:0;font-size:17px\">${inlineFormat(trimmed.removePrefix("# ").trim())}</h1>")
            }
            // horizontal rule --- / ***
            trimmed.matches(Regex("[-*]{3,}")) -> {
                closeLists()
                out.append("<hr>")
            }
            // unordered list item
            trimmed.matches(Regex("^[-*+] .+")) -> {
                if (inOl) { out.append("</ol>"); inOl = false; olCounter = 0 }
                if (!inUl) { out.append("<ul style=\"margin:0;padding-left:18px\">"); inUl = true }
                out.append("<li style=\"margin:0\">${inlineFormat(trimmed.substring(2))}</li>")
            }
            // ordered list item
            trimmed.matches(Regex("^\\d+\\. .+")) -> {
                if (inUl) { out.append("</ul>"); inUl = false }
                if (!inOl) { out.append("<ol style=\"margin:0;padding-left:18px\">"); inOl = true; olCounter = 0 }
                olCounter++
                val content = trimmed.replace(Regex("^\\d+\\. "), "")
                out.append("<li style=\"margin:0\">${inlineFormat(content)}</li>")
            }
            // blockquote
            trimmed.startsWith("> ") -> {
                closeLists()
                out.append("<blockquote style=\"margin:0 0 0 10px;padding-left:8px;border-left:3px solid #aaa\">${inlineFormat(trimmed.removePrefix("> "))}</blockquote>")
            }
            // blank line → paragraph break
            trimmed.isEmpty() -> {
                closeLists()
                out.append("")
            }
            // normal paragraph line
            else -> {
                closeLists()
                out.append("<p style=\"margin:0\">${inlineFormat(trimmed)}</p>")
            }
        }
    }
    closeLists()
    return out.toString()
}
