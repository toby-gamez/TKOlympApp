
package com.tkolymp.tkolympapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.event.EventInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime
import java.time.LocalDateTime
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.launch


@Composable
fun CalendarScreen(
    weekOffset: Int = 0,
    onWeekOffsetChange: (Int) -> Unit = {},
    onOpenEvent: (Long) -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Moje", "Všechny")
    val eventsByDayState = remember { mutableStateOf<Map<String, List<EventInstance>>>(emptyMap()) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val myPersonId = remember { mutableStateOf<String?>(null) }
    val myCoupleIds = remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // compute start (today + offset) and end (start + 7 days)
    val today = LocalDate.now().plusWeeks(weekOffset.toLong())
    val endDay = today.plusDays(7)
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    val startIso = today.toString() + "T00:00:00Z"
    val endIso = endDay.toString() + "T23:59:59Z"

    LaunchedEffect(selectedTab, weekOffset) {
        val onlyMine = selectedTab == 0
        val svc = ServiceLocator.eventService
        val map = try {
            withContext(Dispatchers.IO) {
                svc.fetchEventsGroupedByDay(startIso, endIso, onlyMine, 200, 0, null)
            }
        } catch (e: Exception) {
            errorMessage.value = e.message ?: "Unknown error"
            emptyMap()
        }
        eventsByDayState.value = map
    }

    // load cached user/person/couples (used to detect "my" participants)
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                myPersonId.value = ServiceLocator.userService.getCachedPersonId()
            } catch (_: Throwable) { myPersonId.value = null }
            try {
                myCoupleIds.value = ServiceLocator.userService.getCachedCoupleIds()
            } catch (_: Throwable) { myCoupleIds.value = emptyList() }
        }
    }
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

        // display grouped events inside a scrollable area so tabs stay fixed
        val grouped = eventsByDayState.value
        val todayKey = LocalDate.now().format(fmt)

        // calendar remains visible; opening an event is handled by parent via `onOpenEvent`

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
                                val nowYear = LocalDate.now().year
                                val pattern = if (ld.year == nowYear) "d. MMMM" else "d. MMMM yyyy"
                                ld.format(DateTimeFormatter.ofPattern(pattern, Locale("cs")))
                            }
                        }
                    }
                    Text(header, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))

                    // Group lesson events (type == "lesson") by first trainer name
                    // Treat empty strings as missing names: only group when trainer name is non-blank
                    val lessons = list.filter {
                        it.event?.type?.equals("lesson", ignoreCase = true) == true &&
                        !it.event?.eventTrainersList.isNullOrEmpty() &&
                        !it.event?.eventTrainersList?.firstOrNull().isNullOrBlank()
                    }
                    val other = list - lessons

                    val lessonsByTrainer = lessons.groupBy { it.event?.eventTrainersList?.firstOrNull()!!.trim() }

                    // Render grouped lessons as LessonView when group size > 1, otherwise fall back to single-card
                    lessonsByTrainer.forEach { (trainer, instances) ->
                        // Render LessonView even for a single-instance group so "Moje" shows lessons
                        LessonView(
                            trainerName = trainer,
                            instances = instances.sortedBy { it.since },
                            isAllTab = (selectedTab == 1),
                            myPersonId = myPersonId.value,
                            myCoupleIds = myCoupleIds.value,
                            onEventClick = { id -> onOpenEvent(id) }
                        )
                    }

                    // Render other events
                    other.sortedBy { it.since }.forEach { item ->
                        RenderSingleEventCard(item) { id -> onOpenEvent(id) }
                    }
                }
            }
        }

        if (errorMessage.value != null) {
            AlertDialog(
                onDismissRequest = { errorMessage.value = null },
                confirmButton = {
                    TextButton(onClick = { errorMessage.value = null }) { Text("OK") }
                },
                title = { Text("Chyba při načítání akcí") },
                text = { Text(errorMessage.value ?: "Neznámá chyba") }
            )
        }
    }
}


@Composable
private fun PrimaryTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Simple wrapper over TabRow so we can change styling from one place later
    TabRow(selectedTabIndex = selectedTabIndex, modifier = modifier) {
        content()
    }
}

private fun parseColorOrDefault(hex: String?): Color {
    if (hex.isNullOrBlank()) return Color.Gray
    return try {
        var s = hex.trim()
        if (!s.startsWith("#")) s = "#" + s
        Color(android.graphics.Color.parseColor(s))
    } catch (e: Exception) {
        Color.Gray
    }
}

// helpers moved to EventUtils.kt

@Composable
private fun LessonView(
    trainerName: String,
    instances: List<EventInstance>,
    isAllTab: Boolean,
    myPersonId: String?,
    myCoupleIds: List<String>
    , onEventClick: (Long) -> Unit
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
                // show first non-blank location for the group (if any)
                val groupLocation = instances.mapNotNull { inst ->
                    inst.event?.locationText?.takeIf { !it.isNullOrBlank() } ?: inst.event?.location?.name?.takeIf { !it.isNullOrBlank() }
                }.firstOrNull().orEmpty()
                if (groupLocation.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(groupLocation, style = MaterialTheme.typography.bodySmall)
                }
            Spacer(modifier = Modifier.height(8.dp))

            // participants grid: each event -> one row with time, name, duration
            instances.sortedBy { it.since }.forEach { inst ->
                val time = formatTimes(inst.since, inst.until)
                // build participant display names together with ownership flag
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
                    // fixed width time column so times align
                    Text(
                        time,
                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = deco),
                        modifier = Modifier.width(100.dp)
                    )

                    // participants column grows to fill remaining space
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

                    // fixed width duration column, right-aligned
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
private fun RenderSingleEventCard(item: EventInstance, onEventClick: (Long) -> Unit) {
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
                    val typeText = item.event?.type ?: ""
                    val displayType = translateEventType(typeText)
                    if (!displayType.isNullOrBlank()) {
                        Text(displayType, style = MaterialTheme.typography.labelSmall)
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

// helpers moved to EventUtils.kt

