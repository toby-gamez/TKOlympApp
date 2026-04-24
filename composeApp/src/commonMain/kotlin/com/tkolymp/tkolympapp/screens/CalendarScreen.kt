

package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.durationMinutes
import com.tkolymp.shared.utils.formatFullCalendarDate
import com.tkolymp.shared.utils.formatTimes
import com.tkolymp.shared.utils.formatTimesWithDate
import com.tkolymp.shared.utils.translateEventType
import com.tkolymp.tkolympapp.SwipeToReload
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    weekOffset: Int = 0,
    onWeekOffsetChange: (Int) -> Unit = {},
    onOpenEvent: (Long) -> Unit = {},
    onNavigateTimeline: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onCreatePersonalEvent: (() -> Unit)? = null,
    bottomPadding: Dp = 0.dp
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var localWeekOffset by rememberSaveable { mutableIntStateOf(weekOffset) }
    val tabs = listOf(AppStrings.current.people.mine, AppStrings.current.commonActions.all)
    val calendarViewModel = viewModel<com.tkolymp.shared.viewmodels.CalendarViewModel>()
    val calState by calendarViewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    // Cache last non-empty offline data to avoid brief disappearance on transient empty state
    val cachedVisibleDates = remember { mutableStateOf<List<String>?>(null) }
    val cachedLessonsByTrainer = remember { mutableStateOf<Map<String, Map<String, List<EventInstance>>>?>(null) }
    val cachedOtherEvents = remember { mutableStateOf<Map<String, List<EventInstance>>?>(null) }

    // Update cache whenever we have offline data that's non-empty
    LaunchedEffect(calState.isOffline, calState.visibleDates, calState.lessonsByTrainerByDay, calState.otherEventsByDay) {
        if (calState.isOffline) {
            if (calState.visibleDates.isNotEmpty() && (calState.lessonsByTrainerByDay.isNotEmpty() || calState.otherEventsByDay.isNotEmpty())) {
                cachedVisibleDates.value = calState.visibleDates
                cachedLessonsByTrainer.value = calState.lessonsByTrainerByDay
                cachedOtherEvents.value = calState.otherEventsByDay
            }
        } else {
            // clear cache when online (we want fresh data)
            cachedVisibleDates.value = null
            cachedLessonsByTrainer.value = null
            cachedOtherEvents.value = null
        }
    }

    // Ensure we reload when the screen becomes visible again (e.g. returning from detail).
    // Ignore the first ON_RESUME fired during initial composition to avoid double-load.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val resumeSeen = remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (resumeSeen.value) {
                    // If we're offline, avoid reloading because an online fetch
                    // that returns an empty map can overwrite the offline data.
                    if (!calState.isOffline) {
                        scope.launch { calendarViewModel.load(localWeekOffset, selectedTab == 0) }
                    }
                } else {
                    resumeSeen.value = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // allow internal control of week offset when parent doesn't provide a handler
    LaunchedEffect(weekOffset) { if (localWeekOffset != weekOffset) localWeekOffset = weekOffset }

    LaunchedEffect(selectedTab, localWeekOffset) {
        calendarViewModel.load(localWeekOffset, selectedTab == 0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.current.navigation.calendar) },
                navigationIcon = {
                    onBack?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                        }
                    }
                },
                actions = {
                    if (onNavigateTimeline != null && onBack == null) {
                        FilterChip(
                            selected = false,
                            onClick = onNavigateTimeline,
                            label = { Text(AppStrings.current.onboarding.calendarViewTimeline) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.ViewTimeline,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    IconButton(onClick = {
                        localWeekOffset = localWeekOffset - 1
                        onWeekOffsetChange(localWeekOffset)
                    }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Předchozí týden")
                    }
                    TextButton(onClick = {
                        localWeekOffset = 0
                        onWeekOffsetChange(0)
                    }) { Text(AppStrings.current.timeline.today) }
                    IconButton(onClick = {
                        localWeekOffset = localWeekOffset + 1
                        onWeekOffsetChange(localWeekOffset)
                    }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Následující týden")
                    }
                }
            )
        }
    ) { padding ->
        SwipeToReload(
            isRefreshing = calState.isLoading,
            onRefresh = {
                scope.launch {
                    calendarViewModel.load(localWeekOffset, selectedTab == 0, forceRefresh = true)
                }
            },
            modifier = Modifier.padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {
                PrimaryTabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                Column(modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                ) {
                    val visibleDatesToRender = if (calState.visibleDates.isEmpty() && calState.isOffline && cachedVisibleDates.value != null) cachedVisibleDates.value!! else calState.visibleDates
                    visibleDatesToRender.forEach { date ->
                        val lessonsByTrainer = if ((calState.lessonsByTrainerByDay[date].orEmpty().isEmpty()) && calState.isOffline && cachedLessonsByTrainer.value != null) cachedLessonsByTrainer.value!!.getOrDefault(date, emptyMap()) else calState.lessonsByTrainerByDay[date] ?: emptyMap()
                        val otherList = if ((calState.otherEventsByDay[date].orEmpty().isEmpty()) && calState.isOffline && cachedOtherEvents.value != null) cachedOtherEvents.value!!.getOrDefault(date, emptyList()) else calState.otherEventsByDay[date] ?: emptyList()
                        if (lessonsByTrainer.isEmpty() && otherList.isEmpty()) return@forEach
                        Column(modifier = Modifier.padding(8.dp)) {
                            val header = when (date) {
                                calState.todayString -> AppStrings.current.timeline.today.lowercase()
                                calState.tomorrowString -> AppStrings.current.timeline.tomorrow.lowercase()
                                else -> {
                                    val ld = try { LocalDate.parse(date) } catch (_: Exception) { null }
                                    if (ld == null) date else {
                                        val todayYear = try { LocalDate.parse(calState.todayString).year } catch (_: Exception) { -1 }
                                        formatFullCalendarDate(ld, AppStrings.currentLanguage.code, ld.year != todayYear)
                                    }
                                }
                            }
                            Text(header, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))

                            lessonsByTrainer.forEach { (trainer, instances) ->
                                LessonView(
                                    trainerName = trainer,
                                    instances = instances,
                                    isAllTab = (selectedTab == 1),
                                    myPersonId = calState.myPersonId,
                                    myCoupleIds = calState.myCoupleIds,
                                    onEventClick = { id: Long -> onOpenEvent(id) }
                                )
                            }

                            otherList.forEach { item ->
                                RenderSingleEventCard(item = item, onEventClick = { id: Long -> onOpenEvent(id) })
                            }
                        }
                    }
                }
            }
        }

        calState.error?.let { err ->
            AlertDialog(
                onDismissRequest = { calendarViewModel.clearError() },
                confirmButton = {
                    TextButton(onClick = { calendarViewModel.clearError() }) { Text(AppStrings.current.commonActions.ok) }
                },
                title = { Text(AppStrings.current.events.errorLoadingEvents) },
                text = { Text(err) }
            )
        }
    }
}

