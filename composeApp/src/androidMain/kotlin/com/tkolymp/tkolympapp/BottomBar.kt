package com.tkolymp.tkolympapp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AppBottomBar(current: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(modifier = modifier) {
        NavigationBarItem(
            selected = current == "overview",
            onClick = { onSelect("overview") },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Přehled") }
        )
        NavigationBarItem(
            selected = current == "calendar",
            onClick = { onSelect("calendar") },
            icon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
            label = { Text("Kalendář") }
        )
        NavigationBarItem(
            selected = current == "board",
            onClick = { onSelect("board") },
            icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
            label = { Text("Nástěnka") }
        )
        NavigationBarItem(
            selected = current == "events",
            onClick = { onSelect("events") },
            icon = { Icon(Icons.Default.Event, contentDescription = null) },
            label = { Text("Akce") }
        )
        NavigationBarItem(
            selected = current == "other",
            onClick = { onSelect("other") },
            icon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
            label = { Text("Ostatní") }
        )
    }
}
