package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import com.tkolymp.tkolympapp.components.CoupleAvatar
import com.tkolymp.tkolympapp.components.InitialsAvatar
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.asJsonArrayOrNull
import com.tkolymp.shared.utils.asJsonObjectOrNull
import com.tkolymp.shared.utils.formatTimesWithDateAlways
import com.tkolymp.shared.utils.int
import com.tkolymp.shared.utils.str
import com.tkolymp.shared.utils.translateEventType
import com.tkolymp.shared.viewmodels.EventSideEffect
import com.tkolymp.shared.viewmodels.EventViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.QuantityInput
import com.tkolymp.tkolympapp.util.StaggeredItem
import com.tkolymp.tkolympapp.components.parseColorOrDefault
import com.tkolymp.tkolympapp.platform.HtmlText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import com.tkolymp.tkolympapp.platform.FullscreenImageViewer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EventScreen(eventId: Long, instanceId: Long? = null, onBack: (() -> Unit)? = null, onOpenRegistration: ((String, String?) -> Unit)? = null, onOpenPerson: ((String) -> Unit)? = null) {
    val viewModel = viewModel<EventViewModel>()
    val state by viewModel.state.collectAsState()
    val showAllInstances = remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showReminderDialog by remember { mutableStateOf(false) }
    var showRegistrationDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(eventId) {
        viewModel.loadEvent(eventId, instanceId = instanceId, forceRefresh = true)
    }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is EventSideEffect.CalendarResult -> {
                    val msg = if (effect.success) AppStrings.current.events.addToCalendarSuccess
                              else AppStrings.current.events.addToCalendarFailed
                    snackbarHostState.showSnackbar(msg)
                }
                is EventSideEffect.ReminderResult -> {
                    val msg = if (effect.set) AppStrings.current.notifications.reminderSet
                              else AppStrings.current.notifications.reminderRemoveAction
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    val ev = state.eventJson
    val fullScreenImageUrl = remember { mutableStateOf<String?>(null) }
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(ev) { if (ev != null) contentVisible = true }
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
        Box(modifier = Modifier.fillMaxSize()) {

        SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch { viewModel.loadEvent(eventId, instanceId = instanceId, forceRefresh = true) } },
            modifier = Modifier.padding(padding)
        ) {
            if (ev == null) {
                if (state.error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error?.message ?: "", modifier = Modifier.padding(16.dp))
                            TextButton(onClick = { scope.launch { viewModel.loadEvent(eventId, instanceId = instanceId, forceRefresh = true) } }) {
                                Text(AppStrings.current.commonActions.retry)
                            }
                        }
                    }
                }
                // else: initial state before loading starts — SwipeToReload shows spinner
                } else {
                StaggeredItem(index = 0, visible = contentVisible, durationMs = 250) {
                Column(modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
                ) {
            Text(state.eventName, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(10.dp))
            // isError = true → errorContainer, false → secondaryContainer
            val statusChips = buildList<String> {
                if (state.isCancelled) add(AppStrings.current.stats.cancelled)
                if (state.isVisible) add(AppStrings.current.events.isVisible)
                if (state.isPublic) add(AppStrings.current.events.isPublic)
                if (state.isLocked) add(AppStrings.current.events.registrationClosed)
                if (state.enableNotes) add(AppStrings.current.events.notesAllowed)
            }
            if (statusChips.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    statusChips.forEach { label ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Typ a základní info
            val displayType = translateEventType(state.eventType)
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                @Composable
                fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                InfoRow(Icons.Default.Category, if (!displayType.isNullOrBlank()) displayType else "—")

                if (!state.locationName.isNullOrBlank() || state.eventDateText.isNotBlank()) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                }

                val locationName = state.locationName
                if (!locationName.isNullOrBlank()) {
                    InfoRow(Icons.Default.Place, locationName)
                    if (state.eventDateText.isNotBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                    }
                }

                if (state.scheduleText != null || state.eventDateText.isNotBlank()) {
                    val scheduleText = state.scheduleText
                    if (scheduleText != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(scheduleText, style = MaterialTheme.typography.bodyMedium)
                                if (state.eventDateText.isNotBlank()) {
                                    Text(
                                        state.eventDateText,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        InfoRow(Icons.Default.Schedule, state.eventDateText)
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
                            selectable = true,
                            onImageClick = { url -> fullScreenImageUrl.value = url }
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
                            selectable = true,
                            onImageClick = { url -> fullScreenImageUrl.value = url }
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
                            InitialsAvatar(name = trainerName, size = 28.dp, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
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
                    state.cohorts.forEach { cohortEl ->
                        val cohort = cohortEl.asJsonObjectOrNull()?.get("cohort")?.asJsonObjectOrNull() ?: return@forEach
                        val name = cohort.str("name") ?: return@forEach
                        val colorRgb = cohort.str("colorRgb")
                        val color = try { parseColorOrDefault(colorRgb) } catch (_: Exception) { Color.Gray }
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .fillMaxHeight()
                                        .background(color, RoundedCornerShape(6.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(name, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
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
                    // Use 1 column when couples are the majority (their names are wider)
                    val coupleCount = state.registrations.count { regEl ->
                        regEl.asJsonObjectOrNull()?.get("couple")?.asJsonObjectOrNull() != null
                    }
                    val columns = if (coupleCount * 2 > state.registrations.size) 1 else 2

                    data class ParticipantItem(
                        val name: String,
                        val isMe: Boolean,
                        val subItems: List<String>,
                        val womanName: String? = null,
                        val manName: String? = null,
                    )
                    val participantList = buildList<ParticipantItem> {
                        state.registrations.forEach { regEl ->
                            val reg = regEl.asJsonObjectOrNull() ?: return@forEach
                            val person = reg["person"].asJsonObjectOrNull()
                            val couple = reg["couple"].asJsonObjectOrNull()
                            val note = reg.str("note")
                            val lessonDemands = reg["eventLessonDemandsByRegistrationIdList"].asJsonArrayOrNull() ?: JsonArray(emptyList())

                            val womanName: String?
                            val manName: String?
                            val nameText: String
                            if (couple != null) {
                                womanName = couple["woman"].asJsonObjectOrNull()?.str("name")?.takeIf { it.isNotBlank() }
                                manName = couple["man"].asJsonObjectOrNull()?.str("name")?.takeIf { it.isNotBlank() }
                                nameText = when {
                                    womanName != null && manName != null -> "$womanName - $manName"
                                    womanName != null -> womanName
                                    manName != null -> manName
                                    else -> return@forEach
                                }
                            } else {
                                womanName = null
                                manName = null
                                nameText = person?.str("name")?.takeIf { it.isNotBlank() } ?: return@forEach
                            }

                            val personId = person?.str("id")
                            val coupleId = couple?.str("id")
                            val isMe = (personId != null && personId == state.myPersonId) ||
                                       (coupleId != null && state.myCoupleIds.contains(coupleId))

                            val subItems = buildList<String> {
                                lessonDemands.forEach { demandEl ->
                                    val demand = demandEl.asJsonObjectOrNull() ?: return@forEach
                                    val trainerId = demand.int("trainerId")
                                    val lessonCount = demand.int("lessonCount")
                                    val trainerName = state.trainers.firstOrNull { trainerEl ->
                                        val trainer = trainerEl.asJsonObjectOrNull() ?: return@firstOrNull false
                                        trainer.int("id") == trainerId
                                    }?.asJsonObjectOrNull()?.str("name") ?: "Trenér #$trainerId"
                                    add(if (lessonCount != null && lessonCount > 0) "$trainerName: $lessonCount lekcí" else trainerName)
                                }
                                if (!note.isNullOrBlank()) add("Poznámka: $note")
                            }

                            add(ParticipantItem(nameText, isMe, subItems, womanName, manName))
                        }

                        state.externalRegistrations.forEach { extRegEl ->
                            val extReg = extRegEl.asJsonObjectOrNull() ?: return@forEach
                            val firstName = extReg.str("firstName") ?: ""
                            val lastName = extReg.str("lastName") ?: ""
                            val email = extReg.str("email")
                            val note = extReg.str("note")
                            val subItems = buildList<String> {
                                if (!email.isNullOrBlank()) add("Email: $email")
                                if (!note.isNullOrBlank()) add("Poznámka: $note")
                            }
                            add(ParticipantItem("$firstName $lastName (externí)", false, subItems))
                        }
                    }

                    participantList.chunked(columns).forEach { pair ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            pair.forEach { item ->
                                Column(modifier = Modifier.weight(1f).padding(vertical = 5.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (item.womanName != null || item.manName != null) {
                                            CoupleAvatar(
                                                womanName = item.womanName,
                                                manName = item.manName,
                                                size = 24.dp,
                                                fontSize = 9.sp,
                                            )
                                        } else {
                                            InitialsAvatar(
                                                name = item.name,
                                                size = 24.dp,
                                                fontSize = 9.sp,
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            item.name,
                                            style = if (item.isMe)
                                                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                            else
                                                MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    item.subItems.forEach { sub ->
                                        Text(
                                            sub,
                                            modifier = Modifier.padding(start = 32.dp, top = 1.dp),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }
                            if (columns > 1) repeat(columns - pair.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(if (state.registerButtonVisible || state.registrationActionsRowVisible) 96.dp else 16.dp))
    }
}
} // StaggeredItem
}   // closes SwipeToReload

        // Floating registration button — no background, overlays scroll content
        val showRegistrationBar = state.registerButtonVisible || state.registrationActionsRowVisible
        if (showRegistrationBar && onOpenRegistration != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.editRegistrationButtonVisible -> {
                        Box {
                            SplitButtonLayout(
                                modifier = Modifier.height(56.dp),
                                leadingButton = {
                                    SplitButtonDefaults.LeadingButton(
                                        onClick = { onOpenRegistration.invoke("edit", null) },
                                        shapes = SplitButtonDefaults.leadingButtonShapesFor(SplitButtonDefaults.MediumContainerHeight),
                                        contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(SplitButtonDefaults.MediumContainerHeight)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(AppStrings.current.registration.editRegistration)
                                    }
                                },
                                trailingButton = {
                                    SplitButtonDefaults.TrailingButton(
                                        checked = showRegistrationDropdown,
                                        onCheckedChange = { showRegistrationDropdown = it },
                                        shapes = SplitButtonDefaults.trailingButtonShapesFor(SplitButtonDefaults.MediumContainerHeight),
                                        contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(SplitButtonDefaults.MediumContainerHeight)
                                    ) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenu(
                                expanded = showRegistrationDropdown,
                                onDismissRequest = { showRegistrationDropdown = false }
                            ) {
                                if (state.registerButtonVisible) {
                                    DropdownMenuItem(
                                        text = { Text(AppStrings.current.registration.registerAnother) },
                                        leadingIcon = { Icon(Icons.Default.Add, null) },
                                        onClick = {
                                            showRegistrationDropdown = false
                                            onOpenRegistration.invoke("register", null)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(AppStrings.current.registration.deleteRegistration) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    onClick = {
                                        showRegistrationDropdown = false
                                        onOpenRegistration.invoke("delete", null)
                                    }
                                )
                            }
                        }
                    }
                    state.registerButtonVisible -> {
                        Button(
                            onClick = { onOpenRegistration.invoke("register", null) },
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(AppStrings.current.registration.register, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    else -> {
                        Button(
                            onClick = { onOpenRegistration.invoke("delete", null) },
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(AppStrings.current.registration.deleteRegistration, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // Fullscreen image viewer for clicked images
        fullScreenImageUrl.value?.let { url ->
            FullscreenImageViewer(imageUrl = url) { fullScreenImageUrl.value = null }
        }
        } // closes Box(fillMaxSize)

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