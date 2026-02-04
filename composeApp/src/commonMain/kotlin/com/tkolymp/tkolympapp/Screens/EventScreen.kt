package com.tkolymp.tkolympapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

private fun JsonElement?.asJsonObjectOrNull(): JsonObject? = try {
    when {
        this == null -> null
        this is JsonNull -> null
        this is JsonObject -> this
        else -> null
    }
} catch (_: Exception) {
    null
}

private fun JsonElement?.asJsonArrayOrNull(): JsonArray? = try {
    when {
        this == null -> null
        this is JsonNull -> null
        this is JsonArray -> this
        else -> null
    }
} catch (_: Exception) {
    null
}

@Composable
fun EventScreen(eventId: Long, onBack: (() -> Unit)? = null) {
    val loading = remember { mutableStateOf(false) }
    val error = remember { mutableStateOf<String?>(null) }
    val eventJson = remember { mutableStateOf<JsonObject?>(null) }

    LaunchedEffect(eventId) {
        loading.value = true
        error.value = null
        // initialize auth (harmless if already initialized)
        try { withContext(Dispatchers.IO) { ServiceLocator.authService.initialize() } } catch (_: Exception) {}

        val svc = ServiceLocator.eventService
        try {
            val ev = withContext(Dispatchers.IO) { svc.fetchEventById(eventId) }
            if (ev == null) {
                error.value = "Událost nenalezena"
            } else {
                eventJson.value = ev
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            error.value = ex.message ?: "Chyba při načítání: ${ex::class.simpleName}"
        } finally {
            loading.value = false
        }
    }

    if (loading.value) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }
        return
    }

    if (error.value != null) {
        AlertDialog(
            onDismissRequest = { error.value = null },
            confirmButton = { TextButton(onClick = { error.value = null }) { Text("OK") } },
            title = { Text("Chyba") },
            text = { Text(error.value ?: "Neznámá chyba") }
        )
        return
    }

    val ev = eventJson.value
    if (ev == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Žádná událost k zobrazení", modifier = Modifier.padding(16.dp))
        }
        return
    }

    fun JsonObject.str(key: String) = try { this[key]?.jsonPrimitive?.contentOrNull } catch (_: Exception) { null }
    fun JsonObject.int(key: String) = try { this[key]?.jsonPrimitive?.intOrNull } catch (_: Exception) { null }
    fun JsonObject.bool(key: String) = try { this[key]?.jsonPrimitive?.booleanOrNull } catch (_: Exception) { null }

    val name = ev.str("name") ?: "(bez názvu)"
    val descr = ev.str("description") ?: ""
    val summary = ev.str("summary") ?: ""
    val type = ev.str("type") ?: ""
    val locationName = ev["location"].asJsonObjectOrNull()?.str("name") ?: ev.str("locationText")
        
        val isVisible = ev.bool("isVisible") ?: false
        val isPublic = ev.bool("isPublic") ?: false
        val isLocked = ev.bool("isLocked") ?: false
        val enableNotes = ev.bool("enableNotes") ?: false
        val capacity = ev.int("capacity")
        val remainingPersonSpots = ev.int("remainingPersonSpots")
        val remainingLessons = ev.int("remainingLessons")

        val instances = ev["eventInstancesList"].asJsonArrayOrNull() ?: JsonArray(emptyList())
        val firstInstance = instances.firstOrNull().asJsonObjectOrNull()
        val lastInstance = instances.lastOrNull().asJsonObjectOrNull()
        val firstDate = firstInstance?.str("since")
        val lastDate = lastInstance?.str("until")

        val trainers = ev["eventTrainersList"].asJsonArrayOrNull() ?: JsonArray(emptyList())
        val cohorts = ev["eventTargetCohortsList"].asJsonArrayOrNull() ?: JsonArray(emptyList())
        val registrations = ev["eventRegistrationsList"].asJsonArrayOrNull() ?: JsonArray(emptyList())
        val externalRegistrations = ev["eventExternalRegistrationsList"].asJsonArrayOrNull() ?: JsonArray(emptyList())

        Column(modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
        ) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            // Typ a základní info
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                val displayType = translateEventType(type)
                if (!displayType.isNullOrBlank()) {
                    Text("Typ: $displayType", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (!locationName.isNullOrBlank()) {
                    Text("Místo: $locationName", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (firstDate != null || lastDate != null) {
                    val dateText = when {
                        firstDate != null && lastDate != null && firstDate != lastDate -> 
                            "Termín: ${formatTimes(firstDate, null)} - ${formatTimes(lastDate, null)}"
                        firstDate != null -> "Termín: ${formatTimes(firstDate, lastDate)}"
                        else -> ""
                    }
                    if (dateText.isNotBlank()) {
                        Text(dateText, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                if (instances.isNotEmpty()) {
                    Text("Počet termínů: ${instances.size}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // O události (stav a nastavení)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("O události", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Viditelné: ${if (isVisible) "Ano" else "Ne"}", style = MaterialTheme.typography.bodySmall)
                Text("Veřejné: ${if (isPublic) "Ano" else "Ne"}", style = MaterialTheme.typography.bodySmall)
                Text("Registrace: ${if (isLocked) "Uzavřeno" else "Otevřeno"}", style = MaterialTheme.typography.bodySmall)
                Text("Poznámky povoleny: ${if (enableNotes) "Ano" else "Ne"}", style = MaterialTheme.typography.bodySmall)
            }
        }

        // Popis a shrnutí
        if (summary.isNotBlank() || descr.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (summary.isNotBlank()) {
                        Text(summary, style = MaterialTheme.typography.bodyMedium)
                        if (descr.isNotBlank()) Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (descr.isNotBlank()) {
                        Text(descr, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Kapacita
        if (capacity != null || remainingLessons != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Kapacita", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    val registeredCount = registrations.size + externalRegistrations.size
                    if (capacity != null) {
                        Text("Registrováno: $registeredCount / $capacity osob", style = MaterialTheme.typography.bodySmall)
                    } else if (registeredCount > 0) {
                        Text("Registrováno: $registeredCount osob", style = MaterialTheme.typography.bodySmall)
                    }
                    if (remainingLessons != null) {
                        Text("Zbývající lekce: $remainingLessons", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Trenéři
        if (trainers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Trenéři", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    val trainerNames = trainers.mapNotNull { trainerEl ->
                        val trainer = trainerEl.asJsonObjectOrNull() ?: return@mapNotNull null
                        val trainerName = trainer.str("name") ?: "(bez jména)"
                        val lessonsOffered = trainer.int("lessonsOffered")
                        val lessonsRemaining = trainer.int("lessonsRemaining")
                        when {
                            lessonsOffered != null && lessonsRemaining != null -> 
                                "$trainerName (nabízí: $lessonsOffered, zbývá: $lessonsRemaining)"
                            lessonsOffered != null -> "$trainerName (nabízí: $lessonsOffered)"
                            else -> trainerName
                        }
                    }.joinToString(", ")
                    Text(trainerNames.ifBlank { "(žádní)" }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Cílové kohorty
        if (cohorts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Cílové skupiny", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    val cohortNames = cohorts.mapNotNull { cohortEl ->
                        cohortEl.asJsonObjectOrNull()
                            ?.get("cohort")
                            ?.asJsonObjectOrNull()
                            ?.str("name")
                    }.joinToString(", ")
                    Text(cohortNames.ifBlank { "(žádné)" }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Přihlášení účastníci
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Přihlášení účastníci (${registrations.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                if (registrations.isEmpty()) {
                    Text("Žádní přihlášení účastníci", style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                } else {
                    registrations.forEachIndexed { index, regEl ->
                        val reg = regEl.asJsonObjectOrNull() ?: return@forEachIndexed
                        val person = reg["person"].asJsonObjectOrNull()
                        val couple = reg["couple"].asJsonObjectOrNull()
                        val note = reg.str("note")
                        
                        val nameText = when {
                            couple != null -> {
                                val woman = couple["woman"].asJsonObjectOrNull()?.str("name")
                                val man = couple["man"].asJsonObjectOrNull()?.str("name")
                                "$woman & $man (pár)"
                            }
                            person != null -> person.str("name") ?: "(bez jména)"
                            else -> "(bez jména)"
                        }
                        
                        Text("${index + 1}. $nameText", style = MaterialTheme.typography.bodySmall)
                        if (!note.isNullOrBlank()) {
                            Text("   Poznámka: $note", style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ))
                        }
                    }
                }
            }
        }

        // Externí registrace
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Externí registrace (${externalRegistrations.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                if (externalRegistrations.isEmpty()) {
                    Text("Žádné externí registrace", style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                } else {
                    externalRegistrations.forEachIndexed { index, extRegEl ->
                        val extReg = extRegEl.asJsonObjectOrNull() ?: return@forEachIndexed
                        val firstName = extReg.str("firstName") ?: ""
                        val lastName = extReg.str("lastName") ?: ""
                        val email = extReg.str("email")
                        val phone = extReg.str("phone")
                        val note = extReg.str("note")
                        
                        Text("${index + 1}. $firstName $lastName", style = MaterialTheme.typography.bodySmall)
                        if (!email.isNullOrBlank()) {
                            Text("   Email: $email", style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ))
                        }
                        if (!phone.isNullOrBlank()) {
                            Text("   Tel: $phone", style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ))
                        }
                        if (!note.isNullOrBlank()) {
                            Text("   Poznámka: $note", style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}