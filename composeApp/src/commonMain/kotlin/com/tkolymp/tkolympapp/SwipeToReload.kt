package com.tkolymp.tkolympapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToReload(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Always fill available size so the pull-to-refresh indicator is centered against a
    // stable width, even when `content` is momentarily empty (e.g. before data loads) —
    // otherwise the Box shrinks to zero width and the indicator renders at the left edge
    // before jumping to center once real content lays out.
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        content()
    }
}
