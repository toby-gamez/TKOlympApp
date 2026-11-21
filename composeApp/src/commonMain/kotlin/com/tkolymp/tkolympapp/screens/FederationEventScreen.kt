package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import com.tkolymp.tkolympapp.components.skeleton.SkeletonDetail
import com.tkolymp.tkolympapp.components.AppTopBar
import com.tkolymp.tkolympapp.components.InfoPill
import com.tkolymp.tkolympapp.components.RankBadge
import com.tkolymp.tkolympapp.components.SectionHeader
import com.tkolymp.tkolympapp.components.cardLineBorder
import com.tkolymp.tkolympapp.components.pressClickable
import com.tkolymp.tkolympapp.components.successColor
import com.tkolymp.tkolympapp.components.successContainerColor
import com.tkolymp.tkolympapp.components.surfaceCardColors
import com.tkolymp.tkolympapp.ui.theme.BrandLightPrimary
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.HowToReg
import com.tkolymp.tkolympapp.components.RowDivider
import com.tkolymp.tkolympapp.components.SheetPrimaryButton
import com.tkolymp.tkolympapp.ui.theme.Spacing
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.tkolymp.tkolympapp.components.TopAppBar
import androidx.compose.runtime.Composable
import com.tkolymp.tkolympapp.util.SkeletonCrossfade
import com.tkolymp.tkolympapp.util.cozyContentSize
import com.tkolymp.tkolympapp.util.cozyItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.competitivedata.FederationCompetitionSummary
import com.tkolymp.shared.competitivedata.FederationEventDetail
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.viewmodels.FederationEventDetailState
import com.tkolymp.shared.viewmodels.FederationEventDetailViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.EmptyState
import com.tkolymp.tkolympapp.components.ErrorState
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Category names the way the competition screens show them (DanceForAll -> TPV, Standard -> STT, ...).
 * WDSF names ("General SENIOR IV OPEN STANDARD") are lower-cased first so the same replacements apply.
 */
internal fun formatFederationCategory(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    val normalised = raw.removePrefix("General ").split(' ').joinToString(" ") { w ->
        if (w.length > 3 && w.all { it.isUpperCase() }) w.lowercase().replaceFirstChar { it.uppercase() } else w
    }
    return AppStrings.current.competition.formatType(normalised)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FederationEventScreen(
    eventId: Long,
    onBack: () -> Unit = {},
    bottomPadding: Dp = 0.dp
) {
    val viewModel = viewModel<FederationEventDetailViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val strings = AppStrings.current.danceOrg
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(eventId) { viewModel.load(eventId) }

    val event = state.event
    val signUp = remember(event) { event?.let { signUpUrl(it) } }

    Scaffold(
        topBar = {
            AppTopBar(title = event?.name ?: strings.event, onBack = onBack)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            SwipeToReload(
                isRefreshing = state.isLoading,
                onRefresh = { scope.launch { viewModel.load(eventId) } },
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                SkeletonCrossfade(event == null && state.isLoading) { showSkeleton ->
                when {
                    showSkeleton -> SkeletonDetail()
                    event == null -> ErrorState(state.error?.message, onRetry = { scope.launch { viewModel.load(eventId) } })
                    else -> EventContent(event, state, if (signUp == null) bottomPadding else 0.dp,
                        onOpenWeb = { uriHandler.openUri(it) },
                        onExpand = { id -> scope.launch { viewModel.loadResults(id) } })
                }
                }
            }
            if (signUp != null) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                    RowDivider()
                    SheetPrimaryButton(
                        text = strings.signUp,
                        onClick = { uriHandler.openUri(signUp) },
                        modifier = Modifier.navigationBarsPadding().padding(start = Spacing.default, end = Spacing.default, top = Spacing.medium, bottom = 14.dp),
                        icon = Icons.Default.HowToReg
                    )
                }
            }
        }
    }
}

