package com.tkolymp.tkolympapp.screens

// Biometric support removed — no FragmentActivity import
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedSuggestionChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.language.NationalityHelper
import com.tkolymp.shared.user.fmtProfileDate
import com.tkolymp.shared.viewmodels.ProfileViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.CoupleAvatar
import com.tkolymp.tkolympapp.components.InitialsAvatar
import com.tkolymp.tkolympapp.components.parseColorOrDefault
import com.tkolymp.tkolympapp.util.StaggeredItem
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
    val profileState by profileViewModel.state.collectAsStateWithLifecycle()
    val derived = profileState.derived
    LaunchedEffect(refreshTriggerState.value) { profileViewModel.load() }

    var sectionsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sectionsVisible = true }

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
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(8.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {
                val person = profileState.person
                val displayName = derived.titleText ?: profileState.currentUser?.uLogin

                // Header
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                    InitialsAvatar(name = displayName ?: AppStrings.current.profile.displayNameFallback, size = 64.dp, fontSize = 22.sp)
                }
                Text(
                    displayName ?: AppStrings.current.profile.displayNameFallback,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                profileState.currentUser?.uLogin?.let { login ->
                    if (login != displayName) {
                        Text(login, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }
                if (person?.isTrainer == true) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        ElevatedSuggestionChip(onClick = {}, label = { Text(AppStrings.current.profile.trainer) })
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Birth date + Gender
                StaggeredItem(index = 0, visible = sectionsVisible, baseDelayMs = 50) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(person?.birthDate?.let { fmtProfileDate(it) } ?: "—", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                val genderIcon = when (person?.gender) {
                                    "MAN" -> Icons.Default.Male
                                    "WOMAN" -> Icons.Default.Female
                                    else -> Icons.AutoMirrored.Filled.HelpOutline
                                }
                                Icon(genderIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                val genderLabel = when (person?.gender) {
                                    "MAN" -> AppStrings.current.gender.genderMale
                                    "WOMAN" -> AppStrings.current.gender.genderFemale
                                    "UNSPECIFIED" -> AppStrings.current.gender.genderUnspecified
                                    null, "" -> "—"
                                    else -> (person?.gender ?: "").lowercase()
                                }
                                Text(genderLabel, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // Email
                val emailText = derived.emailText
                if (!emailText.isNullOrBlank()) {
                    StaggeredItem(index = 1, visible = sectionsVisible, baseDelayMs = 50) {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(emailText, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // Phone
                val phoneText = person?.phone
                if (!phoneText.isNullOrBlank()) {
                    StaggeredItem(index = 2, visible = sectionsVisible, baseDelayMs = 50) {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(phoneText, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // Address
                StaggeredItem(index = 3, visible = sectionsVisible, baseDelayMs = 50) {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(AppStrings.current.address.address, style = MaterialTheme.typography.labelLarge)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            if (derived.addressFields.isNotEmpty()) {
                                val addrMap = derived.addressFields.toMap()
                                val street = addrMap["street"]?.takeIf { it.isNotBlank() }
                                val city = addrMap["city"]?.takeIf { it.isNotBlank() }
                                val postalRaw = addrMap["postalCode"]?.takeIf { it.isNotBlank() }
                                val region = addrMap["region"]?.takeIf { it.isNotBlank() }
                                val district = addrMap["district"]?.takeIf { it.isNotBlank() }
                                val conscription = addrMap["conscriptionNumber"]?.takeIf { it.isNotBlank() }
                                val orientation = addrMap["orientationNumber"]?.takeIf { it.isNotBlank() }

                                val formattedPostal = postalRaw?.let {
                                    val digits = it.trim().replace(" ", "")
                                    if (digits.length >= 4) "${digits.take(3)} ${digits.drop(3)}" else it
                                }
                                val numberPart = when {
                                    conscription != null && orientation != null -> "$conscription/$orientation"
                                    conscription != null -> conscription
                                    orientation != null -> orientation
                                    else -> null
                                }
                                val streetPart = listOfNotNull(street, numberPart).joinToString(" ").takeIf { it.isNotBlank() }
                                val cityPart = listOfNotNull(city, formattedPostal).joinToString(" ").takeIf { it.isNotBlank() }
                                val line1 = listOfNotNull(streetPart, cityPart).joinToString(", ")
                                val line2 = listOfNotNull(district, region).joinToString(", ")

                                if (line1.isNotBlank()) Text(line1, style = MaterialTheme.typography.bodyMedium)
                                if (line2.isNotBlank()) Text(line2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                if (line1.isBlank() && line2.isBlank()) Text(AppStrings.current.address.addressNotAvailable, style = MaterialTheme.typography.bodySmall)
                            } else if (!derived.addrText.isNullOrBlank()) {
                                Text(derived.addrText ?: "", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Text(AppStrings.current.address.addressNotAvailable, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // Active couples
                StaggeredItem(index = 4, visible = sectionsVisible, baseDelayMs = 50) {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(AppStrings.current.profile.activeCouple, style = MaterialTheme.typography.labelLarge)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            if (derived.activeCoupleNames.isNotEmpty()) {
                                derived.activeCoupleNames.forEach { name ->
                                    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                                }
                            } else {
                                Text(AppStrings.current.profile.noActiveCouples, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // Training groups
                StaggeredItem(index = 5, visible = sectionsVisible, baseDelayMs = 50) {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(AppStrings.current.otherScreen.trainingGroups, style = MaterialTheme.typography.labelLarge)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            if (derived.cohortItems.isNotEmpty()) {
                                derived.cohortItems.forEach { item ->
                                    val color = try { parseColorOrDefault(item.colorRgb) } catch (_: Exception) { Color.Gray }
                                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                        Column(modifier = Modifier.padding(6.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                                Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(color, RoundedCornerShape(6.dp)))
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(text = item.name, style = MaterialTheme.typography.titleSmall)
                                                    if (!item.since.isNullOrBlank()) Text("${AppStrings.current.profile.dateFrom}: ${item.since}", style = MaterialTheme.typography.labelSmall)
                                                    if (!item.until.isNullOrBlank()) Text("${AppStrings.current.profile.dateTo}: ${item.until}", style = MaterialTheme.typography.labelSmall)
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
                }

                // ČSTS Progress
                val cstsProgressList = person?.cstsProgressList ?: emptyList()
                StaggeredItem(index = 6, visible = sectionsVisible, baseDelayMs = 50) {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(AppStrings.current.competition.cstsProgress, style = MaterialTheme.typography.labelLarge)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            if (cstsProgressList.isNotEmpty()) {
                                val byCompetitor = cstsProgressList.groupBy { it.competitorName?.takeIf { n -> n.isNotBlank() } ?: "" }
                                byCompetitor.entries.forEachIndexed { idx, (competitorName, entries) ->
                                    if (idx > 0) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    if (competitorName.isNotBlank()) {
                                        val coupleNames = competitorName.split(" - ", limit = 2).map { it.trim() }.takeIf { it.size == 2 }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (coupleNames != null) {
                                                CoupleAvatar(womanName = coupleNames[0], manName = coupleNames[1], size = 24.dp)
                                            } else {
                                                InitialsAvatar(name = competitorName, size = 24.dp)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(competitorName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                    entries.forEach { entry ->
                                        val rawCat = entry.category?.name?.takeIf { it.isNotBlank() } ?: ""
                                        val catFormatted = AppStrings.current.competition.formatType(rawCat)
                                        val entryPoints = entry.points
                                        val entryFinals = entry.finals
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = catFormatted,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val pts = entryPoints?.toDoubleOrNull()
                                            if (pts != null && pts != 0.0) {
                                                val ptsStr = if (pts % 1.0 == 0.0) pts.toInt().toString() else { val s = kotlin.math.round(pts * 10).toLong(); "${s / 10}.${kotlin.math.abs(s % 10)}" }
                                                Row(
                                                    modifier = Modifier
                                                        .padding(start = 4.dp)
                                                        .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "$ptsStr${AppStrings.current.competition.pointsSuffix}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                                    )
                                                }
                                            }
                                            if (entryFinals != null && entryFinals > 0) {
                                                Row(
                                                    modifier = Modifier
                                                        .padding(start = 4.dp)
                                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "${entryFinals}F",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(AppStrings.current.competition.noCstsProgress, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // Remaining personal/contact fields not covered by icon cards above
                val labelMap = mapOf(
                    "nationality" to AppStrings.current.profile.nationality,
                    "mobilePhone" to AppStrings.current.profile.mobile,
                    "workPhone" to AppStrings.current.extendedProfile.workPhone,
                    "personalId" to AppStrings.current.profile.personalId,
                    "nationalIdNumber" to AppStrings.current.profile.personalId,
                    "passportNumber" to AppStrings.current.extendedProfile.passportNumber,
                    "idNumber" to AppStrings.current.extendedProfile.idNumber,
                    "wdsfId" to AppStrings.current.profile.wdsfId,
                    "cstsId" to AppStrings.current.profile.cstsId,
                    "username" to AppStrings.current.extendedProfile.username,
                    "id" to "ID"
                )
                fun humanizeKey(k: String): String {
                    val spaced = k.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ').replace('-', ' ')
                    return spaced.split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                }

                val iconShownKeys = setOf("birthDate", "gender", "email", "phone")
                val remainingPersonal = derived.personalList.filter { (k, _) -> k !in iconShownKeys }
                val remainingContact = derived.contactList.filter { (k, _) -> k !in iconShownKeys }
                val combined = remainingPersonal + remainingContact
                if (combined.isNotEmpty()) {
                    StaggeredItem(index = 7, visible = sectionsVisible, baseDelayMs = 50) {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(AppStrings.current.profile.personalData, style = MaterialTheme.typography.labelLarge)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                combined.forEach { (k, v) ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                                        Text("${labelMap[k] ?: humanizeKey(k)}:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                                        Text(formatProfileValue(k, v), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                if (derived.externalList.isNotEmpty()) {
                    StaggeredItem(index = 8, visible = sectionsVisible, baseDelayMs = 50) {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(AppStrings.current.profile.externalIdSection, style = MaterialTheme.typography.labelLarge)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                derived.externalList.forEach { (k, v) ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                                        Text("${labelMap[k] ?: humanizeKey(k)}:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                                        Text(formatProfileValue(k, v), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                val filteredOtherList = derived.otherList.filter { (k, _) -> k !in setOf("isTrainer", "username") }
                if (filteredOtherList.isNotEmpty()) {
                    StaggeredItem(index = 9, visible = sectionsVisible, baseDelayMs = 50) {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(AppStrings.current.profile.otherDetails, style = MaterialTheme.typography.labelLarge)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                filteredOtherList.forEach { (k, v) ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                                        Text("${labelMap[k] ?: humanizeKey(k)}:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                                        Text(formatProfileValue(k, v), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Action buttons
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var showChangePassDialog by remember { mutableStateOf(false) }
                    var showEditPersonal by remember { mutableStateOf(false) }

                    FilledTonalButton(onClick = { showChangePassDialog = true }, modifier = Modifier.weight(1f)) {
                        Text(AppStrings.current.auth.changePassword)
                    }

                    if (showChangePassDialog) {
                        ChangePasswordDialog(onDismiss = { showChangePassDialog = false }, onSuccess = {
                            scope.launch {
                                profileViewModel.logout()
                                showChangePassDialog = false
                                onLogout()
                            }
                        })
                    }

                    FilledTonalButton(onClick = { showEditPersonal = true }, modifier = Modifier.weight(1f)) {
                        Text(AppStrings.current.profile.changePersonalData)
                    }

                    if (showEditPersonal) {
                        val p = profileState.person
                        ChangePersonalDataDialog(
                            initialFirst = p?.firstName ?: "",
                            initialLast = p?.lastName ?: "",
                            initialEmail = (p?.email ?: profileState.currentUser?.uEmail) ?: "",
                            initialPrefix = p?.prefixTitle ?: "",
                            initialSuffix = p?.suffixTitle ?: "",
                            initialCstsId = p?.cstsId ?: "",
                            initialWdsfId = p?.wdsfId ?: "",
                            initialNationalIdNumber = p?.nationalIdNumber ?: "",
                            initialNationality = p?.nationality ?: "",
                            initialStreet = p?.address?.street ?: "",
                            initialCity = p?.address?.city ?: "",
                            initialPostal = p?.address?.postalCode ?: "",
                            initialRegion = p?.address?.region ?: "",
                            initialDistrict = p?.address?.district ?: "",
                            initialConscription = p?.address?.conscriptionNumber ?: "",
                            initialOrientation = p?.address?.orientationNumber ?: "",
                            initialPhone = p?.phone ?: "",
                            initialMobile = p?.phone ?: "",
                            initialBirthDate = p?.birthDate ?: "",
                            initialGender = p?.gender ?: "",
                            onDismiss = { showEditPersonal = false },
                            onSave = { _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String, _: String ->
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

                Button(onClick = { showLogoutConfirm = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(AppStrings.current.commonActions.logout)
                }
            }
        }
    }
}

// Dialog composables were moved to ProfileDialogs.kt
