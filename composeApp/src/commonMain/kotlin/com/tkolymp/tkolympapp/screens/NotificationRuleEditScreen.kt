package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tkolymp.tkolympapp.util.settleIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.Logger
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.management.ManagementOption
import com.tkolymp.shared.notification.FilterType
import com.tkolymp.shared.notification.NotificationRule
import com.tkolymp.shared.viewmodels.NotificationsSettingsViewModel
import com.tkolymp.tkolympapp.components.AppTopBar
import com.tkolymp.tkolympapp.components.Button
import com.tkolymp.tkolympapp.components.OptionChips
import com.tkolymp.tkolympapp.components.OutlinedTextField
import com.tkolymp.tkolympapp.components.QuantityInput
import com.tkolymp.tkolympapp.components.SectionLabel
import com.tkolymp.tkolympapp.components.cardLineBorder
import com.tkolymp.tkolympapp.components.surfaceCardColors
import com.tkolymp.tkolympapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Full-screen editor for one notification rule; [ruleId] == null creates a new rule. */
@Composable
fun NotificationRuleEditScreen(ruleId: String?, onBack: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val strings = AppStrings.current
    val viewModel = viewModel<NotificationsSettingsViewModel>()
    val vmState by viewModel.state.collectAsStateWithLifecycle()
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            viewModel.loadSettings()
            viewModel.loadClubData()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w("NotificationRuleEdit", "load failed: ${e.message}")
        }
        loaded = true
    }

    val existing = remember(vmState.rules, ruleId) { vmState.rules.find { it.id == ruleId } }
    // Seed the form once the rule is available; later state updates must not overwrite edits.
    var seeded by remember { mutableStateOf(false) }
    var ruleName by remember { mutableStateOf("") }
    var selType by remember { mutableStateOf(FilterType.BY_LOCATION) }
    var timeValue by remember { mutableStateOf(60) }
    var timeUnit by remember { mutableStateOf("min") }
    val selectedLocations = remember { mutableStateListOf<String>() }
    val selectedTrainers = remember { mutableStateListOf<String>() }
    val selectedTypes = remember { mutableStateListOf<String>() }

    LaunchedEffect(loaded, existing, vmState.availableTrainers) {
        if (!loaded || seeded) return@LaunchedEffect
        seeded = true
        existing?.let { r ->
            ruleName = r.name
            selType = if (r.filterType == FilterType.ALL) FilterType.BY_LOCATION else r.filterType
            val first = r.timesBeforeMinutes.firstOrNull() ?: 60
            if (first >= 60 && first % 60 == 0) { timeValue = first / 60; timeUnit = "h" } else { timeValue = first; timeUnit = "min" }
            selectedLocations.addAll(r.locations)
            selectedTypes.addAll(r.types)
            r.trainers.forEach { s ->
                if (s.contains("::")) selectedTrainers.add(s)
                else {
                    val matched = vmState.availableTrainers.find { it.second == s }
                    selectedTrainers.add(if (matched != null) "${matched.first}::$s" else s)
                }
            }
        }
    }

    val typeLabels = listOf(
        "CAMP" to strings.events.eventTypeCamp,
        "LESSON" to strings.events.eventTypeLesson,
        "GROUP" to strings.events.eventTypeGroup,
        "RESERVATION" to strings.events.eventTypeReservation,
        "HOLIDAY" to strings.events.eventTypeHoliday,
        "PERSONAL_TRAINING" to strings.notifications.eventTypePersonalTraining
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (ruleId == null) strings.notifications.addRule else strings.notifications.editRuleTitle,
                onBack = onBack
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    val minutes = if (timeUnit == "h") timeValue * 60 else timeValue
                    val locations = selectedLocations.toList()
                    val trainers = selectedTrainers.toList()
                    val types = selectedTypes.toList()
                    val rule = existing?.copy(
                        name = ruleName, filterType = selType, locations = locations, trainers = trainers,
                        types = types, timesBeforeMinutes = listOf(minutes)
                    ) ?: NotificationRule(
                        id = kotlin.time.Clock.System.now().toEpochMilliseconds().toString(),
                        name = ruleName, enabled = true, filterType = selType,
                        locations = locations, trainers = trainers, types = types,
                        timesBeforeMinutes = listOf(minutes)
                    )
                    scope.launch {
                        viewModel.addOrUpdateRule(rule)
                        onBack()
                    }
                },
                // Saving rewrites the whole rule list, so wait until the current rules are loaded.
                enabled = seeded && (ruleId == null || existing != null),
                modifier = Modifier.fillMaxWidth().padding(Spacing.default)
            ) { Text(strings.commonActions.save) }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .settleIn()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.default),
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = surfaceCardColors(),
                border = cardLineBorder()
            ) {
                Column(modifier = Modifier.padding(Spacing.default), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                    OutlinedTextField(
                        value = ruleName,
                        onValueChange = { ruleName = it },
                        label = { Text(strings.notifications.ruleNameLabel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Re-keyed on the seeded value so QuantityInput (which keeps its own state) shows the loaded rule.
                    androidx.compose.runtime.key(seeded) {
                        QuantityInput(
                            value = timeValue,
                            onValueChange = { v, u -> timeValue = v; timeUnit = u },
                            units = listOf("min", "h"),
                            defaultUnit = timeUnit,
                            label = strings.notifications.timeAheadLabel,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            SectionLabel(strings.notifications.filterTypeLabel, horizontalPadding = Spacing.none)
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                listOf(
                    FilterType.BY_LOCATION to strings.filters.filterPlace,
                    FilterType.BY_TRAINER to strings.filters.filterTrainer,
                    FilterType.BY_TYPE to strings.filters.filterType
                ).forEach { (t, label) ->
                    FilterChip(selected = selType == t, onClick = { selType = t }, label = { Text(label) })
                }
            }

            SectionLabel(strings.notifications.orPickFromValues, horizontalPadding = Spacing.none)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = surfaceCardColors(),
                border = cardLineBorder()
            ) {
                Column(modifier = Modifier.padding(Spacing.default)) {
                    when (selType) {
                        FilterType.BY_LOCATION -> OptionChips(
                            label = strings.filters.filterPlace,
                            options = vmState.availableLocations.map { ManagementOption(it, it) },
                            selectedIds = selectedLocations,
                            onToggle = { if (!selectedLocations.remove(it)) selectedLocations.add(it) }
                        )
                        FilterType.BY_TRAINER -> OptionChips(
                            label = strings.filters.filterTrainer,
                            options = vmState.availableTrainers.map { ManagementOption(it.first, it.second) },
                            selectedIds = selectedTrainers.map { it.substringBefore("::") },
                            onToggle = { id ->
                                val name = vmState.availableTrainers.find { it.first == id }?.second ?: id
                                if (!selectedTrainers.removeAll { it.substringBefore("::") == id }) selectedTrainers.add("$id::$name")
                            }
                        )
                        FilterType.BY_TYPE -> OptionChips(
                            label = strings.filters.filterType,
                            options = typeLabels.map { ManagementOption(it.first, it.second) },
                            selectedIds = selectedTypes,
                            onToggle = { if (!selectedTypes.remove(it)) selectedTypes.add(it) }
                        )
                        else -> {}
                    }
                }
            }
        }
    }
}
