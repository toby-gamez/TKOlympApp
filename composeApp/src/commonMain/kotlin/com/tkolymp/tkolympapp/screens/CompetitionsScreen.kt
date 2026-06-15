package com.tkolymp.tkolympapp.screens

import com.tkolymp.tkolympapp.SwipeToReload
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.competitions.Competition
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.tkolympapp.util.StaggeredItem
import com.tkolymp.tkolympapp.util.tabContentTransitionSpec
import com.tkolymp.shared.viewmodels.CompetitionViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionsScreen(
    onBack: () -> Unit = {},
    bottomPadding: Dp = 0.dp
) {
    val viewModel = viewModel<CompetitionViewModel>()
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        AppStrings.current.competition.upcoming,
        AppStrings.current.competition.results
    )

    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) scope.launch { viewModel.load(forceRefresh = false) }
        else scope.launch { viewModel.loadPast(forceRefresh = false) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.current.competition.competitions) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = bottomPadding),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(title) }
                    )
                }
            }

            SwipeToReload(
                isRefreshing = state.isLoading,
                onRefresh = {
                    if (selectedTab == 0) scope.launch { viewModel.load(forceRefresh = true) }
                    else scope.launch { viewModel.loadPast(forceRefresh = true) }
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { tabContentTransitionSpec() },
                    label = "competitionsTabContent"
                ) { tab ->
                    if (tab == 0) {
                        CompetitionsListContent(
                            competitions = state.upcomingCompetitions,
                            isLoading = state.isLoading,
                            emptyText = AppStrings.current.competition.noUpcoming,
                            sortDescending = false
                        )
                    } else {
                        CompetitionsListContent(
                            competitions = state.pastCompetitions,
                            isLoading = state.isLoading,
                            emptyText = AppStrings.current.competition.noResults,
                            sortDescending = true
                        )
                    }
                }
            }
        }

        if (state.error != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearError() }) { Text(AppStrings.current.commonActions.ok) }
                },
                title = { Text(AppStrings.current.competition.competitions) },
                text = { Text(state.error?.message ?: "") }
            )
        }
    }
}

@Composable
private fun CompetitionsListContent(
    competitions: List<Competition>,
    isLoading: Boolean,
    emptyText: String,
    sortDescending: Boolean
) {
    var listVisible by remember { mutableStateOf(false) }
    LaunchedEffect(competitions.isNotEmpty()) { if (competitions.isNotEmpty()) listVisible = true }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        if (competitions.isEmpty() && !isLoading) {
            Text(
                text = emptyText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val grouped = competitions
                .groupBy { it.eventId?.toString() ?: it.eventName ?: it.competitionDate }
                .entries
                .let { entries ->
                    if (sortDescending) entries.sortedByDescending { (_, comps) -> comps.first().competitionDate }
                    else entries.sortedBy { (_, comps) -> comps.first().competitionDate }
                }
            grouped.forEachIndexed { i, (_, eventComps) ->
                StaggeredItem(index = i, visible = listVisible) {
                    CompetitionEventCard(competitions = eventComps)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun CompetitionEventCard(competitions: List<Competition>) {
    val first = competitions.first()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = first.eventName ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                first.eventLocation?.takeIf { it.isNotBlank() }?.let { loc ->
                    Text(loc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(first.competitionDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val byCompetitor = competitions.groupBy { it.competitorName?.takeIf { n -> n.isNotBlank() } ?: it.personName ?: "" }
            byCompetitor.values.forEach { competitorComps ->
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(6.dp))

                val rep = competitorComps.first()
                val nameDisplay = rep.competitorName?.takeIf { it.isNotBlank() } ?: rep.personName ?: ""
                if (nameDisplay.isNotBlank()) {
                    Text(nameDisplay, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(3.dp))
                }

                val scheduled = competitorComps.filter { !it.hasResult }.sortedBy { it.checkInEnd }
                val results = competitorComps.filter { it.hasResult }.sortedBy { it.ranking }

                scheduled.forEach { comp -> CompetitionEntryRow(comp) }

                if (scheduled.isNotEmpty() && results.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                }

                results.forEach { comp -> CompetitionEntryRow(comp) }
            }
        }
    }
}

@Composable
internal fun CompetitionEntryRow(comp: Competition) {
    val rawCategory = comp.category?.name?.takeIf { it.isNotBlank() }
        ?: comp.competitionType?.takeIf { it.isNotBlank() }
        ?: ""
    val categoryName = AppStrings.current.competition.formatType(rawCategory)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = comp.checkInEnd?.take(5) ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 60.dp)
        )
        Text(
            text = categoryName,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        if (comp.hasResult && comp.ranking != null) {
            val rankNum = comp.rankingTo?.takeIf { it != comp.ranking }
                ?.let { "${comp.ranking}-$it." } ?: "${comp.ranking}."
            val total = comp.participants?.let { " z $it" } ?: ""
            Text(
                text = rankNum + total,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        val rightLabel = buildString {
            comp.pointGain?.takeIf { it.isNotBlank() }?.let { raw ->
                val formatted = raw.toDoubleOrNull()?.let { d ->
                    if (d % 1.0 == 0.0) d.toInt().toString() else raw
                } ?: raw
                append(formatted)
                append(AppStrings.current.competition.pointsSuffix)
            }
            if (comp.isFinal) { if (isNotEmpty()) append(" "); append("F") }
        }
        if (rightLabel.isNotBlank()) {
            Text(
                text = rightLabel,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