internal fun parseColorOrDefault(hex: String?): Color {
    if (hex.isNullOrBlank()) return Color.Gray
    return try {
        var s = hex.trim().trimStart('#')
        if (s.length == 6) s = "FF$s"
        Color(s.toLong(16).toInt())
    } catch (_: Exception) {
        Color.Gray
    }
}

@Composable
internal fun LessonView(
    trainerName: String,
    instances: List<EventInstance>,
    isAllTab: Boolean,
    myPersonId: String?,
    myCoupleIds: List<String>,
    onEventClick: (Long) -> Unit
) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(trainerName, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                Text(AppStrings.current.eventCalendarTabs.lessonLabel, style = MaterialTheme.typography.labelSmall)
            }

            val groupLocation = instances.mapNotNull { inst ->
                inst.event?.locationText?.takeIf { !it.isNullOrBlank() } ?: inst.event?.location?.name?.takeIf { !it.isNullOrBlank() }
            }.firstOrNull().orEmpty()
            if (groupLocation.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(groupLocation, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(8.dp))

            instances.sortedBy { it.since }.forEach { inst ->
                val time = formatTimes(inst.since, inst.until)
                val regs = inst.event?.eventRegistrationsList ?: emptyList()
                                val parts: List<Pair<String, Boolean>> = regs.mapNotNull { r ->
                    val display = r.person?.name ?: run {
                        val man = r.couple?.man
                        val woman = r.couple?.woman
                        if (man != null && woman != null) {
                            val manSurname = man.lastName?.takeIf { it.isNotBlank() } ?: man.firstName?.takeIf { it.isNotBlank() } ?: ""
                            val womanSurname = woman.lastName?.takeIf { it.isNotBlank() } ?: woman.firstName?.takeIf { it.isNotBlank() } ?: ""
                            val pair = listOfNotNull(manSurname.takeIf { it.isNotBlank() }, womanSurname.takeIf { it.isNotBlank() }).joinToString(" - ")
                            if (pair.isNotBlank()) pair else null
                        } else null
                    }
                    if (display == null) null else {
                        val personIdStr = r.person?.id?.toString()
                        val coupleIdStr = r.couple?.id?.toString()
                        val isMine = (myPersonId != null && personIdStr == myPersonId) || (coupleIdStr != null && myCoupleIds.contains(coupleIdStr))
                        Pair(display, isMine)
                    }
                }
                val participantsEmpty = parts.isEmpty()
                val durationMin = durationMinutes(inst.since, inst.until)
                val deco = if (inst.isCancelled) TextDecoration.LineThrough else TextDecoration.None

                Row(modifier = Modifier.fillMaxWidth().clickable {
                    val evId = inst.event?.id ?: return@clickable
                    onEventClick(evId)
                }, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        time,
                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = deco),
                        modifier = Modifier.width(100.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        if (participantsEmpty) {
                            Text(
                                "VOLNO",
                                style = MaterialTheme.typography.bodySmall.copy(textDecoration = deco),
                                color = Color(0xFF4CAF50)
                            )
                        } else {
                                val annotated = buildAnnotatedString {
                                parts.forEachIndexed { idx, (display, isMine) ->
                                    if (isAllTab && isMine) {
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append(display) }
                                    } else append(display)
                                    if (idx != parts.lastIndex) append(", ")
                                }
                                }
                            Text(annotated, style = MaterialTheme.typography.bodySmall.copy(textDecoration = deco))
                        }
                    }

                    Text(
                        durationMin ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = deco),
                        modifier = Modifier.width(48.dp),
                        textAlign = TextAlign.End
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
internal fun RenderSingleEventCard(item: EventInstance, onEventClick: (Long) -> Unit, showType: Boolean = true) {
    val name = item.event?.name ?: "(no name)"
    val cancelled = item.isCancelled
    val eventObj = item.event
    val locationOrTrainer = listOfNotNull(
        eventObj?.locationText?.takeIf { !it.isNullOrBlank() },
        eventObj?.location?.name?.takeIf { !it.isNullOrBlank() },
        eventObj?.eventTrainersList?.firstOrNull()?.takeIf { !it.isNullOrBlank() }
    ).firstOrNull().orEmpty()
    val timeText = formatTimesWithDate(item.since, item.until)

    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .clickable { val evId = item.event?.id ?: return@clickable; onEventClick(evId) }
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium.copy(textDecoration = if (cancelled) TextDecoration.LineThrough else TextDecoration.None)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // Show personal training type / short subtitle if available (first line of description)
                    val subtitle = item.event?.description?.substringBefore('\n')?.takeIf { it.isNotBlank() }
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (locationOrTrainer.isNotBlank()) {
                        Text(locationOrTrainer, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (timeText.isNotBlank()) {
                        Text(timeText, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (showType) {
                        val typeText = item.event?.type ?: ""
                        val displayType = translateEventType(typeText)
                        if (!displayType.isNullOrBlank()) {
                            Text(displayType, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row {
                        val cohorts = item.event?.eventTargetCohortsList ?: emptyList()
                        val cohortColors = cohorts.mapNotNull { tc ->
                            val hex = tc.cohort?.colorRgb
                            if (hex.isNullOrBlank()) null else try {
                                parseColorOrDefault(hex)
                            } catch (_: Exception) { null }
                        }

                        if (cohortColors.isNotEmpty()) {
                            cohortColors.forEachIndexed { idx, color ->
                                Box(modifier = Modifier
                                    .size(12.dp)
                                    .background(color, CircleShape)
                                )
                                if (idx != cohortColors.lastIndex) Spacer(modifier = Modifier.width(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Note: helpers such as formatTimes, formatTimesWithDate, durationMinutes and translateEventType
// are expected to live in EventUtils.kt as in the original project.

