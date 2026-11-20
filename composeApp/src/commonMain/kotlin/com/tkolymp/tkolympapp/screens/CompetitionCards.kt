package com.tkolymp.tkolympapp.screens

import com.tkolymp.tkolympapp.components.InfoPill
import com.tkolymp.tkolympapp.components.RankBadge
import com.tkolymp.tkolympapp.components.cardLineBorder
import com.tkolymp.tkolympapp.components.pressClickable
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
import com.tkolymp.shared.competitions.CompetitionOfficial
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
internal fun CompetitionEventCard(competitions: List<Competition>, onOpenWeb: (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    val first = competitions.first()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (onClick != null) Modifier.pressClickable(0.985f, onClick) else Modifier),
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

            val byCategory = competitions
                .groupBy { competitionCategoryLabel(it) }
                .values
                .sortedBy { group -> group.mapNotNull { it.checkInEnd }.minOrNull() ?: "99:99" }
            byCategory.forEach { group ->
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(8.dp))

                val time = group.mapNotNull { it.checkInEnd?.take(5)?.takeIf { t -> t.isNotBlank() } }.minOrNull()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (time != null) {
                        Text(
                            text = time,
                            modifier = Modifier.padding(end = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = competitionCategoryLabel(group.first()),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                group
                    .sortedWith(compareBy({ !it.hasResult }, { it.ranking ?: Int.MAX_VALUE }, { competitionEntryName(it) }))
                    .forEach { comp ->
                        CompetitionEntryRow(
                            comp,
                            showCategory = false,
                            showCheckIn = false,
                            competitorName = competitionEntryName(comp),
                            boldName = false
                        )
                    }
            }
        }
    }
}

/** Adjudicators under "Porota"; every other role (chairperson, invigilator, scrutineers) under "Funkcionáři". */
@Composable
internal fun JuryList(officials: List<CompetitionOfficial>) {
    val strings = AppStrings.current.competition
    val (jury, others) = remember(officials) { officials.partition { it.role == "ADJUDICATOR" } }
    if (jury.isNotEmpty()) {
        OfficialsHeader(strings.jury)
        jury.distinctBy { it.name }.forEach { o ->
            Text(
                text = listOfNotNull(o.judgeLabel?.takeIf { it.isNotBlank() }, o.name?.takeIf { it.isNotBlank() }).joinToString(" "),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }
    }
    if (others.isNotEmpty()) {
        if (jury.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
        OfficialsHeader(strings.officials)
        others.sortedBy { OFFICIAL_ROLE_ORDER.indexOf(it.role).let { i -> if (i < 0) OFFICIAL_ROLE_ORDER.size else i } }.forEach { o ->
            Row(modifier = Modifier.padding(vertical = 1.dp)) {
                Text(
                    text = strings.term(o.role),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(120.dp)
                )
                Text(text = o.name.orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

private val OFFICIAL_ROLE_ORDER = listOf("CHAIRPERSON", "INVIGILATOR", "LEAD_SCRUTINEER", "SCRUTINEER")

@Composable
private fun OfficialsHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(4.dp))
}

private fun competitionCategoryLabel(comp: Competition): String {
    val raw = comp.category?.name?.takeIf { it.isNotBlank() }
        ?: comp.competitionType?.takeIf { it.isNotBlank() }
        ?: ""
    return AppStrings.current.competition.formatType(raw)
}

private fun competitionEntryName(comp: Competition): String =
    comp.competitorName?.takeIf { it.isNotBlank() } ?: comp.personName ?: ""

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
    competitorName: String? = null,
    boldName: Boolean = true
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
                if (boldName) {
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold))
                    append(name)
                    pop()
                } else {
                    append(name)
                }
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
