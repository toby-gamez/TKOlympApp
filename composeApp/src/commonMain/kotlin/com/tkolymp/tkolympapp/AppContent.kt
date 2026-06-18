package com.tkolymp.tkolympapp

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.appearance.AppearanceSettings
import com.tkolymp.shared.appearance.ThemeMode
import com.tkolymp.shared.language.AppLanguage
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.language.getDeviceLanguageCode
import com.tkolymp.shared.registration.RegMode
import com.tkolymp.shared.viewmodels.OnboardingViewModel
import com.tkolymp.tkolympapp.platform.getAppVersion
import com.tkolymp.tkolympapp.screens.AboutScreen
import com.tkolymp.tkolympapp.screens.BarcodeScreen
import com.tkolymp.tkolympapp.screens.BirthdayNotificationsScreen
import com.tkolymp.tkolympapp.screens.BoardScreen
import com.tkolymp.tkolympapp.screens.CalendarScreen
import com.tkolymp.tkolympapp.screens.CalendarViewScreen
import com.tkolymp.tkolympapp.screens.CompetitionsScreen
import com.tkolymp.tkolympapp.screens.EventScreen
import com.tkolymp.tkolympapp.screens.EventsScreen
import com.tkolymp.tkolympapp.screens.FreeLessonsScreen
import com.tkolymp.tkolympapp.screens.GroupsScreen
import com.tkolymp.tkolympapp.screens.LanguageScreen
import com.tkolymp.tkolympapp.screens.LeaderboardScreen
import com.tkolymp.tkolympapp.screens.LoginScreen
import com.tkolymp.tkolympapp.screens.NoticeScreen
import com.tkolymp.tkolympapp.screens.NotificationsSettingsScreen
import com.tkolymp.tkolympapp.screens.OnboardingScreen
import com.tkolymp.tkolympapp.screens.OtherScreen
import com.tkolymp.tkolympapp.screens.OverviewScreen
import com.tkolymp.tkolympapp.screens.PaymentsScreen
import com.tkolymp.tkolympapp.screens.PeopleScreen
import com.tkolymp.tkolympapp.screens.PersonScreen
import com.tkolymp.tkolympapp.screens.PersonalEventEditScreen
import com.tkolymp.tkolympapp.screens.PersonalEventsScreen
import com.tkolymp.tkolympapp.screens.PrivacyPolicyScreen
import com.tkolymp.tkolympapp.screens.ProfileScreen
import com.tkolymp.tkolympapp.screens.RegistrationScreen
import com.tkolymp.tkolympapp.screens.RoleSelectionScreen
import com.tkolymp.tkolympapp.screens.SettingsScreen
import com.tkolymp.tkolympapp.screens.StatsScreen
import com.tkolymp.tkolympapp.screens.TermsOfUseScreen
import com.tkolymp.tkolympapp.screens.TrainersLocationsScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import ui.theme.AppTheme

