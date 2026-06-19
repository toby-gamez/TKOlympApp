package com.tkolymp.tkolympapp.screens

import com.tkolymp.tkolympapp.SwipeToReload
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.competitions.Competition
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.formatFullCalendarDate
import com.tkolymp.tkolympapp.components.CoupleAvatar
import com.tkolymp.tkolympapp.components.InitialsAvatar
import com.tkolymp.tkolympapp.util.StaggeredItem
import com.tkolymp.tkolympapp.util.tabContentTransitionSpec
import com.tkolymp.shared.viewmodels.CompetitionViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionsScreen(
    onBack: () -> Unit = {},
    bottomPadding: Dp = 0.dp
) {
    val viewModel = viewModel<CompetitionViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

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
                            sortDescending = false,
                            onOpenWeb = { uriHandler.openUri("https://www.csts.cz/dancesport/kalendar_akci") }
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

private const val PAGE_SIZE = 20

@Composable
private fun CompetitionsListContent(
    competitions: List<Competition>,
    isLoading: Boolean,
    emptyText: String,
    sortDescending: Boolean,
    onOpenWeb: (() -> Unit)? = null
) {
    val grouped = remember(competitions, sortDescending) {
        competitions
            .groupBy { it.eventId?.toString() ?: it.eventName ?: it.competitionDate }
            .entries
            .let { if (sortDescending) it.sortedByDescending { e -> e.value.first().competitionDate } else it.sortedBy { e -> e.value.first().competitionDate } }
    }

    var displayCount by remember { mutableIntStateOf(PAGE_SIZE) }
    val lazyState = rememberLazyListState()

    var cardsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(grouped) { if (grouped.isNotEmpty()) cardsVisible = true }

    val reachedBottom by remember {
        derivedStateOf {
            val last = lazyState.layoutInfo.visibleItemsInfo.lastOrNull()
            last != null && last.index >= lazyState.layoutInfo.totalItemsCount - 3
        }
    }

    LaunchedEffect(reachedBottom) {
        if (reachedBottom && displayCount < grouped.size) displayCount += PAGE_SIZE
    }

    val visibleGroups = grouped.take(displayCount)

    LazyColumn(state = lazyState, modifier = Modifier.fillMaxWidth()) {
        if (grouped.isEmpty() && !isLoading) {
            item {
                Text(
                    text = emptyText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            itemsIndexed(visibleGroups, key = { _, (key, _) -> key }) { index, (_, eventComps) ->
                StaggeredItem(index = index, visible = cardsVisible, baseDelayMs = 50) {
                    CompetitionEventCard(competitions = eventComps, onOpenWeb = onOpenWeb)
                }
            }
            if (displayCount < grouped.size) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
internal fun CompetitionEventCard(competitions: List<Competition>, onOpenWeb: (() -> Unit)? = null) {
    val first = competitions.first()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = first.eventName ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (onOpenWeb != null) {
                    IconButton(onClick = onOpenWeb, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = AppStrings.current.commonActions.openInWeb,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val boxBg = MaterialTheme.colorScheme.surfaceVariant
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                first.eventLocation?.takeIf { it.isNotBlank() }?.let { loc ->
                    InfoChip(icon = Icons.Default.Place, text = loc, background = boxBg)
                }
                InfoChip(icon = Icons.Default.CalendarMonth, text = formatCompetitionDate(first.competitionDate), background = boxBg)
            }

            val byCompetitor = competitions.groupBy { it.competitorName?.takeIf { n -> n.isNotBlank() } ?: it.personName ?: "" }
            byCompetitor.values.forEach { competitorComps ->
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                val rep = competitorComps.first()
                val nameDisplay = rep.competitorName?.takeIf { it.isNotBlank() } ?: rep.personName ?: ""
                if (nameDisplay.isNotBlank()) {
                    val coupleNames = nameDisplay.split(" - ", limit = 2).map { it.trim() }.takeIf { it.size == 2 }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (coupleNames != null) {
                            CoupleAvatar(womanName = coupleNames[0], manName = coupleNames[1], size = 24.dp)
                        } else {
                            InitialsAvatar(name = nameDisplay, size = 24.dp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(nameDisplay, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                val scheduled = competitorComps.filter { !it.hasResult }.sortedBy { it.checkInEnd }
                val results = competitorComps.filter { it.hasResult }.sortedBy { it.ranking }

                scheduled.forEach { comp -> CompetitionEntryRow(comp) }

                if (scheduled.isNotEmpty() && results.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                results.forEach { comp -> CompetitionEntryRow(comp) }
            }
        }
    }
}

internal fun formatCompetitionDate(raw: String): String {
    val date = try { LocalDate.parse(raw.substringBefore('T')) } catch (_: Exception) { return raw }
    return formatFullCalendarDate(date, AppStrings.currentLanguage.code, false)
}

@Composable
internal fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, background: Color, trailingMargin: Boolean = true) {
    Box(
        modifier = Modifier
            .then(if (trailingMargin) Modifier.padding(end = 8.dp, bottom = 6.dp) else Modifier)
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun CompetitionEntryRow(comp: Competition, showCategory: Boolean = true, showCheckIn: Boolean = true) {
    val rawCategory = comp.category?.name?.takeIf { it.isNotBlank() }
        ?: comp.competitionType?.takeIf { it.isNotBlank() }
        ?: ""
    val categoryName = AppStrings.current.competition.formatType(rawCategory)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCheckIn) {
            comp.checkInEnd?.take(5)?.takeIf { it.isNotBlank() }?.let { time ->
                InfoChip(
                    icon = Icons.Default.Schedule,
                    text = time,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    trailingMargin = false
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

        if (showCategory) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        if (comp.hasResult && comp.ranking != null) {
            val rankNum = comp.rankingTo?.takeIf { it != comp.ranking }
                ?.let { "${comp.ranking}-$it." } ?: "${comp.ranking}."
            val total = comp.participants?.let { " / $it" } ?: ""
            Row(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = rankNum + total,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        val rightLabel = buildString {
            val pointsValue = comp.pointGain?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
            if (pointsValue != null && pointsValue != 0.0) {
                val formatted = if (pointsValue % 1.0 == 0.0) pointsValue.toInt().toString() else pointsValue.toString()
                if (pointsValue > 0) append("+")
                append(formatted)
                append(AppStrings.current.competition.pointsSuffix)
            }
            if (comp.isFinal) { if (isNotEmpty()) append(" "); append("F") }
        }
        if (rightLabel.isNotBlank()) {
            Row(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = rightLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}
