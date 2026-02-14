package com.tkolymp.tkolympapp.Screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.calendar.CalendarUtils
import com.tkolymp.shared.calendar.CalendarViewModel
import com.tkolymp.shared.calendar.EventLayoutData
import com.tkolymp.shared.calendar.ViewMode
import com.tkolymp.shared.event.Event
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/**
 * Main CalendarView screen with timeline visualization
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarViewScreen(
    viewModel: CalendarViewModel = remember { CalendarViewModel() },
    onEventClick: (Long) -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Load events on first composition
    LaunchedEffect(Unit) {
        viewModel.loadEvents()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timeline") },
                navigationIcon = {
                    onBack?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Zpět")
                        }
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
        // Top bar with navigation and view mode selector
        CalendarTopBar(
            dateLabel = viewModel.getDateLabel(),
            viewMode = state.viewMode,
            showOnlyMine = state.showOnlyMine,
            onPreviousClick = { scope.launch { viewModel.navigatePrevious() } },
            onNextClick = { scope.launch { viewModel.navigateNext() } },
            onTodayClick = { scope.launch { viewModel.navigateToday() } },
            onViewModeChange = { mode -> scope.launch { viewModel.setViewMode(mode) } },
            onToggleOnlyMine = { scope.launch { viewModel.toggleShowOnlyMine() } }
        )
        
        Divider()
        
        // Timeline view
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.error != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Chyba při načítání",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.error ?: "",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { scope.launch { viewModel.loadEvents() } }) {
                        Text("Zkusit znovu")
                    }
                }
            }
        } else {
            // Show timeline based on view mode
            when (state.viewMode) {
                ViewMode.DAY -> {
                    SingleDayTimelineView(
                        events = state.layoutData.values.toList(),
                        selectedDate = state.selectedDate,
                        onEventClick = onEventClick
                    )
                }
                ViewMode.THREE_DAY, ViewMode.WEEK -> {
                    MultiDayTimelineView(
                        dates = viewModel.getDatesInRange(),
                        getEventsForDate = { date -> viewModel.getEventsForDate(date) },
                        onEventClick = onEventClick
                    )
                }
            }
        }
    }
    }
}

/**
 * Top bar with navigation controls
 */
@Composable
internal fun CalendarTopBar(
    dateLabel: String,
    viewMode: ViewMode,
    showOnlyMine: Boolean,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onTodayClick: () -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    onToggleOnlyMine: () -> Unit
) {
    Column {
        // Date navigation row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousClick) {
                Icon(Icons.Default.ChevronLeft, "Předchozí")
            }
            
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            
            IconButton(onClick = onNextClick) {
                Icon(Icons.Default.ChevronRight, "Další")
            }
        }
        
        // View mode and filter row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // View mode selector
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ViewMode.entries.forEach { mode ->
                    FilterChip(
                        selected = viewMode == mode,
                        onClick = { onViewModeChange(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ViewMode.DAY -> "Den"
                                    ViewMode.THREE_DAY -> "3 dny"
                                    ViewMode.WEEK -> "Týden"
                                }
                            )
                        }
                    )
                }
            }

            // Right-side: my events filter + Today button
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onTodayClick) {
                    Text("Dnes")
                }

                FilterChip(
                    selected = showOnlyMine,
                    onClick = onToggleOnlyMine,
                    label = { Text("Moje") }
                )
            }
        }
    }
}

/**
 * Single day timeline view with hour markers and events
 */
@Composable
internal fun SingleDayTimelineView(
    events: List<EventLayoutData>,
    selectedDate: LocalDate,
    onEventClick: (Long) -> Unit
) {
    val scrollState = rememberScrollState()
    val minuteHeight = 1.dp // Height per minute
    val hourHeight = 60.dp // 60 minutes
    val totalHeight = hourHeight * 24 // 24 hours
    
    // Auto-scroll to morning (7 AM) on first display
    LaunchedEffect(selectedDate) {
        scrollState.scrollTo((7 * 60 * minuteHeight.value).toInt())
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
                    .height(totalHeight)
            )
            
            // Events area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(totalHeight)
            ) {
                // Grid lines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (hour in 0..23) {
                        val y = hour * hourHeight.toPx()
                        drawLine(
                            color = Color.LightGray,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                    }
                }
                
                // Now line
                NowLine(
                    modifier = Modifier.fillMaxWidth(),
                    minuteHeight = minuteHeight
                )
                
                // Render events
                events.forEach { layoutData ->
                    TimelineEventCard(
                        layoutData = layoutData,
                        minuteHeight = minuteHeight,
                        onClick = { 
                            val evId = layoutData.event.event?.id ?: return@TimelineEventCard
                            onEventClick(evId)
                        }
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
    getEventsForDate: (LocalDate) -> List<EventLayoutData>,
    onEventClick: (Long) -> Unit
) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val minuteHeight = 0.8.dp // Slightly compressed for multi-day view
    val hourHeight = 48.dp
    val totalHeight = hourHeight * 24
    val dayWidth = 200.dp
    val dayHeaderHeight = 40.dp // Fixed height for day headers
    
    // Auto-scroll to morning
    LaunchedEffect(dates) {
        verticalScrollState.scrollTo((7 * 60 * minuteHeight.value).toInt())
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
                compact = true
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
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(dayHeaderHeight)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
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
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                for (hour in 0..23) {
                                    val y = hour * hourHeight.toPx()
                                    drawLine(
                                        color = Color.LightGray,
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 1f
                                    )
                                }
                            }
                            
                            // Now line (only for today)
                            if (date == Clock.System.todayIn(TimeZone.currentSystemDefault())) {
                                NowLine(
                                    modifier = Modifier.fillMaxWidth(),
                                    minuteHeight = minuteHeight
                                )
                            }
                            
                            // Events for this day
                            getEventsForDate(date).forEach { layoutData ->
                                TimelineEventCard(
                                    layoutData = layoutData,
                                    minuteHeight = minuteHeight,
                                    compact = true,
                                    onClick = { 
                                        val evId = layoutData.event.event?.id ?: return@TimelineEventCard
                                        onEventClick(evId)
                                    }
                                )
                            }
                        }
                    }
                    
                    // Divider between days
                    Divider(
                        modifier = Modifier
                            .width(1.dp)
                            .height(totalHeight + dayHeaderHeight),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

/**
 * Hour markers column
 */
@Composable
internal fun HourMarkersColumn(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val hourHeight = if (compact) 48.dp else 60.dp
    
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
    ) {
        for (hour in 0..23) {
            Box(
                modifier = Modifier
                    .height(hourHeight)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    text = "${hour.toString().padStart(2, '0')}:00",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp, top = 0.dp)
                )
            }
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
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val minutesFromMidnight = now.hour * 60 + now.minute
    val offsetDp = minuteHeight * minutesFromMidnight
    
    Canvas(
        modifier = modifier
            .offset(y = offsetDp)
            .height(2.dp)
    ) {
        drawLine(
            color = Color.Red,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 4f
        )
    }
}

