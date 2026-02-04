package com.tkolymp.tkolympapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import ui.theme.AppTheme

@Composable
@Preview
fun App() {
    AppTheme {
        var current by remember { mutableStateOf("overview") }
        var loggedIn by remember { mutableStateOf<Boolean?>(null) }

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

        when (loggedIn) {
            null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            false -> LoginScreen(onSuccess = { loggedIn = true; current = "overview" })
            true -> Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = { AppBottomBar(current = current, onSelect = { current = it }) }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .safeContentPadding()
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    when (current) {
                        "overview" -> OverviewScreen()
                        "calendar" -> CalendarScreen()
                        "board" -> BoardScreen()
                        "events" -> EventsScreen()
                        "other" -> OtherScreen()
                    }
                }
            }
        }
    }
}
