package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.tkolymp.tkolympapp.components.AppTopBar
import com.tkolymp.tkolympapp.components.ChipRow
import com.tkolymp.tkolympapp.components.LayoutCard
import com.tkolymp.tkolympapp.components.SegmentedToggle
import com.tkolymp.tkolympapp.components.SheetPrimaryButton
import com.tkolymp.tkolympapp.components.SheetSectionLabel
import com.tkolymp.tkolympapp.components.successColor
import com.tkolymp.tkolympapp.components.successContainerColor
import com.tkolymp.tkolympapp.ui.theme.BrandLightPrimary
import com.tkolymp.tkolympapp.ui.theme.Spacing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.tkolymp.tkolympapp.components.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import com.tkolymp.tkolympapp.LocalBottomBarPadding
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.shared.utils.formatTimes
import com.tkolymp.shared.utils.formatHtmlContent
import com.tkolymp.shared.utils.formatTimesWithDate
import com.tkolymp.shared.utils.formatTimesWithDateAlways
import com.tkolymp.shared.utils.durationMinutes
import com.tkolymp.shared.utils.translateEventType
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.calendar.CalendarUtils
import com.tkolymp.shared.viewmodels.CalendarViewViewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.calendar.EventLayoutData
import com.tkolymp.shared.calendar.ViewMode
import com.tkolymp.shared.event.Event
import com.tkolymp.shared.calendar.CoupleInfo
import com.tkolymp.shared.calendar.getCoupleInfo
import com.tkolymp.tkolympapp.util.parseEventColor
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/**
 * Main CalendarView screen with timeline visualization
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarViewScreen(
    viewModel: CalendarViewViewModel = viewModel(),
    onEventClick: (Long, Long?) -> Unit = { _, _ -> },
    onBack: (() -> Unit)? = null,
    onSwitchToBlocks: (() -> Unit)? = null,
    bottomPadding: Dp = 0.dp
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val resumeSeen = remember { mutableStateOf(false) }
    val cachedLayout = remember { mutableStateOf<List<EventLayoutData>?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (resumeSeen.value) {
                    scope.launch { viewModel.loadEvents() }
                } else {
                    resumeSeen.value = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    // Load events on first composition
    LaunchedEffect(Unit) {
        viewModel.loadEvents()
    }

    val showBottomSheet = remember { mutableStateOf(false) }
    val sheetDragOffset = remember { Animatable(0f) }
    LaunchedEffect(showBottomSheet.value) { if (showBottomSheet.value) sheetDragOffset.snapTo(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = if (onSwitchToBlocks != null) AppStrings.current.navigation.calendar else AppStrings.current.timeline.timeline,
                subtitle = viewModel.getDateLabel(),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { scope.launch { viewModel.navigatePrevious() } }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = AppStrings.current.calendarView.previous)
                    }
                    IconButton(onClick = { scope.launch { viewModel.navigateNext() } }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = AppStrings.current.calendarView.next)
                    }
                    IconButton(onClick = { showBottomSheet.value = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = AppStrings.current.commonActions.options)
                    }
                }
            )
        }
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(MaterialTheme.colorScheme.background)
    ) {
        ChipRow {
            SegmentedToggle(
                labels = listOf(AppStrings.current.people.mine, AppStrings.current.commonActions.all),
                selectedIndex = if (state.showOnlyMine) 0 else 1,
                onSelect = { i -> if ((i == 0) != state.showOnlyMine) scope.launch { viewModel.toggleShowOnlyMine() } }
            )
        }
        Spacer(Modifier.height(Spacing.small))

        SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch { viewModel.loadEvents() } },
            modifier = Modifier.weight(1f)
        ) {
            if (state.error != null) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = AppStrings.current.events.errorLoadingEvents,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.error?.message ?: "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { scope.launch { viewModel.loadEvents() } }) {
                            Text(AppStrings.current.commonActions.retry)
                        }
                    }
                }
            } else {
                when (state.viewMode) {
                    ViewMode.DAY -> {
                        val layoutListToRender = if (state.layoutData.isEmpty() && state.isOffline && cachedLayout.value != null) cachedLayout.value.orEmpty() else state.layoutData.values.toList()
                        LaunchedEffect(state.layoutData) { if (state.layoutData.isNotEmpty()) cachedLayout.value = state.layoutData.values.toList() }
                        SingleDayTimelineView(
                            events = layoutListToRender,
                            selectedDate = state.selectedDate,
                            onEventClick = onEventClick
                        )
                    }
                    ViewMode.THREE_DAY, ViewMode.WEEK -> {
                        MultiDayTimelineView(
                            dates = viewModel.getDatesInRange(),
                            events = state.layoutData.values.toList(),
                            onEventClick = onEventClick
                        )
                    }
                }
            }
        }
        TimelineLegend()
    }
    }

    // Scrim
    AnimatedVisibility(
        visible = showBottomSheet.value,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showBottomSheet.value = false }
        )
    }

    // Inline bottom sheet — same window as Scaffold, no keyboard flash
    AnimatedVisibility(
        visible = showBottomSheet.value,
        enter = slideInVertically(tween(300)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(200)),
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, sheetDragOffset.value.toInt().coerceAtLeast(0)) }
                .pointerInput(Unit) {
                    val dismissThreshold = 100.dp.toPx()
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (sheetDragOffset.value > dismissThreshold) {
                                showBottomSheet.value = false
                            } else {
                                scope.launch { sheetDragOffset.animateTo(0f, spring()) }
                            }
                        },
                        onDragCancel = { scope.launch { sheetDragOffset.animateTo(0f, spring()) } },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch { sheetDragOffset.snapTo(sheetDragOffset.value + dragAmount) }
                        }
                    )
                }
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showBottomSheet.value = false }
                        .padding(top = 10.dp, bottom = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
                TimelineBottomSheetContent(
                    viewMode = state.viewMode,
                    onViewModeChange = { mode -> scope.launch { viewModel.setViewMode(mode) } },
                    onSwitchToBlocks = onSwitchToBlocks?.let { sw -> { showBottomSheet.value = false; sw() } },
                    onNavigateToday = { showBottomSheet.value = false; scope.launch { viewModel.navigateToday() } },
                    onClose = { showBottomSheet.value = false }
                )
            }
        }
    }
    } // Box
}

/**
 * Top bar with navigation controls
 */
