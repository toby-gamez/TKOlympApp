package com.tkolymp.tkolympapp.Screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.people.PersonDetails
import com.tkolymp.shared.viewmodels.PersonViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(personId: String, onBack: () -> Unit = {}, onOpenCouple: (String) -> Unit = {}) {
    val viewModel = remember { PersonViewModel() }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Osoba") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Zpět") }
        })
    }) { padding ->
        val scope = rememberCoroutineScope()

        SwipeToReload(isRefreshing = state.isLoading, onRefresh = { scope.launch { viewModel.loadPerson(personId) } }, modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {

            state.error?.let { Text("Chyba: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }

            val p = state.person as? PersonDetails
            if (p == null) {
                Text("Osoba nenalezena: $personId", modifier = Modifier.padding(8.dp))
                return@Column
            }

            // Header (name)
            val baseName = listOf(p.prefixTitle, p.firstName, p.lastName).filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
            val fullName = if (!p.suffixTitle.isNullOrBlank()) "$baseName, ${p.suffixTitle}" else baseName
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fullName.ifBlank { p.id }, style = MaterialTheme.typography.headlineSmall)
                if (p.isTrainer == true) {
                    Text("trenér", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }

            // Basic Info card
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Základní informace", style = MaterialTheme.typography.labelLarge)
                    Divider(modifier = Modifier.padding(vertical = 6.dp))
                    p.birthDate?.let { bd ->
                        val bdText = formatDateStringSmall(bd) ?: bd
                        Text("Datum narození: $bdText", style = MaterialTheme.typography.bodySmall)
                    }
                    p.email?.let { Text("Email: $it", style = MaterialTheme.typography.bodySmall) }
                    p.cstsId?.let { Text("CSTS: $it", style = MaterialTheme.typography.bodySmall) }
                    p.wdsfId?.let { Text("WDSF: $it", style = MaterialTheme.typography.bodySmall) }
                    p.gender?.let {
                        val genderLabel = when (it) {
                            "MAN" -> "muž"
                            "WOMAN" -> "žena"
                            "UNSPECIFIED" -> "neuvedeno"
                            else -> it.lowercase()
                        }
                        Text("Pohlaví: $genderLabel", style = MaterialTheme.typography.bodySmall)
                    }
                    // trainer badge is shown under the name header; do not duplicate here
                }
            }

            // Bio card (if present and non-empty)
            p.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("O mně", style = MaterialTheme.typography.labelLarge)
                        Divider(modifier = Modifier.padding(vertical = 6.dp))
                        Text(bio, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Tréninkové skupiny (stacked)
            val visibleGroups = p.cohortMembershipsList.filter { it.cohort?.isVisible != false }
            if (visibleGroups.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Tréninkové skupiny", style = MaterialTheme.typography.labelLarge)
                        Divider(modifier = Modifier.padding(vertical = 6.dp))
                        Column {
                            visibleGroups.forEach { mem ->
                                    val c = mem.cohort ?: return@forEach
                                    val color = try { parseColorOrDefault(c.colorRgb) } catch (_: Exception) { Color.Gray }
                                    Card(modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(6.dp)) {
                                            val since = mem.since
                                            val until = mem.until
                                            Row(modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = (c.name ?: c.id ?: "-"),
                                                        style = MaterialTheme.typography.titleSmall,
                                                    )
                                                    if (!since.isNullOrBlank()) {
                                                        val sinceLabel = formatDateStringSmall(since) ?: since
                                                        Text("Od: $sinceLabel", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                    if (!until.isNullOrBlank()) {
                                                        val untilLabel = formatDateStringSmall(until) ?: until
                                                        Text("Do: $untilLabel", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                                Box(modifier = Modifier
                                                    .width(28.dp)
                                                    .fillMaxHeight(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Box(modifier = Modifier.size(12.dp).background(color, shape = CircleShape))
                                                }
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
            }

            // Active couples card
            if (p.activeCouplesList.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Aktivní páry", style = MaterialTheme.typography.labelLarge)
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        p.activeCouplesList.forEach { c ->
                            Card(modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { c.id?.let { onOpenCouple(it) } }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val man = c.man
                                        val woman = c.woman
                                        val manName = listOfNotNull(man?.firstName, man?.lastName).joinToString(" ").trim()
                                        val womanName = listOfNotNull(woman?.firstName, woman?.lastName).joinToString(" ").trim()
                                        Text(listOfNotNull(manName.takeIf { it.isNotBlank() }, womanName.takeIf { it.isNotBlank() }).joinToString(" - "), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    // hide internal couple id in UI
                                }
                            }
                        }
                    }
                }
            }
            // (duplicate UI removed)
                }
            }
        }
    }


private fun formatDateStringSmall(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val s = raw.trim()
    val datePrefix = Regex("\\d{4}-\\d{2}-\\d{2}").find(s)?.value
    val formatterOut = DateTimeFormatter.ofPattern("d. M. yyyy")
    try {
        if (datePrefix != null && datePrefix.length == 10) {
            val ld = LocalDate.parse(datePrefix)
            return ld.format(formatterOut)
        }
        val odt = OffsetDateTime.parse(s)
        return odt.toLocalDate().format(formatterOut)
    } catch (_: DateTimeParseException) {
    }
    try {
        val zdt = ZonedDateTime.parse(s)
        return zdt.toLocalDate().format(formatterOut)
    } catch (_: DateTimeParseException) {
    }
    try {
        val instant = Instant.parse(s)
        val ld = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        return ld.format(formatterOut)
    } catch (_: DateTimeParseException) {
    }
    return null
}
