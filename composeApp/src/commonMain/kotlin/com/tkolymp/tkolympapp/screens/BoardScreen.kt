package com.tkolymp.tkolympapp.screens
import com.tkolymp.tkolympapp.SwipeToReload

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.tkolymp.shared.tutorial.TutorialManager
import com.tkolymp.tkolympapp.TutorialHighlight
import com.tkolymp.tkolympapp.util.StaggeredItem
import com.tkolymp.tkolympapp.util.tabContentTransitionSpec
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.formatHtmlContent
import com.tkolymp.shared.viewmodels.BoardViewModel
import com.tkolymp.shared.viewmodels.CompetitionViewModel
import com.tkolymp.shared.competitions.Competition
import com.tkolymp.tkolympapp.util.normalizeForSearch
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(bottomPadding: Dp = 0.dp, onOpenNotice: (Long) -> Unit = {}) {
    val viewModel = viewModel<BoardViewModel>()
    val state by viewModel.state.collectAsState()
    val competitionViewModel = viewModel<CompetitionViewModel>()
    val competitionState by competitionViewModel.state.collectAsState()
    val tabs = listOf(AppStrings.current.boardTabs.news, AppStrings.current.boardTabs.permanentBoard, AppStrings.current.boardTabs.competitions)
    val scope = rememberCoroutineScope()

    val tutorialActive by TutorialManager.isActive.collectAsState()
    val tutorialStep by TutorialManager.currentStep.collectAsState()

    var contentBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var localSelectedTab by rememberSaveable { mutableIntStateOf(state.selectedTab) }

    LaunchedEffect(state.selectedTab) {
        if (localSelectedTab != state.selectedTab) localSelectedTab = state.selectedTab
    }

    LaunchedEffect(tutorialStep, tutorialActive) {
        if (!tutorialActive || tutorialStep !in 9..10) return@LaunchedEffect
        localSelectedTab = if (tutorialStep == 10) 1 else 0
        delay(100)
        contentBounds?.let { TutorialHighlight.rect = it }
    }

    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        viewModel.markAsSeen()
    }

    LaunchedEffect(showSearch) {
        if (showSearch) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.current.navigation.board) },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(imageVector = if (showSearch) Icons.Filled.Close else Icons.Filled.Search, contentDescription = AppStrings.current.commonActions.search)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = bottomPadding),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            PrimaryTabRow(
                selectedTabIndex = localSelectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = localSelectedTab == index,
                        onClick = { localSelectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            AnimatedVisibility(
                visible = showSearch,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (searchQuery.isNotBlank()) searchQuery = "" else showSearch = false
                        }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = AppStrings.current.commonActions.cancel)
                        }
                    },
                    placeholder = { Text(AppStrings.current.commonActions.search) }
                )
            }

            LaunchedEffect(localSelectedTab) {
                viewModel.selectTab(localSelectedTab)
                if (localSelectedTab < 2) {
                    scope.launch { viewModel.loadAnnouncements(forceRefresh = false) }
                } else {
                    scope.launch { competitionViewModel.load(forceRefresh = false) }
                }
            }

            val isRefreshing = if (localSelectedTab < 2) state.isLoading else competitionState.isLoading
            SwipeToReload(
                isRefreshing = isRefreshing,
                onRefresh = {
                    if (localSelectedTab < 2) scope.launch { viewModel.loadAnnouncements(forceRefresh = true) }
                    else scope.launch { competitionViewModel.load(forceRefresh = true) }
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = localSelectedTab,
                    transitionSpec = { tabContentTransitionSpec() },
                    label = "boardTabContent",
                    modifier = Modifier.onGloballyPositioned { coords ->
                        val b = coords.boundsInRoot()
                        contentBounds = b
                        if (tutorialActive && tutorialStep == 9) TutorialHighlight.rect = b
                    }
                ) { tab ->
                    if (tab == 2) {
                        CompetitionsTabContent(
                            upcoming = competitionState.upcomingCompetitions,
                            past = competitionState.pastCompetitions,
                            isLoading = competitionState.isLoading
                        )
                    } else {
                        var listVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(tab) { listVisible = false }
                        val announcements = if (tab == 1) state.permanentAnnouncements else state.currentAnnouncements
                        LaunchedEffect(tab, announcements.isNotEmpty()) { if (announcements.isNotEmpty()) listVisible = true }
                        val filtered = announcements.filter { a ->
                            val q = searchQuery.trim()
                            if (q.isBlank()) return@filter true
                            val nq = normalizeForSearch(q)
                            val titleOk = a.title?.let { normalizeForSearch(it).contains(nq) } == true
                            val bodyText = formatHtmlContent(a.body ?: "")
                            val bodyOk = normalizeForSearch(bodyText).contains(nq)
                            titleOk || bodyOk
                        }
                        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            filtered.forEachIndexed { i, a ->
                                StaggeredItem(index = i, visible = listVisible) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp)
                                            .clickable {
                                                a.id.toLongOrNull()?.let { nid -> onOpenNotice(nid) }
                                            },
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Text(a.title ?: AppStrings.current.dialogs.noName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                            val authorName = listOfNotNull(a.author?.uJmeno, a.author?.uPrijmeni).joinToString(" ").trim()
                                            if (authorName.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(authorName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            val plainBody = formatHtmlContent(a.body ?: "")
                                            Text(
                                                plainBody,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.error != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.clearError() },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearError() }) { Text(AppStrings.current.commonActions.ok) }
                    },
                    title = { Text(AppStrings.current.announcements.errorLoadingAnnouncements) },
                    text = { Text(state.error?.message ?: "Neznámá chyba") }
                )
            }
        }
    }
}