/**
 * Individual event card in timeline
 */
@Composable
internal fun TimelineEventCard(
    layoutData: EventLayoutData,
    minuteHeight: Dp,
    compact: Boolean = false,
    onClick: () -> Unit
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
    val coupleInfo = remember(event.event, isLesson) {
        if (isLesson) {
            getCoupleInfo(event.event)
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
    
    // Get color
    val backgroundColor = parseEventColor(event.colorRgb, event.type)

    val contentColor = if (isLesson) {
        if (isSystemInDarkTheme()) Color.White else Color(0xFF212121)
    } else {
        Color.White
    }
    
    // Get trainer name for lessons
    val trainerName = remember(event.event, isLesson) {
        if (isLesson) {
            event.event?.eventTrainersList?.firstOrNull()
        } else null
    }
    
    // Determine max lines based on duration
    val titleMaxLines = when {
        compact -> 1
        layoutData.durationMinutes < 30 -> 1
        layoutData.durationMinutes < 60 -> 2
        else -> 3
    }
    
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val totalWidth = maxWidth
        val cardWidth = totalWidth * widthFraction
        val leftOffset = totalWidth * leftFraction
        
        Box(
            modifier = Modifier
                .width(cardWidth)
                .offset(
                    x = leftOffset,
                    y = offsetDp
                )
                .height(heightDp.coerceAtLeast(20.dp))
                .padding(horizontal = 1.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick)
                    .alpha(if (event.isCancelled) 0.5f else 1f),
                colors = CardDefaults.cardColors(
                    containerColor = backgroundColor
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    // Title (couple name for lessons, event title for others)
                    Text(
                        text = displayTitle,
                        style = if (compact) {
                            MaterialTheme.typography.labelSmall
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        fontWeight = if (event.isMyEvent) FontWeight.Bold else FontWeight.Normal,
                        textDecoration = if (event.isCancelled) TextDecoration.LineThrough else null,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        color = contentColor
                    )
                    
                    // Secondary info: trainer for lessons, time for others
                    if (layoutData.durationMinutes > 30 && !compact) {
                            if (isLesson && !trainerName.isNullOrBlank()) {
                            Text(
                                text = trainerName,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (!isLesson) {
                            Text(
                                text = "${CalendarUtils.formatTime(event.startTime)} - ${CalendarUtils.formatTime(event.endTime)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Couple info for lessons
 */
internal data class CoupleInfo(
    val displayName: String,
    val isFree: Boolean
)

/**
 * Get couple info from event - returns couple name or "VOLNO" if no registrations
 */
internal fun getCoupleInfo(event: Event?): CoupleInfo? {
    if (event == null) return null
    
    // Check if there are any registrations
    if (event.eventRegistrationsList.isEmpty()) {
        return CoupleInfo("VOLNO", true)
    }
    
    // Try to get first registration with couple
    val registration = event.eventRegistrationsList.firstOrNull { it.couple != null }
    val couple = registration?.couple
    
    if (couple != null) {
        val manLastName = couple.man?.lastName
        val womanLastName = couple.woman?.lastName
        
        return if (!manLastName.isNullOrBlank() && !womanLastName.isNullOrBlank()) {
            CoupleInfo("$manLastName - $womanLastName", false)
        } else {
            CoupleInfo("VOLNO", true)
        }
    }
    
    // No couple, show as free
    return CoupleInfo("VOLNO", true)
}

/**
 * Parse event color from string
 */
@Composable
internal fun parseEventColor(colorRgb: String?, type: String?): Color {
    // Special handling for lessons - use very light gray in light mode and dark gray in dark mode
    if (colorRgb == "lesson" || type?.equals("lesson", ignoreCase = true) == true) {
        return if (isSystemInDarkTheme()) Color(0xFF303030) else Color(0xFFF5F5F5)
    }

    if (colorRgb.isNullOrBlank()) {
        return Color(0xFFADD8E6) // Light blue default
    }

    return try {
        val hex = if (colorRgb.startsWith("#")) {
            colorRgb.substring(1)
        } else {
            colorRgb
        }

        val rgb = hex.toLong(16)
        Color(
            red = ((rgb shr 16) and 0xFF).toInt(),
            green = ((rgb shr 8) and 0xFF).toInt(),
            blue = (rgb and 0xFF).toInt()
        )
    } catch (e: Exception) {
        Color(0xFFADD8E6)
    }
}
