package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.tutorial.TutorialManager
import com.tkolymp.shared.utils.formatFullCalendarDate
import com.tkolymp.shared.utils.formatHtmlContent
import com.tkolymp.shared.viewmodels.OverviewViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.TutorialHighlight
import com.tkolymp.tkolympapp.components.LessonView
import com.tkolymp.tkolympapp.components.RenderSingleEventCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import com.tkolymp.shared.calendar.WeekPersona
import com.tkolymp.shared.calendar.WeekVibesData
import com.tkolymp.shared.calendar.computeWeekVibes
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.event.EventType
import com.tkolymp.shared.event.firstTrainerOrEmpty
import com.tkolymp.shared.event.toEventType
import com.tkolymp.tkolympapp.components.WeekPersonaBadge
import com.tkolymp.shared.competitions.Competition

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OverviewScreen(
    bottomPadding: Dp = 0.dp,
    onOpenEvent: (Long, Long?) -> Unit = { _, _ -> },
    onOpenNotice: (Long) -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenBoard: () -> Unit = {},
    onOpenEvents: () -> Unit = {},
    onOpenPerson: (String) -> Unit = {},
    onOpenCompetitions: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(AppStrings.current.navigation.overview) })
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        val viewModel = viewModel<OverviewViewModel>()
        val state by viewModel.state.collectAsState()
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            viewModel.loadOverview(forceRefresh = false)
        }

        var cardsVisible by remember { mutableStateOf(false) }
        LaunchedEffect(state.isLoading) { if (!state.isLoading) cardsVisible = true }

        val tutorialActive by TutorialManager.isActive.collectAsState()
        val tutorialStep by TutorialManager.currentStep.collectAsState()

        val bvrStats = remember { BringIntoViewRequester() }
        val bvrUpcoming = remember { BringIntoViewRequester() }
        val bvrBoard = remember { BringIntoViewRequester() }
        val bvrCamps = remember { BringIntoViewRequester() }
        val bvrCompetitions = remember { BringIntoViewRequester() }
        val bvrBirthdays = remember { BringIntoViewRequester() }

        // Always-fresh bounds per section — updated unconditionally by onGloballyPositioned
        var boundsStats by remember { mutableStateOf<Rect?>(null) }
        var boundsUpcoming by remember { mutableStateOf<Rect?>(null) }
        var boundsBoard by remember { mutableStateOf<Rect?>(null) }
        var boundsCamps by remember { mutableStateOf<Rect?>(null) }
        var boundsCompetitions by remember { mutableStateOf<Rect?>(null) }
        var boundsBirthdays by remember { mutableStateOf<Rect?>(null) }

        LaunchedEffect(tutorialStep, tutorialActive) {
            if (!tutorialActive || tutorialStep !in 1..6) return@LaunchedEffect
            val bvr = when (tutorialStep) {
                1 -> bvrUpcoming; 2 -> bvrBoard; 3 -> bvrCamps
                4 -> bvrCompetitions; 5 -> bvrBirthdays; else -> bvrStats
            }

            bvr.bringIntoView()
            // BVR suspends until the item is visible but layout takes another frame to settle
            delay(200)

            val bounds = when (tutorialStep) {
                1 -> boundsUpcoming; 2 -> boundsBoard; 3 -> boundsCamps
                4 -> boundsCompetitions; 5 -> boundsBirthdays; else -> boundsStats
            }
            if (bounds != null) TutorialHighlight.rect = bounds
        }

        SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch { viewModel.loadOverview(forceRefresh = true) } },
            modifier = Modifier.padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {

            val announcements = state.recentAnnouncements

            Column(
                modifier = Modifier
                    .bringIntoViewRequester(bvrStats)
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInRoot()
                        boundsStats = b
                        if (tutorialActive && tutorialStep == 6) TutorialHighlight.rect = b
                    }
            ) {
                if (state.isDancer && state.upcomingEvents.isNotEmpty()) {
                    val weekVibes = remember(state.upcomingEvents, state.todayString) {
                        computeOverviewWeekVibes(state.upcomingEvents, state.todayString)
                    }
                    if (weekVibes != null) {
                        val ps = AppStrings.current.weekPersona
                        val personaLabel = when (weekVibes.persona) {
                            WeekPersona.HUSTLE -> ps.hustle
                            WeekPersona.EASY -> ps.easy
                            WeekPersona.SPRINT -> ps.sprint
                            WeekPersona.MIX -> ps.mix
                            WeekPersona.SOCIAL -> ps.social
                            WeekPersona.CAMP -> ps.camp
                            WeekPersona.ALL_ROUNDER -> ps.allRounder
                        }
                        WeekPersonaBadge(
                            vibes = weekVibes,
                            personaLabel = personaLabel,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                if (state.isDancer && !state.isLoading) {
                    MiniStatsRow(
                        sessionCount = state.currentWeekCount,
                        minutes = state.currentWeekMinutes,
                        thisWeekLabel = AppStrings.current.stats.thisWeek,
                        sessionsUnit = AppStrings.current.stats.sessionsUnit,
                        hoursUnit = AppStrings.current.stats.hoursUnit,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }

                if (state.isDancer && state.weeklyGoal > 0) {
                    WeeklyGoalIndicator(
                        goal = state.weeklyGoal,
                        count = state.currentWeekCount,
                        thisWeekLabel = AppStrings.current.stats.thisWeek,
                        sessionsPerWeek = AppStrings.current.stats.sessionsPerWeek,
                        goalReached = AppStrings.current.stats.goalReached,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // Trainings section
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .bringIntoViewRequester(bvrUpcoming)
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInRoot()
                        boundsUpcoming = b
                        if (tutorialActive && tutorialStep == 1) TutorialHighlight.rect = b
                    }
            ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(AppStrings.current.overview.upcomingTrainings, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (state.trainingSelectedDate == null && !state.isLoading) {
                    Text(AppStrings.current.timeline.nothingPlanned, modifier = Modifier.padding(vertical = 6.dp))
                } else if (state.trainingSelectedDate == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                } else {
                    val date = state.trainingSelectedDate ?: return@Column
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        val header = dateHeader(date, state.todayString, state.tomorrowString)
                        Text(header, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))

                        state.trainingLessonsByTrainer.entries.toList().forEachIndexed { i, (trainer, instances) ->
                            StaggeredItem(index = i, visible = cardsVisible) {
                                LessonView(
                                    trainerName = trainer,
                                    instances = instances,
                                    isAllTab = false,
                                    myPersonId = state.myPersonId,
                                    myCoupleIds = state.myCoupleIds,
                                    onEventClick = { id, instId -> onOpenEvent(id, instId) }
                                )
                            }
                        }
                        val trainerCount = state.trainingLessonsByTrainer.size
                        state.trainingOtherEvents.forEachIndexed { i, item ->
                            StaggeredItem(index = trainerCount + i, visible = cardsVisible) {
                                RenderSingleEventCard(item = item, onEventClick = { id, instId -> onOpenEvent(id, instId) })
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onOpenCalendar) {
                    Text(if (state.trainingSelectedDate == null) AppStrings.current.overview.browseOthers else AppStrings.current.overview.more)
                }
            }
            } // Trainings section

            state.paymentDaysUntilDue?.let { days ->
                PaymentDueBanner(
                    daysUntilDue = days,
                    dueInTemplate = AppStrings.current.misc.paymentDueIn,
                    overdueLabel = AppStrings.current.misc.paymentOverdue,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Board announcements
            Column(
                modifier = Modifier
                    .bringIntoViewRequester(bvrBoard)
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInRoot()
                        boundsBoard = b
                        if (tutorialActive && tutorialStep == 2) TutorialHighlight.rect = b
                    }
            ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(AppStrings.current.overview.fromTheBoard, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (announcements.isEmpty()) {
                    if (state.isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) { CircularProgressIndicator() }
                    } else {
                        Text(AppStrings.current.timeline.nothingPlanned, modifier = Modifier.padding(vertical = 6.dp))
                    }
                } else {
                    announcements.forEachIndexed { i, a ->
                        StaggeredItem(index = i, visible = cardsVisible) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        a.id.toLongOrNull()?.let { nid -> onOpenNotice(nid) }
                                    },
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(a.title ?: AppStrings.current.dialogs.noName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    val authorName = listOfNotNull(a.author?.uJmeno, a.author?.uPrijmeni).joinToString(" ").trim()
                                    if (authorName.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(authorName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    var plainBody = formatHtmlContent(a.body ?: "")
                                    if (plainBody.isNotBlank()) plainBody = plainBody.trimEnd() + "..."
                                    Text(
                                        plainBody,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        } // StaggeredItem announcement
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onOpenBoard) {
                    Text(if (announcements.isEmpty()) AppStrings.current.overview.browseOthers else AppStrings.current.overview.more)
                }
            }
            } // Board section
            Spacer(modifier = Modifier.height(8.dp))

            // Camps section
            Column(
                modifier = Modifier
                    .bringIntoViewRequester(bvrCamps)
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInRoot()
                        boundsCamps = b
                        if (tutorialActive && tutorialStep == 3) TutorialHighlight.rect = b
                    }
            ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(AppStrings.current.overview.upcomingCamps, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (state.campsMapByDay.isEmpty()) {
                    if (state.isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) { CircularProgressIndicator() }
                    } else {
                        Text(AppStrings.current.timeline.nothingPlanned, modifier = Modifier.padding(vertical = 6.dp))
                    }
                } else {
                    state.campsMapByDay.entries.toList().forEachIndexed { i, (date, list) ->
                        StaggeredItem(index = i, visible = cardsVisible) {
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                val header = dateHeader(date, state.todayString, state.tomorrowString)
                                Text(header, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                list.forEach { item ->
                                    RenderSingleEventCard(item = item, onEventClick = { id, instId -> onOpenEvent(id, instId) })
                                }
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onOpenEvents) {
                    Text(if (state.campsMapByDay.isEmpty()) AppStrings.current.overview.browseOthers else AppStrings.current.overview.more)
                }
            }
            } // Camps section
            Spacer(modifier = Modifier.height(8.dp))
            // Nearest competition section
            Column(
                modifier = Modifier
                    .bringIntoViewRequester(bvrCompetitions)
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInRoot()
                        boundsCompetitions = b
                        if (tutorialActive && tutorialStep == 4) TutorialHighlight.rect = b
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(AppStrings.current.competition.nearestCompetition, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    val comp = state.nearestCompetition
                    if (comp == null) {
                        Text(AppStrings.current.competition.noUpcomingMine, modifier = Modifier.padding(vertical = 6.dp))
                    } else {
                        StaggeredItem(index = 0, visible = cardsVisible) {
                            NearestCompetitionCard(competition = comp, onOpenCompetitions = onOpenCompetitions)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onOpenCompetitions) {
                        Text(AppStrings.current.overview.more)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Upcoming birthdays
            Column(
                modifier = Modifier
                    .bringIntoViewRequester(bvrBirthdays)
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInRoot()
                        boundsBirthdays = b
                        if (tutorialActive && tutorialStep == 5) TutorialHighlight.rect = b
                    }
            ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(AppStrings.current.profile.birthdays, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (state.upcomingBirthdays.isEmpty()) {
                    Text(AppStrings.current.timeline.nothingPlanned, modifier = Modifier.padding(vertical = 6.dp))
                } else {
                    state.upcomingBirthdays.forEachIndexed { i, entry ->
                        StaggeredItem(index = i, visible = cardsVisible) {
                        val isBirthdayToday = entry.days == 0
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpenPerson(entry.personId) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val cohortColors = entry.cohortColors.mapNotNull { hex ->
                                    try { parseColorOrDefault(hex) } catch (_: Exception) { null }
                                }
                                Column(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(6.dp))
                                ) {
                                    if (cohortColors.isNotEmpty()) {
                                        cohortColors.forEach { color ->
                                            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(color))
                                        }
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary))
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                InitialsAvatar(name = entry.name, size = 36.dp, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        entry.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isBirthdayToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    entry.formattedBirthDate?.let {
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            if (isBirthdayToday) {
                                                Icon(
                                                    imageVector = Icons.Filled.Cake,
                                                    contentDescription = "Dnes mají narozeniny",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isBirthdayToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                if (!isBirthdayToday) {
                                    Text("${entry.days}d", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                        } // StaggeredItem birthday
                    }
                }
            }
            } // Birthdays section
            if (state.isLoading) {
                Text(AppStrings.current.commonActions.loading, modifier = Modifier.padding(12.dp))
            }
            state.error?.let { Text(text = it.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        }
    }
}

}

@Composable
private fun MiniStatsRow(
    sessionCount: Int,
    minutes: Long,
    thisWeekLabel: String,
    sessionsUnit: String,
    hoursUnit: String,
    modifier: Modifier = Modifier
) {
    val hours = minutes / 60
    val label = buildString {
        append("$thisWeekLabel: $sessionCount $sessionsUnit")
        if (hours > 0) append(" · $hours $hoursUnit")
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun WeeklyGoalIndicator(
    modifier: Modifier = Modifier,
    goal: Int,
    count: Int,
    thisWeekLabel: String,
    sessionsPerWeek: String,
    goalReached: String
) {
    val progress = (count.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
    val goalMet = count >= goal
    val green = androidx.compose.ui.graphics.Color(0xFF4CAF50)
    val progressColor = if (goalMet) green else MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$thisWeekLabel · $count / $goal $sessionsPerWeek",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (goalMet) {
                    Text(goalReached, style = MaterialTheme.typography.labelSmall, color = green, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
private fun PaymentDueBanner(
    daysUntilDue: Int,
    dueInTemplate: String,
    overdueLabel: String,
    modifier: Modifier = Modifier
) {
    val isOverdue = daysUntilDue < 0
    val containerColor = if (isOverdue)
        MaterialTheme.colorScheme.errorContainer
    else
        MaterialTheme.colorScheme.tertiaryContainer
    val contentColor = if (isOverdue)
        MaterialTheme.colorScheme.onErrorContainer
    else
        MaterialTheme.colorScheme.onTertiaryContainer
    val label = if (isOverdue) overdueLabel
    else dueInTemplate.replace("%d", daysUntilDue.toString())

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = contentColor)
        }
    }
}

private fun computeOverviewWeekVibes(
    events: List<EventInstance>,
    todayString: String
): WeekVibesData? {
    val today = try { LocalDate.parse(todayString) } catch (_: Exception) { return null }
    val visibleDates = (0..6).map { today.plus(it, DateTimeUnit.DAY).toString() }
    val weekEvents = events.filter { inst ->
        val ds = (inst.since ?: inst.until ?: "").substringBefore('T')
        ds in visibleDates
    }
    val grouped = weekEvents.groupBy { (it.since ?: it.until ?: "").substringBefore('T') }
    val lessons = mutableMapOf<String, Map<String, List<EventInstance>>>()
    val other = mutableMapOf<String, List<EventInstance>>()
    for ((date, list) in grouped) {
        val less = list.filter { isLessonRaw(it) }
        lessons[date] = less.groupBy { it.event.firstTrainerOrEmpty() }
        other[date] = (list - less.toSet()).sortedBy { it.since }
    }
    return computeWeekVibes(lessons, other, visibleDates)
}

private fun isLessonRaw(inst: EventInstance): Boolean {
    val ev = inst.event ?: return false
    return ev.type?.toEventType() == EventType.LESSON == true &&
        ev.eventTrainersList.isNotEmpty() &&
        !ev.eventTrainersList.firstOrNull().isNullOrBlank()
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
private fun NearestCompetitionCard(competition: Competition, onOpenCompetitions: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onOpenCompetitions() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = competition.eventName ?: competition.competitorName ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            val boxBg = MaterialTheme.colorScheme.surfaceVariant
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                competition.eventLocation?.takeIf { it.isNotBlank() }?.let { loc ->
                    InfoChip(icon = Icons.Default.Place, text = loc, background = boxBg)
                }
                InfoChip(icon = Icons.Default.CalendarMonth, text = formatCompetitionDate(competition.competitionDate), background = boxBg)
            }

            CompetitionEntryRow(competition, showCategory = false, showCheckIn = false)
        }
    }
}

