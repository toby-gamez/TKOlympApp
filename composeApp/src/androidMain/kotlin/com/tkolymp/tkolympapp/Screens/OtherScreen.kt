package com.tkolymp.tkolympapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tkolymp.shared.ServiceLocator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

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

@Composable
fun OtherScreen(onProfileClick: () -> Unit = {}) {
    var name by remember { mutableStateOf<String?>(null) }
    var subtitle by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var personId by remember { mutableStateOf<String?>(null) }
    var coupleIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var rawJson by remember { mutableStateOf<String?>(null) }
    var personDetailsRaw by remember { mutableStateOf<String?>(null) }
    var personDob by remember { mutableStateOf<String?>(null) }
    var personFirstName by remember { mutableStateOf<String?>(null) }
    var personLastName by remember { mutableStateOf<String?>(null) }
    var personPrefix by remember { mutableStateOf<String?>(null) }
    var personSuffix by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        rawJson = try { ServiceLocator.userService.getCachedCurrentUserJson() } catch (_: Throwable) { null }
        personId = try { ServiceLocator.userService.getCachedPersonId() } catch (_: Throwable) { null }
        coupleIds = try { ServiceLocator.userService.getCachedCoupleIds() } catch (_: Throwable) { emptyList() }
        personDetailsRaw = try { ServiceLocator.userService.getCachedPersonDetailsJson() } catch (_: Throwable) { null }

        // If we have personId but no cached person details yet, try fetching them now.
        if (personDetailsRaw.isNullOrBlank() && !personId.isNullOrBlank()) {
            loading = true
            try {
                ServiceLocator.userService.fetchAndStorePersonDetails(personId!!)
                personDetailsRaw = try { ServiceLocator.userService.getCachedPersonDetailsJson() } catch (_: Throwable) { null }
                val apiErr = try { ServiceLocator.userService.getLastApiError() } catch (_: Throwable) { null }
                if (!apiErr.isNullOrBlank()) error = apiErr
            } catch (_: Throwable) {
                // ignore network errors here; error will be shown if parsing fails
            } finally {
                loading = false
            }
        }

        // show any last API error
        try {
            val apiErr = ServiceLocator.userService.getLastApiError()
            if (!apiErr.isNullOrBlank()) error = apiErr
        } catch (_: Throwable) { }

        if (rawJson != null) {
            try {
                val obj = Json.parseToJsonElement(rawJson!!).jsonObject
                val jmeno = obj["uJmeno"]?.let { it.toString().replace("\"", "") }
                val prijmeni = obj["uPrijmeni"]?.let { it.toString().replace("\"", "") }
                name = listOfNotNull(jmeno, prijmeni).joinToString(" ")
                subtitle = obj["uLogin"]?.toString()?.replace("\"", "")
            } catch (ex: Throwable) {
                error = ex.message ?: "Chyba při načítání profilu"
            }
        }
        if (personDetailsRaw != null) {
            try {
                val p = Json.parseToJsonElement(personDetailsRaw!!).jsonObject
                personFirstName = p["firstName"]?.toString()?.replace("\"", "")
                personLastName = p["lastName"]?.toString()?.replace("\"", "")
                personDob = p["birthDate"]?.toString()?.replace("\"", "")
                    ?: p["dateOfBirth"]?.toString()?.replace("\"", "")
                    ?: p["dob"]?.toString()?.replace("\"", "")
                    ?: p["bio"]?.toString()?.replace("\"", "")
                personPrefix = p["prefixTitle"]?.toString()?.replace("\"", "")
                personSuffix = p["suffixTitle"]?.toString()?.replace("\"", "")
                    ?: p["postfixTitle"]?.toString()?.replace("\"", "")
                    ?: p["suffix"]?.toString()?.replace("\"", "")
                if (!personFirstName.isNullOrBlank() || !personLastName.isNullOrBlank()) {
                    val base = listOfNotNull(personPrefix, personFirstName, personLastName).joinToString(" ")
                    name = if (!personSuffix.isNullOrBlank()) "$base, ${personSuffix}" else base
                }
            } catch (_: Throwable) { }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {

        Card(modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
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
                        modifier = Modifier.size(40.dp).align(Alignment.CenterStart)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name ?: "Můj účet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Start))
                    if (subtitle != null) Text(subtitle!!, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.Start))
                    if (personDob != null && !showDebug) {
                        val formatted = formatDateString(personDob!!)
                        Text(
                            formatted ?: personDob!!,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        )
                    }
                }

                if (loading) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }

        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (showDebug) {
            Text("personId: ${personId ?: "(null)"}", style = MaterialTheme.typography.bodySmall)
            Text("coupleIds: ${if (coupleIds.isEmpty()) "[]" else coupleIds.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            if (rawJson != null) Text("raw: ${rawJson}", style = MaterialTheme.typography.bodySmall)
            if (personDetailsRaw != null) Text("person: ${personDetailsRaw}", style = MaterialTheme.typography.bodySmall)
            if (personDob != null) Text("birth: ${personDob}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