/**
 * Common app shell: theme, language, navigation, state management.
 * [platformInit] is called first inside the startup LaunchedEffect; use it to call
 * initNetworking() and any platform-specific setup.
 * [integrityFailed] shows an error screen when true (set by platform before calling AppContent).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(
    platformInit: suspend () -> Unit,
    integrityFailed: Boolean = false,
) {
    val themeMode by AppearanceSettings.themeMode.collectAsState()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    AppTheme(darkTheme = isDark) {
        val currentLanguage by AppStrings.languageFlow.collectAsState()

        var loggedIn by remember { mutableStateOf<Boolean?>(null) }
        var showOnboarding by remember { mutableStateOf<Boolean?>(null) }
        var showRoleSelection by remember { mutableStateOf(false) }
        var tutorialSeen by remember { mutableStateOf(false) }
        val preferTimeline by AppearanceSettings.preferTimeline.collectAsState()
        var weekOffset by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            try {
                platformInit()
                // Restore saved language, or fall back to device language on first launch
                try {
                    val code = ServiceLocator.languageStorage.getLanguageCode()
                    val language = if (code != null) {
                        AppLanguage.fromCode(code)
                    } else {
                        val detected = AppLanguage.fromCode(getDeviceLanguageCode())
                        ServiceLocator.languageStorage.saveLanguageCode(detected.code)
                        detected
                    }
                    AppStrings.setLanguage(language)
                } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                val has = try { ServiceLocator.authService.hasToken() } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
                try {
                    val role = try { ServiceLocator.onboardingStorage.getUserRole() } catch (_: Exception) { null }
                    ServiceLocator.notificationService.initializeIfNeeded(role)
                } catch (_: Exception) {}
                val onboardingVm = OnboardingViewModel()
                val seen = try { onboardingVm.hasSeenOnboarding() } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
                val prefTimeline = try { onboardingVm.getPreferTimeline() } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
                AppearanceSettings.setPreferTimeline(prefTimeline)
                val themeRaw = try { ServiceLocator.calendarPreferenceStorage.getThemeMode() } catch (_: Exception) { "system" }
                AppearanceSettings.setThemeMode(when (themeRaw) { "light" -> ThemeMode.LIGHT; "dark" -> ThemeMode.DARK; else -> ThemeMode.SYSTEM })
                tutorialSeen = try { ServiceLocator.onboardingStorage.hasSeenTutorial() } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
                showOnboarding = !seen
                loggedIn = has
                try {
                    if (ServiceLocator.networkMonitor.isConnected()) {
                        kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
                            try { ServiceLocator.offlineSyncManager.downloadAll() } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                loggedIn = false
                showOnboarding = true
            }
        }

        Crossfade(targetState = currentLanguage, animationSpec = tween(600), label = "languageTransition") { _ ->
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose { com.tkolymp.shared.tutorial.TutorialManager.skip() }
            }
            val scope = rememberCoroutineScope()
            val navController = rememberNavController()
            val currentBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = currentBackStackEntry?.destination?.route
            val boardHasUnread by com.tkolymp.shared.announcements.AnnouncementBadge.hasUnread.collectAsState()
            val tutorialActive by com.tkolymp.shared.tutorial.TutorialManager.isActive.collectAsState()
            val tutorialStep by com.tkolymp.shared.tutorial.TutorialManager.currentStep.collectAsState()

            LaunchedEffect(tutorialStep, tutorialActive) {
                TutorialHighlight.rect = null
                if (tutorialActive) {
                    val route = com.tkolymp.shared.tutorial.TutorialManager.steps[tutorialStep].route
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    if (route != currentRoute) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    bottomBar = {
                        AnimatedVisibility(
                            visible = showOnboarding != true && showRoleSelection != true && loggedIn == true && currentRoute in listOf("overview", "calendar", "timeline", "board", "events", "other"),
                            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)),
                            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300))
                        ) {
                            AppBottomBar(current = currentRoute ?: "overview", boardHasUnread = boardHasUnread, onSelect = {
                                val startId = navController.graph.findStartDestination().id
                                if (it == "overview") {
                                    navController.navigate(it) {
                                        popUpTo(startId) { }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                } else if (it == "calendar") {
                                    val destination = if (preferTimeline) "timeline" else "calendar"
                                    navController.navigate(destination) {
                                        popUpTo(startId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                } else {
                                    navController.navigate(it) {
                                        popUpTo(startId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            })
                        }
                    }
                ) { padding ->
                    CompositionLocalProvider(
                        LocalBottomBarPadding provides padding.calculateBottomPadding(),
                        LocalTopBarPadding provides padding.calculateTopPadding()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            when {
                                integrityFailed -> IntegrityBlockedScreen()
                                loggedIn == null || showOnboarding == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                                showOnboarding == true -> OnboardingScreen(
                                    onFinish = {
                                        showOnboarding = false
                                        loggedIn = false
                                    }
                                )
                                loggedIn == false -> LoginScreen(onSuccess = {
                                    loggedIn = true
                                    showRoleSelection = true
                                    try {
                                        if (ServiceLocator.networkMonitor.isConnected()) {
                                            kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
                                                try { ServiceLocator.offlineSyncManager.downloadAll() } catch (_: Exception) {}
                                            }
                                        }
                                    } catch (_: Exception) {}
                                })
                                showRoleSelection -> RoleSelectionScreen(onFinish = {
                                    showRoleSelection = false
                                    if (!tutorialSeen) {
                                        tutorialSeen = true
                                        scope.launch {
                                            try { ServiceLocator.onboardingStorage.setTutorialSeen() } catch (_: Exception) {}
                                        }
                                        com.tkolymp.shared.tutorial.TutorialManager.start()
                                    }
                                })
                                else -> AppNavHost(
                                    navController = navController,
                                    weekOffset = weekOffset,
                                    onWeekOffsetChange = { weekOffset = it },
                                    preferTimeline = preferTimeline,
                                    onLogout = {
                                        loggedIn = false
                                        navController.navigate("overview") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    },
                                    bottomPadding = padding.calculateBottomPadding()
                                )
                            }
                        }
                    }
                }

                if (loggedIn == true && showOnboarding == false && !showRoleSelection) {
                    com.tkolymp.tkolympapp.components.TutorialOverlay(
                        isActive = tutorialActive,
                        step = tutorialStep,
                        onNext = { com.tkolymp.shared.tutorial.TutorialManager.next() },
                        onPrevious = { com.tkolymp.shared.tutorial.TutorialManager.previous() },
                        onSkip = { com.tkolymp.shared.tutorial.TutorialManager.skip() }
                    )
                }
            }
        }
    }
}

/** Shown when platform integrity check fails (Android-only in practice; defined here for type-safety). */
@Composable
expect fun IntegrityBlockedScreen()

