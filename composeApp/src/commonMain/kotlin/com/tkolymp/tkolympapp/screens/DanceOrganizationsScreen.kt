package com.tkolymp.tkolympapp.screens

import com.tkolymp.tkolympapp.components.AppTopBar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tkolymp.shared.viewmodels.ClubResultsState
import com.tkolymp.shared.viewmodels.ClubResultsViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
    val clubVm = viewModel<ClubResultsViewModel>()
    val clubState by clubVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(Unit) { clubVm.load() }

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
                    item(key = "federations") {
                        SectionLabel(AppStrings.current.danceOrg.dataTitle)
                        ScheduleCard(Modifier.padding(horizontal = Spacing.default)) {
                            state.federations.forEachIndexed { i, f ->
                                if (i > 0) RowDivider()
                                FederationRow(f, onClick = { onOpenOrganization(f.code) })
                            }
                        }
                    }
                    clubResultsItems(clubState, clubVm, onOpenFederationEvent)
                }
            }
        }
    }
}

private fun highlightTitle(kind: HighlightKind): String = with(AppStrings.current.danceOrg) {
    when (kind) {
        HighlightKind.YEAR_ACHIEVEMENTS -> yearHighlights
        HighlightKind.HALL_OF_CHAMPIONS -> hallOfChampions
        HighlightKind.LAST_WEEKEND -> lastWeekend
    }
}

/** The club's result lists as lazy items: tabs, year / type filters, then the grouped rows. */
private fun LazyListScope.clubResultsItems(state: ClubResultsState, vm: ClubResultsViewModel, onOpenEvent: (Long) -> Unit) {
    val strings = AppStrings.current.danceOrg
    val hall = state.kind == HighlightKind.HALL_OF_CHAMPIONS
    item(key = "club_header") {
        SectionLabel(strings.clubResults)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.default),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HighlightKind.entries.forEach { kind ->
                FilterChip(selected = state.kind == kind, onClick = { vm.selectKind(kind) }, label = { Text(highlightTitle(kind)) })
            }
        }
        if (state.kind == HighlightKind.LAST_WEEKEND) {
            val range = state.results
            if (range?.since != null && range.until != null) {
                Text(
                    weekRangeLabel(range.since!!, range.until!!),
                    modifier = Modifier.padding(horizontal = Spacing.default, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            YearPicker(state.years, state.year, allowAll = hall, onSelect = vm::selectYear, modifier = Modifier.padding(horizontal = Spacing.default, vertical = 8.dp))
        }
        if (hall && state.availableTypes.size > 1) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.default),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.availableTypes.forEach { type ->
                    FilterChip(selected = type !in state.hiddenTypes, onClick = { vm.toggleType(type) }, label = { Text(strings.competitorType(type)) })
                }
            }
        }
    }
    val rows = state.rows
    when {
        state.isLoading && state.results == null -> item(key = "club_loading") {
            Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
        }
        state.error != null && state.results == null -> item(key = "club_error") {
            Text(state.error!!.message ?: "", Modifier.padding(Spacing.default), color = MaterialTheme.colorScheme.error)
        }
        rows.isEmpty() -> item(key = "club_empty") {
            Text(strings.noHighlights, Modifier.padding(Spacing.default), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> {
            // The champions' gallery is grouped by competitor, the others by event (newest first, as sorted).
            val groups = if (hall) rows.groupBy { it.competitorId ?: it.competitorName } else rows.groupBy { it.competitionEventKey() }
            groups.forEach { (key, group) ->
                item(key = "club_group_$key") {
                    ScheduleCard(Modifier.padding(horizontal = Spacing.default, vertical = 6.dp)) {
                        val first = group.first()
                        if (hall) HallHeader(first.competitorName, group) else EventHeader(first.startDate, first.eventName)
                        RowDivider()
                        group.forEachIndexed { i, h ->
                            if (i > 0) RowDivider()
                            HighlightRow(h, byCompetitor = hall, onClick = { h.eventId?.let(onOpenEvent) })
                        }
                    }
                }
            }
        }
    }
}

private fun FederationHighlight.competitionEventKey(): Any = eventId ?: (startDate to eventName)

/** Placing number on a square tinted by podium place (plain for 4th and lower). */
@Composable
private fun PlaceBadge(highlight: FederationHighlight) {
    val container = when (highlight.ranking) {
        1 -> MaterialTheme.colorScheme.primaryContainer
        2 -> MaterialTheme.colorScheme.secondaryContainer
        3 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        Modifier.height(40.dp).widthIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(container).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "${highlight.ranking}." + (highlight.rankingTo?.takeIf { it > highlight.ranking }?.let { "–$it." } ?: ""),
            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1
        )
    }
}

/** Date tile (day over month) and the event name. */
@Composable
private fun EventHeader(isoDate: String, name: String?) {
    val parts = formatIsoDate(isoDate).split(". ")
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text(parts.getOrElse(0) { "" }, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimary)
            Text(AppStrings.current.danceOrg.monthShort(parts.getOrNull(1)?.toIntOrNull() ?: 0), fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary)
        }
        Text(name ?: "–", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2)
    }
}

/** Competitor name plus a count per podium place ("1. ×3"). */
@Composable
private fun HallHeader(name: String?, group: List<FederationHighlight>) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name ?: "–", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2)
        (1..3).forEach { place ->
            val n = group.count { it.ranking == place }
            if (n > 0) Box(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("$place. ×$n", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Placing tile, category, and competitor (or date in the champions' gallery, where the competitor heads the group). */
@Composable
private fun HighlightRow(highlight: FederationHighlight, byCompetitor: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val clickable = highlight.eventId != null
    Row(
        modifier = modifier.fillMaxWidth()
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlaceBadge(highlight)
        Column(Modifier.weight(1f)) {
            Text(highlight.categoryName?.let { AppStrings.current.competition.formatType(it) } ?: "–", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2)
            val sub = if (byCompetitor) formatIsoDate(highlight.startDate) else highlight.competitorName
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        if (clickable) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun YearPicker(years: List<Int>, selected: Int?, allowAll: Boolean, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val strings = AppStrings.current.danceOrg
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(strings.yearLabel, style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { open = true }) { Text(selected?.toString() ?: strings.allYears) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (allowAll) DropdownMenuItem(text = { Text(strings.allYears) }, onClick = { open = false; onSelect(null) })
                years.forEach { y -> DropdownMenuItem(text = { Text(y.toString()) }, onClick = { open = false; onSelect(y) }) }
            }
        }
    }
}

/** "16.–22. 11. 2026 (47. týden)" for an inclusive Monday..Sunday range. */
private fun weekRangeLabel(since: String, until: String): String = runCatching {
    val a = LocalDate.parse(since); val b = LocalDate.parse(until)
    val week = (a.dayOfYear - a.dayOfWeek.isoDayNumber + 10) / 7
    "${a.day}.–${b.day}. ${b.month.number}. ${b.year} ($week. ${AppStrings.current.danceOrg.weekLabel})"
}.getOrDefault("$since – $until")

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
