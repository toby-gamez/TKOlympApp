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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


// using Material3 DatePickerDialog

// ISO 3166-1 numeric → Czech country name (Czech Republic first, rest Czech-alphabetically)
internal val NATIONALITY_OPTIONS: List<Pair<String, String>> = listOf(
    "203" to "Česká republika",
    "703" to "Slovensko",
    "40"  to "Rakousko",
    "276" to "Německo",
    "348" to "Maďarsko",
    "4"   to "Afghánistán",
    "8"   to "Albánie",
    "12"  to "Alžírsko",
    "20"  to "Andorra",
    "24"  to "Angola",
    "28"  to "Antigua a Barbuda",
    "32"  to "Argentina",
    "51"  to "Arménie",
    "36"  to "Austrálie",
    "31"  to "Ázerbájdžán",
    "44"  to "Bahamy",
    "48"  to "Bahrajn",
    "50"  to "Bangladéš",
    "52"  to "Barbados",
    "56"  to "Belgie",
    "84"  to "Belize",
    "204" to "Benin",
    "112" to "Bělorusko",
    "64"  to "Bhútán",
    "68"  to "Bolívie",
    "70"  to "Bosna a Hercegovina",
    "72"  to "Botswana",
    "76"  to "Brazílie",
    "96"  to "Brunej",
    "100" to "Bulharsko",
    "854" to "Burkina Faso",
    "108" to "Burundi",
    "148" to "Čad",
    "499" to "Černá Hora",
    "152" to "Chile",
    "156" to "Čína",
    "191" to "Chorvatsko",
    "208" to "Dánsko",
    "180" to "Demokratická republika Kongo",
    "212" to "Dominika",
    "214" to "Dominikánská republika",
    "262" to "Džibutsko",
    "218" to "Ekvádor",
    "818" to "Egypt",
    "222" to "Salvador",
    "232" to "Eritrea",
    "233" to "Estonsko",
    "231" to "Etiopie",
    "242" to "Fidži",
    "608" to "Filipíny",
    "246" to "Finsko",
    "250" to "Francie",
    "266" to "Gabon",
    "270" to "Gambie",
    "288" to "Ghana",
    "308" to "Grenada",
    "300" to "Řecko",
    "268" to "Gruzie",
    "320" to "Guatemala",
    "324" to "Guinea",
    "624" to "Guinea-Bissau",
    "328" to "Guyana",
    "332" to "Haiti",
    "340" to "Honduras",
    "356" to "Indie",
    "360" to "Indonésie",
    "368" to "Irák",
    "364" to "Írán",
    "372" to "Irsko",
    "352" to "Island",
    "376" to "Izrael",
    "380" to "Itálie",
    "388" to "Jamajka",
    "392" to "Japonsko",
    "887" to "Jemen",
    "710" to "Jihoafrická republika",
    "400" to "Jordánsko",
    "410" to "Jižní Korea",
    "132" to "Kapverdy",
    "116" to "Kambodža",
    "120" to "Kamerun",
    "124" to "Kanada",
    "398" to "Kazachstán",
    "404" to "Keňa",
    "296" to "Kiribati",
    "170" to "Kolumbie",
    "174" to "Komory",
    "178" to "Kongo",
    "192" to "Kuba",
    "414" to "Kuvajt",
    "196" to "Kypr",
    "417" to "Kyrgyzstán",
    "418" to "Laos",
    "426" to "Lesotho",
    "422" to "Libanon",
    "430" to "Libérie",
    "434" to "Libye",
    "438" to "Lichtenštejnsko",
    "440" to "Litva",
    "428" to "Lotyšsko",
    "442" to "Lucembursko",
    "450" to "Madagaskar",
    "458" to "Malajsie",
    "454" to "Malawi",
    "462" to "Maledivy",
    "466" to "Mali",
    "470" to "Malta",
    "504" to "Maroko",
    "584" to "Marshallovy ostrovy",
    "478" to "Mauritánie",
    "480" to "Mauricius",
    "484" to "Mexiko",
    "583" to "Mikronésie",
    "498" to "Moldavsko",
    "492" to "Monako",
    "496" to "Mongolsko",
    "508" to "Mosambik",
    "807" to "Severní Makedonie",
    "516" to "Namibie",
    "520" to "Nauru",
    "524" to "Nepál",
    "562" to "Niger",
    "566" to "Nigérie",
    "558" to "Nikaragua",
    "528" to "Nizozemsko",
    "578" to "Norsko",
    "554" to "Nový Zéland",
    "512" to "Omán",
    "586" to "Pákistán",
    "585" to "Palau",
    "591" to "Panama",
    "598" to "Papua Nová Guinea",
    "600" to "Paraguay",
    "604" to "Peru",
    "384" to "Pobřeží slonoviny",
    "616" to "Polsko",
    "620" to "Portugalsko",
    "634" to "Katar",
    "642" to "Rumunsko",
    "643" to "Rusko",
    "646" to "Rwanda",
    "674" to "San Marino",
    "682" to "Saúdská Arábie",
    "686" to "Senegal",
    "690" to "Seychely",
    "694" to "Sierra Leone",
    "702" to "Singapur",
    "408" to "Severní Korea",
    "705" to "Slovinsko",
    "706" to "Somálsko",
    "688" to "Srbsko",
    "144" to "Srí Lanka",
    "140" to "Středoafrická republika",
    "729" to "Súdán",
    "597" to "Surinam",
    "748" to "Svazijsko",
    "662" to "Svatá Lucie",
    "659" to "Svatý Kryštof a Nevis",
    "678" to "Svatý Tomáš a Princův ostrov",
    "670" to "Svatý Vincenc a Grenadiny",
    "760" to "Sýrie",
    "90"  to "Šalamounovy ostrovy",
    "724" to "Španělsko",
    "752" to "Švédsko",
    "756" to "Švýcarsko",
    "762" to "Tádžikistán",
    "158" to "Tchaj-wan",
    "764" to "Thajsko",
    "626" to "Východní Timor (Timor-Leste)",
    "768" to "Togo",
    "776" to "Tonga",
    "780" to "Trinidad a Tobago",
    "788" to "Tunisko",
    "792" to "Turecko",
    "795" to "Turkmenistán",
    "798" to "Tuvalu",
    "800" to "Uganda",
    "804" to "Ukrajina",
    "858" to "Uruguay",
    "860" to "Uzbekistán",
    "548" to "Vanuatu",
    "336" to "Vatikán",
    "862" to "Venezuela",
    "704" to "Vietnam",
    "894" to "Zambie",
    "716" to "Zimbabwe",
    "784" to "Spojené arabské emiráty",
    "826" to "Spojené království",
    "840" to "Spojené státy americké",
)

