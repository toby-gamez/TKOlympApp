
package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.people.ScoreboardEntry
import com.tkolymp.shared.utils.roundTo1dp
import com.tkolymp.shared.utils.stripTitles
import com.tkolymp.shared.viewmodels.MonthStats
import com.tkolymp.shared.viewmodels.SeasonSelection
import com.tkolymp.shared.viewmodels.TrainerStat
import com.tkolymp.shared.viewmodels.TypeStat
import com.tkolymp.shared.viewmodels.WeekStats
import com.tkolymp.tkolympapp.platform.AppLogo
import com.tkolymp.tkolympapp.platform.rememberShareStatsCallback
import kotlinx.coroutines.launch

// ─── Share sections ───────────────────────────────────────────────────────────

enum class ShareSection {
    OVERVIEW,
    TOTAL_HOURS,
    AVG_PER_WEEK,
    TOTAL_SESSIONS,
    WEEKLY_ACTIVITY,
    MONTHLY_ACTIVITY,
    TYPE_BREAKDOWN,
    TRAINER_BREAKDOWN,
    SCORE,
    ATTENDANCE,
}

// ─── Share preview dialog ─────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ShareStatsDialog(
    season: SeasonSelection,
    totalSessions: Int,
    totalMinutes: Long,
    avgPerWeek: Double,
    currentStreak: Int,
    weeklyData: List<WeekStats>,
    monthlyData: List<MonthStats> = emptyList(),
    typeData: List<TypeStat> = emptyList(),
    trainerData: List<TrainerStat> = emptyList(),
    scoreEntry: ScoreboardEntry? = null,
    cancelledCount: Int = 0,
    onDismiss: () -> Unit
) {
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val shareCallback = rememberShareStatsCallback()
    var isSharing by remember { mutableStateOf(false) }
    var selectedSections by remember {
        mutableStateOf(setOf(ShareSection.OVERVIEW, ShareSection.WEEKLY_ACTIVITY))
    }
    val strings = AppStrings.current.stats
    val availableSections = remember(weeklyData, monthlyData, typeData, trainerData, scoreEntry) {
        buildList {
            add(ShareSection.OVERVIEW)
            add(ShareSection.TOTAL_HOURS)
            add(ShareSection.AVG_PER_WEEK)
            add(ShareSection.TOTAL_SESSIONS)
            if (weeklyData.isNotEmpty()) add(ShareSection.WEEKLY_ACTIVITY)
            if (monthlyData.isNotEmpty()) add(ShareSection.MONTHLY_ACTIVITY)
            if (typeData.isNotEmpty()) add(ShareSection.TYPE_BREAKDOWN)
            if (trainerData.isNotEmpty()) add(ShareSection.TRAINER_BREAKDOWN)
            if (scoreEntry != null) add(ShareSection.SCORE)
            if (totalSessions > 0) add(ShareSection.ATTENDANCE)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Card rendered into graphicsLayer for capture ───────────────
                StatsShareCard(
                    season = season,
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
                    selectedSections = selectedSections,
                    modifier = Modifier.drawWithContent {
                        graphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(graphicsLayer)
                    }
                )

                Spacer(Modifier.height(8.dp))

                // ── Section picker ─────────────────────────────────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    availableSections.forEach { section ->
                        val label = when (section) {
                            ShareSection.OVERVIEW -> "Řada"
                            ShareSection.TOTAL_HOURS -> "Celkem hodin"
                            ShareSection.AVG_PER_WEEK -> "Trén./týden"
                            ShareSection.TOTAL_SESSIONS -> "Počet tréninků"
                            ShareSection.WEEKLY_ACTIVITY -> strings.weeklyActivity
                            ShareSection.MONTHLY_ACTIVITY -> strings.monthlyBreakdown
                            ShareSection.TYPE_BREAKDOWN -> strings.typeBreakdown
                            ShareSection.TRAINER_BREAKDOWN -> strings.trainerBreakdown
                            ShareSection.SCORE -> strings.myScore
                            ShareSection.ATTENDANCE -> strings.attendanceTabTitle
                        }
                        val isSelected = section in selectedSections
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedSections = if (isSelected) {
                                    if (selectedSections.size > 1) selectedSections - section
                                    else selectedSections
                                } else {
                                    if (selectedSections.size < 3) selectedSections + section
                                    else selectedSections
                                }
                            },
                            label = { Text(label, maxLines = 1) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Buttons ───────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSharing,
                        modifier = Modifier.weight(1f)
                    ) { Text("Zrušit") }

                    Button(
                        onClick = {
                            if (!isSharing) {
                                isSharing = true
                                scope.launch {
                                    val bitmap = graphicsLayer.toImageBitmap()
                                    shareCallback(bitmap)
                                    isSharing = false
                                    onDismiss()
                                }
                            }
                        },
                        enabled = !isSharing,
                        modifier = Modifier.weight(2f)
                    ) {
                        if (isSharing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Sdílet")
                        }
                    }
                }
            }
        }
    }
}

