
package com.tkolymp.tkolympapp

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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import com.tkolymp.shared.event.EventInstance
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.tkolymp.tkolympapp.SwipeToReload

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    weekOffset: Int = 0,
    onWeekOffsetChange: (Int) -> Unit = {},
    onOpenEvent: (Long) -> Unit = {},
    onNavigateTimeline: (() -> Unit)? = null,
    bottomPadding: Dp = 0.dp
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Moje", "Všechny")
    // moved data loading into shared CalendarViewModel
    val calendarViewModel = remember { com.tkolymp.shared.viewmodels.CalendarViewModel() }
    val calState by calendarViewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    val today = LocalDate.now().plusWeeks(weekOffset.toLong())
    val endDay = today.plusDays(7)
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    val startIso = today.toString() + "T00:00:00Z"
    val endIso = endDay.toString() + "T23:59:59Z"

    LaunchedEffect(selectedTab, weekOffset) {
        val onlyMine = selectedTab == 0
        calendarViewModel.load(startIso, endIso, onlyMine)
    }

    // user id / couple ids are provided by calendar viewmodel state

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kalendář") },
                actions = {
                    onNavigateTimeline?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.Default.ViewTimeline, contentDescription = "Timeline zobrazení")
                        }
                    }
                    IconButton(onClick = { onWeekOffsetChange(weekOffset - 1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Předchozí týden")
                    }
                    TextButton(onClick = { onWeekOffsetChange(0) }) { Text("dnes") }
                    IconButton(onClick = { onWeekOffsetChange(weekOffset + 1) }) {
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
                    val onlyMine = selectedTab == 0
                    calendarViewModel.load(startIso, endIso, onlyMine)
                }
            },
            modifier = Modifier.padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
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

                Spacer(modifier = Modifier.height(8.dp))

                val grouped = calState.eventsByDay
                val todayKey = LocalDate.now().format(fmt)

                Column(modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                ) {
                    grouped.forEach { (date, list) ->
                        Column(modifier = Modifier.padding(8.dp)) {
                            val header = when (date) {
                                todayKey -> "dnes"
                                LocalDate.now().plusDays(1).format(fmt) -> "zítra"
                                else -> {
                                    val ld = try { LocalDate.parse(date) } catch (_: Exception) { null }
                                    if (ld == null) date else {
                                        val now = LocalDate.now()
                                        val pattern = if (ld.year == now.year) "EEEE, d. MMMM" else "EEEE, d. MMMM yyyy"
                                        ld.format(DateTimeFormatter.ofPattern(pattern, Locale("cs")))
                                    }
                                }
                            }
                            Text(header, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))

                            val lessons = list.filter {
                                it.event?.type?.equals("lesson", ignoreCase = true) == true &&
                                        !it.event?.eventTrainersList.isNullOrEmpty() &&
                                        !it.event?.eventTrainersList?.firstOrNull().isNullOrBlank()
                            }
                            val other = list - lessons

                            val lessonsByTrainer = lessons.groupBy { it.event?.eventTrainersList?.firstOrNull()!!.trim() }

                            lessonsByTrainer.forEach { (trainer, instances) ->
                                LessonView(
                                    trainerName = trainer,
                                    instances = instances.sortedBy { it.since },
                                    isAllTab = (selectedTab == 1),
                                    myPersonId = calState.myPersonId,
                                    myCoupleIds = calState.myCoupleIds,
                                    onEventClick = { id: Long -> onOpenEvent(id) }
                                )
                            }

                            other.sortedBy { it.since }.forEach { item ->
                                RenderSingleEventCard(item = item, onEventClick = { id: Long -> onOpenEvent(id) })
                            }
                        }
                    }
                }
            }
        }

        calState.error?.let { err ->
            AlertDialog(
                onDismissRequest = { /* dismiss handled by parent state refresh */ },
                confirmButton = {
                    TextButton(onClick = { /* noop */ }) { Text("OK") }
                },
                title = { Text("Chyba při načítání akcí") },
                text = { Text(err) }
            )
        }
    }
}

@Composable
internal fun PrimaryTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    TabRow(selectedTabIndex = selectedTabIndex, modifier = modifier) {
        content()
    }
}

internal fun parseColorOrDefault(hex: String?): Color {
    if (hex.isNullOrBlank()) return Color.Gray
    return try {
        var s = hex.trim()
        if (!s.startsWith("#")) s = "#" + s
        Color(android.graphics.Color.parseColor(s))
    } catch (e: Exception) {
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
                Text("lekce", style = MaterialTheme.typography.labelSmall)
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

