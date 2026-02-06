package com.tkolymp.tkolympapp

import android.util.Log
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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.ServiceLocator
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

sealed class RegMode {
    object Register : RegMode()
    object Edit : RegMode()
    object Delete : RegMode()
}

data class LessonInput(val trainerId: Int, val lessonCount: Int)
data class RegistrationInput(val personId: String?, val coupleId: String?, val lessons: List<LessonInput>)

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
    onClose: () -> Unit,
    onRegister: (List<RegistrationInput>) -> Unit,
    onSetLessonDemand: (String, Int, Int) -> Unit,
    onDelete: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when (mode) { is RegMode.Register -> "Zapsat se"; is RegMode.Edit -> "Upravit registraci"; is RegMode.Delete -> "Smazat registraci" }) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zpět")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
        ) {

        // cache trainer display names (shared across modes)
        val trainerNames = remember { mutableStateMapOf<String, String>() }
        LaunchedEffect(trainers) {
            try {
                val svc = ServiceLocator.peopleService
                trainers.forEachIndexed { idx, tEl ->
                    val tObj = tEl as? JsonObject
                    val tIdStr = tObj?.get("id")?.jsonPrimitive?.contentOrNull ?: idx.toString()
                    val personRef = tObj?.get("person").asJsonObjectOrNull()?.get("id")?.jsonPrimitive?.contentOrNull
                        ?: tObj?.get("personId")?.jsonPrimitive?.contentOrNull
                    val fetched = when {
                        !personRef.isNullOrBlank() -> try { svc.fetchPersonDisplayName(personRef, false) } catch (_: Throwable) { null }
                        else -> null
                    }
                    if (!fetched.isNullOrBlank()) trainerNames[tIdStr] = fetched
                }
            } catch (_: Throwable) {
            }
        }

        when (mode) {
            is RegMode.Register -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Vyberte, koho registrujete:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        val selectedRegistrant = remember { mutableStateOf<Pair<String?, String?>>(Pair(myPersonId, null)) }
                        // local observable display name state (start with provided names if any)
                        val personNameState = remember { androidx.compose.runtime.mutableStateOf<String?>(myPersonName) }
                        val coupleNamesState = remember { mutableStateMapOf<String, String>().apply { putAll(myCoupleNames) } }

                        // fetch names if not provided and peopleService available
                        LaunchedEffect(myPersonId, myCoupleIds) {
                            // fetch person name
                            if (personNameState.value.isNullOrBlank() && myPersonId != null) {
                                try {
                                    val svc = ServiceLocator.peopleService
                                    val fetched = svc.fetchPersonDisplayName(myPersonId, true)
                                    if (!fetched.isNullOrBlank()) personNameState.value = fetched
                                } catch (_: Throwable) {
                                }
                            }
                            // fetch couple names
                            myCoupleIds.forEach { cid ->
                                if (coupleNamesState[cid].isNullOrBlank()) {
                                    try {
                                        val svc = ServiceLocator.peopleService
                                        val fetched = svc.fetchCoupleDisplayName(cid)
                                        if (!fetched.isNullOrBlank()) coupleNamesState[cid] = fetched
                                    } catch (_: Throwable) {
                                    }
                                }
                            }
                        }
                        Column {
                            if (myPersonId != null) {
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
                        Text("Vyberte trenéry a počet lekcí:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))

                        // Maintain local counts per trainer (observable list so element changes update instantly)
                        val counts = remember {
                            val list = mutableStateListOf<Int>()
                            repeat(trainers.size) { list.add(0) }
                            list
                        }

                        trainers.forEachIndexed { idx, tEl ->
                            val tObj = tEl as? JsonObject
                            val tIdStr = tObj?.get("id")?.jsonPrimitive?.contentOrNull ?: idx.toString()
                            val tName = tObj?.get("name")?.jsonPrimitive?.contentOrNull
                                ?: trainerNames[tIdStr]
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
                                        Log.d("RegScreen", "Register mode: trainer=$idx old=$old new=$newVal")
                                    }) { Icon(Icons.Default.Remove, contentDescription = "Snížit") }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${counts[idx]}", modifier = Modifier.padding(8.dp))
                                    IconButton(onClick = {
                                        val old = counts.getOrNull(idx) ?: 0
                                        val maxForTrainer = (tObj?.get("lessonsRemaining")?.jsonPrimitive?.intOrNull
                                            ?.let { if (it < 0) Int.MAX_VALUE else it } ?: Int.MAX_VALUE)
                                        val newVal = (old + 1).coerceAtMost(maxForTrainer)
                                        if (idx < counts.size) counts[idx] = newVal else counts.add(newVal)
                                        Log.d("RegScreen", "Register mode: trainer=$idx old=$old max=$maxForTrainer new=$newVal")
                                    }) { Icon(Icons.Default.Add, contentDescription = "Přidat") }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        val showSelectError = remember { mutableStateOf(false) }
                        val showRegisterError = remember { mutableStateOf<String?>(null) }
                        if (showSelectError.value) {
                            Text("Vyberte někoho k registraci", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                            val regInput = RegistrationInput(registrant.first, registrant.second, lessons)
                            try {
                                onRegister(listOf(regInput))
                                onClose()
                            } catch (t: Throwable) {
                                Log.e("RegScreen", "Register failed", t)
                                showRegisterError.value = t.message ?: "Chyba při registraci"
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Potvrdit registraci") }
                        if (!showRegisterError.value.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(showRegisterError.value!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            is RegMode.Edit -> {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(12.dp)) {
                    Text("Vyberte svou registraci:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))

                        val selectedRegId = remember { mutableStateOf<String?>(null) }
                        // helper to compute initial counts for a registration id
                        fun computeInitCountsFor(regId: String): MutableList<Int> {
                            val reg = registrations.firstOrNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull == regId } as? JsonObject
                            val demands = reg?.get("eventLessonDemandsByRegistrationIdList").asJsonArrayOrNull()
                            return trainers.map { t ->
                                val tId = (t as? JsonObject)?.get("id")?.jsonPrimitive?.intOrNull
                                val found = demands?.firstOrNull { (it as? JsonObject)?.get("trainerId")?.jsonPrimitive?.intOrNull == tId } as? JsonObject
                                found?.get("lessonCount")?.jsonPrimitive?.intOrNull ?: 0
                            }.toMutableList()
                        }

                        // keep editable counts per registration so edits persist when switching
                        val countsByReg = remember {
                            val map = mutableStateMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<Int>>()
                            // prepopulate for all registrations so switching doesn't recompute
                            registrations.forEach { rEl ->
                                val r = rEl as? JsonObject
                                val rid = r?.get("id")?.jsonPrimitive?.contentOrNull
                                if (rid != null) {
                                    val init = computeInitCountsFor(rid)
                                    val st = mutableStateListOf<Int>()
                                    init.forEach { st.add(it) }
                                    map[rid] = st
                                }
                            }
                            map
                        }

                        Column {
                            // fetch display names for registrations (person/couple) and show them
                            val regDisplayNames = remember { mutableStateMapOf<String, String>() }
                            LaunchedEffect(registrations) {
                                try {
                                    val svc = ServiceLocator.peopleService
                                    registrations.forEach { rEl ->
                                        val r = rEl as? JsonObject
                                        val rid = r?.get("id")?.jsonPrimitive?.contentOrNull
                                        if (rid != null) {
                                            val personId = r?.get("person").asJsonObjectOrNull()?.get("id")?.jsonPrimitive?.contentOrNull
                                            val coupleId = r?.get("couple").asJsonObjectOrNull()?.get("id")?.jsonPrimitive?.contentOrNull
                                            val fetched = when {
                                                !personId.isNullOrBlank() -> try { svc.fetchPersonDisplayName(personId, false) } catch (_: Throwable) { null }
                                                !coupleId.isNullOrBlank() -> try { svc.fetchCoupleDisplayName(coupleId) } catch (_: Throwable) { null }
                                                else -> null
                                            }
                                            if (!fetched.isNullOrBlank()) regDisplayNames[rid] = fetched
                                        }
                                    }
                                } catch (_: Throwable) {
                                }
                            }

                            registrations.forEach { rEl ->
                                val r = rEl as? JsonObject
                                val rid = r?.get("id")?.jsonPrimitive?.contentOrNull
                                val labelFromJson = r?.get("person").asJsonObjectOrNull()?.get("name")?.jsonPrimitive?.contentOrNull
                                    ?: r?.get("couple").asJsonObjectOrNull()?.get("id")?.jsonPrimitive?.contentOrNull
                                val label = regDisplayNames[rid ?: ""] ?: labelFromJson ?: "#${rid ?: "?"}"
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
                            Text("Upravit nároky na lekce:", style = MaterialTheme.typography.bodyMedium)
                            // ensure counts exist for this registration
                            val countsState = countsByReg[selectedId]!!
                            trainers.forEachIndexed { idx, tEl ->
                                val tObj = tEl as? JsonObject
                                val tIdStr = tObj?.get("id")?.jsonPrimitive?.contentOrNull ?: idx.toString()
                                val tName = tObj?.get("name")?.jsonPrimitive?.contentOrNull ?: trainerNames[tIdStr] ?: "Trenér #$idx"
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
                                            Log.d("RegScreen", "Edit mode: reg=$selectedId trainer=$idx old=$old max=$maxForTrainer new=$newVal")
                                        }) { Icon(Icons.Default.Add, contentDescription = "Přidat") }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = {
                                showEditError.value = null
                                try {
                                    // call onSetLessonDemand for each trainer using countsState
                                    val regId = selectedId
                                    countsState.forEachIndexed { i, cnt ->
                                        val trainerId = (trainers[i] as? JsonObject)?.get("id")?.jsonPrimitive?.intOrNull ?: i
                                        onSetLessonDemand(regId!!, trainerId, cnt)
                                    }
                                    onClose()
                                } catch (t: Throwable) {
                                    Log.e("RegScreen", "SetLessonDemand failed", t)
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
                        Text("Vyberte registraci k odstranění:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        val selectedReg = remember { mutableStateOf<String?>(null) }
                        val showDeleteError = remember { mutableStateOf<String?>(null) }
                        Column {
                            // fetch display names for registrations (person/couple) and show them
                            val regDisplayNames = remember { mutableStateMapOf<String, String>() }
                            LaunchedEffect(registrations) {
                                try {
                                    val svc = ServiceLocator.peopleService
                                    registrations.forEach { rEl ->
                                        val r = rEl as? JsonObject
                                        val rid = r?.get("id")?.jsonPrimitive?.contentOrNull
                                        if (rid != null) {
                                            val personId = r?.get("person").asJsonObjectOrNull()?.get("id")?.jsonPrimitive?.contentOrNull
                                            val coupleId = r?.get("couple").asJsonObjectOrNull()?.get("id")?.jsonPrimitive?.contentOrNull
                                            val fetched = when {
                                                !personId.isNullOrBlank() -> try { svc.fetchPersonDisplayName(personId, false) } catch (_: Throwable) { null }
                                                !coupleId.isNullOrBlank() -> try { svc.fetchCoupleDisplayName(coupleId) } catch (_: Throwable) { null }
                                                else -> null
                                            }
                                            if (!fetched.isNullOrBlank()) regDisplayNames[rid] = fetched
                                        }
                                    }
                                } catch (_: Throwable) {
                                }
                            }

                            registrations.forEach { rEl ->
                                val r = rEl as? JsonObject
                                val rid = r?.get("id")?.jsonPrimitive?.contentOrNull
                                val labelFromJson = r?.get("person").asJsonObjectOrNull()?.get("name")?.jsonPrimitive?.contentOrNull
                                    ?: r?.get("couple").asJsonObjectOrNull()?.get("id")?.jsonPrimitive?.contentOrNull
                                val label = regDisplayNames[rid ?: ""] ?: labelFromJson ?: "#${rid ?: "?"}"
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
                        ) { Text("Smazat vybranou registraci") }

                        if (showConfirmDelete.value) {
                            AlertDialog(
                                onDismissRequest = { showConfirmDelete.value = false },
                                title = { Text("Potvrzení smazání") },
                                text = { Text("Opravdu chcete smazat vybranou registraci?") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showConfirmDelete.value = false
                                        showDeleteError.value = null
                                        selectedReg.value?.let {
                                            try {
                                                onDelete(it)
                                                onClose()
                                            } catch (t: Throwable) {
                                                Log.e("RegScreen", "Delete failed", t)
                                                showDeleteError.value = t.message ?: "Chyba při mazání"
                                            }
                                        }
                                    }) { Text("Smazat") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showConfirmDelete.value = false }) { Text("Zrušit") }
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
}}
