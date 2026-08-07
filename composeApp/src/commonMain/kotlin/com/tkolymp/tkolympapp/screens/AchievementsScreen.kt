package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.formatMonthDay
import com.tkolymp.shared.viewmodels.AchievementsViewModel
import com.tkolymp.shared.viewmodels.BadgeUiState
import com.tkolymp.shared.viewmodels.DiplomaUiState
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.DiplomaDialog
import com.tkolymp.tkolympapp.components.DiplomaThumbnail
import com.tkolymp.tkolympapp.components.ErrorBanner
import com.tkolymp.tkolympapp.components.ErrorState
import com.tkolymp.tkolympapp.components.badgeGridSections
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(personId: String? = null, onBack: () -> Unit = {}) {
    val viewModel = viewModel<AchievementsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val strings = AppStrings.current.achievements
    val showDiplomas = personId == null

    LaunchedEffect(personId) { viewModel.load(personId) }

    var selectedBadge by remember { mutableStateOf<BadgeUiState?>(null) }
    var selectedDiploma by remember { mutableStateOf<DiplomaUiState?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.screenTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                    }
                }
            )
        }
    ) { padding ->
        SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch { viewModel.load(personId) } },
            modifier = Modifier.padding(padding)
        ) {
            if (state.error != null && state.badges.isEmpty() && state.diplomas.isEmpty()) {
                ErrorState(error = state.error!!, onRetry = { scope.launch { viewModel.load(personId) } })
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (state.error != null) {
                        ErrorBanner(error = state.error!!, onRetry = { scope.launch { viewModel.load(personId) } })
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                Text(
                                    text = "${state.earnedCount}/${state.badges.size}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                LinearProgressIndicator(
                                    progress = {
                                        if (state.badges.isEmpty()) 0f
                                        else state.earnedCount.toFloat() / state.badges.size
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = StrokeCap.Round,
                                )
                            }
                        }

                        badgeGridSections(
                            badges = state.badges,
                            strings = strings,
                            onBadgeClick = { selectedBadge = it }
                        )

                        if (showDiplomas) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = strings.sectionDiplomas,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                                )
                            }

                            if (state.diplomas.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = strings.noDiplomasYet,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            } else {
                                items(
                                    items = state.diplomas,
                                    key = { it.camp.eventId },
                                ) { diploma ->
                                    DiplomaThumbnail(diploma = diploma, onClick = { selectedDiploma = diploma })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedBadge?.let { badge ->
        BadgeDetailDialog(badge = badge, onDismiss = { selectedBadge = null })
    }
    selectedDiploma?.let { diploma ->
        DiplomaDialog(diploma = diploma, onDismiss = { selectedDiploma = null })
    }
}

@Composable
private fun BadgeDetailDialog(badge: BadgeUiState, onDismiss: () -> Unit) {
    val strings = AppStrings.current.achievements
    val langCode = AppStrings.currentLanguage.code
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(badge.definition.title(strings)) },
        text = {
            Column {
                Text(badge.definition.description(strings))
                Spacer(Modifier.height(8.dp))
                val earnedOn = badge.earnedOn
                if (badge.earned && earnedOn != null) {
                    Text(
                        text = "${strings.earnedOnLabel} ${formatMonthDay(earnedOn, langCode, true)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = strings.lockedLabel + (badge.progress?.let { (cur, target) -> " · $cur/$target" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(AppStrings.current.commonActions.ok) }
        }
    )
}
