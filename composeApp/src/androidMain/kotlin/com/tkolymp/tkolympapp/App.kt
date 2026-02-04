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
                        "calendar" -> TopAppBar(title = { Text("Kalendář") })
                        "board" -> TopAppBar(title = { Text("Nástěnka") })
                        "events" -> TopAppBar(title = { Text("Akce") })
                        "other" -> TopAppBar(title = { Text("Ostatní") })
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
                        "calendar" -> CalendarScreen()
                        "board" -> BoardScreen()
                        "events" -> EventsScreen()
                        "other" -> OtherScreen()
                        else -> {}
                    }
                }
            }
        }
    }
}
