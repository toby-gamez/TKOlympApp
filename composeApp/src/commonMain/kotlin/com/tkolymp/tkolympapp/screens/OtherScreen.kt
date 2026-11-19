package com.tkolymp.tkolympapp.screens

import com.tkolymp.tkolympapp.components.AppTopBar
import com.tkolymp.tkolympapp.components.MenuRow
import com.tkolymp.tkolympapp.components.QuickTile
import com.tkolymp.tkolympapp.components.RowDivider
import com.tkolymp.tkolympapp.components.ScheduleCard
import com.tkolymp.tkolympapp.components.SectionLabel
import com.tkolymp.tkolympapp.components.cardLineBorder
import com.tkolymp.tkolympapp.components.pressClickable
import com.tkolymp.tkolympapp.components.surfaceCardColors
import com.tkolymp.tkolympapp.ui.theme.Spacing
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.tkolymp.tkolympapp.components.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkolymp.tkolympapp.TutorialHighlight
import com.tkolymp.tkolympapp.components.InitialsAvatar
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.tutorial.TutorialManager
import com.tkolymp.shared.utils.formatShortDate
import com.tkolymp.tkolympapp.util.StaggeredItem
import com.tkolymp.tkolympapp.util.pressScaleEffect
import com.tkolymp.shared.utils.parseToLocal
import com.tkolymp.shared.viewmodels.OtherViewModel
import kotlinx.datetime.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import com.tkolymp.tkolympapp.components.ErrorBanner
import com.tkolymp.tkolympapp.util.ISO_DATE_PREFIX_REGEX

private enum class MainItem { PEOPLE, TRAINERS, GROUPS, LEADERBOARD, COMPETITIONS }

// Helper: do not surface internal/cancellation/compose runtime messages to the UI
private fun shouldShowErrorMessage(msg: String?): Boolean {
    if (msg.isNullOrBlank()) return false
    val low = msg.lowercase()
    if (low.contains("remembercoroutinescope") || low.contains("left the composition") || low.contains("left composition") ) return false
    if (low.contains("compose") && low.contains("coroutine")) return false
    return true
}

private fun shouldShowError(t: Throwable?): Boolean {
    if (t == null) return false
    if (t is CancellationException) return false
    val m = t.message ?: return true
    return shouldShowErrorMessage(m)
}

private fun formatDateString(raw: String): String? {
    val s = raw.trim()
    val datePrefix = ISO_DATE_PREFIX_REGEX.find(s)?.value
    val ld = if (datePrefix != null) {
        try { LocalDate.parse(datePrefix) } catch (_: Exception) { null }
    } else {
        parseToLocal(s)?.date
    } ?: return null
    return formatShortDate(ld)
}

