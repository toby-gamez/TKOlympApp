package com.tkolymp.tkolympapp.screens

// Biometric support removed — no FragmentActivity import
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import com.tkolymp.tkolympapp.components.skeleton.SkeletonProfile
import com.tkolymp.tkolympapp.components.RowDivider
import com.tkolymp.tkolympapp.components.ScheduleCard
import com.tkolymp.tkolympapp.components.SectionLabel
import androidx.compose.material.icons.filled.Edit
import com.tkolymp.tkolympapp.components.SecondaryPillButton
import com.tkolymp.tkolympapp.components.cardLineBorder
import com.tkolymp.tkolympapp.components.surfaceCardColors
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import com.tkolymp.tkolympapp.components.Button
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
import com.tkolymp.tkolympapp.components.TopAppBar
import androidx.compose.runtime.Composable
import com.tkolymp.tkolympapp.util.SkeletonCrossfade
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.tkolympapp.ui.theme.Spacing
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.language.NationalityHelper
import com.tkolymp.shared.user.fmtProfileDate
import com.tkolymp.shared.utils.withImageWidth
import com.tkolymp.shared.viewmodels.ProfileViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.CoupleAvatar
import com.tkolymp.tkolympapp.components.ErrorBanner
import com.tkolymp.tkolympapp.components.ErrorState
import com.tkolymp.shared.competitions.deriveDanceClasses
import com.tkolymp.tkolympapp.components.PersonHeader
import com.tkolymp.tkolympapp.components.InitialsAvatar
import com.tkolymp.tkolympapp.components.PersonNoteCard
import com.tkolymp.tkolympapp.components.SocialLinksRow
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
fun ProfileScreen(
    onLogout: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    onEditProfile: () -> Unit = {},
    onChangePassword: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val refreshTriggerState = remember { mutableStateOf(0) }

    val profileViewModel = viewModel<ProfileViewModel>()
    val profileState by profileViewModel.state.collectAsStateWithLifecycle()
    val derived = profileState.derived
    LaunchedEffect(refreshTriggerState.value) { profileViewModel.load() }

    // The edit screens change the cached person; reload when coming back to this one.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var resumedOnce = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (resumedOnce) scope.launch { profileViewModel.load() } else resumedOnce = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            val error = profileState.error
            SkeletonCrossfade(error == null && profileState.person == null && profileState.isLoading) { showSkeleton ->
            if (error != null && profileState.person == null) {
                ErrorState(error = error, onRetry = { scope.launch { profileViewModel.load() } })
            } else if (showSkeleton) {
                SkeletonProfile()
            } else {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.default, vertical = Spacing.small),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {
                profileState.error?.let { error ->
                    ErrorBanner(error = error, onRetry = { scope.launch { profileViewModel.load() } })
                }
                val person = profileState.person
                val displayName = derived.titleText ?: profileState.currentUser?.uLogin

                // Header
                val headerName = displayName ?: AppStrings.current.profile.displayNameFallback
                PersonHeader(
                    rawName = headerName,
                    imageUrl = withImageWidth(person?.imageUrl, 256),
                    subtitle = profileState.currentUser?.uLogin?.takeIf { it != displayName },
                    details = listOfNotNull(
                        AppStrings.current.profile.trainer.takeIf { person?.isTrainer == true },
                        person?.let { deriveDanceClasses(it.cstsProgressList) }?.let { "STT/LAT ${it.label}" }
                    )
                )
                if (person != null) {
                    SocialLinksRow(person = person)
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Edit action sits right under the header (prototype)
                SecondaryPillButton(
                    text = AppStrings.current.profile.editPersonalData,
                    onClick = onEditProfile,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.small),
                    icon = Icons.Filled.Edit
                )

                val cstsProgressList = person?.cstsProgressList ?: emptyList()

                // Active couple (hidden when the ČSTS Progress card already shows the couple)
                if (cstsProgressList.isEmpty()) {
                    StaggeredItem(index = 0, visible = sectionsVisible, baseDelayMs = 50) {
                        Column {
                            SectionLabel(AppStrings.current.profile.activeCouple, horizontalPadding = 0.dp)
                            ScheduleCard() {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                                    val couples = person?.activeCouplesList ?: emptyList()
                                    if (couples.isNotEmpty()) {
                                        couples.forEach { c ->
                                            val manName = listOfNotNull(c.man?.firstName, c.man?.lastName).joinToString(" ").trim().takeIf { it.isNotBlank() }
                                            val womanName = listOfNotNull(c.woman?.firstName, c.woman?.lastName).joinToString(" ").trim().takeIf { it.isNotBlank() }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                                CoupleAvatar(womanName = womanName, manName = manName, size = 40.dp, womanImageUrl = c.woman?.imageUrl, manImageUrl = c.man?.imageUrl)
                                                Spacer(Modifier.width(18.dp))
                                                Text(listOfNotNull(manName, womanName).joinToString(" - "), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    } else if (derived.activeCoupleNames.isNotEmpty()) {
                                        derived.activeCoupleNames.forEach { name ->
                                            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                        }
                                    } else {
                                        Text(AppStrings.current.profile.noActiveCouples, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                // Personal data
                val genderLabel = when (person?.gender) {
                    "MAN" -> AppStrings.current.gender.genderMale
                    "WOMAN" -> AppStrings.current.gender.genderFemale
                    "UNSPECIFIED" -> AppStrings.current.gender.genderUnspecified
                    null, "" -> "—"
                    else -> (person.gender ?: "").lowercase()
                }
                StaggeredItem(index = 1, visible = sectionsVisible, baseDelayMs = 50) {
                    Column {
                        SectionLabel(AppStrings.current.profile.personalData, horizontalPadding = 0.dp)
                        ScheduleCard() {
                            ProfileValueRow(AppStrings.current.profile.birthDate, person?.birthDate?.let { fmtProfileDate(it) } ?: "—")
                            RowDivider()
                            ProfileValueRow(AppStrings.current.profile.gender, genderLabel)
                        }
                    }
                }

                // Contacts
                val emailText = derived.emailText?.takeIf { it.isNotBlank() }
                val phoneText = person?.phone?.takeIf { it.isNotBlank() }
                if (emailText != null || phoneText != null) {
                    StaggeredItem(index = 2, visible = sectionsVisible, baseDelayMs = 50) {
                        Column {
                            SectionLabel(AppStrings.current.profile.contacts, horizontalPadding = 0.dp)
                            ScheduleCard() {
                                if (emailText != null) ProfileValueRow(AppStrings.current.profile.email, emailText)
                                if (emailText != null && phoneText != null) RowDivider()
                                if (phoneText != null) ProfileValueRow(AppStrings.current.profile.phone, phoneText)
                            }
                        }
                    }
                }

                // External identification (ČSTS IDT opens the federation page)
                val cstsId = person?.cstsId?.takeIf { it.isNotBlank() }
                val wdsfId = person?.wdsfId?.takeIf { it.isNotBlank() }
                if (cstsId != null || wdsfId != null) {
                    StaggeredItem(index = 3, visible = sectionsVisible, baseDelayMs = 50) {
                        val uriHandler = LocalUriHandler.current
                        Column {
                            SectionLabel("ČSTS · WDSF", horizontalPadding = 0.dp)
                            ScheduleCard() {
                                if (cstsId != null) ProfileValueRow(
                                    "ČSTS IDT", cstsId,
                                    onClick = { uriHandler.openUri("https://www.csts.cz/dancesport/evidence/lide/$cstsId/osobni_udaje") },
                                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew
                                )
                                if (cstsId != null && wdsfId != null) RowDivider()
                                if (wdsfId != null) ProfileValueRow("WDSF MIN", wdsfId)
                            }
                        }
                    }
                }

                // Note (markdown)
                if (!person?.note.isNullOrBlank()) {
                    StaggeredItem(index = 4, visible = sectionsVisible, baseDelayMs = 50) {
                        PersonNoteCard(note = person?.note)
                    }
                }

                // Address
                StaggeredItem(index = 5, visible = sectionsVisible, baseDelayMs = 50) {
                    Column {
                        SectionLabel(AppStrings.current.address.address, horizontalPadding = 0.dp)
                        ScheduleCard() {
                            Column(modifier = Modifier.padding(14.dp)) {
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

                                    if (line1.isNotBlank()) Text(line1, style = MaterialTheme.typography.bodyLarge)
                                    if (line2.isNotBlank()) Text(line2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                    if (line1.isBlank() && line2.isBlank()) Text(AppStrings.current.address.addressNotAvailable, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else if (!derived.addrText.isNullOrBlank()) {
                                    Text(derived.addrText ?: "", style = MaterialTheme.typography.bodyLarge)
                                } else {
                                    Text(AppStrings.current.address.addressNotAvailable, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Training groups
                StaggeredItem(index = 6, visible = sectionsVisible, baseDelayMs = 50) {
                    Column {
                        SectionLabel(AppStrings.current.otherScreen.trainingGroups, horizontalPadding = 0.dp)
                        if (derived.cohortItems.isNotEmpty()) {
                            ScheduleCard() {
                                derived.cohortItems.forEachIndexed { i, item ->
                                    val color = try { parseColorOrDefault(item.colorRgb) } catch (_: Exception) { Color.Gray }
                                    if (i > 0) RowDivider()
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Box(Modifier.size(36.dp).background(color, RoundedCornerShape(10.dp)))
                                        Column(Modifier.weight(1f)) {
                                            Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                            val period = listOfNotNull(
                                                item.since?.takeIf { it.isNotBlank() }?.let { "${AppStrings.current.profile.dateFrom}: $it" },
                                                item.until?.takeIf { it.isNotBlank() }?.let { "${AppStrings.current.profile.dateTo}: $it" }
                                            ).joinToString(" · ")
                                            if (period.isNotBlank()) Text(period, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(AppStrings.current.people.noGroupsToShow, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // ČSTS Progress
                StaggeredItem(index = 7, visible = sectionsVisible, baseDelayMs = 50) {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs), shape = RoundedCornerShape(16.dp), colors = surfaceCardColors(), border = cardLineBorder()) {
                        Column(modifier = Modifier.padding(Spacing.medium)) {
                            Text(AppStrings.current.competition.cstsProgress, style = MaterialTheme.typography.labelLarge)
                            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.small))
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
                                                        .padding(start = Spacing.xs)
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
                                                        .padding(start = Spacing.xs)
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
                    StaggeredItem(index = 8, visible = sectionsVisible, baseDelayMs = 50) {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs), shape = RoundedCornerShape(16.dp), colors = surfaceCardColors(), border = cardLineBorder()) {
                            Column(modifier = Modifier.padding(Spacing.medium)) {
                                Text(AppStrings.current.profile.personalData, style = MaterialTheme.typography.labelLarge)
                                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.small))
                                combined.forEach { (k, v) ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                                        Text("${labelMap[k] ?: humanizeKey(k)}:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = Spacing.small))
                                        Text(formatProfileValue(k, v), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                if (derived.externalList.isNotEmpty()) {
                    StaggeredItem(index = 9, visible = sectionsVisible, baseDelayMs = 50) {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs), shape = RoundedCornerShape(16.dp), colors = surfaceCardColors(), border = cardLineBorder()) {
                            Column(modifier = Modifier.padding(Spacing.medium)) {
                                Text(AppStrings.current.profile.externalIdSection, style = MaterialTheme.typography.labelLarge)
                                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.small))
                                derived.externalList.forEach { (k, v) ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                                        Text("${labelMap[k] ?: humanizeKey(k)}:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = Spacing.small))
                                        Text(formatProfileValue(k, v), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                val filteredOtherList = derived.otherList.filter { (k, _) -> k !in setOf("isTrainer", "username") }
                if (filteredOtherList.isNotEmpty()) {
                    StaggeredItem(index = 10, visible = sectionsVisible, baseDelayMs = 50) {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs), shape = RoundedCornerShape(16.dp), colors = surfaceCardColors(), border = cardLineBorder()) {
                            Column(modifier = Modifier.padding(Spacing.medium)) {
                                Text(AppStrings.current.profile.otherDetails, style = MaterialTheme.typography.labelLarge)
                                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.small))
                                filteredOtherList.forEach { (k, v) ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                                        Text("${labelMap[k] ?: humanizeKey(k)}:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = Spacing.small))
                                        Text(formatProfileValue(k, v), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Action buttons
                Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.default), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    SecondaryPillButton(AppStrings.current.auth.changePassword, onChangePassword, Modifier.weight(1f))
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

                SecondaryPillButton(
                    text = AppStrings.current.commonActions.logout,
                    onClick = { showLogoutConfirm = true },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.small),
                    danger = true
                )
            }
            }
            }
        }
    }
}



/** Label on the left (muted), value on the right; optional tap action with a trailing icon. */
@Composable
internal fun ProfileValueRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
        if (trailingIcon != null) Icon(trailingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}