// ─── The actual visual share card ─────────────────────────────────────────────

@Composable
internal fun StatsShareCard(
    season: SeasonSelection,
    totalSessions: Int,
    totalMinutes: Long,
    avgPerWeek: Double,
    currentStreak: Int,
    weeklyData: List<WeekStats>,
    monthlyData: List<MonthStats> = emptyList(),
    typeData: List<TypeStat> = emptyList(),
    trainerData: List<TrainerStat> = emptyList(),
    scoreEntry: ScoreboardEntry? = null,
    cancelledCount: Int = 0,
    selectedSections: Set<ShareSection> = setOf(ShareSection.OVERVIEW, ShareSection.WEEKLY_ACTIVITY),
    modifier: Modifier = Modifier
) {
    val hours = (totalMinutes / 60.0).roundTo1dp()
    val avgFmt = avgPerWeek.roundTo1dp()
    val bg = MaterialTheme.colorScheme.background
    val onBg = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.primary
    val accentContainer = MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // ── Header: logo + season chip (always shown) ─────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppLogo(
                        size = 60.dp,
                        modifier = Modifier.graphicsLayer({
                            colorFilter = ColorFilter.tint(onBg)
                        })
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = accentContainer
                ) {
                    Text(
                        "Sezóna ${season.label}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // ── Řada (streak) ─────────────────────────────────────────────────
            if (ShareSection.OVERVIEW in selectedSections && currentStreak > 0) {
                Spacer(Modifier.height(24.dp))
                Text("🔥", fontSize = 48.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$currentStreak",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    color = accent,
                    lineHeight = 64.sp
                )
                Text(
                    text = "týdnů v řadě",
                    fontSize = 15.sp,
                    color = onBg.copy(alpha = 0.65f)
                )
            }

            // ── Celkem hodin ──────────────────────────────────────────────────
            if (ShareSection.TOTAL_HOURS in selectedSections) {
                Spacer(Modifier.height(24.dp))
                Text("⏱️", fontSize = 48.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$hours",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    color = accent,
                    lineHeight = 64.sp
                )
                Text(
                    text = "celkem hodin",
                    fontSize = 15.sp,
                    color = onBg.copy(alpha = 0.65f)
                )
            }

            // ── Tréninků za týden ─────────────────────────────────────────────
            if (ShareSection.AVG_PER_WEEK in selectedSections) {
                Spacer(Modifier.height(24.dp))
                Text("📅", fontSize = 48.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$avgFmt",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    color = accent,
                    lineHeight = 64.sp
                )
                Text(
                    text = "tréninků za týden",
                    fontSize = 15.sp,
                    color = onBg.copy(alpha = 0.65f)
                )
            }

            // ── Počet tréninků ────────────────────────────────────────────────
            if (ShareSection.TOTAL_SESSIONS in selectedSections) {
                Spacer(Modifier.height(24.dp))
                Text("🏅", fontSize = 48.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$totalSessions",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    color = accent,
                    lineHeight = 64.sp
                )
                Text(
                    text = "tréninků",
                    fontSize = 15.sp,
                    color = onBg.copy(alpha = 0.65f)
                )
            }

            // ── Týdenní aktivita ──────────────────────────────────────────────
            if (ShareSection.WEEKLY_ACTIVITY in selectedSections && weeklyData.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Týdenní aktivita",
                    fontSize = 10.sp,
                    color = onBg.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(6.dp))
                ShareMiniBarChart(weeklyData = weeklyData.takeLast(16))
            }

            // ── Měsíční aktivita ──────────────────────────────────────────────
            if (ShareSection.MONTHLY_ACTIVITY in selectedSections && monthlyData.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Měsíční aktivita",
                    fontSize = 10.sp,
                    color = onBg.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(6.dp))
                ShareMonthlyChart(data = monthlyData)
            }

            // ── Podle typu ────────────────────────────────────────────────────
            if (ShareSection.TYPE_BREAKDOWN in selectedSections && typeData.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Podle typu",
                    fontSize = 10.sp,
                    color = onBg.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(4.dp))
                ShareHorizontalBars(
                    items = typeData.take(6).map { it.displayName to it.count },
                    maxCount = typeData.take(6).maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                )
            }

            // ── Podle trenérů ─────────────────────────────────────────────────
            if (ShareSection.TRAINER_BREAKDOWN in selectedSections && trainerData.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Podle trenérů",
                    fontSize = 10.sp,
                    color = onBg.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(4.dp))
                ShareHorizontalBars(
                    items = trainerData.take(5).map {
                        (if (it.name.startsWith("(")) "ostatní" else stripTitles(it.name)) to it.count
                    },
                    maxCount = trainerData.take(5).maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                )
            }

            // ── Skóre ─────────────────────────────────────────────────────────
            if (ShareSection.SCORE in selectedSections && scoreEntry != null) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Skóre",
                    fontSize = 10.sp,
                    color = onBg.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (scoreEntry.ranking != null) {
                        ShareStatPill(
                            modifier = Modifier.weight(1f),
                            value = "#${scoreEntry.ranking}",
                            label = "pořadí"
                        )
                    }
                    if (scoreEntry.totalScore != null) {
                        ShareStatPill(
                            modifier = Modifier.weight(1f),
                            value = scoreEntry.totalScore!!.roundTo1dp().toString(),
                            label = "bodů celkem"
                        )
                    }
                }
            }

            // ── Docházka ──────────────────────────────────────────────────────
            if (ShareSection.ATTENDANCE in selectedSections && totalSessions > 0) {
                val total = totalSessions + cancelledCount
                val pct = if (total > 0) totalSessions * 100 / total else 100
                Spacer(Modifier.height(20.dp))
                Text(
                    "Docházka",
                    fontSize = 10.sp,
                    color = onBg.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShareStatPill(
                        modifier = Modifier.weight(1f),
                        value = "$pct %",
                        label = "absolvováno"
                    )
                    ShareStatPill(
                        modifier = Modifier.weight(1f),
                        value = totalSessions.toString(),
                        label = "tréninků"
                    )
                    if (cancelledCount > 0) {
                        ShareStatPill(
                            modifier = Modifier.weight(1f),
                            value = cancelledCount.toString(),
                            label = "omluveno"
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Footer branding ───────────────────────────────────────────────
            Text(
                "tkolymp.cz",
                fontSize = 10.sp,
                color = onBg.copy(alpha = 0.3f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Helper: stat pill ────────────────────────────────────────────────────────

@Composable
private fun ShareStatPill(modifier: Modifier = Modifier, value: String, label: String) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val accentContainer = MaterialTheme.colorScheme.primaryContainer
    val onAccentContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accentContainer)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = onAccentContainer,
                maxLines = 1
            )
            Text(
                text = label,
                fontSize = 9.sp,
                color = onAccentContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Helper: mini weekly bar chart ────────────────────────────────────────────

@Composable
private fun ShareMiniBarChart(weeklyData: List<WeekStats>) {
    val maxCount = weeklyData.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val highlightIdx = weeklyData.indexOfFirst { it.isCurrent }
    val accent = MaterialTheme.colorScheme.primary
    val onBg = MaterialTheme.colorScheme.onBackground

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        weeklyData.forEachIndexed { idx, week ->
            val fraction = week.count.toFloat() / maxCount.toFloat()
            val isHighlight = idx == highlightIdx
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction.coerceAtLeast(0.06f))
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(
                        when {
                            isHighlight -> accent
                            week.count > 0 -> onBg.copy(alpha = 0.38f)
                            else -> onBg.copy(alpha = 0.08f)
                        }
                    )
            )
        }
    }
}

// ─── Helper: monthly bar chart with roman-numeral month labels ────────────────

@Composable
private fun ShareMonthlyChart(data: List<MonthStats>) {
    val maxCount = data.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val accent = MaterialTheme.colorScheme.primary
    val onBg = MaterialTheme.colorScheme.onBackground

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEach { month ->
                val fraction = month.count.toFloat() / maxCount.toFloat()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction.coerceAtLeast(0.06f))
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(
                            if (month.count > 0) accent.copy(alpha = 0.8f)
                            else onBg.copy(alpha = 0.08f)
                        )
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            data.forEach { month ->
                val monthNum = month.yearMonth.takeLast(2).toIntOrNull() ?: 0
                val label = when (monthNum) {
                    1 -> "I"; 2 -> "II"; 3 -> "III"; 4 -> "IV"; 5 -> "V"; 6 -> "VI"
                    7 -> "VII"; 8 -> "VIII"; 9 -> "IX"; 10 -> "X"; 11 -> "XI"; 12 -> "XII"
                    else -> "?"
                }
                Text(
                    text = label,
                    fontSize = 5.sp,
                    color = onBg.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
            }
        }
    }
}

// ─── Helper: horizontal mini bars (type / trainer) ───────────────────────────

@Composable
private fun ShareHorizontalBars(items: List<Pair<String, Int>>, maxCount: Int) {
    val accent = MaterialTheme.colorScheme.primary
    val onBg = MaterialTheme.colorScheme.onBackground

    items.forEach { (name, count) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                fontSize = 9.sp,
                color = onBg.copy(alpha = 0.7f),
                modifier = Modifier.width(72.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp))
                        .background(onBg.copy(alpha = 0.08f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(count.toFloat() / maxCount.toFloat())
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent.copy(alpha = 0.8f))
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = count.toString(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = onBg.copy(alpha = 0.7f)
            )
        }
    }
}
