
package com.tkolymp.tkolympapp.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.formatFullCalendarDate
import com.tkolymp.tkolympapp.SwipeToReload
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
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
    val today = remember { kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    // null → floating today-based view; non-null → a specific Monday chosen from the picker
    var customStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

    // Cache last non-empty offline data to avoid brief disappearance on transient empty state
    val cachedVisibleDates = remember { mutableStateOf<List<String>?>(null) }
    val cachedLessonsByTrainer = remember { mutableStateOf<Map<String, Map<String, List<EventInstance>>>?>(null) }
    val cachedOtherEvents = remember { mutableStateOf<Map<String, List<EventInstance>>?>(null) }

    LaunchedEffect(calState.isOffline, calState.visibleDates, calState.lessonsByTrainerByDay, calState.otherEventsByDay) {
        if (calState.isOffline) {
            if (calState.visibleDates.isNotEmpty() && (calState.lessonsByTrainerByDay.isNotEmpty() || calState.otherEventsByDay.isNotEmpty())) {
                cachedVisibleDates.value = calState.visibleDates
                cachedLessonsByTrainer.value = calState.lessonsByTrainerByDay
                cachedOtherEvents.value = calState.otherEventsByDay
            }
        } else {
            cachedVisibleDates.value = null
            cachedLessonsByTrainer.value = null
            cachedOtherEvents.value = null
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val resumeSeen = remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (resumeSeen.value) {
                    scope.launch { calendarViewModel.load(localWeekOffset, selectedTab == 0, weekStartOverride = customStartDate) }
                } else {
                    resumeSeen.value = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(weekOffset) { if (localWeekOffset != weekOffset) localWeekOffset = weekOffset }

    LaunchedEffect(selectedTab, localWeekOffset, customStartDate) {
        calendarViewModel.load(localWeekOffset, selectedTab == 0, weekStartOverride = customStartDate)
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
                    Box(modifier = Modifier.padding(end = 4.dp)) {
                        BadgedBox(
                            badge = {
                                if (calState.hasCancelledMineToShow) {
                                    Badge(modifier = Modifier.size(8.dp))
                                }
                            }
                        ) {
                            IconButton(onClick = { showBottomSheet = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        SwipeToReload(
            isRefreshing = calState.isLoading,
            onRefresh = {
                scope.launch {
                    calendarViewModel.load(localWeekOffset, selectedTab == 0, forceRefresh = true, weekStartOverride = customStartDate)
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

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    val visibleDatesToRender = if (calState.visibleDates.isEmpty() && calState.isOffline && cachedVisibleDates.value != null)
                        cachedVisibleDates.value.orEmpty() else calState.visibleDates
                    visibleDatesToRender.forEach { date ->
                        val lessonsByTrainer = if ((calState.lessonsByTrainerByDay[date].orEmpty().isEmpty()) && calState.isOffline && cachedLessonsByTrainer.value != null)
                            cachedLessonsByTrainer.value?.getOrDefault(date, emptyMap()) ?: emptyMap()
                        else calState.lessonsByTrainerByDay[date] ?: emptyMap()
                        val otherList = if ((calState.otherEventsByDay[date].orEmpty().isEmpty()) && calState.isOffline && cachedOtherEvents.value != null)
                            cachedOtherEvents.value?.getOrDefault(date, emptyList()) ?: emptyList()
                        else calState.otherEventsByDay[date] ?: emptyList()
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
                text = { Text(err.message) }
            )
        }
    }

    if (showBottomSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            CalendarBottomSheetContent(
                today = today,
                currentWeekStart = customStartDate ?: today.plus(localWeekOffset * 7, DateTimeUnit.DAY),
                onNavigateTimeline = onNavigateTimeline?.let { nav ->
                    { showBottomSheet = false; nav() }
                },
                onFindFreeLessons = onFindFreeLessons?.let { finder ->
                    { showBottomSheet = false; finder() }
                },
                onWeekSelected = { monday ->
                    customStartDate = monday
                },
                onReset = {
                    customStartDate = null
                    localWeekOffset = 0
                    onWeekOffsetChange(0)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarBottomSheetContent(
    today: LocalDate,
    currentWeekStart: LocalDate,         // actual start of the currently shown period
    onNavigateTimeline: (() -> Unit)?,
    onFindFreeLessons: (() -> Unit)?,
    onWeekSelected: (LocalDate) -> Unit, // receives Monday of the tapped week
    onReset: () -> Unit
) {
    var displayMonth by remember(currentWeekStart) {
        mutableStateOf(LocalDate(currentWeekStart.year, currentWeekStart.month, 1))
    }

    // All sections animate in together
    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showContent = true }

    val enterAnim = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 4 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── View switcher ──────────────────────────────────────────────────────
        AnimatedVisibility(visible = showContent && onNavigateTimeline != null, enter = enterAnim) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = AppStrings.current.calendarView.calendarOptionsTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text(AppStrings.current.settings.calendarViewList) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { onNavigateTimeline!!() },
                        label = { Text(AppStrings.current.onboarding.calendarViewTimeline) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.ViewTimeline,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize)
                            )
                        }
                    )
                }
                HorizontalDivider()
            }
        }

        // ── Lesson finder ──────────────────────────────────────────────────────
        AnimatedVisibility(visible = showContent && onFindFreeLessons != null, enter = enterAnim) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = { onFindFreeLessons!!() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        AppStrings.current.freeLessons.findButton,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                HorizontalDivider()
            }
        }

        // ── Timestamp / week selector ──────────────────────────────────────────
        AnimatedVisibility(visible = showContent, enter = enterAnim) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Month navigation header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        displayMonth = displayMonth.minus(DatePeriod(months = 1))
                    }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = AppStrings.current.calendarView.previous)
                    }
                    Text(
                        text = "${displayMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${displayMonth.year}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = {
                        displayMonth = displayMonth.plus(DatePeriod(months = 1))
                    }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = AppStrings.current.calendarView.next)
                    }
                }

                // Day-of-week header row
                Row(modifier = Modifier.fillMaxWidth()) {
                    AppStrings.current.calendarView.weekDayAbbreviations.forEach { label ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Month grid with slide transition on month change
                AnimatedContent(
                    targetState = displayMonth,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(tween(300)) { it } + fadeIn(tween(200))) togetherWith
                                    (slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(200)))
                        } else {
                            (slideInHorizontally(tween(300)) { -it } + fadeIn(tween(200))) togetherWith
                                    (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200)))
                        }
                    },
                    label = "month_transition"
                ) { month ->
                    MonthCalendarGrid(
                        displayMonth = month,
                        today = today,
                        selectedWeekStart = currentWeekStart,
                        onDayClick = { day ->
                            val monday = day.plus(-day.dayOfWeek.ordinal, DateTimeUnit.DAY)
                            onWeekSelected(monday)
                        }
                    )
                }

                // Reset to current week
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(AppStrings.current.calendarView.resetToToday)
                }
            }
        }
    }
}

