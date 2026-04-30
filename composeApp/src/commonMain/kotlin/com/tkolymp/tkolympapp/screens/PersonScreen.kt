package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.people.PersonDetails
import com.tkolymp.shared.utils.formatShortDate
import com.tkolymp.shared.utils.parseToLocal
import com.tkolymp.shared.viewmodels.PersonViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.parseColorOrDefault
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(personId: String, onBack: () -> Unit = {}, onOpenCouple: (String) -> Unit = {}) {
    val viewModel = viewModel<PersonViewModel>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(AppStrings.current.profile.person) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back) }
        })
    }) { padding ->
        val scope = rememberCoroutineScope()

        SwipeToReload(isRefreshing = state.isLoading, onRefresh = { scope.launch { viewModel.loadPerson(personId) } }, modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {

            state.error?.let { Text("${AppStrings.current.registration.errorPrefix} $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }

            val p = state.person as? PersonDetails
            if (p == null) {
                Text(AppStrings.current.profile.personNotFound, modifier = Modifier.padding(8.dp))
                return@Column
            }

            // Header (name)
            val baseName = listOf(p.prefixTitle, p.firstName, p.lastName).filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
            val fullName = if (!p.suffixTitle.isNullOrBlank()) "$baseName, ${p.suffixTitle}" else baseName
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fullName.ifBlank { p.id }, style = MaterialTheme.typography.headlineSmall)
                if (p.isTrainer == true) {
                    Text(AppStrings.current.profile.trainer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }

            // Basic Info - like EventScreen (Row with two Cards, then Card for email)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Cake,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val bdText = p.birthDate?.let { formatDateStringSmall(it) ?: it } ?: "—"
                        Text(bdText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val genderIcon = when (p.gender) {
                            "MAN" -> Icons.Default.Male
                            "WOMAN" -> Icons.Default.Female
                            else -> Icons.AutoMirrored.Filled.HelpOutline
                        }
                        Icon(
                            genderIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val genderLabel = when (p.gender) {
                            "MAN" -> AppStrings.current.gender.genderMale
                            "WOMAN" -> AppStrings.current.gender.genderFemale
                            "UNSPECIFIED" -> AppStrings.current.gender.genderUnspecified
                            null, "" -> "—"
                            else -> p.gender!!.lowercase()
                        }
                        Text(genderLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (!p.email.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(p.email ?: "—", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Bio card (if present and non-empty)
            p.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(AppStrings.current.profile.aboutMe, style = MaterialTheme.typography.labelLarge)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        Text(bio, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Tréninkové skupiny (stacked)
            val visibleGroups = p.cohortMembershipsList.filter { it.cohort?.isVisible != false }
            if (visibleGroups.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(AppStrings.current.otherScreen.trainingGroups, style = MaterialTheme.typography.labelLarge)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        Column {
                            visibleGroups.forEach { mem ->
                                    val c = mem.cohort ?: return@forEach
                                    val color = try { parseColorOrDefault(c.colorRgb) } catch (_: Exception) { Color.Gray }
                                    Card(modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(6.dp)) {
                                            val since = mem.since
                                            val until = mem.until
                                            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(6.dp)
                                                        .fillMaxHeight()
                                                        .background(color, RoundedCornerShape(6.dp))
                                                )

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = (c.name ?: c.id ?: "-"),
                                                        style = MaterialTheme.typography.titleSmall,
                                                    )
                                                    if (!since.isNullOrBlank()) {
                                                        val sinceLabel = formatDateStringSmall(since) ?: since
                                                        Text("${AppStrings.current.profile.dateFrom}: $sinceLabel", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                    if (!until.isNullOrBlank()) {
                                                        val untilLabel = formatDateStringSmall(until) ?: until
                                                        Text("${AppStrings.current.profile.dateTo}: $untilLabel", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
            }

            // Active couples card
            if (p.activeCouplesList.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(AppStrings.current.profile.activeCouple, style = MaterialTheme.typography.labelLarge)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        p.activeCouplesList.forEach { c ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 0.dp) // Remove extra vertical padding
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val man = c.man
                                        val woman = c.woman
                                        val manName = listOfNotNull(man?.firstName, man?.lastName).joinToString(" ").trim()
                                        val womanName = listOfNotNull(woman?.firstName, woman?.lastName).joinToString(" ").trim()
                                        Text(listOfNotNull(manName.takeIf { it.isNotBlank() }, womanName.takeIf { it.isNotBlank() }).joinToString(" - "), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    // hide internal couple id in UI
                                }
                            }
                        }
                    }
                }
            }
            // (duplicate UI removed)
                }
            }
        }
    }


private fun formatDateStringSmall(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val s = raw.trim()
    val datePrefix = Regex("\\d{4}-\\d{2}-\\d{2}").find(s)?.value
    val ld = if (datePrefix != null) {
        try { LocalDate.parse(datePrefix) } catch (_: Exception) { null }
    } else {
        parseToLocal(s)?.date
    } ?: return null
    return formatShortDate(ld)
}
