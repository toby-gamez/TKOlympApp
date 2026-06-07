package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import com.tkolymp.tkolympapp.components.parseColorOrDefault
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.formatFullCalendarDate
import com.tkolymp.shared.utils.formatHtmlContent
import com.tkolymp.shared.viewmodels.OverviewViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.LessonView
import com.tkolymp.tkolympapp.components.RenderSingleEventCard
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    bottomPadding: Dp = 0.dp,
    onOpenEvent: (Long) -> Unit = {},
    onOpenNotice: (Long) -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenBoard: () -> Unit = {},
    onOpenEvents: () -> Unit = {},
    onOpenPerson: (String) -> Unit = {}
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

            if (state.upcomingEvents.isNotEmpty()) {
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
                    val dayLabels = remember { listOf(ps.mon, ps.tue, ps.wed, ps.thu, ps.fri, ps.sat, ps.sun) }
                    WeekPersonaBadge(
                        vibes = weekVibes,
                        personaLabel = personaLabel,
                        dayLabels = dayLabels,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // Trainings section
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    val date = state.trainingSelectedDate!!
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
                                    onEventClick = { id: Long -> onOpenEvent(id) }
                                )
                            }
                        }
                        val trainerCount = state.trainingLessonsByTrainer.size
                        state.trainingOtherEvents.forEachIndexed { i, item ->
                            StaggeredItem(index = trainerCount + i, visible = cardsVisible) {
                                RenderSingleEventCard(item = item, onEventClick = { id: Long -> onOpenEvent(id) })
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
            Spacer(modifier = Modifier.height(8.dp))

            // Board announcements
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(modifier = Modifier.height(8.dp))

            // Camps section
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                    RenderSingleEventCard(item = item, onEventClick = { id: Long -> onOpenEvent(id) })
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
            // Upcoming birthdays
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
            if (state.isLoading) {
                Text("Načítám...", modifier = Modifier.padding(12.dp))
            }
            state.error?.let { Text(text = it.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        }
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