@Composable
internal fun ProfileCard(
    name: String?,
    dob: String?,
    imageUrl: String? = null,
    showQr: Boolean,
    onClick: () -> Unit,
    onQrClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    qrModifier: Modifier = Modifier,
    showDebug: Boolean = false,
    isLoading: Boolean = false,
    selected: Boolean = false,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(cardModifier)
            .pressClickable(0.985f) { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = surfaceCardColors(),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else cardLineBorder()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            InitialsAvatar(
                name = name ?: AppStrings.current.otherScreen.myAccount,
                size = 60.dp,
                fontSize = 21.sp,
                imageUrl = imageUrl
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name ?: AppStrings.current.otherScreen.myAccount,
                    fontSize = 21.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (dob != null && !showDebug) {
                    Text(
                        formatDateString(dob) ?: dob,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(Spacing.small))
            }

            if (showQr) {
                IconButton(
                    onClick = onQrClick,
                    modifier = qrModifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCode2,
                        contentDescription = "Barcode",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OtherScreen(
    onProfileClick: () -> Unit = {},
    onPeopleClick: () -> Unit = {},
    onTrainersClick: () -> Unit = {},
    onGroupsClick: () -> Unit = {},
    onLeaderboardClick: () -> Unit = {},
    onStatsClick: () -> Unit = {},
    onPaymentsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onPersonalEventsClick: () -> Unit = {},
    onBarcodeClick: (personId: String?) -> Unit = {},
    onCompetitionsClick: () -> Unit = {},
    onAchievementsClick: () -> Unit = {},
    bottomPadding: Dp = 0.dp
) {
    val viewModel = viewModel<OtherViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDebug by remember { mutableStateOf(false) }
    var showPersonSwitcher by remember { mutableStateOf(false) }

    var itemsVisible by remember { mutableStateOf(false) }

    val tutorialActive by TutorialManager.isActive.collectAsStateWithLifecycle()
    val tutorialStep by TutorialManager.currentStep.collectAsStateWithLifecycle()

    val scrollState = androidx.compose.foundation.rememberScrollState()
    val bvrPeople = remember { BringIntoViewRequester() }

    var accountBounds by remember { mutableStateOf<Rect?>(null) }
    var qrBounds by remember { mutableStateOf<Rect?>(null) }
    var peopleSectionBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(tutorialStep, tutorialActive) {
        if (!tutorialActive || tutorialStep !in 14..16) return@LaunchedEffect
        if (tutorialStep == 16) {
            bvrPeople.bringIntoView()
        } else {
            scrollState.animateScrollTo(0)
        }
        delay(200)
        when (tutorialStep) {
            14 -> accountBounds?.let { TutorialHighlight.rect = it }
            15 -> (qrBounds ?: accountBounds)?.let { TutorialHighlight.rect = it }
            16 -> peopleSectionBounds?.let { TutorialHighlight.rect = it }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.load()
        itemsVisible = true
    }

    if (showPersonSwitcher) {
        ModalBottomSheet(onDismissRequest = { showPersonSwitcher = false }) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    AppStrings.current.otherScreen.switchPerson,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Text(
                    AppStrings.current.otherScreen.switchPersonHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)
                )
                state.linkedPersons.forEach { person ->
                    val isActive = person.id == state.personId
                    ProfileCard(
                        name = person.name,
                        dob = person.birthDate,
                        imageUrl = person.imageUrl,
                        showQr = person.cstsId != null,
                        selected = isActive,
                        onClick = {
                            showPersonSwitcher = false
                            if (!isActive) viewModel.switchPerson(person.id)
                        },
                        onQrClick = {
                            showPersonSwitcher = false
                            onBarcodeClick(person.id)
                        },
                        cardModifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = AppStrings.current.navigation.profile,
                actions = {
                    com.tkolymp.tkolympapp.components.ManagementModeBadge(
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    if (state.linkedPersons.size > 1) {
                        IconButton(onClick = { showPersonSwitcher = true }) {
                            Icon(
                                imageVector = Icons.Filled.SwapHoriz,
                                contentDescription = AppStrings.current.otherScreen.switchPerson
                            )
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = AppStrings.current.otherScreen.settings
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(bottom = bottomPadding),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            // Profile card
            ProfileCard(
                name = state.name,
                dob = state.personDob,
                imageUrl = state.personImageUrl,
                showDebug = showDebug,
                showQr = state.cstsId != null,
                isLoading = state.isLoading,
                onClick = onProfileClick,
                onQrClick = { onBarcodeClick(null) },
                cardModifier = Modifier
                    .padding(top = 12.dp, bottom = 5.dp, start = 16.dp, end = 16.dp)
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInRoot()
                        accountBounds = b
                        if (tutorialActive && tutorialStep == 14) TutorialHighlight.rect = b
                    },
                qrModifier = Modifier.onGloballyPositioned { coords ->
                    val b = coords.boundsInRoot()
                    qrBounds = b
                    if (tutorialActive && tutorialStep == 15) TutorialHighlight.rect = b
                }
            )

            state.error?.let { ErrorBanner(error = it) }

            // Quick tiles: Payments | Stats | My trainings | Achievements
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.default, end = Spacing.default, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                QuickTile(Icons.Filled.Payments, AppStrings.current.otherScreen.payments, onPaymentsClick, Modifier.weight(1f))
                QuickTile(Icons.Filled.BarChart, AppStrings.current.stats.statsTitle, onStatsClick, Modifier.weight(1f))
                QuickTile(Icons.Filled.FitnessCenter, AppStrings.current.personalEvents.myTrainings, onPersonalEventsClick, Modifier.weight(1f))
                QuickTile(Icons.Filled.WorkspacePremium, AppStrings.current.achievements.profilePreviewTitle, onAchievementsClick, Modifier.weight(1f))
            }

            // První sekce - Členové a klub
            Column(
                modifier = Modifier
                    .bringIntoViewRequester(bvrPeople)
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInRoot()
                        peopleSectionBounds = b
                        if (tutorialActive && tutorialStep == 16) TutorialHighlight.rect = b
                    }
            ) {
            SectionLabel(text = AppStrings.current.otherScreen.membersAndClub)

            val mainItems = listOf(
                Pair(MainItem.PEOPLE, Icons.Filled.People),
                Pair(MainItem.TRAINERS, Icons.Filled.FitnessCenter),
                Pair(MainItem.GROUPS, Icons.Filled.Groups),
                Pair(MainItem.LEADERBOARD, Icons.Filled.EmojiEvents),
                Pair(MainItem.COMPETITIONS, Icons.Filled.MilitaryTech)
            )

            StaggeredItem(index = 0, visible = itemsVisible, baseDelayMs = 35) {
                ScheduleCard(Modifier.padding(horizontal = Spacing.default)) {
                    mainItems.forEachIndexed { i, (item, icon) ->
                        val label = when (item) {
                            MainItem.PEOPLE -> AppStrings.current.otherScreen.people
                            MainItem.TRAINERS -> AppStrings.current.otherScreen.trainersAndSpaces
                            MainItem.GROUPS -> AppStrings.current.otherScreen.trainingGroups
                            MainItem.LEADERBOARD -> AppStrings.current.otherScreen.leaderboard
                            MainItem.COMPETITIONS -> AppStrings.current.danceOrg.title
                        }
                        if (i > 0) RowDivider()
                        MenuRow(
                            icon = icon,
                            label = label,
                            onClick = {
                                when (item) {
                                    MainItem.PEOPLE -> onPeopleClick()
                                    MainItem.TRAINERS -> onTrainersClick()
                                    MainItem.GROUPS -> onGroupsClick()
                                    MainItem.LEADERBOARD -> onLeaderboardClick()
                                    MainItem.COMPETITIONS -> onCompetitionsClick()
                                }
                            }
                        )
                    }
                }
            }
            } // people section Column

                if (showDebug) {
                    Text("personId: ${state.personId ?: "(null)"}", style = MaterialTheme.typography.bodySmall)
                    Text("coupleIds: ${if (state.coupleIds.isEmpty()) "[]" else state.coupleIds.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                    if (state.rawJson != null) Text("raw: ${state.rawJson}", style = MaterialTheme.typography.bodySmall)
                    if (state.personDetailsRaw != null) Text("person: ${state.personDetailsRaw}", style = MaterialTheme.typography.bodySmall)
                    if (state.personDob != null) Text("birth: ${state.personDob}", style = MaterialTheme.typography.bodySmall)
                }
        }
    }
}
