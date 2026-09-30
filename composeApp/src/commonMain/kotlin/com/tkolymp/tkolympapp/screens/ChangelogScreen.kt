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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import com.tkolymp.shared.changelog.ChangelogViewModel
import com.tkolymp.shared.language.AppStrings
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
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant

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
                val richTextState = remember(release.tagName, release.body, linkColor, codeBackground) {
                    RichTextState().apply {
                        config.linkColor = linkColor
                        config.codeSpanBackgroundColor = codeBackground
                        setMarkdown(stripDuplicateTitle(release.body))
                    }
                }
                RichText(
                    state = richTextState,
                    modifier = Modifier.fillMaxWidth(),
                    color = textColor,
                    fontSize = 14.sp,
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

/** Drops a leading h1 line — it duplicates the release title shown in the card header. */
private fun stripDuplicateTitle(md: String): String {
    return md.lines()
        .dropWhile { it.trimEnd().startsWith("# ") && !it.trimEnd().startsWith("## ") }
        .joinToString("\n")
        .trimStart()
}
