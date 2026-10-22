package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.competitivedata.FederationCompetitionSummary
import com.tkolymp.shared.competitivedata.FederationEventDetail
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.viewmodels.FederationEventDetailState
import com.tkolymp.shared.viewmodels.FederationEventDetailViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.EmptyState
import com.tkolymp.tkolympapp.components.ErrorState
import kotlinx.coroutines.launch

/**
 * Category names the way the competition screens show them (DanceForAll -> TPV, Standard -> STT, ...).
 * WDSF names ("General SENIOR IV OPEN STANDARD") are lower-cased first so the same replacements apply.
 */
internal fun formatFederationCategory(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    val normalised = raw.removePrefix("General ").split(' ').joinToString(" ") { w ->
        if (w.length > 3 && w.all { it.isUpperCase() }) w.lowercase().replaceFirstChar { it.uppercase() } else w
    }
    return AppStrings.current.competition.formatType(normalised)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FederationEventScreen(
    eventId: Long,
    onBack: () -> Unit = {},
    bottomPadding: Dp = 0.dp
) {
    val viewModel = viewModel<FederationEventDetailViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val strings = AppStrings.current.danceOrg
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(eventId) { viewModel.load(eventId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.event?.name ?: strings.event, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                    }
                }
            )
        }
    ) { padding ->
        SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch { viewModel.load(eventId) } },
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
        ) {
            val event = state.event
            when {
                event == null && state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                event == null -> ErrorState(state.error?.message, onRetry = { scope.launch { viewModel.load(eventId) } })
                else -> EventContent(event, state, bottomPadding,
                    onOpenWeb = { uriHandler.openUri(it) },
                    onExpand = { id -> scope.launch { viewModel.loadResults(id) } })
            }
        }
    }
}

@Composable
private fun EventContent(
    event: FederationEventDetail,
    state: FederationEventDetailState,
    bottomPadding: Dp,
    onOpenWeb: (String) -> Unit,
    onExpand: (Long) -> Unit
) {
    val strings = AppStrings.current.danceOrg
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                SelectionContainer {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(event.name ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(formatDateRange(event.startDate, event.endDate), style = MaterialTheme.typography.bodyLarge)
                    strings.status(event.status).takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = if (event.status == "CANCELLED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                    val address = listOfNotNull(
                        event.streetAddress,
                        listOfNotNull(event.postalCode, event.city ?: event.location).joinToString(" ").takeIf { it.isNotBlank() },
                        event.country
                    ).joinToString(", ")
                    InfoLine(strings.address, address)
                    InfoLine(strings.address, event.addressNote)
                    InfoLine(strings.organizer, event.organizer)
                    InfoLine(strings.contact, listOfNotNull(event.contactName, event.contactPhone, event.contactEmail).joinToString(", "))
                    event.websiteUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        TextButton(onClick = { onOpenWeb(url) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" ${strings.website}")
                        }
                    }
                }
                }
            }
        }
        item { Text(strings.competitionsHeader, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (event.competitions.isEmpty()) {
            item { EmptyState(strings.noCompetitions, modifier = Modifier.padding(24.dp)) }
        } else {
            items(event.competitions, key = { it.id }) { c ->
                val isOpen = c.id in expanded
                CompetitionCard(c, isOpen, state, onToggle = {
                    expanded = if (isOpen) expanded - c.id else expanded + c.id
                    if (!isOpen) onExpand(c.id)
                })
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CompetitionCard(c: FederationCompetitionSummary, open: Boolean, state: FederationEventDetailState, onToggle: () -> Unit) {
    val strings = AppStrings.current.danceOrg
    val results = state.results[c.id]
    Card(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(formatFederationCategory(c.categoryName), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    val meta = listOfNotNull(
                        formatIsoDate(c.startDate),
                        strings.competitionType(c.competitionType ?: "").takeIf { it.isNotBlank() },
                        strings.status(c.status).takeIf { it.isNotBlank() },
                        c.participants?.let { "${AppStrings.current.competition.participants}: $it" }
                    ).joinToString(" · ")
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
            if (open) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                when {
                    c.id in state.loadingResults -> Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                    results == null || results.results.isEmpty() -> Text(strings.noResultsYet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        results.results.forEach { r ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    if (r.rankingTo != null && r.rankingTo != r.ranking) "${r.ranking}.–${r.rankingTo}." else "${r.ranking}.",
                                    modifier = Modifier.padding(end = 8.dp).then(Modifier.fillMaxWidth(0.18f)),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(r.competitorName ?: "—", modifier = Modifier.weight(1f))
                                r.pointGain?.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                                    Text("+${it.toString().removeSuffix(".0")}${AppStrings.current.competition.pointsSuffix}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (r.isFinal) Text(
                                    "F",
                                    modifier = Modifier.padding(start = 6.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}
