package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.tkolymp.tkolympapp.components.AppTopBar
import com.tkolymp.tkolympapp.components.InitialsAvatar
import com.tkolymp.tkolympapp.components.QuickChip
import com.tkolymp.tkolympapp.components.cardLineBorder
import com.tkolymp.tkolympapp.components.surfaceCardColors
import com.tkolymp.tkolympapp.ui.theme.BrandLightPrimary
import com.tkolymp.tkolympapp.ui.theme.Spacing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.tkolymp.tkolympapp.components.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tkolymp.tkolympapp.util.StaggeredItem
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.viewmodels.LeaderboardViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.ui.Alignment as ComposeAlignment
import com.tkolymp.tkolympapp.components.ErrorState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(onBack: () -> Unit = {}, bottomPadding: Dp = 0.dp) {
    val viewModel = viewModel<LeaderboardViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentPersonId = remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // people map used for cohort lookup and group chips
    val peopleById = remember { mutableStateOf<Map<String, com.tkolymp.shared.people.Person>>(emptyMap()) }
    var selectedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        viewModel.loadLeaderboard()
;        // try to load current person id for highlighting
        try {
            currentPersonId.value = withContext(Dispatchers.Default) { com.tkolymp.shared.ServiceLocator.userService.getCachedPersonId() }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { /* ignore */ }
        // Use people loaded by ViewModel (it already includes offline fallback)
        peopleById.value = viewModel.state.value.peopleById
    }

    Scaffold(
        topBar = { AppTopBar(title = AppStrings.current.leaderboard.leaderboardTitle, onBack = onBack) }
    ) { padding ->
        SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch { viewModel.loadLeaderboard() } },
            modifier = Modifier.padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // build cohort group list from loaded people
                val groups = remember(peopleById.value) {
                    peopleById.value.values
                        .flatMap { p -> p.cohortMembershipsList.mapNotNull { it.cohort } }
                        .mapNotNull { c -> if (c.isVisible == false) null else c.id?.let { id -> id to (c.name ?: id) } }
                        .distinctBy { it.first }
                }

                // compute displayed entries filtered by selected groups (client-side filtering)
                val entries = state.rankings
                val displayed = remember(entries, selectedGroups, peopleById.value, groups) {
                    if (selectedGroups.isEmpty() || selectedGroups.size == groups.size) entries
                    else entries.filter { item ->
                        val pid = item.personId ?: return@filter false
                        val person = peopleById.value[pid] ?: return@filter false
                        person.cohortMembershipsList
                            .mapNotNull { it.cohort }
                            .filter { it.isVisible != false }
                            .any { it.id?.let { id -> selectedGroups.contains(id) } == true }
                    }
                }

                when {
                    state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.error != null -> ErrorState(message = state.error?.message)
                    else -> {
                        val myEntry = remember(state.rankings, currentPersonId.value) {
                            currentPersonId.value?.let { pid -> state.rankings.find { it.personId == pid } }
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            // filter chips (All + per-cohort)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(start = Spacing.default, end = Spacing.default, top = Spacing.small),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                QuickChip(
                                    label = AppStrings.current.commonActions.all,
                                    selected = selectedGroups.isEmpty() || selectedGroups.size == groups.size,
                                    onClick = { selectedGroups = emptySet() }
                                )
                                groups.forEach { (id, name) ->
                                    QuickChip(
                                        label = name,
                                        selected = selectedGroups.contains(id),
                                        onClick = { selectedGroups = if (selectedGroups.contains(id)) selectedGroups - id else selectedGroups + id }
                                    )
                                }
                            }

                            var entranceVisible by remember { mutableStateOf(false) }
                            LaunchedEffect(state.rankings.isNotEmpty()) { if (state.rankings.isNotEmpty()) entranceVisible = true }

                            fun scoreText(v: Double?): String = v?.let { if (it % 1.0 == 0.0) it.toInt().toString() else formatScore(it) } ?: "-"
                            fun nameOf(item: com.tkolymp.shared.people.ScoreboardEntry) = listOfNotNull(item.personFirstName, item.personLastName).joinToString(" ")

                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(top = 14.dp, bottom = if (myEntry != null) 84.dp else Spacing.default),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                                ) {
                                    val hasPodium = displayed.size >= 3
                                    if (hasPodium) {
                                        item {
                                            // 2nd, 1st, 3rd — the winner stands tallest in the middle
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.default),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.Bottom
                                            ) {
                                                listOf(1, 0, 2).forEach { i ->
                                                    val item = displayed[i]
                                                    PodiumColumn(
                                                        name = nameOf(item),
                                                        place = i + 1,
                                                        rank = item.ranking ?: (i + 1),
                                                        points = scoreText(item.totalScore),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(Spacing.small))
                                        }
                                    }
                                    val rest = if (hasPodium) displayed.drop(3) else displayed
                                    itemsIndexed(rest, key = { _, item -> item.personId ?: item.hashCode() }) { index, item ->
                                        val rank = item.ranking ?: ((if (hasPodium) 3 else 0) + index + 1)
                                        val isCurrent = item.personId != null && item.personId == currentPersonId.value
                                        val name = nameOf(item)
                                        StaggeredItem(index = index, visible = entranceVisible, baseDelayMs = 40, durationMs = 250, modifier = Modifier.animateItem()) {
                                            Card(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.default),
                                                shape = RoundedCornerShape(16.dp),
                                                colors = if (isCurrent) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)) else surfaceCardColors(),
                                                border = cardLineBorder()
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalAlignment = ComposeAlignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
                                                ) {
                                                    Text("$rank", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    InitialsAvatar(name = name, size = 34.dp, fontSize = 12.sp)
                                                    Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium)
                                                    Text(scoreText(item.totalScore), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    }
                                }

                                myEntry?.let { entry ->
                                    val rank = entry.ranking ?: (state.rankings.indexOf(entry) + 1)
                                    val name = nameOf(entry)
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .padding(horizontal = Spacing.default, vertical = Spacing.small),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = BrandLightPrimary)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = ComposeAlignment.CenterVertically
                                        ) {
                                            Text("$rank. $name", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text(scoreText(entry.totalScore), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatScore(v: Double): String {
    if (v % 1.0 == 0.0) return v.toInt().toString()
    val scaled = kotlin.math.round(v * 10).toLong()
    return "${scaled / 10}.${kotlin.math.abs(scaled % 10)}"
}

/** One step of the podium: ringed avatar, name and a block whose height reflects the position; [rank] is the real (possibly filtered-out-of) ranking shown. */
@Composable
private fun PodiumColumn(name: String, place: Int, rank: Int, points: String, modifier: Modifier = Modifier) {
    val first = place == 1
    val avatarSize = if (first) 72.dp else 58.dp
    val blockHeight = when (place) { 1 -> 96.dp; 2 -> 72.dp; else -> 56.dp }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier.size(avatarSize).border(3.dp, if (first) BrandLightPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f), CircleShape).padding(5.dp),
            contentAlignment = Alignment.Center
        ) {
            InitialsAvatar(name = name, size = avatarSize - 10.dp, fontSize = 16.sp)
        }
        Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(blockHeight)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                .background(if (first) BrandLightPrimary else MaterialTheme.colorScheme.surface)
                .then(if (first) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 4.dp, bottomEnd = 4.dp))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("$rank", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = if (first) Color.White else MaterialTheme.colorScheme.onSurface)
            Text(points, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = if (first) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
