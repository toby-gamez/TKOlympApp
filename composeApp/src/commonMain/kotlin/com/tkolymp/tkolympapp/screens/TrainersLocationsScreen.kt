package com.tkolymp.tkolympapp.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import com.tkolymp.shared.club.Trainer
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.viewmodels.TrainersLocationsViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.InitialsAvatar
import com.tkolymp.tkolympapp.util.StaggeredItem
import kotlinx.coroutines.launch

private const val NAME_WEIGHT = 2.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainersLocationsScreen(onBack: () -> Unit = {}) {
    val vm = viewModel<TrainersLocationsViewModel>()
    val state by vm.state.collectAsState()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) { vm.load() }

    val scope = rememberCoroutineScope()
    var cardsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.clubData) { if (state.clubData != null) cardsVisible = true }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(AppStrings.current.otherScreen.trainersAndSpaces) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                }
            }
        )
    }) { padding ->
        SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch { vm.load() } },
            modifier = Modifier.padding(padding)
        ) {
            val club = state.clubData

            val visibleTrainers = club?.trainers?.filter { t ->
                if (t.isVisible == false) return@filter false
                val p = t.person
                val name = listOfNotNull(p?.firstName?.takeIf { it.isNotBlank() }, p?.lastName?.takeIf { it.isNotBlank() }).joinToString(" ")
                name.isNotBlank()
            } ?: emptyList()

            Column(modifier = Modifier.fillMaxSize()) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(AppStrings.current.people.tabOverview) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(AppStrings.current.people.tabWorkload) }
                    )
                }

                AnimatedContent(targetState = selectedTab, modifier = Modifier.fillMaxSize()) { tab ->
                    when (tab) {
                        1 -> TrainerHeatmapTab(
                            trainers = visibleTrainers,
                            counts = state.trainerLessonCounts
                        )
                        else -> {
                            if (state.error != null) {
                                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(state.error?.message ?: "")
                                }
                            } else if (club == null || (club.locations.isEmpty() && club.trainers.isEmpty())) {
                                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(AppStrings.current.commonActions.noData)
                                }
                            } else {
                                LazyColumn(modifier = Modifier) {
                                    item(key = "header_locations") {
                                        Text(AppStrings.current.people.trainingSpaces, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }

                                    val visibleLocations = club.locations.filter { loc ->
                                        val n = loc.name?.trim()
                                        !n.isNullOrBlank() && !n.equals("ZRUŠENO", ignoreCase = true)
                                    }

                                    if (visibleLocations.isNotEmpty()) {
                                        itemsIndexed(visibleLocations, key = { index, loc -> "loc_${index}_${loc.name ?: loc.hashCode()}" }) { index, loc ->
                                            StaggeredItem(index = index, visible = cardsVisible, baseDelayMs = 50) {
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                                        .clickable {
                                                            val q = loc.name ?: return@clickable
                                                            val encoded = q.map { c ->
                                                                when {
                                                                    c.isLetterOrDigit() || c in "-_.~" -> c.toString()
                                                                    c == ' ' -> "+"
                                                                    else -> "%${c.code.toString(16).uppercase()}"
                                                                }
                                                            }.joinToString("")
                                                            try { uriHandler.openUri("https://www.google.com/maps/search/?api=1&query=$encoded") } catch (_: Exception) { }
                                                        },
                                                    shape = RoundedCornerShape(16.dp),
                                                ) {
                                                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(imageVector = Icons.Filled.Place, contentDescription = AppStrings.current.people.trainingSpaces, modifier = Modifier.size(28.dp))
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Text(text = loc.name ?: AppStrings.current.dialogs.noName, style = MaterialTheme.typography.bodyLarge)
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        item(key = "no_locations") { Text(AppStrings.current.people.noTrainingSpaces, modifier = Modifier.padding(16.dp)) }
                                    }

                                    item(key = "header_trainers") {
                                        Text(AppStrings.current.profile.trainers, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }

                                    fun formatPrice(amount: Double?, currency: String?): String? {
                                        if (amount == null) return null
                                        val isWhole = amount.rem(1.0) == 0.0
                                        val amtStr = if (isWhole) amount.toInt().toString() else { val s = kotlin.math.round(amount * 100).toLong(); "${s / 100}.${kotlin.math.abs(s % 100).toString().padStart(2, '0')}" }
                                        val cur = currency?.uppercase()
                                        val c = AppStrings.current.profile.couple
                                        return when (cur) {
                                            "EUR" -> "$amtStr € /$c, 45'"
                                            "USD" -> "$$amtStr /$c, 45'"
                                            else -> "$amtStr,- /$c, 45'"
                                        }
                                    }

                                    if (visibleTrainers.isNotEmpty()) {
                                        itemsIndexed(visibleTrainers, key = { index, t -> "trainer_${index}_${t.person?.id ?: t.hashCode()}" }) { index, t ->
                                            StaggeredItem(index = index, visible = cardsVisible, baseDelayMs = 50) {
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(16.dp)
                                                ) {
                                                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            val p = t.person
                                                            val name = listOfNotNull(
                                                                p?.prefixTitle?.takeIf { it.isNotBlank() },
                                                                p?.firstName?.takeIf { it.isNotBlank() },
                                                                p?.lastName?.takeIf { it.isNotBlank() }
                                                            ).joinToString(" ")
                                                            val suffix = p?.suffixTitle?.takeIf { it.isNotBlank() }
                                                            val displayName = if (suffix != null) "$name, $suffix" else name
                                                            val avatarName = if (displayName.isBlank()) (p?.id ?: "?") else displayName
                                                            InitialsAvatar(name = avatarName, size = 28.dp, fontSize = 11.sp)
                                                            Spacer(modifier = Modifier.width(12.dp))
                                                            Column {
                                                                Text(text = if (displayName.isBlank()) (p?.id ?: "(trenér)") else displayName, style = MaterialTheme.typography.bodyLarge)
                                                            }
                                                        }
                                                        val priceStr = formatPrice(t.guestPrice45Min?.amount, t.guestPrice45Min?.currency)
                                                        if (priceStr != null) {
                                                            Text(text = priceStr, style = MaterialTheme.typography.bodyMedium)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        item(key = "no_trainers") { Text(AppStrings.current.people.noTrainers, modifier = Modifier.padding(16.dp)) }
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

@Composable
private fun TrainerHeatmapTab(
    trainers: List<Trainer>,
    counts: Map<String, IntArray>
) {
    val dayLabels = AppStrings.current.calendarView.weekDayAbbreviations
    val maxCount = remember(counts) {
        counts.values.flatMap { it.toList() }.maxOrNull()?.coerceAtLeast(1) ?: 1
    }
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant

    if (trainers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                AppStrings.current.people.heatmapNoData,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            AppStrings.current.people.heatmapPeriod,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Spacer(Modifier.weight(NAME_WEIGHT))
            dayLabels.forEach { label ->
                Text(
                    label,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(trainers, key = { index, t -> "h_${index}_${t.person?.id}" }) { _, trainer ->
                val fullName = listOfNotNull(
                    trainer.person?.firstName?.trim(),
                    trainer.person?.lastName?.trim()
                ).joinToString(" ")
                val dayArray = counts.keys
                    .firstOrNull { it.equals(fullName, ignoreCase = true) }
                    ?.let { counts[it] } ?: IntArray(7)
                val shortName = trainer.person?.let { p ->
                    val f = p.firstName?.trim()?.firstOrNull()?.let { c -> "$c." } ?: ""
                    val l = p.lastName?.trim() ?: ""
                    "$f $l".trim()
                } ?: "(trenér)"

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        shortName,
                        Modifier.weight(NAME_WEIGHT),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    for (d in 0..6) {
                        val cnt = dayArray[d]
                        val intensity = if (cnt == 0) 0f else 0.15f + (cnt.toFloat() / maxCount) * 0.85f
                        val bg = if (cnt == 0) surface.copy(alpha = 0.3f) else primary.copy(alpha = intensity)
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(bg),
                            contentAlignment = Alignment.Center
                        ) {
                            if (cnt > 0) {
                                Text(
                                    cnt.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (intensity > 0.6f) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
