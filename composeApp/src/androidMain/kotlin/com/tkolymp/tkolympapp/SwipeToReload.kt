package com.tkolymp.tkolympapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

@Composable
fun SwipeToReload(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val state = rememberSwipeRefreshState(isRefreshing)
    SwipeRefresh(state = state, onRefresh = onRefresh, modifier = modifier) {
        content()
    }
}
