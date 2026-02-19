
package com.tkolymp.tkolympapp

import android.text.Html
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.viewmodels.BoardViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(bottomPadding: Dp = 0.dp, onOpenNotice: (Long) -> Unit = {}) {
    val viewModel = remember { BoardViewModel() }
    val state by viewModel.state.collectAsState()
    val tabs = listOf("Aktuality", "Stálá nástěnka")
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Nástěnka") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = bottomPadding),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            PrimaryTabRow(selectedTabIndex = state.selectedTab, modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(title) }
                    )
                }
            }

            LaunchedEffect(state.selectedTab) {
                // load announcements whenever selected tab changes
                scope.launch { viewModel.loadAnnouncements(forceRefresh = false) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SwipeToReload(
                isRefreshing = state.isLoading,
                onRefresh = { scope.launch { viewModel.loadAnnouncements(forceRefresh = true) } },
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val announcements = state.currentAnnouncements.filterIsInstance<com.tkolymp.shared.announcements.Announcement>()
                    announcements.forEach { a ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clickable {
                                    a.id.toLongOrNull()?.let { nid -> onOpenNotice(nid) }
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(a.title ?: "(bez názvu)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                val authorName = listOfNotNull(a.author?.uJmeno, a.author?.uPrijmeni).joinToString(" ").trim()
                                if (authorName.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(authorName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                val plainBody = Html.fromHtml(a.body ?: "", Html.FROM_HTML_MODE_LEGACY).toString()
                                Text(
                                    plainBody,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (state.error != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.clearError() },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
                    },
                    title = { Text("Chyba při načítání příspěvků") },
                    text = { Text(state.error ?: "Neznámá chyba") }
                )
            }
        }
    }
}

// `PrimaryTabRow` is provided in other screens (e.g. CalendarScreen). Use that implementation to avoid duplicates.