@Composable
private fun CompetitionsTabContent(
    upcoming: List<Competition>,
    past: List<Competition>,
    isLoading: Boolean
) {
    // Merge upcoming + past, preferring entries with results when IDs clash
    val all = remember(upcoming, past) {
        val seenIds = mutableSetOf<Long>()
        buildList {
            past.forEach { comp -> val id = comp.competitionId; if (id == null || seenIds.add(id)) add(comp) }
            upcoming.forEach { comp -> val id = comp.competitionId; if (id == null || seenIds.add(id)) add(comp) }
        }
    }

    var listVisible by remember { mutableStateOf(false) }
    LaunchedEffect(all.isNotEmpty()) { if (all.isNotEmpty()) listVisible = true }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        if (all.isEmpty()) {
            Text(
                text = AppStrings.current.competition.noUpcoming,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val byEvent = all.groupBy { it.eventId?.toString() ?: it.eventName ?: it.competitionDate }
                .entries.sortedBy { (_, comps) -> comps.first().competitionDate }
            byEvent.forEachIndexed { i, (_, eventComps) ->
                StaggeredItem(index = i, visible = listVisible) {
                    CompetitionEventCard(competitions = eventComps)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CompetitionEventCard(competitions: List<Competition>) {
    val first = competitions.first()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Event header
            Text(
                text = first.eventName ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                first.eventLocation?.takeIf { it.isNotBlank() }?.let { loc ->
                    Text(loc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(first.competitionDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Group by competitor
            val byCompetitor = competitions.groupBy { it.competitorName?.takeIf { n -> n.isNotBlank() } ?: it.personName ?: "" }
            byCompetitor.values.forEach { competitorComps ->
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(6.dp))

                val rep = competitorComps.first()
                val nameDisplay = rep.competitorName?.takeIf { it.isNotBlank() } ?: rep.personName ?: ""
                if (nameDisplay.isNotBlank()) {
                    Text(nameDisplay, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(3.dp))
                }

                val scheduled = competitorComps.filter { !it.hasResult }.sortedBy { it.checkInEnd }
                val results = competitorComps.filter { it.hasResult }.sortedBy { it.ranking }

                scheduled.forEach { comp -> CompetitionEntryRow(comp) }

                if (scheduled.isNotEmpty() && results.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                }

                results.forEach { comp -> CompetitionEntryRow(comp) }
            }
        }
    }
}

@Composable
private fun CompetitionEntryRow(comp: Competition) {
    val categoryName = comp.category?.name?.takeIf { it.isNotBlank() }
        ?: comp.competitionType?.takeIf { it.isNotBlank() }
        ?: ""
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time always on the left
        Text(
            text = comp.checkInEnd?.take(5) ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 60.dp)
        )
        Text(
            text = categoryName,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        // Right: ranking (if result) + points + F
        if (comp.hasResult && comp.ranking != null) {
            val rankText = if (comp.rankingTo != null) "${comp.ranking}. z ${comp.rankingTo}" else "${comp.ranking}."
            Text(
                text = rankText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        val rightLabel = buildString {
            comp.pointGain?.takeIf { it.isNotBlank() }?.let { raw ->
                val formatted = raw.toDoubleOrNull()?.let { d ->
                    if (d % 1.0 == 0.0) d.toInt().toString() else raw
                } ?: raw
                append(formatted)
            }
            if (comp.isFinal) { if (isNotEmpty()) append(" "); append("F") }
        }
        if (rightLabel.isNotBlank()) {
            Text(
                text = rightLabel,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
