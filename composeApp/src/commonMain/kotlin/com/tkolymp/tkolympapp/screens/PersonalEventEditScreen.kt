package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.personalevents.PersonalEvent
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalEventEditScreen(eventId: String? = null, onSaved: () -> Unit = {}, bottomPadding: Dp = 0.dp) {
    val service = ServiceLocator.personalEventService
    val title = remember { mutableStateOf("") }
    val startIso = remember { mutableStateOf(kotlin.time.Clock.System.now().toString()) }
    val endIso = remember { mutableStateOf(kotlin.time.Clock.System.now().plus(1.hours).toString()) }

    LaunchedEffect(eventId) {
        if (!eventId.isNullOrBlank()) {
            val ev = service.getAll().find { it.id == eventId }
            if (ev != null) {
                title.value = ev.title
                startIso.value = ev.startIso
                endIso.value = ev.endIso
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(if (eventId.isNullOrBlank()) AppStrings.current.personalEvents.newTraining else AppStrings.current.personalEvents.editTraining) }) }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(12.dp)) {
            OutlinedTextField(value = title.value, onValueChange = { title.value = it }, label = { Text(AppStrings.current.personalEvents.trainingTitle) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = startIso.value, onValueChange = { startIso.value = it }, label = { Text(AppStrings.current.personalEvents.startTime) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(value = endIso.value, onValueChange = { endIso.value = it }, label = { Text(AppStrings.current.personalEvents.endTime) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

            Button(onClick = {
                val id = eventId ?: (kotlin.time.Clock.System.now().toEpochMilliseconds().toString() + "_${(0..Int.MAX_VALUE).random()}")
                val ev = PersonalEvent(id = id, title = title.value, startIso = startIso.value, endIso = endIso.value)
                // save
                try { kotlin.runCatching { kotlinx.coroutines.runBlocking { service.save(ev) } } } catch (_: Exception) {}
                onSaved()
            }, modifier = Modifier.padding(top = 12.dp)) {
                Text(AppStrings.current.personalEvents.saveTraining)
            }
        }
    }
}
