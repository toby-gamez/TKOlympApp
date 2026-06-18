package com.tkolymp.tkolympapp.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.people.ScoreboardEntry
import com.tkolymp.shared.utils.formatTimesWithDateAlways
import com.tkolymp.shared.utils.getLocalizedMonthNameNominative
import com.tkolymp.shared.utils.roundTo1dp
import com.tkolymp.shared.utils.stripTitles
import com.tkolymp.shared.utils.translateEventType
import com.tkolymp.shared.viewmodels.AttendanceMonth
import com.tkolymp.shared.viewmodels.MonthStats
import com.tkolymp.shared.viewmodels.SeasonDetailStats
import com.tkolymp.shared.viewmodels.SeasonSelection
import com.tkolymp.shared.viewmodels.SessionItem
import com.tkolymp.shared.viewmodels.TrainerStat
import com.tkolymp.shared.viewmodels.TypeStat
import com.tkolymp.tkolympapp.components.BarChart
import com.tkolymp.tkolympapp.util.StaggeredItem
import kotlinx.coroutines.launch

// ─── Season selector ──────────────────────────────────────────────────────────

@Composable
internal fun SeasonSelector(
    modifier: Modifier = Modifier,
    selected: SeasonSelection,
    seasons: List<SeasonSelection>,
    currentLabel: String,
    lastLabel: String,
    onSelect: (SeasonSelection) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
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
internal fun SummaryRow(
    modifier: Modifier = Modifier,
    totalSessions: Int,
    totalMinutes: Long,
    avgPerWeek: Double,
    strings: com.tkolymp.shared.language.StatsStrings
) {
    val totalHoursFmt = (totalMinutes / 60.0).roundTo1dp()
    val avgFmt = avgPerWeek.roundTo1dp()

    Row(
        modifier = modifier.fillMaxWidth(),
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
            value = avgFmt.toString(),
            label = strings.avgPerWeek
        )
    }
}

// ─── Weekly goal section ──────────────────────────────────────────────────────

@Composable
internal fun WeeklyGoalSection(
    modifier: Modifier = Modifier,
    weeklyGoal: Int,
    currentWeekCount: Int,
    strings: com.tkolymp.shared.language.StatsStrings,
    onSetGoal: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var pendingGoal by remember(weeklyGoal) { mutableStateOf(if (weeklyGoal > 0) weeklyGoal else 3) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(strings.weeklyGoalTitle) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(strings.sessionsPerWeek, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(
                            onClick = { if (pendingGoal > 1) pendingGoal-- },
                            enabled = pendingGoal > 1
                        ) { Text("−") }
                        Text(
                            text = pendingGoal.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = { if (pendingGoal < 14) pendingGoal++ },
                            enabled = pendingGoal < 14
                        ) { Text("+") }
                    }
                    if (weeklyGoal > 0) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { onSetGoal(0); showDialog = false }) {
                            Text("✕ ${strings.weeklyGoalTitle}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onSetGoal(pendingGoal); showDialog = false }) {
                    Text(AppStrings.current.commonActions.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(AppStrings.current.commonActions.cancel)
                }
            }
        )
    }

    if (weeklyGoal <= 0) {
        OutlinedButton(
            onClick = { showDialog = true },
            modifier = modifier.fillMaxWidth()
        ) {
            Text(strings.setWeeklyGoal)
        }
        return
    }

    val progress = (currentWeekCount.toFloat() / weeklyGoal.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "goalProgress"
    )
    val goalMet = currentWeekCount >= weeklyGoal
    val progressColor = if (goalMet) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (goalMet) Color(0xFF4CAF50).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.weeklyGoalTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "$currentWeekCount / $weeklyGoal ${strings.sessionsPerWeek}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (goalMet) {
                        Text(strings.goalReached, style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = strings.weeklyGoalTitle, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(progressColor)
                )
            }
        }
    }
}

// ─── Streak section (with milestone badges) ──────────────────────────────────

internal data class StreakMilestone(
    val minStreak: Int,
    val emoji: String,
    val label: String,
    val badgeColor: Color,
    val glowColor: Color
)

internal val streakMilestones = listOf(
    StreakMilestone(0, "🔥", "", Color.Unspecified, Color.Unspecified),
    StreakMilestone(5, "🔥", "On Fire!", Color(0xFFFF6D00), Color(0xFFFF9800)),
    StreakMilestone(10, "⚡", "Unstoppable!", Color(0xFFAB47BC), Color(0xFFCE93D8)),
    StreakMilestone(15, "💎", "Legendary!", Color(0xFF42A5F5), Color(0xFF90CAF9)),
    StreakMilestone(20, "🏆", "Champion!", Color(0xFFFFCA28), Color(0xFFFFE082)),
    StreakMilestone(25, "👑", "Titan!", Color(0xFF7C4DFF), Color(0xFFB388FF)),
)

