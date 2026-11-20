package com.tkolymp.tkolympapp.screens

import androidx.compose.ui.unit.sp
import com.tkolymp.tkolympapp.components.AppTopBar
import com.tkolymp.tkolympapp.components.SegmentedToggle
import com.tkolymp.tkolympapp.components.accentColor
import com.tkolymp.tkolympapp.components.cardLineBorder
import com.tkolymp.tkolympapp.components.surfaceCardColors
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.AlertDialog
import com.tkolymp.tkolympapp.components.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import com.tkolymp.tkolympapp.components.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.tkolympapp.ui.theme.Spacing
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.appearance.ThemeMode
import com.tkolymp.shared.feedback.FeedbackType
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.language.SettingsStrings
import com.tkolymp.shared.sync.OFFLINE_DOWNLOAD_STAGES
import com.tkolymp.shared.tutorial.TutorialManager
import com.tkolymp.shared.viewmodels.SettingsViewModel
import com.tkolymp.tkolympapp.ui.theme.AppTheme
import com.tkolymp.tkolympapp.util.StaggeredItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onOpenLanguages: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenChangelog: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenTerms: () -> Unit = {},
) {
    val viewModel = viewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val s = AppStrings.current.settings

    val downloading = remember { mutableStateOf(false) }
    val progressStage = remember { mutableStateOf("") }
    val progressDone = remember { mutableStateOf(0) }
    val progressTotal = remember { mutableStateOf(0) }

    var itemsVisible by remember { mutableStateOf(false) }
    var feedbackDialogType by remember { mutableStateOf<FeedbackType?>(null) }

    LaunchedEffect(Unit) {
        viewModel.load()
        itemsVisible = true
    }

    Scaffold(
        topBar = {
            AppTopBar(title = s.title, onBack = onBack)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(bottom = Spacing.default),
            verticalArrangement = Arrangement.Top
        ) {
            // Appearance
            StaggeredItem(index = 0, visible = itemsVisible, baseDelayMs = 50) {
                Column {
                    SettingsSectionHeader(text = s.appearanceSection)
                    Card(
                        colors = surfaceCardColors(),
                        border = cardLineBorder(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.default, vertical = Spacing.xs)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = Spacing.default, vertical = 14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SettingsIconBox(Icons.Filled.LightMode)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = s.themeLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            val modes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
                            val labels = listOf(s.themeSystem, s.themeLight, s.themeDark)
                            SegmentedToggle(
                                labels = labels,
                                selectedIndex = modes.indexOf(state.themeMode).coerceAtLeast(0),
                                onSelect = { i -> scope.launch { viewModel.setThemeMode(modes[i]) } },
                                fillWidth = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Calendar
            StaggeredItem(index = 1, visible = itemsVisible, baseDelayMs = 50) {
                Column {
                    SettingsSectionHeader(text = s.calendarSection)
                    Card(
                        colors = surfaceCardColors(),
                        border = cardLineBorder(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.default, vertical = Spacing.xs)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = Spacing.default, vertical = 14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SettingsIconBox(Icons.Filled.CalendarMonth)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = s.calendarDefaultView,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            SegmentedToggle(
                                labels = listOf(s.calendarViewList, s.calendarViewTimeline),
                                selectedIndex = if (state.preferTimeline) 1 else 0,
                                onSelect = { i -> scope.launch { viewModel.setPreferTimeline(i == 1) } },
                                fillWidth = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Language
            StaggeredItem(index = 2, visible = itemsVisible, baseDelayMs = 50) {
                Column {
                    SettingsSectionHeader(text = AppStrings.current.otherScreen.languages)
                    Card(
                        colors = surfaceCardColors(),
                        border = cardLineBorder(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.default, vertical = Spacing.xs)
                            .clickable { onOpenLanguages() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.default, vertical = 14.dp)
                        ) {
                            SettingsIconBox(Icons.Filled.Psychology)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = AppStrings.current.languageScreen.selectLanguage,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Notifications
            StaggeredItem(index = 3, visible = itemsVisible, baseDelayMs = 50) {
                Column {
                    SettingsSectionHeader(text = AppStrings.current.otherScreen.notificationSettings)
                    Card(
                        colors = surfaceCardColors(),
                        border = cardLineBorder(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.default, vertical = Spacing.xs)
                            .clickable { onOpenNotifications() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.default, vertical = 14.dp)
                        ) {
                            SettingsIconBox(Icons.Filled.Notifications)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = AppStrings.current.otherScreen.notificationSettings,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Tutorial
            StaggeredItem(index = 4, visible = itemsVisible, baseDelayMs = 50) {
                Column {
                    SettingsSectionHeader(text = AppStrings.current.tutorial.tutorialRowLabel)
                    Card(
                        colors = surfaceCardColors(),
                        border = cardLineBorder(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.default, vertical = Spacing.xs)
                            .clickable { TutorialManager.start() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.default, vertical = 14.dp)
                        ) {
                            SettingsIconBox(Icons.Filled.School)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = AppStrings.current.tutorial.tutorialRowLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = AppStrings.current.tutorial.tutorialRowSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Offline download
            StaggeredItem(index = 5, visible = itemsVisible, baseDelayMs = 50) {
                Column {
                    SettingsSectionHeader(text = s.offlineSection)
                    Card(
                        colors = surfaceCardColors(),
                        border = cardLineBorder(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.default, vertical = Spacing.xs)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = Spacing.default, vertical = 14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SettingsIconBox(Icons.Filled.Download)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = s.offlineDownloadLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    scope.launch {
                                        downloading.value = true
                                        progressStage.value = "starting"
                                        progressDone.value = 0
                                        progressTotal.value = 0
                                        try {
                                            ServiceLocator.offlineSyncManager.downloadAll { stage, done, total ->
                                                progressStage.value = stage
                                                progressDone.value = done
                                                progressTotal.value = total
                                            }
                                        } catch (_: Exception) {}
                                        downloading.value = false
                                    }
                                }
                            ) { Text(s.offlineDownloadButton) }
                        }
                    }
                }
            }

            // Privacy: the error-report choice from the privacy screen must stay changeable (GDPR art. 7(3)/21)
            StaggeredItem(index = 6, visible = itemsVisible, baseDelayMs = 50) {
                PrivacySettingsSection(
                    errorReportingEnabled = state.errorReportingEnabled,
                    onErrorReportingChange = { enabled -> scope.launch { viewModel.setErrorReportingEnabled(enabled) } },
                    onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                    onOpenTerms = onOpenTerms,
                )
            }

            // Feedback
            StaggeredItem(index = 7, visible = itemsVisible, baseDelayMs = 50) {
                Column {
                    SettingsSectionHeader(text = AppStrings.current.feedback.sectionTitle)
                    Card(
                        colors = surfaceCardColors(),
                        border = cardLineBorder(),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.default, vertical = 2.dp)
                            .clickable { feedbackDialogType = FeedbackType.BUG_REPORT }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.default, vertical = 14.dp)
                        ) {
                            SettingsIconBox(Icons.Filled.BugReport)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = AppStrings.current.feedback.reportBugLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Card(
                        colors = surfaceCardColors(),
                        border = cardLineBorder(),
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.default, vertical = 2.dp)
                            .clickable { feedbackDialogType = FeedbackType.FEATURE_SUGGESTION }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.default, vertical = 14.dp)
                        ) {
                            SettingsIconBox(Icons.Filled.Lightbulb)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = AppStrings.current.feedback.suggestFeatureLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // App section: Changelog + About
            StaggeredItem(index = 8, visible = itemsVisible, baseDelayMs = 50) {
                Column {
                    SettingsSectionHeader(text = AppStrings.current.otherScreen.appSection)
                    Card(
                        colors = surfaceCardColors(),
                        border = cardLineBorder(),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.default, vertical = 2.dp)
                            .clickable { onOpenChangelog() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.default, vertical = 14.dp)
                        ) {
                            SettingsIconBox(Icons.Filled.NewReleases)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = AppStrings.current.otherScreen.changelog,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Card(
                        colors = surfaceCardColors(),
                        border = cardLineBorder(),
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.default, vertical = 2.dp)
                            .clickable { onOpenAbout() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.default, vertical = 14.dp)
                        ) {
                            SettingsIconBox(Icons.Filled.Info)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = AppStrings.current.otherScreen.aboutApp,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Download progress dialog
        if (downloading.value) {
            val stages = OFFLINE_DOWNLOAD_STAGES
            val currentIdx = stages.indexOf(progressStage.value).coerceAtLeast(0)
            val stageFraction = if (progressTotal.value > 0) progressDone.value.toFloat() / progressTotal.value else 0f
            val overall by animateFloatAsState((currentIdx + stageFraction) / stages.size, label = "downloadProgress")
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text(s.offlineDownloadingTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        LinearProgressIndicator(progress = { overall }, modifier = Modifier.fillMaxWidth())
                        Text(
                            "${(overall * 100).toInt()} %",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        stages.forEachIndexed { idx, stage ->
                            val state = when {
                                idx < currentIdx -> 2
                                idx == currentIdx -> 1
                                else -> 0
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                    when (state) {
                                        2 -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        1 -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                        else -> Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(Spacing.small))
                                Text(
                                    text = offlineStageName(s, stage),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (state == 1) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (state == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (state == 1 && progressTotal.value > 1) {
                                    Text("${progressDone.value} / ${progressTotal.value}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            )
        }

        feedbackDialogType?.let { type ->
            FeedbackDialog(type = type, onDismiss = { feedbackDialogType = null })
        }
    }
}

private fun offlineStageName(s: SettingsStrings, stage: String): String = when (stage) {
    "calendar" -> s.offlineStageCalendar
    "user" -> s.offlineStageUser
    "announcements" -> s.offlineStageAnnouncements
    "people" -> s.offlineStagePeople
    "couples" -> s.offlineStageCouples
    "payments" -> s.offlineStagePayments
    "scoreboard" -> s.offlineStageScoreboard
    "club" -> s.offlineStageClub
    "events" -> s.offlineStageEvents
    else -> s.offlineStageCompetitions
}

@Composable
private fun PrivacySettingsSection(
    errorReportingEnabled: Boolean,
    onErrorReportingChange: (Boolean) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = AppStrings.current.privacyConsent
    Column(modifier = modifier) {
        SettingsSectionHeader(text = p.settingsSection)
        Card(
            colors = surfaceCardColors(),
            border = cardLineBorder(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.default, vertical = 2.dp)
                .toggleable(
                    value = errorReportingEnabled,
                    role = Role.Switch,
                    onValueChange = onErrorReportingChange,
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.default, vertical = 14.dp)
            ) {
                SettingsIconBox(Icons.Filled.BugReport)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = p.errorReportingLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = p.errorReportingSettingsDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                // Row handles the toggle (one accessible switch node), so the Switch itself is not clickable.
                Switch(checked = errorReportingEnabled, onCheckedChange = null)
            }
        }
        Card(
            colors = surfaceCardColors(),
            border = cardLineBorder(),
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.default, vertical = 2.dp)
                .clickable { onOpenPrivacyPolicy() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.default, vertical = 14.dp)
            ) {
                SettingsIconBox(Icons.Filled.PrivacyTip)
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = p.privacyPolicyRow,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Card(
            colors = surfaceCardColors(),
            border = cardLineBorder(),
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.default, vertical = 2.dp)
                .clickable { onOpenTerms() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.default, vertical = 14.dp)
            ) {
                SettingsIconBox(Icons.Filled.Description)
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = p.termsRow,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(name = "PrivacySettingsSection — Light")
@Composable
private fun PrivacySettingsSectionPreviewLight() {
    AppTheme(darkTheme = false) {
        PrivacySettingsSection(errorReportingEnabled = true, onErrorReportingChange = {}, onOpenPrivacyPolicy = {}, onOpenTerms = {})
    }
}

@Preview(name = "PrivacySettingsSection — Dark")
@Composable
private fun PrivacySettingsSectionPreviewDark() {
    AppTheme(darkTheme = true) {
        PrivacySettingsSection(errorReportingEnabled = false, onErrorReportingChange = {}, onOpenPrivacyPolicy = {}, onOpenTerms = {})
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.default, top = 20.dp, bottom = Spacing.small)
    )
}

@Composable
private fun SettingsIconBox(icon: ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor(),
            modifier = Modifier.size(20.dp)
        )
    }
}
