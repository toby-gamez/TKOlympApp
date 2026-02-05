
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.tkolymp.shared.ServiceLocator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(bottomPadding: Dp = 0.dp, onOpenNotice: (Long) -> Unit = {}) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Aktuality", "Stálá nástěnka")
    var announcements by remember { mutableStateOf(listOf<com.tkolymp.shared.announcements.Announcement>()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
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
            PrimaryTabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            LaunchedEffect(selectedTab) {
                isLoading = true
                errorMsg = null
                try {
                    val sticky = selectedTab == 1
                    announcements = ServiceLocator.announcementService.getAnnouncements(sticky)
                } catch (t: Throwable) {
                    errorMsg = t.message ?: "Chyba při načítání"
                } finally {
                    isLoading = false
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    announcements.forEach { a ->
                        Column(modifier = Modifier.padding(4.dp)) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        a.id.toLongOrNull()?.let { nid -> onOpenNotice(nid) }
                                    },
                                colors = CardDefaults.cardColors()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(a.title ?: "(bez názvu)", style = MaterialTheme.typography.titleMedium)
                                    val authorName = listOfNotNull(a.author?.uJmeno, a.author?.uPrijmeni).joinToString(" ").trim()
                                    if (authorName.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(authorName, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val plainBody = Html.fromHtml(a.body ?: "", Html.FROM_HTML_MODE_LEGACY).toString()
                                    Text(
                                        plainBody,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (errorMsg != null) {
                AlertDialog(
                    onDismissRequest = { errorMsg = null },
                    confirmButton = {
                        TextButton(onClick = { errorMsg = null }) { Text("OK") }
                    },
                    title = { Text("Chyba při načítání příspěvků") },
                    text = { Text(errorMsg ?: "Neznámá chyba") }
                )
            }
        }
    }
}

// `PrimaryTabRow` is provided in other screens (e.g. CalendarScreen). Use that implementation to avoid duplicates.