@Composable
fun AppNavHost(
    navController: NavHostController,
    weekOffset: Int,
    onWeekOffsetChange: (Int) -> Unit,
    onLogout: () -> Unit,
    preferTimeline: Boolean = false,
    bottomPadding: Dp
) {
    NavHost(
        navController = navController,
        startDestination = "overview"
    ) {
        composable(
            route = "overview",
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            OverviewScreen(
                bottomPadding = bottomPadding,
                onOpenEvent = { id, instId -> navController.navigate("event/$id" + if (instId != null) "?instanceId=$instId" else "") },
                onOpenNotice = { id -> navController.navigate("notice/$id") },
                onOpenCalendar = { navController.navigate("calendar") },
                onOpenBoard = { navController.navigate("board") },
                onOpenEvents = { navController.navigate("events") },
                onOpenPerson = { id -> navController.navigate("person/$id") },
                onOpenCompetitions = { navController.navigate("competitions") }
            )
        }

        composable(
            route = "calendar",
            enterTransition = { if (preferTimeline) slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(400)) else fadeIn(tween(300)) },
            exitTransition = { if (preferTimeline) slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(400)) else fadeOut(tween(300)) },
            popEnterTransition = { if (preferTimeline) slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(400)) else fadeIn(tween(300)) },
            popExitTransition = { if (preferTimeline) slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(400)) else fadeOut(tween(300)) }
        ) {
            CalendarScreen(
                weekOffset = weekOffset,
                onWeekOffsetChange = onWeekOffsetChange,
                onOpenEvent = { id, instId -> navController.navigate("event/$id" + if (instId != null) "?instanceId=$instId" else "") },
                onNavigateTimeline = if (preferTimeline) ({ navController.navigateUp() }) else ({ navController.navigate("timeline") }),
                onBack = if (preferTimeline) ({ navController.navigateUp() }) else null,
                onFindFreeLessons = { navController.navigate("free-lessons") },
                bottomPadding = bottomPadding
            )
        }

        composable(
            route = "free-lessons",
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) }
        ) {
            FreeLessonsScreen(
                onBack = { navController.navigateUp() },
                onOpenEvent = { id -> navController.navigate("event/$id") }
            )
        }

        composable(
            route = "board",
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            BoardScreen(bottomPadding = bottomPadding, onOpenNotice = { id -> navController.navigate("notice/$id") })
        }

        composable(
            route = "events",
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            EventsScreen(bottomPadding = bottomPadding, onOpenEvent = { id -> navController.navigate("event/$id") })
        }

        composable(
            route = "other",
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            OtherScreen(
                onProfileClick = { navController.navigate("profile") },
                onPeopleClick = { navController.navigate("people") },
                onTrainersClick = { navController.navigate("trainers") },
                onGroupsClick = { navController.navigate("groups") },
                onLeaderboardClick = { navController.navigate("leaderboard") },
                onPaymentsClick = { navController.navigate("payments") },
                onStatsClick = { navController.navigate("stats") },
                onAboutClick = { navController.navigate("about") },
                onSettingsClick = { navController.navigate("settings") },
                onPersonalEventsClick = { navController.navigate("personal_events") },
                onBarcodeClick = { navController.navigate("barcode") },
                onCompetitionsClick = { navController.navigate("competitions") },
                bottomPadding = bottomPadding
            )
        }

        composable(
            route = "barcode",
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) {
            BarcodeScreen(onBack = { navController.navigateUp() })
        }

        composable(
            route = "languages",
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) {
            LanguageScreen(onBack = { navController.navigateUp() })
        }

        composable(
            route = "personal_events",
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) }
        ) {
            PersonalEventsScreen(
                onBack = { navController.navigateUp() },
                onEdit = { id -> navController.navigate("personal_event_edit?eventId=$id") },
                onCreatePersonalEvent = { navController.navigate("personal_event_edit") },
                bottomPadding = bottomPadding
            )
        }

        composable(
            route = "personal_event_edit?eventId={eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.StringType; defaultValue = "" }),
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) }
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId")
            PersonalEventEditScreen(eventId = eventId?.ifEmpty { null }, onSaved = { navController.navigateUp() }, onBack = { navController.navigateUp() }, bottomPadding = bottomPadding)
        }

        composable(
            route = "payments",
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) {
            val paymentsVm: com.tkolymp.shared.payments.PaymentsViewModel = viewModel()
            PaymentsScreen(vm = paymentsVm, onBack = { navController.navigateUp() }, bottomPadding = bottomPadding)
        }

        composable(
            route = "settings",
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) {
            SettingsScreen(
                onBack = { navController.navigateUp() },
                onOpenLanguages = { navController.navigate("languages") },
                onOpenNotifications = { navController.navigate("notifications") }
            )
        }

        composable(
            route = "notifications",
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) {
            NotificationsSettingsScreen(onBack = { navController.navigateUp() })
        }

        composable(
            route = "about",
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) {
            val (versionName, versionCode) = getAppVersion()
            AboutScreen(
                onBack = { navController.navigateUp() },
                appVersionName = versionName,
                appVersionCode = versionCode,
                onPrivacyClick = { navController.navigate("privacy") },
                onTermsClick = { navController.navigate("terms") }
            )
        }

        composable(
            route = "privacy",
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) {
            PrivacyPolicyScreen(onBack = { navController.navigateUp() })
        }

        composable(
            route = "terms",
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) {
            TermsOfUseScreen(onBack = { navController.navigateUp() })
        }

        composable(
            route = "leaderboard",
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) {
            LeaderboardScreen(onBack = { navController.navigateUp() }, bottomPadding = bottomPadding)
        }

        composable(
            route = "competitions",
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) {
            CompetitionsScreen(onBack = { navController.navigateUp() }, bottomPadding = bottomPadding)
        }

        composable(
            route = "stats",
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) {
            StatsScreen(
                onBack = { navController.navigateUp() },
                bottomPadding = bottomPadding,
                onOpenLeaderboard = { navController.navigate("leaderboard") }
            )
        }

        composable(
            route = "groups",
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) {
            GroupsScreen(onBack = { navController.navigateUp() }, bottomPadding = bottomPadding)
        }

        composable(
            route = "trainers",
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) {
            TrainersLocationsScreen(onBack = { navController.navigateUp() })
        }

        composable(
            route = "people",
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) {
            PeopleScreen(
                onPersonClick = { id -> navController.navigate("person/$id") },
                onBack = { navController.navigateUp() },
                onBirthdayNotificationsClick = { navController.navigate("birthday_notifications") }
            )
        }

        composable(
            route = "birthday_notifications",
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) {
            BirthdayNotificationsScreen(onBack = { navController.navigateUp() })
        }

        composable(
            route = "person/{personId}",
            arguments = listOf(navArgument("personId") { type = NavType.StringType }),
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getString("personId")
            personId?.let { pid ->
                PersonScreen(
                    personId = pid,
                    onBack = { navController.navigateUp() },
                    onOpenCouple = { }
                )
            }
        }

        composable(
            route = "timeline",
            enterTransition = { if (preferTimeline) fadeIn(tween(300)) else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(400)) },
            exitTransition = { if (preferTimeline) fadeOut(tween(300)) else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(400)) },
            popEnterTransition = { if (preferTimeline) fadeIn(tween(300)) else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(400)) },
            popExitTransition = { if (preferTimeline) fadeOut(tween(300)) else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(400)) }
        ) {
            CalendarViewScreen(
                onEventClick = { id, instId -> navController.navigate("event/$id" + if (instId != null) "?instanceId=$instId" else "") },
                onBack = if (!preferTimeline) ({ navController.navigateUp() }) else null,
                onSwitchToBlocks = if (preferTimeline) ({ navController.navigate("calendar") }) else null,
                onFindFreeLessons = { navController.navigate("free-lessons") },
                bottomPadding = bottomPadding,
            )
        }

        composable(
            route = "event/{eventId}?instanceId={instanceId}",
            arguments = listOf(
                navArgument("eventId") { type = NavType.LongType },
                navArgument("instanceId") { type = NavType.LongType; defaultValue = -1L }
            ),
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getLong("eventId")
            val instanceId = backStackEntry.arguments?.getLong("instanceId")?.takeIf { it != -1L }
            eventId?.let { eid ->
                EventScreen(
                    eventId = eid,
                    instanceId = instanceId,
                    onBack = { navController.navigateUp() },
                    onOpenRegistration = { mode, _ ->
                        navController.navigate("event/$eid/registration/$mode")
                    },
                    onOpenPerson = { personId -> navController.navigate("person/$personId") }
                )
            }
        }

        composable(
            route = "event/{eventId}/registration/{mode}",
            arguments = listOf(navArgument("eventId") { type = NavType.LongType }, navArgument("mode") { type = NavType.StringType }),
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) }
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getLong("eventId")
            val modeStr = backStackEntry.arguments?.getString("mode")
            val coroutineScope = rememberCoroutineScope()
            var loading by remember { mutableStateOf(true) }
            var error by remember { mutableStateOf<String?>(null) }
            var evJson by remember { mutableStateOf<JsonObject?>(null) }

            LaunchedEffect(eventId) {
                loading = true
                try {
                    if (eventId == null) {
                        error = "Missing event id"
                    } else {
                        val svc = ServiceLocator.eventService
                        evJson = withContext(Dispatchers.Default) { svc.fetchEventById(eventId) }
                    }
                } catch (ex: Exception) {
                    error = ex.message
                } finally {
                    loading = false
                }
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error ?: "Chyba") }
            } else {
                evJson?.let { ev ->
                    val trainers = (ev["eventTrainersList"] as? JsonArray) ?: JsonArray(emptyList())
                    val registrations = (ev["eventRegistrationsList"] as? JsonArray) ?: JsonArray(emptyList())

                    val myPersonIdState = remember { mutableStateOf<String?>(null) }
                    val myCoupleIdsState = remember { mutableStateOf<List<String>>(emptyList()) }

                    LaunchedEffect(Unit) {
                        try {
                            val pid = withContext(Dispatchers.Default) { ServiceLocator.userService.getCachedPersonId() }
                            myPersonIdState.value = pid
                        } catch (_: Exception) {}
                        try {
                            val cids = withContext(Dispatchers.Default) { ServiceLocator.userService.getCachedCoupleIds() }
                            myCoupleIdsState.value = cids
                        } catch (_: Exception) {}
                    }

                    val mode = when (modeStr) {
                        "register" -> RegMode.Register
                        "edit" -> RegMode.Edit
                        "delete" -> RegMode.Delete
                        else -> RegMode.Register
                    }

                    val regResultMessage = remember { mutableStateOf<String?>(null) }

                    Column {
                        val safeEventId = eventId ?: 0L
                        RegistrationScreen(
                            eventId = safeEventId,
                            mode = mode,
                            trainers = trainers,
                            registrations = registrations,
                            myPersonId = myPersonIdState.value,
                            myCoupleIds = myCoupleIdsState.value,
                            enableNotes = (ev["enableNotes"] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull ?: false,
                            eventType = (ev["type"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull,
                            onClose = { navController.navigateUp() },
                            onRegister = { regs ->
                                val regsJson = regs.map { r ->
                                    buildJsonObject {
                                        put("eventId", JsonPrimitive(eventId))
                                        if (r.personId != null) put("personId", JsonPrimitive(r.personId))
                                        if (r.coupleId != null) put("coupleId", JsonPrimitive(r.coupleId))
                                        put("lessons", JsonArray(r.lessons.map { l -> buildJsonObject { put("trainerId", JsonPrimitive(l.trainerId)); put("lessonCount", JsonPrimitive(l.lessonCount)) } }))
                                        if (r.note != null) put("note", JsonPrimitive(r.note))
                                    }
                                }
                                val resp = withContext(Dispatchers.Default) {
                                    ServiceLocator.eventService.registerToEventMany(JsonArray(regsJson))
                                }
                                val jsonObj = resp?.jsonObject ?: throw Exception("Network error")
                                val errors = jsonObj["errors"]
                                if (errors != null) throw Exception("Server errors: $errors")
                                val data = jsonObj["data"]?.jsonObject
                                data?.get("registerToEventMany")?.jsonObject?.get("eventRegistrations")
                                    ?: throw Exception("Unexpected response: $resp")
                            },
                            onSetLessonDemand = { registrationId, trainerId, lessonCount ->
                                coroutineScope.launch {
                                    try {
                                        val success = withContext(Dispatchers.Default) {
                                            ServiceLocator.eventService.setLessonDemand(registrationId, trainerId, lessonCount)
                                        }
                                        if (success) {
                                            regResultMessage.value = "Uloženo"
                                        } else {
                                            regResultMessage.value = "Chyba při ukládání"
                                        }
                                    } catch (_: Exception) {}
                                }
                            },
                            onSetNote = { registrationId, note ->
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.Default) {
                                            ServiceLocator.eventService.setRegistrationNote(registrationId, note)
                                        }
                                    } catch (_: Exception) {}
                                }
                            },
                            onDelete = { registrationId ->
                                val resp = withContext(Dispatchers.Default) {
                                    ServiceLocator.eventService.deleteEventRegistration(registrationId)
                                }
                                val jsonObj = resp?.jsonObject ?: throw Exception("Network error")
                                val errors = jsonObj["errors"]
                                if (errors != null) throw Exception("Server errors: $errors")
                                val data = jsonObj["data"]?.jsonObject
                                data?.get("cancelRegistration")?.jsonObject?.get("clientMutationId")
                                    ?: throw Exception("Unexpected response: $resp")
                            }
                        )

                        regResultMessage.value?.let { msg ->
                            Text(msg, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        composable(
            route = "notice/{noticeId}",
            arguments = listOf(navArgument("noticeId") { type = NavType.LongType }),
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) { backStackEntry ->
            val noticeId = backStackEntry.arguments?.getLong("noticeId")
            noticeId?.let { nid ->
                NoticeScreen(
                    announcementId = nid,
                    onBack = { navController.navigateUp() }
                )
            }
        }

        composable(
            route = "profile",
            enterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            exitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) },
            popEnterTransition = { slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) },
            popExitTransition = { slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) }
        ) {
            ProfileScreen(
                onLogout = onLogout,
                onBack = { navController.navigateUp() }
            )
        }
    }
}