@Composable
fun ChangePasswordDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
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
                if (loading) CircularProgressIndicator()
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
                                val apiErr = try { ServiceLocator.userService.getLastApiError() } catch (_: Throwable) { null }
                                error = if (!apiErr.isNullOrBlank()) "Změna hesla selhala: $apiErr" else "Změna hesla selhala"
                            }
                        } catch (ex: Throwable) {
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

    // Gender selection: display Czech labels but send MAN/WOMAN/UNSPECIFIED
    val genderMap = mapOf("muž" to "MAN", "žena" to "WOMAN", "nespecifikováno" to "UNSPECIFIED")
    fun isoToLabel(g: String?): String {
        if (g.isNullOrBlank()) return "nespecifikováno"
        val up = g.uppercase()
        return when (up) {
            "MAN" -> "muž"
            "WOMAN" -> "žena"
            "UNSPECIFIED" -> "nespecifikováno"
            else -> g
        }
    }
    fun labelToIso(label: String): String = genderMap[label] ?: label.uppercase()

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
                TextField(value = nid, onValueChange = { nid = it }, label = { Text("Rodné číslo") }, singleLine = true)
                // Nationality dropdown (ISO 3166-1 numeric ID stored, Czech name displayed; read-only, select only)
                var nationalityExpanded by remember { mutableStateOf(false) }
                val nationalityDisplay = NATIONALITY_OPTIONS.find { it.first == nationalityId }?.second ?: nationalityId
                ExposedDropdownMenuBox(
                    expanded = nationalityExpanded,
                    onExpandedChange = { nationalityExpanded = it }
                ) {
                    TextField(
                        value = nationalityDisplay,
                        onValueChange = {},
                        label = { Text("Národnost") },
                        singleLine = true,
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = nationalityExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = nationalityExpanded,
                        onDismissRequest = { nationalityExpanded = false }
                    ) {
                        NATIONALITY_OPTIONS.forEach { (id, name) ->
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
                androidx.compose.material3.Divider(modifier = Modifier.padding(vertical = 6.dp))
                Text("Kontakty", style = MaterialTheme.typography.labelSmall)
                TextField(value = phone, onValueChange = { phone = it }, label = { Text("Telefon") }, singleLine = true)
                TextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Mobil") }, singleLine = true)
                            val focusManager = LocalFocusManager.current
                            // Date picker field (read-only) with explicit IconButton to open Material3 DatePickerDialog
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextField(
                                    value = birthDisplay,
                                    onValueChange = { /* read-only */ },
                                    label = { Text("Datum narození") },
                                    singleLine = true,
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                )
                                IconButton(onClick = {
                                    println("[ProfileDialogs] Date icon clicked")
                                    focusManager.clearFocus()
                                    showDatePicker = true
                                }) {
                                    Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Vybrat datum")
                                }
                            }
                            if (showDatePicker) {
                                DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
                                    TextButton(onClick = {
                                        val selMillis = datePickerState.selectedDateMillis
                                        if (selMillis != null) {
                                            val sel = Instant.ofEpochMilli(selMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                                            birthIso = sel.toString()
                                            birth = birthIso
                                            birthDisplay = formatDisplayDate(birthIso)
                                        }
                                        showDatePicker = false
                                    }) { Text("OK") }
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
                        label = { Text("Pohlaví") },
                        singleLine = true,
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    DropdownMenu(expanded = genderExpanded, onDismissRequest = { genderExpanded = false }) {
                        listOf("muž", "žena", "nespecifikováno").forEach { opt ->
                            DropdownMenuItem(text = { Text(opt) }, onClick = {
                                println("[ProfileDialogs] Gender selected: $opt")
                                gender = opt
                                genderExpanded = false
                            })
                        }
                    }
                }
                androidx.compose.material3.Divider(modifier = Modifier.padding(vertical = 6.dp))
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
                        val pid = try { ServiceLocator.userService.getCachedPersonId() } catch (_: Throwable) { null }
                        if (pid.isNullOrBlank()) {
                            error = "Nelze určit ID uživatele"
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
