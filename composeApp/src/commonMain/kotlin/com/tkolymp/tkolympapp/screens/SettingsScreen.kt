package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsSystemDaydream
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.appearance.ThemeMode
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {}
) {
    val viewModel = viewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val s = AppStrings.current.settings

    LaunchedEffect(Unit) {
        viewModel.load()
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // ── Appearance section ──────────────────────────────────
            SectionHeader(text = s.appearanceSection)

            SettingsCard {
                SettingsRow(
                    icon = Icons.Filled.LightMode,
                    label = s.themeLabel
                ) {
                    val modes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
                    val labels = listOf(s.themeSystem, s.themeLight, s.themeDark)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = state.themeMode == mode,
                                onClick = { scope.launch { viewModel.setThemeMode(mode) } },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                                icon = {}
                            ) {
                                Text(labels[index], style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Calendar section ────────────────────────────────────
            SectionHeader(text = s.calendarSection)

            SettingsCard {
                SettingsRow(
                    icon = Icons.Filled.CalendarMonth,
                    label = s.calendarDefaultView
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !state.preferTimeline,
                            onClick = { scope.launch { viewModel.setPreferTimeline(false) } },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {
                                Icon(
                                    Icons.Filled.ViewWeek,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize).rotate(90f)
                                )
                            }
                        ) {
                            Text(s.calendarViewList, style = MaterialTheme.typography.labelSmall)
                        }
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
                        ) {
                            Text(s.calendarViewTimeline, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    control: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        control()
    }
}
