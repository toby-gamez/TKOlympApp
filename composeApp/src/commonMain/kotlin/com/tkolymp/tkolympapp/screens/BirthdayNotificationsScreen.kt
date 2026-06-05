package com.tkolymp.tkolympapp.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.people.Person
import com.tkolymp.shared.viewmodels.BirthdayNotificationViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.util.normalizeForSearch
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayNotificationsScreen(onBack: () -> Unit = {}) {
    val viewModel = viewModel<BirthdayNotificationViewModel>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    val d = state.draft

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.current.notifications.birthdayNotificationsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                    }
                }
            )
        },
        snackbarHost = {},
        bottomBar = {
            Button(
                onClick = {
                    viewModel.save()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(AppStrings.current.commonActions.save)
            }
        }
    ) { padding ->
        SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { viewModel.load() },
            modifier = Modifier.padding(padding)
        ) {
            if (state.isLoading) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    // ── Enable toggle ────────────────────────────────────────────
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                AppStrings.current.notifications.birthdayNotificationsEnabled,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = d.enabled,
                                onCheckedChange = { viewModel.updateDraft(d.copy(enabled = it)) }
                            )
                        }
                        HorizontalDivider()
                    }

                    // ── Enabled-section (all conditional content in one animated item) ──
                    item {
                        AnimatedVisibility(
                            visible = d.enabled,
                            enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                            exit = fadeOut(tween(160)) + shrinkVertically(tween(160))
                        ) {
                            Column {
                                // ── Všichni (shortcut) ───────────────────────────────
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.updateDraft(d.copy(notifyAll = !d.notifyAll)) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = d.notifyAll,
                                        onCheckedChange = { viewModel.updateDraft(d.copy(notifyAll = it)) }
                                    )
                                    Text(
                                        AppStrings.current.notifications.birthdayNotifyAllLabel,
                                        modifier = Modifier.padding(start = 4.dp),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                HorizontalDivider()

                                // ── Granular options (hidden when notifyAll) ──────────
                                AnimatedVisibility(
                                    visible = !d.notifyAll,
                                    enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                                    exit = fadeOut(tween(140)) + shrinkVertically(tween(140))
                                ) {
                                    Column {
                                        // ── Skupiny ──────────────────────────────────
                                        if (state.availableGroups.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                AppStrings.current.notifications.birthdaySelectGroups,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState())
                                            ) {
                                                state.availableGroups.forEach { cohort ->
                                                    val cid = cohort.id ?: return@forEach
                                                    val selected = d.selectedCohortIds.contains(cid)
                                                    FilterChip(
                                                        selected = selected,
                                                        onClick = {
                                                            val ids = if (selected)
                                                                d.selectedCohortIds - cid
                                                            else
                                                                d.selectedCohortIds + cid
                                                            viewModel.updateDraft(d.copy(selectedCohortIds = ids, notifyAll = false))
                                                        },
                                                        label = { Text(cohort.name ?: cid) }
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            HorizontalDivider()
                                        }

                                        // ── Trenéři ──────────────────────────────────
                                        if (state.trainerPersonIds.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            val allTrainersSelected = state.trainerPersonIds.isNotEmpty() &&
                                                state.trainerPersonIds.all { it in d.selectedPersonIds }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        val newIds = if (allTrainersSelected)
                                                            d.selectedPersonIds - state.trainerPersonIds
                                                        else
                                                            d.selectedPersonIds + state.trainerPersonIds
                                                        viewModel.updateDraft(d.copy(selectedPersonIds = newIds, notifyTrainers = false, notifyAll = false))
                                                    }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = allTrainersSelected,
                                                    onCheckedChange = { isChecked ->
                                                        val newIds = if (isChecked)
                                                            d.selectedPersonIds + state.trainerPersonIds
                                                        else
                                                            d.selectedPersonIds - state.trainerPersonIds
                                                        viewModel.updateDraft(d.copy(selectedPersonIds = newIds, notifyTrainers = false, notifyAll = false))
                                                    }
                                                )
                                                Text(
                                                    AppStrings.current.notifications.birthdayFilterTrainers,
                                                    modifier = Modifier.padding(start = 4.dp),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            HorizontalDivider()
                                        }

                                        // ── Konkrétní sportovci ───────────────────────
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            AppStrings.current.notifications.birthdaySelectPeople,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        var searchQuery by remember { mutableStateOf("") }
                                        OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            placeholder = { Text(AppStrings.current.people.searchByName) }
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val displayed = remember(state.nonTrainerPeople, searchQuery) {
                                            if (searchQuery.isBlank()) state.nonTrainerPeople
                                            else {
                                                val q = normalizeForSearch(searchQuery.trim())
                                                state.nonTrainerPeople.filter { p ->
                                                    listOfNotNull(p.firstName, p.lastName).any {
                                                        normalizeForSearch(it).contains(q)
                                                    }
                                                }
                                            }
                                        }
                                        displayed.forEach { person ->
                                            val checked = d.selectedPersonIds.contains(person.id)
                                            BirthdayPersonRow(
                                                person = person,
                                                checked = checked,
                                                onCheckedChange = { isChecked ->
                                                    val ids = if (isChecked)
                                                        d.selectedPersonIds + person.id
                                                    else
                                                        d.selectedPersonIds - person.id
                                                    viewModel.updateDraft(d.copy(selectedPersonIds = ids, notifyAll = false))
                                                }
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        HorizontalDivider()
                                    }
                                }

                                // ── WHEN section ─────────────────────────────────────
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    AppStrings.current.notifications.birthdayWhenLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val daysOptions = listOf(
                                    0 to AppStrings.current.notifications.birthdayDayOfLabel,
                                    1 to AppStrings.current.notifications.birthdayDayBeforeLabel,
                                    2 to "2 ${AppStrings.current.notifications.birthdayDaysBeforeLabel}",
                                    3 to "3 ${AppStrings.current.notifications.birthdayDaysBeforeLabel}",
                                    7 to "7 ${AppStrings.current.notifications.birthdayDaysBeforeLabel}"
                                )
                                daysOptions.forEach { (days, label) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.updateDraft(d.copy(daysBefore = days)) }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = d.daysBefore == days,
                                            onClick = { viewModel.updateDraft(d.copy(daysBefore = days)) }
                                        )
                                        Text(label, modifier = Modifier.padding(start = 4.dp))
                                    }
                                }

                                // ── HOUR section ─────────────────────────────────────
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "${AppStrings.current.notifications.birthdayNotificationHourLabel}: ${d.notificationHour}:00",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = d.notificationHour.toFloat(),
                                    onValueChange = { viewModel.updateDraft(d.copy(notificationHour = it.toInt())) },
                                    valueRange = 6f..22f,
                                    steps = 15,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BirthdayPersonRow(
    person: Person,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val name = buildString {
        listOfNotNull(person.prefixTitle, person.firstName, person.lastName)
            .filter { it.isNotBlank() }
            .joinTo(this, " ")
        if (!person.suffixTitle.isNullOrBlank()) append(", ${person.suffixTitle}")
    }.ifBlank { person.id }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(name, modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
