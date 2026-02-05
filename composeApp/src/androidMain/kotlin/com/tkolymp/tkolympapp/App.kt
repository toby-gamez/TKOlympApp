package com.tkolymp.tkolympapp

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    AppTheme {
        var loggedIn by remember { mutableStateOf<Boolean?>(null) }
        var weekOffset by remember { mutableIntStateOf(0) }
        val navController = rememberNavController()
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = currentBackStackEntry?.destination?.route

        val ctx = LocalContext.current
        if (!LocalInspectionMode.current) {
            LaunchedEffect(Unit) {
                try {
                    com.tkolymp.shared.initNetworking(ctx, "https://api.rozpisovnik.cz/graphql")
                    val has = try { com.tkolymp.shared.ServiceLocator.authService.hasToken() } catch (_: Throwable) { false }
                    loggedIn = has
                } catch (_: Throwable) {
                    loggedIn = false
                }
            }
        } else {
            if (loggedIn == null) loggedIn = false
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            bottomBar = {
                AnimatedVisibility(
                    visible = loggedIn == true && currentRoute in listOf("overview", "calendar", "board", "events", "other"),
                    enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)),
                    exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300))
                ) {
                    AppBottomBar(current = currentRoute ?: "overview", onSelect = { 
                        navController.navigate(it) {
                            popUpTo("overview") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    })
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                when (loggedIn) {
                    null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                        CircularProgressIndicator() 
                    }
                    false -> LoginScreen(onSuccess = { 
                        loggedIn = true
                        navController.navigate("overview") {
                            popUpTo(0) { inclusive = true }
                        }
                    })
                    true -> AppNavHost(
                        navController = navController,
                        weekOffset = weekOffset,
                        onWeekOffsetChange = { weekOffset = it },
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
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    weekOffset: Int,
    onWeekOffsetChange: (Int) -> Unit,
    onLogout: () -> Unit,
    bottomPadding: Dp
) {
    NavHost(
        navController = navController,
        startDestination = "overview"
    ) {
        // Hlavní obrazovky bez animací
        composable(
            route = "overview",
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            OverviewScreen(bottomPadding = bottomPadding)
        }
        
        composable(
            route = "calendar",
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            CalendarScreen(
                weekOffset = weekOffset,
                onWeekOffsetChange = onWeekOffsetChange,
                onOpenEvent = { id -> navController.navigate("event/$id") },
                onNavigateTimeline = { navController.navigate("timeline") },
                bottomPadding = bottomPadding
            )
        }
        
        composable(
            route = "board",
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            BoardScreen(bottomPadding = bottomPadding)
        }
        
        composable(
            route = "events",
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            EventsScreen(bottomPadding = bottomPadding)
        }
        
        composable(
            route = "other",
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            OtherScreen(onProfileClick = { navController.navigate("profile") }, bottomPadding = bottomPadding)
        }
        
        // Vedlejší obrazovky s horizontálními přechody
        composable(
            route = "timeline",
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(400)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(400)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(400)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(400)
                )
            }
        ) {
            com.tkolymp.tkolympapp.calendar.CalendarViewScreen(
                onEventClick = { id -> navController.navigate("event/$id") },
                onBack = { navController.navigateUp() }
            )
        }
        
        composable(
            route = "event/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.LongType }),
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(400)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(400)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(400)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(400)
                )
            }
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getLong("eventId")
            eventId?.let { eid ->
                EventScreen(
                    eventId = eid,
                    onBack = { navController.navigateUp() }
                )
            }
        }
        
        composable(
            route = "profile",
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(400)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(400)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(400)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(400)
                )
            }
        ) {
            ProfileScreen(
                onLogout = onLogout,
                onBack = { navController.navigateUp() }
            )
        }
    }
}
