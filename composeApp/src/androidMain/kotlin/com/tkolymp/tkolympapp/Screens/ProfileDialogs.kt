package com.tkolymp.tkolympapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.launch

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
                TextField(value = nid, onValueChange = { nid = it }, label = { Text("Rodné číslo / národní ID") }, singleLine = true)
                TextField(value = nationality, onValueChange = { nationality = it }, label = { Text("Národnost") }, singleLine = true)
                androidx.compose.material3.Divider(modifier = Modifier.padding(vertical = 6.dp))
                Text("Kontakty", style = MaterialTheme.typography.labelSmall)
                TextField(value = phone, onValueChange = { phone = it }, label = { Text("Telefon") }, singleLine = true)
                TextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Mobil") }, singleLine = true)
                TextField(value = birth, onValueChange = { birth = it }, label = { Text("Datum narození") }, singleLine = true)
                TextField(value = gender, onValueChange = { gender = it }, label = { Text("Pohlaví") }, singleLine = true)
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
                            try { println("[ProfileScreen] Sending PersonUpdateRequest: $req") } catch (_: Throwable) {}
                            val ok = try { ServiceLocator.userService.updatePerson(pid, req) } catch (ex: Throwable) { false }
                            if (ok) {
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
