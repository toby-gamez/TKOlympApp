package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.formatHtmlContent
import com.tkolymp.shared.utils.formatMonthDay
import com.tkolymp.shared.viewmodels.EventsViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.util.normalizeForSearch
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(bottomPadding: Dp = 0.dp, onOpenEvent: (Long) -> Unit = {}) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf(AppStrings.current.eventCalendarTabs.planned, AppStrings.current.eventCalendarTabs.past)

    val viewModel = viewModel<EventsViewModel>()
    val state by viewModel.state.collectAsState()

    val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())

    val scope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(showSearch) {
        if (showSearch) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(selectedTab) {
        // load when screen first composes and when switching tabs to ensure data is present
        // use cache by default; do not force network call on each tab switch
        scope.launch { viewModel.loadCampsNextYear() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.current.navigation.events) },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(imageVector = if (showSearch) Icons.Filled.Close else Icons.Filled.Search, contentDescription = AppStrings.current.commonActions.search)
                    }
                }
            )
        }
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
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }
            if (showSearch) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (searchQuery.isNotBlank()) searchQuery = "" else showSearch = false
                        }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = AppStrings.current.commonActions.cancel)
                        }
                    },
                    placeholder = { Text(AppStrings.current.commonActions.search) }
                )
            }
            

            // keep content visible during refresh; SwipeToReload shows the progress indicator

            // Ensure we only show CAMP events here (defensive: viewmodel should already filter)
            var grouped = state.eventsByDay.mapValues { entry ->
                entry.value.filter { it.event?.type?.equals("CAMP", ignoreCase = true) == true }
            }.filterValues { it.isNotEmpty() }

            // Offline fallback handled in EventsViewModel; UI should not scan storage directly.

            // If search is active, filter across all dates (not limited to planned/past)
            val filteredGrouped = if (searchQuery.isBlank()) {
                grouped
            } else {
                val nq = normalizeForSearch(searchQuery.trim())
                grouped.flatMap { (dateStr, list) -> list.map { dateStr to it } }
                    .filter { (_dateStr, item) ->
                        val title = item.event?.name ?: ""
                        val titleOk = title.isNotBlank() && normalizeForSearch(title).contains(nq)
                        val body = try { formatHtmlContent(item.event?.description ?: "") } catch (_: Exception) { "" }
                        val bodyOk = body.isNotBlank() && normalizeForSearch(body).contains(nq)
                        val trainers = (item.event?.eventTrainersList ?: emptyList()).joinToString(" ")
                        val trainersOk = trainers.isNotBlank() && normalizeForSearch(trainers).contains(nq)
                        val typeOk = item.event?.type?.let { normalizeForSearch(it).contains(nq) } == true
                        titleOk || bodyOk || trainersOk || typeOk
                    }
                    .groupBy({ it.first }, { it.second })
                    .filterValues { it.isNotEmpty() }
            }

            Column(modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
            ) {
                if (selectedTab == 0) {
                    // Naplánováno: today..future (ascending)
                    val planned = filteredGrouped.filter { (dateStr, _) ->
                        val d = try { LocalDate.parse(dateStr) } catch (_: Exception) { null }
                        d != null && d >= today
                    }.entries.sortedBy { it.key }.associate { it.key to it.value }

                    if (planned.isEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(AppStrings.current.events.noEventsPlanned, style = MaterialTheme.typography.bodyMedium)
                    }

                    planned.forEach { (date, list) ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            val header = when (date) {
                                today.toString() -> AppStrings.current.timeline.today.lowercase()
                                today.plus(1, DateTimeUnit.DAY).toString() -> AppStrings.current.timeline.tomorrow.lowercase()
                                else -> {
                                    val ld = try { LocalDate.parse(date) } catch (_: Exception) { null }
                                    if (ld == null) date else
                                        formatMonthDay(ld, AppStrings.currentLanguage.code, ld.year != today.year)
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
                    val yesterday = today.minus(1, DateTimeUnit.DAY)
                    val past = filteredGrouped.filter { (dateStr, _) ->
                        val d = try { LocalDate.parse(dateStr) } catch (_: Exception) { null }
                        d != null && d <= yesterday
                    }.toList().sortedByDescending { (dateStr, _) ->
                        try { LocalDate.parse(dateStr) } catch (_: Exception) { LocalDate(1, 1, 1) }
                    }

                    if (past.isEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(AppStrings.current.events.noPastEvents, style = MaterialTheme.typography.bodyMedium)
                    }

                    past.forEach { (date, list) ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            val header = when (date) {
                                today.toString() -> AppStrings.current.timeline.today.lowercase()
                                today.plus(1, DateTimeUnit.DAY).toString() -> AppStrings.current.timeline.tomorrow.lowercase()
                                else -> {
                                    val ld = try { LocalDate.parse(date) } catch (_: Exception) { null }
                                    if (ld == null) date else
                                        formatMonthDay(ld, AppStrings.currentLanguage.code, ld.year != today.year)
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
                    onDismissRequest = { viewModel.clearError() },
                    confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text(AppStrings.current.commonActions.ok) } },
                    title = { Text(AppStrings.current.events.errorLoadingEvents) },
                    text = { Text(err) }
                )
            }
        }
    }
}
}
