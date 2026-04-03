package com.tkolymp.tkolympapp.screens
import com.tkolymp.tkolympapp.SwipeToReload

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tkolymp.shared.Logger
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.viewmodels.RegistrationViewModel
import com.tkolymp.shared.registration.computeInitCountsFor
import com.tkolymp.shared.registration.computeInitialRegistrant
import com.tkolymp.shared.registration.computeRegisteredCoupleIds
import com.tkolymp.shared.registration.computeRegisteredPersonIds
import com.tkolymp.shared.registration.filterOwnedRegistrations
import com.tkolymp.shared.registration.RegMode
import com.tkolymp.shared.registration.LessonInput
import com.tkolymp.shared.registration.RegistrationInput
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

private fun kotlinx.serialization.json.JsonElement?.asJsonObjectOrNull(): JsonObject? = try {
    when {
        this == null -> null
        this is kotlinx.serialization.json.JsonNull -> null
        this is JsonObject -> this
        else -> null
    }
} catch (_: Exception) { null }

private fun kotlinx.serialization.json.JsonElement?.asJsonArrayOrNull(): JsonArray? = try {
    when {
        this == null -> null
        this is kotlinx.serialization.json.JsonNull -> null
        this is JsonArray -> this
        else -> null
    }
} catch (_: Exception) { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    eventId: Long,
    mode: RegMode,
    trainers: JsonArray,
    registrations: JsonArray,
    myPersonId: String?,
    myCoupleIds: List<String>,
    // optional display names: if provided the UI will show these instead of generic labels
    myPersonName: String? = null,
    myCoupleNames: Map<String, String> = emptyMap(),
    enableNotes: Boolean = false,
    eventType: String? = null,
    onClose: () -> Unit,
    onRegister: (List<RegistrationInput>) -> Unit,
    onSetLessonDemand: (String, Int, Int) -> Unit,
    onDelete: (String) -> Unit,
    onSetNote: ((String, String) -> Unit)? = null
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when (mode) { is RegMode.Register -> AppStrings.current.registration.register; is RegMode.Edit -> AppStrings.current.registration.editRegistration; is RegMode.Delete -> AppStrings.current.registration.deleteRegistration }) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                    }
                }
            )
        }
    ) { padding ->
        val scope = rememberCoroutineScope()

        // cache trainer display names (shared across modes) — moved to shared ViewModel
        val showLessonSelection = eventType?.trim()?.lowercase().let { it == "camp" || it == "rezervation" }

        val regViewModel = viewModel<RegistrationViewModel>()
        val regState by regViewModel.state.collectAsState()
        LaunchedEffect(Unit) {
            regViewModel.loadNames(trainers, registrations, myPersonId, myCoupleIds, myPersonName, myCoupleNames)
        }

        // Ensure we refresh every time the screen becomes visible (ON_RESUME).
        val lifecycleOwner = LocalLifecycleOwner.current
        // Re-create observer when `trainers` changes so the observer captures up-to-date data
        DisposableEffect(lifecycleOwner, trainers) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    scope.launch {
                        try {
                            regViewModel.invalidateAndRefresh(trainers, registrations, myPersonId, myCoupleIds)
                        } catch (e: CancellationException) { throw e } catch (t: Exception) {
                            Logger.d("RegScreen", "lifecycle refresh failed: ${t.message}")
                        }
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        SwipeToReload(isRefreshing = regState.isLoading, onRefresh = {
            scope.launch {
                try {
                    regViewModel.invalidateAndRefresh(trainers, registrations, myPersonId, myCoupleIds)
                } catch (e: CancellationException) { throw e } catch (t: Exception) {
                    Logger.d("RegScreen", "refresh failed: ${t.message}")
                }
            }
        }, modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp)
            ) {

        when (mode) {
            is RegMode.Register -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(AppStrings.current.registration.selectRegistrant, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        // compute already-registered person/couple ids so we don't offer them for new registration
                        val registeredPersonIds = remember(registrations) { computeRegisteredPersonIds(registrations) }
                        val registeredCoupleIds = remember(registrations) { computeRegisteredCoupleIds(registrations) }

                        // pick a sensible initial selection: prefer self if not already registered, otherwise first unregistered couple
                        val selectedRegistrant = remember {
                            mutableStateOf(computeInitialRegistrant(myPersonId, myCoupleIds, registeredPersonIds, registeredCoupleIds))
                        }
                        // local observable display name state (start with provided names if any)
                        val personNameState = remember { androidx.compose.runtime.mutableStateOf<String?>(myPersonName) }
                        val coupleNamesState = remember { mutableStateMapOf<String, String>().apply { putAll(myCoupleNames) } }

                        // populate local hints from shared ViewModel state
                        LaunchedEffect(myPersonId, myCoupleIds, regState) {
                            // if server-provided name exists, prefer it (refresh should replace stale hints)
                            regState.myPersonName?.let { personNameState.value = it }
                            // merge couple names from view model (overwrite to keep them fresh)
                            regState.myCoupleNames.forEach { (k, v) -> if (!v.isNullOrBlank()) coupleNamesState[k] = v }
                        }
                        Column {
                                if (myPersonId != null && !registeredPersonIds.contains(myPersonId)) {
                                        val personLabel = personNameState.value ?: "Jako já"
                                        Row(modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable { selectedRegistrant.value = Pair(myPersonId, null) },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = selectedRegistrant.value.first == myPersonId && selectedRegistrant.value.second == null,
                                                onClick = { selectedRegistrant.value = Pair(myPersonId, null) })
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(personLabel)
                                        }
                                    }
                                    myCoupleIds.forEach { cid ->
                                        if (registeredCoupleIds.contains(cid)) return@forEach
                                        val coupleLabel = coupleNamesState[cid] ?: "Pár $cid"
                                        Row(modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable { selectedRegistrant.value = Pair(null, cid) },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = selectedRegistrant.value.first == null && selectedRegistrant.value.second == cid,
                                                onClick = { selectedRegistrant.value = Pair(null, cid) })
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(coupleLabel)
                                        }
                                    }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        // Optional note input (visible only when event allows notes)
                        val noteState = remember { mutableStateOf("") }
                        if (enableNotes) {
                            OutlinedTextField(
                                value = noteState.value,
                                onValueChange = { noteState.value = it },
                                label = { Text(AppStrings.current.registration.noteOptional) },
                                placeholder = { Text(AppStrings.current.registration.registrationNote) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Maintain local counts per trainer (observable list so element changes update instantly)
                        val counts = remember {
                            val list = mutableStateListOf<Int>()
                            repeat(trainers.size) { list.add(0) }
                            list
                        }

                        if (showLessonSelection) {
                            Text(AppStrings.current.registration.selectTrainersAndLessons, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(6.dp))

                            trainers.forEachIndexed { idx, tEl ->
                                val tObj = tEl as? JsonObject
                                val tIdStr = tObj?.get("id")?.jsonPrimitive?.contentOrNull ?: idx.toString()
                                val tName = tObj?.get("name")?.jsonPrimitive?.contentOrNull
                                    ?: regState.trainerNames[tIdStr]
                                    ?: "Trenér #$idx"
                                Row(modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Text(tName, modifier = Modifier.weight(1f))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            val old = counts.getOrNull(idx) ?: 0
                                            val newVal = (old - 1).coerceAtLeast(0)
                                            if (idx < counts.size) counts[idx] = newVal else counts.add(newVal)
                                            Logger.d("RegScreen", "Register mode: trainer=$idx old=$old new=$newVal")
                                        }) { Icon(Icons.Default.Remove, contentDescription = "Snížit") }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("${counts[idx]}", modifier = Modifier.padding(8.dp))
                                        IconButton(onClick = {
                                            val old = counts.getOrNull(idx) ?: 0
                                            val maxForTrainer = (tObj?.get("lessonsRemaining")?.jsonPrimitive?.intOrNull
                                                ?.let { if (it < 0) Int.MAX_VALUE else it } ?: Int.MAX_VALUE)
                                            val newVal = (old + 1).coerceAtMost(maxForTrainer)
                                            if (idx < counts.size) counts[idx] = newVal else counts.add(newVal)
                                            Logger.d("RegScreen", "Register mode: trainer=$idx old=$old max=$maxForTrainer new=$newVal")
                                        }) { Icon(Icons.Default.Add, contentDescription = "Přidat") }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        val showSelectError = remember { mutableStateOf(false) }
                        val showRegisterError = remember { mutableStateOf<String?>(null) }
                        if (showSelectError.value) {
                            Text(AppStrings.current.registration.noRegistrationSelected, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Button(onClick = {
                            val registrant = selectedRegistrant.value
                            if (registrant.first.isNullOrBlank() && registrant.second.isNullOrBlank()) {
                                showSelectError.value = true
                                return@Button
                            }
                            showSelectError.value = false
                            showRegisterError.value = null
                            val lessons = trainers.mapIndexedNotNull { i, tEl ->
                                val cnt = counts[i]
                                if (cnt > 0) {
                                    val trainerId = (tEl as? JsonObject)?.get("id")?.jsonPrimitive?.intOrNull ?: i
                                    LessonInput(trainerId, cnt)
                                } else null
                            }
                            val noteToSend = noteState.value.takeIf { it.isNotBlank() }
                            val regInput = RegistrationInput(registrant.first, registrant.second, lessons, note = noteToSend)
                            try {
                                onRegister(listOf(regInput))
                                onClose()
                            } catch (e: CancellationException) { throw e } catch (t: Exception) {
                                Logger.d("RegScreen", "Register failed: ${t.message}")
                                showRegisterError.value = t.message ?: "Chyba při registraci"
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(AppStrings.current.registration.confirmRegistrationTitle) }
                        if (!showRegisterError.value.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(showRegisterError.value!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            is RegMode.Edit -> {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(12.dp)) {
                    Text(AppStrings.current.registration.selectRegistration, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))

                        val selectedRegId = remember { mutableStateOf<String?>(null) }
                        // registrations that belong to current user
                        val ownedRegistrations = remember(registrations, myPersonId, myCoupleIds) {
                            filterOwnedRegistrations(registrations, myPersonId, myCoupleIds)
                        }


                        val countsByReg = remember {
                            val map = mutableStateMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<Int>>()
                            // prepopulate only for owned registrations so switching doesn't recompute
                            ownedRegistrations.forEach { rEl ->
                                val r = rEl as? JsonObject
                                val rid = r?.get("id")?.jsonPrimitive?.contentOrNull
                                if (rid != null) {
                                    val init = computeInitCountsFor(ownedRegistrations, trainers, rid)
                                    val st = mutableStateListOf<Int>()
                                    init.forEach { st.add(it) }
                                    map[rid] = st
                                }
                            }
                            map
                        }

                        Column {
                            ownedRegistrations.forEach { rEl ->
                                val r = rEl as? JsonObject
                                val rid = r?.get("id")?.jsonPrimitive?.contentOrNull
                                val labelFromJson = r?.get("person").asJsonObjectOrNull()?.get("name")?.jsonPrimitive?.contentOrNull
                                    ?: r?.get("couple").asJsonObjectOrNull()?.get("id")?.jsonPrimitive?.contentOrNull
                                val label = regState.registrationDisplayNames[rid ?: ""] ?: labelFromJson ?: "#${rid ?: "?"}"
                                Row(modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        selectedRegId.value = rid
                                    },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selectedRegId.value == rid, onClick = { selectedRegId.value = rid })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(label ?: "registrace")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val selectedId = selectedRegId.value
                        val showEditError = remember { mutableStateOf<String?>(null) }
                        if (selectedId != null) {
                            // pre-populate note from the selected registration JSON
                            val editNoteState = remember { mutableStateOf("") }
                            LaunchedEffect(selectedId) {
                                val reg = ownedRegistrations.firstOrNull {
                                    (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull == selectedId
                                } as? JsonObject
                                editNoteState.value = reg?.get("note")?.jsonPrimitive?.contentOrNull ?: ""
                            }
                            // ensure counts exist for this registration (needed even when hidden to send updates)
                            val sid = selectedId
                            val countsState = countsByReg.getOrPut(sid) {
                                val init = computeInitCountsFor(ownedRegistrations, trainers, sid)
                                val st = mutableStateListOf<Int>()
                                init.forEach { st.add(it) }
                                st
                            }

                            if (showLessonSelection) {
                                Text(AppStrings.current.misc.editLessonClaims, style = MaterialTheme.typography.bodyMedium)
                                trainers.forEachIndexed { idx, tEl ->
                                    val tObj = tEl as? JsonObject
                                    val tIdStr = tObj?.get("id")?.jsonPrimitive?.contentOrNull ?: idx.toString()
                                    val tName = tObj?.get("name")?.jsonPrimitive?.contentOrNull ?: regState.trainerNames[tIdStr] ?: "Trenér #$idx"
                                    Row(modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        Text(tName, modifier = Modifier.weight(1f))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = {
                                                val old = countsState.getOrNull(idx) ?: 0
                                                val newVal = (old - 1).coerceAtLeast(0)
                                                if (idx < countsState.size) countsState[idx] = newVal else countsState.add(newVal)
                                            }) { Icon(Icons.Default.Remove, contentDescription = "Snížit") }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("${countsState.getOrNull(idx) ?: 0}", modifier = Modifier.padding(8.dp))
                                            IconButton(onClick = {
                                                val old = countsState.getOrNull(idx) ?: 0
                                                val maxForTrainer = (tObj?.get("lessonsRemaining")?.jsonPrimitive?.intOrNull
                                                    ?.let { if (it < 0) Int.MAX_VALUE else it } ?: Int.MAX_VALUE)
                                                val newVal = (old + 1).coerceAtMost(maxForTrainer)
                                                if (idx < countsState.size) countsState[idx] = newVal else countsState.add(newVal)
                                                Logger.d("RegScreen", "Edit mode: reg=$selectedId trainer=$idx old=$old max=$maxForTrainer new=$newVal")
                                            }) { Icon(Icons.Default.Add, contentDescription = "Přidat") }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            if (enableNotes) {
                                OutlinedTextField(
                                    value = editNoteState.value,
                                    onValueChange = { editNoteState.value = it },
                                    label = { Text(AppStrings.current.registration.noteOptional) },
                                    placeholder = { Text(AppStrings.current.registration.registrationNote) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Button(onClick = {
                                showEditError.value = null
                                try {
                                    // call onSetLessonDemand for each trainer using countsState (only for camp/rezervation)
                                    val regId = selectedId
                                    if (showLessonSelection) countsState.forEachIndexed { i, cnt ->
                                        val trainerId = (trainers[i] as? JsonObject)?.get("id")?.jsonPrimitive?.intOrNull ?: i
                                        onSetLessonDemand(regId!!, trainerId, cnt)
                                    }
                                    if (enableNotes) onSetNote?.invoke(selectedId, editNoteState.value)
                                    onClose()
                                } catch (e: CancellationException) { throw e } catch (t: Exception) {
                                    Logger.d("RegScreen", "SetLessonDemand failed: ${t.message}")
                                    showEditError.value = t.message ?: "Chyba při ukládání změn"
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Uložit změny") }
                            if (!showEditError.value.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(showEditError.value!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

            is RegMode.Delete -> {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(AppStrings.current.registration.selectToDelete, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        val selectedReg = remember { mutableStateOf<String?>(null) }
                        val showDeleteError = remember { mutableStateOf<String?>(null) }
                        // only registrations that belong to current user
                        val ownedRegistrations = remember(registrations, myPersonId, myCoupleIds) {
                            filterOwnedRegistrations(registrations, myPersonId, myCoupleIds)
                        }
                        Column {
                            ownedRegistrations.forEach { rEl ->
                                val r = rEl as? JsonObject
                                val rid = r?.get("id")?.jsonPrimitive?.contentOrNull
                                val labelFromJson = r?.get("person").asJsonObjectOrNull()?.get("name")?.jsonPrimitive?.contentOrNull
                                    ?: r?.get("couple").asJsonObjectOrNull()?.get("id")?.jsonPrimitive?.contentOrNull
                                val label = regState.registrationDisplayNames[rid ?: ""] ?: labelFromJson ?: "#${rid ?: "?"}"
                                Row(modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedReg.value = rid },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selectedReg.value == rid, onClick = { selectedReg.value = rid })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(label ?: "registrace")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        val showConfirmDelete = remember { mutableStateOf(false) }
                        Button(
                            onClick = {
                                showDeleteError.value = null
                                if (selectedReg.value != null) {
                                    showConfirmDelete.value = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedReg.value != null
                        ) { Text(AppStrings.current.registration.deleteSelectedRegistration) }

                        if (showConfirmDelete.value) {
                            AlertDialog(
                                onDismissRequest = { showConfirmDelete.value = false },
                                title = { Text(AppStrings.current.registration.deleteRegistrationConfirmTitle) },
                                text = { Text(AppStrings.current.registration.deleteRegistrationConfirmText) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showConfirmDelete.value = false
                                        showDeleteError.value = null
                                        selectedReg.value?.let { selId ->
                                            // verify ownership before deleting
                                            val owned = ownedRegistrations.firstOrNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull == selId } != null
                                            if (!owned) {
                                                showDeleteError.value = AppStrings.current.registration.noRegistrationOwned
                                                return@TextButton
                                            }
                                            try {
                                                onDelete(selId)
                                                onClose()
                                            } catch (e: CancellationException) { throw e } catch (t: Exception) {
                                                Logger.d("RegScreen", "Delete failed: ${t.message}")
                                                showDeleteError.value = t.message ?: "Chyba při mazání"
                                            }
                                        }
                                    }) { Text(AppStrings.current.commonActions.delete) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showConfirmDelete.value = false }) { Text(AppStrings.current.commonActions.cancel) }
                                }
                            )
                        }
                        if (!showDeleteError.value.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(showDeleteError.value!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}}}
