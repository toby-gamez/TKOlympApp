package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.formatFullCalendarDate
import com.tkolymp.shared.utils.formatHtmlContent
import com.tkolymp.shared.viewmodels.OverviewViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    bottomPadding: Dp = 0.dp,
    onOpenEvent: (Long) -> Unit = {},
    onOpenNotice: (Long) -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenBoard: () -> Unit = {},
    onOpenEvents: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(AppStrings.current.navigation.overview) })
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        val viewModel = viewModel<OverviewViewModel>()
        val state by viewModel.state.collectAsState()
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
            val startIso = today.toString() + "T00:00:00Z"
            val endIso = today.plus(365, DateTimeUnit.DAY).toString() + "T23:59:59Z"
            viewModel.loadOverview(startIso, endIso, forceRefresh = false)
        }

        SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch {
                val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
                val startIso = today.toString() + "T00:00:00Z"
                val endIso = today.plus(365, DateTimeUnit.DAY).toString() + "T23:59:59Z"
                viewModel.loadOverview(startIso, endIso, forceRefresh = true)
            } },
            modifier = Modifier.padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {

            val trainings = state.upcomingEvents
            val camps = trainings.filter { it.event?.type?.contains("CAMP", ignoreCase = true) == true }
            val trainingItems = remember(trainings) { trainings.take(2).map { Pair(it.id, it.event?.name ?: AppStrings.current.dialogs.noName) } }
            val campItems = remember(camps) { camps.take(2).map { Pair(it.id, it.event?.name ?: AppStrings.current.dialogs.noName) } }
            val announcements = state.recentAnnouncements

            // Trainings section (styled like Calendar)
            // Trainings section (grouped by day, styled like Calendar)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(AppStrings.current.overview.upcomingTrainings, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            
            val limitedTrainings = trainings.sortedBy { it.since ?: it.updatedAt ?: "" }
            val trainingsMapByDay = limitedTrainings.groupBy { inst ->
                val s = inst.since ?: inst.until ?: inst.updatedAt ?: ""
                s.substringBefore('T').ifEmpty { s }
            }.entries.sortedBy { it.key }.associate { it.key to it.value }

            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (trainingsMapByDay.isEmpty()) {
                    if (state.isLoading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Text(AppStrings.current.timeline.nothingPlanned, modifier = Modifier.padding(vertical = 6.dp))
                    }
                } else {
                    val todayKey = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()

                    // Show only one day: prefer today (only if it has future events), otherwise the next training day
                    val sortedKeys = trainingsMapByDay.keys.sorted()
                    val nowInstant = kotlin.time.Clock.System.now()
                    val selectedKey = run {
                        if (sortedKeys.isEmpty()) null
                        else if (sortedKeys.contains(todayKey)) {
                            val todayList = trainingsMapByDay[todayKey] ?: emptyList()
                            val hasFutureToday = todayList.any { inst ->
                                val timeStr = inst.until ?: inst.since ?: inst.updatedAt ?: ""
                                val instInstant = try { Instant.parse(timeStr) } catch (_: Exception) { null }
                                instInstant != null && instInstant > nowInstant
                            }
                            if (hasFutureToday) todayKey else sortedKeys.find { it > todayKey } ?: sortedKeys.firstOrNull()
                        } else {
                            sortedKeys.find { it > todayKey } ?: sortedKeys.firstOrNull()
                        }
                    }

                    selectedKey?.let { date ->
                        val list = trainingsMapByDay[date] ?: emptyList()
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            val now = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
                            val header = when (date) {
                                todayKey -> AppStrings.current.timeline.today.lowercase()
                                now.plus(1, DateTimeUnit.DAY).toString() -> AppStrings.current.timeline.tomorrow.lowercase()
                                else -> {
                                    val ld = try { LocalDate.parse(date) } catch (_: Exception) { null }
                                    if (ld == null) date else
                                        formatFullCalendarDate(ld, AppStrings.currentLanguage.code, ld.year != now.year)
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
                                    myPersonId = state.myPersonId,
                                    myCoupleIds = state.myCoupleIds,
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
            val trainingsEmpty = trainingsMapByDay.isEmpty()
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onOpenCalendar) { Text(if (trainingsEmpty) AppStrings.current.overview.browseOthers else AppStrings.current.overview.more) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Board announcements (styled like BoardScreen)
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(AppStrings.current.overview.fromTheBoard, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (announcements.isEmpty()) {
                    if (state.isLoading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Text(AppStrings.current.timeline.nothingPlanned, modifier = Modifier.padding(vertical = 6.dp))
                    }
                } else {
                    announcements.forEach { a ->
                        Column(modifier = Modifier.padding(4.dp)) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        a.id.toLongOrNull()?.let { nid -> onOpenNotice(nid) }
                                    },
                                shape = RoundedCornerShape(16.dp),
                                
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(a.title ?: AppStrings.current.dialogs.noName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    val authorName = listOfNotNull(a.author?.uJmeno, a.author?.uPrijmeni).joinToString(" ").trim()
                                    if (authorName.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(authorName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    var plainBody = formatHtmlContent(a.body ?: "")
                                    if (plainBody.isNotBlank()) plainBody = plainBody.trimEnd() + "..."
                                    Text(
                                        plainBody,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            val boardEmpty = announcements.isEmpty()
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onOpenBoard) { Text(if (boardEmpty) AppStrings.current.overview.browseOthers else AppStrings.current.overview.more) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Camps section (styled like Calendar)
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(AppStrings.current.overview.upcomingCamps, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            val limitedCamps = camps.sortedBy { it.since ?: it.updatedAt ?: "" }.take(2)
            val campsMapByDay = limitedCamps.groupBy { inst ->
                val s = inst.since ?: inst.until ?: inst.updatedAt ?: ""
                s.substringBefore('T').ifEmpty { s }
            }.entries.sortedBy { it.key }.associate { it.key to it.value }

            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (campsMapByDay.isEmpty()) {
                    if (state.isLoading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Text(AppStrings.current.timeline.nothingPlanned, modifier = Modifier.padding(vertical = 6.dp))
                    }
                } else {
                    val todayKey = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()

                    campsMapByDay.forEach { (date, list) ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            val now = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
                            val header = when (date) {
                                todayKey -> AppStrings.current.timeline.today.lowercase()
                                now.plus(1, DateTimeUnit.DAY).toString() -> AppStrings.current.timeline.tomorrow.lowercase()
                                else -> {
                                    val ld = try { LocalDate.parse(date) } catch (_: Exception) { null }
                                    if (ld == null) date else
                                        formatFullCalendarDate(ld, AppStrings.currentLanguage.code, ld.year != now.year)
                                }
                            }
                            Text(header, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))

                            list.sortedBy { it.since }.forEach { item ->
                                RenderSingleEventCard(item = item, onEventClick = { id: Long -> onOpenEvent(id) })
                            }
                        }
                    }
                }
            }
            val campsEmpty = campsMapByDay.isEmpty()
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onOpenEvents) { Text(if (campsEmpty) AppStrings.current.overview.browseOthers else AppStrings.current.overview.more) }
            }

            if (state.isLoading) {
                Text("Načítám...", modifier = Modifier.padding(12.dp))
            }
            state.error?.let { Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        }
    }
}

}

@Composable
private fun OverviewSection(
    title: String,
    items: List<Pair<Long, String>>,
    onMore: () -> Unit,
    onItemClick: (Long) -> Unit
) {
    Row(modifier = Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onMore) { Text(if (items.isEmpty()) AppStrings.current.overview.browseOthers else AppStrings.current.overview.more) }
    }
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        if (items.isEmpty()) {
            Text(AppStrings.current.timeline.nothingPlanned, modifier = Modifier.padding(vertical = 6.dp))
        } else {
            items.forEach { (id, label) ->
                Text(
                    text = label,
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .clickable { onItemClick(id) }
                )
            }
        }
    }
}