@Composable
internal fun CalendarTopBar(
    dateLabel: String,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousClick) {
            Icon(Icons.Default.ChevronLeft, AppStrings.current.calendarView.previous)
        }
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onNextClick) {
            Icon(Icons.Default.ChevronRight, AppStrings.current.calendarView.next)
        }
    }
}

@Composable
private fun TimelineBottomSheetContent(
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    onSwitchToBlocks: (() -> Unit)?,
    onNavigateToday: () -> Unit,
    onClose: () -> Unit
) {
    val strings = AppStrings.current.calendarView
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = LocalBottomBarPadding.current + Spacing.default)
    ) {
        Text(strings.calendarOptionsTitle, modifier = Modifier.padding(top = Spacing.small), fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)

        if (onSwitchToBlocks != null) {
            SheetSectionLabel(strings.layoutSection)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                LayoutCard(Icons.Default.ViewAgenda, strings.viewList, false, { onSwitchToBlocks() }, Modifier.weight(1f))
                LayoutCard(Icons.Default.ViewTimeline, strings.viewAxis, true, {}, Modifier.weight(1f))
            }
        }

        SheetSectionLabel(strings.showSection)
        SegmentedToggle(
            labels = ViewMode.entries.map { mode ->
                when (mode) {
                    ViewMode.DAY -> strings.viewModeDay
                    ViewMode.THREE_DAY -> strings.viewModeThreeDays
                    ViewMode.WEEK -> strings.viewModeWeek
                }
            },
            selectedIndex = ViewMode.entries.indexOf(viewMode),
            onSelect = { onViewModeChange(ViewMode.entries[it]) },
            modifier = Modifier.fillMaxWidth(),
            fillWidth = true
        )

        Spacer(Modifier.height(Spacing.default))
        SheetPrimaryButton(text = strings.resetToToday, onClick = onNavigateToday)
    }
}

