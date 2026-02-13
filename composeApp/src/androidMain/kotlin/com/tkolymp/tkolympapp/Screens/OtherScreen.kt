package com.tkolymp.tkolympapp

// coroutine helpers not needed here; avoid importing isActive directly
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.viewmodels.OtherViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.coroutines.cancellation.CancellationException

// Helper: do not surface internal/cancellation/compose runtime messages to the UI
private fun shouldShowErrorMessage(msg: String?): Boolean {
    if (msg.isNullOrBlank()) return false
    val low = msg.lowercase()
    if (low.contains("remembercoroutinescope") || low.contains("left the composition") || low.contains("left composition") ) return false
    if (low.contains("compose") && low.contains("coroutine")) return false
    return true
}

private fun shouldShowError(t: Throwable?): Boolean {
    if (t == null) return false
    if (t is CancellationException) return false
    val m = t.message ?: return true
    return shouldShowErrorMessage(m)
}

private fun formatDateString(raw: String): String? {
    val s = raw.trim()
    // Quick extract if value contains a date-like prefix
    val datePrefix = Regex("\\d{4}-\\d{2}-\\d{2}").find(s)?.value
    val formatterOut = DateTimeFormatter.ofPattern("d. M. yyyy")
    try {
        // Try plain local date
        if (datePrefix != null && datePrefix.length == 10) {
            val ld = LocalDate.parse(datePrefix)
            return ld.format(formatterOut)
        }
        // Try ISO local date-time / offset
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherScreen(onProfileClick: () -> Unit = {}, onPeopleClick: () -> Unit = {}, onTrainersClick: () -> Unit = {}, onGroupsClick: () -> Unit = {}, onLeaderboardClick: () -> Unit = {}, onAboutClick: () -> Unit = {}, onPrivacyClick: () -> Unit = {}, onNotificationsClick: () -> Unit = {}, bottomPadding: Dp = 0.dp) {
    val viewModel = remember { OtherViewModel() }
    val state by viewModel.state.collectAsState()
    var showDebug by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ostatní") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(bottom = bottomPadding),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Card(modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clickable { onProfileClick() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Box(modifier = Modifier.width(48.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Profile",
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = state.name ?: "Můj účet", style = MaterialTheme.typography.titleLarge)
                        if (state.subtitle != null) Text(state.subtitle!!, style = MaterialTheme.typography.bodySmall)
                        if (state.personDob != null && !showDebug) {
                            val formatted = formatDateString(state.personDob!!)
                            Text(
                                formatted ?: state.personDob!!,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    if (state.isLoading) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }

            if (state.error != null) Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))

            Spacer(modifier = Modifier.width(16.dp))

            // První sekce - Členové a klub
            Text(
                text = "Členové a klub",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )

            val mainItems = listOf(
                Pair("Lidé", Icons.Filled.People),
                Pair("Trenéři a tréninkové prostory", Icons.Filled.FitnessCenter),
                Pair("Tréninkové skupiny", Icons.Filled.Groups),
                Pair("Žebříček", Icons.Filled.EmojiEvents)
            )

            mainItems.forEach { (item, icon) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable {
                            if (item == "Lidé") onPeopleClick()
                            if (item == "Trenéři a tréninkové prostory") onTrainersClick()
                            if (item == "Tréninkové skupiny") onGroupsClick()
                            if (item == "Žebříček") onLeaderboardClick()
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = item,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Druhá sekce - Aplikace
            Text(
                text = "Aplikace",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
            )

            val settingsItems = listOf(
                Pair("Jazyky", Icons.Filled.Language),
                Pair("O aplikaci", Icons.Filled.Info),
                Pair("Nastavení notifikací", Icons.Filled.Notifications),
                Pair("Zásady ochrany osobních údajů", Icons.Filled.Security)
            )

            settingsItems.forEach { (item, icon) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable {
                            if (item == "Jazyky") {}
                            if (item == "O aplikaci") onAboutClick()
                            if (item == "Nastavení notifikací") onNotificationsClick()
                            if (item == "Zásady ochrany osobních údajů") onPrivacyClick()
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = item,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

                if (showDebug) {
                    Text("personId: ${state.personId ?: "(null)"}", style = MaterialTheme.typography.bodySmall)
                    Text("coupleIds: ${if (state.coupleIds.isEmpty()) "[]" else state.coupleIds.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                    if (state.rawJson != null) Text("raw: ${state.rawJson}", style = MaterialTheme.typography.bodySmall)
                    if (state.personDetailsRaw != null) Text("person: ${state.personDetailsRaw}", style = MaterialTheme.typography.bodySmall)
                    if (state.personDob != null) Text("birth: ${state.personDob}", style = MaterialTheme.typography.bodySmall)
                }
        }
    }
}
