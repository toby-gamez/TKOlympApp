package com.tkolymp.tkolympapp.screens
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import com.tkolymp.shared.utils.daysSince
import com.tkolymp.shared.utils.formatShortDate
import com.tkolymp.shared.utils.parseToLocal
import com.tkolymp.tkolympapp.components.AppTopBar
import com.tkolymp.tkolympapp.components.NoticeCard
import com.tkolymp.tkolympapp.components.SearchField
import com.tkolymp.tkolympapp.components.SegmentedToggle
import com.tkolymp.tkolympapp.ui.theme.Spacing
import kotlinx.datetime.todayIn
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.EmptyState
import com.tkolymp.tkolympapp.components.ManageCreateAction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.tkolymp.tkolympapp.components.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.tkolymp.tkolympapp.util.normalizeForSearch
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Campaign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(bottomPadding: Dp = 0.dp, onOpenNotice: (Long) -> Unit = {}, onCreateNotice: ((sticky: Boolean) -> Unit)? = null) {
    val viewModel = viewModel<BoardViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tabs = listOf(AppStrings.current.boardTabs.news, AppStrings.current.boardTabs.permanentBoard)
    val scope = rememberCoroutineScope()

    val tutorialActive by TutorialManager.isActive.collectAsStateWithLifecycle()
    val tutorialStep by TutorialManager.currentStep.collectAsStateWithLifecycle()

    var contentBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var localSelectedTab by rememberSaveable { mutableIntStateOf(state.selectedTab) }

    LaunchedEffect(state.selectedTab) {
        if (localSelectedTab != state.selectedTab) localSelectedTab = state.selectedTab
    }

    LaunchedEffect(tutorialStep, tutorialActive) {
        if (!tutorialActive || tutorialStep !in 9..10) return@LaunchedEffect
        localSelectedTab = if (tutorialStep == 10) 1 else 0
    }

    LaunchedEffect(tutorialStep, tutorialActive, contentBounds) {
        if (!tutorialActive || tutorialStep !in 9..10) return@LaunchedEffect
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
            AppTopBar(
                title = AppStrings.current.navigation.board,
                actions = {
                    ManageCreateAction(
                        label = AppStrings.current.management.newAnnouncement,
                        onClick = onCreateNotice?.let { create -> { create(localSelectedTab == 1) } }
                    )
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
            AnimatedVisibility(
                visible = showSearch,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                SearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = AppStrings.current.commonActions.search,
                    onTrailingClick = { if (searchQuery.isNotBlank()) searchQuery = "" else showSearch = false },
                    trailingDescription = AppStrings.current.commonActions.cancel,
                    modifier = Modifier.padding(start = Spacing.default, end = Spacing.default, top = Spacing.small),
                    focusRequester = focusRequester
                )
            }

            SegmentedToggle(
                labels = tabs,
                selectedIndex = localSelectedTab,
                onSelect = { localSelectedTab = it },
                fillWidth = true,
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.default, end = Spacing.default, top = Spacing.small)
            )

            LaunchedEffect(localSelectedTab) {
                viewModel.selectTab(localSelectedTab)
                scope.launch { viewModel.loadAnnouncements(forceRefresh = false) }
            }

            SwipeToReload(
                isRefreshing = state.isLoading,
                onRefresh = {
                    scope.launch { viewModel.loadAnnouncements(forceRefresh = true) }
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
                    var listVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(tab) { listVisible = false }
                    val announcements = if (tab == 1) state.permanentAnnouncements else state.currentAnnouncements
                    LaunchedEffect(tab, announcements.isNotEmpty()) { if (announcements.isNotEmpty()) listVisible = true }
                    val filtered = announcements.filter { a ->
                        val q = searchQuery.trim()
                        if (q.isBlank()) return@filter true
                        val nq = normalizeForSearch(q)
                        val titleOk = a.title?.let { normalizeForSearch(it).contains(nq) } == true
                        val bodyOk = try { normalizeForSearch(formatHtmlContent(a.body ?: "")).contains(nq) } catch (_: Exception) { false }
                        titleOk || bodyOk
                    }
                    if (filtered.isEmpty() && !state.isLoading) {
                        EmptyState(title = AppStrings.current.announcements.noAnnouncementsToShow, icon = Icons.Outlined.Campaign, fullPage = true)
                    } else {
                        val today = remember { kotlin.time.Clock.System.todayIn(kotlinx.datetime.TimeZone.currentSystemDefault()) }
                        val strings = AppStrings.current
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(start = Spacing.default, end = Spacing.default, top = 14.dp, bottom = Spacing.default),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(filtered, key = { _, a -> a.id }) { i, a ->
                                StaggeredItem(index = i, visible = listVisible) {
                                    val authorName = listOfNotNull(a.author?.uJmeno, a.author?.uPrijmeni).joinToString(" ").trim()
                                    val age = daysSince(a.createdAt, today)
                                    val ago = when {
                                        age == null -> null
                                        age == 0 -> strings.timeline.today
                                        age < 14 -> strings.announcements.daysAgo.replace("%d", age.toString())
                                        else -> parseToLocal(a.createdAt)?.let { formatShortDate(it.date) }
                                    }
                                    NoticeCard(
                                        authorName = authorName,
                                        meta = listOfNotNull(authorName.takeIf { it.isNotBlank() }, ago).joinToString(" · "),
                                        title = a.title ?: strings.dialogs.noName,
                                        body = remember(a.id, a.body) { formatHtmlContent(a.body ?: "") },
                                        onClick = { a.id.toLongOrNull()?.let { nid -> onOpenNotice(nid) } },
                                        featured = i == 0,
                                        badge = strings.announcements.newBadge.takeIf { i == 0 && age != null && age <= 3 }
                                    )
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

