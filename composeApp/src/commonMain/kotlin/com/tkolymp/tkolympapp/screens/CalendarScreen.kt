

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import com.tkolymp.tkolympapp.components.LessonView
import com.tkolymp.tkolympapp.components.RenderSingleEventCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    weekOffset: Int = 0,
    onWeekOffsetChange: (Int) -> Unit = {},
    onOpenEvent: (Long) -> Unit = {},
    onNavigateTimeline: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onCreatePersonalEvent: (() -> Unit)? = null,
    onFindFreeLessons: (() -> Unit)? = null,
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
        },
        floatingActionButton = {
            val showFab = (onFindFreeLessons != null) || calState.hasCancelledMineToShow
            if (showFab) {
                Box(modifier = Modifier.padding(bottom = bottomPadding)) {
                    BadgedBox(
                        badge = {
                            if (calState.hasCancelledMineToShow) {
                                Badge(modifier = Modifier.size(20.dp))
                            }
                        }
                    ) {
                        FloatingActionButton(onClick = { onFindFreeLessons?.invoke() }) {
                            Icon(Icons.Default.Search, contentDescription = "Najít volné lekce")
                        }
                    }
                }
            }
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
                    val visibleDatesToRender = if (calState.visibleDates.isEmpty() && calState.isOffline && cachedVisibleDates.value != null) cachedVisibleDates.value.orEmpty() else calState.visibleDates
                    visibleDatesToRender.forEach { date ->
                        val lessonsByTrainer = if ((calState.lessonsByTrainerByDay[date].orEmpty().isEmpty()) && calState.isOffline && cachedLessonsByTrainer.value != null) cachedLessonsByTrainer.value?.getOrDefault(date, emptyMap()) ?: emptyMap() else calState.lessonsByTrainerByDay[date] ?: emptyMap()
                        val otherList = if ((calState.otherEventsByDay[date].orEmpty().isEmpty()) && calState.isOffline && cachedOtherEvents.value != null) cachedOtherEvents.value?.getOrDefault(date, emptyList()) ?: emptyList() else calState.otherEventsByDay[date] ?: emptyList()
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

// Note: helpers such as formatTimes, formatTimesWithDate, durationMinutes and translateEventType
// are expected to live in EventUtils.kt as in the original project.

// Note: helpers such as formatTimes, formatTimesWithDate, durationMinutes and translateEventType
// are expected to live in EventUtils.kt as in the original project.

