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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import kotlinx.datetime.LocalDate

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
            viewModel.loadOverview(forceRefresh = false)
        }

        SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch { viewModel.loadOverview(forceRefresh = true) } },
            modifier = Modifier.padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {

            val announcements = state.recentAnnouncements

            // Trainings section (grouped by day, styled like Calendar)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(AppStrings.current.overview.upcomingTrainings, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (state.trainingSelectedDate == null && !state.isLoading) {
                    Text(AppStrings.current.timeline.nothingPlanned, modifier = Modifier.padding(vertical = 6.dp))
                } else if (state.trainingSelectedDate == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                } else {
                    val date = state.trainingSelectedDate!!
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        val header = dateHeader(date, state.todayString, state.tomorrowString)
                        Text(header, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))

                        state.trainingLessonsByTrainer.forEach { (trainer, instances) ->
                            LessonView(
                                trainerName = trainer,
                                instances = instances,
                                isAllTab = false,
                                myPersonId = state.myPersonId,
                                myCoupleIds = state.myCoupleIds,
                                onEventClick = { id: Long -> onOpenEvent(id) }
                            )
                        }
                        state.trainingOtherEvents.forEach { item ->
                            RenderSingleEventCard(item = item, onEventClick = { id: Long -> onOpenEvent(id) })
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onOpenCalendar) {
                    Text(if (state.trainingSelectedDate == null) AppStrings.current.overview.browseOthers else AppStrings.current.overview.more)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Board announcements
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(AppStrings.current.overview.fromTheBoard, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (announcements.isEmpty()) {
                    if (state.isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) { CircularProgressIndicator() }
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
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onOpenBoard) {
                    Text(if (announcements.isEmpty()) AppStrings.current.overview.browseOthers else AppStrings.current.overview.more)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Camps section
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(AppStrings.current.overview.upcomingCamps, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (state.campsMapByDay.isEmpty()) {
                    if (state.isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) { CircularProgressIndicator() }
                    } else {
                        Text(AppStrings.current.timeline.nothingPlanned, modifier = Modifier.padding(vertical = 6.dp))
                    }
                } else {
                    state.campsMapByDay.forEach { (date, list) ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            val header = dateHeader(date, state.todayString, state.tomorrowString)
                            Text(header, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            list.forEach { item ->
                                RenderSingleEventCard(item = item, onEventClick = { id: Long -> onOpenEvent(id) })
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onOpenEvents) {
                    Text(if (state.campsMapByDay.isEmpty()) AppStrings.current.overview.browseOthers else AppStrings.current.overview.more)
                }
            }

            // Upcoming birthdays
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(AppStrings.current.profile.birthdays, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (state.upcomingBirthdays.isEmpty()) {
                    Text(AppStrings.current.timeline.nothingPlanned, modifier = Modifier.padding(vertical = 6.dp))
                } else {
                    state.upcomingBirthdays.forEach { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                                    entry.formattedBirthDate?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (entry.days == 0) {
                                    Icon(imageVector = Icons.Filled.Cake, contentDescription = "Dnes mají narozeniny", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                } else {
                                    Text("${entry.days}d", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (state.isLoading) {
                Text("Načítám...", modifier = Modifier.padding(12.dp))
            }
            state.error?.let { Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        }
    }
}

}

private fun dateHeader(date: String, todayString: String, tomorrowString: String): String {
    return when (date) {
        todayString -> AppStrings.current.timeline.today.lowercase()
        tomorrowString -> AppStrings.current.timeline.tomorrow.lowercase()
        else -> {
            val ld = try { LocalDate.parse(date) } catch (_: Exception) { null }
            if (ld == null) date else {
                val todayYear = try { LocalDate.parse(todayString).year } catch (_: Exception) { -1 }
                formatFullCalendarDate(ld, AppStrings.currentLanguage.code, ld.year != todayYear)
            }
        }
    }
}