internal fun getMilestone(streak: Int): StreakMilestone =
    streakMilestones.lastOrNull { it.minStreak <= streak } ?: streakMilestones.first()

internal fun nextMilestoneTarget(streak: Int): Int {
    val next = streakMilestones.firstOrNull { it.minStreak > streak }
    return next?.minStreak ?: streakMilestones.last().minStreak
}

internal fun milestoneProgress(streak: Int): Float {
    if (streak <= 0) return 0f
    val current = getMilestone(streak)
    val nextTarget = nextMilestoneTarget(streak)
    if (nextTarget <= current.minStreak) return 1f
    return ((streak - current.minStreak).toFloat() / (nextTarget - current.minStreak).toFloat()).coerceIn(0f, 1f)
}

@Composable
internal fun StreakSection(
    modifier: Modifier = Modifier,
    streak: Int,
    longestStreak: Int,
    strings: com.tkolymp.shared.language.StatsStrings
) {
    val milestone = remember(streak) { getMilestone(streak) }
    val nextTarget = remember(streak) { nextMilestoneTarget(streak) }
    val progress = remember(streak) { milestoneProgress(streak) }

    val cardColor = if (streak >= 5) milestone.badgeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = milestone.emoji,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center
            )

            Text(
                text = streak.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )

            Text(
                text = strings.weekStreak,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            if (streak >= 5) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(milestone.badgeColor)
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${milestone.emoji} ${milestone.label}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            if (streak > 0 && nextTarget > getMilestone(streak).minStreak) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "+${nextTarget - streak}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(milestone.glowColor.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.primary)
                        )
                    }
                    Text(
                        text = nextTarget.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (longestStreak > streak) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${strings.bestStreak}: $longestStreak ${strings.weeksInARow}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.45f)
                )
            }
        }
    }
}

// ─── Summary card ─────────────────────────────────────────────────────────────

