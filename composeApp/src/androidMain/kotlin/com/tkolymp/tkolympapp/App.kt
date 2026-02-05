package com.tkolymp.tkolympapp

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    AppTheme {
        var current by remember { mutableStateOf("overview") }
        var loggedIn by remember { mutableStateOf<Boolean?>(null) }
        var activeEventId by remember { mutableStateOf<Long?>(null) }
        var weekOffset by remember { mutableStateOf(0) }
        var previousScreen by remember { mutableStateOf<String?>(null) }

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
            topBar = {
                if (loggedIn == true) {
                    when (current) {
                        "overview" -> TopAppBar(title = { Text("Přehled") })
                        "calendar" -> TopAppBar(
                            title = { Text("Kalendář") },
                            actions = {
                                IconButton(onClick = { current = "timeline" }) {
                                    Icon(Icons.Default.ViewTimeline, contentDescription = "Timeline zobrazení")
                                }
                                IconButton(onClick = { weekOffset -= 1 }) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Předchozí týden")
                                }
                                TextButton(onClick = { weekOffset = 0 }) {
                                    Text("dnes")
                                }
                                IconButton(onClick = { weekOffset += 1 }) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Následující týden")
                                }
                            }
                        )
                        "timeline" -> TopAppBar(
                            title = { Text("Timeline") },
                            navigationIcon = {
                                IconButton(onClick = { current = "calendar" }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Zpět")
                                }
                            }
                        )
                        "event" -> TopAppBar(
                            title = { Text("Událost") },
                            navigationIcon = {
                                IconButton(onClick = { 
                                    current = previousScreen ?: "calendar"
                                    activeEventId = null
                                    previousScreen = null
                                }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Zpět")
                                }
                            }
                        )
                        "board" -> TopAppBar(title = { Text("Nástěnka") })
                        "events" -> TopAppBar(title = { Text("Akce") })
                        "other" -> TopAppBar(title = { Text("Ostatní") })
                        "profile" -> TopAppBar(
                            title = { Text("Můj profil") },
                            navigationIcon = {
                                IconButton(onClick = { 
                                    current = previousScreen ?: "other"
                                    previousScreen = null
                                }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Zpět")
                                }
                            }
                        )
                        else -> {}
                    }
                }
            },
            bottomBar = {
                if (loggedIn == true) AppBottomBar(current = current, onSelect = { current = it })
            }
        ) { padding ->
            val contentPadding = PaddingValues(
                start = 0.dp,
                top = padding.calculateTopPadding(),
                end = 0.dp,
                bottom = padding.calculateBottomPadding()
            )

            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                when (loggedIn) {
                    null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    false -> LoginScreen(onSuccess = { loggedIn = true; current = "overview" })
                            true -> when (current) {
                            "overview" -> OverviewScreen()
                            "calendar" -> CalendarScreen(
                                weekOffset = weekOffset,
                                onWeekOffsetChange = { weekOffset = it },
                                onOpenEvent = { id -> 
                                    previousScreen = "calendar"
                                    activeEventId = id
                                    current = "event"
                                }
                            )
                            "timeline" -> com.tkolymp.tkolympapp.calendar.CalendarViewScreen(
                                onEventClick = { id -> 
                                    previousScreen = "timeline"
                                    activeEventId = id
                                    current = "event"
                                }
                            )
                            "event" -> activeEventId?.let { eid -> 
                                EventScreen(
                                    eventId = eid,
                                    onBack = { 
                                        current = previousScreen ?: "calendar"
                                        activeEventId = null
                                        previousScreen = null
                                    }
                                )
                            } ?: run { /* no-op */ }
                            "board" -> BoardScreen()
                            "events" -> EventsScreen()
                            "other" -> OtherScreen(onProfileClick = { 
                                previousScreen = "other"
                                current = "profile"
                            })
                            "profile" -> ProfileScreen(
                                onLogout = { loggedIn = false; current = "overview" },
                                onBack = { 
                                    current = previousScreen ?: "other"
                                    previousScreen = null
                                }
                            )
                            else -> {}
                        }
                }
            }
        }
    }
}
