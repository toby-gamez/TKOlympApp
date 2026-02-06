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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.tkolympapp.Screens.CalendarViewScreen
import com.tkolymp.tkolympapp.Screens.PeopleScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
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
            OtherScreen(onProfileClick = { navController.navigate("profile") }, onPeopleClick = { navController.navigate("people") }, bottomPadding = bottomPadding)
        }

        composable(
            route = "people",
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
            PeopleScreen(onPersonClick = { id -> navController.navigate("person/$id") }, onBack = { navController.navigateUp() })
        }

        composable(
            route = "person/{personId}",
            arguments = listOf(navArgument("personId") { type = NavType.StringType }),
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
            val personId = backStackEntry.arguments?.getString("personId")
            personId?.let { pid ->
                com.tkolymp.tkolympapp.Screens.PersonPage(
                    personId = pid,
                    onBack = { navController.navigateUp() },
                    onOpenCouple = { /* no-op for now */ }
                )
            }
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
            CalendarViewScreen(
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
                    onBack = { navController.navigateUp() },
                    onOpenRegistration = { mode, _ ->
                        navController.navigate("event/$eid/registration/$mode")
                    }
                )
            }
        }

        // registration routes for event (single composable with mode)
        composable(
            route = "event/{eventId}/registration/{mode}",
            arguments = listOf(navArgument("eventId") { type = NavType.LongType }, navArgument("mode") { type = NavType.StringType }),
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            }
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
                    val svc = ServiceLocator.eventService
                    evJson = withContext(Dispatchers.IO) { svc.fetchEventById(eventId!!) }
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

                    val coroutineScope = rememberCoroutineScope()
                    val myPersonIdState = remember { mutableStateOf<String?>(null) }
                    val myCoupleIdsState = remember { mutableStateOf<List<String>>(emptyList()) }

                    LaunchedEffect(Unit) {
                        try {
                            val pid = withContext(Dispatchers.IO) { ServiceLocator.userService.getCachedPersonId() }
                            myPersonIdState.value = pid
                        } catch (_: Exception) {}
                        try {
                            val cids = withContext(Dispatchers.IO) { ServiceLocator.userService.getCachedCoupleIds() }
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
                        RegistrationScreen(
                        eventId = eventId!!,
                        mode = mode,
                        trainers = trainers,
                        registrations = registrations,
                        myPersonId = myPersonIdState.value,
                        myCoupleIds = myCoupleIdsState.value,
                        onClose = { navController.navigateUp() },
                        onRegister = { regs ->
                            coroutineScope.launch {
                                try {
                                    val regsJson = regs.map { r ->
                                        buildJsonObject {
                                            // include eventId so server knows which event these registrations belong to
                                            put("eventId", JsonPrimitive(eventId))
                                            if (r.personId != null) put("personId", JsonPrimitive(r.personId))
                                            if (r.coupleId != null) put("coupleId", JsonPrimitive(r.coupleId))
                                            put("lessons", JsonArray(r.lessons.map { l -> buildJsonObject { put("trainerId", JsonPrimitive(l.trainerId)); put("lessonCount", JsonPrimitive(l.lessonCount)) } }))
                                        }
                                    }
                                    val resp = withContext(Dispatchers.IO) {
                                        ServiceLocator.eventService.registerToEventMany(JsonArray(regsJson))
                                    }

                                    if (resp == null) {
                                        regResultMessage.value = "Network error"
                                    } else {
                                        val jsonObj = resp.jsonObject
                                        val errors = jsonObj["errors"]
                                        if (errors != null) {
                                            regResultMessage.value = "Server errors: ${errors}"
                                        } else {
                                            val data = jsonObj["data"]?.jsonObject
                                            val created = data?.get("registerToEventMany")?.jsonObject?.get("eventRegistrations")
                                            if (created != null) {
                                                navController.navigateUp()
                                            } else {
                                                regResultMessage.value = "Unexpected response: ${resp}"
                                            }
                                        }
                                    }
                                } catch (ex: Exception) {
                                    regResultMessage.value = ex.message ?: "Unknown error"
                                }
                            }
                        },
                        onSetLessonDemand = { registrationId, trainerId, lessonCount ->
                            coroutineScope.launch {
                                try {
                                    val success = withContext(Dispatchers.IO) {
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
                        onDelete = { registrationId ->
                            coroutineScope.launch {
                                try {
                                    val resp = withContext(Dispatchers.IO) {
                                        ServiceLocator.eventService.deleteEventRegistration(registrationId)
                                    }

                                    if (resp == null) {
                                        regResultMessage.value = "Network error"
                                    } else {
                                        val jsonObj = resp.jsonObject
                                        val errors = jsonObj["errors"]
                                        if (errors != null) {
                                            regResultMessage.value = "Server errors: ${errors}"
                                        } else {
                                            val data = jsonObj["data"]?.jsonObject
                                            val canceled = data?.get("cancelRegistration")?.jsonObject?.get("clientMutationId")
                                            if (canceled != null) {
                                                navController.navigateUp()
                                            } else {
                                                regResultMessage.value = "Unexpected response: ${resp}"
                                            }
                                        }
                                    }
                                } catch (ex: Exception) {
                                    regResultMessage.value = ex.message ?: "Unknown error"
                                }
                            }
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

