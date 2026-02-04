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

@Composable
fun AppBottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = current is Screen.Overview,
            onClick = { onSelect(Screen.Overview) },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text(Screen.Overview.title) }
        )
        NavigationBarItem(
            selected = current is Screen.Calendar,
            onClick = { onSelect(Screen.Calendar) },
            icon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
            label = { Text(Screen.Calendar.title) }
        )
        NavigationBarItem(
            selected = current is Screen.Board,
            onClick = { onSelect(Screen.Board) },
            icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
            label = { Text(Screen.Board.title) }
        )
        NavigationBarItem(
            selected = current is Screen.Events,
            onClick = { onSelect(Screen.Events) },
            icon = { Icon(Icons.Default.Event, contentDescription = null) },
            label = { Text(Screen.Events.title) }
        )
        NavigationBarItem(
            selected = current is Screen.Other,
            onClick = { onSelect(Screen.Other) },
            icon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
            label = { Text(Screen.Other.title) }
        )
    }
}
