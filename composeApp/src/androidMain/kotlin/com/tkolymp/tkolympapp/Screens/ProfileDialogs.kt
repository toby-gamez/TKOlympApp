package com.tkolymp.tkolympapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.language.NationalityHelper
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChangePasswordDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val strings = AppStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.changePassword) },
        text = {
            Column(modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                Text(strings.passwordMinLength)
                TextField(value = newPass, onValueChange = { newPass = it }, label = { Text(strings.newPassword) })
                TextField(value = confirm, onValueChange = { confirm = it }, label = { Text(strings.confirmPassword) })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (loading) CircularProgressIndicator()
            }
        },
        confirmButton = {
            Button(onClick = {
                // validation
                error = null
                if (newPass.length < 8) { error = strings.passwordMinLength; return@Button }
                if (newPass != confirm) { error = strings.passwordsMismatch; return@Button }

                scope.launch {
                    loading = true
                    try {
                        try {
                            val result = ServiceLocator.userService.changePassword(newPass)
                            if (result) {
                                onSuccess()
                            } else {
                                val apiErr = try { ServiceLocator.userService.getLastApiError() } catch (_: Throwable) { null }
                                error = if (!apiErr.isNullOrBlank()) "${strings.changePasswordFailed}: $apiErr" else strings.changePasswordFailed
                            }
                        } catch (ex: Throwable) {
                            val causeMsg = generateSequence(ex.cause) { it.cause }
                                .mapNotNull { it.message }
                                .firstOrNull()
                            val msg = ex.message ?: ex.toString()
                            error = "${strings.changePasswordFailed}: ${causeMsg ?: msg}"
                        }
                    } finally {
                        loading = false
                    }
                }
            }) { Text(strings.confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePersonalDataDialog(
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
    // nationality stored as ISO 3166-1 numeric ID; display Czech name
    var nationalityId by remember(initialNationality) { mutableStateOf(initialNationality) }
    var street by remember(initialStreet) { mutableStateOf(initialStreet) }
    var city by remember(initialCity) { mutableStateOf(initialCity) }
    var postal by remember(initialPostal) { mutableStateOf(initialPostal) }
    var region by remember(initialRegion) { mutableStateOf(initialRegion) }
    var district by remember(initialDistrict) { mutableStateOf(initialDistrict) }
    var conscription by remember(initialConscription) { mutableStateOf(initialConscription) }
    var orientation by remember(initialOrientation) { mutableStateOf(initialOrientation) }
    var phone by remember(initialPhone) { mutableStateOf(initialPhone) }
    var mobile by remember(initialMobile) { mutableStateOf(initialMobile) }
    // Birth date handling: keep ISO value for API and user-friendly display
    fun parseToLocalDate(s: String?): LocalDate? {
        if (s.isNullOrBlank()) return null
        try {
            return LocalDate.parse(s)
        } catch (_: Exception) {}
        try {
            val odt = OffsetDateTime.parse(s)
            return odt.toLocalDate()
        } catch (_: Exception) {}
        val m = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(s)
        if (m != null) return LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        return null
    }

    val initialLocal = parseToLocalDate(initialBirthDate)
    var birthIso by remember(initialBirthDate) { mutableStateOf(initialLocal?.toString() ?: initialBirthDate) }
    fun formatDisplayDate(iso: String?): String {
        val ld = parseToLocalDate(iso) ?: return iso ?: ""
        return try { ld.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) } catch (_: Exception) { ld.toString() }
    }
    var birthDisplay by remember(birthIso) { mutableStateOf(formatDisplayDate(birthIso)) }

    // Gender: display localized labels but send MAN/WOMAN/UNSPECIFIED to API
    val strings = AppStrings.current
    fun isoToLabel(g: String?): String = when (g?.uppercase()) {
        "MAN" -> strings.genderMale
        "WOMAN" -> strings.genderFemale
        else -> strings.genderUnspecified
    }
    fun labelToIso(label: String): String = when (label) {
        strings.genderMale -> "MAN"
        strings.genderFemale -> "WOMAN"
        else -> "UNSPECIFIED"
    }

    var birth by remember { mutableStateOf(birthIso) }
    var showDatePicker by remember { mutableStateOf(false) }
    val initialMillis = parseToLocalDate(birth)?.atStartOfDay()?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    var gender by remember(initialGender) { mutableStateOf(isoToLabel(initialGender)) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(strings.editPersonalData) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                TextField(value = first, onValueChange = { first = it }, label = { Text(strings.firstName) }, singleLine = true)
                TextField(value = last, onValueChange = { last = it }, label = { Text(strings.lastName) }, singleLine = true)
                TextField(value = prefix, onValueChange = { prefix = it }, label = { Text(strings.prefixTitle) }, singleLine = true)
                TextField(value = suffix, onValueChange = { suffix = it }, label = { Text(strings.suffixTitle) }, singleLine = true)
                TextField(value = bio, onValueChange = { bio = it }, label = { Text(strings.aboutMe) })
                TextField(value = email, onValueChange = { email = it }, label = { Text(strings.email) }, singleLine = true)
                TextField(value = csts, onValueChange = { csts = it }, label = { Text(strings.cstsId) }, singleLine = true)
                TextField(value = wdsf, onValueChange = { wdsf = it }, label = { Text(strings.wdsfId) }, singleLine = true)
                TextField(value = nid, onValueChange = { nid = it }, label = { Text(strings.personalId) }, singleLine = true)
                // Nationality dropdown (ISO 3166-1 numeric ID stored, localized name displayed; read-only, select only)
                val nationalityOptions = NationalityHelper.getNationalityOptions(AppStrings.currentLanguage)
                var nationalityExpanded by remember { mutableStateOf(false) }
                val nationalityDisplay = nationalityOptions.find { it.first == nationalityId }?.second ?: nationalityId
                ExposedDropdownMenuBox(
                    expanded = nationalityExpanded,
                    onExpandedChange = { nationalityExpanded = it }
                ) {
                    TextField(
                        value = nationalityDisplay,
                        onValueChange = {},
                        label = { Text(strings.nationality) },
                        singleLine = true,
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = nationalityExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = nationalityExpanded,
                        onDismissRequest = { nationalityExpanded = false }
                    ) {
                        nationalityOptions.forEach { (id, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    nationalityId = id
                                    nationalityExpanded = false
                                }
                            )
                        }
                    }
                }
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text(strings.contacts, style = MaterialTheme.typography.labelSmall)
                TextField(value = phone, onValueChange = { phone = it }, label = { Text(strings.phone) }, singleLine = true)
                TextField(value = mobile, onValueChange = { mobile = it }, label = { Text(strings.mobile) }, singleLine = true)
                            val focusManager = LocalFocusManager.current
                            // Date picker field (read-only) with explicit IconButton to open Material3 DatePickerDialog
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextField(
                                    value = birthDisplay,
                                    onValueChange = { /* read-only */ },
                                    label = { Text(strings.birthDate) },
                                    singleLine = true,
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                )
                                IconButton(onClick = {
                                    println("[ProfileDialogs] Date icon clicked")
                                    focusManager.clearFocus()
                                    showDatePicker = true
                                }) {
                                    Icon(imageVector = Icons.Default.CalendarToday, contentDescription = strings.selectDate)
                                }
                            }
                            if (showDatePicker) {
                                DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
                                    Button(onClick = {
                                        val selMillis = datePickerState.selectedDateMillis
                                        if (selMillis != null) {
                                            val sel = Instant.ofEpochMilli(selMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                                            birthIso = sel.toString()
                                            birth = birthIso
                                            birthDisplay = formatDisplayDate(birthIso)
                                        }
                                        showDatePicker = false
                                    }) { Text(strings.ok) }
                                }) {
                                    DatePicker(state = datePickerState)
                                }
                            }

                // Gender selection using ExposedDropdownMenuBox
                var genderExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = genderExpanded, onExpandedChange = { genderExpanded = it }) {
                    TextField(
                        value = gender,
                        onValueChange = { /* no-op */ },
                        label = { Text(strings.gender) },
                        singleLine = true,
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    DropdownMenu(expanded = genderExpanded, onDismissRequest = { genderExpanded = false }) {
                        listOf(strings.genderMale, strings.genderFemale, strings.genderUnspecified).forEach { opt ->
                            DropdownMenuItem(text = { Text(opt) }, onClick = {
                                gender = opt
                                genderExpanded = false
                            })
                        }
                    }
                }
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text(strings.address, style = MaterialTheme.typography.labelSmall)
                TextField(value = street, onValueChange = { street = it }, label = { Text(strings.street) }, singleLine = true)
                TextField(value = city, onValueChange = { city = it }, label = { Text(strings.city) }, singleLine = true)
                TextField(value = postal, onValueChange = { postal = it }, label = { Text(strings.zip) }, singleLine = true)
                TextField(value = region, onValueChange = { region = it }, label = { Text(strings.region) }, singleLine = true)
                TextField(value = district, onValueChange = { district = it }, label = { Text(strings.district) }, singleLine = true)
                TextField(value = conscription, onValueChange = { conscription = it }, label = { Text(strings.conscriptionNumber) }, singleLine = true)
                TextField(value = orientation, onValueChange = { orientation = it }, label = { Text(strings.orientationNumber) }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (saving) androidx.compose.material3.CircularProgressIndicator()
            }
        },
        confirmButton = {
            Button(onClick = {
                error = null
                if (email.isNotBlank() && !email.contains("@")) { error = strings.invalidEmail; return@Button }
                scope.launch {
                    saving = true
                    try {
                        val pid = try { ServiceLocator.userService.getCachedPersonId() } catch (_: Throwable) { null }
                        if (pid.isNullOrBlank()) {
                            error = strings.cannotDetermineUserId
                        } else {
                            val genderIso = labelToIso(gender)
                            val req = com.tkolymp.shared.user.PersonUpdateRequest(
                                bio = bio,
                                cstsId = csts,
                                email = email,
                                firstName = first,
                                lastName = last,
                                nationalIdNumber = nid,
                                nationality = nationalityId,
                                phone = phone,
                                wdsfId = wdsf,
                                prefixTitle = prefix,
                                suffixTitle = suffix,
                                gender = genderIso,
                                birthDateSet = true,
                                birthDate = birthIso,
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
                            try { println("[ProfileScreen] Sending PersonUpdateRequest: $req") } catch (_: Throwable) {}
                            val ok = try { ServiceLocator.userService.updatePerson(pid, req) } catch (ex: Throwable) { false }
                            if (ok) {
                                onSave(
                                    first.trim(), last.trim(), bio.trim(), email.trim(), prefix.trim(), suffix.trim(), csts.trim(), wdsf.trim(), nid.trim(), nationalityId.trim(),
                                    street.trim(), city.trim(), postal.trim(), region.trim(), district.trim(), conscription.trim(), orientation.trim(),
                                    phone.trim(), mobile.trim(), birth.trim(), gender.trim()
                                )
                                try { ServiceLocator.userService.fetchAndStorePersonDetails(pid) } catch (_: Throwable) { }
                                try { ServiceLocator.authService.initialize() } catch (_: Throwable) { }
                                try { android.widget.Toast.makeText(ctx, strings.dataSaved, android.widget.Toast.LENGTH_SHORT).show() } catch (_: Throwable) { }
                                onDismiss()
                            } else {
                                val apiErr = try { ServiceLocator.userService.getLastApiError() } catch (_: Throwable) { null }
                                error = "${strings.saveFailed}: ${apiErr ?: "neznámá chyba"}"
                            }
                        }
                    } catch (ex: Throwable) {
                        error = ex.message ?: strings.saveFailed
                    } finally {
                        saving = false
                    }
                }
            }) { Text(strings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } }
    )
}
