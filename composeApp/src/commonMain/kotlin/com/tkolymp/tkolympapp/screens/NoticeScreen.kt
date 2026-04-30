package com.tkolymp.tkolympapp.screens
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.shared.utils.formatShortDateTime
import com.tkolymp.shared.utils.parseToLocal
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tkolymp.tkolympapp.platform.HtmlText
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.viewmodels.NoticeViewModel
import com.tkolymp.shared.language.AppStrings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeScreen(announcementId: Long, onBack: (() -> Unit)? = null) {
    val viewModel = viewModel<NoticeViewModel>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(announcementId) {
        viewModel.load(announcementId, forceRefresh = true)
    }

    // Keep content visible during loading; SwipeToReload handles the refresh indicator.
    val a = state.announcement

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.current.announcements.announcement) },
                navigationIcon = {
                    onBack?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                        }
                    }
                }
            )
        }
    ) { padding ->
        val scope = rememberCoroutineScope()
        SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch { viewModel.load(announcementId, forceRefresh = true) } },
            modifier = Modifier.padding(padding)
        ) {
            if (a == null) {
                Column(modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(AppStrings.current.announcements.noAnnouncementToShow, modifier = Modifier.padding(16.dp))
                }
            } else {
                Column(modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
                ) {
                    Text(a.title ?: AppStrings.current.dialogs.noName, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))

                    fun formatIso(iso: String?): String? {
                        if (iso.isNullOrBlank()) return null
                        val ldt = parseToLocal(iso) ?: return iso
                        return formatShortDateTime(ldt.date, ldt.hour, ldt.minute)
                    }
                    val authorName = listOfNotNull(a.author?.uJmeno, a.author?.uPrijmeni).joinToString(" ").trim()
                    val updatedFormatted = formatIso(a.updatedAt)
                    val createdFormatted = formatIso(a.createdAt)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(
                                    if (authorName.isNotBlank()) authorName else "–",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (updatedFormatted != null) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Text(updatedFormatted, style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Text(createdFormatted ?: "–", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    if (!a.body.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                val bodySizeSp = MaterialTheme.typography.bodyMedium.fontSize.value
                                HtmlText(
                                    html = a.body ?: "",
                                    modifier = Modifier.fillMaxWidth(),
                                    textColor = MaterialTheme.colorScheme.onBackground,
                                    linkColor = MaterialTheme.colorScheme.primary,
                                    textSizeSp = bodySizeSp,
                                    selectable = true
                                )
                            }
                        }
                    }
                }
            }
        }

        state.error?.let { err ->
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text(AppStrings.current.commonActions.ok) } },
                title = { Text(AppStrings.current.commonActions.error) },
                text = { Text(err ?: "Neznámá chyba") }
            )
        }
    }
}
