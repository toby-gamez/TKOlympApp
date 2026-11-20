package com.tkolymp.tkolympapp.screens
import com.tkolymp.tkolympapp.components.skeleton.SkeletonDetail
import com.tkolymp.shared.utils.daysSince
import com.tkolymp.tkolympapp.components.AppTopBar
import com.tkolymp.tkolympapp.ui.theme.Spacing
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.datetime.todayIn
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.tkolymp.tkolympapp.components.TopAppBar
import androidx.compose.runtime.Composable
import com.tkolymp.tkolympapp.util.SkeletonCrossfade
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.tkolymp.tkolympapp.platform.HtmlText
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.viewmodels.NoticeViewModel
import com.tkolymp.shared.language.AppStrings
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tkolymp.tkolympapp.components.HeaderIconFallback
import com.tkolymp.tkolympapp.components.HeaderImageShape
import com.tkolymp.tkolympapp.components.HeaderWithImage
import com.tkolymp.tkolympapp.components.InitialsAvatar
import com.tkolymp.tkolympapp.platform.FullscreenImageViewer
import com.tkolymp.tkolympapp.util.StaggeredItem
import com.tkolymp.tkolympapp.components.EmptyState
import com.tkolymp.tkolympapp.components.AnnouncementManageActions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.outlined.Campaign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeScreen(announcementId: Long, onBack: (() -> Unit)? = null, onEdit: ((Long) -> Unit)? = null) {
    val viewModel = viewModel<NoticeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(announcementId) {
        viewModel.load(announcementId, forceRefresh = true)
    }

    // Keep content visible during loading; SwipeToReload handles the refresh indicator.
    val a = state.announcement
    val fullScreenImageUrl = remember { mutableStateOf<String?>(null) }
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(a) { if (a != null) contentVisible = true }

    Scaffold(
        topBar = {
            AppTopBar(
                title = AppStrings.current.announcements.announcement,
                onBack = onBack,
                actions = {
                    if (a != null && !state.isOffline) {
                        AnnouncementManageActions(
                            announcementId = announcementId,
                            authorId = a.author?.id,
                            onEdit = onEdit,
                            onDeleted = { onBack?.invoke() }
                        )
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
            SkeletonCrossfade(a == null && state.isLoading) { showSkeleton ->
            if (showSkeleton) {
                SkeletonDetail()
            } else if (a == null) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        EmptyState(
                            title = AppStrings.current.announcements.noAnnouncementToShow,
                            icon = Icons.Outlined.Campaign,
                            fullPage = true,
                            modifier = Modifier.fillParentMaxSize()
                        )
                    }
                }
            } else {
                StaggeredItem(index = 0, visible = contentVisible, durationMs = 250) {
                Column(modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = Spacing.small, bottom = Spacing.large)
                ) {
                    fun formatIso(iso: String?): String? {
                        if (iso.isNullOrBlank()) return null
                        val ldt = parseToLocal(iso) ?: return iso
                        return formatShortDateTime(ldt.date, ldt.hour, ldt.minute)
                    }
                    val authorName = listOfNotNull(a.author?.uJmeno, a.author?.uPrijmeni).joinToString(" ").trim()
                    val updatedFormatted = formatIso(a.updatedAt)
                    val createdFormatted = formatIso(a.createdAt)
                    val today = remember { kotlin.time.Clock.System.todayIn(kotlinx.datetime.TimeZone.currentSystemDefault()) }
                    val age = daysSince(a.createdAt, today)
                    val strings = AppStrings.current
                    val ago = when {
                        age == null -> null
                        age == 0 -> strings.timeline.today
                        age < 14 -> strings.announcements.daysAgo.replace("%d", age.toString())
                        else -> null
                    }

                    // Author row: avatar, name, "date · ago" (and the edit time when the notice was changed)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (authorName.isNotBlank()) InitialsAvatar(name = authorName, size = 36.dp, fontSize = 13.sp)
                        Column {
                            Text(authorName.ifBlank { "–" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(createdFormatted, ago).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (updatedFormatted != null && updatedFormatted != createdFormatted) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                                    Text(" $updatedFormatted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    if (a.imageUrl != null) {
                        Spacer(modifier = Modifier.height(Spacing.default))
                        HeaderWithImage(
                            title = a.title ?: AppStrings.current.dialogs.noName,
                            imageUrl = a.imageUrl,
                            shape = HeaderImageShape.RoundedRect,
                            fallback = { HeaderIconFallback(Icons.Outlined.Campaign) }
                        )
                    } else {
                        Text(
                            a.title ?: AppStrings.current.dialogs.noName,
                            modifier = Modifier.padding(top = Spacing.default),
                            fontSize = 26.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!a.body.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(Spacing.medium))
                        HtmlText(
                            html = a.body ?: "",
                            modifier = Modifier.fillMaxWidth(),
                            textColor = MaterialTheme.colorScheme.onBackground,
                            linkColor = MaterialTheme.colorScheme.primary,
                            textSizeSp = 16f,
                            selectable = true,
                            onImageClick = { url -> fullScreenImageUrl.value = url }
                        )
                    }
                }
                } // StaggeredItem
            }
            }
        }

        fullScreenImageUrl.value?.let { url ->
            FullscreenImageViewer(imageUrl = url) { fullScreenImageUrl.value = null }
        }

        state.error?.let { err ->
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text(AppStrings.current.commonActions.ok) } },
                title = { Text(AppStrings.current.commonActions.error) },
                text = { Text(err.message) }
            )
        }
    }
}