/** Colour key under the axis, same four entries as the prototype. */
@Composable
internal fun TimelineLegend(modifier: Modifier = Modifier) {
    val strings = AppStrings.current.calendarView
    val ok = successColor()
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.default, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(AppStrings.current.people.mine) { Box(Modifier.size(width = 12.dp, height = 10.dp).background(BrandLightPrimary, RoundedCornerShape(3.dp))) }
        LegendItem(strings.legendFree) {
            Box(Modifier.size(width = 12.dp, height = 10.dp).background(successContainerColor(), RoundedCornerShape(3.dp)).border(1.5.dp, ok, RoundedCornerShape(3.dp)))
        }
        LegendItem(strings.legendTaken) {
            Box(Modifier.size(width = 12.dp, height = 10.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(3.dp)).border(1.dp, lineColor, RoundedCornerShape(3.dp)))
        }
        LegendItem(strings.legendGroup) {
            Box(Modifier.size(width = 12.dp, height = 10.dp).background(Color(0xFFC9A000).copy(alpha = 0.25f), RoundedCornerShape(3.dp))) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xFFC9A000), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        swatch()
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Single day timeline view with hour markers and events
 */
@Composable
internal fun SingleDayTimelineView(
    events: List<EventLayoutData>,
    selectedDate: LocalDate,
    onEventClick: (Long, Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val minuteHeight = 1.dp // Height per minute
    val hourHeight = 60.dp // 60 minutes
    val totalHeight = hourHeight * 24 // 24 hours

    val today = remember { kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val now = remember { kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val viewportHeightPx = with(density) { maxHeight.toPx() }

    // Auto-scroll only on initial load; preserve position when navigating days
    LaunchedEffect(Unit) {
        val targetMinute = if (selectedDate == today) {
            now.hour * 60 + now.minute
        } else {
            7 * 60
        }
        val positionPx = with(density) { (minuteHeight * targetMinute).toPx() }
        scrollState.scrollTo((positionPx - viewportHeightPx / 2).coerceAtLeast(0f).toInt())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Row(
            modifier = Modifier.height(totalHeight)
        ) {
            // Hour markers (fixed column)
            HourMarkersColumn(
                modifier = Modifier
                    .width(60.dp)
                    .height(totalHeight),
                minuteHeight = minuteHeight,
                showNow = selectedDate == today
            )
            
            // Events area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(totalHeight)
            ) {
                // Grid lines
                val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (hour in 0..23) {
                        val y = hour * hourHeight.toPx()
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                    }
                }
                
                // Render events
                events.forEach { layoutData ->
                    TimelineEventCard(
                        layoutData = layoutData,
                        minuteHeight = minuteHeight,
                        onClick = {
                            val evId = layoutData.event.event?.id ?: return@TimelineEventCard
                            onEventClick(evId, layoutData.event.id)
                        }
                    )
                }

                // Now line (on top of events)
                NowLine(
                    modifier = Modifier.fillMaxWidth(),
                    minuteHeight = minuteHeight
                )
            }
        }
    }
    }
}

/**
 * Multi-day timeline view (3-day or week view)
 */
@Composable
internal fun MultiDayTimelineView(
    dates: List<LocalDate>,
    events: List<EventLayoutData>,
    onEventClick: (Long, Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val eventsByDate = remember(events) { events.groupBy { it.event.startTime.date } }
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val minuteHeight = 0.8.dp // Slightly compressed for multi-day view
    val hourHeight = 48.dp
    val totalHeight = hourHeight * 24
    val dayWidth = 200.dp
    val dayHeaderHeight = 40.dp // Fixed height for day headers

    val today = remember { kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val now = remember { kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val viewportHeightPx = with(density) { maxHeight.toPx() }

    // Auto-scroll only on initial load; preserve position when navigating
    LaunchedEffect(Unit) {
        val targetMinute = if (today in dates) {
            now.hour * 60 + now.minute
        } else {
            7 * 60
        }
        val positionPx = with(density) { (minuteHeight * targetMinute).toPx() }
        verticalScrollState.scrollTo((positionPx - viewportHeightPx / 2).coerceAtLeast(0f).toInt())
    }

    // Reset horizontal scroll to the start of the range whenever the visible dates change
    // (e.g. navigating to a different week) — otherwise the previous scroll offset can land
    // on a day with no events, making it look like nothing loaded.
    LaunchedEffect(dates.firstOrNull()) {
        horizontalScrollState.scrollTo(0)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Hour labels (fixed column)
        Column(
            modifier = Modifier.width(50.dp)
        ) {
            // Spacer to align with day headers
            Spacer(modifier = Modifier.height(dayHeaderHeight))
            
            // Hour markers
            HourMarkersColumn(
                modifier = Modifier
                    .width(50.dp)
                    .height(totalHeight)
                    .verticalScroll(verticalScrollState, enabled = false),
                compact = true,
                minuteHeight = minuteHeight,
                showNow = today in dates
            )
        }
        
        // Days (scrollable horizontally and vertically)
        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(verticalScrollState)
                .horizontalScroll(horizontalScrollState)
        ) {
            Row(
                modifier = Modifier.height(totalHeight + dayHeaderHeight)
            ) {
                dates.forEach { date ->
                    Column(
                        modifier = Modifier.width(dayWidth)
                    ) {
                        // Day header
                        Text(
                            text = CalendarUtils.formatDayLabel(date),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (date == today) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(dayHeaderHeight)
                                .background(MaterialTheme.colorScheme.background)
                                .padding(8.dp),
                            textAlign = TextAlign.Center
                        )

                        // Timeline for this day
                        Box(
                            modifier = Modifier
                                .width(dayWidth)
                                .height(totalHeight)
                        ) {
                            // Grid lines
                            val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                for (hour in 0..23) {
                                    val y = hour * hourHeight.toPx()
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 1f
                                    )
                                }
                            }
                            
                            // Events for this day
                            (eventsByDate[date] ?: emptyList()).forEach { layoutData ->
                                TimelineEventCard(
                                    layoutData = layoutData,
                                    minuteHeight = minuteHeight,
                                    compact = true,
                                    onClick = {
                                        val evId = layoutData.event.event?.id ?: return@TimelineEventCard
                                        onEventClick(evId, layoutData.event.id)
                                    }
                                )
                            }
                            
                            // Now line on top of events (only for today)
                            if (date == today) {
                                NowLine(
                                    modifier = Modifier.fillMaxWidth(),
                                    minuteHeight = minuteHeight
                                )
                            }
                        }
                    }
                    
                    // Divider between days
                    VerticalDivider(
                        modifier = Modifier.height(totalHeight + dayHeaderHeight),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                }
            }
        }
    }
    }
}

/**
 * Hour markers column: labels centred on their grid line, plus a red pill with the current time.
 */
@Composable
internal fun HourMarkersColumn(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    minuteHeight: Dp = 1.dp,
    showNow: Boolean = false
) {
    val hourHeight = if (compact) 48.dp else 60.dp
    var now by remember { mutableStateOf(kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) }
    LaunchedEffect(showNow) {
        while (showNow) {
            now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            kotlinx.coroutines.delay(60_000)
        }
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        for (hour in 0..23) {
            Text(
                text = "${hour.toString().padStart(2, '0')}:00",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = hourHeight * hour + if (hour == 0) 0.dp else (-7).dp)
                    .padding(end = 6.dp)
            )
        }
        if (showNow) {
            Text(
                text = CalendarUtils.formatTime(now),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = minuteHeight * (now.hour * 60 + now.minute) - 8.dp)
                    .padding(end = 3.dp)
                    .background(BrandLightPrimary, RoundedCornerShape(5.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

/**
 * "Now" line showing current time
 */
@Composable
internal fun NowLine(
    modifier: Modifier = Modifier,
    minuteHeight: Dp
) {
    var now by remember { mutableStateOf(kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) }
    LaunchedEffect(Unit) {
        while (true) {
            now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            kotlinx.coroutines.delay(60_000)
        }
    }
    val minutesFromMidnight = now.hour * 60 + now.minute
    val offsetDp = minuteHeight * minutesFromMidnight

    Box(
        modifier = modifier
            .offset(y = offsetDp - 1.dp)
            .height(2.dp)
            .background(BrandLightPrimary)
    )
}

/**
 * Individual event card in timeline
 */
@Composable
internal fun TimelineEventCard(
    layoutData: EventLayoutData,
    minuteHeight: Dp,
    compact: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val event = layoutData.event
    val offsetDp = minuteHeight * layoutData.startMinute
    val heightDp = minuteHeight * layoutData.durationMinutes
    
    // Calculate width and offset based on collision columns
    val totalColumns = layoutData.totalColumns
    val column = layoutData.column
    
    // Calculate fractions for positioning
    val widthFraction = 1f / totalColumns
    val leftFraction = column.toFloat() / totalColumns
    
    val isLesson = event.type?.equals("lesson", ignoreCase = true) == true
    
    // For lessons, get couple name or "VOLNO"
    val freeLesson = AppStrings.current.calendarView.freeLesson
    val coupleInfo = remember(event.event, isLesson, freeLesson) {
        if (isLesson) {
            getCoupleInfo(event.event, freeLesson)
        } else null
    }
    
    val displayTitle = remember(event.title, coupleInfo, isLesson) {
        if (isLesson && coupleInfo != null) {
            coupleInfo.displayName
        } else {
            event.title
        }
    }
    
    val isFreeLesson = coupleInfo?.isFree == true
    
    val trainerName = remember(event.event, isLesson) {
        if (isLesson) event.event?.eventTrainersList?.firstOrNull() else null
    }
    val isMine = event.isMyEvent
    val cancelled = event.isCancelled
    val isGroup = !isLesson
    val accent = parseEventColor(event.colorRgb, event.type)
    val ok = successColor()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = onSurface.copy(alpha = 0.18f)
    val shape = RoundedCornerShape(8.dp)
    val density = LocalDensity.current

    // Look follows the prototype: group = cohort tint + top bar, cancelled = grey, free = dashed green, mine = red, else outlined.
    val background: Color
    val foreground: Color
    when {
        isGroup -> { background = accent.copy(alpha = 0.2f); foreground = onSurface }
        cancelled -> { background = MaterialTheme.colorScheme.surfaceVariant; foreground = MaterialTheme.colorScheme.onSurfaceVariant }
        isFreeLesson -> { background = successContainerColor(); foreground = ok }
        isMine -> { background = BrandLightPrimary; foreground = Color.White }
        else -> { background = MaterialTheme.colorScheme.surface; foreground = onSurface }
    }

    val titleMaxLines = when {
        compact -> 1
        layoutData.durationMinutes < 30 -> 1
        layoutData.durationMinutes < 60 -> 2
        else -> 3
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalWidth = maxWidth
        val cardWidth = totalWidth * widthFraction
        val leftOffset = totalWidth * leftFraction

        Box(
            modifier = Modifier
                .width(cardWidth)
                .offset(x = leftOffset, y = offsetDp)
                .height(heightDp.coerceAtLeast(20.dp))
                .padding(horizontal = 3.dp, vertical = 1.dp)
                .clip(shape)
                .background(background)
                .then(
                    when {
                        isGroup -> Modifier.drawBehind {
                            drawRect(accent, size = androidx.compose.ui.geometry.Size(size.width, with(density) { 3.dp.toPx() }))
                            if (isMine) {
                                val w = with(density) { 1.5.dp.toPx() }
                                drawRoundRect(
                                    BrandLightPrimary,
                                    topLeft = Offset(w / 2, w / 2),
                                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(with(density) { 8.dp.toPx() }),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(w)
                                )
                            }
                        }
                        isFreeLesson && !cancelled -> Modifier.drawBehind {
                            val w = with(density) { 1.5.dp.toPx() }
                            drawRoundRect(
                                ok,
                                topLeft = Offset(w / 2, w / 2),
                                size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(with(density) { 8.dp.toPx() }),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    w,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                                )
                            )
                        }
                        !isMine && !cancelled -> Modifier.border(1.dp, outline, shape)
                        else -> Modifier
                    }
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Column {
                if (!compact && layoutData.durationMinutes >= 30) {
                    Text(
                        text = "${CalendarUtils.formatTime(event.startTime)}–${CalendarUtils.formatTime(event.endTime)}",
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = foreground.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                }
                Text(
                    text = displayTitle,
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    color = foreground
                )
                if (!compact && layoutData.durationMinutes >= 60 && isLesson && !trainerName.isNullOrBlank()) {
                    Text(
                        text = trainerName,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = foreground.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