@Composable
private fun EventContent(
    event: FederationEventDetail,
    state: FederationEventDetailState,
    bottomPadding: Dp,
    onOpenWeb: (String) -> Unit,
    onExpand: (Long) -> Unit
) {
    val strings = AppStrings.current.danceOrg
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            // Hero: same red card as "next training" on Overview, carrying date and place.
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BrandLightPrimary, contentColor = Color.White)
            ) {
                SelectionContainer {
                    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val status = strings.status(event.status).takeIf { it.isNotBlank() }
                        if (status != null) Text(status.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Text(formatDateRange(event.startDate, event.endDate), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(event.name ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val where = listOfNotNull(event.city ?: event.location, event.country).distinct().joinToString(", ")
                        if (where.isNotBlank()) Text(where, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            val address = listOfNotNull(
                event.streetAddress,
                listOfNotNull(event.postalCode, event.city ?: event.location).joinToString(" ").takeIf { it.isNotBlank() },
                event.country
            ).joinToString(", ")
            val rows = listOf(
                strings.address to address,
                strings.address to event.addressNote,
                strings.organizer to event.organizer,
                strings.contact to listOfNotNull(event.contactName, event.contactPhone, event.contactEmail).joinToString(", ")
            ).filter { !it.second.isNullOrBlank() }
            if (rows.isNotEmpty() || !event.websiteUrl.isNullOrBlank()) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = surfaceCardColors(), border = cardLineBorder()) {
                    SelectionContainer {
                        Column {
                            rows.forEachIndexed { i, (label, value) ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                InfoLine(label, value)
                            }
                            event.websiteUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                if (rows.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                Row(
                                    Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenWeb(url) }.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text("  ${strings.website}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (event.officials.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = surfaceCardColors(), border = cardLineBorder()) {
                    SelectionContainer { Column(Modifier.padding(14.dp)) { JuryList(event.officials) } }
                }
            }
        }
        item { SectionHeader(title = strings.competitionsHeader, modifier = Modifier.padding(horizontal = 0.dp).offset(x = (-16).dp)) }
        if (event.competitions.isEmpty()) {
            item { EmptyState(strings.noCompetitions, modifier = Modifier.padding(24.dp)) }
        } else {
            items(event.competitions, key = { it.id }) { c ->
                val isOpen = c.id in expanded
                Box(cozyItem()) {
                    CompetitionCard(c, isOpen, state, onToggle = {
                        expanded = if (isOpen) expanded - c.id else expanded + c.id
                        if (!isOpen) onExpand(c.id)
                    })
                }
            }
        }
    }
}

/**
 * Where to sign up for an event that is not finished yet. ČSTS has one sign-up page per event, addressed by the
 * event's `externalId` and `extraId`; for the other federations only the event's own website is known.
 */
private fun signUpUrl(event: FederationEventDetail): String? {
    if (event.status == "COMPLETED" || event.status == "CANCELLED") return null
    val lastDay = (event.endDate ?: event.startDate).substringBefore('T')
    val last = try { LocalDate.parse(lastDay) } catch (_: Exception) { null }
    if (last != null && last < kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())) return null
    return when (event.federation.lowercase()) {
        "csts" -> if (event.externalId != null && event.extraId != null) {
            "https://www.csts.cz/dancesport/kalendar_akci/event/${event.externalId}/competition/${event.extraId}/prihlaseni"
        } else null
        "wdsf" -> wdsfEventUrl(event) ?: event.websiteUrl?.takeIf { it.isNotBlank() }
        else -> event.websiteUrl?.takeIf { it.isNotBlank() }
    }
}

/**
 * WDSF event page, e.g. `/Events/Dresden-Germany-20112026-22112026-8739`: the name with its separators turned into
 * dashes ("Dresden, Germany" and "Dresden - Germany" both give `Dresden-Germany`), start and end date as ddMMyyyy, and
 * the WDSF event id (our `externalId`).
 */
private fun wdsfEventUrl(event: FederationEventDetail): String? {
    val id = event.externalId?.takeIf { it.isNotBlank() && it.all(Char::isDigit) } ?: return null
    val start = try { LocalDate.parse(event.startDate.substringBefore('T')) } catch (_: Exception) { return null }
    val end = try { LocalDate.parse((event.endDate ?: event.startDate).substringBefore('T')) } catch (_: Exception) { start }
    fun ddMMyyyy(d: LocalDate) = "${d.dayOfMonth.toString().padStart(2, '0')}${d.monthNumber.toString().padStart(2, '0')}${d.year}"
    val slug = (event.name ?: "").split(Regex("[\\s,\\-–]+")).filter { it.isNotBlank() }.joinToString("-")
    val parts = listOf(slug, ddMMyyyy(start), ddMMyyyy(end), id).filter { it.isNotEmpty() }
    return "https://www.worlddancesport.org/Events/${parts.joinToString("-")}#tab=general"
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CompetitionCard(
    c: FederationCompetitionSummary,
    open: Boolean,
    state: FederationEventDetailState,
    onToggle: () -> Unit
) {
    val strings = AppStrings.current.danceOrg
    val results = state.results[c.id]
    Card(
        Modifier.fillMaxWidth().pressClickable(0.995f, onToggle),
        shape = RoundedCornerShape(16.dp),
        colors = surfaceCardColors(),
        border = cardLineBorder()
    ) {
        Column(Modifier.cozyContentSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(formatFederationCategory(c.categoryName), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    val meta = listOfNotNull(
                        formatIsoDate(c.startDate),
                        strings.competitionType(c.competitionType ?: "").takeIf { it.isNotBlank() },
                        strings.status(c.status).takeIf { it.isNotBlank() },
                        c.participants?.let { "${AppStrings.current.competition.participants}: $it" }
                    ).joinToString(" · ")
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
            if (open) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                when {
                    c.id in state.loadingResults -> Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                    results != null && results.results.isEmpty() && results.entries.isNotEmpty() -> SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${strings.registeredHeader} (${results.entries.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            results.entries.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = if (it in results.clubEntries) FontWeight.Bold else null) }
                        }
                    }
                    results == null || results.results.isEmpty() -> Text(strings.noResultsYet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        results.results.forEach { r ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                RankBadge(
                                    if (r.rankingTo != null && r.rankingTo != r.ranking) "${r.ranking}.–${r.rankingTo}." else "${r.ranking}.",
                                    winner = r.ranking == 1,
                                    modifier = Modifier.widthIn(min = 40.dp)
                                )
                                Text(
                                    r.competitorName ?: "—",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (r.isClub) FontWeight.Bold else null
                                )
                                r.pointGain?.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                                    InfoPill("+${it.toString().removeSuffix(".0")}${AppStrings.current.competition.pointsSuffix}", successContainerColor(), successColor())
                                }
                                if (r.isFinal) InfoPill("F", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                    }
                }
                if (results != null && results.officials.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    SelectionContainer { Column { JuryList(results.officials) } }
                }
            }
        }
    }
}
