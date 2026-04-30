package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.asJsonArrayOrNull
import com.tkolymp.shared.utils.asJsonObjectOrNull
import com.tkolymp.shared.utils.formatTimesWithDateAlways
import com.tkolymp.shared.utils.int
import com.tkolymp.shared.utils.str
import com.tkolymp.shared.utils.translateEventType
import com.tkolymp.shared.viewmodels.EventViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.QuantityInput
import com.tkolymp.tkolympapp.platform.HtmlText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventScreen(eventId: Long, onBack: (() -> Unit)? = null, onOpenRegistration: ((String, String?) -> Unit)? = null, onOpenPerson: ((String) -> Unit)? = null) {
    val viewModel = viewModel<EventViewModel>()
    val state by viewModel.state.collectAsState()
    val showAllInstances = remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showReminderDialog by remember { mutableStateOf(false) }

    LaunchedEffect(eventId) {
        viewModel.loadEvent(eventId, forceRefresh = true)
    }

    // Show snackbar when calendarResult changes
    LaunchedEffect(state.calendarResult) {
        val result = state.calendarResult ?: return@LaunchedEffect
        val msg = if (result) AppStrings.current.events.addToCalendarSuccess
                  else AppStrings.current.events.addToCalendarFailed
        snackbarHostState.showSnackbar(msg)
        viewModel.clearCalendarResult()
    }

    // Show snackbar when reminderResult changes
    LaunchedEffect(state.reminderResult) {
        val result = state.reminderResult ?: return@LaunchedEffect
        val msg = if (result) AppStrings.current.notifications.reminderSet
                  else AppStrings.current.notifications.reminderRemoveAction
        snackbarHostState.showSnackbar(msg)
        viewModel.clearReminderResult()
    }

    val ev = state.eventJson
    // registration navigation handled by App NavHost via `onOpenRegistration`

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(AppStrings.current.events.event) },
                    navigationIcon = {
                        onBack?.let {
                            IconButton(onClick = it) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                            }
                        }
                    },
                    actions = {
                        if (!state.isPast && state.firstInstanceIso != null) {
                            if (state.reminderId != null) {
                                IconButton(onClick = { showReminderDialog = true }) {
                                    Icon(Icons.Default.Notifications, contentDescription = AppStrings.current.notifications.remindMe)
                                }
                                IconButton(onClick = { scope.launch { viewModel.removeReminder(eventId) } }) {
                                    Icon(Icons.Default.NotificationsOff, contentDescription = AppStrings.current.notifications.reminderRemoveAction)
                                }
                            } else {
                                IconButton(onClick = { showReminderDialog = true }) {
                                    Icon(Icons.Default.Notifications, contentDescription = AppStrings.current.notifications.remindMe)
                                }
                            }
                        }
                        IconButton(
                            onClick = { scope.launch { viewModel.addToCalendar(eventId) } },
                            enabled = !state.isAddedToCalendar
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = AppStrings.current.events.addToCalendar)
                        }
                    }
                )
            }
        ) { padding ->

        SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch { viewModel.loadEvent(eventId, forceRefresh = true) } },
            modifier = Modifier.padding(padding)
        ) {
            if (ev == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(AppStrings.current.events.noEventToShow, modifier = Modifier.padding(16.dp))
                }
                } else {
                Column(modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
                ) {
            Text(state.eventName, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(6.dp))
            val statusChips = buildList<String> {
                if (state.isVisible) add(AppStrings.current.events.isVisible)
                if (state.isPublic) add(AppStrings.current.events.isPublic)
                if (state.isLocked) add(AppStrings.current.events.registrationClosed)
                if (state.enableNotes) add(AppStrings.current.events.notesAllowed)
            }
            if (statusChips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    statusChips.forEach { label ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Typ a základní info
            val displayType = translateEventType(state.eventType)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (!displayType.isNullOrBlank()) displayType else "—",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (!state.locationName.isNullOrBlank()) state.locationName!! else "—",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            if (state.eventDateText.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(state.eventDateText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

        // Seznam termínů
        // Show the instances card only when there is more than one instance
        if (state.instances.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(AppStrings.current.events.eventDates, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    val instancesToShow = if (showAllInstances.value || state.instances.size <= 3) {
                        state.instances
                    } else {
                        state.instances.take(3)
                    }
                    
                    instancesToShow.forEachIndexed { index, instanceEl ->
                        val instance = instanceEl.asJsonObjectOrNull() ?: return@forEachIndexed
                        val since = instance.str("since")
                        val until = instance.str("until")
                        val instLocation = instance["location"].asJsonObjectOrNull()?.str("name")
                        
                        if (index > 0) Spacer(modifier = Modifier.height(4.dp))
                        
                        Text("${index + 1}. ${formatTimesWithDateAlways(since, until)}", 
                            style = MaterialTheme.typography.bodySmall)
                        
                        if (!instLocation.isNullOrBlank() && instLocation != state.locationName) {
                            Text("   ${AppStrings.current.events.place}: $instLocation", 
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ))
                        }
                    }
                    
                    if (state.instances.size > 3 && !showAllInstances.value) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { showAllInstances.value = true }) {
                            Text("${AppStrings.current.commonActions.showAll.replace("vše", "všech")} ${state.instances.size} ${AppStrings.current.events.eventDates.lowercase()}")
                        }
                    } else if (state.instances.size > 3 && showAllInstances.value) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { showAllInstances.value = false }) {
                            Text(AppStrings.current.commonActions.showLess)
                        }
                    }
                }
            }
        }

        // Registrační tlačítka
        if (state.registerButtonVisible || state.registrationActionsRowVisible) {
            Spacer(modifier = Modifier.height(8.dp))
            val deleteFullWidth = state.trainers.size == 1
            Column(modifier = Modifier.fillMaxWidth()) {
                if (state.registerButtonVisible) {
                    Button(onClick = { onOpenRegistration?.invoke("register", null) }, modifier = Modifier.fillMaxWidth()) {
                        Text(AppStrings.current.registration.register)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (state.registrationActionsRowVisible) {
                    if (state.editRegistrationButtonVisible) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { onOpenRegistration?.invoke("register", null) },
                                modifier = Modifier.widthIn(max = 300.dp).padding(end = 112.dp)
                            ) {
                                Text(AppStrings.current.registration.registerAnother)
                            }
                            Row(modifier = Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { onOpenRegistration?.invoke("edit", null) }) {
                                    Icon(Icons.Default.Edit, contentDescription = AppStrings.current.registration.editRegistration)
                                }
                                FilledTonalButton(onClick = { onOpenRegistration?.invoke("delete", null) }) {
                                    Icon(Icons.Default.Delete, contentDescription = AppStrings.current.registration.deleteRegistration)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    } else {
                        Button(
                            onClick = { onOpenRegistration?.invoke("delete", null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(AppStrings.current.registration.deleteRegistration)
                        }
                    }
                }
            }
        }

        // Popis a shrnutí
        if (state.summary.isNotBlank() || state.eventDescription.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val headerText = when {
                        state.summary.isNotBlank() && state.eventDescription.isNotBlank() -> "Shrnutí a popis"
                        state.summary.isNotBlank() -> "Shrnutí"
                        else -> "Popis"
                    }
                    Text(headerText, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    val bodySizeSp = MaterialTheme.typography.bodyMedium.fontSize.value
                    if (state.summary.isNotBlank()) {
                        HtmlText(
                            html = state.summary,
                            modifier = Modifier.fillMaxWidth(),
                            textColor = MaterialTheme.colorScheme.onBackground,
                            linkColor = MaterialTheme.colorScheme.primary,
                            textSizeSp = bodySizeSp,
                            selectable = true
                        )
                        if (state.eventDescription.isNotBlank()) Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (state.eventDescription.isNotBlank()) {
                        HtmlText(
                            html = state.eventDescription,
                            modifier = Modifier.fillMaxWidth(),
                            textColor = MaterialTheme.colorScheme.onBackground,
                            linkColor = MaterialTheme.colorScheme.primary,
                            textSizeSp = bodySizeSp,
                            selectable = true
                        )
                    }
                }
            }
        }

        // Trenéři
        if (state.trainers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(AppStrings.current.profile.trainers, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    state.trainers.forEach { trainerEl ->
                        val trainer = trainerEl.asJsonObjectOrNull() ?: return@forEach
                        val personId = trainer.str("personId") ?: return@forEach
                        val trainerName = trainer.str("name") ?: return@forEach
                        val lessonsOffered = trainer.int("lessonsOffered")?.takeIf { it > 0 }
                        val lessonsRemaining = trainer.int("lessonsRemaining")?.takeIf { it > 0 }
                        val label = when {
                            lessonsOffered != null && lessonsRemaining != null ->
                                "$trainerName (nabízí: $lessonsOffered, zbývá: $lessonsRemaining)"
                            else -> trainerName
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (onOpenPerson != null) Modifier.clickable { onOpenPerson(personId) } else Modifier)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Cílové kohorty
        if (state.cohorts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(AppStrings.current.events.targetGroups, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(state.cohortDisplayNames.ifBlank { "(žádné)" }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Přihlášení účastníci
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                val totalCount = state.registrations.size + state.externalRegistrations.size
                Text("${AppStrings.current.registration.participants} ($totalCount)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                if (totalCount == 0) {
                    Text(AppStrings.current.registration.noParticipants, style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                } else {
                    // Běžné registrace
                    state.registrations.forEach { regEl ->
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
                        val isMe = (personId != null && personId == state.myPersonId) || (coupleId != null && state.myCoupleIds.contains(coupleId))

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
                                val trainerName = state.trainers.firstOrNull { trainerEl ->
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
                    state.externalRegistrations.forEach { extRegEl ->
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
}   // closes SwipeToReload

    // Reminder dialog
    if (showReminderDialog) {
        val existingMinutes = state.reminderMinutesBefore ?: 30
        val isHoursInit = existingMinutes >= 60 && existingMinutes % 60 == 0
        var reminderUnit by remember(showReminderDialog) { mutableStateOf(if (isHoursInit) "h" else "min") }
        var reminderValue by remember(showReminderDialog) { mutableIntStateOf(if (isHoursInit) existingMinutes / 60 else existingMinutes) }
        AlertDialog(
            onDismissRequest = { showReminderDialog = false },
            title = { Text(AppStrings.current.notifications.reminderDialogTitle) },
            text = {
                QuantityInput(
                    value = reminderValue,
                    onValueChange = { v, u -> reminderValue = v; reminderUnit = u },
                    units = listOf("min", "h"),
                    defaultUnit = reminderUnit,
                    label = AppStrings.current.notifications.timeAheadLabel,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val minutes = if (reminderUnit == "h") reminderValue * 60 else reminderValue
                    scope.launch { viewModel.setReminder(eventId, minutes) }
                    showReminderDialog = false
                }) { Text(AppStrings.current.commonActions.save) }
            },
            dismissButton = {
                TextButton(onClick = { showReminderDialog = false }) { Text(AppStrings.current.commonActions.cancel) }
            }
        )
    }
}  // closes Scaffold { padding ->
}  // closes EventScreen