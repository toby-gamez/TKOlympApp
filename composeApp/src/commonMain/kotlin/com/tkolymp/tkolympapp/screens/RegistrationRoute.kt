package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.registration.LessonInput
import com.tkolymp.shared.registration.RegMode
import com.tkolymp.shared.registration.RegistrationInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Route-level composable that loads event data and user info,
 * then renders [RegistrationScreen] with proper callbacks.
 * Extracted from the inline NavHost composable in AppContent.kt
 * to reduce complexity and improve maintainability.
 */
@Composable
fun RegistrationRoute(
    eventId: Long?,
    modeStr: String?,
    onClose: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var evJson by remember { mutableStateOf<JsonObject?>(null) }

    LaunchedEffect(eventId) {
        loading = true
        try {
            if (eventId == null) {
                error = AppStrings.current.errorMessages.errorLoadingEvent
            } else {
                val svc = ServiceLocator.eventService
                evJson = withContext(Dispatchers.Default) { svc.fetchEventById(eventId) }
            }
        } catch (ex: Exception) {
            error = ex.message
        } finally {
            loading = false
        }
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(error ?: AppStrings.current.commonActions.error)
        }
    } else {
        evJson?.let { ev ->
            val trainers = (ev["eventTrainersList"] as? JsonArray) ?: JsonArray(emptyList())
            val registrations = (ev["eventRegistrationsList"] as? JsonArray) ?: JsonArray(emptyList())

            val myPersonIdState = remember { mutableStateOf<String?>(null) }
            val myCoupleIdsState = remember { mutableStateOf<List<String>>(emptyList()) }

            LaunchedEffect(Unit) {
                try {
                    val pid = withContext(Dispatchers.Default) { ServiceLocator.userService.getCachedPersonId() }
                    myPersonIdState.value = pid
                } catch (_: Exception) {}
                try {
                    val cids = withContext(Dispatchers.Default) { ServiceLocator.userService.getCachedCoupleIds() }
                    myCoupleIdsState.value = cids
                } catch (_: Exception) {}
            }

            val mode = when (modeStr) {
                "register" -> RegMode.Register
                "edit" -> RegMode.Edit
                "delete" -> RegMode.Delete
                else -> RegMode.Register
            }

            val regResultMessage = remember { mutableStateOf<String?>(null) }

            Column {
                RegistrationScreen(
                    eventId = eventId ?: 0L,
                    mode = mode,
                    trainers = trainers,
                    registrations = registrations,
                    myPersonId = myPersonIdState.value,
                    myCoupleIds = myCoupleIdsState.value,
                    enableNotes = (ev["enableNotes"] as? JsonPrimitive)?.booleanOrNull ?: false,
                    eventType = (ev["type"] as? JsonPrimitive)?.contentOrNull,
                    onClose = onClose,
                    onRegister = { regs ->
                        val regsJson = regs.map { r ->
                            buildJsonObject {
                                put("eventId", JsonPrimitive(eventId))
                                if (r.personId != null) put("personId", JsonPrimitive(r.personId))
                                if (r.coupleId != null) put("coupleId", JsonPrimitive(r.coupleId))
                                put("lessons", JsonArray(r.lessons.map { l -> buildJsonObject { put("trainerId", JsonPrimitive(l.trainerId)); put("lessonCount", JsonPrimitive(l.lessonCount)) } }))
                                if (r.note != null) put("note", JsonPrimitive(r.note))
                            }
                        }
                        val resp = withContext(Dispatchers.Default) {
                            ServiceLocator.eventService.registerToEventMany(JsonArray(regsJson))
                        }
                        val jsonObj = resp?.jsonObject ?: throw Exception("Network error")
                        val errors = jsonObj["errors"]
                        if (errors != null) throw Exception("Server errors: $errors")
                        val data = jsonObj["data"]?.jsonObject
                        data?.get("registerToEventMany")?.jsonObject?.get("eventRegistrations")
                            ?: throw Exception("Unexpected response: $resp")
                    },
                    onSetLessonDemand = { registrationId, trainerId, lessonCount ->
                        coroutineScope.launch {
                            try {
                                val success = withContext(Dispatchers.Default) {
                                    ServiceLocator.eventService.setLessonDemand(registrationId, trainerId, lessonCount)
                                }
                                regResultMessage.value = if (success) {
                                    AppStrings.current.dialogs.dataSaved
                                } else {
                                    AppStrings.current.dialogs.saveFailed
                                }
                            } catch (_: Exception) {}
                        }
                    },
                    onSetNote = { registrationId, note ->
                        coroutineScope.launch {
                            try {
                                withContext(Dispatchers.Default) {
                                    ServiceLocator.eventService.setRegistrationNote(registrationId, note)
                                }
                            } catch (_: Exception) {}
                        }
                    },
                    onDelete = { registrationId ->
                        val resp = withContext(Dispatchers.Default) {
                            ServiceLocator.eventService.deleteEventRegistration(registrationId)
                        }
                        val jsonObj = resp?.jsonObject ?: throw Exception("Network error")
                        val errors = jsonObj["errors"]
                        if (errors != null) throw Exception("Server errors: $errors")
                        val data = jsonObj["data"]?.jsonObject
                        data?.get("cancelRegistration")?.jsonObject?.get("clientMutationId")
                            ?: throw Exception("Unexpected response: $resp")
                    }
                )

                regResultMessage.value?.let { msg ->
                    Text(msg, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
