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
import ui.theme.AppTheme

@Composable
@Preview
fun App() {
    AppTheme {
        var current by remember { mutableStateOf<Screen>(Screen.Overview) }

        Scaffold(
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
                    Screen.Overview -> OverviewScreen()
                    Screen.Calendar -> CalendarScreen()
                    Screen.Board -> BoardScreen()
                    Screen.Events -> EventsScreen()
                    Screen.Other -> OtherScreen()
                }
            }
        }
    }
}
