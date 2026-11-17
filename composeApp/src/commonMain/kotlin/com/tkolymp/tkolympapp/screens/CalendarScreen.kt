
package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Person
import com.tkolymp.shared.viewmodels.mondayOf
import com.tkolymp.tkolympapp.components.InitialsAvatar
import com.tkolymp.tkolympapp.components.LayoutCard
import com.tkolymp.tkolympapp.components.SheetChip
import com.tkolymp.tkolympapp.components.SheetPrimaryButton
import com.tkolymp.tkolympapp.components.SheetSectionLabel
import com.tkolymp.tkolympapp.components.SheetSwitchRow
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FilterAltOff
import com.tkolymp.shared.event.firstTrainerOrEmpty
import com.tkolymp.shared.utils.standardDurationMinutes
import com.tkolymp.shared.utils.formatTimes
import com.tkolymp.shared.utils.getLocalizedMonthName
import com.tkolymp.shared.utils.parseToLocal
import com.tkolymp.tkolympapp.components.AppTopBar
import com.tkolymp.tkolympapp.components.ChipRow
import com.tkolymp.tkolympapp.components.DayEmptyState
import com.tkolymp.tkolympapp.components.FilterButton
import com.tkolymp.tkolympapp.components.HourHeader
import com.tkolymp.tkolympapp.components.QuickChip
import com.tkolymp.tkolympapp.components.RowDivider
import com.tkolymp.tkolympapp.components.ScheduleCard
import com.tkolymp.tkolympapp.components.ScheduleRow
import com.tkolymp.tkolympapp.components.ScheduleTag
import com.tkolymp.tkolympapp.components.SegmentedToggle
import com.tkolymp.tkolympapp.components.TimelineDot
import com.tkolymp.tkolympapp.components.TimelineRow
import com.tkolymp.tkolympapp.components.TrainerCard
import com.tkolymp.tkolympapp.components.WeekDayCell
import com.tkolymp.tkolympapp.components.WeekStrip
import com.tkolymp.tkolympapp.components.successColor
import com.tkolymp.tkolympapp.components.timelineInfo
import com.tkolymp.tkolympapp.ui.theme.Spacing
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.datetime.number
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
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
import com.tkolymp.tkolympapp.components.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.tkolymp.tkolympapp.TutorialHighlight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.formatFullCalendarDate
import com.tkolymp.shared.viewmodels.BirthdayEntry
import com.tkolymp.tkolympapp.LocalBottomBarPadding
import com.tkolymp.tkolympapp.SwipeToReload
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import com.tkolymp.tkolympapp.components.EmptyState
import com.tkolymp.tkolympapp.components.LessonView
import com.tkolymp.tkolympapp.components.RenderSingleEventCard
import com.tkolymp.tkolympapp.util.StaggeredItem
import com.tkolymp.tkolympapp.util.tabContentTransitionSpec
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.CalendarMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    weekOffset: Int = 0,
    onWeekOffsetChange: (Int) -> Unit = {},
    onOpenEvent: (Long, Long?) -> Unit = { _, _ -> },
    onOpenRozpis: (Long, Long?) -> Unit = { _, _ -> },
    onNavigateTimeline: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onCreatePersonalEvent: (() -> Unit)? = null,
    onCreateEvent: (() -> Unit)? = null,
    bottomPadding: Dp = 0.dp
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var localWeekOffset by rememberSaveable { mutableIntStateOf(weekOffset) }
    val tabs = listOf(AppStrings.current.people.mine, AppStrings.current.commonActions.all)

    val tutorialActive by com.tkolymp.shared.tutorial.TutorialManager.isActive.collectAsStateWithLifecycle()
    val tutorialStep by com.tkolymp.shared.tutorial.TutorialManager.currentStep.collectAsStateWithLifecycle()

    var contentBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var filterBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    LaunchedEffect(tutorialStep, tutorialActive) {
        if (!tutorialActive || tutorialStep !in 6..8) return@LaunchedEffect
        when (tutorialStep) {
            6 -> { selectedTab = 0; delay(100); contentBounds?.let { TutorialHighlight.rect = it } }
            7 -> { selectedTab = 1; delay(100); contentBounds?.let { TutorialHighlight.rect = it } }
            8 -> { selectedTab = 0; delay(100); filterBounds?.let { TutorialHighlight.rect = it } }
        }
    }
    val calendarViewModel = viewModel<com.tkolymp.shared.viewmodels.CalendarViewModel>()
    val calState by calendarViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val today = remember { kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    // null → floating today-based view; non-null → a specific Monday chosen from the picker
    var customStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetDragOffset = remember { Animatable(0f) }
    LaunchedEffect(showBottomSheet) { if (showBottomSheet) sheetDragOffset.snapTo(0f) }

    var selectedTrainers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedLocations by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedDateIso by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyFree by remember { mutableStateOf(false) }
    var hideCancelled by remember { mutableStateOf(false) }
    var selectedTypes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var trainerView by rememberSaveable { mutableStateOf(false) }
    var collapsedTrainers by remember { mutableStateOf<Set<String>>(emptySet()) }

    val availableTrainers = remember(calState.lessonsByTrainerByDay) {
        calState.lessonsByTrainerByDay.values.flatMap { it.keys }.sorted().toSet()
    }
    val availableLocations = remember(calState.eventsByDay) {
        calState.eventsByDay.values.flatten().mapNotNull { inst ->
            inst.event?.locationText?.takeIf { it.isNotBlank() }
                ?: inst.event?.location?.name?.takeIf { it.isNotBlank() }
        }.sorted().toSet()
    }

    LaunchedEffect(localWeekOffset, customStartDate) {
        selectedTrainers = emptySet()
        selectedLocations = emptySet()
    }

    // Day shown in the list (also what the filter sheet counts and previews).
    val activeDateIso = selectedDateIso?.takeIf { it in calState.visibleDates }
        ?: calState.todayString.takeIf { it in calState.visibleDates } ?: calState.visibleDates.firstOrNull()
    val rawDay = remember(activeDateIso, calState.lessonsByTrainerByDay, calState.otherEventsByDay) {
        activeDateIso?.let { d -> calState.lessonsByTrainerByDay[d].orEmpty().values.flatten() + calState.otherEventsByDay[d].orEmpty() } ?: emptyList()
    }
    fun locationOf(inst: EventInstance): String? =
        inst.event?.locationText?.takeIf { it.isNotBlank() } ?: inst.event?.location?.name?.takeIf { it.isNotBlank() }
    fun passesFilters(inst: EventInstance): Boolean =
        (selectedTrainers.isEmpty() || inst.event?.eventTrainersList?.any { it in selectedTrainers } == true) &&
            (selectedLocations.isEmpty() || locationOf(inst) in selectedLocations) &&
            (selectedTypes.isEmpty() || inst.event?.type?.lowercase() in selectedTypes) &&
            (!hideCancelled || !inst.isCancelled) &&
            (!onlyFree || timelineInfo(inst, calState.myPersonId, calState.myCoupleIds).isFree)

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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val sheetMaxHeight = maxHeight * 0.88f
    Scaffold(
        topBar = {
            AppTopBar(
                title = AppStrings.current.navigation.calendar,
                subtitle = weekRangeLabel(calState.visibleDates),
                actions = {
                    com.tkolymp.tkolympapp.components.ManageCreateAction(
                        label = AppStrings.current.management.newEvent,
                        onClick = onCreateEvent
                    )
                    val shiftWeek = { delta: Int ->
                        val start = customStartDate
                        if (start != null) customStartDate = start.plus(delta * 7, DateTimeUnit.DAY)
                        else { localWeekOffset += delta; onWeekOffsetChange(localWeekOffset) }
                        selectedDateIso = null
                    }
                    if (localWeekOffset != 0 || customStartDate != null) {
                        IconButton(onClick = {
                            customStartDate = null
                            localWeekOffset = 0
                            onWeekOffsetChange(0)
                            selectedDateIso = null
                        }) { Icon(Icons.Filled.Today, contentDescription = AppStrings.current.calendarView.resetToToday) }
                    }
                    IconButton(onClick = { shiftWeek(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = AppStrings.current.calendarView.previous)
                    }
                    IconButton(onClick = { shiftWeek(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = AppStrings.current.calendarView.next)
                    }
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .onGloballyPositioned { coords ->
                                val b = coords.boundsInRoot()
                                filterBounds = b
                                if (tutorialActive && tutorialStep == 8) TutorialHighlight.rect = b
                            }
                    ) {
                        BadgedBox(
                            badge = {
                                if (calState.hasCancelledMineToShow || selectedTrainers.isNotEmpty() || selectedLocations.isNotEmpty()) {
                                    Badge(modifier = Modifier.size(8.dp))
                                }
                            }
                        ) {
                            IconButton(onClick = { showBottomSheet = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = AppStrings.current.commonActions.options)
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
                // week strip, mode toggle and chips are rendered below, once the day data is known

                // Compute filtered+offline-merged data once; only recomputes when filter
                // inputs actually change — not on every unrelated recomposition.
                val filteredEventsByDay = remember(
                    calState.visibleDates,
                    calState.lessonsByTrainerByDay,
                    calState.otherEventsByDay,
                    calState.isOffline,
                    cachedVisibleDates.value,
                    cachedLessonsByTrainer.value,
                    cachedOtherEvents.value,
                    selectedTrainers,
                    selectedLocations
                ) {
                    val visibleDates = if (calState.visibleDates.isEmpty() && calState.isOffline && cachedVisibleDates.value != null)
                        cachedVisibleDates.value.orEmpty() else calState.visibleDates
                    visibleDates.mapNotNull { date ->
                        val lessonsByTrainer = if ((calState.lessonsByTrainerByDay[date].orEmpty().isEmpty()) && calState.isOffline && cachedLessonsByTrainer.value != null)
                            cachedLessonsByTrainer.value?.getOrElse(date) { emptyMap() } ?: emptyMap()
                        else calState.lessonsByTrainerByDay[date] ?: emptyMap()
                        val otherList = if ((calState.otherEventsByDay[date].orEmpty().isEmpty()) && calState.isOffline && cachedOtherEvents.value != null)
                            cachedOtherEvents.value?.getOrElse(date) { emptyList() } ?: emptyList()
                        else calState.otherEventsByDay[date] ?: emptyList()
                        val fl = lessonsByTrainer
                            .let { if (selectedTrainers.isEmpty()) it else it.filterKeys { k -> k in selectedTrainers } }
                            .let { m -> if (selectedLocations.isEmpty()) m else m.filter { (_, instances) ->
                                instances.mapNotNull { i -> i.event?.locationText?.takeIf { it.isNotBlank() } ?: i.event?.location?.name?.takeIf { it.isNotBlank() } }.firstOrNull()
                                    ?.let { it in selectedLocations } == true
                            }}
                        val fo = otherList
                            .let { list -> if (selectedTrainers.isEmpty()) list else list.filter { inst -> inst.event?.eventTrainersList?.any { it in selectedTrainers } == true } }
                            .let { list -> if (selectedLocations.isEmpty()) list else list.filter { inst ->
                                val loc = inst.event?.locationText?.takeIf { it.isNotBlank() } ?: inst.event?.location?.name?.takeIf { it.isNotBlank() }
                                loc != null && loc in selectedLocations
                            }}
                        if (fl.isEmpty() && fo.isEmpty()) null else Triple(date, fl, fo)
                    }
                }

                val filteredByDate = remember(filteredEventsByDay) { filteredEventsByDay.associateBy { it.first } }
                val calStrings = AppStrings.current.calendarView
                fun dayInstances(date: String): List<EventInstance> {
                    val t = filteredByDate[date] ?: return emptyList()
                    return (t.second.values.flatten() + t.third)
                        .filter { !hideCancelled || !it.isCancelled }
                        .filter { selectedTypes.isEmpty() || it.event?.type?.lowercase() in selectedTypes }
                        .filter { !onlyFree || timelineInfo(it, calState.myPersonId, calState.myCoupleIds).isFree }
                        .sortedBy { it.since ?: "" }
                }
                val visibleDates = calState.visibleDates
                val activeDate = activeDateIso
                val activeFilterCount = selectedTrainers.size + selectedLocations.size + selectedTypes.size + (if (onlyFree) 1 else 0) + (if (hideCancelled) 1 else 0)

                val dayCells = visibleDates.mapIndexed { i, iso ->
                    val ld = try { LocalDate.parse(iso) } catch (_: Exception) { null }
                    WeekDayCell(
                        abbr = calStrings.weekDayAbbreviations.getOrElse(ld?.dayOfWeek?.ordinal ?: i) { "" },
                        number = ld?.day ?: 0,
                        count = dayInstances(iso).size,
                        selected = iso == activeDate,
                        isToday = iso == calState.todayString
                    )
                }
                WeekStrip(days = dayCells, onSelect = { i -> selectedDateIso = visibleDates.getOrNull(i) })

                ChipRow {
                    SegmentedToggle(labels = tabs, selectedIndex = selectedTab, onSelect = { selectedTab = it })
                    FilterButton(label = calStrings.filters, activeCount = activeFilterCount, onClick = { showBottomSheet = true })
                    QuickChip(calStrings.onlyFree, onlyFree, { onlyFree = !onlyFree })
                    QuickChip(calStrings.hideCancelled, hideCancelled, { hideCancelled = !hideCancelled })
                    availableLocations.forEach { loc ->
                        QuickChip(loc, loc in selectedLocations, {
                            selectedLocations = if (loc in selectedLocations) selectedLocations - loc else selectedLocations + loc
                        })
                    }
                    selectedTrainers.forEach { tr ->
                        QuickChip(tr, true, { selectedTrainers = selectedTrainers - tr }, removable = true)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { coords ->
                            val b = coords.boundsInRoot()
                            contentBounds = b
                            if (tutorialActive && tutorialStep in 6..7) TutorialHighlight.rect = b
                        }
                ) {
                    if (activeDate == null) {
                        if (!calState.isLoading) {
                            EmptyState(title = calStrings.emptyCalendar, icon = Icons.Outlined.CalendarMonth, fullPage = true)
                        }
                    } else {
                        val instances = dayInstances(activeDate)
                        val competitions = calState.competitionsByDay[activeDate] ?: emptyList()
                        val birthdays = calState.birthdaysByDay[activeDate] ?: emptyList()
                        val ld = try { LocalDate.parse(activeDate) } catch (_: Exception) { null }
                        val dayLabel = ld?.let {
                            val full = formatFullCalendarDate(it, AppStrings.currentLanguage.code, false)
                            if (activeDate == calState.todayString) AppStrings.current.timeline.today + " · " + full else full
                        } ?: activeDate
                        val myId = calState.myPersonId
                        val myCouples = calState.myCoupleIds
                        val freeCount = instances.count { timelineInfo(it, myId, myCouples).isFree }
                        val summary = if (instances.isEmpty()) "" else listOfNotNull(
                            calStrings.eventsCount.replace("%d", instances.size.toString()),
                            calStrings.freeCount.replace("%d", freeCount.toString()).takeIf { freeCount > 0 }
                        ).joinToString(" · ")

                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.default)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = Spacing.default, end = Spacing.default, top = Spacing.medium, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Text(
                                        dayLabel.uppercase(),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (birthdays.isNotEmpty()) BirthdaySummary(birthdays = birthdays)
                                }
                                if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            if (instances.isEmpty() && competitions.isEmpty()) {
                                val filtered = activeFilterCount > 0 && (filteredByDate.isNotEmpty() || calState.lessonsByTrainerByDay.isNotEmpty() || calState.otherEventsByDay.isNotEmpty())
                                DayEmptyState(
                                    text = calStrings.noEventsThisDay,
                                    icon = if (filtered) Icons.Outlined.FilterAltOff else Icons.Outlined.EventBusy,
                                    resetLabel = if (filtered) calStrings.clearFilters else null,
                                    onReset = {
                                        selectedTrainers = emptySet(); selectedLocations = emptySet(); selectedTypes = emptySet(); onlyFree = false; hideCancelled = false
                                    }
                                )
                            } else if (instances.isNotEmpty()) {
                                val onOpen = { inst: EventInstance -> inst.event?.id?.let { onOpenEvent(it, inst.id) }; Unit }
                                if (selectedTab == 0) {
                                    val primary = MaterialTheme.colorScheme.primary
                                    val muted = MaterialTheme.colorScheme.onSurfaceVariant
                                    val ok = successColor()
                                    Column(Modifier.padding(horizontal = Spacing.default)) {
                                        instances.forEachIndexed { i, inst ->
                                            val info = timelineInfo(inst, myId, myCouples)
                                            TimelineRow(
                                                time = formatTimes(inst.since, null),
                                                dot = when {
                                                    info.isFree -> TimelineDot.Free
                                                    info.cohortColor != null -> TimelineDot.Filled(info.cohortColor)
                                                    info.isMine -> TimelineDot.Filled(primary)
                                                    else -> TimelineDot.Filled(muted)
                                                },
                                                title = if (info.isFree) AppStrings.current.overview.freeLesson else info.title,
                                                subtitle = info.subtitle,
                                                isFirst = i == 0,
                                                isLast = i == instances.lastIndex,
                                                onClick = { onOpen(inst) },
                                                titleColor = if (info.isFree) ok else MaterialTheme.colorScheme.onSurface,
                                                strikeThrough = inst.isCancelled
                                            )
                                        }
                                    }
                                } else {
                                    SegmentedToggle(
                                        labels = listOf(calStrings.viewTime, calStrings.viewTrainers),
                                        selectedIndex = if (trainerView) 1 else 0,
                                        onSelect = { trainerView = it == 1 },
                                        modifier = Modifier.padding(horizontal = Spacing.default, vertical = Spacing.xs)
                                    )
                                    if (!trainerView) {
                                        val byHour = instances.groupBy { parseToLocal(it.since)?.hour ?: -1 }.toSortedMap()
                                        byHour.forEach { (hour, list) ->
                                            HourHeader(
                                                hour = if (hour < 0) "" else hour.toString().padStart(2, '0') + ":00",
                                                count = calStrings.eventsCount.replace("%d", list.size.toString())
                                            )
                                            ScheduleCard(Modifier.padding(start = Spacing.default, end = Spacing.default, bottom = Spacing.xs)) {
                                                list.forEachIndexed { i, inst ->
                                                    if (i > 0) RowDivider()
                                                    EventScheduleRow(inst, myId, myCouples, withTrainer = true, onClick = { onOpen(inst) })
                                                }
                                            }
                                        }
                                    } else {
                                        val lessons = instances.filter { it.event?.type.equals("lesson", ignoreCase = true) }
                                        val others = instances - lessons.toSet()
                                        Column(Modifier.padding(horizontal = Spacing.default), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            if (others.isNotEmpty()) {
                                                ScheduleCard {
                                                    others.forEachIndexed { i, inst ->
                                                        if (i > 0) RowDivider()
                                                        EventScheduleRow(inst, myId, myCouples, withTrainer = true, onClick = { onOpen(inst) })
                                                    }
                                                }
                                            }
                                            lessons.groupBy { it.event.firstTrainerOrEmpty() }.forEach { (trainer, list) ->
                                                val free = list.count { timelineInfo(it, myId, myCouples).isFree }
                                                val place = list.firstNotNullOfOrNull { it.event?.locationText?.takeIf(String::isNotBlank) ?: it.event?.location?.name?.takeIf(String::isNotBlank) }
                                                TrainerCard(
                                                    name = trainer,
                                                    summary = listOfNotNull(
                                                        place,
                                                        calStrings.eventsCount.replace("%d", list.size.toString()),
                                                        formatTimes(list.first().since, list.last().until)
                                                    ).joinToString(" · "),
                                                    freeBadge = free.takeIf { it > 0 }?.let { calStrings.freeCount.replace("%d", it.toString()) },
                                                    expanded = trainer !in collapsedTrainers,
                                                    onToggle = { collapsedTrainers = if (trainer in collapsedTrainers) collapsedTrainers - trainer else collapsedTrainers + trainer }
                                                ) {
                                                    list.forEach { inst ->
                                                        RowDivider()
                                                        EventScheduleRow(inst, myId, myCouples, withTrainer = false, onClick = { onOpen(inst) })
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (competitions.isNotEmpty()) {
                                Column(Modifier.padding(horizontal = Spacing.default, vertical = Spacing.small)) {
                                    competitions
                                        .groupBy { it.eventId?.toString() ?: it.eventName ?: it.competitionDate }
                                        .values
                                        .forEach { eventComps -> CompetitionEventCard(competitions = eventComps) }
                                }
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

    // Scrim — same window as Scaffold, no Dialog, no keyboard flash
    AnimatedVisibility(
        visible = showBottomSheet,
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
                ) { showBottomSheet = false }
        )
    }

    AnimatedVisibility(
        visible = showBottomSheet,
        enter = slideInVertically(tween(300)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(200)),
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = sheetMaxHeight)
                .offset { IntOffset(0, sheetDragOffset.value.toInt().coerceAtLeast(0)) }
                .pointerInput(Unit) {
                    val dismissThreshold = 100.dp.toPx()
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (sheetDragOffset.value > dismissThreshold) {
                                showBottomSheet = false
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
                        ) { showBottomSheet = false }
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
                val sheetTrainers = availableTrainers
                    .map { t -> t to rawDay.count { it.event?.eventTrainersList?.contains(t) == true } }
                    .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
                CalendarBottomSheetContent(
                    layout = when {
                        selectedTab == 1 && trainerView -> CalendarLayout.Trainers
                        else -> CalendarLayout.List
                    },
                    showTrainersLayout = selectedTab == 1,
                    onLayoutSelect = { l ->
                        when (l) {
                            CalendarLayout.List -> trainerView = false
                            CalendarLayout.Trainers -> trainerView = true
                            CalendarLayout.Axis -> { showBottomSheet = false; onNavigateTimeline?.invoke() }
                        }
                    },
                    axisAvailable = onNavigateTimeline != null,
                    onlyFree = onlyFree,
                    onOnlyFreeChange = { onlyFree = it },
                    hideCancelled = hideCancelled,
                    onHideCancelledChange = { hideCancelled = it },
                    lessonCount = rawDay.count { it.event?.type.equals("lesson", ignoreCase = true) },
                    groupCount = rawDay.count { it.event?.type.equals("group", ignoreCase = true) },
                    selectedTypes = selectedTypes,
                    onTypeToggle = { t -> selectedTypes = if (t in selectedTypes) selectedTypes - t else selectedTypes + t },
                    locations = availableLocations.map { l -> l to rawDay.count { locationOf(it) == l } },
                    selectedLocations = selectedLocations,
                    onLocationToggle = { location ->
                        selectedLocations = if (location in selectedLocations) selectedLocations - location else selectedLocations + location
                    },
                    trainers = sheetTrainers,
                    selectedTrainers = selectedTrainers,
                    onTrainerToggle = { trainer ->
                        selectedTrainers = if (trainer in selectedTrainers) selectedTrainers - trainer else selectedTrainers + trainer
                    },
                    activeFilterCount = selectedTrainers.size + selectedLocations.size + selectedTypes.size + (if (onlyFree) 1 else 0) + (if (hideCancelled) 1 else 0),
                    onResetFilters = {
                        selectedTrainers = emptySet(); selectedLocations = emptySet(); selectedTypes = emptySet()
                        onlyFree = false; hideCancelled = false
                    },
                    resultCount = rawDay.count { passesFilters(it) },
                    onClose = { showBottomSheet = false }
                )
            }
        }
    }
    } // Box
}

private enum class CalendarLayout { List, Trainers, Axis }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarBottomSheetContent(
    layout: CalendarLayout,
    showTrainersLayout: Boolean,
    onLayoutSelect: (CalendarLayout) -> Unit,
    axisAvailable: Boolean,
    onlyFree: Boolean,
    onOnlyFreeChange: (Boolean) -> Unit,
    hideCancelled: Boolean,
    onHideCancelledChange: (Boolean) -> Unit,
    lessonCount: Int,
    groupCount: Int,
    selectedTypes: Set<String>,
    onTypeToggle: (String) -> Unit,
    locations: List<Pair<String, Int>>,
    selectedLocations: Set<String>,
    onLocationToggle: (String) -> Unit,
    trainers: List<Pair<String, Int>>,
    selectedTrainers: Set<String>,
    onTrainerToggle: (String) -> Unit,
    activeFilterCount: Int,
    onResetFilters: () -> Unit,
    resultCount: Int,
    onClose: () -> Unit
) {
    val strings = AppStrings.current.calendarView
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = Spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.filters, modifier = Modifier.weight(1f), fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
            if (activeFilterCount > 0) {
                Text(
                    strings.clearFilters,
                    modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onResetFilters).padding(top = 14.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = Spacing.small)
        ) {
            SheetSectionLabel(strings.layoutSection)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                LayoutCard(Icons.Default.ViewAgenda, strings.viewList, layout == CalendarLayout.List, { onLayoutSelect(CalendarLayout.List) }, Modifier.weight(1f))
                if (showTrainersLayout) {
                    LayoutCard(Icons.Default.Person, strings.viewTrainers, layout == CalendarLayout.Trainers, { onLayoutSelect(CalendarLayout.Trainers) }, Modifier.weight(1f))
                }
                if (axisAvailable) {
                    LayoutCard(Icons.Default.ViewTimeline, strings.viewAxis, false, { onLayoutSelect(CalendarLayout.Axis) }, Modifier.weight(1f))
                }
            }

            SheetSectionLabel(strings.showSection)
            SheetSwitchRow(strings.onlyFreeLessons, strings.onlyFreeLessonsDesc, onlyFree, onOnlyFreeChange)
            SheetSwitchRow(strings.hideCancelled, strings.hideCancelledDesc, hideCancelled, onHideCancelledChange)

            SheetSectionLabel(strings.typeSection)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                SheetChip(strings.typeLessons, lessonCount, "lesson" in selectedTypes, { onTypeToggle("lesson") })
                SheetChip(strings.typeGroups, groupCount, "group" in selectedTypes, { onTypeToggle("group") })
            }

            if (locations.size >= 2) {
                SheetSectionLabel(strings.placeSection)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    locations.forEach { (loc, n) -> SheetChip(loc, n, loc in selectedLocations, { onLocationToggle(loc) }) }
                }
            }

            if (trainers.size >= 2) {
                SheetSectionLabel(strings.trainerSection)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    trainers.forEach { (name, n) ->
                        SheetChip(name, n, name in selectedTrainers, { onTrainerToggle(name) }, leading = {
                            InitialsAvatar(name = name, size = 24.dp, fontSize = 10.sp)
                        })
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(line))
        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = Spacing.medium, bottom = Spacing.default + LocalBottomBarPadding.current)) {
            SheetPrimaryButton(
                text = if (resultCount > 0) strings.showResults.replace("%d", resultCount.toString()) else strings.noResults,
                onClick = onClose
            )
        }
    }
}

/** Single-line birthday names; when they don't fit, a "+N" chip opens a bottom sheet listing everyone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdaySummary(birthdays: List<BirthdayEntry>, modifier: Modifier = Modifier) {
    var hiddenCount by remember(birthdays) { mutableIntStateOf(0) }
    var showSheet by remember { mutableStateOf(false) }
    val text = remember(birthdays) { birthdays.joinToString(", ") { it.name } }

    Row(
        modifier = modifier
            .then(if (hiddenCount > 0) Modifier.clickable { showSheet = true } else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Cake,
            contentDescription = AppStrings.current.profile.birthdays,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout ->
                hiddenCount = if (layout.hasVisualOverflow) {
                    val visibleEnd = layout.getLineEnd(0, visibleEnd = true)
                    var offset = 0
                    var shown = 0
                    for (b in birthdays) {
                        offset += b.name.length
                        if (offset > visibleEnd) break
                        shown++
                        offset += 2
                    }
                    (birthdays.size - shown).coerceAtLeast(1)
                } else 0
            },
            modifier = Modifier.weight(1f, fill = false)
        )
        if (hiddenCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "+$hiddenCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    AppStrings.current.profile.birthdays,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                birthdays.forEach { b ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Cake,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(b.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        b.turningAge?.let {
                            Text(
                                AppStrings.current.profile.yearsOld.replace("%d", it.toString()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** [ScheduleRow] for an [EventInstance], choosing mark colour and trailing tag the way the prototype does. */
@Composable
private fun EventScheduleRow(
    inst: EventInstance,
    myPersonId: String?,
    myCoupleIds: List<String>,
    withTrainer: Boolean,
    onClick: () -> Unit
) {
    val info = timelineInfo(inst, myPersonId, myCoupleIds)
    val strings = AppStrings.current.calendarView
    val ok = successColor()
    val isLesson = inst.event?.type.equals("lesson", ignoreCase = true)
    val trainer = inst.event.firstTrainerOrEmpty()
    val place = inst.event?.locationText?.takeIf { it.isNotBlank() } ?: inst.event?.location?.name.orEmpty()
    val mark = when {
        !isLesson && info.cohortColor != null -> info.cohortColor
        info.isFree -> ok
        info.isMine -> MaterialTheme.colorScheme.primary
        inst.isCancelled -> Color.Transparent
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    }
    val subtitle = when {
        !isLesson -> listOf(trainer, place)
        withTrainer -> listOf(trainer, place)
        else -> emptyList()
    }.filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }
    ScheduleRow(
        time = formatTimes(inst.since, null),
        mark = mark,
        title = if (info.isFree) AppStrings.current.overview.freeLesson else info.title,
        subtitle = subtitle,
        tag = when {
            info.isFree -> ScheduleTag.Free(strings.signUpShort)
            inst.isCancelled -> ScheduleTag.Cancelled(strings.cancelledTag)
            info.isMine -> ScheduleTag.Mine(strings.youTag)
            else -> standardDurationMinutes(inst.since, inst.until)?.let { ScheduleTag.Duration("$it'") } ?: ScheduleTag.None
        },
        onClick = onClick,
        highlighted = info.isMine && !info.isFree,
        strikeThrough = inst.isCancelled,
        emphasized = info.isMine || !isLesson,
        titleColor = when {
            info.isFree -> ok
            inst.isCancelled -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        }
    )
}

private fun weekRangeLabel(visibleDates: List<String>): String? {
    val first = visibleDates.firstOrNull()?.let { try { LocalDate.parse(it) } catch (_: Exception) { null } } ?: return null
    val last = visibleDates.lastOrNull()?.let { try { LocalDate.parse(it) } catch (_: Exception) { null } } ?: return null
    val lang = AppStrings.currentLanguage.code
    val lastMonth = getLocalizedMonthName(last.month.number, lang)
    return if (first.month == last.month) "${first.day}.–${last.day}. $lastMonth"
    else "${first.day}. ${getLocalizedMonthName(first.month.number, lang)} – ${last.day}. $lastMonth"
}
