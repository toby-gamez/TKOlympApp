package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.tkolymp.shared.viewmodels.MonthStats
import com.tkolymp.shared.viewmodels.SeasonSelection
import com.tkolymp.shared.viewmodels.StatsViewModel
import com.tkolymp.shared.viewmodels.TrainerStat
import com.tkolymp.shared.viewmodels.TypeStat
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.BarChart
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit = {},
    bottomPadding: Dp = 0.dp,
    onOpenLeaderboard: () -> Unit = {},
    viewModel: StatsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val strings = AppStrings.current.stats

    LaunchedEffect(Unit) {
        viewModel.loadStats(SeasonSelection.default())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.statsTitle) },
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
            onRefresh = { scope.launch { viewModel.loadStats(state.selectedSeason, forceRefresh = true) } },
            modifier = Modifier.padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
        ) {
            if (state.isLoading && state.totalSessions == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@SwipeToReload
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Season selector ──────────────────────────────────────────
                SeasonSelector(
                    selected = state.selectedSeason,
                    seasons = SeasonSelection.recent(),
                    currentLabel = strings.currentSeason,
                    lastLabel = strings.lastSeason,
                    onSelect = { season ->
                        scope.launch { viewModel.loadStats(season) }
                    }
                )

                if (state.totalSessions == 0 && !state.isLoading) {
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
                    totalSessions = state.totalSessions,
                    totalMinutes = state.totalMinutes,
                    avgPerWeek = state.avgSessionsPerWeek,
                    streak = state.currentStreak,
                    strings = strings
                )

                // ── Bar chart: weekly activity ───────────────────────────────
                if (state.weeklyData.isNotEmpty()) {
                    StatsCard(title = strings.weeklyActivity) {
                        val barData = state.weeklyData.map { Pair(it.weekLabel, it.count) }
                        val highlightIndex = state.weeklyData.indexOfFirst { it.isCurrent }
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
                            val week = state.weeklyData.getOrNull(selectedBar)
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
                if (state.monthlyData.isNotEmpty()) {
                    StatsCard(title = strings.monthlyBreakdown) {
                        MonthlyTable(
                            data = state.monthlyData,
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
                if (state.typeData.isNotEmpty()) {
                    StatsCard(title = strings.typeBreakdown) {
                        TypeBreakdownSection(data = state.typeData)
                    }
                }

                // ── Trainer breakdown ────────────────────────────────────────
                if (state.trainerData.isNotEmpty()) {
                    StatsCard(title = strings.trainerBreakdown) {
                        TrainerBreakdownSection(
                            data = state.trainerData,
                            sessionsUnit = strings.sessionsUnit,
                            hoursUnit = strings.hoursUnit,
                            otherLabel = strings.otherTrainers
                        )
                    }
                }

                // ── Score card ───────────────────────────────────────────────
                state.scoreEntry?.let { score ->
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

// ─── Score card ───────────────────────────────────────────────────────────────

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

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun stripTitles(name: String): String {
    val cleaned = name.trim()
    if (cleaned.isEmpty() || cleaned.startsWith("(")) return cleaned

    val titleTokens = setOf(
        "Bc", "Bc.", "Mgr", "Mgr.", "Ing", "Ing.", "PhDr", "PhDr.", "RNDr", "RNDr.",
        "JUDr", "JUDr.", "PhD", "PhD.", "Dr", "Dr.", "Prof", "Prof.", "doc", "doc.",
        "MBA", "MVDr", "MVDr.", "MD", "MD."
    )

    val parts = cleaned.split(Regex("\\s+"))
    var start = 0
    var end = parts.size

    while (start < end && titleTokens.contains(parts[start].trimEnd(','))) start++
    while (end - 1 >= start && titleTokens.contains(parts[end - 1].trimEnd(','))) end--

    val core = parts.subList(start, end).joinToString(" ")
    return if (core.isBlank()) cleaned else core
}

private fun Double.roundTo1dp(): Double = (this * 10.0).roundToInt() / 10.0
