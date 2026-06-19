package com.tkolymp.tkolympapp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import com.tkolymp.shared.language.AppStrings

@Composable
private fun AnimatedNavIcon(selected: Boolean, icon: ImageVector, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(if (selected) 1.2f else 1f) }

    LaunchedEffect(selected) {
        if (selected) {
            scale.animateTo(1.5f, animationSpec = tween(durationMillis = 150))
            scale.animateTo(
                targetValue = 1.2f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        } else {
            scale.animateTo(1f, animationSpec = tween(durationMillis = 200))
        }
    }

    Icon(icon, contentDescription = null, modifier = modifier.scale(scale.value))
}

@Composable
fun AppBottomBar(
    current: String,
    onSelect: (String) -> Unit,
    boardHasUnread: Boolean = false,
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier) {
        val calendarSelected = current == "calendar" || current == "timeline"

        NavigationBarItem(
            selected = current == "overview",
            onClick = { onSelect("overview") },
            icon = { AnimatedNavIcon(selected = current == "overview", icon = Icons.Default.Home) },
            label = { Text(AppStrings.current.navigation.overview) }
        )
        NavigationBarItem(
            selected = calendarSelected,
            onClick = { onSelect("calendar") },
            icon = { AnimatedNavIcon(selected = calendarSelected, icon = Icons.Default.CalendarToday) },
            label = { Text(AppStrings.current.navigation.calendar) }
        )
        NavigationBarItem(
            selected = current == "board",
            onClick = { onSelect("board") },
            icon = {
                val boardScale = remember { Animatable(if (current == "board") 1.2f else 1f) }
                LaunchedEffect(current == "board") {
                    if (current == "board") {
                        boardScale.animateTo(1.5f, animationSpec = tween(durationMillis = 150))
                        boardScale.animateTo(
                            targetValue = 1.2f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    } else {
                        boardScale.animateTo(1f, animationSpec = tween(durationMillis = 200))
                    }
                }
                BadgedBox(badge = { if (boardHasUnread) Badge() }) {
                    Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.scale(boardScale.value))
                }
            },
            label = { Text(AppStrings.current.navigation.board) }
        )
        NavigationBarItem(
            selected = current == "events",
            onClick = { onSelect("events") },
            icon = { AnimatedNavIcon(selected = current == "events", icon = Icons.Default.Event) },
            label = { Text(AppStrings.current.navigation.events) }
        )
        NavigationBarItem(
            selected = current == "other",
            onClick = { onSelect("other") },
            icon = { AnimatedNavIcon(selected = current == "other", icon = Icons.Default.MoreHoriz) },
            label = { Text(AppStrings.current.navigation.other) }
        )
    }
}