@Composable
internal fun SummaryCard(modifier: Modifier = Modifier, value: String, label: String) {
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
internal fun StatsCard(modifier: Modifier = Modifier.fillMaxWidth(), title: String, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
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
internal fun MonthlyTable(
    modifier: Modifier = Modifier,
    data: List<MonthStats>,
    monthCol: String,
    countCol: String,
    hoursCol: String
) {
    Row(modifier = modifier.fillMaxWidth()) {
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
internal fun TypeBreakdownSection(modifier: Modifier = Modifier, data: List<TypeStat>) {
    val maxCount = data.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val isDark = isSystemInDarkTheme()
    var triggered by remember(data) { mutableStateOf(false) }
    LaunchedEffect(data) { triggered = true }

    data.forEach { item ->
        TypeBarRow(item = item, maxCount = maxCount, isDark = isDark, triggered = triggered, modifier = modifier)
    }
}

@Composable
internal fun TypeBarRow(item: TypeStat, maxCount: Int, isDark: Boolean, triggered: Boolean, modifier: Modifier = Modifier) {
    val fraction = item.count.toFloat() / maxCount.toFloat()
    val animFraction by animateFloatAsState(
        targetValue = if (triggered) fraction else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "typeBarWidth"
    )
    Row(
        modifier = modifier
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
        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animFraction)
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

// ─── Trainer breakdown ────────────────────────────────────────────────────────

@Composable
internal fun TrainerBreakdownSection(
    modifier: Modifier = Modifier,
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
    var triggered by remember(data) { mutableStateOf(false) }
    LaunchedEffect(data) { triggered = true }

    data.forEachIndexed { idx, item ->
        val displayName = if (item.name.startsWith("(")) otherLabel else stripTitles(item.name)
        val barColor = primaryColors.getOrNull(idx) ?: MaterialTheme.colorScheme.primary
        TrainerBarRow(
            item = item,
            maxCount = maxCount,
            isDark = isDark,
            barColor = barColor,
            displayName = displayName,
            triggered = triggered,
            sessionsUnit = sessionsUnit,
            hoursUnit = hoursUnit,
            modifier = modifier
        )
    }
}

@Composable
internal fun TrainerBarRow(
    item: TrainerStat,
    maxCount: Int,
    isDark: Boolean,
    barColor: Color,
    displayName: String,
    triggered: Boolean,
    sessionsUnit: String,
    hoursUnit: String,
    modifier: Modifier = Modifier
) {
    val fraction = item.count.toFloat() / maxCount.toFloat()
    val animFraction by animateFloatAsState(
        targetValue = if (triggered) fraction else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "trainerBarWidth"
    )
    Row(
        modifier = modifier
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
        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animFraction)
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

// ─── Compare mode screen content ─────────────────────────────────────────────

@Composable
internal fun CompareScreenContent(
    viewModel: com.tkolymp.shared.viewmodels.StatsViewModel,
    strings: com.tkolymp.shared.language.StatsStrings,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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

        if (state.isLoadingCompare.any { it }) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val activeSlots = state.compareSeasons.indices
                .filter { state.compareSeasons[it] != null && state.compareData[it] != null }
            if (activeSlots.size >= 2) {
                val slotPairs = activeSlots.mapNotNull { idx -> state.compareData[idx]?.let { Pair(slotLetters[idx], it) } }
                if (slotPairs.size >= 2) {
                    CompareDetailContent(
                        slots = slotPairs,
                        strings = strings
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ─── Up-to-5-slot season picker (A–E) ────────────────────────────────────────

@Composable
internal fun CompareSeasonPicker(
    modifier: Modifier = Modifier,
    seasons: List<SeasonSelection>,
    selectedSlots: List<SeasonSelection?>,
    currentLabel: String,
    lastLabel: String,
    onToggle: (SeasonSelection) -> Unit
) {
    val slotLetters = listOf("A", "B", "C", "D", "E")
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
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        seasons.forEachIndexed { idx, season ->
            val slotIdx = selectedSlots.indexOf(season)
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

@Composable
internal fun CompareDetailContent(
    modifier: Modifier = Modifier,
    slots: List<Pair<String, SeasonDetailStats>>,
    strings: com.tkolymp.shared.language.StatsStrings
) {
    if (slots.isEmpty()) return
    val isDark = isSystemInDarkTheme()

    StatsCard(modifier = modifier, title = strings.seasonComparison) {
        Spacer(Modifier.height(4.dp))
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

    val hasMonthly = slots.any { (_, d) -> d.monthlyData.isNotEmpty() }
    if (hasMonthly) {
        val monthOrder = listOf(9, 10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8)
        val monthMaps = slots.map { (_, d) -> d.monthlyData.associateBy { it.yearMonth.takeLast(2).toIntOrNull() ?: 0 } }
        val allMonths = monthMaps.flatMap { it.keys }.distinct()
            .sortedBy { monthOrder.indexOf(it).let { i -> if (i < 0) 99 else i } }
        val maxCount = allMonths.maxOf { m -> monthMaps.maxOf { map -> map[m]?.count ?: 0 } }.coerceAtLeast(1)

        StatsCard(title = strings.monthlyBreakdown) {
            allMonths.forEach { monthNum ->
                val counts = monthMaps.map { it[monthNum]?.count ?: 0 }
                if (counts.all { it == 0 }) return@forEach
                val monthName = getLocalizedMonthNameNominative(monthNum, AppStrings.currentLanguage.code)
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

    val allTypes = slots.flatMap { (_, d) -> d.typeData.map { it.type } }.distinct()
    if (allTypes.isNotEmpty()) {
        val typeMaps = slots.map { (_, d) -> d.typeData.associateBy { it.type } }
        val maxType = allTypes.maxOf { t -> typeMaps.maxOf { m -> m[t]?.count ?: 0 } }.coerceAtLeast(1)
        StatsCard(title = strings.typeBreakdown) {
            allTypes.sortedByDescending { t -> typeMaps.sumOf { m -> m[t]?.count ?: 0 } }.forEach { type ->
                val entries = typeMaps.map { it[type] }
                val displayName = entries.firstNotNullOfOrNull { it }?.displayName?.replaceFirstChar { it.uppercase() } ?: ""
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
internal fun slotColor(slotIndex: Int): Color = when (slotIndex) {
    0 -> MaterialTheme.colorScheme.primary
    1 -> MaterialTheme.colorScheme.secondary
    2 -> MaterialTheme.colorScheme.tertiary
    3 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.outline
}

@Composable
internal fun slotContainerColor(slotIndex: Int): Color = when (slotIndex) {
    0 -> MaterialTheme.colorScheme.primaryContainer
    1 -> MaterialTheme.colorScheme.secondaryContainer
    2 -> MaterialTheme.colorScheme.tertiaryContainer
    3 -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
internal fun slotOnContainerColor(slotIndex: Int): Color = when (slotIndex) {
    0 -> MaterialTheme.colorScheme.onPrimaryContainer
    1 -> MaterialTheme.colorScheme.onSecondaryContainer
    2 -> MaterialTheme.colorScheme.onTertiaryContainer
    3 -> MaterialTheme.colorScheme.onErrorContainer
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

// ─── N-season horizontal bars ─────────────────────────────────────────────────

@Composable
internal fun CompareMultiBars(
    modifier: Modifier = Modifier,
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
            modifier = modifier.padding(bottom = 3.dp))
    }
    values.indices.forEach { i ->
        val v = values[i]
        val fraction = (v / maxValue).toFloat().coerceIn(0f, 1f)
        val color = slotColor(i)
        val isWinner = i == maxIdx && values.count { it == v } == 1
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.padding(vertical = 1.dp)) {
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

// ─── Score card ───────────────────────────────────────────────────────────────

@Composable
internal fun ScoreCard(
    modifier: Modifier = Modifier,
    score: ScoreboardEntry,
    strings: com.tkolymp.shared.language.StatsStrings,
    onOpenLeaderboard: () -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                if (it % 1.0 == 0.0) it.toInt().toString() else { val s = kotlin.math.round(it * 10).toLong(); "${s / 10}.${kotlin.math.abs(s % 10)}" }
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
                    val fmt = if (value % 1.0 == 0.0) value.toInt().toString() else { val s = kotlin.math.round(value * 10).toLong(); "${s / 10}.${kotlin.math.abs(s % 10)}" }
                    Text(fmt, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

// ─── Attendance tab ────────────────────────────────────────────────────────────

@Composable
internal fun AttendanceTabContent(
    modifier: Modifier = Modifier,
    attendanceMonths: List<AttendanceMonth>,
    cancelledCount: Int,
    totalSessions: Int,
    isLoading: Boolean,
    selectedSeason: SeasonSelection,
    seasons: List<SeasonSelection>,
    strings: com.tkolymp.shared.language.StatsStrings,
    onSeasonSelect: (SeasonSelection) -> Unit
) {
    if (isLoading && totalSessions == 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        SeasonSelector(
            selected = selectedSeason,
            seasons = seasons,
            currentLabel = strings.currentSeason,
            lastLabel = strings.lastSeason,
            onSelect = onSeasonSelect
        )

        if (attendanceMonths.isEmpty() && !isLoading) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Text(
                    text = strings.noAttendanceData,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(16.dp))
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AttendanceSummaryItem(
                    value = totalSessions.toString(),
                    label = strings.sessionsUnit
                )
                AttendanceSummaryItem(
                    value = cancelledCount.toString(),
                    label = strings.cancelled
                )
            }
        }

        Text(
            text = strings.sessionsList,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp)
        )

        attendanceMonths.forEach { month ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "${month.monthLabel} (${month.sessions.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    month.sessions.forEachIndexed { idx, session ->
                        if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        AttendanceSessionRow(
                            session = session,
                            cancelledLabel = strings.cancelled,
                            attendedLabel = strings.attendanceAttended,
                            notExcusedLabel = strings.attendanceNotExcused,
                            unknownLabel = strings.attendanceUnknown
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun AttendanceSummaryItem(modifier: Modifier = Modifier, value: String, label: String) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun AttendanceSessionRow(
    modifier: Modifier = Modifier,
    session: SessionItem,
    cancelledLabel: String,
    attendedLabel: String = "Attended",
    notExcusedLabel: String = "Unexcused",
    unknownLabel: String = "Unknown"
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val timeText = remember(session.since, session.until) { formatTimesWithDateAlways(session.since, session.until) }
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val trainerName = session.trainerName
            val name = when {
                session.eventType?.equals("lesson", ignoreCase = true) == true && !trainerName.isNullOrBlank() ->
                    "${translateEventType(session.eventType)}: ${stripTitles(trainerName)}"
                session.eventName.isNotBlank() -> session.eventName
                else -> translateEventType(session.eventType) ?: session.eventType ?: ""
            }
            if (name.isNotBlank()) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (session.isCancelled) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = cancelledLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        } else {
            when (session.attendanceStatus) {
                "ATTENDED" -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = attendedLabel,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(20.dp)
                )
                "NOT_EXCUSED" -> Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = notExcusedLabel,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.width(20.dp)
                )
                "UNKNOWN" -> Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = unknownLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(20.dp)
                )
                else -> if (session.durationMinutes > 0) {
                    Text(
                        text = "${session.durationMinutes} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
