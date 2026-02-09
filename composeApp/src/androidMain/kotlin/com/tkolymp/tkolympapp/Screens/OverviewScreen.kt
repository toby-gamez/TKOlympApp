package com.tkolymp.tkolympapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        topBar = { TopAppBar(title = { Text("Přehled") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = bottomPadding),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text("Přehled", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(12.dp))

            var trainings by remember { mutableStateOf<List<com.tkolymp.shared.event.EventInstance>>(emptyList()) }
            var camps by remember { mutableStateOf<List<com.tkolymp.shared.event.EventInstance>>(emptyList()) }
            var announcements by remember { mutableStateOf<List<com.tkolymp.shared.announcements.Announcement>>(emptyList()) }
            var loading by remember { mutableStateOf(true) }
            var error by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                loading = true
                try {
                    val now = java.time.Instant.now()
                    val startIso = now.toString()
                    val endIso = now.plus(java.time.Duration.ofDays(90)).toString()

                    val evSvc = com.tkolymp.shared.ServiceLocator.eventService
                    val map = withContext(Dispatchers.IO) { evSvc.fetchEventsGroupedByDay(startIso, endIso, onlyMine = true, first = 200) }

                    val instances = map.values.flatten().mapNotNull { inst ->
                        inst.since?.let { sinceStr ->
                            try {
                                val instTime = java.time.Instant.parse(sinceStr)
                                Pair(instTime, inst)
                            } catch (_: Exception) { null }
                        }
                    }.sortedBy { it.first }.map { it.second }

                    trainings = instances.filter { !it.isCancelled && it.event?.type != "CAMP" }.take(2)
                    camps = instances.filter { !it.isCancelled && it.event?.type == "CAMP" }.take(2)

                    val annSvc = com.tkolymp.shared.ServiceLocator.announcementService
                    val anns = withContext(Dispatchers.IO) { annSvc.getAnnouncements(false) }
                    announcements = anns.filter { it.isVisible }.take(2)
                } catch (ex: Exception) {
                    error = ex.message
                } finally {
                    loading = false
                }
            }

            // Trainings section
            OverviewSection(
                title = "Nejbližší tréninky",
                items = trainings.mapNotNull { inst -> inst.event?.let { ev -> Pair(inst.id, ev.name ?: "(bez názvu) to ${inst.since}") } },
                onMore = onOpenCalendar,
                onItemClick = { id -> onOpenEvent(id) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Board announcements
            OverviewSection(
                title = "Něco z nástěnky",
                items = announcements.mapNotNull { a -> a.id?.toLongOrNull()?.let { Pair(it, a.title ?: "(bez názvu)") } },
                onMore = onOpenBoard,
                onItemClick = { id -> onOpenNotice(id) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Camps
            OverviewSection(
                title = "Nejbližší soustředění",
                items = camps.mapNotNull { inst -> inst.event?.let { ev -> Pair(inst.id, ev.name ?: "(bez názvu) to ${inst.since}") } },
                onMore = onOpenEvents,
                onItemClick = { id -> onOpenEvent(id) }
            )

            if (loading) {
                Text("Načítám...", modifier = Modifier.padding(12.dp))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
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
        TextButton(onClick = onMore) { Text("Více") }
    }
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        if (items.isEmpty()) {
            Text("Nic k zobrazení", modifier = Modifier.padding(vertical = 6.dp))
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
