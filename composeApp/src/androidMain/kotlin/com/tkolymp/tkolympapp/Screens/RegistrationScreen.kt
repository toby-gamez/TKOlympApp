package com.tkolymp.tkolympapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    onClose: () -> Unit,
    onRegister: (List<RegistrationInput>) -> Unit,
    onSetLessonDemand: (String, Int, Int) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        TopAppBar(
            title = { Text(when (mode) { is RegMode.Register -> "Zapsat se"; is RegMode.Edit -> "Upravit registraci"; is RegMode.Delete -> "Smazat registraci" }) },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Zpět")
                }
            }
        )

        // show event id for debugging / clarity
        Text("ID: $eventId", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(8.dp))

        when (mode) {
            is RegMode.Register -> {
                Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Vyberte, koho registrujete:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        val selectedRegistrant = remember { mutableStateOf<Pair<String?, String?>>(Pair(myPersonId, null)) }
                        Column {
                            if (myPersonId != null) {
                                Row(modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedRegistrant.value = Pair(myPersonId, null) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selectedRegistrant.value.first == myPersonId && selectedRegistrant.value.second == null,
                                        onClick = { selectedRegistrant.value = Pair(myPersonId, null) })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Jako já")
                                }
                            }
                            myCoupleIds.forEach { cid ->
                                Row(modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedRegistrant.value = Pair(null, cid) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selectedRegistrant.value.first == null && selectedRegistrant.value.second == cid,
                                        onClick = { selectedRegistrant.value = Pair(null, cid) })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Pár $cid")
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
                            val tName = tObj?.get("name")?.jsonPrimitive?.contentOrNull ?: "Trenér #$idx"
                            Row(modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Text(tName, modifier = Modifier.weight(1f))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(onClick = {
                                        counts[idx] = (counts[idx] - 1).coerceAtLeast(0)
                                    }) { Text("-") }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${counts[idx]}", modifier = Modifier.padding(8.dp))
                                    Button(onClick = {
                                        counts[idx] = counts[idx] + 1
                                    }) { Text("+") }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = {
                            val registrant = selectedRegistrant.value
                            val lessons = trainers.mapIndexedNotNull { i, tEl ->
                                val cnt = counts[i]
                                if (cnt > 0) {
                                    val trainerId = (tEl as? JsonObject)?.get("id")?.jsonPrimitive?.intOrNull ?: i
                                    LessonInput(trainerId, cnt)
                                } else null
                            }
                            val regInput = RegistrationInput(registrant.first, registrant.second, lessons)
                            onRegister(listOf(regInput))
                        }) { Text("Potvrdit registraci") }
                    }
                }
            }
            is RegMode.Edit -> {
                Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
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
                            registrations.forEach { rEl ->
                                val r = rEl as? JsonObject
                                val rid = r?.get("id")?.jsonPrimitive?.contentOrNull
                                val label = r?.get("person").asJsonObjectOrNull()?.get("name")?.jsonPrimitive?.contentOrNull
                                    ?: r?.get("couple").asJsonObjectOrNull()?.get("id")?.jsonPrimitive?.contentOrNull ?: "#${rid ?: "?"}"
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
                        if (selectedId != null) {
                            Text("Upravit nároky na lekce:")
                            // ensure counts exist for this registration
                            val countsState = countsByReg[selectedId]!!
                            trainers.forEachIndexed { idx, tEl ->
                                val tObj = tEl as? JsonObject
                                val tName = tObj?.get("name")?.jsonPrimitive?.contentOrNull ?: "Trenér #$idx"
                                Row(modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Text(tName, modifier = Modifier.weight(1f))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Button(onClick = {
                                            countsState[idx] = (countsState[idx] - 1).coerceAtLeast(0)
                                        }) { Text("-") }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("${countsState[idx]}", modifier = Modifier.padding(8.dp))
                                        Button(onClick = {
                                            val maxForTrainer = (tObj?.get("lessonsRemaining")?.jsonPrimitive?.intOrNull
                                                ?: Int.MAX_VALUE)
                                            countsState[idx] = (countsState[idx] + 1).coerceAtMost(maxForTrainer)
                                        }) { Text("+") }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = {
                                // call onSetLessonDemand for each trainer using countsState
                                val regId = selectedId
                                countsState.forEachIndexed { i, cnt ->
                                    val trainerId = (trainers[i] as? JsonObject)?.get("id")?.jsonPrimitive?.intOrNull ?: i
                                    onSetLessonDemand(regId!!, trainerId, cnt)
                                }
                            }) { Text("Uložit změny") }
                        }
                    }
                }
            }
            is RegMode.Delete -> {
                Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Vyberte registraci k odstranění:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        val selectedReg = remember { mutableStateOf<String?>(null) }
                        registrations.forEach { rEl ->
                            val r = rEl as? JsonObject
                            val rid = r?.get("id")?.jsonPrimitive?.contentOrNull
                            val label = r?.get("person").asJsonObjectOrNull()?.get("name")?.jsonPrimitive?.contentOrNull
                                ?: r?.get("couple").asJsonObjectOrNull()?.get("id")?.jsonPrimitive?.contentOrNull ?: "#${rid ?: "?"}"
                            Button(onClick = { selectedReg.value = rid }) { Text(label ?: "registrace") }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = {
                            selectedReg.value?.let { onDelete(it) }
                        }) { Text("Smazat vybranou registraci") }
                    }
                }
            }
        }
    }
}
