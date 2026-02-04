
package com.tkolymp.tkolympapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.event.EventInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun CalendarScreen() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Moje", "Všechny")
    val eventsByDayState = remember { mutableStateOf<Map<String, List<EventInstance>>>(emptyMap()) }
    val errorMessage = remember { mutableStateOf<String?>(null) }

    // compute start (Monday) and end (Sunday) for current week
    val today = LocalDate.now()
    val monday = today.with(DayOfWeek.MONDAY)
    val sunday = today.with(DayOfWeek.SUNDAY)
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    val startIso = monday.toString() + "T00:00:00Z"
    val endIso = sunday.toString() + "T23:59:59Z"

    LaunchedEffect(selectedTab) {
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

        when (selectedTab) {
            0 -> Text(
                "Moje",
                style = MaterialTheme.typography.bodyLarge
            )

            1 -> Text(
                "Všechny",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // display grouped events
        val grouped = eventsByDayState.value
        val todayKey = LocalDate.now().format(fmt)

        grouped.forEach { (date, list) ->
            Column(modifier = Modifier.padding(8.dp)) {
                val header = when (date) {
                    todayKey -> "dnes"
                    LocalDate.now().plusDays(1).format(fmt) -> "zítra"
                    else -> date
                }
                Text(header, style = MaterialTheme.typography.titleMedium)
                list.forEach { item ->
                    val name = item.event?.name ?: "(no name)"
                    val cancelled = item.isCancelled
                    Text(
                        text = "- $name",
                        style = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (cancelled) TextDecoration.LineThrough else TextDecoration.None),
                        modifier = Modifier.padding(start = 8.dp)
                    )
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

