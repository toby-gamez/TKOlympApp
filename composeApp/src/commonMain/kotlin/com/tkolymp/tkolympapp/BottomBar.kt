package com.tkolymp.tkolympapp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tkolymp.shared.language.AppStrings

@Composable
fun AppBottomBar(
    current: String,
    onSelect: (String) -> Unit,
    boardHasUnread: Boolean = false,
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier) {
        NavigationBarItem(
            selected = current == "overview",
            onClick = { onSelect("overview") },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text(AppStrings.current.navigation.overview) }
        )
        NavigationBarItem(
            selected = current == "calendar" || current == "timeline",
            onClick = { onSelect("calendar") },
            icon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
            label = { Text(AppStrings.current.navigation.calendar) }
        )
        NavigationBarItem(
            selected = current == "board",
            onClick = { onSelect("board") },
            icon = {
                BadgedBox(badge = { if (boardHasUnread) Badge() }) {
                    Icon(Icons.Default.Dashboard, contentDescription = null)
                }
            },
            label = { Text(AppStrings.current.navigation.board) }
        )
        NavigationBarItem(
            selected = current == "events",
            onClick = { onSelect("events") },
            icon = { Icon(Icons.Default.Event, contentDescription = null) },
            label = { Text(AppStrings.current.navigation.events) }
        )
        NavigationBarItem(
            selected = current == "other",
            onClick = { onSelect("other") },
            icon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
            label = { Text(AppStrings.current.navigation.other) }
        )
    }
}
