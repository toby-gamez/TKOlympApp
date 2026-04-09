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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.language.NationalityHelper
import com.tkolymp.shared.user.fmtProfileDate
import com.tkolymp.shared.viewmodels.ProfileViewModel
import kotlinx.coroutines.launch

private fun formatProfileValue(key: String, value: String): String {
    val v = value.trim()
    when {
        v.equals("true", ignoreCase = true) -> return AppStrings.current.commonActions.yes
        v.equals("false", ignoreCase = true) -> return AppStrings.current.commonActions.no
    }
    if (key.equals("birthDate", ignoreCase = true)) {
        val formatted = fmtProfileDate(v)
        if (formatted != null && formatted != v) return formatted
    }
    if (key.equals("gender", ignoreCase = true)) {
        return when (v.uppercase()) {
            "MAN" -> AppStrings.current.gender.genderMale
            "WOMAN" -> AppStrings.current.gender.genderFemale
            else -> AppStrings.current.gender.genderUnspecified
        }
    }
    if (key.equals("nationality", ignoreCase = true)) {
        val name = NationalityHelper.getNationalityOptions(AppStrings.currentLanguage).find { it.first == v }?.second
        if (name != null) return name
    }
    return v
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onLogout: () -> Unit = {}, onBack: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    val refreshTriggerState = remember { mutableStateOf(0) }

    val profileViewModel = viewModel<ProfileViewModel>()
    val profileState by profileViewModel.state.collectAsState()
    val derived = profileState.derived
    LaunchedEffect(refreshTriggerState.value) { profileViewModel.load() }

    val outerScroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.current.profile.myProfile) },
                navigationIcon = {
                    onBack?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
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

                val displayName = derived.titleText ?: profileState.currentUser?.uLogin
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(displayName ?: "Uživatel", style = MaterialTheme.typography.headlineSmall)
                }
                // show login under name if available
                profileState.currentUser?.uLogin?.let {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
                // Labeling and categorization
                val labelMap = mapOf(
                    "email" to AppStrings.current.profile.email,
                    "address" to AppStrings.current.address.address,
                    "isTrainer" to AppStrings.current.profile.trainer,
                    "mobilePhone" to AppStrings.current.profile.mobile,
                    "phone" to AppStrings.current.profile.phone,
                    "workPhone" to AppStrings.current.extendedProfile.workPhone,
                    "birthDate" to AppStrings.current.profile.birthDate,
                    "gender" to AppStrings.current.profile.gender,
                    "nationality" to AppStrings.current.profile.nationality,
                    "username" to AppStrings.current.extendedProfile.username,
                    "id" to "ID",
                    "personalId" to AppStrings.current.profile.personalId,
                    "nationalIdNumber" to AppStrings.current.profile.personalId,
                    "passportNumber" to AppStrings.current.extendedProfile.passportNumber,
                    "idNumber" to AppStrings.current.extendedProfile.idNumber,
                    "wdsfId" to AppStrings.current.profile.wdsfId,
                    "cstsId" to AppStrings.current.profile.cstsId
                )

                fun humanizeKey(k: String): String {
                    val spaced = k.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ').replace('-', ' ')
                    return spaced.split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                }

                val personalList = derived.personalList
                val contactList = derived.contactList
                val externalList = derived.externalList
                val otherList = derived.otherList
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(AppStrings.current.profile.aboutMe, style = MaterialTheme.typography.labelLarge)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (!derived.bioText.isNullOrBlank()) {
                    Text(derived.bioText!!, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(AppStrings.current.profile.bioNotAvailable, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Address card — show all available address subfields
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(AppStrings.current.address.address, style = MaterialTheme.typography.labelLarge)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (derived.addressFields.isNotEmpty()) {
                    derived.addressFields.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                            val label = when (k) {
                                "street" -> AppStrings.current.address.street
                                "city" -> AppStrings.current.address.city
                                "postalCode" -> AppStrings.current.address.zip
                                "region" -> AppStrings.current.address.region
                                "district" -> AppStrings.current.address.district
                                "conscriptionNumber" -> AppStrings.current.extendedProfile.conscriptionNumber
                                "orientationNumber" -> AppStrings.current.extendedProfile.orientationNumber
                                else -> k
                            }
                            Text("$label:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                            Text(formatProfileValue(k, v), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (!derived.addrText.isNullOrBlank()) {
                    Text(derived.addrText!!, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(AppStrings.current.address.addressNotAvailable, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Active couples card (prefer activeCouplesList data)
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(AppStrings.current.profile.activeCouple, style = MaterialTheme.typography.labelLarge)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (derived.activeCoupleNames.isNotEmpty()) {
                    Column {
                        derived.activeCoupleNames.forEach { name ->
                            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                } else {
                    Text(AppStrings.current.profile.noActiveCouples, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Groups (cohorts) card - styled like PersonPage
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(AppStrings.current.otherScreen.trainingGroups, style = MaterialTheme.typography.labelLarge)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (derived.cohortItems.isNotEmpty()) {
                    Column {
                        derived.cohortItems.forEach { item ->
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
                                            if (!since.isNullOrBlank()) Text("${AppStrings.current.profile.dateFrom}: ${since}", style = MaterialTheme.typography.labelSmall)
                                            if (!until.isNullOrBlank()) Text("${AppStrings.current.profile.dateTo}: ${until}", style = MaterialTheme.typography.labelSmall)
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
                    Text(AppStrings.current.people.noGroupsToShow, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Category cards (visible, not hidden in details)
        if (personalList.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(AppStrings.current.profile.personalData, style = MaterialTheme.typography.labelLarge)
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
                    Text(AppStrings.current.profile.contacts, style = MaterialTheme.typography.labelLarge)
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
                    Text(AppStrings.current.profile.externalIdSection, style = MaterialTheme.typography.labelLarge)
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
                    Text(AppStrings.current.profile.otherDetails, style = MaterialTheme.typography.labelLarge)
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
                    Text(AppStrings.current.auth.changePassword)
                }

                if (showChangePassDialog) {
                    ChangePasswordDialog(onDismiss = { showChangePassDialog = false }, onSuccess = {
                        // clear storage and navigate to login
                        scope.launch {
                            profileViewModel.logout()
                            showChangePassDialog = false
                            onLogout()
                        }
                    })
                }

                FilledTonalButton(
                    onClick = { showEditPersonal = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(AppStrings.current.profile.changePersonalData)
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
                title = { Text(AppStrings.current.commonActions.logout) },
                text = { Text(AppStrings.current.commonActions.confirmLogoutText) },
                confirmButton = {
                    Button(onClick = {
                        showLogoutConfirm = false
                        scope.launch {
                            profileViewModel.logout()
                            onLogout()
                        }
                    }) { Text(AppStrings.current.commonActions.logout) }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirm = false }) { Text(AppStrings.current.commonActions.cancel) }
                }
            )
        }

        Button(
            onClick = { showLogoutConfirm = true },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(AppStrings.current.commonActions.logout)
        }
    }
    }
}}

// Dialog composables were moved to ProfileDialogs.kt