@Composable
private fun MonthCalendarGrid(
    displayMonth: LocalDate,
    today: LocalDate,
    selectedWeekStart: LocalDate,
    onDayClick: (LocalDate) -> Unit
) {
    val selectedWeekEnd = remember(selectedWeekStart) { selectedWeekStart.plus(6, DateTimeUnit.DAY) }

    val firstDayOfMonth = LocalDate(displayMonth.year, displayMonth.month, 1)
    val dayOfWeekOffset = firstDayOfMonth.dayOfWeek.ordinal  // 0 = Monday … 6 = Sunday
    val firstCellDate = firstDayOfMonth.plus(-dayOfWeekOffset, DateTimeUnit.DAY)

    // Capture theme colors here — they can't be read inside drawBehind (DrawScope)
    val barColor = MaterialTheme.colorScheme.primaryContainer
    val circleColor = MaterialTheme.colorScheme.primary
    val onCircleColor = MaterialTheme.colorScheme.onPrimary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (row in 0..5) {
            val rowStart = firstCellDate.plus(row * 7, DateTimeUnit.DAY)
            val rowEnd = rowStart.plus(6, DateTimeUnit.DAY)

            // Intersection of the selected range with this calendar row
            val rowHasSelection = rowEnd >= selectedWeekStart && rowStart <= selectedWeekEnd
            val barStartCol = if (rowHasSelection)
                maxOf(0, (selectedWeekStart.toEpochDays() - rowStart.toEpochDays()).toInt()) else 0
            val barEndCol = if (rowHasSelection)
                minOf(6, (selectedWeekEnd.toEpochDays() - rowStart.toEpochDays()).toInt()) else 0
            // Left cap is rounded only when this is the absolute start of the 7-day period
            val roundLeft = rowHasSelection && rowStart.plus(barStartCol, DateTimeUnit.DAY) == selectedWeekStart
            // Right cap is rounded only when this is the absolute end of the 7-day period
            val roundRight = rowHasSelection && rowStart.plus(barEndCol, DateTimeUnit.DAY) == selectedWeekEnd

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .drawBehind {
                        if (!rowHasSelection) return@drawBehind

                        val cellW = size.width / 7f
                        val startX = barStartCol * cellW
                        val endX = (barEndCol + 1) * cellW
                        val barWidth = endX - startX
                        // Bar height with vertical padding so it looks like a pill inside the cell
                        val vPad = 4f
                        val barH = size.height - vPad * 2
                        val barTop = vPad
                        val r = barH / 2f
                        val capW = minOf(r, barWidth / 2f)

                        // Step 1: draw fully-rounded rect covering the whole selection in this row
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(startX, barTop),
                            size = Size(barWidth, barH),
                            cornerRadius = CornerRadius(r, r)
                        )
                        // Step 2: square off the left end if it continues from a previous row
                        if (!roundLeft) {
                            drawRect(
                                color = barColor,
                                topLeft = Offset(startX, barTop),
                                size = Size(capW, barH)
                            )
                        }
                        // Step 3: square off the right end if it continues into the next row
                        if (!roundRight) {
                            drawRect(
                                color = barColor,
                                topLeft = Offset(endX - capW, barTop),
                                size = Size(capW, barH)
                            )
                        }
                    }
            ) {
                for (col in 0..6) {
                    val date = rowStart.plus(col, DateTimeUnit.DAY)
                    val isCurrentMonth = date.month == displayMonth.month
                    val isToday = date == today
                    val isRangeEdge = date == selectedWeekStart || date == selectedWeekEnd
                    val showCircle = isToday || isRangeEdge

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onDayClick(date) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (showCircle) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(circleColor, CircleShape)
                            )
                        }
                        Text(
                            text = date.day.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = when {
                                showCircle -> onCircleColor
                                !isCurrentMonth -> onSurfaceColor.copy(alpha = 0.3f)
                                else -> onSurfaceColor
                            }
                        )
                    }
                }
            }
        }
    }
}
