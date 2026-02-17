package com.tkolymp.tkolympapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.collectAsState
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
import com.tkolymp.shared.viewmodels.EventsViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(bottomPadding: Dp = 0.dp, onOpenEvent: (Long) -> Unit = {}) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Naplánováno", "Proběhlé")

    val viewModel = remember { EventsViewModel() }
    val state by viewModel.state.collectAsState()

    val today = LocalDate.now()
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    val scope = rememberCoroutineScope()
    LaunchedEffect(selectedTab) {
        // load when screen first composes and when switching tabs to ensure data is present
        // use cache by default; do not force network call on each tab switch
        scope.launch { viewModel.loadCampsNextYear() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Akce") }) }
    ) { padding ->
            SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch { viewModel.loadCampsNextYear(forceRefresh = true) } },
            modifier = Modifier.padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }
            

            // keep content visible during refresh; SwipeToReload shows the progress indicator

            // Ensure we only show CAMP events here (defensive: viewmodel should already filter)
            val grouped = state.eventsByDay.mapValues { entry ->
                entry.value.filter { it.event?.type?.equals("CAMP", ignoreCase = true) == true }
            }.filterValues { it.isNotEmpty() }

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
                    }.entries.sortedBy { it.key }.associate { it.key to it.value }

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

            state.error?.let { err ->
                AlertDialog(
                    onDismissRequest = { /* no-op */ },
                    confirmButton = { TextButton(onClick = { /* no-op */ }) { Text("OK") } },
                    title = { Text("Chyba při načítání akcí") },
                    text = { Text(err) }
                )
            }
        }
    }
}}
