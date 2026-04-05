package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.people.ScoreboardEntry
import com.tkolymp.shared.utils.getLocalizedMonthNameNominative
import com.tkolymp.shared.utils.roundTo1dp
import com.tkolymp.shared.utils.stripTitles
import com.tkolymp.shared.viewmodels.MonthStats
import com.tkolymp.shared.viewmodels.SeasonDetailStats
import com.tkolymp.shared.viewmodels.SeasonSelection
import com.tkolymp.shared.viewmodels.StatsViewModel
import com.tkolymp.shared.viewmodels.TrainerStat
import com.tkolymp.shared.viewmodels.TypeStat
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.BarChart
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit = {},
    bottomPadding: Dp = 0.dp,
    onOpenLeaderboard: () -> Unit = {},
    viewModel: StatsViewModel = viewModel()
) {
    val totalSessions by viewModel.totalSessionsFlow.collectAsState(initial = 0)
    val totalMinutes by viewModel.totalMinutesFlow.collectAsState(initial = 0L)
    val avgPerWeek by viewModel.avgPerWeekFlow.collectAsState(initial = 0.0)
    val currentStreak by viewModel.currentStreakFlow.collectAsState(initial = 0)
    val weeklyData by viewModel.weeklyFlow.collectAsState(initial = emptyList())
    val monthlyData by viewModel.monthlyFlow.collectAsState(initial = emptyList())
    val typeData by viewModel.typeFlow.collectAsState(initial = emptyList())
    val trainerData by viewModel.trainerFlow.collectAsState(initial = emptyList())
    val scoreEntry by viewModel.scoreEntryFlow.collectAsState(initial = null)
    val selectedSeason by viewModel.selectedSeasonFlow.collectAsState(initial = SeasonSelection.default())
    val comparisonData by viewModel.comparisonDataFlow.collectAsState(initial = emptyList())
    val isLoadingComparison by viewModel.isLoadingComparisonFlow.collectAsState(initial = false)
    val compareSeasons by viewModel.compareSeasonsFlow.collectAsState(initial = List(5) { null })
    val compareData by viewModel.compareDataFlow.collectAsState(initial = List<SeasonDetailStats?>(5) { null })
    val isLoadingCompare by viewModel.isLoadingCompareFlow.collectAsState(initial = List(5) { false })
    val isLoading by viewModel.isLoadingFlow.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val strings = AppStrings.current.stats
    var compareMode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadStats(SeasonSelection.default())
    }

    LaunchedEffect(compareMode) {
        if (compareMode && comparisonData.isEmpty() && !isLoadingComparison) {
            viewModel.loadComparison()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (compareMode) strings.seasonComparison else strings.statsTitle) },
                navigationIcon = {
                    IconButton(onClick = { if (compareMode) compareMode = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                    }
                },
                actions = {
                    IconButton(onClick = { compareMode = !compareMode }) {
                        Icon(
                            Icons.Default.CompareArrows,
                            contentDescription = strings.seasonComparison,
                            tint = if (compareMode) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                }
            )
        }
    ) { padding ->
        SwipeToReload(
            isRefreshing = if (compareMode) isLoadingComparison else isLoading,
            onRefresh = { scope.launch { if (compareMode) viewModel.loadComparison() else viewModel.loadStats(selectedSeason, forceRefresh = true) } },
            modifier = Modifier.padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
        ) {
            if (compareMode) {
                CompareScreenContent(viewModel = viewModel, strings = strings)
                return@SwipeToReload
            }
            if (isLoading && totalSessions == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@SwipeToReload
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Season selector ──────────────────────────────────────────
                SeasonSelector(
                    selected = selectedSeason,
                    seasons = SeasonSelection.recent(),
                    currentLabel = strings.currentSeason,
                    lastLabel = strings.lastSeason,
                    onSelect = { season ->
                        scope.launch { viewModel.loadStats(season) }
                    }
                )

                if (totalSessions == 0 && !isLoading) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Text(
                            text = strings.noData,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    return@Column
                }

                // ── Summary cards ────────────────────────────────────────────
                SummaryRow(
                    totalSessions = totalSessions,
                    totalMinutes = totalMinutes,
                    avgPerWeek = avgPerWeek,
                    streak = currentStreak,
                    strings = strings
                )

                // ── Bar chart: weekly activity ───────────────────────────────
                if (weeklyData.isNotEmpty()) {
                    StatsCard(title = strings.weeklyActivity) {
                        val barData = weeklyData.map { Pair(it.weekLabel, it.count) }
                        val highlightIndex = weeklyData.indexOfFirst { it.isCurrent }
                        var selectedBar by remember { mutableStateOf(-1) }
                        BarChart(
                            data = barData,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            highlightIndex = highlightIndex,
                            barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            highlightColor = MaterialTheme.colorScheme.primary,
                            dimColor = MaterialTheme.colorScheme.primaryContainer,
                            labelEvery = 1,
                            showLabels = false,
                            onBarClick = { idx, _, _ ->
                                selectedBar = if (selectedBar == idx) -1 else idx
                            }
                        )
                        // Show tapped-week details (value + hours)
                        if (selectedBar >= 0) {
                            val week = weeklyData.getOrNull(selectedBar)
                            if (week != null) {
                                Spacer(Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Text(
                                        text = "${week.weekLabel} · ${week.count} ${strings.sessionsUnit} · ${(week.minutes / 60.0).roundTo1dp()} ${strings.hoursUnit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Monthly breakdown ────────────────────────────────────────
                if (monthlyData.isNotEmpty()) {
                    StatsCard(title = strings.monthlyBreakdown) {
                        MonthlyTable(
                            data = monthlyData,
                            monthCol = strings.monthColumn,
                            countCol = strings.countColumn,
                            hoursCol = strings.hoursColumn
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = strings.futureDataExplanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }

                // ── Type breakdown ───────────────────────────────────────────
                if (typeData.isNotEmpty()) {
                    StatsCard(title = strings.typeBreakdown) {
                        TypeBreakdownSection(data = typeData)
                    }
                }

                // ── Trainer breakdown ────────────────────────────────────────
                if (trainerData.isNotEmpty()) {
                    StatsCard(title = strings.trainerBreakdown) {
                        TrainerBreakdownSection(
                            data = trainerData,
                            sessionsUnit = strings.sessionsUnit,
                            hoursUnit = strings.hoursUnit,
                            otherLabel = strings.otherTrainers
                        )
                    }
                }

                // ── Score card ───────────────────────────────────────────────
                scoreEntry?.let { score ->
                    ScoreCard(score = score, strings = strings, onOpenLeaderboard = onOpenLeaderboard)
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ─── Season selector ──────────────────────────────────────────────────────────

@Composable
private fun SeasonSelector(
    selected: SeasonSelection,
    seasons: List<SeasonSelection>,
    currentLabel: String,
    lastLabel: String,
    onSelect: (SeasonSelection) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    ) {
        seasons.forEachIndexed { idx, season ->
            val label = when (idx) {
                0 -> currentLabel
                1 -> lastLabel
                else -> season.label
            }
            FilterChip(
                selected = selected == season,
                onClick = { if (selected != season) onSelect(season) },
                label = { Text(label) }
            )
        }
    }
}

// ─── Summary row (3 cards) ────────────────────────────────────────────────────

@Composable
private fun SummaryRow(
    totalSessions: Int,
    totalMinutes: Long,
    avgPerWeek: Double,
    streak: Int,
    strings: com.tkolymp.shared.language.StatsStrings
) {
    val totalHoursFmt = (totalMinutes / 60.0).roundTo1dp()
    val avgFmt = avgPerWeek.roundTo1dp()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryCard(
            modifier = Modifier.weight(1f),
            value = totalSessions.toString(),
            label = strings.totalSessions
        )
        SummaryCard(
            modifier = Modifier.weight(1f),
            value = "$totalHoursFmt ${strings.hoursUnit}",
            label = strings.totalHours
        )
        SummaryCard(
            modifier = Modifier.weight(1f),
            value = if (streak > 0) "$streak ${strings.weeksInARow}" else avgFmt.toString(),
            label = if (streak > 0) strings.currentStreak else strings.avgPerWeek
        )
    }
}

@Composable
private fun SummaryCard(modifier: Modifier = Modifier, value: String, label: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ─── Section card wrapper ─────────────────────────────────────────────────────

@Composable
private fun StatsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

// ─── Monthly table ────────────────────────────────────────────────────────────

@Composable
private fun MonthlyTable(
    data: List<MonthStats>,
    monthCol: String,
    countCol: String,
    hoursCol: String
) {
    // Header
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(monthCol, modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(countCol, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(hoursCol, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    data.forEach { month ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Try to show localized month name (from `yearMonth` "yyyy-MM").
            val displayMonth = run {
                val parts = month.yearMonth.split("-")
                val year = parts.getOrNull(0)
                val monthNum = parts.getOrNull(1)?.toIntOrNull()
                if (monthNum != null && year != null) {
                    val monthName = getLocalizedMonthNameNominative(monthNum, AppStrings.currentLanguage.code)
                    "$monthName $year"
                } else month.monthLabel
            }
            Text(displayMonth, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
            Text(
                month.count.toString(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End
            )
            Text(
                (month.minutes / 60.0).roundTo1dp().toString(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End
            )
        }
    }

    // Totals row
    val totalCount = data.sumOf { it.count }
    val totalMinutes = data.sumOf { it.minutes }
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(AppStrings.current.stats.sumLabel, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Text(totalCount.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        Text((totalMinutes / 60.0).roundTo1dp().toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

// ─── Type breakdown (horizontal bars) ────────────────────────────────────────

@Composable
private fun TypeBreakdownSection(data: List<TypeStat>) {
    val maxCount = data.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val isDark = isSystemInDarkTheme()

    data.forEach { item ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.displayName.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(90.dp),
                maxLines = 1
            )
            Spacer(Modifier.width(8.dp))
            // Bar
            val fraction = item.count.toFloat() / maxCount.toFloat()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.7f else 0.85f))
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.count.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

// ─── Trainer breakdown ────────────────────────────────────────────────────────

@Composable
private fun TrainerBreakdownSection(
    data: List<TrainerStat>,
    sessionsUnit: String,
    hoursUnit: String,
    otherLabel: String
) {
    val maxCount = data.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val isDark = isSystemInDarkTheme()
    val primaryColors = listOf(
        Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFF57C00),
        Color(0xFF7B1FA2), Color(0xFFD32F2F)
    )

    data.forEachIndexed { idx, item ->
        val displayName = if (item.name.startsWith("(")) otherLabel else stripTitles(item.name)
        val barColor = primaryColors.getOrNull(idx) ?: MaterialTheme.colorScheme.primary

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(90.dp),
                maxLines = 1
            )
            Spacer(Modifier.width(8.dp))
            val fraction = item.count.toFloat() / maxCount.toFloat()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(barColor.copy(alpha = if (isDark) 0.7f else 0.85f))
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.width(46.dp), horizontalAlignment = Alignment.End) {
                Text(text = "$sessionsUnit: ${item.count}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Text(
                    text = "${(item.minutes / 60.0).roundTo1dp()} $hoursUnit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

// ─── Compare mode screen content ─────────────────────────────────────────────

@Composable
private fun CompareScreenContent(
    viewModel: StatsViewModel,
    strings: com.tkolymp.shared.language.StatsStrings,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val slotLetters = listOf("A", "B", "C", "D", "E")

    if (state.isLoadingComparison && state.comparisonData.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.comparisonData.isEmpty()) return@Column

        // ── Season picker chips (bare, no card) ───────────────────────────────
        CompareSeasonPicker(
            seasons = state.comparisonData.map { it.season },
            selectedSlots = state.compareSeasons,
            currentLabel = strings.currentSeason,
            lastLabel = strings.lastSeason,
            onToggle = { season ->
                val slotIdx = state.compareSeasons.indexOf(season)
                if (slotIdx >= 0) {
                    viewModel.clearCompare(slotIdx)
                } else {
                    val emptySlot = state.compareSeasons.indexOfFirst { it == null }
                    if (emptySlot >= 0) scope.launch { viewModel.loadSeasonDetail(season, emptySlot) }
                }
            }
        )

        // ── Bar chart overview (sessions per season, labeled A/B/etc if selected) ──
        StatsCard(title = strings.totalSessions) {
            val barData = state.comparisonData.map { summary ->
                val slotIdx = state.compareSeasons.indexOf(summary.season)
                val label = if (slotIdx >= 0) slotLetters[slotIdx] else summary.season.label
                Pair(label, summary.totalSessions)
            }
            val highlightIdx = state.comparisonData.indexOfFirst { it.season == SeasonSelection.current() }
            BarChart(
                data = barData,
                modifier = Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp),
                highlightIndex = highlightIdx,
                barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                highlightColor = MaterialTheme.colorScheme.primary,
                dimColor = MaterialTheme.colorScheme.primaryContainer,
                labelEvery = 1,
                showLabels = true
            )
        }

        // ── Side-by-side detail ───────────────────────────────────────────────
        if (state.isLoadingCompare.any { it }) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Collect active slots (season set + data loaded), preserving order A→E
            val activeSlots = state.compareSeasons.indices
                .filter { state.compareSeasons[it] != null && state.compareData[it] != null }
            if (activeSlots.size >= 2) {
                CompareDetailContent(
                    slots = activeSlots.map { idx -> Pair(slotLetters[idx], state.compareData[idx]!!) },
                    strings = strings
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ─── Up-to-5-slot season picker (A–E) ────────────────────────────────────────

@Composable
private fun CompareSeasonPicker(
    seasons: List<SeasonSelection>,
    selectedSlots: List<SeasonSelection?>,   // size 5, index = slot (A=0…E=4)
    currentLabel: String,
    lastLabel: String,
    onToggle: (SeasonSelection) -> Unit
) {
    val slotLetters = listOf("A", "B", "C", "D", "E")
    // Material3 container/on-container colors for each slot
    val containerColors = @Composable {
        listOf(
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    val colors = containerColors()

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        seasons.forEachIndexed { idx, season ->
            val slotIdx = selectedSlots.indexOf(season)  // -1 if not selected
            val isSelected = slotIdx >= 0
            val baseLabel = when (idx) {
                0 -> currentLabel
                1 -> lastLabel
                else -> season.label
            }
            val chipLabel = if (isSelected) "${slotLetters[slotIdx]} · $baseLabel" else baseLabel
            val (container, onContainer) = if (isSelected) colors[slotIdx] else (
                MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
            )
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(season) },
                label = { Text(chipLabel) },
                colors = if (isSelected) FilterChipDefaults.filterChipColors(
                    selectedContainerColor = container,
                    selectedLabelColor = onContainer
                ) else FilterChipDefaults.filterChipColors()
            )
        }
    }
}

// ─── Side-by-side detail card ─────────────────────────────────────────────────

/**
 * [slots] — ordered list of (letter, data) for each active slot, e.g. [("A", …), ("B", …), ("C", …)].
 * Labels in bars are just the short letter ("A", "B"…) for compactness.
 */
@Composable
private fun CompareDetailContent(
    slots: List<Pair<String, SeasonDetailStats>>,
    strings: com.tkolymp.shared.language.StatsStrings
) {
    if (slots.isEmpty()) return
    val isDark = isSystemInDarkTheme()

    // ── Summary stat cards ────────────────────────────────────────────────────
    StatsCard(title = strings.seasonComparison) {
        Spacer(Modifier.height(4.dp))
        // Season header chips
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            slots.forEachIndexed { i, (letter, data) ->
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(slotContainerColor(i)).padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$letter · ${data.season.label}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = slotOnContainerColor(i),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Summary metrics as multi-bars
        CompareMultiBars(
            label = strings.totalSessions,
            values = slots.map { (_, d) -> d.totalSessions.toDouble() },
            formatValue = { it.toInt().toString() },
            letters = slots.map { it.first },
            isDark = isDark
        )
        CompareMultiBars(
            label = strings.totalHours,
            values = slots.map { (_, d) -> d.totalMinutes.toDouble() },
            formatValue = { "${(it / 60.0).roundTo1dp()} ${strings.hoursUnit}" },
            letters = slots.map { it.first },
            isDark = isDark
        )
        CompareMultiBars(
            label = strings.avgPerWeek,
            values = slots.map { (_, d) -> d.avgSessionsPerWeek },
            formatValue = { "${it.roundTo1dp()} ${strings.sessionsUnit}" },
            letters = slots.map { it.first },
            isDark = isDark
        )
    }

    // ── Monthly breakdown ─────────────────────────────────────────────────────
    val hasMonthly = slots.any { (_, d) -> d.monthlyData.isNotEmpty() }
    if (hasMonthly) {
        val monthOrder = listOf(9, 10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8)
        val monthMaps = slots.map { (_, d) -> d.monthlyData.associateBy { it.yearMonth.takeLast(2).toIntOrNull() ?: 0 } }
        val allMonths = monthMaps.flatMap { it.keys }.toSortedSet(
            compareBy { monthOrder.indexOf(it).let { i -> if (i < 0) 99 else i } }
        )
        val maxCount = allMonths.maxOf { m -> monthMaps.maxOf { map -> map[m]?.count ?: 0 } }.coerceAtLeast(1)

        StatsCard(title = strings.monthlyBreakdown) {
            allMonths.forEach { monthNum ->
                val counts = monthMaps.map { it[monthNum]?.count ?: 0 }
                if (counts.all { it == 0 }) return@forEach
                val monthName = com.tkolymp.shared.utils.getLocalizedMonthNameNominative(monthNum, AppStrings.currentLanguage.code)
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(monthName, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    CompareMultiBars(
                        values = counts.map { it.toDouble() },
                        maxValue = maxCount.toDouble(),
                        letters = slots.map { it.first },
                        isDark = isDark
                    )
                }
            }
        }
    }

    // ── Type breakdown ────────────────────────────────────────────────────────
    val allTypes = slots.flatMap { (_, d) -> d.typeData.map { it.type } }.distinct()
    if (allTypes.isNotEmpty()) {
        val typeMaps = slots.map { (_, d) -> d.typeData.associateBy { it.type } }
        val maxType = allTypes.maxOf { t -> typeMaps.maxOf { m -> m[t]?.count ?: 0 } }.coerceAtLeast(1)
        StatsCard(title = strings.typeBreakdown) {
            allTypes.sortedByDescending { t -> typeMaps.sumOf { m -> m[t]?.count ?: 0 } }.forEach { type ->
                val entries = typeMaps.map { it[type] }
                val displayName = entries.firstNotNullOfOrNull { it }!!.displayName.replaceFirstChar { it.uppercase() }
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(displayName, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    CompareMultiBars(
                        values = entries.map { (it?.count ?: 0).toDouble() },
                        maxValue = maxType.toDouble(),
                        letters = slots.map { it.first },
                        isDark = isDark
                    )
                }
            }
        }
    }

    // ── Trainer breakdown ─────────────────────────────────────────────────────
    val allTrainers = slots.flatMap { (_, d) -> d.trainerData.map { it.name } }.distinct()
    if (allTrainers.isNotEmpty()) {
        val trainerMaps = slots.map { (_, d) -> d.trainerData.associateBy { it.name } }
        val maxTrainer = allTrainers.maxOf { n -> trainerMaps.maxOf { m -> m[n]?.count ?: 0 } }.coerceAtLeast(1)
        StatsCard(title = strings.trainerBreakdown) {
            allTrainers.sortedByDescending { n -> trainerMaps.sumOf { m -> m[n]?.count ?: 0 } }.forEach { name ->
                val entries = trainerMaps.map { it[name] }
                val displayName = if (name.startsWith("(")) strings.otherTrainers else stripTitles(name)
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(displayName, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    CompareMultiBars(
                        values = entries.map { (it?.count ?: 0).toDouble() },
                        maxValue = maxTrainer.toDouble(),
                        letters = slots.map { it.first },
                        isDark = isDark
                    )
                }
            }
        }
    }
}

// ─── Slot color helpers ───────────────────────────────────────────────────────

@Composable
private fun slotColor(slotIndex: Int): Color = when (slotIndex) {
    0 -> MaterialTheme.colorScheme.primary
    1 -> MaterialTheme.colorScheme.secondary
    2 -> MaterialTheme.colorScheme.tertiary
    3 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.outline
}

@Composable
private fun slotContainerColor(slotIndex: Int): Color = when (slotIndex) {
    0 -> MaterialTheme.colorScheme.primaryContainer
    1 -> MaterialTheme.colorScheme.secondaryContainer
    2 -> MaterialTheme.colorScheme.tertiaryContainer
    3 -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun slotOnContainerColor(slotIndex: Int): Color = when (slotIndex) {
    0 -> MaterialTheme.colorScheme.onPrimaryContainer
    1 -> MaterialTheme.colorScheme.onSecondaryContainer
    2 -> MaterialTheme.colorScheme.onTertiaryContainer
    3 -> MaterialTheme.colorScheme.onErrorContainer
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

// ─── N-season horizontal bars ─────────────────────────────────────────────────

/**
 * Stacked horizontal bars for up to 5 seasons, labeled with short slot letters.
 * [label] is shown above the bars if non-null. [maxValue] defaults to the max of [values].
 * [formatValue] formats the count shown at the right; defaults to integer formatting.
 */
@Composable
private fun CompareMultiBars(
    values: List<Double>,
    letters: List<String>,
    isDark: Boolean,
    label: String? = null,
    maxValue: Double = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0,
    formatValue: ((Double) -> String)? = null
) {
    val alphaBar = if (isDark) 0.7f else 0.85f
    val maxIdx = values.indices.maxByOrNull { values[it] } ?: -1

    if (label != null) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 3.dp))
    }
    values.indices.forEach { i ->
        val v = values[i]
        val fraction = (v / maxValue).toFloat().coerceIn(0f, 1f)
        val color = slotColor(i)
        val isWinner = i == maxIdx && values.count { it == v } == 1
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
            Text(
                text = letters.getOrElse(i) { "$i" },
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.width(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier.weight(1f).height(13.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (v > 0) Box(
                    modifier = Modifier.fillMaxWidth(fraction).height(13.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color.copy(alpha = alphaBar))
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = formatValue?.invoke(v) ?: v.toInt().toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                color = if (isWinner) color else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun ScoreCard(
    score: ScoreboardEntry,
    strings: com.tkolymp.shared.language.StatsStrings,
    onOpenLeaderboard: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    strings.myScore,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                TextButton(onClick = onOpenLeaderboard) {
                    Text(AppStrings.current.otherScreen.leaderboard, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            val totalText = score.totalScore?.let {
                if (it % 1.0 == 0.0) it.toInt().toString() else String.format("%.1f", it)
            } ?: "-"

            Text(
                text = totalText,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )

            if (score.ranking != null) {
                Text(
                    text = "#${score.ranking}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(8.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f))
            Spacer(Modifier.height(8.dp))

            // Sub-scores
            listOfNotNull(
                score.lessonTotalScore?.let { strings.lessonScore to it },
                score.groupTotalScore?.let { strings.groupScore to it },
                score.eventTotalScore?.let { strings.eventScore to it },
                score.manualTotalScore?.let { strings.manualScore to it }
            ).forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                    val fmt = if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.1f", value)
                    Text(fmt, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}


