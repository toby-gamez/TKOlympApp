package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.appearance.ThemeMode
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.tutorial.TutorialManager
import com.tkolymp.shared.viewmodels.SettingsViewModel
import com.tkolymp.tkolympapp.util.StaggeredItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onOpenLanguages: () -> Unit = {},
    onOpenNotifications: () -> Unit = {}
) {
    val viewModel = viewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val s = AppStrings.current.settings

    val downloading = remember { mutableStateOf(false) }
    val progressStage = remember { mutableStateOf("") }
    val progressDone = remember { mutableStateOf(0) }
    val progressTotal = remember { mutableStateOf(0) }

    var itemsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.load()
        itemsVisible = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppStrings.current.commonActions.back
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // Appearance
            StaggeredItem(index = 0, visible = itemsVisible, baseDelayMs = 50) {
                Column {
                    SettingsSectionHeader(text = s.appearanceSection)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
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
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                modes.forEachIndexed { index, mode ->
                                    SegmentedButton(
                                        selected = state.themeMode == mode,
                                        onClick = { scope.launch { viewModel.setThemeMode(mode) } },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                                        icon = {}
                                    ) { Text(labels[index], style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                    }
                }
            }

            // Calendar
            StaggeredItem(index = 1, visible = itemsVisible, baseDelayMs = 50) {
                Column {
                    SettingsSectionHeader(text = s.calendarSection)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
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
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = !state.preferTimeline,
                                    onClick = { scope.launch { viewModel.setPreferTimeline(false) } },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    icon = {
                                        Icon(
                                            Icons.Filled.ViewWeek,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(SegmentedButtonDefaults.IconSize)
                                                .rotate(90f)
                                        )
                                    }
                                ) { Text(s.calendarViewList, style = MaterialTheme.typography.labelSmall) }

                                SegmentedButton(
                                    selected = state.preferTimeline,
                                    onClick = { scope.launch { viewModel.setPreferTimeline(true) } },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    icon = {
                                        Icon(
                                            Icons.Filled.ViewTimeline,
                                            contentDescription = null,
                                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                        )
                                    }
                                ) { Text(s.calendarViewTimeline, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }

            // Language
            StaggeredItem(index = 2, visible = itemsVisible, baseDelayMs = 50) {
                Column {
                    SettingsSectionHeader(text = AppStrings.current.otherScreen.languages)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { onOpenLanguages() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
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
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { onOpenNotifications() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
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
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { TutorialManager.start() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
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
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
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
        }

        // Download progress dialog
        if (downloading.value) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text(s.offlineDownloadingTitle) },
                text = {
                    Column {
                        Text("${s.offlineStageLabel} ${progressStage.value}")
                        Spacer(modifier = Modifier.height(8.dp))
                        if (progressTotal.value > 0) Text("${progressDone.value} / ${progressTotal.value}")
                        Spacer(modifier = Modifier.height(8.dp))
                        CircularProgressIndicator()
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsIconBox(icon: ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}
