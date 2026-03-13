package com.tkolymp.tkolympapp.screens
import com.tkolymp.tkolympapp.SwipeToReload

// Biometric support removed — no FragmentActivity import
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.tkolymp.shared.language.NationalityHelper
import com.tkolymp.shared.people.PersonDetails
import com.tkolymp.shared.user.CurrentUser
import com.tkolymp.shared.viewmodels.ProfileViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class CohortDisplay(val name: String, val colorRgb: String?, val since: String?, val until: String?)

private fun formatProfileValue(key: String, value: String): String {
    val v = value.trim()
    when {
        v.equals("true", ignoreCase = true) -> return AppStrings.current.yes
        v.equals("false", ignoreCase = true) -> return AppStrings.current.no
    }
    if (key.equals("birthDate", ignoreCase = true)) {
        val formatted = fmtProfileDate(v)
        if (formatted != null && formatted != v) return formatted
    }
    if (key.equals("gender", ignoreCase = true)) {
        return when (v.uppercase()) {
            "MAN" -> AppStrings.current.genderMale
            "WOMAN" -> AppStrings.current.genderFemale
            else -> AppStrings.current.genderUnspecified
        }
    }
    if (key.equals("nationality", ignoreCase = true)) {
        val name = NationalityHelper.getNationalityOptions(AppStrings.currentLanguage).find { it.first == v }?.second
        if (name != null) return name
    }
    return v
}

private fun fmtProfileDate(s: String?): String? {
    if (s.isNullOrBlank()) return null
    try {
        val ld = LocalDate.parse(s)
        return "${ld.dayOfMonth.toString().padStart(2,'0')}.${ld.monthNumber.toString().padStart(2,'0')}.${ld.year}"
    } catch (_: Exception) {}
    try {
        val inst = Instant.parse(s)
        val ld = inst.toLocalDateTime(TimeZone.UTC).date
        return "${ld.dayOfMonth.toString().padStart(2,'0')}.${ld.monthNumber.toString().padStart(2,'0')}.${ld.year}"
    } catch (_: Exception) {}
    val m = Regex("""(\d{4})-(\d{2})-(\d{2})""").find(s)
    if (m != null) return "${m.groupValues[3]}.${m.groupValues[2]}.${m.groupValues[1]}"
    return s
}

private fun buildPersonFieldList(person: PersonDetails?): List<Pair<String, String>> {
    person ?: return emptyList()
    return buildList {
        person.birthDate?.let { add("birthDate" to it) }
        person.gender?.let { add("gender" to it) }
        person.nationality?.let { add("nationality" to it) }
        person.nationalIdNumber?.let { add("nationalIdNumber" to it) }
        person.phone?.let { add("phone" to it) }
        person.wdsfId?.let { add("wdsfId" to it) }
        person.cstsId?.let { add("cstsId" to it) }
        person.isTrainer?.let { add("isTrainer" to it.toString()) }
    }
}

