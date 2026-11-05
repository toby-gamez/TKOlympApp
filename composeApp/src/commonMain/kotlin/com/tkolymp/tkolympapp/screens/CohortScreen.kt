package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.withImageWidth
import com.tkolymp.shared.viewmodels.CohortViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.ErrorState
import com.tkolymp.tkolympapp.components.HeaderIconFallback
import com.tkolymp.tkolympapp.components.HeaderImageShape
import com.tkolymp.tkolympapp.components.HeaderWithImage
import com.tkolymp.tkolympapp.components.InitialsAvatar
import com.tkolymp.tkolympapp.components.parseColorOrDefault
import com.tkolymp.tkolympapp.platform.HtmlText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CohortScreen(
    cohortId: String,
    onBack: () -> Unit = {},
    onOpenPerson: ((String) -> Unit)? = null,
    bottomPadding: Dp = 0.dp,
) {
    val vm = viewModel<CohortViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(cohortId) { vm.load(cohortId) }
    val cohort = state.cohort

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(cohort?.name ?: AppStrings.current.otherScreen.trainingGroups) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                    }
                }
            )
        }
    ) { padding ->
        SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch { vm.load(cohortId) } },
            modifier = Modifier.padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
        ) {
            if (cohort == null) {
                state.error?.let { ErrorState(error = it, onRetry = { scope.launch { vm.load(cohortId) } }) }
                return@SwipeToReload
            }
            val accent = try { parseColorOrDefault(cohort.colorRgb) } catch (_: Exception) { MaterialTheme.colorScheme.primary }
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeaderWithImage(
                    title = cohort.name ?: AppStrings.current.dialogs.noName,
                    imageUrl = withImageWidth(cohort.imageUrl, 480),
                    shape = HeaderImageShape.RoundedRect,
                    keepImageRatio = true,
                    fallback = { HeaderIconFallback(Icons.Outlined.Groups, square = true) }
                ) {
                    cohort.groupName?.takeIf { it.isNotBlank() }?.let { SuggestionChip(onClick = {}, label = { Text(it) }) }
                    cohort.location?.takeIf { it.isNotBlank() }?.let { loc ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(loc, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                val description = cohort.description?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString("\n")?.trim()
                if (!description.isNullOrBlank()) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        // The bar is drawn behind the content: HtmlText is a WebView and must not be
                        // measured with intrinsic sizes (it is drawn at zero height and crashes the renderer).
                        HtmlText(
                            html = description,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            linkColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawBehind {
                                    drawRoundRect(
                                        color = accent,
                                        topLeft = Offset(12.dp.toPx(), 12.dp.toPx()),
                                        size = Size(6.dp.toPx(), (size.height - 24.dp.toPx()).coerceAtLeast(0f)),
                                        cornerRadius = CornerRadius(3.dp.toPx())
                                    )
                                }
                                .padding(start = 30.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                        )
                    }
                }

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${AppStrings.current.people.cohortMembers} (${cohort.members.size})", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        if (cohort.members.isEmpty()) {
                            Text(
                                AppStrings.current.people.noCohortMembers,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        cohort.members.forEach { m ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(if (onOpenPerson != null) Modifier.clickable { onOpenPerson(m.id) } else Modifier)
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                InitialsAvatar(name = m.name, size = 36.dp, fontSize = 13.sp, imageUrl = withImageWidth(m.imageUrl, 96))
                                Spacer(Modifier.width(12.dp))
                                Text(m.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                if (m.isTrainer) Text(AppStrings.current.profile.trainer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
