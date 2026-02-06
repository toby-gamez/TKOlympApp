package com.tkolymp.tkolympapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.event.EventInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(bottomPadding: Dp = 0.dp, onOpenEvent: (Long) -> Unit = {}) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Naplánováno", "Proběhlé")

    val eventsByDayState = remember { mutableStateOf<Map<String, List<EventInstance>>>(emptyMap()) }
    val isLoading = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val today = LocalDate.now()
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    LaunchedEffect(Unit) {
        isLoading.value = true
        errorMessage.value = null
            try {
            val start = LocalDate.of(2023, 1, 1)
            val end = today.plusYears(1)
            val startIso = start.toString() + "T00:00:00Z"
            val endIso = end.toString() + "T23:59:59Z"
            val svc = ServiceLocator.eventService
            val map = withContext(Dispatchers.IO) {
                svc.fetchEventsGroupedByDay(startIso, endIso, false, 500, 0, "CAMP")
            }

            // filter out non-visible events
            val filtered = map.mapValues { entry ->
                entry.value.filter { it.event?.isVisible != false }
            }.filterValues { it.isNotEmpty() }

            eventsByDayState.value = filtered
        } catch (ex: Exception) {
            errorMessage.value = ex.message ?: "Chyba při načítání"
        } finally {
            isLoading.value = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Akce") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = bottomPadding),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }

            if (isLoading.value) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            val grouped = eventsByDayState.value

            Column(modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
            ) {
                if (selectedTab == 0) {
                    // Naplánováno: today..future (ascending)
                    val planned = grouped.filter { (dateStr, _) ->
                        val d = try { LocalDate.parse(dateStr, fmt) } catch (_: Exception) { null }
                        d != null && !d.isBefore(today)
                    }.toSortedMap()

                    if (planned.isEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Žádné naplánované akce", style = MaterialTheme.typography.bodyMedium)
                    }

                    planned.forEach { (date, list) ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            val header = when (date) {
                                today.format(fmt) -> "dnes"
                                today.plusDays(1).format(fmt) -> "zítra"
                                else -> {
                                    val ld = try { LocalDate.parse(date) } catch (_: Exception) { null }
                                    if (ld == null) date else {
                                        val nowYear = LocalDate.now().year
                                        val pattern = if (ld.year == nowYear) "d. MMMM" else "d. MMMM yyyy"
                                        ld.format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale("cs")))
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
                                    isAllTab = false,
                                    myPersonId = null,
                                    myCoupleIds = emptyList(),
                                    onEventClick = onOpenEvent
                                )
                            }

                            other.sortedBy { it.since }.forEach { item ->
                                RenderSingleEventCard(item = item, onEventClick = onOpenEvent, showType = false)
                            }
                        }
                    }

                } else {
                    // Proběhlé: yesterday -> past (descending)
                    val yesterday = today.minusDays(1)
                    val past = grouped.filter { (dateStr, _) ->
                        val d = try { LocalDate.parse(dateStr, fmt) } catch (_: Exception) { null }
                        d != null && !d.isAfter(yesterday)
                    }.toList().sortedByDescending { (dateStr, _) ->
                        try { LocalDate.parse(dateStr, fmt) } catch (_: Exception) { LocalDate.MIN }
                    }

                    if (past.isEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Žádné proběhlé akce", style = MaterialTheme.typography.bodyMedium)
                    }

                    past.forEach { (date, list) ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            val header = when (date) {
                                today.format(fmt) -> "dnes"
                                today.plusDays(1).format(fmt) -> "zítra"
                                else -> {
                                    val ld = try { LocalDate.parse(date) } catch (_: Exception) { null }
                                    if (ld == null) date else {
                                        val nowYear = LocalDate.now().year
                                        val pattern = if (ld.year == nowYear) "d. MMMM" else "d. MMMM yyyy"
                                        ld.format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale("cs")))
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
                                    isAllTab = false,
                                    myPersonId = null,
                                    myCoupleIds = emptyList(),
                                    onEventClick = onOpenEvent
                                )
                            }

                            other.sortedByDescending { it.since }.forEach { item ->
                                RenderSingleEventCard(item = item, onEventClick = onOpenEvent, showType = false)
                            }
                        }
                    }
                }
            }

            if (errorMessage.value != null) {
                AlertDialog(
                    onDismissRequest = { errorMessage.value = null },
                    confirmButton = { TextButton(onClick = { errorMessage.value = null }) { Text("OK") } },
                    title = { Text("Chyba při načítání akcí") },
                    text = { Text(errorMessage.value ?: "Neznámá chyba") }
                )
            }
        }
    }
}