private fun buildCurrentUserFieldList(currentUser: CurrentUser?): List<Pair<String, String>> {
    currentUser ?: return emptyList()
    return buildList {
        currentUser.uLogin?.let { add("username" to it) }
        currentUser.uEmail?.let { add("email" to it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onLogout: () -> Unit = {}, onBack: (() -> Unit)? = null) {
    var titleText by remember { mutableStateOf<String?>(null) }
    var bioText by remember { mutableStateOf<String?>(null) }
    var addrText by remember { mutableStateOf<String?>(null) }
    var emailText by remember { mutableStateOf<String?>(null) }
    var coupleIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeCoupleNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var cohortItems by remember { mutableStateOf<List<CohortDisplay>>(emptyList()) }
        var personFields by remember { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
        var currentUserFields by remember { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
    var addressFields by remember { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val refreshTriggerState = remember { mutableStateOf(0) }

    // Use ProfileViewModel to load cached user/person JSON and couple IDs
    val profileViewModel = remember { ProfileViewModel() }
    val profileState by profileViewModel.state.collectAsState()
    LaunchedEffect(refreshTriggerState.value) { profileViewModel.load() }
    LaunchedEffect(profileState) {
        val person = profileState.person
        val currentUser = profileState.currentUser
        coupleIds = profileState.coupleIds

        activeCoupleNames = person?.activeCouplesList?.mapNotNull { couple ->
            val man = listOfNotNull(couple.man?.firstName, couple.man?.lastName).joinToString(" ").takeIf { it.isNotBlank() }
            val woman = listOfNotNull(couple.woman?.firstName, couple.woman?.lastName).joinToString(" ").takeIf { it.isNotBlank() }
            listOfNotNull(man, woman).joinToString(" - ").takeIf { it.isNotBlank() }
        }?.takeIf { it.isNotEmpty() } ?: coupleIds.map { "#$it" }

        cohortItems = person?.cohortMembershipsList?.mapNotNull { m ->
            val cohort = m.cohort ?: return@mapNotNull null
            val name = cohort.name ?: return@mapNotNull null
            CohortDisplay(name, cohort.colorRgb, fmtProfileDate(m.since), fmtProfileDate(m.until))
        } ?: emptyList()

        titleText = listOfNotNull(person?.prefixTitle, person?.firstName, person?.lastName).joinToString(" ").takeIf { it.isNotBlank() }
        bioText = person?.bio?.takeIf { it.isNotBlank() }
        emailText = person?.email?.takeIf { it.isNotBlank() } ?: currentUser?.uEmail?.takeIf { it.isNotBlank() }
        addressFields = person?.address?.let { a ->
            buildList {
                a.street?.let { add("street" to it) }
                a.city?.let { add("city" to it) }
                a.postalCode?.let { add("postalCode" to it) }
                a.region?.let { add("region" to it) }
                a.district?.let { add("district" to it) }
                a.conscriptionNumber?.let { add("conscriptionNumber" to it) }
                a.orientationNumber?.let { add("orientationNumber" to it) }
            }
        } ?: emptyList()
        addrText = person?.address?.let { a ->
            listOfNotNull(a.street, a.city, a.postalCode).joinToString(", ").takeIf { it.isNotBlank() }
        }
        personFields = buildPersonFieldList(person)
        currentUserFields = buildCurrentUserFieldList(currentUser)
    }

    val outerScroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.current.myProfile) },
                navigationIcon = {
                    onBack?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.back)
                        }
                    }
                }
            )
        }
    ) { padding ->
    SwipeToReload(
        isRefreshing = profileState.isLoading,
        onRefresh = { scope.launch { profileViewModel.load() } },
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(outerScroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {

                val displayName = titleText ?: profileState.currentUser?.uLogin
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
                    "email" to AppStrings.current.email,
                    "address" to AppStrings.current.address,
                    "isTrainer" to AppStrings.current.trainer,
                    "mobilePhone" to AppStrings.current.mobile,
                    "phone" to AppStrings.current.phone,
                    "workPhone" to AppStrings.current.workPhone,
                    "birthDate" to AppStrings.current.birthDate,
                    "gender" to AppStrings.current.gender,
                    "nationality" to AppStrings.current.nationality,
                    "username" to AppStrings.current.username,
                    "id" to "ID",
                    "personalId" to AppStrings.current.personalId,
                    "nationalIdNumber" to AppStrings.current.personalId,
                    "passportNumber" to AppStrings.current.passportNumber,
                    "idNumber" to AppStrings.current.idNumber,
                    "wdsfId" to AppStrings.current.wdsfId,
                    "cstsId" to AppStrings.current.cstsId
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
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(AppStrings.current.aboutMe, style = MaterialTheme.typography.labelLarge)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (!bioText.isNullOrBlank()) {
                    Text(bioText!!, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(AppStrings.current.bioNotAvailable, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Address card — show all available address subfields
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(AppStrings.current.address, style = MaterialTheme.typography.labelLarge)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (addressFields.isNotEmpty()) {
                    addressFields.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                            val label = when (k) {
                                "street" -> AppStrings.current.street
                                "city" -> AppStrings.current.city
                                "postalCode" -> AppStrings.current.zip
                                "region" -> AppStrings.current.region
                                "district" -> AppStrings.current.district
                                "conscriptionNumber" -> AppStrings.current.conscriptionNumber
                                "orientationNumber" -> AppStrings.current.orientationNumber
                                else -> k
                            }
                            Text("$label:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                            Text(formatProfileValue(k, v), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (!addrText.isNullOrBlank()) {
                    Text(addrText!!, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(AppStrings.current.addressNotAvailable, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Active couples card (prefer activeCouplesList data)
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(AppStrings.current.activeCouple, style = MaterialTheme.typography.labelLarge)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (activeCoupleNames.isNotEmpty()) {
                    Column {
                        activeCoupleNames.forEach { name ->
                            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                } else {
                    Text(AppStrings.current.noActiveCouples, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Groups (cohorts) card - styled like PersonPage
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(AppStrings.current.trainingGroups, style = MaterialTheme.typography.labelLarge)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
                                            if (!since.isNullOrBlank()) Text("${AppStrings.current.dateFrom}: ${since}", style = MaterialTheme.typography.labelSmall)
                                            if (!until.isNullOrBlank()) Text("${AppStrings.current.dateTo}: ${until}", style = MaterialTheme.typography.labelSmall)
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
                    Text(AppStrings.current.noGroupsToShow, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Category cards (visible, not hidden in details)
        if (personalList.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(AppStrings.current.personalData, style = MaterialTheme.typography.labelLarge)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    personalList.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                            val label = labelMap[k] ?: humanizeKey(k)
                            Text("$label:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                            Text(formatProfileValue(k, v), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (contactList.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(AppStrings.current.contacts, style = MaterialTheme.typography.labelLarge)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    contactList.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                            val label = labelMap[k] ?: humanizeKey(k)
                            Text("$label:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                            Text(formatProfileValue(k, v), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (externalList.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(AppStrings.current.externalIdSection, style = MaterialTheme.typography.labelLarge)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    externalList.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                            val label = labelMap[k] ?: humanizeKey(k)
                            Text("$label:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                            Text(formatProfileValue(k, v), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (otherList.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(AppStrings.current.otherDetails, style = MaterialTheme.typography.labelLarge)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    otherList.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                            val label = labelMap[k] ?: humanizeKey(k)
                            Text("$label:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                            Text(formatProfileValue(k, v), style = MaterialTheme.typography.bodySmall)
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
                    Text(AppStrings.current.changePassword)
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
                    Text(AppStrings.current.changePersonalData)
                }

                if (showEditPersonal) {
                    val person = profileState.person
                    ChangePersonalDataDialog(
                        initialFirst = person?.firstName ?: "",
                        initialLast = person?.lastName ?: "",
                        initialBio = person?.bio ?: "",
                        initialEmail = (person?.email ?: profileState.currentUser?.uEmail) ?: "",
                        initialPrefix = person?.prefixTitle ?: "",
                        initialSuffix = person?.suffixTitle ?: "",
                        initialCstsId = person?.cstsId ?: "",
                        initialWdsfId = person?.wdsfId ?: "",
                        initialNationalIdNumber = person?.nationalIdNumber ?: "",
                        initialNationality = person?.nationality ?: "",
                        initialStreet = person?.address?.street ?: "",
                        initialCity = person?.address?.city ?: "",
                        initialPostal = person?.address?.postalCode ?: "",
                        initialRegion = person?.address?.region ?: "",
                        initialDistrict = person?.address?.district ?: "",
                        initialConscription = person?.address?.conscriptionNumber ?: "",
                        initialOrientation = person?.address?.orientationNumber ?: "",
                        initialPhone = person?.phone ?: "",
                        initialMobile = person?.phone ?: "",
                        initialBirthDate = person?.birthDate ?: "",
                        initialGender = person?.gender ?: "",
                        onDismiss = { showEditPersonal = false },
                        onSave = { _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String ->
                            // persistence is handled inside the dialog; reload from storage
                            showEditPersonal = false
                            refreshTriggerState.value = refreshTriggerState.value + 1
                        }
                    )
                }
        }

        var showLogoutConfirm by remember { mutableStateOf(false) }

        if (showLogoutConfirm) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirm = false },
                title = { Text(AppStrings.current.logout) },
                text = { Text(AppStrings.current.confirmLogoutText) },
                confirmButton = {
                    Button(onClick = {
                        showLogoutConfirm = false
                        scope.launch {
                            try { ServiceLocator.tokenStorage.clear() } catch (_: Throwable) {}
                            try { ServiceLocator.userService.clear() } catch (_: Throwable) {}
                            onLogout()
                        }
                    }) { Text(AppStrings.current.logout) }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirm = false }) { Text(AppStrings.current.cancel) }
                }
            )
        }

        Button(
            onClick = { showLogoutConfirm = true },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(AppStrings.current.logout)
        }
    }
    }
}}

// Dialog composables were moved to ProfileDialogs.kt
