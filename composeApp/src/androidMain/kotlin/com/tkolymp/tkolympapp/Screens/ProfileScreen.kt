package com.tkolymp.tkolympapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ProfileScreen(onLogout: () -> Unit = {}) {
    var userJson by remember { mutableStateOf<String?>(null) }
    var personJson by remember { mutableStateOf<String?>(null) }
    var titleText by remember { mutableStateOf<String?>(null) }
    var bioText by remember { mutableStateOf<String?>(null) }
    var addrText by remember { mutableStateOf<String?>(null) }
    var coupleIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var coupleNames by remember { mutableStateOf<List<String>>(emptyList()) }
        var personFields by remember { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
        var currentUserFields by remember { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        userJson = try { ServiceLocator.userService.getCachedCurrentUserJson() } catch (_: Throwable) { null }

        val cachedPerson = try { ServiceLocator.userService.getCachedPersonDetailsJson() } catch (_: Throwable) { null }
        if (cachedPerson.isNullOrBlank()) {
            val pid = try { ServiceLocator.userService.getCachedPersonId() } catch (_: Throwable) { null }
            if (!pid.isNullOrBlank()) {
                try {
                    ServiceLocator.userService.fetchAndStorePersonDetails(pid)
                } catch (_: Throwable) { }
            }
        }

        personJson = try { ServiceLocator.userService.getCachedPersonDetailsJson() } catch (_: Throwable) { null }
        coupleIds = try { ServiceLocator.userService.getCachedCoupleIds() } catch (_: Throwable) { emptyList() }

        // Try to extract human-friendly group names from personJson if possible
        val names = mutableListOf<String>()
        try {
            if (!personJson.isNullOrBlank()) {
                val p = Json.parseToJsonElement(personJson!!).jsonObject
                // activeCouplesList -> man/woman names
                val active = p["activeCouplesList"]
                if (active is kotlinx.serialization.json.JsonArray) {
                    active.forEach { item ->
                        val obj = item as? kotlinx.serialization.json.JsonObject ?: return@forEach
                        val man = obj["man"]?.jsonObject
                        val woman = obj["woman"]?.jsonObject
                        val manName = man?.let { m -> listOfNotNull(m["firstName"]?.toString()?.replace("\"", ""), m["lastName"]?.toString()?.replace("\"", "")).joinToString(" ") }
                        val womanName = woman?.let { w -> listOfNotNull(w["firstName"]?.toString()?.replace("\"", ""), w["lastName"]?.toString()?.replace("\"", "")).joinToString(" ") }
                        val display = listOfNotNull(manName, womanName).joinToString(" / ")
                        if (display.isNotBlank()) names.add(display)
                    }
                }

                // cohortMembershipsList -> cohort { name }
                val cohorts = p["cohortMembershipsList"]
                if (cohorts is kotlinx.serialization.json.JsonArray) {
                    cohorts.forEach { item ->
                        val obj = item as? kotlinx.serialization.json.JsonObject ?: return@forEach
                        val cohort = obj["cohort"]?.jsonObject
                        val cname = cohort?.get("name")?.jsonPrimitive?.contentOrNull
                        if (!cname.isNullOrBlank()) names.add(cname)
                    }
                }
            }
        } catch (_: Throwable) { }
        coupleNames = if (names.isNotEmpty()) names else coupleIds.map { "#${it}" }

        if (!personJson.isNullOrBlank()) {
            try {
                val p = Json.parseToJsonElement(personJson!!).jsonObject
                val prefix = p["prefixTitle"]?.toString()?.replace("\"", "")
                val first = p["firstName"]?.toString()?.replace("\"", "")
                val last = p["lastName"]?.toString()?.replace("\"", "")
                val bio = p["bio"]?.toString()?.replace("\"", "")
                val addressObj = p["address"]?.jsonObject
                val addr = addressObj?.let { a -> listOfNotNull(a["street"]?.toString()?.replace("\"", ""), a["city"]?.toString()?.replace("\"", ""), a["postalCode"]?.toString()?.replace("\"", "")).joinToString(", ") }

                titleText = listOfNotNull(prefix, first, last).joinToString(" ").takeIf { it.isNotBlank() }
                bioText = bio?.takeIf { it.isNotBlank() }
                addrText = addr?.takeIf { it.isNotBlank() }
            } catch (_: Throwable) { }
        }
            // Build flattened fields list for display
            try {
                val fields = mutableListOf<Pair<String,String>>()
                if (!personJson.isNullOrBlank()) {
                    val p = Json.parseToJsonElement(personJson!!)
                    fun eltToStr(e: kotlinx.serialization.json.JsonElement): String {
                        return when (e) {
                            is kotlinx.serialization.json.JsonPrimitive -> e.contentOrNull ?: e.toString()
                            is kotlinx.serialization.json.JsonObject -> e.map { (k,v) -> "$k: ${eltToStr(v)}" }.joinToString(", ")
                            is kotlinx.serialization.json.JsonArray -> e.map { eltToStr(it) }.joinToString("; ")
                            else -> e.toString()
                        }
                    }

                    val obj = p.jsonObject
                    obj.entries.forEach { (k,v) ->
                        fields.add(k to eltToStr(v))
                    }
                }
                personFields = fields
            } catch (_: Throwable) { }

            try {
                val cu = mutableListOf<Pair<String,String>>()
                if (!userJson.isNullOrBlank()) {
                    val u = Json.parseToJsonElement(userJson!!)
                    val obj = u.jsonObject
                    obj.entries.forEach { (k,v) -> cu.add(k to (v.jsonPrimitive.contentOrNull ?: v.toString())) }
                }
                currentUserFields = cu
            } catch (_: Throwable) { }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text("Můj profil", style = MaterialTheme.typography.headlineMedium)

        // Title
        if (titleText != null) Text(titleText!!, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))

        Spacer(modifier = Modifier.height(12.dp))

        // Bio card
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("O mně", style = MaterialTheme.typography.labelLarge)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                if (!bioText.isNullOrBlank()) {
                    Text(bioText!!, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Bio není dostupné.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Address card
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Adresa", style = MaterialTheme.typography.labelLarge)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                if (!addrText.isNullOrBlank()) {
                    Text(addrText!!, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Adresa není dostupná.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Groups card
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Skupiny", style = MaterialTheme.typography.labelLarge)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                if (coupleNames.isNotEmpty()) {
                    Column {
                        coupleNames.forEach { name ->
                            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                } else {
                    Text("Žádné skupiny.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // All fields card (person)
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            val scroll = rememberScrollState()
            Column(modifier = Modifier.padding(12.dp).verticalScroll(scroll)) {
                Text("Detaily (person)", style = MaterialTheme.typography.labelLarge)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                if (personFields.isNotEmpty()) {
                    personFields.forEach { (k,v) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                            Text("$k:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                            Text(v, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    Text("Žádné detaily.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // All fields card (current user)
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            val scroll2 = rememberScrollState()
            Column(modifier = Modifier.padding(12.dp).verticalScroll(scroll2)) {
                Text("Detaily (currentUser)", style = MaterialTheme.typography.labelLarge)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                if (currentUserFields.isNotEmpty()) {
                    currentUserFields.forEach { (k,v) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                            Text("$k:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                            Text(v, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    Text("Žádné detaily.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Button(onClick = { /* TODO: change password flow */ }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text("Změnit heslo")
        }

        Button(onClick = { /* TODO: change personal data flow */ }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Změnit osobní data")
        }

        Button(onClick = {
            scope.launch {
                try { ServiceLocator.tokenStorage.clear() } catch (_: Throwable) {}
                try { ServiceLocator.userService.clear() } catch (_: Throwable) {}
                onLogout()
            }
        }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Odhlásit")
        }
    }
}
