package com.tkolymp.tkolympapp.screens

import com.tkolymp.tkolympapp.components.InfoPill
import com.tkolymp.tkolympapp.components.RankBadge
import com.tkolymp.tkolympapp.components.cardLineBorder
import com.tkolymp.tkolympapp.components.successColor
import com.tkolymp.tkolympapp.components.successContainerColor
import com.tkolymp.tkolympapp.components.surfaceCardColors
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
import com.tkolymp.tkolympapp.components.TopAppBar
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
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import com.tkolymp.tkolympapp.components.EmptyState
import androidx.compose.material.icons.outlined.EmojiEvents

@Composable
internal fun CompetitionEventCard(competitions: List<Competition>, onOpenWeb: (() -> Unit)? = null) {
    val first = competitions.first()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = surfaceCardColors(),
        border = cardLineBorder()
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
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(8.dp))

                val rep = competitorComps.first()
                val nameDisplay = rep.competitorName?.takeIf { it.isNotBlank() } ?: rep.personName ?: ""
                val scheduled = competitorComps.filter { !it.hasResult }.sortedBy { it.checkInEnd }
                val results = competitorComps.filter { it.hasResult }.sortedBy { it.ranking }

                scheduled.forEach { comp -> CompetitionEntryRow(comp, competitorName = nameDisplay) }

                if (scheduled.isNotEmpty() && results.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                results.forEach { comp -> CompetitionEntryRow(comp, competitorName = nameDisplay) }
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
            .background(background, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun CompetitionEntryRow(
    comp: Competition,
    showCategory: Boolean = true,
    showCheckIn: Boolean = true,
    competitorName: String? = null
) {
    val rawCategory = comp.category?.name?.takeIf { it.isNotBlank() }
        ?: comp.competitionType?.takeIf { it.isNotBlank() }
        ?: ""
    val categoryName = AppStrings.current.competition.formatType(rawCategory)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCheckIn) {
            // Check-in time sits in its own fixed column at the far left so rows line up.
            Text(
                text = comp.checkInEnd?.take(5)?.takeIf { it.isNotBlank() }.orEmpty(),
                modifier = Modifier.width(44.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        val name = competitorName?.takeIf { it.isNotBlank() }
        val line = androidx.compose.ui.text.buildAnnotatedString {
            if (name != null) {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold))
                append(name)
                pop()
            }
            if (showCategory && categoryName.isNotBlank()) {
                if (name != null) append(" · ")
                append(categoryName)
            }
        }
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (comp.hasResult && comp.ranking != null) {
            val rankNum = comp.rankingTo?.takeIf { it != comp.ranking }
                ?.let { "${comp.ranking}-$it." } ?: "${comp.ranking}."
            val total = comp.participants?.let { " / $it" } ?: ""
            RankBadge(rankNum + total, winner = comp.ranking == 1, modifier = Modifier.padding(start = 6.dp))
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
            InfoPill(
                rightLabel,
                successContainerColor(),
                successColor(),
                modifier = Modifier.padding(start = 4.dp),
                icon = Icons.Default.BarChart
            )
        }
    }
}
