package com.tkolymp.tkolympapp.screens

import com.tkolymp.tkolympapp.components.AppTopBar
import androidx.compose.foundation.clickable
import com.tkolymp.tkolympapp.ui.theme.Spacing
import com.tkolymp.tkolympapp.components.RowDivider
import com.tkolymp.tkolympapp.components.ScheduleCard
import com.tkolymp.tkolympapp.components.SectionLabel
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.tkolymp.tkolympapp.components.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.competitivedata.DanceFederation
import com.tkolymp.shared.competitivedata.FederationHighlight
import com.tkolymp.shared.competitivedata.HighlightKind
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.viewmodels.DanceOrganizationsViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.EmptyState
import com.tkolymp.tkolympapp.components.ErrorState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DanceOrganizationsScreen(
    onBack: () -> Unit = {},
    onOpenOrganization: (code: String) -> Unit = {},
    onOpenFederationEvent: (id: Long) -> Unit = {},
    bottomPadding: Dp = 0.dp
) {
    val viewModel = viewModel<DanceOrganizationsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            AppTopBar(title = AppStrings.current.danceOrg.title, subtitle = AppStrings.current.danceOrg.dataTitle, onBack = onBack)
        }
    ) { padding ->
        SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch { viewModel.load() } },
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
        ) {
            val error = state.error
            when {
                state.federations.isEmpty() && state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.federations.isEmpty() && error != null -> ErrorState(error, onRetry = { scope.launch { viewModel.load() } })
                state.federations.isEmpty() -> EmptyState(AppStrings.current.danceOrg.noDataYet, fullPage = true)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
                ) {
                    state.nearestCompetition?.let { comp ->
                        item(key = "nearest") {
                            SectionLabel(AppStrings.current.competition.nearestCompetition)
                            NearestCompetitionCard(
                                event = comp,
                                onOpen = { onOpenFederationEvent(comp.id) },
                                modifier = Modifier.padding(horizontal = Spacing.default)
                            )
                        }
                    }
                    HighlightKind.entries.forEach { kind ->
                        val list = state.highlights[kind] ?: return@forEach
                        item(key = "highlights_$kind") {
                            SectionLabel(highlightTitle(kind))
                            ScheduleCard(Modifier.padding(horizontal = Spacing.default)) {
                                if (list.isEmpty()) {
                                    Text(
                                        AppStrings.current.danceOrg.noHighlights,
                                        modifier = Modifier.padding(14.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                list.take(HIGHLIGHTS_SHOWN).forEachIndexed { i, h ->
                                    if (i > 0) RowDivider()
                                    HighlightRow(h, onClick = { h.eventId?.let(onOpenFederationEvent) })
                                }
                            }
                        }
                    }
                    item(key = "federations") {
                        SectionLabel(AppStrings.current.danceOrg.dataTitle)
                        ScheduleCard(Modifier.padding(horizontal = Spacing.default)) {
                            state.federations.forEachIndexed { i, f ->
                                if (i > 0) RowDivider()
                                FederationRow(f, onClick = { onOpenOrganization(f.code) })
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val HIGHLIGHTS_SHOWN = 10

private fun highlightTitle(kind: HighlightKind): String = with(AppStrings.current.danceOrg) {
    when (kind) {
        HighlightKind.YEAR_ACHIEVEMENTS -> yearHighlights
        HighlightKind.HALL_OF_CHAMPIONS -> hallOfChampions
        HighlightKind.LAST_WEEKEND -> lastWeekend
    }
}

/** One placement: rank tile, competitor, then "category · event" and a chevron when the event can be opened. */
@Composable
private fun HighlightRow(highlight: FederationHighlight, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val clickable = highlight.eventId != null
    Row(
        modifier = modifier.fillMaxWidth()
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("${highlight.ranking}.", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        Column(Modifier.weight(1f)) {
            Text(highlight.competitorName ?: "–", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2)
            val detail = listOfNotNull(highlight.categoryName, highlight.eventName).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
        if (clickable) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Federation code as written in Czech/Slovak: ČSTS, SZTŠ (WDSF and unknown codes just upper-cased). */
internal fun federationLabel(code: String): String = when (code.lowercase()) {
    "csts" -> "ČSTS"
    "szts" -> "SZTŠ"
    else -> code.uppercase()
}

/** Flag of the federation's country, or the globe for the international WDSF; null for an unknown federation. */
internal fun federationEmoji(code: String): String? = when (code.lowercase()) {
    "csts" -> "\uD83C\uDDE8\uD83C\uDDFF"
    "szts" -> "\uD83C\uDDF8\uD83C\uDDF0"
    "wdsf" -> "\uD83C\uDF0D"
    else -> null
}

/** One federation row: code tile, name, "Soutěžní data" subtitle and chevron (prototype "Soutěžní data" list). */
@Composable
private fun FederationRow(federation: DanceFederation, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(federationLabel(federation.code), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        Column(Modifier.weight(1f)) {
            Text(federation.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(AppStrings.current.danceOrg.dataTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
