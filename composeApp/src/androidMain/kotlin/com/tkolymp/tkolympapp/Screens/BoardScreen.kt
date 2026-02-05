package com.tkolymp.tkolympapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(bottomPadding: Dp = 0.dp) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Aktuality", "Stálá nástěnka")

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Nástěnka") })
        }
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding(), bottom = bottomPadding),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        PrimaryTabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedTab) {
            0 -> Text("Nástěnka", style = MaterialTheme.typography.bodyLarge)
            1 -> Text("Všechny příspěvky", style = MaterialTheme.typography.bodyLarge)
        }
    }
    }
}
