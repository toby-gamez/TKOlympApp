package com.tkolymp.tkolympapp.screens

import com.tkolymp.tkolympapp.components.SectionLabel
import com.tkolymp.tkolympapp.components.CountdownCard
import com.tkolymp.tkolympapp.components.cardLineBorder
import com.tkolymp.tkolympapp.components.surfaceCardColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.tkolymp.tkolympapp.util.cozyContentSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.tkolymp.shared.competitions.CstsProgress
import com.tkolymp.shared.language.AppStrings
import kotlinx.datetime.todayIn
import androidx.compose.foundation.clickable
import com.tkolymp.shared.people.CoupleResult
import com.tkolymp.shared.people.FederationCompetitionEntry
import com.tkolymp.shared.people.FederationProfile
import com.tkolymp.shared.people.FederationRanking

internal const val WDSF = "WDSF"

private const val COLLAPSED_COUNT = 3

private fun fmtPoints(raw: String?): String? {
    val d = raw?.toDoubleOrNull() ?: return null
    if (d == 0.0) return null
    return if (d % 1.0 == 0.0) d.toInt().toString() else { val s = kotlin.math.round(d * 100).toLong(); "${s / 100}.${(s % 100).toString().padStart(2, '0')}" }
}

private fun date(raw: String?): String? = formatDateStringSmall(raw) ?: raw

/** "valid until X" for future dates, past tense once the date has passed. */
@Composable
private fun validUntilText(raw: String?): String? {
    val formatted = date(raw) ?: return null
    val c = AppStrings.current.competition
    val today = remember { kotlin.time.Clock.System.todayIn(kotlinx.datetime.TimeZone.currentSystemDefault()) }
    val parsed = raw?.trim()?.take(10)?.let { try { kotlinx.datetime.LocalDate.parse(it) } catch (_: Exception) { null } }
    val label = if (parsed != null && parsed < today) c.validUntilPast else c.validUntil
    return "$label $formatted"
}

@Composable
private fun FederationCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = surfaceCardColors(),
        border = cardLineBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

