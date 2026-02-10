package com.tkolymp.tkolympapp

// Biometric support removed — no FragmentActivity import
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class CohortDisplay(val name: String, val colorRgb: String?, val since: String?, val until: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onLogout: () -> Unit = {}, onBack: (() -> Unit)? = null) {
    var userJson by remember { mutableStateOf<String?>(null) }
    var personJson by remember { mutableStateOf<String?>(null) }
    var titleText by remember { mutableStateOf<String?>(null) }
    var bioText by remember { mutableStateOf<String?>(null) }
    var addrText by remember { mutableStateOf<String?>(null) }
    var emailText by remember { mutableStateOf<String?>(null) }
    var coupleIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var coupleNames by remember { mutableStateOf<List<String>>(emptyList()) } // cohort names (legacy)
    var activeCoupleNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var cohortItems by remember { mutableStateOf<List<CohortDisplay>>(emptyList()) }
        var personFields by remember { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
        var currentUserFields by remember { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
    var addressFields by remember { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val refreshTriggerState = remember { mutableStateOf(0) }

    LaunchedEffect(refreshTriggerState.value) {
        userJson = try { ServiceLocator.userService.getCachedCurrentUserJson() } catch (_: Throwable) { null }

        val cachedPerson = try { ServiceLocator.userService.getCachedPersonDetailsJson() } catch (_: Throwable) { null }
        val pid = try { ServiceLocator.userService.getCachedPersonId() } catch (_: Throwable) { null }
        if (pid != null && pid.isNotBlank()) {
            // refetch if there's no cached person or the cached JSON lacks required fields (activeCouplesList/cohortMembershipsList/email)
            val needsRefetch = cachedPerson.isNullOrBlank() || !(
                cachedPerson.contains("activeCouplesList") &&
                cachedPerson.contains("cohortMembershipsList") &&
                (cachedPerson.contains("email") || cachedPerson.contains("uEmail"))
            )
            if (needsRefetch) {
                try { ServiceLocator.userService.fetchAndStorePersonDetails(pid) } catch (_: Throwable) { }
            }
        }

        personJson = try { ServiceLocator.userService.getCachedPersonDetailsJson() } catch (_: Throwable) { null }
        coupleIds = try { ServiceLocator.userService.getCachedCoupleIds() } catch (_: Throwable) { emptyList() }

        // Try to extract human-friendly active couples and cohort names from personJson if possible
        val cohortNames = mutableListOf<String>()
        val activeNames = mutableListOf<String>()
        val cohortItemsLocal = mutableListOf<CohortDisplay>()
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
                        val display = listOfNotNull(manName, womanName).joinToString(" - ")
                        if (display.isNotBlank()) activeNames.add(display)
                    }
                }

                // cohortMembershipsList -> cohort { name, isVisible } + since/until
                val cohorts = p["cohortMembershipsList"]
                if (cohorts is kotlinx.serialization.json.JsonArray) {
                    fun fmtDate(s: String?): String? {
                        if (s.isNullOrBlank()) return null
                        val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                        try {
                            val ld = LocalDate.parse(s)
                            return ld.format(fmt)
                        } catch (_: Exception) {}
                        try {
                            val odt = OffsetDateTime.parse(s)
                            return odt.toLocalDate().format(fmt)
                        } catch (_: Exception) {}
                        val m = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(s)
                        if (m != null) return "${m.groupValues[3]}.${m.groupValues[2]}.${m.groupValues[1]}"
                        return s
                    }

                    cohorts.forEach { item ->
                        val obj = item as? kotlinx.serialization.json.JsonObject ?: return@forEach
                        val cohort = obj["cohort"]?.jsonObject
                        val cname = cohort?.get("name")?.jsonPrimitive?.contentOrNull
                        if (!cname.isNullOrBlank()) {
                            // show all cohorts regardless of visibility flag
                            val sinceRaw = obj["since"]?.jsonPrimitive?.contentOrNull
                            val untilRaw = obj["until"]?.jsonPrimitive?.contentOrNull
                            val since = fmtDate(sinceRaw)
                            val until = fmtDate(untilRaw)
                            val cColor = cohort?.get("colorRgb")?.jsonPrimitive?.contentOrNull
                            cohortNames.add(cname)
                            cohortItemsLocal.add(CohortDisplay(cname, cColor, since, until))
                        }
                    }
                }
            }
        } catch (_: Throwable) { }
        coupleNames = cohortNames
        activeCoupleNames = if (activeNames.isNotEmpty()) activeNames else coupleIds.map { "#${it}" }
        cohortItems = cohortItemsLocal

        if (!personJson.isNullOrBlank()) {
            try {
                val p = Json.parseToJsonElement(personJson!!).jsonObject
                val prefix = p["prefixTitle"]?.toString()?.replace("\"", "")
                val first = p["firstName"]?.toString()?.replace("\"", "")
                val last = p["lastName"]?.toString()?.replace("\"", "")
                val bio = p["bio"]?.toString()?.replace("\"", "")
                val email = p["email"]?.jsonPrimitive?.contentOrNull ?: p["uEmail"]?.jsonPrimitive?.contentOrNull
                    val addressObj = p["address"]?.jsonObject
                    // build a human-friendly single-line address (fallback) and a list of individual address fields
                    val addr = addressObj?.let { a -> listOfNotNull(a["street"]?.toString()?.replace("\"", ""), a["city"]?.toString()?.replace("\"", ""), a["postalCode"]?.toString()?.replace("\"", "")).joinToString(", ") }
                    val afields = mutableListOf<Pair<String,String>>()
                    addressObj?.let { a ->
                        listOf("street","city","postalCode","region","district","conscriptionNumber","orientationNumber").forEach { key ->
                            val value = a[key]?.jsonPrimitive?.contentOrNull ?: a[key]?.toString()?.replace("\"", "")
                            if (!value.isNullOrBlank()) afields.add(key to value)
                        }
                    }

                    titleText = listOfNotNull(prefix, first, last).joinToString(" ").takeIf { it.isNotBlank() }
                    bioText = bio?.takeIf { it.isNotBlank() }
                    addrText = addr?.takeIf { it.isNotBlank() }
                    emailText = email?.takeIf { it.isNotBlank() }
                    addressFields = afields
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
                // if we didn't get email from personJson, try to populate from current user JSON
                if (emailText.isNullOrBlank()) {
                    val found = cu.firstOrNull { it.first.equals("email", ignoreCase = true) || it.first.equals("uEmail", ignoreCase = true) }
                    emailText = found?.second?.takeIf { it.isNotBlank() }
                }
            } catch (_: Throwable) { }
    }

    val outerScroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Můj profil") },
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(outerScroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
                
                // Top header: show full name with titles (fallback to username)
                fun formatValue(key: String, value: String): String {
                    val v = value.trim()

                    // Booleans
                    when {
                        v.equals("true", ignoreCase = true) -> return "ano"
                        v.equals("false", ignoreCase = true) -> return "ne"
                    }

                    // Birth date formatting (try several ISO-like formats)
                    if (key.equals("birthDate", ignoreCase = true)) {
                        val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                        try {
                            val ld = LocalDate.parse(v)
                            return ld.format(fmt)
                        } catch (_: DateTimeParseException) {
                        }
                        try {
                            val odt = OffsetDateTime.parse(v)
                            return odt.toLocalDate().format(fmt)
                        } catch (_: DateTimeParseException) {
                        }
                        // Fallback simple regex YYYY-MM-DD
                        val m = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(v)
                        if (m != null) return "${m.groupValues[3]}.${m.groupValues[2]}.${m.groupValues[1]}"
                    }

                    // Gender values
                    val up = v.uppercase()
                    if (key.equals("gender", ignoreCase = true) || up in setOf("MAN", "WOMAN", "UNSPECIFIED")) {
                        return when (up) {
                            "MAN" -> "muž"
                            "WOMAN" -> "žena"
                            else -> "nevybráno"
                        }
                    }

                    return v
                }

                val displayName = titleText ?: currentUserFields.find { it.first == "username" }?.second
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(displayName ?: "Uživatel", style = MaterialTheme.typography.headlineSmall)
                }
                // show email under name if available
                emailText?.let {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
                // Merge personFields and currentUserFields but exclude keys shown elsewhere
                val mergedFields = remember(personFields, currentUserFields, addrText, titleText, bioText) {
                val map = linkedMapOf<String, String>()
                val excluded = setOf(
                    "prefixTitle", "firstName", "lastName", "bio", "address",
                    "activeCouplesList", "cohortMembershipsList"
                )

                personFields.forEach { (k, v) -> if (k !in excluded && v.isNotBlank()) map[k] = v }
                currentUserFields.forEach { (k, v) -> if (k !in excluded && !map.containsKey(k) && v.isNotBlank()) map[k] = v }

                map
                }

                // Labeling and categorization
                val labelMap = mapOf(
                    "email" to "Email",
                    "address" to "Adresa",
                    "isTrainer" to "Trenér",
                    "mobilePhone" to "Telefon",
                    "phone" to "Telefon",
                    "workPhone" to "Telefon (služ.)",
                    "birthDate" to "Datum narození",
                    "gender" to "Pohlaví",
                    "nationality" to "Státní příslušnost",
                    "username" to "Uživatelské jméno",
                    "id" to "ID",
                    "personalId" to "Rodné číslo",
                    "passportNumber" to "Číslo pasu",
                    "idNumber" to "Číslo dokladu",
                    "wdsfId" to "WDSF ID",
                    "cstsId" to "ČSTS IDT"
                )

                fun humanizeKey(k: String): String {
                    val spaced = k.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ').replace('-', ' ')
                    return spaced.split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                }

                val personalKeys = setOf("birthDate", "gender", "nationality", "maritalStatus", "placeOfBirth")
                val contactKeys = setOf("email", "mobilePhone", "phone", "workPhone")
                val externalKeys = setOf("personalId", "idNumber", "passportNumber", "externalId", "ico", "dic")

                val personalList = mutableListOf<Pair<String, String>>()
                val contactList = mutableListOf<Pair<String, String>>()
                val externalList = mutableListOf<Pair<String, String>>()
                val otherList = mutableListOf<Pair<String, String>>()

                mergedFields.forEach { (k, v) ->
                    when {
                        k in personalKeys -> personalList.add(k to v)
                        k in contactKeys -> contactList.add(k to v)
                        k in externalKeys -> externalList.add(k to v)
                        else -> otherList.add(k to v)
                    }
                }
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

        // Address card — show all available address subfields
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Adresa", style = MaterialTheme.typography.labelLarge)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                if (addressFields.isNotEmpty()) {
                    addressFields.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                            val label = when (k) {
                                "street" -> "Ulice"
                                "city" -> "Město"
                                "postalCode" -> "PSČ"
                                "region" -> "Kraj"
                                "district" -> "Okres"
                                "conscriptionNumber" -> "Číslo popisné"
                                "orientationNumber" -> "Číslo orientační"
                                else -> k
                            }
                            Text("$label:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                            Text(formatValue(k, v), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (!addrText.isNullOrBlank()) {
                    Text(addrText!!, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Adresa není dostupná.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Active couples card (prefer activeCouplesList data)
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Aktivní páry", style = MaterialTheme.typography.labelLarge)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                if (activeCoupleNames.isNotEmpty()) {
                    Column {
                        activeCoupleNames.forEach { name ->
                            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                } else {
                    Text("Žádné aktivní páry.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Groups (cohorts) card - styled like PersonPage
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Tréninkové skupiny", style = MaterialTheme.typography.labelLarge)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                if (cohortItems.isNotEmpty()) {
                    Column {
                        cohortItems.forEach { item ->
                            val color = try { parseColorOrDefault(item.colorRgb) } catch (_: Exception) { androidx.compose.ui.graphics.Color.Gray }
                            Card(modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    val since = item.since
                                    val until = item.until
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = item.name, style = MaterialTheme.typography.titleSmall)
                                            if (!since.isNullOrBlank()) Text("Od: ${since}", style = MaterialTheme.typography.labelSmall)
                                            if (!until.isNullOrBlank()) Text("Do: ${until}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Box(modifier = Modifier
                                            .width(28.dp)
                                            .fillMaxHeight(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(modifier = Modifier.size(12.dp).background(color, shape = androidx.compose.foundation.shape.CircleShape))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text("Žádné skupiny.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Category cards (visible, not hidden in details)
        if (personalList.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Osobní údaje", style = MaterialTheme.typography.labelLarge)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    personalList.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                            val label = labelMap[k] ?: humanizeKey(k)
                            Text("$label:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                            Text(formatValue(k, v), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (contactList.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Kontakty", style = MaterialTheme.typography.labelLarge)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    contactList.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                            val label = labelMap[k] ?: humanizeKey(k)
                            Text("$label:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                            Text(formatValue(k, v), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (externalList.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Externí identifikace", style = MaterialTheme.typography.labelLarge)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    externalList.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                            val label = labelMap[k] ?: humanizeKey(k)
                            Text("$label:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                            Text(formatValue(k, v), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (otherList.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Ostatní detaily", style = MaterialTheme.typography.labelLarge)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    otherList.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                            val label = labelMap[k] ?: humanizeKey(k)
                            Text("$label:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                            Text(formatValue(k, v), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // (Removed duplicate detail cards; fields are shown in categorized cards above)

        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var showChangePassDialog by remember { mutableStateOf(false) }
                var showEditPersonal by remember { mutableStateOf(false) }

                FilledTonalButton(
                    onClick = { showChangePassDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Změnit heslo")
                }

                if (showChangePassDialog) {
                    ChangePasswordDialog(onDismiss = { showChangePassDialog = false }, onSuccess = {
                        // clear storage and navigate to login
                        scope.launch {
                            try { ServiceLocator.tokenStorage.clear() } catch (_: Throwable) {}
                            try { ServiceLocator.userService.clear() } catch (_: Throwable) {}
                            showChangePassDialog = false
                            onLogout()
                        }
                    })
                }

                FilledTonalButton(
                    onClick = { showEditPersonal = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Změnit osobní data")
                }

                if (showEditPersonal) {
                    ChangePersonalDataDialog(
                        initialFirst = currentUserFields.find { it.first.equals("firstName", ignoreCase = true) }?.second ?: personFields.find { it.first.equals("firstName", ignoreCase = true) }?.second ?: "",
                        initialLast = currentUserFields.find { it.first.equals("lastName", ignoreCase = true) }?.second ?: personFields.find { it.first.equals("lastName", ignoreCase = true) }?.second ?: "",
                        initialBio = bioText ?: "",
                        initialEmail = emailText ?: "",
                        initialPrefix = personFields.find { it.first.equals("prefixTitle", ignoreCase = true) }?.second ?: currentUserFields.find { it.first.equals("prefixTitle", ignoreCase = true) }?.second ?: "",
                        initialSuffix = personFields.find { it.first.equals("suffixTitle", ignoreCase = true) }?.second ?: currentUserFields.find { it.first.equals("suffixTitle", ignoreCase = true) }?.second ?: "",
                        initialCstsId = personFields.find { it.first.equals("cstsId", ignoreCase = true) }?.second ?: "",
                        initialWdsfId = personFields.find { it.first.equals("wdsfId", ignoreCase = true) }?.second ?: "",
                        initialNationalIdNumber = personFields.find { it.first.equals("nationalIdNumber", ignoreCase = true) }?.second ?: "",
                        initialNationality = personFields.find { it.first.equals("nationality", ignoreCase = true) }?.second ?: "",
                        initialStreet = addressFields.firstOrNull { it.first == "street" }?.second ?: "",
                        initialCity = addressFields.firstOrNull { it.first == "city" }?.second ?: "",
                        initialPostal = addressFields.firstOrNull { it.first == "postalCode" }?.second ?: "",
                        initialRegion = addressFields.firstOrNull { it.first == "region" }?.second ?: "",
                        initialDistrict = addressFields.firstOrNull { it.first == "district" }?.second ?: "",
                        initialConscription = addressFields.firstOrNull { it.first == "conscriptionNumber" }?.second ?: "",
                        initialOrientation = addressFields.firstOrNull { it.first == "orientationNumber" }?.second ?: "",
                        initialPhone = currentUserFields.firstOrNull { it.first.equals("phone", ignoreCase = true) || it.first.equals("mobilePhone", ignoreCase = true) }?.second ?: personFields.firstOrNull { it.first.equals("phone", ignoreCase = true) || it.first.equals("mobilePhone", ignoreCase = true) }?.second ?: "",
                        initialMobile = currentUserFields.firstOrNull { it.first.equals("mobilePhone", ignoreCase = true) || it.first.equals("phone", ignoreCase = true) }?.second ?: personFields.firstOrNull { it.first.equals("mobilePhone", ignoreCase = true) || it.first.equals("phone", ignoreCase = true) }?.second ?: "",
                        initialBirthDate = personFields.find { it.first.equals("birthDate", ignoreCase = true) }?.second ?: currentUserFields.find { it.first.equals("birthDate", ignoreCase = true) }?.second ?: "",
                        initialGender = personFields.find { it.first.equals("gender", ignoreCase = true) }?.second ?: currentUserFields.find { it.first.equals("gender", ignoreCase = true) }?.second ?: "",
                        onDismiss = { showEditPersonal = false },
                        onSave = { first, last, bio, email, prefix, suffix, csts, wdsf, nid, nationality, street, city, postal, region, district, conscription, orientation, phone, mobile, birth, gender ->
                            // update local UI state so user sees changes immediately
                            titleText = listOfNotNull(first.takeIf { it.isNotBlank() }, last.takeIf { it.isNotBlank() }).joinToString(" ").takeIf { it.isNotBlank() }
                            bioText = bio.takeIf { it.isNotBlank() }
                            emailText = email.takeIf { it.isNotBlank() }
                            val singleLineAddr = listOfNotNull(street.takeIf { it.isNotBlank() }, city.takeIf { it.isNotBlank() }, postal.takeIf { it.isNotBlank() }).joinToString(", ")
                            addrText = singleLineAddr.takeIf { it.isNotBlank() }
                            val af = mutableListOf<Pair<String,String>>()
                            if (street.isNotBlank()) af.add("street" to street)
                            if (city.isNotBlank()) af.add("city" to city)
                            if (postal.isNotBlank()) af.add("postalCode" to postal)
                            if (region.isNotBlank()) af.add("region" to region)
                            if (district.isNotBlank()) af.add("district" to district)
                            if (conscription.isNotBlank()) af.add("conscriptionNumber" to conscription)
                            if (orientation.isNotBlank()) af.add("orientationNumber" to orientation)
                            addressFields = af
                            // update contact fields locally
                            val newCurrent = currentUserFields.toMutableList()
                            fun upsert(list: MutableList<Pair<String,String>>, key: String, value: String) {
                                if (value.isBlank()) return
                                val idx = list.indexOfFirst { it.first.equals(key, ignoreCase = true) }
                                if (idx >= 0) list[idx] = key to value else list.add(key to value)
                            }
                            upsert(newCurrent, "phone", phone)
                            upsert(newCurrent, "mobilePhone", mobile)
                            upsert(newCurrent, "birthDate", birth)
                            upsert(newCurrent, "gender", gender)
                            upsert(newCurrent, "prefixTitle", prefix)
                            upsert(newCurrent, "suffixTitle", suffix)
                            currentUserFields = newCurrent
                            // persistence moved into dialog; parent only updates UI optimistically
                            showEditPersonal = false
                            // trigger a refresh of person data
                            refreshTriggerState.value = refreshTriggerState.value + 1
                        }
                    )
                }
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
}


@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Změnit heslo") },
        text = {
            Column(modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                Text("Heslo musí mít alespoň 8 znaků.")
                TextField(value = newPass, onValueChange = { newPass = it }, label = { Text("Nové heslo") })
                TextField(value = confirm, onValueChange = { confirm = it }, label = { Text("Potvrzení hesla") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (loading) androidx.compose.material3.CircularProgressIndicator()
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // validation
                error = null
                if (newPass.length < 8) { error = "Heslo musí mít alespoň 8 znaků"; return@TextButton }
                if (newPass != confirm) { error = "Hesla se neshodují"; return@TextButton }

                scope.launch {
                    loading = true
                    try {
                        try {
                            val result = ServiceLocator.userService.changePassword(newPass)
                            if (result) {
                                onSuccess()
                            } else {
                                // try to get last API error from the service
                                val apiErr = try { ServiceLocator.userService.getLastApiError() } catch (_: Throwable) { null }
                                error = if (!apiErr.isNullOrBlank()) "Změna hesla selhala: $apiErr" else "Změna hesla selhala"
                            }
                        } catch (ex: Throwable) {
                            // Show exception message when available
                            val causeMsg = generateSequence(ex.cause) { it.cause }
                                .mapNotNull { it.message }
                                .firstOrNull()
                            val msg = ex.message ?: ex.toString()
                            error = "Změna hesla selhala: ${causeMsg ?: msg}"
                        }
                    } finally {
                        loading = false
                    }
                }
            }) { Text("Potvrdit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } }
    )
}


@Composable
private fun ChangePersonalDataDialog(
    initialFirst: String,
    initialLast: String,
    initialBio: String,
    initialEmail: String,
    initialPrefix: String,
    initialSuffix: String,
    initialCstsId: String,
    initialWdsfId: String,
    initialNationalIdNumber: String,
    initialNationality: String,
    initialStreet: String,
    initialCity: String,
    initialPostal: String,
    initialRegion: String,
    initialDistrict: String,
    initialConscription: String,
    initialOrientation: String,
    initialPhone: String,
    initialMobile: String,
    initialBirthDate: String,
    initialGender: String,
    onDismiss: () -> Unit,
    onSave: (first: String, last: String, bio: String, email: String, prefix: String, suffix: String, csts: String, wdsf: String, nid: String, nationality: String, street: String, city: String, postal: String, region: String, district: String, conscription: String, orientation: String, phone: String, mobile: String, birthDate: String, gender: String) -> Unit
) {
    var first by remember(initialFirst) { mutableStateOf(initialFirst) }
    var last by remember(initialLast) { mutableStateOf(initialLast) }
    var bio by remember(initialBio) { mutableStateOf(initialBio) }
    var email by remember(initialEmail) { mutableStateOf(initialEmail) }
    var prefix by remember(initialPrefix) { mutableStateOf(initialPrefix) }
    var suffix by remember(initialSuffix) { mutableStateOf(initialSuffix) }
    var csts by remember(initialCstsId) { mutableStateOf(initialCstsId) }
    var wdsf by remember(initialWdsfId) { mutableStateOf(initialWdsfId) }
    var nid by remember(initialNationalIdNumber) { mutableStateOf(initialNationalIdNumber) }
    var nationality by remember(initialNationality) { mutableStateOf(initialNationality) }
    var street by remember(initialStreet) { mutableStateOf(initialStreet) }
    var city by remember(initialCity) { mutableStateOf(initialCity) }
    var postal by remember(initialPostal) { mutableStateOf(initialPostal) }
    var region by remember(initialRegion) { mutableStateOf(initialRegion) }
    var district by remember(initialDistrict) { mutableStateOf(initialDistrict) }
    var conscription by remember(initialConscription) { mutableStateOf(initialConscription) }
    var orientation by remember(initialOrientation) { mutableStateOf(initialOrientation) }
    var phone by remember(initialPhone) { mutableStateOf(initialPhone) }
    var mobile by remember(initialMobile) { mutableStateOf(initialMobile) }
    var birth by remember(initialBirthDate) { mutableStateOf(initialBirthDate) }
    var gender by remember(initialGender) { mutableStateOf(initialGender) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Upravit osobní údaje") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                TextField(value = first, onValueChange = { first = it }, label = { Text("Jméno") }, singleLine = true)
                TextField(value = last, onValueChange = { last = it }, label = { Text("Příjmení") }, singleLine = true)
                TextField(value = prefix, onValueChange = { prefix = it }, label = { Text("Titul před") }, singleLine = true)
                TextField(value = suffix, onValueChange = { suffix = it }, label = { Text("Titul za") }, singleLine = true)
                TextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") })
                TextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true)
                TextField(value = csts, onValueChange = { csts = it }, label = { Text("ČSTS ID") }, singleLine = true)
                TextField(value = wdsf, onValueChange = { wdsf = it }, label = { Text("WDSF ID") }, singleLine = true)
                TextField(value = nid, onValueChange = { nid = it }, label = { Text("Rodné číslo / národní ID") }, singleLine = true)
                TextField(value = nationality, onValueChange = { nationality = it }, label = { Text("Národnost") }, singleLine = true)
                Divider(modifier = Modifier.padding(vertical = 6.dp))
                Text("Kontakty", style = MaterialTheme.typography.labelSmall)
                TextField(value = phone, onValueChange = { phone = it }, label = { Text("Telefon") }, singleLine = true)
                TextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Mobil") }, singleLine = true)
                TextField(value = birth, onValueChange = { birth = it }, label = { Text("Datum narození") }, singleLine = true)
                TextField(value = gender, onValueChange = { gender = it }, label = { Text("Pohlaví") }, singleLine = true)
                Divider(modifier = Modifier.padding(vertical = 6.dp))
                Text("Adresa", style = MaterialTheme.typography.labelSmall)
                TextField(value = street, onValueChange = { street = it }, label = { Text("Ulice") }, singleLine = true)
                TextField(value = city, onValueChange = { city = it }, label = { Text("Město") }, singleLine = true)
                TextField(value = postal, onValueChange = { postal = it }, label = { Text("PSČ") }, singleLine = true)
                TextField(value = region, onValueChange = { region = it }, label = { Text("Kraj / region") }, singleLine = true)
                TextField(value = district, onValueChange = { district = it }, label = { Text("Okres / district") }, singleLine = true)
                TextField(value = conscription, onValueChange = { conscription = it }, label = { Text("Číslo popisné (conscription)") }, singleLine = true)
                TextField(value = orientation, onValueChange = { orientation = it }, label = { Text("Číslo orientační (orientation)") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (saving) androidx.compose.material3.CircularProgressIndicator()
            }
        },
        confirmButton = {
                TextButton(onClick = {
                error = null
                if (email.isNotBlank() && !email.contains("@")) { error = "Neplatný email"; return@TextButton }
                scope.launch {
                    saving = true
                    try {
                        // perform network save here and only close dialog on success
                        val pid = try { ServiceLocator.userService.getCachedPersonId() } catch (_: Throwable) { null }
                        if (pid.isNullOrBlank()) {
                            error = "Nelze určit ID uživatele"
                        } else {
                            val req = com.tkolymp.shared.user.PersonUpdateRequest(
                                bio = bio,
                                cstsId = csts,
                                email = email,
                                firstName = first,
                                lastName = last,
                                nationalIdNumber = nid,
                                nationality = nationality,
                                phone = phone,
                                wdsfId = wdsf,
                                prefixTitle = prefix,
                                suffixTitle = suffix,
                                gender = gender,
                                birthDateSet = true,
                                birthDate = birth,
                                address = com.tkolymp.shared.user.AddressUpdate(
                                    street = street,
                                    city = city,
                                    postalCode = postal,
                                    region = region,
                                    district = district,
                                    conscriptionNumber = conscription,
                                    orientationNumber = orientation
                                )
                            )
                            try {
                                println("[ProfileScreen] Sending PersonUpdateRequest: $req")
                            } catch (_: Throwable) {}
                            val ok = try { ServiceLocator.userService.updatePerson(pid, req) } catch (ex: Throwable) { false }
                            if (ok) {
                                // update parent UI and refetch details
                                onSave(
                                    first.trim(), last.trim(), bio.trim(), email.trim(), prefix.trim(), suffix.trim(), csts.trim(), wdsf.trim(), nid.trim(), nationality.trim(),
                                    street.trim(), city.trim(), postal.trim(), region.trim(), district.trim(), conscription.trim(), orientation.trim(),
                                    phone.trim(), mobile.trim(), birth.trim(), gender.trim()
                                )
                                    try { ServiceLocator.userService.fetchAndStorePersonDetails(pid) } catch (_: Throwable) { }
                                try { ServiceLocator.authService.initialize() } catch (_: Throwable) { }
                                try { android.widget.Toast.makeText(ctx, "Údaje uloženy", android.widget.Toast.LENGTH_SHORT).show() } catch (_: Throwable) { }
                                    onDismiss()
                            } else {
                                val apiErr = try { ServiceLocator.userService.getLastApiError() } catch (_: Throwable) { null }
                                error = "Uložení selhalo: ${apiErr ?: "neznámá chyba"}"
                            }
                        }
                    } catch (ex: Throwable) {
                        error = ex.message ?: "Uložení selhalo"
                    } finally {
                        saving = false
                    }
                }
            }) { Text("Uložit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } }
    )
}
