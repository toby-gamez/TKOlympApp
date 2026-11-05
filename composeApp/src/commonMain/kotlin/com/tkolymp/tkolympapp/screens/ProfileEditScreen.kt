package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.language.NationalityHelper
import com.tkolymp.shared.user.AddressUpdate
import com.tkolymp.shared.user.PersonUpdateRequest
import com.tkolymp.shared.utils.parseToLocal
import com.tkolymp.shared.utils.withImageWidth
import com.tkolymp.shared.viewmodels.ProfileViewModel
import com.tkolymp.tkolympapp.components.InitialsAvatar
import com.tkolymp.tkolympapp.platform.rememberAvatarPicker
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private fun parseBirthDate(s: String?): LocalDate? {
    if (s.isNullOrBlank()) return null
    val iso = Regex("\\d{4}-\\d{2}-\\d{2}").find(s.trim())?.value
    if (iso != null) return try { LocalDate.parse(iso) } catch (_: Exception) { null }
    return parseToLocal(s)?.date
}

private fun displayDate(iso: String?): String {
    val d = parseBirthDate(iso) ?: return iso ?: ""
    return "${d.day.toString().padStart(2, '0')}.${d.month.number.toString().padStart(2, '0')}.${d.year}"
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)
    )
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

/** Edits the signed-in person's data and profile photo; replaces the former dialog. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ProfileEditScreen(onSaved: () -> Unit, onBack: () -> Unit) {
    val strings = AppStrings.current
    val vm = viewModel<ProfileViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    val person = state.person
    // The form is seeded once the person is loaded; after that the user's edits win over reloads (e.g. after a photo change).
    val formKey = person?.id ?: ""

    var first by remember(formKey) { mutableStateOf(person?.firstName ?: "") }
    var last by remember(formKey) { mutableStateOf(person?.lastName ?: "") }
    var prefix by remember(formKey) { mutableStateOf(person?.prefixTitle ?: "") }
    var suffix by remember(formKey) { mutableStateOf(person?.suffixTitle ?: "") }
    var email by remember(formKey) { mutableStateOf((person?.email ?: state.currentUser?.uEmail) ?: "") }
    var csts by remember(formKey) { mutableStateOf(person?.cstsId ?: "") }
    var wdsf by remember(formKey) { mutableStateOf(person?.wdsfId ?: "") }
    var nid by remember(formKey) { mutableStateOf(person?.nationalIdNumber ?: "") }
    var nationalityId by remember(formKey) { mutableStateOf(person?.nationality ?: "") }
    var phone by remember(formKey) { mutableStateOf(person?.phone ?: "") }
    var birthIso by remember(formKey) { mutableStateOf(parseBirthDate(person?.birthDate)?.toString() ?: (person?.birthDate ?: "")) }
    var genderIso by remember(formKey) { mutableStateOf(person?.gender?.uppercase() ?: "UNSPECIFIED") }
    var street by remember(formKey) { mutableStateOf(person?.address?.street ?: "") }
    var city by remember(formKey) { mutableStateOf(person?.address?.city ?: "") }
    var postal by remember(formKey) { mutableStateOf(person?.address?.postalCode ?: "") }
    var region by remember(formKey) { mutableStateOf(person?.address?.region ?: "") }
    var district by remember(formKey) { mutableStateOf(person?.address?.district ?: "") }
    var conscription by remember(formKey) { mutableStateOf(person?.address?.conscriptionNumber ?: "") }
    var orientation by remember(formKey) { mutableStateOf(person?.address?.orientationNumber ?: "") }
    var instagram by remember(formKey) { mutableStateOf(person?.instagramUsername ?: "") }
    var tiktok by remember(formKey) { mutableStateOf(person?.tiktokUsername ?: "") }
    var facebook by remember(formKey) { mutableStateOf(person?.facebookUrl ?: "") }
    var website by remember(formKey) { mutableStateOf(person?.websiteUrl ?: "") }
    var note by remember(formKey) { mutableStateOf(TextFieldValue(person?.note ?: "")) }

    fun snapshot() = listOf(
        first, last, prefix, suffix, email, csts, wdsf, nid, nationalityId, phone, birthIso, genderIso,
        street, city, postal, region, district, conscription, orientation, instagram, tiktok, facebook, website, note.text
    ).joinToString("\u0000")
    val initialSnapshot = remember(formKey) { snapshot() }
    val dirty = person != null && snapshot() != initialSnapshot

    var saving by remember { mutableStateOf(false) }
    var photoBusy by remember { mutableStateOf(false) }
    var showPhotoSheet by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = dirty && !saving) { showDiscard = true }
    fun requestBack() { if (dirty && !saving) showDiscard = true else onBack() }

    val avatarPicker = rememberAvatarPicker { bytes ->
        scope.launch {
            photoBusy = true
            val err = vm.setAvatar(bytes)
            photoBusy = false
            if (err != null) snackbar.showSnackbar(err)
        }
    }

    fun save() {
        if (email.isNotBlank() && !email.contains("@")) {
            scope.launch { snackbar.showSnackbar(strings.invalidEmail) }
            return
        }
        scope.launch {
            saving = true
            val err = vm.savePerson(
                PersonUpdateRequest(
                    cstsId = csts, email = email, firstName = first, lastName = last,
                    nationalIdNumber = nid, nationality = nationalityId, phone = phone, wdsfId = wdsf,
                    prefixTitle = prefix, suffixTitle = suffix, gender = genderIso,
                    birthDateSet = true, birthDate = birthIso,
                    instagramUsername = instagram.trim().removePrefix("@"),
                    tiktokUsername = tiktok.trim().removePrefix("@"),
                    facebookUrl = facebook.trim(), websiteUrl = website.trim(), note = note.text,
                    address = AddressUpdate(
                        street = street, city = city, postalCode = postal, region = region,
                        district = district, conscriptionNumber = conscription, orientationNumber = orientation
                    )
                )
            )
            saving = false
            if (err == null) onSaved() else snackbar.showSnackbar(err)
        }
    }

    fun wrapNote(open: String, close: String) {
        val sel = note.selection
        val text = note.text
        note = if (sel.collapsed) {
            TextFieldValue(text.substring(0, sel.start) + open + close + text.substring(sel.start), TextRange(sel.start + open.length))
        } else {
            val selected = text.substring(sel.min, sel.max)
            val start = sel.min + open.length
            TextFieldValue(text.substring(0, sel.min) + open + selected + close + text.substring(sel.max), TextRange(start, start + selected.length))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.editPersonalData) },
                navigationIcon = {
                    IconButton(onClick = ::requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.commonActions.back)
                    }
                },
                actions = {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = ::save, enabled = dirty) { Text(strings.save) }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (person == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading) CircularProgressIndicator()
                else Text(state.error?.message ?: strings.errorMessages.errorLoadingProfile, color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            // Photo
            Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                val avatarName = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ").ifBlank { strings.profile.displayNameFallback }
                Box(modifier = Modifier.size(112.dp)) {
                    InitialsAvatar(
                        name = avatarName,
                        size = 112.dp,
                        fontSize = 36.sp,
                        imageUrl = withImageWidth(person.imageUrl, 256),
                        modifier = Modifier
                            .clip(CircleShape)
                            .then(if (avatarPicker != null && !photoBusy) Modifier.clickable(onClickLabel = strings.profile.changePhoto) { showPhotoSheet = true } else Modifier)
                    )
                    if (photoBusy) {
                        Box(
                            Modifier.matchParentSize().clip(CircleShape).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                        }
                    } else if (avatarPicker != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            SectionHeader(strings.profile.basicInfoSection)
            FormField(first, { first = it }, strings.firstName)
            FormField(last, { last = it }, strings.lastName)
            FormField(prefix, { prefix = it }, strings.prefixTitle)
            FormField(suffix, { suffix = it }, strings.suffixTitle)

            // Birth date: read-only field, picked through a date picker
            OutlinedTextField(
                value = displayDate(birthIso),
                onValueChange = {},
                label = { Text(strings.birthDate) },
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { focusManager.clearFocus(); showDatePicker = true }) {
                        Icon(Icons.Default.CalendarToday, contentDescription = strings.selectDate)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            if (showDatePicker) {
                val pickerState = rememberDatePickerState(
                    initialSelectedDateMillis = parseBirthDate(birthIso)?.let {
                        LocalDateTime(it, LocalTime(0, 0)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                    }
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        Button(onClick = {
                            pickerState.selectedDateMillis?.let {
                                birthIso = Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                            }
                            showDatePicker = false
                        }) { Text(strings.ok) }
                    }
                ) { DatePicker(state = pickerState) }
            }

            // Gender (stored as MAN / WOMAN / UNSPECIFIED, shown localized)
            val genderLabels = listOf("MAN" to strings.genderMale, "WOMAN" to strings.genderFemale, "UNSPECIFIED" to strings.genderUnspecified)
            var genderExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = genderExpanded, onExpandedChange = { genderExpanded = it }) {
                OutlinedTextField(
                    value = genderLabels.firstOrNull { it.first == genderIso }?.second ?: strings.genderUnspecified,
                    onValueChange = {},
                    label = { Text(strings.profile.gender) },
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = genderExpanded, onDismissRequest = { genderExpanded = false }) {
                    genderLabels.forEach { (iso, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { genderIso = iso; genderExpanded = false })
                    }
                }
            }

            // Nationality (stored as ISO 3166-1 numeric id, shown as a localized name)
            val nationalityOptions = NationalityHelper.getNationalityOptions(AppStrings.currentLanguage)
            var nationalityExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = nationalityExpanded, onExpandedChange = { nationalityExpanded = it }) {
                OutlinedTextField(
                    value = nationalityOptions.find { it.first == nationalityId }?.second ?: nationalityId,
                    onValueChange = {},
                    label = { Text(strings.nationality) },
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = nationalityExpanded) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = nationalityExpanded, onDismissRequest = { nationalityExpanded = false }) {
                    nationalityOptions.forEach { (id, name) ->
                        DropdownMenuItem(text = { Text(name) }, onClick = { nationalityId = id; nationalityExpanded = false })
                    }
                }
            }

            SectionHeader(strings.contacts)
            FormField(email, { email = it }, strings.email, keyboardType = KeyboardType.Email)
            FormField(phone, { phone = it }, strings.phone, keyboardType = KeyboardType.Phone)

            SectionHeader(strings.profile.externalIdSection)
            FormField(csts, { csts = it }, strings.cstsId, keyboardType = KeyboardType.Number)
            FormField(wdsf, { wdsf = it }, strings.wdsfId, keyboardType = KeyboardType.Number)
            FormField(nid, { nid = it }, strings.personalId)

            SectionHeader(strings.addressLabel)
            FormField(street, { street = it }, strings.street)
            FormField(city, { city = it }, strings.city)
            FormField(postal, { postal = it }, strings.zip)
            FormField(region, { region = it }, strings.region)
            FormField(district, { district = it }, strings.district)
            FormField(conscription, { conscription = it }, strings.conscriptionNumber)
            FormField(orientation, { orientation = it }, strings.orientationNumber)

            SectionHeader(strings.profile.socialLinks)
            FormField(instagram, { instagram = it }, strings.profile.instagram, supportingText = strings.profile.socialUsernameHelper)
            FormField(tiktok, { tiktok = it }, strings.profile.tiktok, supportingText = strings.profile.socialUsernameHelper)
            FormField(facebook, { facebook = it }, strings.profile.facebook, supportingText = strings.profile.facebookNameHint)
            FormField(website, { website = it }, strings.profile.website, keyboardType = KeyboardType.Uri, supportingText = strings.profile.urlHint)

            SectionHeader(strings.profile.note)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = { wrapNote("<b>", "</b>") }) { Icon(Icons.Default.FormatBold, contentDescription = strings.profile.formatBold) }
                IconButton(onClick = { wrapNote("<i>", "</i>") }) { Icon(Icons.Default.FormatItalic, contentDescription = strings.profile.formatItalic) }
                IconButton(onClick = { wrapNote("<u>", "</u>") }) { Icon(Icons.Default.FormatUnderlined, contentDescription = strings.profile.formatUnderline) }
                IconButton(onClick = { wrapNote("<ul><li>", "</li></ul>") }) { Icon(Icons.Default.FormatListBulleted, contentDescription = strings.profile.formatBulletList) }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                minLines = 4,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
            )
            Spacer(Modifier.size(32.dp))
        }
    }

    if (showPhotoSheet && avatarPicker != null) {
        ModalBottomSheet(onDismissRequest = { showPhotoSheet = false }) {
            Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                ListItem(
                    headlineContent = { Text(strings.profile.takePhoto) },
                    leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                    modifier = Modifier.clickable { showPhotoSheet = false; avatarPicker.takePhoto() }
                )
                ListItem(
                    headlineContent = { Text(strings.profile.chooseFromGallery) },
                    leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                    modifier = Modifier.clickable { showPhotoSheet = false; avatarPicker.pickFromGallery() }
                )
                if (person?.imageUrl != null) {
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(strings.profile.removePhoto, color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            showPhotoSheet = false
                            scope.launch {
                                photoBusy = true
                                val err = vm.setAvatar(null)
                                photoBusy = false
                                if (err != null) snackbar.showSnackbar(err)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(strings.profile.discardChangesTitle) },
            text = { Text(strings.profile.discardChangesText) },
            confirmButton = { TextButton(onClick = { showDiscard = false; onBack() }) { Text(strings.profile.discard) } },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text(strings.profile.keepEditing) } }
        )
    }
}
