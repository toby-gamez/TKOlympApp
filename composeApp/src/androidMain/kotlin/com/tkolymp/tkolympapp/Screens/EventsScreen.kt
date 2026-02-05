package com.tkolymp.tkolympapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(bottomPadding: Dp = 0.dp) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Akce") })
        }
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding(), bottom = bottomPadding),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text("Akce", style = MaterialTheme.typography.headlineMedium)
    }
    }
}
