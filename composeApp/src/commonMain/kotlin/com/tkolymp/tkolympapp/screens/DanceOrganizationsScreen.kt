package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.competitivedata.DanceFederation
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.viewmodels.DanceOrganizationsViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.EmptyState
import com.tkolymp.tkolympapp.components.ErrorState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DanceOrganizationsScreen(
    onBack: () -> Unit = {},
    onOpenOrganization: (code: String) -> Unit = {},
    bottomPadding: Dp = 0.dp
) {
    val viewModel = viewModel<DanceOrganizationsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.current.danceOrg.title) },
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
            onRefresh = { scope.launch { viewModel.load() } },
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
        ) {
            when {
                state.federations.isEmpty() && state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.federations.isEmpty() && state.error != null -> ErrorState(state.error!!, onRetry = { scope.launch { viewModel.load() } })
                state.federations.isEmpty() -> EmptyState(AppStrings.current.danceOrg.noDataYet, fullPage = true)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            AppStrings.current.danceOrg.dataTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(state.federations, key = { it.code }) { f -> FederationCard(f, onClick = { onOpenOrganization(f.code) }) }
                }
            }
        }
    }
}

/** Federation code as written in Czech/Slovak: ČSTS, SZTŠ (WDSF and unknown codes just upper-cased). */
internal fun federationLabel(code: String): String = when (code.lowercase()) {
    "csts" -> "ČSTS"
    "szts" -> "SZTŠ"
    else -> code.uppercase()
}

/** Flag of the federation's country, or the globe for the international WDSF; null for an unknown federation. */
internal fun federationEmoji(code: String): String? = when (code.lowercase()) {
    "csts" -> "\uD83C\uDDE8\uD83C\uDDFF"
    "szts" -> "\uD83C\uDDF8\uD83C\uDDF0"
    "wdsf" -> "\uD83C\uDF0D"
    else -> null
}

@Composable
private fun FederationCard(federation: DanceFederation, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    val flag = federationEmoji(federation.code)
                    if (flag != null) Text(flag, fontSize = 28.sp)
                    else Icon(Icons.Filled.Public, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(federationLabel(federation.code), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(federation.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