/** Shows the first [COLLAPSED_COUNT] items and a toggle button for the rest. */
@Composable
private fun <T> CollapsibleList(items: List<T>, stateKey: String, row: @Composable (Int, T) -> Unit) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    val c = AppStrings.current.competition
    val shown = if (expanded) items else items.take(COLLAPSED_COUNT)
    Column(Modifier.cozyContentSize()) {
        shown.forEachIndexed { idx, item ->
            if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            row(idx, item)
        }
    }
    if (items.size > COLLAPSED_COUNT) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) c.showLess else c.showAll.replace("{n}", items.size.toString()))
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun WdsfProfileCard(profile: FederationProfile) {
    val c = AppStrings.current.competition
    FederationCard(c.wdsfProfile) {
        profile.name?.let { LabelValue(AppStrings.current.profile.person, it) }
        profile.externalId?.let { LabelValue("WDSF MIN", it) }
        profile.nationality?.let { LabelValue(AppStrings.current.profile.nationality, it) }
        profile.ageGroup?.let { LabelValue(c.ageGroupLabel, c.term(it)) }
        date(profile.birthDate)?.let { LabelValue(AppStrings.current.profile.birthDate, it) }
        val med = listOfNotNull(profile.medicalCheckupType?.let { c.term(it) }, validUntilText(profile.medicalCheckupExpiration))
        if (med.isNotEmpty()) LabelValue(c.medicalCheckup, med.joinToString(", "))
        if (profile.licenses.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(c.wdsfLicenses, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            // active licenses first
            val sorted = profile.licenses.sortedBy { if (it.status.equals("ACTIVE", true)) 0 else 1 }
            CollapsibleList(sorted, "wdsf_licenses") { _, l ->
                val title = listOfNotNull(
                    l.kind?.let { c.term(it) },
                    l.discipline?.takeIf { it != "GENERAL" && it != "UNKNOWN" }?.let { c.term(it) },
                    l.grade
                ).joinToString(" · ")
                val sub = listOfNotNull(l.status?.let { c.term(it) }, validUntilText(l.validUntil)).joinToString(", ")
                Column {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun WdsfProgressCard(progress: List<CstsProgress>, title: String? = null) {
    val c = AppStrings.current.competition
    // most valuable categories first (points, then finals)
    val sorted = progress.sortedWith(compareByDescending<CstsProgress> { it.points?.toDoubleOrNull() ?: 0.0 }.thenByDescending { it.finals ?: 0 })
    val multiple = sorted.mapNotNull { it.competitorName?.takeIf { n -> n.isNotBlank() } }.distinct().size > 1
    FederationCard(title ?: c.wdsfProgress) {
        CollapsibleList(sorted, "wdsf_progress_${title.orEmpty()}") { _, e ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatFederationCategory(e.category?.name), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    fmtPoints(e.points)?.let { Text("$it${c.pointsSuffix}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                }
                val sub = listOfNotNull(
                    e.competitorName?.takeIf { multiple && it.isNotBlank() },
                    e.finals?.takeIf { it > 0 }?.let { "$it ${c.finals.lowercase()}" },
                    e.foreignFinals?.takeIf { it > 0 }?.let { "$it ${c.foreignFinalsLabel}" }
                ).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Chip(text: String, container: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f), content: Color = MaterialTheme.colorScheme.onSurface) {
    Surface(shape = RoundedCornerShape(8.dp), color = container, contentColor = content) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), maxLines = 1)
    }
}

@Composable
private fun federationName(code: String?): String? = code?.let { federationLabel(it) }

/** Newest ranking per ranklist (federation + category + competitor); older snapshots are dropped. */
internal fun latestRankings(all: List<FederationRanking>): List<FederationRanking> =
    all.groupBy { Triple(it.federation, it.ranklistName ?: it.categoryName, it.competitorName) }
        .values.mapNotNull { g -> g.maxByOrNull { it.asOfDate.orEmpty() } }
        .sortedWith(compareBy<FederationRanking> { it.federation.orEmpty() }.thenBy { it.ranking })

/** Newest ranking per ranklist as cards like the competitions: the placing in the tile, ranklist name, federation and date. */
@Composable
fun FederationRankingsCard(rankings: List<FederationRanking>) {
    val c = AppStrings.current.competition
    val latest = remember(rankings) { latestRankings(rankings) }
    var expanded by rememberSaveable("fed_rankings") { mutableStateOf(false) }
    val shown = if (expanded) latest else latest.take(COLLAPSED_COUNT)
    Column {
        SectionLabel(c.wdsfRankings, horizontalPadding = 0.dp)
        Column(Modifier.cozyContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { r ->
                CountdownCard(
                    days = if (r.rankingTo != null && r.rankingTo != r.ranking) "${r.ranking}.–${r.rankingTo}." else "${r.ranking}.",
                    daysUnit = fmtPoints(r.points)?.let { "$it${c.pointsSuffix}" }.orEmpty(),
                    title = (r.ranklistName ?: r.categoryName)?.let { formatFederationCategory(it) } ?: "—",
                    meta = listOfNotNull(federationName(r.federation), r.competitorName).joinToString(" · "),
                    footnote = date(r.asOfDate)?.let { "${c.asOf} $it" },
                    onClick = null
                )
            }
            if (latest.size > COLLAPSED_COUNT) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (expanded) c.showLess else c.showAll.replace("{n}", latest.size.toString()))
                }
            }
        }
    }
}

private class EventGroup(val eventId: String?, val name: String?, val date: String?, val federation: String?, val place: String?, val items: List<FederationCompetitionEntry>)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FederationCompetitionsCard(items: List<FederationCompetitionEntry>, onOpenEvent: (String) -> Unit = {}) {
    val c = AppStrings.current.competition
    val org = AppStrings.current.danceOrg
    // one row per event: its categories (STT, LAT, ...) become pills instead of repeated rows
    val groups = remember(items) {
        items.groupBy { Triple(it.eventName, it.startDate, it.federation) }.map { (k, g) ->
            EventGroup(g.firstNotNullOfOrNull { it.eventId }, k.first, k.second, k.third, listOfNotNull(g.first().city, g.first().country).joinToString(", ").takeIf { it.isNotBlank() }, g)
        }
    }
    var expanded by rememberSaveable("fed_competitions") { mutableStateOf(false) }
    val shown = if (expanded) groups else groups.take(COLLAPSED_COUNT)
    Column {
        SectionLabel(c.recentCompetitions, horizontalPadding = 0.dp)
        Column(Modifier.cozyContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { g ->
                val parts = g.date?.split("-")
                val meta = listOfNotNull(
                    federationName(g.federation),
                    g.place,
                    g.items.firstOrNull()?.competitionType?.let { org.competitionType(it).ifBlank { null } }
                ).joinToString(" · ")
                val categories = g.items.joinToString(" · ") { e ->
                    formatFederationCategory(e.categoryName) + (e.participants?.let { " ($it)" } ?: "")
                }
                CountdownCard(
                    days = parts?.getOrNull(2)?.trimStart('0')?.ifEmpty { "0" } ?: "–",
                    daysUnit = listOfNotNull(parts?.getOrNull(1)?.toIntOrNull()?.let { org.monthShort(it) }, parts?.getOrNull(0)).joinToString(" "),
                    title = g.name ?: formatFederationCategory(g.items.first().categoryName),
                    meta = meta,
                    footnote = categories.takeIf { it.isNotBlank() },
                    onClick = g.eventId?.let { id -> { onOpenEvent(id) } }
                )
            }
            if (groups.size > COLLAPSED_COUNT) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (expanded) c.showLess else c.showAll.replace("{n}", groups.size.toString()))
                }
            }
        }
    }
}

private class CoupleEventGroup(val eventId: String?, val name: String?, val date: String?, val federation: String?, val place: String?, val items: List<CoupleResult>)

/** A couple's recent competitions in the person screen's design: one card per event with the placing of every category. */
@Composable
fun CoupleResultsCard(results: List<CoupleResult>, onOpenEvent: (String) -> Unit = {}) {
    val c = AppStrings.current.competition
    val org = AppStrings.current.danceOrg
    val groups = remember(results) {
        results.groupBy { Triple(it.eventName, it.startDate, it.federation) }.map { (k, g) ->
            CoupleEventGroup(g.firstNotNullOfOrNull { it.eventId }, k.first, k.second, k.third, listOfNotNull(g.first().city, g.first().country).joinToString(", ").takeIf { it.isNotBlank() }, g)
        }
    }
    var expanded by rememberSaveable("couple_results") { mutableStateOf(false) }
    val shown = if (expanded) groups else groups.take(COLLAPSED_COUNT)
    Column {
        SectionLabel(c.recentCompetitions, horizontalPadding = 0.dp)
        Column(Modifier.cozyContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { g ->
                val parts = g.date?.split("-")
                val footnote = g.items.joinToString("\n") { r ->
                    val place = (if (r.rankingTo != null && r.rankingTo != r.ranking) "${r.ranking}.–${r.rankingTo}." else "${r.ranking}.") + (r.participants?.let { "/$it" } ?: "")
                    listOfNotNull(
                        formatFederationCategory(r.categoryName),
                        place,
                        fmtPoints(r.pointGain)?.let { "+$it${c.pointsSuffix}" }
                    ).joinToString(" · ")
                }
                CountdownCard(
                    days = parts?.getOrNull(2)?.trimStart('0')?.ifEmpty { "0" } ?: "–",
                    daysUnit = listOfNotNull(parts?.getOrNull(1)?.toIntOrNull()?.let { org.monthShort(it) }, parts?.getOrNull(0)).joinToString(" "),
                    title = g.name ?: formatFederationCategory(g.items.first().categoryName),
                    meta = listOfNotNull(federationName(g.federation), g.place).joinToString(" · "),
                    footnote = footnote.takeIf { it.isNotBlank() },
                    onClick = g.eventId?.let { id -> { onOpenEvent(id) } }
                )
            }
            if (groups.size > COLLAPSED_COUNT) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (expanded) c.showLess else c.showAll.replace("{n}", groups.size.toString()))
                }
            }
        }
    }
}
