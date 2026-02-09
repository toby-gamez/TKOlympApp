package com.tkolymp.tkolympapp

import android.text.Html
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = padding.calculateTopPadding(), bottom = bottomPadding),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {

            var trainings by remember { mutableStateOf<List<com.tkolymp.shared.event.EventInstance>>(emptyList()) }
            var camps by remember { mutableStateOf<List<com.tkolymp.shared.event.EventInstance>>(emptyList()) }
            var trainingItems by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
            var campItems by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
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

                    trainings = instances.filter { inst ->
                        if (inst.isCancelled) return@filter false
                        val t = inst.event?.type
                        !(t?.contains("CAMP", ignoreCase = true) == true)
                    }
                    camps = instances.filter { inst ->
                        if (inst.isCancelled) return@filter false
                        val t = inst.event?.type
                        t?.contains("CAMP", ignoreCase = true) == true
                    }

                    // Helper to resolve an event name: prefer provided name, otherwise fetch full event by id
                    suspend fun resolveEventLabel(inst: com.tkolymp.shared.event.EventInstance): String {
                        val ev = inst.event
                        val sincePart = inst.since ?: inst.until ?: ""
                        val suffix = if (sincePart.isNotEmpty()) " to $sincePart" else ""
                        val provided = ev?.name
                        if (!provided.isNullOrBlank()) return "$provided$suffix"

                        val evId = ev?.id
                        if (evId != null) {
                            try {
                                val evJson = withContext(Dispatchers.IO) { evSvc.fetchEventById(evId) }
                                val fetchedName = evJson?.get("name")?.jsonPrimitive?.contentOrNull
                                if (!fetchedName.isNullOrBlank()) return "$fetchedName$suffix"
                            } catch (_: Exception) {
                                // ignore and fallthrough to default
                            }
                        }

                        return "(bez názvu)$suffix"
                    }

                    // Pre-resolve labels for small lists so UI shows names even if initial payload lacks them
                    val resolvedTrainings = mutableListOf<Pair<Long, String>>()
                    for (inst in trainings.take(2)) {
                        val label = resolveEventLabel(inst)
                        resolvedTrainings += Pair(inst.id, label)
                    }

                    val resolvedCamps = mutableListOf<Pair<Long, String>>()
                    for (inst in camps.take(2)) {
                        val label = resolveEventLabel(inst)
                        resolvedCamps += Pair(inst.id, label)
                    }

                    trainingItems = resolvedTrainings
                    campItems = resolvedCamps

                    val annSvc = com.tkolymp.shared.ServiceLocator.announcementService
                    val anns = withContext(Dispatchers.IO) { annSvc.getAnnouncements(false) }
                    announcements = anns.filter { it.isVisible }.take(2)
                } catch (ex: Exception) {
                    error = ex.message
                } finally {
                    loading = false
                }
            }

            // Trainings section (styled like Calendar)
            // Trainings section (grouped by day, styled like Calendar)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Nejbližší tréninky", style = MaterialTheme.typography.titleLarge)
            }
            val limitedTrainings = trainings.sortedBy { it.since ?: it.updatedAt ?: "" }.take(2)
            val trainingsMapByDay = limitedTrainings.groupBy { inst ->
                val s = inst.since ?: inst.until ?: inst.updatedAt ?: ""
                s.substringBefore('T').ifEmpty { s }
            }.toSortedMap()

            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (trainingsMapByDay.isEmpty()) {
                    if (loading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Text("Zatím jste si nic nenaplánovali", modifier = Modifier.padding(vertical = 6.dp))
                    }
                } else {
                    val fmt = DateTimeFormatter.ISO_LOCAL_DATE
                    val todayKey = LocalDate.now().format(fmt)

                    trainingsMapByDay.forEach { (date, list) ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
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
                                    isAllTab = false,
                                    myPersonId = null,
                                    myCoupleIds = emptyList(),
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
                TextButton(onClick = onOpenCalendar) { Text(if (trainingsEmpty) "Podívat se na ostatní" else "Více") }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Board announcements (styled like BoardScreen)
            Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Něco z nástěnky", style = MaterialTheme.typography.titleLarge)
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (announcements.isEmpty()) {
                    if (loading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Text("Zatím jste si nic nenaplánovali", modifier = Modifier.padding(vertical = 6.dp))
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
                                colors = CardDefaults.cardColors()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(a.title ?: "(bez názvu)", style = MaterialTheme.typography.titleMedium)
                                    val authorName = listOfNotNull(a.author?.uJmeno, a.author?.uPrijmeni).joinToString(" ").trim()
                                    if (authorName.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(authorName, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    var plainBody = Html.fromHtml(a.body ?: "", Html.FROM_HTML_MODE_LEGACY).toString()
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
                TextButton(onClick = onOpenBoard) { Text(if (boardEmpty) "Podívat se na ostatní" else "Více") }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Camps section (styled like Calendar)
            Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Nejbližší soustředění", style = MaterialTheme.typography.titleLarge)
            }
            val limitedCamps = camps.sortedBy { it.since ?: it.updatedAt ?: "" }.take(2)
            val campsMapByDay = limitedCamps.groupBy { inst ->
                val s = inst.since ?: inst.until ?: inst.updatedAt ?: ""
                s.substringBefore('T').ifEmpty { s }
            }.toSortedMap()

            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (campsMapByDay.isEmpty()) {
                    if (loading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Text("Zatím jste si nic nenaplánovali", modifier = Modifier.padding(vertical = 6.dp))
                    }
                } else {
                    val fmt = DateTimeFormatter.ISO_LOCAL_DATE
                    val todayKey = LocalDate.now().format(fmt)

                    campsMapByDay.forEach { (date, list) ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
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
                TextButton(onClick = onOpenEvents) { Text(if (campsEmpty) "Podívat se na ostatní" else "Více") }
            }

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
        TextButton(onClick = onMore) { Text(if (items.isEmpty()) "Podívat se na ostatní" else "Více") }
    }
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        if (items.isEmpty()) {
            Text("Zatím jste si nic nenaplánovali", modifier = Modifier.padding(vertical = 6.dp))
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
