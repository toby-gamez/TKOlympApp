package com.tkolymp.tkolympapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

sealed class Screen(val key: String, val title: String) {
    object Overview : Screen("overview", "Přehled")
    object Calendar : Screen("calendar", "Kalendář")
    object Board : Screen("board", "Nástěnka")
    object Events : Screen("events", "Akce")
    object Other : Screen("other", "Ostatní")
}

@Composable
fun OverviewScreen() {
    SimpleCenteredScreen(title = Screen.Overview.title)
}

@Composable
fun CalendarScreen() {
    SimpleCenteredScreen(title = Screen.Calendar.title)
}

@Composable
fun BoardScreen() {
    SimpleCenteredScreen(title = Screen.Board.title)
}

@Composable
fun EventsScreen() {
    SimpleCenteredScreen(title = Screen.Events.title)
}

@Composable
fun OtherScreen() {
    SimpleCenteredScreen(title = Screen.Other.title)
}

@Composable
private fun SimpleCenteredScreen(title: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}
