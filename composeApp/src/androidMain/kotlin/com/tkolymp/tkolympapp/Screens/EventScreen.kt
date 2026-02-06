package com.tkolymp.tkolympapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch
import java.util.UUID
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventScreen(eventId: Long, onBack: (() -> Unit)? = null, onOpenRegistration: ((String, String?) -> Unit)? = null) {
    val loading = remember { mutableStateOf(false) }
    val error = remember { mutableStateOf<String?>(null) }
    val eventJson = remember { mutableStateOf<JsonObject?>(null) }
    val showAllInstances = remember { mutableStateOf(false) }
    val myPersonId = remember { mutableStateOf<String?>(null) }
    val myCoupleIds = remember { mutableStateOf<List<String>>(emptyList()) }

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
            // load cached current user ids for highlighting
            try {
                withContext(Dispatchers.IO) {
                    val us = ServiceLocator.userService
                    myPersonId.value = us.getCachedPersonId()
                    myCoupleIds.value = us.getCachedCoupleIds()
                }
            } catch (_: Exception) {}
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
    // registration navigation handled by App NavHost via `onOpenRegistration`

    fun JsonObject.str(key: String) = try { this[key]?.jsonPrimitive?.contentOrNull } catch (_: Exception) { null }
    fun JsonObject.int(key: String) = try { this[key]?.jsonPrimitive?.intOrNull } catch (_: Exception) { null }
    fun JsonObject.bool(key: String) = try { this[key]?.jsonPrimitive?.booleanOrNull } catch (_: Exception) { null }

    val type = ev.str("type") ?: ""
    val trainers = ev["eventTrainersList"].asJsonArrayOrNull() ?: JsonArray(emptyList())
    val name = when {
        !ev.str("name").isNullOrBlank() -> ev.str("name")!!
        type.equals("lesson", ignoreCase = true) -> {
            trainers.firstOrNull()?.asJsonObjectOrNull()?.str("name")?.takeIf { it.isNotBlank() } ?: "(bez názvu)"
        }
        else -> "(bez názvu)"
    }
    val descr = ev.str("description") ?: ""
    val summary = ev.str("summary") ?: ""
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

        val cohorts = ev["eventTargetCohortsList"].asJsonArrayOrNull() ?: JsonArray(emptyList())
        val registrations = ev["eventRegistrationsList"].asJsonArrayOrNull() ?: JsonArray(emptyList())
        val externalRegistrations = ev["eventExternalRegistrationsList"].asJsonArrayOrNull() ?: JsonArray(emptyList())

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Událost") },
                    navigationIcon = {
                        onBack?.let {
                            IconButton(onClick = it) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Zpět")
                            }
                        }
                    }
                )
            }
        ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
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
                            "Termín: ${formatTimesWithDateAlways(firstDate, null)} - ${formatTimesWithDateAlways(lastDate, null)}"
                        firstDate != null -> "Termín: ${formatTimesWithDateAlways(firstDate, lastDate)}"
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

        // Seznam termínů
        // Show the instances card only when there is more than one instance
        if (instances.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Termíny události", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    val instancesToShow = if (showAllInstances.value || instances.size <= 3) {
                        instances
                    } else {
                        instances.take(3)
                    }
                    
                    instancesToShow.forEachIndexed { index, instanceEl ->
                        val instance = instanceEl.asJsonObjectOrNull() ?: return@forEachIndexed
                        val since = instance.str("since")
                        val until = instance.str("until")
                        val instLocation = instance["location"].asJsonObjectOrNull()?.str("name")
                        
                        if (index > 0) Spacer(modifier = Modifier.height(4.dp))
                        
                        Text("${index + 1}. ${formatTimesWithDateAlways(since, until)}", 
                            style = MaterialTheme.typography.bodySmall)
                        
                        if (!instLocation.isNullOrBlank() && instLocation != locationName) {
                            Text("   Místo: $instLocation", 
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ))
                        }
                    }
                    
                    if (instances.size > 3 && !showAllInstances.value) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { showAllInstances.value = true }) {
                            Text("Zobrazit všech ${instances.size} termínů")
                        }
                    } else if (instances.size > 3 && showAllInstances.value) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { showAllInstances.value = false }) {
                            Text("Zobrazit méně")
                        }
                    }
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
                // --- Registration action buttons visibility logic (from MAUI EventViewModel.LoadEventAsync) ---
                // If event is locked, treat registrations as closed regardless of the explicit flag
                val isRegistrationOpen = if (isLocked) false else (ev.bool("isRegistrationOpen") ?: true)

                val trainerCount = trainers.size

                val userRegistered = registrations.any { regEl ->
                    val reg = regEl.asJsonObjectOrNull() ?: return@any false
                    val personId = reg["person"].asJsonObjectOrNull()?.str("id")
                    val coupleId = reg["couple"].asJsonObjectOrNull()?.str("id")
                    (personId != null && personId == myPersonId.value) || (coupleId != null && myCoupleIds.value.contains(coupleId))
                }

                val isPast = try {
                    val now = java.time.Instant.now()
                    when {
                        lastDate != null -> java.time.OffsetDateTime.parse(lastDate).toInstant().isBefore(now)
                        firstDate != null -> java.time.OffsetDateTime.parse(firstDate).toInstant().plus(java.time.Duration.ofDays(1)).isBefore(now)
                        else -> false
                    }
                } catch (_: Exception) {
                    false
                }

                // Defaults (pre-load)
                val registerButtonVisibleDefault = false
                val registrationActionsRowVisibleDefault = false
                val editRegistrationButtonVisibleDefault = true
                val deleteButtonColumnDefault = 1
                val deleteButtonColumnSpanDefault = 1

                // Final visibility rules
                val registerButtonVisible = if (isPast) {
                    false
                } else {
                    if (userRegistered) false else isRegistrationOpen
                }

                val registrationActionsRowVisible = if (isPast) {
                    false
                } else {
                    if (userRegistered) isRegistrationOpen else false
                }

                val editRegistrationButtonVisible = if (trainerCount == 1) false else true
                val deleteFullWidth = trainerCount == 1

                // --- Render buttons stacked under the event info ---
                if (registerButtonVisible || registrationActionsRowVisible) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (registerButtonVisible) {
                            Button(onClick = { onOpenRegistration?.invoke("register", null) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Zapsat se")
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        if (registrationActionsRowVisible) {
                            // Stacked actions: edit (if visible) and delete
                            if (editRegistrationButtonVisible) {
                                Button(onClick = { onOpenRegistration?.invoke("edit", null) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Upravit registraci")
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // Delete button: if only one trainer, show as full-width (deleteFullWidth)
                            Button(
                                onClick = { onOpenRegistration?.invoke("delete", null) },
                                modifier = if (deleteFullWidth) Modifier.fillMaxWidth() else Modifier.fillMaxWidth()
                            ) {
                                Text("Smazat registraci")
                            }
                        }
                    }
                }
            }
        }

        

        // Popis a shrnutí
        if (summary.isNotBlank() || descr.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val headerText = when {
                            summary.isNotBlank() && descr.isNotBlank() -> "Shrnutí a popis"
                            summary.isNotBlank() -> "Shrnutí"
                            else -> "Popis"
                        }
                        Text(headerText, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        if (summary.isNotBlank()) {
                            Text(formatHtmlContent(summary), style = MaterialTheme.typography.bodyMedium)
                            if (descr.isNotBlank()) Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (descr.isNotBlank()) {
                            Text(formatHtmlContent(descr), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Kapacita
        if ((capacity != null && capacity > 0) || remainingLessons != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Kapacita", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    val registeredCount = registrations.size + externalRegistrations.size
                    if (capacity != null && capacity > 0) {
                        Text("Registrováno: $registeredCount / $capacity míst", style = MaterialTheme.typography.bodySmall)
                    } else if (registeredCount > 0) {
                        Text("Registrováno: $registeredCount míst", style = MaterialTheme.typography.bodySmall)
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
                val totalCount = registrations.size + externalRegistrations.size
                Text("Přihlášení účastníci ($totalCount)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                if (totalCount == 0) {
                    Text("Žádní přihlášení účastníci", style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                } else {
                    // Běžné registrace
                    registrations.forEach { regEl ->
                        val reg = regEl.asJsonObjectOrNull() ?: return@forEach
                        val person = reg["person"].asJsonObjectOrNull()
                        val couple = reg["couple"].asJsonObjectOrNull()
                        val note = reg.str("note")

                        // Získat demands (požadavky na lekce s trenéry)
                        val lessonDemands = reg["eventLessonDemandsByRegistrationIdList"].asJsonArrayOrNull() ?: JsonArray(emptyList())

                        // Build name text and skip entries without any real name
                        val nameText = when {
                            couple != null -> {
                                val woman = couple["woman"].asJsonObjectOrNull()?.str("name")?.takeIf { it.isNotBlank() }
                                val man = couple["man"].asJsonObjectOrNull()?.str("name")?.takeIf { it.isNotBlank() }
                                when {
                                    woman != null && man != null -> "$woman - $man"
                                    woman != null -> woman
                                    man != null -> man
                                    else -> null
                                }
                            }
                            person != null -> person.str("name")?.takeIf { it.isNotBlank() }
                            else -> null
                        }

                        if (nameText.isNullOrBlank()) return@forEach // do not show unnamed registrations

                        // Determine whether this registration is the current user
                        val personId = person?.str("id")
                        val coupleId = couple?.str("id")
                        val isMe = (personId != null && personId == myPersonId.value) || (coupleId != null && myCoupleIds.value.contains(coupleId))

                        if (isMe) {
                            Text(nameText, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        } else {
                            Text(nameText, style = MaterialTheme.typography.bodySmall)
                        }

                        // Zobrazit trenéry a počet lekcí z demands
                        if (lessonDemands.isNotEmpty()) {
                            lessonDemands.forEach { demandEl ->
                                val demand = demandEl.asJsonObjectOrNull() ?: return@forEach
                                val trainerId = demand.int("trainerId")
                                val lessonCount = demand.int("lessonCount")
                                
                                // Najít jméno trenéra podle trainerId
                                val trainerName = trainers.firstOrNull { trainerEl ->
                                    val trainer = trainerEl.asJsonObjectOrNull() ?: return@firstOrNull false
                                    trainer.int("id") == trainerId
                                }?.asJsonObjectOrNull()?.str("name") ?: "Trenér #$trainerId"
                                
                                val demandText = if (lessonCount != null && lessonCount > 0) {
                                    "$trainerName: $lessonCount lekcí"
                                } else {
                                    trainerName
                                }
                                
                                Text("   $demandText", style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ))
                            }
                        }
                        
                        if (!note.isNullOrBlank()) {
                            Text("   Poznámka: $note", style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ))
                        }
                    }
                    
                    // Externí registrace
                    externalRegistrations.forEach { extRegEl ->
                        val extReg = extRegEl.asJsonObjectOrNull() ?: return@forEach
                        val firstName = extReg.str("firstName") ?: ""
                        val lastName = extReg.str("lastName") ?: ""
                        val email = extReg.str("email")
                        val note = extReg.str("note")

                        Text("$firstName $lastName (externí)", style = MaterialTheme.typography.bodySmall)
                        if (!email.isNullOrBlank()) {
                            Text("   Email: $email", style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ))
                        }
                        // Phone number intentionally not shown for external registrations
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
}