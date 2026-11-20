package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import com.tkolymp.tkolympapp.components.skeleton.SkeletonProfile
import com.tkolymp.tkolympapp.components.RowDivider
import com.tkolymp.tkolympapp.components.ScheduleCard
import com.tkolymp.tkolympapp.components.SectionLabel
import com.tkolymp.tkolympapp.ui.theme.Spacing
import com.tkolymp.tkolympapp.components.cardLineBorder
import com.tkolymp.tkolympapp.components.surfaceCardColors
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material3.Card
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.tkolymp.tkolympapp.components.TopAppBar
import androidx.compose.runtime.Composable
import com.tkolymp.tkolympapp.util.SkeletonCrossfade
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tkolymp.tkolympapp.util.StaggeredItem
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.people.PersonDetails
import com.tkolymp.shared.utils.currentAge
import com.tkolymp.shared.utils.formatShortDate
import com.tkolymp.shared.utils.parseToLocal
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.tkolymp.tkolympapp.components.successContainerColor
import com.tkolymp.tkolympapp.components.successColor
import com.tkolymp.tkolympapp.components.pressClickable
import com.tkolymp.tkolympapp.components.InfoPill
import com.tkolymp.shared.utils.withImageWidth
import com.tkolymp.shared.people.CoupleMember
import com.tkolymp.shared.people.CouplePeriod
import com.tkolymp.shared.viewmodels.PersonViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.CoupleAvatar
import com.tkolymp.shared.competitions.deriveDanceClasses
import com.tkolymp.tkolympapp.components.PersonHeader
import com.tkolymp.tkolympapp.components.InitialsAvatar
import com.tkolymp.tkolympapp.components.PersonNoteCard
import com.tkolymp.tkolympapp.components.SocialLinksRow
import com.tkolymp.tkolympapp.components.parseColorOrDefault
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import com.tkolymp.tkolympapp.components.EmptyState
import com.tkolymp.tkolympapp.components.ErrorBanner
import androidx.compose.material.icons.outlined.PersonOff
import com.tkolymp.tkolympapp.util.ISO_DATE_PREFIX_REGEX

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(personId: String, onBack: () -> Unit = {}, onOpenCouple: (String) -> Unit = {}, onOpenAchievements: (String) -> Unit = {}, onOpenCohort: ((String) -> Unit)? = null, onOpenFederationEvent: (String) -> Unit = {}) {
    val viewModel = viewModel<PersonViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var cardsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
    }
    LaunchedEffect(state.person) { if (state.person != null) cardsVisible = true }

    Scaffold(topBar = {
        TopAppBar(title = { Text(AppStrings.current.profile.person) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back) }
        })
    }) { padding ->
        val scope = rememberCoroutineScope()

        SwipeToReload(isRefreshing = state.isLoading, onRefresh = { scope.launch { viewModel.loadPerson(personId) } }, modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.padding(horizontal = Spacing.default, vertical = Spacing.small).verticalScroll(rememberScrollState())) {

            state.error?.let { ErrorBanner(error = it, onRetry = { scope.launch { viewModel.loadPerson(personId) } }) }

            val p = state.person as? PersonDetails
            if (p == null) {
                SkeletonCrossfade(state.isLoading && state.error == null) { showSkeleton ->
                if (showSkeleton) {
                    SkeletonProfile()
                } else if (state.error == null) {
                    EmptyState(title = AppStrings.current.profile.personNotFound, icon = Icons.Outlined.PersonOff)
                }
                }
                return@Column
            }

            // Header (name)
            val baseName = listOf(p.prefixTitle, p.firstName, p.lastName).filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
            val fullName = if (!p.suffixTitle.isNullOrBlank()) "$baseName, ${p.suffixTitle}" else baseName
            PersonHeader(
                rawName = fullName.ifBlank { p.id },
                imageUrl = p.imageUrl,
                details = listOfNotNull(
                    AppStrings.current.profile.trainer.takeIf { p.isTrainer == true },
                    deriveDanceClasses(p.cstsProgressList)?.let { "STT/LAT ${it.label}" }
                )
            )
            SocialLinksRow(person = p)
            Spacer(modifier = Modifier.height(10.dp))

            // Couples: every relationship the club has for this person, active first
            if (p.allCouplesList.isNotEmpty()) {
                val couples = remember(p.allCouplesList, p.activeCouplesList) {
                    p.allCouplesList
                        .map { cp ->
                            // older cached payloads carry no names for the full list; the active list may still have them
                            if (cp.man != null || cp.woman != null) cp else p.activeCouplesList.firstOrNull { it.id == cp.id }?.let { cp.copy(man = it.man, woman = it.woman) } ?: cp
                        }
                        .sortedWith(compareByDescending<CouplePeriod> { it.isActive }.thenByDescending { it.since.orEmpty() })
                }
                StaggeredItem(index = 0, visible = cardsVisible, baseDelayMs = 50) {
                    Column {
                        SectionLabel(AppStrings.current.profile.couples, horizontalPadding = 0.dp)
                        ScheduleCard {
                            Column {
                                couples.forEachIndexed { i, cp ->
                                    if (i > 0) RowDivider()
                                    CoupleRow(cp, onClick = cp.id?.let { cid -> { onOpenCouple(cid) } })
                                }
                            }
                        }
                    }
                }
            }
            val cstsProgressList = p.cstsProgressList.filter { !it.federation.equals(WDSF, ignoreCase = true) }
            val wdsfProgressList = p.cstsProgressList.filter { it.federation.equals(WDSF, ignoreCase = true) }


            // Personal data
            val genderLabel = when (p.gender) {
                "MAN" -> AppStrings.current.gender.genderMale
                "WOMAN" -> AppStrings.current.gender.genderFemale
                "UNSPECIFIED" -> AppStrings.current.gender.genderUnspecified
                null, "" -> "—"
                else -> (p.gender ?: "").lowercase()
            }
            StaggeredItem(index = 1, visible = cardsVisible, baseDelayMs = 50) {
                Column {
                    SectionLabel(AppStrings.current.profile.personalData, horizontalPadding = 0.dp)
                    ScheduleCard {
                        val bdText = p.birthDate?.let { formatDateStringSmall(it) ?: it } ?: "—"
                        val ageText = currentAge(p.birthDate)?.let { " · ${AppStrings.current.profile.yearsOld.replace("%d", it.toString())}" } ?: ""
                        ProfileValueRow(AppStrings.current.profile.birthDate, "$bdText$ageText")
                        RowDivider()
                        ProfileValueRow(AppStrings.current.profile.gender, genderLabel)
                    }
                }
            }

            // Contacts
            val emailText = p.email?.takeIf { it.isNotBlank() }
            if (emailText != null) {
                StaggeredItem(index = 2, visible = cardsVisible, baseDelayMs = 50) {
                    Column {
                        SectionLabel(AppStrings.current.profile.contacts, horizontalPadding = 0.dp)
                        ScheduleCard { ProfileValueRow(AppStrings.current.profile.email, emailText) }
                    }
                }
            }

            // External identification
            val cstsId = p.cstsId?.takeIf { it.isNotBlank() }
            val wdsfId = p.wdsfId?.takeIf { it.isNotBlank() }
            if (cstsId != null || wdsfId != null) {
                StaggeredItem(index = 3, visible = cardsVisible, baseDelayMs = 50) {
                    val uriHandler = LocalUriHandler.current
                    Column {
                        SectionLabel("ČSTS · WDSF", horizontalPadding = 0.dp)
                        ScheduleCard {
                            if (cstsId != null) ProfileValueRow(
                                "ČSTS IDT", cstsId,
                                onClick = { uriHandler.openUri("https://www.csts.cz/dancesport/evidence/lide/$cstsId/osobni_udaje") },
                                trailingIcon = Icons.AutoMirrored.Filled.OpenInNew
                            )
                            if (cstsId != null && wdsfId != null) RowDivider()
                            if (wdsfId != null) ProfileValueRow("WDSF MIN", wdsfId)
                        }
                    }
                }
            }

            if (!p.note.isNullOrBlank()) {
                StaggeredItem(index = 4, visible = cardsVisible, baseDelayMs = 50) {
                    PersonNoteCard(note = p.note)
                }
            }

            // Training groups
            val visibleGroups = p.cohortMembershipsList.filter { it.cohort?.isVisible != false }
            if (visibleGroups.isNotEmpty()) {
                StaggeredItem(index = 5, visible = cardsVisible, baseDelayMs = 50) {
                    Column {
                        SectionLabel(AppStrings.current.otherScreen.trainingGroups, horizontalPadding = 0.dp)
                        ScheduleCard {
                            visibleGroups.forEachIndexed { i, mem ->
                                val c = mem.cohort ?: return@forEachIndexed
                                val color = remember(c.colorRgb) { try { parseColorOrDefault(c.colorRgb) } catch (_: Exception) { Color.Gray } }
                                val cohortId = c.id
                                if (i > 0) RowDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (onOpenCohort != null && cohortId != null) Modifier.clickable { onOpenCohort(cohortId) } else Modifier)
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(Modifier.size(36.dp).background(color, RoundedCornerShape(10.dp)))
                                    Column(Modifier.weight(1f)) {
                                        Text(c.name ?: c.id ?: "-", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                        val period = listOfNotNull(
                                            mem.since?.takeIf { it.isNotBlank() }?.let { "${AppStrings.current.profile.dateFrom}: ${formatDateStringSmall(it) ?: it}" },
                                            mem.until?.takeIf { it.isNotBlank() }?.let { "${AppStrings.current.profile.dateTo}: ${formatDateStringSmall(it) ?: it}" }
                                        ).joinToString(" · ")
                                        if (period.isNotBlank()) Text(period, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ČSTS Progress
            if (cstsProgressList.isNotEmpty()) {
                StaggeredItem(index = 4, visible = cardsVisible, baseDelayMs = 50) {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp), colors = surfaceCardColors(), border = cardLineBorder()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(AppStrings.current.competition.cstsProgress, style = MaterialTheme.typography.labelLarge)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            val byCompetitor = cstsProgressList.groupBy { it.competitorName?.takeIf { n -> n.isNotBlank() } ?: "" }
                            byCompetitor.entries.forEachIndexed { idx, (competitorName, entries) ->
                                if (idx > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                if (competitorName.isNotBlank()) {
                                    val coupleNames = competitorName.split(" - ", limit = 2).map { it.trim() }.takeIf { it.size == 2 }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (coupleNames != null) {
                                            CoupleAvatar(womanName = coupleNames[0], manName = coupleNames[1], size = 24.dp)
                                        } else {
                                            InitialsAvatar(name = competitorName, size = 24.dp)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(competitorName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                entries.forEach { entry ->
                                    val rawCat = entry.category?.name?.takeIf { it.isNotBlank() } ?: ""
                                    val catFormatted = AppStrings.current.competition.formatType(rawCat)
                                    val entryPoints = entry.points
                                    val entryFinals = entry.finals
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = catFormatted,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val pts = entryPoints?.toDoubleOrNull()
                                        if (pts != null && pts != 0.0) {
                                            val ptsStr = if (pts % 1.0 == 0.0) pts.toInt().toString() else { val s = kotlin.math.round(pts * 10).toLong(); "${s / 10}.${kotlin.math.abs(s % 10)}" }
                                            Row(
                                                modifier = Modifier
                                                    .padding(start = 4.dp)
                                                    .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "$ptsStr${AppStrings.current.competition.pointsSuffix}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                            }
                                        }
                                        if (entryFinals != null && entryFinals > 0) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(start = 4.dp)
                                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "${entryFinals}F",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // WDSF / federation data
            p.federationProfiles.firstOrNull { it.federation.equals(WDSF, ignoreCase = true) }?.let { prof ->
                StaggeredItem(index = 4, visible = cardsVisible, baseDelayMs = 50) { WdsfProfileCard(prof) }
            }
            if (wdsfProgressList.isNotEmpty()) {
                StaggeredItem(index = 4, visible = cardsVisible, baseDelayMs = 50) { WdsfProgressCard(wdsfProgressList) }
            }
            // Rankings card hidden for now: the ranklist data is not up to date (FederationRankingsCard is ready).
            if (p.federationCompetitions.isNotEmpty()) {
                StaggeredItem(index = 4, visible = cardsVisible, baseDelayMs = 50) { FederationCompetitionsCard(p.federationCompetitions, onOpenFederationEvent) }
            }
            // (duplicate UI removed)
            StaggeredItem(index = 5, visible = cardsVisible, baseDelayMs = 50) {
                Card(
                    colors = surfaceCardColors(),
                    border = cardLineBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onOpenAchievements(personId) },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppStrings.current.achievements.viewAchievementsAction,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
                }
            }
        }
    }


internal fun formatDateStringSmall(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val s = raw.trim()
    val datePrefix = ISO_DATE_PREFIX_REGEX.find(s)?.value
    val ld = if (datePrefix != null) {
        try { LocalDate.parse(datePrefix) } catch (_: Exception) { null }
    } else {
        parseToLocal(s)?.date
    } ?: return null
    return formatShortDate(ld)
}


/** One couple of a person: pair avatar, names, period and an active/past pill; opens the couple screen. */
@Composable
private fun CoupleRow(couple: CouplePeriod, onClick: (() -> Unit)?) {
    val strings = AppStrings.current.profile
    fun name(m: CoupleMember?) = listOfNotNull(m?.firstName, m?.lastName).joinToString(" ").takeIf { it.isNotBlank() }
    val manName = name(couple.man)
    val womanName = name(couple.woman)
    val title = listOfNotNull(manName, womanName).joinToString(" - ").ifBlank { "#${couple.id ?: "?"}" }
    val since = parseToLocal(couple.since)?.date?.let { formatShortDate(it) }
    val until = parseToLocal(couple.until)?.date?.let { formatShortDate(it) }
    val period = when {
        since != null && until != null -> "$since – $until"
        since != null -> "$since –"
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressClickable(0.99f, onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CoupleAvatar(
            womanName = womanName, manName = manName, size = 36.dp, fontSize = 13.sp,
            womanImageUrl = withImageWidth(couple.woman?.imageUrl, 96), manImageUrl = withImageWidth(couple.man?.imageUrl, 96)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (period != null) Text(period, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (couple.isActive) InfoPill(strings.coupleActive, successContainerColor(), successColor())
        else InfoPill(strings.couplePast, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
