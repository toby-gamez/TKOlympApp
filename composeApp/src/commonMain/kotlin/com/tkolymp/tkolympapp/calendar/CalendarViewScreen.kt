package com.tkolymp.tkolympapp.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkolymp.shared.calendar.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*

/**
 * Main CalendarView screen with timeline visualization
 */
@Composable
fun CalendarViewScreen(
    viewModel: CalendarViewModel = remember { CalendarViewModel() },
    onEventClick: (Long) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Load events on first composition
    LaunchedEffect(Unit) {
        viewModel.loadEvents()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
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

/**
 * Top bar with navigation controls
 */
@Composable
private fun CalendarTopBar(
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
            
            // My events filter
            FilterChip(
                selected = showOnlyMine,
                onClick = onToggleOnlyMine,
                label = { Text("Moje") }
            )
        }
        
        // Today button
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = onTodayClick) {
                Text("Dnes")
            }
        }
    }
}

/**
 * Single day timeline view with hour markers and events
 */
@Composable
private fun SingleDayTimelineView(
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
                        onClick = { onEventClick(layoutData.event.id) }
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
private fun MultiDayTimelineView(
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
                                    onClick = { onEventClick(layoutData.event.id) }
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
private fun HourMarkersColumn(
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
private fun NowLine(
    modifier: Modifier = Modifier,
    minuteHeight: androidx.compose.ui.unit.Dp
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
private fun TimelineEventCard(
    layoutData: EventLayoutData,
    minuteHeight: androidx.compose.ui.unit.Dp,
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
    
    // Get color
    val backgroundColor = remember(event.colorRgb, event.type) {
        parseEventColor(event.colorRgb, event.type)
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
                .padding(2.dp)
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
                        .padding(4.dp)
                ) {
                    // Title
                    Text(
                        text = event.title,
                        style = if (compact) {
                            MaterialTheme.typography.labelSmall
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        fontWeight = if (event.isMyEvent) FontWeight.Bold else FontWeight.Normal,
                        textDecoration = if (event.isCancelled) TextDecoration.LineThrough else null,
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                    
                    // Time (if space allows)
                    if (layoutData.durationMinutes > 30 && !compact) {
                        Text(
                            text = "${CalendarUtils.formatTime(event.startTime)} - ${CalendarUtils.formatTime(event.endTime)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Parse event color from string
 */
private fun parseEventColor(colorRgb: String?, type: String?): Color {
    // Special handling for lessons - use secondary theme color
    if (colorRgb == "lesson" || type?.equals("lesson", ignoreCase = true) == true) {
        return Color(0xFF2196F3) // Material Blue - will be replaced with theme color in UI
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
