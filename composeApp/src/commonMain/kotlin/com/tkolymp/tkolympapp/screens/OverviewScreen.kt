package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import com.tkolymp.tkolympapp.components.SectionHeader
import com.tkolymp.tkolympapp.components.timelineInfo
import com.tkolymp.tkolympapp.components.campDateRange
import com.tkolymp.tkolympapp.components.campMonth
import com.tkolymp.tkolympapp.components.isRegisteredBy
import com.tkolymp.tkolympapp.components.pressClickable
import com.tkolymp.tkolympapp.components.SectionLabel
import com.tkolymp.tkolympapp.components.CountdownCard
import com.tkolymp.tkolympapp.components.cardLineBorder
import com.tkolymp.tkolympapp.components.surfaceCardColors
import com.tkolymp.tkolympapp.ui.theme.BrandLightPrimary
import com.tkolymp.tkolympapp.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.tkolymp.tkolympapp.components.EmptyState
import com.tkolymp.tkolympapp.components.InitialsAvatar
import com.tkolymp.tkolympapp.components.parseColorOrDefault
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.tkolymp.tkolympapp.components.AppTopBar
import com.tkolymp.tkolympapp.components.CampCard
import com.tkolymp.tkolympapp.components.InfoBanner
import com.tkolymp.tkolympapp.components.TimelineDot
import com.tkolymp.tkolympapp.components.TimelineRow
import com.tkolymp.tkolympapp.components.successColor
import com.tkolymp.tkolympapp.components.successContainerColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import com.tkolymp.tkolympapp.util.StaggeredItem
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.tutorial.TutorialManager
import com.tkolymp.shared.utils.formatFullCalendarDate
import com.tkolymp.shared.utils.formatHtmlContent
import com.tkolymp.shared.utils.parseToLocal
import com.tkolymp.shared.utils.translateEventType
import com.tkolymp.shared.utils.durationMinutes
import com.tkolymp.shared.utils.getLocalizedMonthName
import kotlinx.datetime.todayIn
import kotlinx.datetime.number
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.outlined.EventAvailable
import com.tkolymp.shared.viewmodels.BirthdayEntry
import com.tkolymp.shared.viewmodels.OverviewViewModel
import com.tkolymp.shared.viewmodels.minutesUntil
import com.tkolymp.shared.utils.formatTimes
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.event.firstTrainerOrEmpty
import kotlin.time.Clock
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.TutorialHighlight
import com.tkolymp.tkolympapp.components.LessonView
import com.tkolymp.tkolympapp.components.RenderSingleEventCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import com.tkolymp.shared.competitivedata.FederationEventSummary
import com.tkolymp.tkolympapp.components.ErrorBanner
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.EmojiEvents

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OverviewScreen(
    bottomPadding: Dp = 0.dp,
    onOpenEvent: (Long, Long?) -> Unit = { _, _ -> },
    onOpenRozpis: (Long, Long?) -> Unit = { _, _ -> },
    onOpenNotice: (Long) -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenBoard: () -> Unit = {},
    onOpenEvents: () -> Unit = {},
    onOpenPerson: (String) -> Unit = {},
    onOpenCompetitions: () -> Unit = {},
    onOpenPayments: () -> Unit = {},
    onOpenFederationEvent: (Long) -> Unit = {}
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = AppStrings.current.navigation.overview,
                subtitle = remember { overviewSubtitle() }
            )
        }
    ) { padding ->
        val listState = rememberLazyListState()
        val viewModel = viewModel<OverviewViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            viewModel.loadOverview(forceRefresh = false)
        }

        // Already-loaded content (returning to this screen) shows immediately instead of replaying the fade-in.
        var cardsVisible by remember { mutableStateOf(state.hasContent) }
        LaunchedEffect(state.isLoading) { if (!state.isLoading) cardsVisible = true }

        val tutorialActive by TutorialManager.isActive.collectAsStateWithLifecycle()
        val tutorialStep by TutorialManager.currentStep.collectAsStateWithLifecycle()

        // A category without items is not shown at all; while loading it stays so its spinner can appear.
        val sectionKeys = buildList {
            if (state.trainingSelectedDate != null || state.nextTraining != null || state.isLoading) add("trainings")
            if (state.paymentDaysUntilDue != null) add("payment")
            if (state.recentAnnouncements.isNotEmpty() || state.isLoading) add("board")
            if (state.campsMapByDay.isNotEmpty() || state.isLoading) add("camps")
            if (state.nearestCompetition != null || state.isCompetitionLoading) add("competition")
            if (state.upcomingBirthdays.isNotEmpty()) add("birthdays")
        }

        // Always-fresh bounds per section — updated unconditionally by onGloballyPositioned
        var boundsUpcoming by remember { mutableStateOf<Rect?>(null) }
        var boundsBoard by remember { mutableStateOf<Rect?>(null) }
        var boundsCamps by remember { mutableStateOf<Rect?>(null) }
        var boundsCompetitions by remember { mutableStateOf<Rect?>(null) }
        var boundsBirthdays by remember { mutableStateOf<Rect?>(null) }

        LaunchedEffect(tutorialStep, tutorialActive) {
            if (!tutorialActive || tutorialStep !in 1..5) return@LaunchedEffect
            // Sections are lazy items, so scroll to one (composing it) before reading its bounds.
            // Hidden (empty) sections are skipped, so the item index is looked up by key.
            val index = sectionKeys.indexOf(
                when (tutorialStep) { 1 -> "trainings"; 2 -> "board"; 3 -> "camps"; 4 -> "competition"; else -> "birthdays" }
            )
            if (index < 0) return@LaunchedEffect
            listState.animateScrollToItem(index)
            // layout takes another frame to settle after the scroll
            delay(200)

            val bounds = when (tutorialStep) {
                1 -> boundsUpcoming; 2 -> boundsBoard; 3 -> boundsCamps
                4 -> boundsCompetitions; else -> boundsBirthdays
            }
            if (bounds != null) TutorialHighlight.rect = bounds
        }

        SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch { viewModel.loadOverview(forceRefresh = true) } },
            modifier = Modifier.padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {

            val announcements = state.recentAnnouncements

            // Trainings section
            if ("trainings" in sectionKeys) item(key = "trainings") {
            state.nextTraining?.let { next ->
                NextTrainingHero(
                    instance = next,
                    onClick = { next.event?.id?.let { onOpenEvent(it, next.id) } },
                    modifier = Modifier.padding(start = Spacing.default, end = Spacing.default, top = 14.dp)
                )
            }
            Column(
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInRoot()
                        boundsUpcoming = b
                        if (tutorialActive && tutorialStep == 1) TutorialHighlight.rect = b
                    }
            ) {
            SectionLabel(
                text = state.trainingSelectedDate?.let { dateHeader(it, state.todayString, state.tomorrowString) }
                    ?: AppStrings.current.overview.upcomingTrainings,
                actionLabel = AppStrings.current.navigation.calendar,
                onAction = onOpenCalendar
            )

            Column(modifier = Modifier.padding(horizontal = Spacing.medium)) {
                if (state.trainingSelectedDate == null && !state.isLoading) {
                    EmptyState(title = AppStrings.current.timeline.nothingPlanned, icon = Icons.Outlined.EventBusy)
                } else if (state.trainingSelectedDate == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                } else {
                    val date = state.trainingSelectedDate ?: return@Column
                    val dayItems = remember(state.trainingLessonsByTrainer, state.trainingOtherEvents) {
                        (state.trainingLessonsByTrainer.values.flatten() + state.trainingOtherEvents).sortedBy { it.since ?: "" }
                    }
                    Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
                        val primary = MaterialTheme.colorScheme.primary
                        val muted = MaterialTheme.colorScheme.onSurfaceVariant
                        val ok = successColor()
                        val freeLabel = AppStrings.current.overview.freeLesson
                        dayItems.forEachIndexed { i, inst ->
                            val info = timelineInfo(inst, state.myPersonId, state.myCoupleIds)
                            StaggeredItem(index = i, visible = cardsVisible) {
                                TimelineRow(
                                    time = formatTimes(inst.since, null),
                                    dot = when {
                                        info.isFree -> TimelineDot.Free
                                        info.cohortColor != null -> TimelineDot.Filled(info.cohortColor)
                                        info.isMine -> TimelineDot.Filled(primary)
                                        else -> TimelineDot.Filled(muted)
                                    },
                                    title = if (info.isFree) freeLabel else info.title,
                                    subtitle = info.subtitle,
                                    isFirst = i == 0,
                                    isLast = i == dayItems.lastIndex,
                                    onClick = { inst.event?.id?.let { onOpenEvent(it, inst.id) } },
                                    titleColor = if (info.isFree) ok else MaterialTheme.colorScheme.onSurface,
                                    strikeThrough = inst.isCancelled
                                )
                            }
                        }
                        val freeCount = dayItems.count { timelineInfo(it, state.myPersonId, state.myCoupleIds).isFree }
                        if (freeCount > 0) {
                            InfoBanner(
                                icon = Icons.Outlined.EventAvailable,
                                text = AppStrings.current.overview.freeLessonsToday.replace("%d", freeCount.toString()),
                                containerColor = successContainerColor(),
                                contentColor = ok,
                                modifier = Modifier.padding(start = 66.dp, top = Spacing.xs),
                                onClick = onOpenCalendar
                            )
                        }
                    }
                }
            }
            } // Trainings section
            }

            state.paymentDaysUntilDue?.let { days ->
                item(key = "payment") {
                    PaymentDueBanner(
                        daysUntilDue = days,
                        dueInTemplate = AppStrings.current.misc.paymentDueIn,
                        overdueLabel = AppStrings.current.misc.paymentOverdue,
                        payLabel = AppStrings.current.overview.pay,
                        onPay = onOpenPayments,
                        modifier = Modifier.padding(start = Spacing.default, end = Spacing.default, top = Spacing.default)
                    )
                }
            }

            // Board announcements
            if ("board" in sectionKeys) item(key = "board") {
            Column(
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInRoot()
                        boundsBoard = b
                        if (tutorialActive && tutorialStep == 2) TutorialHighlight.rect = b
                    }
            ) {
            SectionHeader(
                title = AppStrings.current.overview.fromTheBoard,
                actionLabel = if (announcements.isEmpty()) AppStrings.current.overview.browseOthers else AppStrings.current.overview.more,
                onAction = onOpenBoard
            )
            val plainBodies = remember(announcements) {
                announcements.associate { a -> a.id to formatHtmlContent(a.body ?: "").trimEnd() }
            }
            Column {
                if (announcements.isEmpty()) {
                    if (state.isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) { CircularProgressIndicator() }
                    } else {
                        EmptyState(title = AppStrings.current.timeline.nothingPlanned, icon = Icons.Outlined.EventBusy)
                    }
                } else {
                    val newsListState = rememberLazyListState()
                    LazyRow(
                        state = newsListState,
                        flingBehavior = rememberSnapFlingBehavior(newsListState),
                        contentPadding = PaddingValues(horizontal = Spacing.default),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(announcements, key = { _, a -> a.id }) { i, a ->
                            StaggeredItem(index = i, visible = cardsVisible) {
                                val authorName = listOfNotNull(a.author?.uJmeno, a.author?.uPrijmeni).joinToString(" ").trim()
                                Card(
                                    modifier = Modifier
                                        .width(262.dp)
                                        .pressClickable { a.id.toLongOrNull()?.let { nid -> onOpenNotice(nid) } },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = surfaceCardColors(),
                                    border = cardLineBorder()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        if (authorName.isNotEmpty()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                InitialsAvatar(name = authorName, size = 22.dp, fontSize = 9.sp)
                                                Spacer(modifier = Modifier.width(Spacing.small))
                                                Text(
                                                    authorName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                        }
                                        Text(
                                            a.title ?: AppStrings.current.dialogs.noName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(Spacing.xs))
                                        val plainBody = (plainBodies[a.id] ?: "").let { if (it.isNotBlank()) "$it..." else it }
                                        Text(
                                            plainBody,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            } // Board section
            }

            // Camps section
            if ("camps" in sectionKeys) item(key = "camps") {
            Column(
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInRoot()
                        boundsCamps = b
                        if (tutorialActive && tutorialStep == 3) TutorialHighlight.rect = b
                    }
            ) {
            SectionHeader(
                title = AppStrings.current.overview.upcomingCamps,
                actionLabel = if (state.campsMapByDay.isEmpty()) AppStrings.current.overview.browseOthers else AppStrings.current.overview.more,
                onAction = onOpenEvents
            )
            Column(modifier = Modifier.padding(horizontal = Spacing.medium)) {
                if (state.campsMapByDay.isEmpty()) {
                    if (state.isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) { CircularProgressIndicator() }
                    } else {
                        EmptyState(title = AppStrings.current.timeline.nothingPlanned, icon = Icons.Outlined.EventBusy)
                    }
                } else {
                    val campsMapList = remember(state.campsMapByDay) { state.campsMapByDay.entries.toList() }
                    val handleCampEventClick = remember(onOpenEvent) { { id: Long, instId: Long? -> onOpenEvent(id, instId) } }
                    campsMapList.forEachIndexed { i, (date, list) ->
                        StaggeredItem(index = i, visible = cardsVisible) {
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                val dayBirthdays = state.birthdaysByDay[date] ?: emptyList()
                                if (dayBirthdays.isNotEmpty()) {
                                    DateHeaderWithBirthdays(dateHeader(date, state.todayString, state.tomorrowString), dayBirthdays)
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                }
                                list.forEach { item ->
                                    CampCard(
                                        cohortColors = remember(item) {
                                            item.event?.eventTargetCohortsList.orEmpty().mapNotNull { tc ->
                                                tc.cohort?.colorRgb?.takeIf { it.isNotBlank() }?.let { hex -> try { parseColorOrDefault(hex) } catch (_: Exception) { null } }
                                            }
                                        },
                                        dateBig = campDateRange(item.since, item.until),
                                        dateSmall = campMonth(item.since),
                                        name = item.event?.name.orEmpty(),
                                        place = listOfNotNull(
                                            translateEventType(item.event?.type),
                                            item.event?.locationText?.takeIf { it.isNotBlank() } ?: item.event?.location?.name?.takeIf { it.isNotBlank() }
                                        ).joinToString(" · "),
                                        registeredLabel = if (isRegisteredBy(item, state.myPersonId, state.myCoupleIds)) AppStrings.current.registration.signedUp else null,
                                        onClick = { item.event?.id?.let { handleCampEventClick(it, item.id) } },
                                        modifier = Modifier.padding(vertical = Spacing.xs)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            } // Camps section
            }
            // Nearest competition section
            if ("competition" in sectionKeys) item(key = "competition") {
            Column(
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInRoot()
                        boundsCompetitions = b
                        if (tutorialActive && tutorialStep == 4) TutorialHighlight.rect = b
                    }
            ) {
                SectionHeader(
                    title = AppStrings.current.competition.nearestCompetition,
                    actionLabel = AppStrings.current.overview.more,
                    onAction = onOpenCompetitions
                )
                Column(modifier = Modifier.padding(horizontal = Spacing.medium)) {
                    val comp = state.nearestCompetition
                    if (comp == null && state.isCompetitionLoading) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(AppStrings.current.competition.loadingCompetitions, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else if (comp == null) {
                        EmptyState(title = AppStrings.current.competition.noUpcoming, icon = Icons.Outlined.EmojiEvents)
                    } else {
                        StaggeredItem(index = 0, visible = cardsVisible) {
                            NearestCompetitionCard(event = comp, onOpen = { onOpenFederationEvent(comp.id) })
                        }
                    }
                }
            }
            }
            // Upcoming birthdays
            if ("birthdays" in sectionKeys) item(key = "birthdays") {
            Column(
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInRoot()
                        boundsBirthdays = b
                        if (tutorialActive && tutorialStep == 5) TutorialHighlight.rect = b
                    }
            ) {
            SectionHeader(title = AppStrings.current.profile.birthdays)
            Column(modifier = Modifier.padding(horizontal = Spacing.default)) {
                if (state.upcomingBirthdays.isEmpty()) {
                    EmptyState(title = AppStrings.current.timeline.nothingPlanned, icon = Icons.Outlined.EventBusy)
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = surfaceCardColors(),
                        border = cardLineBorder()
                    ) {
                    state.upcomingBirthdays.forEachIndexed { i, entry ->
                        StaggeredItem(index = i, visible = cardsVisible) {
                        val isBirthdayToday = entry.days == 0
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isBirthdayToday) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent)
                                    .clickable { onOpenPerson(entry.personId) }
                                    .padding(horizontal = Spacing.default, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val cohortColors = remember(entry.cohortColors) {
                                    entry.cohortColors.mapNotNull { hex ->
                                        try { parseColorOrDefault(hex) } catch (_: Exception) { null }
                                    }
                                }
                                // Cohort-coloured ring around the avatar (first cohort colour, primary as fallback)
                                val ringColor = cohortColors.firstOrNull() ?: MaterialTheme.colorScheme.primary
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .border(2.dp, ringColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    InitialsAvatar(name = entry.name, size = 32.dp, fontSize = 12.sp, imageUrl = entry.imageUrl)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        entry.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val yearPart = entry.birthYear?.toString() ?: ""
                                        val agePart = entry.turningAge?.let { AppStrings.current.profile.turnsAge.replace("%d", it.toString()) } ?: ""
                                        val subtitle = listOf(yearPart, agePart).filter { it.isNotEmpty() }.joinToString(" · ")
                                        if (subtitle.isNotEmpty()) {
                                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
                                        }
                                    }
                                }
                                if (isBirthdayToday) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text(AppStrings.current.timeline.today, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    Text(
                                        AppStrings.current.overview.inDays.replace("%d", entry.days.toString()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = Spacing.small)
                                    )
                                }
                            }
                        } // StaggeredItem birthday
                    }
                    } // Card
                }
            }
            } // Birthdays section
            }
            if (sectionKeys.isEmpty() && !state.isLoading && state.error == null) {
                item(key = "empty") {
                    EmptyState(title = AppStrings.current.timeline.nothingPlanned, icon = Icons.Outlined.EventBusy)
                }
            }
            if (state.isLoading || state.error != null) {
                item(key = "status") {
                    if (state.isLoading) {
                        Text(AppStrings.current.commonActions.loading, modifier = Modifier.padding(12.dp))
                    }
                    state.error?.let { ErrorBanner(error = it) }
                }
            }
        }
    }
}

}

/** Primary-coloured "next training" card with a countdown, as in the 1b prototype. */
@Composable
private fun NextTrainingHero(instance: EventInstance, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val strings = AppStrings.current.overview
    val countdown = minutesUntil(instance.since, Clock.System.now())?.let { m ->
        if (m >= 60) strings.inHoursMinutes.replaceFirst("%d", (m / 60).toString()).replaceFirst("%d", (m % 60).toString())
        else strings.inMinutes.replace("%d", m.toString())
    }
    val event = instance.event
    val trainer = event.firstTrainerOrEmpty()
    val heading = event?.name?.takeIf { it.isNotBlank() } ?: trainer
    val place = event?.locationText?.takeIf { it.isNotBlank() } ?: event?.location?.name?.takeIf { it.isNotBlank() }
    val meta = listOfNotNull(place, trainer.takeIf { it.isNotEmpty() && it != heading }).joinToString(" · ")

    Card(
        modifier = modifier.fillMaxWidth().pressClickable(0.985f, onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BrandLightPrimary, contentColor = Color.White)
    ) {
        Box {
        Box(
            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).size(36.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.ArrowOutward, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)) {
            Text(
                listOfNotNull(strings.nextTraining, countdown).joinToString(" · ").uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(Spacing.small))
            Text(
                formatTimes(instance.since, instance.until).replace(" - ", " – "),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.medium))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trainer.isNotEmpty()) {
                    InitialsAvatar(name = trainer, size = 30.dp, fontSize = 12.sp, imageUrl = event?.eventTrainerImageUrls?.firstOrNull())
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(heading, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        }
    }
}

@Composable
private fun PaymentDueBanner(
    daysUntilDue: Int,
    dueInTemplate: String,
    overdueLabel: String,
    payLabel: String,
    onPay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOverdue = daysUntilDue < 0
    InfoBanner(
        icon = Icons.Filled.Payments,
        text = if (isOverdue) overdueLabel else dueInTemplate.replace("%d", daysUntilDue.toString()),
        containerColor = if (isOverdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (isOverdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier,
        action = payLabel,
        onClick = onPay
    )
}

@Composable
private fun DateHeaderWithBirthdays(header: String, birthdays: List<BirthdayEntry>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(header, style = MaterialTheme.typography.titleMedium)
        if (birthdays.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Cake,
                contentDescription = AppStrings.current.profile.birthdays,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = birthdays.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

private fun dateHeader(date: String, todayString: String, tomorrowString: String): String {
    return when (date) {
        todayString -> AppStrings.current.timeline.today.lowercase()
        tomorrowString -> AppStrings.current.timeline.tomorrow.lowercase()
        else -> {
            val ld = try { LocalDate.parse(date) } catch (_: Exception) { null }
            if (ld == null) date else {
                val todayYear = try { LocalDate.parse(todayString).year } catch (_: Exception) { -1 }
                formatFullCalendarDate(ld, AppStrings.currentLanguage.code, ld.year != todayYear)
            }
        }
    }
}

@Composable
private fun NearestCompetitionCard(event: FederationEventSummary, onOpen: () -> Unit) {
    val strings = AppStrings.current
    val start = try { LocalDate.parse(event.startDate.take(10)) } catch (_: Exception) { null }
    val today = kotlin.time.Clock.System.todayIn(kotlinx.datetime.TimeZone.currentSystemDefault())
    val days = start?.let { (it.toEpochDays() - today.toEpochDays()).coerceAtLeast(0) }?.toString().orEmpty()
    val date = start?.let { "${it.day}. ${it.month.number}." }.orEmpty()
    val where = listOfNotNull(event.place, event.country).distinct().joinToString(", ")
    CountdownCard(
        days = days,
        daysUnit = strings.overview.daysUnit,
        title = event.name ?: "—",
        meta = listOf(date, where).filter { it.isNotBlank() }.joinToString(" · "),
        footnote = event.competitionCount.takeIf { it > 0 }?.let { "${strings.danceOrg.competitionsCount}: $it" },
        onClick = onOpen,
        strikeThrough = event.isCancelled
    )
}

private fun overviewSubtitle(): String {
    val today = kotlin.time.Clock.System.todayIn(kotlinx.datetime.TimeZone.currentSystemDefault())
    return formatFullCalendarDate(today, AppStrings.currentLanguage.code, false)
}
