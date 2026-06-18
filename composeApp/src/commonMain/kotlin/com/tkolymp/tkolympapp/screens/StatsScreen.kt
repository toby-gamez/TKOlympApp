package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.roundTo1dp
import com.tkolymp.shared.viewmodels.SeasonSelection
import com.tkolymp.shared.viewmodels.StatsViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.BarChart
import com.tkolymp.tkolympapp.util.StaggeredItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit = {},
    bottomPadding: Dp = 0.dp,
    onOpenLeaderboard: () -> Unit = {},
    viewModel: StatsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val totalSessions = state.totalSessions
    val totalMinutes = state.totalMinutes
    val avgPerWeek = state.avgSessionsPerWeek
    val currentStreak = state.currentStreak
    val longestStreak = state.longestStreak
    val weeklyData = state.weeklyData
    val monthlyData = state.monthlyData
    val typeData = state.typeData
    val trainerData = state.trainerData
    val scoreEntry = state.scoreEntry
    val selectedSeason = state.selectedSeason
    val comparisonData = state.comparisonData
    val isLoadingComparison = state.isLoadingComparison
    val isLoading = state.isLoading
    val attendanceMonths = state.attendanceMonths
    val cancelledCount = state.cancelledCount
    val scope = rememberCoroutineScope()
    val strings = AppStrings.current.stats
    var compareMode by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var showShareDialog by remember { mutableStateOf(false) }
    var sectionsVisible by remember { mutableStateOf(false) }
    val seasons = remember { SeasonSelection.recent() }

    LaunchedEffect(Unit) {
        viewModel.loadStats(SeasonSelection.default())
    }

    LaunchedEffect(selectedSeason) { sectionsVisible = false }
    LaunchedEffect(totalSessions) { if (totalSessions > 0) sectionsVisible = true }

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
                    if (!compareMode && totalSessions > 0) {
                        IconButton(onClick = { showShareDialog = true }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = AppStrings.current.commonActions.options
                            )
                        }
                    }
                    IconButton(onClick = { compareMode = !compareMode }) {
                        Icon(
                            Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = strings.seasonComparison,
                            tint = if (compareMode) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (showShareDialog) {
            ShareStatsDialog(
                season = selectedSeason,
                totalSessions = totalSessions,
                totalMinutes = totalMinutes,
                avgPerWeek = avgPerWeek,
                currentStreak = currentStreak,
                weeklyData = weeklyData,
                monthlyData = monthlyData,
                typeData = typeData,
                trainerData = trainerData,
                scoreEntry = scoreEntry,
                cancelledCount = cancelledCount,
                onDismiss = { showShareDialog = false }
            )
        }

        SwipeToReload(
            isRefreshing = if (compareMode) isLoadingComparison else isLoading,
            onRefresh = { scope.launch { if (compareMode) viewModel.loadComparison() else viewModel.loadStats(selectedSeason, forceRefresh = true) } },
            modifier = Modifier.padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
        ) {
            if (compareMode) {
                CompareScreenContent(viewModel = viewModel, strings = strings)
                return@SwipeToReload
            }

            Column(modifier = Modifier.fillMaxSize()) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(strings.statsTabTitle) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(strings.attendanceTabTitle) }
                    )
                }

                if (selectedTab == 1) {
                    AttendanceTabContent(
                        attendanceMonths = attendanceMonths,
                        cancelledCount = cancelledCount,
                        totalSessions = totalSessions,
                        isLoading = isLoading,
                        selectedSeason = selectedSeason,
                        seasons = seasons,
                        strings = strings,
                        onSeasonSelect = { season -> scope.launch { viewModel.loadStats(season) } }
                    )
                    return@Column
                }

                if (isLoading && totalSessions == 0) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@Column
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SeasonSelector(
                        selected = selectedSeason,
                        seasons = seasons,
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

                    StaggeredItem(index = 0, visible = sectionsVisible, baseDelayMs = 60) {
                        SummaryRow(
                            totalSessions = totalSessions,
                            totalMinutes = totalMinutes,
                            avgPerWeek = avgPerWeek,
                            strings = strings
                        )
                    }

                    if (currentStreak > 0) {
                        StaggeredItem(index = 1, visible = sectionsVisible, baseDelayMs = 60) {
                            StreakSection(
                                streak = currentStreak,
                                longestStreak = longestStreak,
                                strings = strings
                            )
                        }
                    }

                    StaggeredItem(index = 2, visible = sectionsVisible, baseDelayMs = 60) {
                        WeeklyGoalSection(
                            weeklyGoal = state.weeklyGoal,
                            currentWeekCount = state.currentWeekCount,
                            strings = strings,
                            onSetGoal = { goal -> viewModel.setWeeklyGoal(goal) }
                        )
                    }

                    if (weeklyData.isNotEmpty()) {
                        StaggeredItem(index = 3, visible = sectionsVisible, baseDelayMs = 60) {
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
                    }

                    if (monthlyData.isNotEmpty()) {
                        StaggeredItem(index = 4, visible = sectionsVisible, baseDelayMs = 60) {
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
                    }

                    if (typeData.isNotEmpty()) {
                        StaggeredItem(index = 5, visible = sectionsVisible, baseDelayMs = 60) {
                            StatsCard(title = strings.typeBreakdown) {
                                TypeBreakdownSection(data = typeData)
                            }
                        }
                    }

                    if (trainerData.isNotEmpty()) {
                        StaggeredItem(index = 6, visible = sectionsVisible, baseDelayMs = 60) {
                            StatsCard(title = strings.trainerBreakdown) {
                                TrainerBreakdownSection(
                                    data = trainerData,
                                    sessionsUnit = strings.sessionsUnit,
                                    hoursUnit = strings.hoursUnit,
                                    otherLabel = strings.otherTrainers
                                )
                            }
                        }
                    }

                    scoreEntry?.let { score ->
                        StaggeredItem(index = 7, visible = sectionsVisible, baseDelayMs = 60) {
                            ScoreCard(score = score, strings = strings, onOpenLeaderboard = onOpenLeaderboard)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
