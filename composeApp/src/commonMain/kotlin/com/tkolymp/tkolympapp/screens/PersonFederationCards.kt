package com.tkolymp.tkolympapp.screens

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

@Composable
private fun FederationCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
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
    shown.forEachIndexed { idx, item ->
        if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        row(idx, item)
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
        val med = listOfNotNull(profile.medicalCheckupType?.let { c.term(it) }, date(profile.medicalCheckupExpiration)?.let { "${c.validUntil} $it" })
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
                val sub = listOfNotNull(l.status?.let { c.term(it) }, date(l.validUntil)?.let { "${c.validUntil} $it" }).joinToString(", ")
                Column {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun WdsfProgressCard(progress: List<CstsProgress>) {
    val c = AppStrings.current.competition
    // most valuable categories first (points, then finals)
    val sorted = progress.sortedWith(compareByDescending<CstsProgress> { it.points?.toDoubleOrNull() ?: 0.0 }.thenByDescending { it.finals ?: 0 })
    val multiple = sorted.mapNotNull { it.competitorName?.takeIf { n -> n.isNotBlank() } }.distinct().size > 1
    FederationCard(c.wdsfProgress) {
        CollapsibleList(sorted, "wdsf_progress") { _, e ->
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
private fun federationName(code: String?): String? = code?.let { federationEmoji(it)?.let { e -> "$e ${federationLabel(it)}" } ?: federationLabel(it) }

/** Newest ranking per ranklist (federation + category + competitor); older snapshots are dropped. */
internal fun latestRankings(all: List<FederationRanking>): List<FederationRanking> =
    all.groupBy { Triple(it.federation, it.ranklistName ?: it.categoryName, it.competitorName) }
        .values.map { g -> g.maxByOrNull { it.asOfDate.orEmpty() }!! }
        .sortedWith(compareBy<FederationRanking> { it.federation.orEmpty() }.thenBy { it.ranking })

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FederationRankingsCard(rankings: List<FederationRanking>) {
    val c = AppStrings.current.competition
    val latest = remember(rankings) { latestRankings(rankings) }
    FederationCard(c.wdsfRankings) {
        CollapsibleList(latest, "fed_rankings") { _, r ->
            val place = if (r.rankingTo != null && r.rankingTo != r.ranking) "${r.ranking}.–${r.rankingTo}." else "${r.ranking}."
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                    Box(Modifier.width(64.dp).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(place, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        (r.ranklistName ?: r.categoryName)?.let { formatFederationCategory(it) } ?: "—",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    r.competitorName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        federationName(r.federation)?.let { Chip(it) }
                        fmtPoints(r.points)?.let { Chip("$it${c.pointsSuffix}", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) }
                        date(r.asOfDate)?.let { Text("${c.asOf} $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

private class EventGroup(val name: String?, val date: String?, val federation: String?, val place: String?, val items: List<FederationCompetitionEntry>)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FederationCompetitionsCard(items: List<FederationCompetitionEntry>) {
    val c = AppStrings.current.competition
    val org = AppStrings.current.danceOrg
    // one row per event: its categories (STT, LAT, ...) become chips instead of repeated rows
    val groups = remember(items) {
        items.groupBy { Triple(it.eventName, it.startDate, it.federation) }.map { (k, g) ->
            EventGroup(k.first, k.second, k.third, listOfNotNull(g.first().city, g.first().country).joinToString(", ").takeIf { it.isNotBlank() }, g)
        }
    }
    FederationCard(c.recentCompetitions) {
        CollapsibleList(groups, "fed_competitions") { _, g ->
            val parts = g.date?.split("-")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)) {
                    Column(Modifier.width(52.dp).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(parts?.getOrNull(2)?.trimStart('0') ?: "–", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(parts?.getOrNull(1)?.toIntOrNull()?.let { org.monthShort(it) } ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(parts?.getOrNull(0) ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(g.name ?: formatFederationCategory(g.items.first().categoryName), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(g.place, g.items.firstOrNull()?.competitionType?.let { org.competitionType(it).ifBlank { null } }).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        federationName(g.federation)?.let { Chip(it) }
                        g.items.forEach { e ->
                            val label = formatFederationCategory(e.categoryName) + (e.participants?.let { " · $it" } ?: "")
                            Chip(label)
                        }
                    }
                }
            }
        }
    }
}
