package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.competitivedata.FederationEventSummary
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.viewmodels.EventsViewMode
import com.tkolymp.shared.viewmodels.FederationEventsViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.EmptyState
import com.tkolymp.tkolympapp.components.ErrorState
import com.tkolymp.tkolympapp.components.OfflineBanner
import com.tkolymp.tkolympapp.components.map.EventMap
import com.tkolymp.tkolympapp.components.map.MapRegion
import com.tkolymp.tkolympapp.components.map.monthColor
import kotlinx.coroutines.launch

/** "2026-12-19" -> "19. 12. 2026"; a range is "19. 12. 2026 – 20. 12. 2026". */
internal fun formatIsoDate(iso: String): String {
    val p = iso.take(10).split('-')
    return if (p.size == 3) "${p[2].trimStart('0')}. ${p[1].trimStart('0')}. ${p[0]}" else iso
}

internal fun formatDateRange(start: String, end: String?): String =
    if (end == null || end.take(10) == start.take(10)) formatIsoDate(start) else "${formatIsoDate(start)} – ${formatIsoDate(end)}"

private sealed interface ListRow {
    val key: String
    data class Header(val month: Int, val year: String, val count: Int) : ListRow { override val key get() = "h$year-$month" }
    data class Item(val event: FederationEventSummary) : ListRow { override val key get() = "e${event.id}" }
}

private fun defaultRegion(code: String) = when (code.lowercase()) {
    "csts" -> MapRegion.CZECHIA
    "szts" -> MapRegion.SLOVAKIA
    else -> MapRegion.WORLD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FederationEventsScreen(
    code: String,
    onBack: () -> Unit = {},
    onOpenEvent: (eventId: Long) -> Unit = {},
    bottomPadding: Dp = 0.dp
) {
    val viewModel = viewModel<FederationEventsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val strings = AppStrings.current.danceOrg
    var regionName by rememberSaveable(code) { mutableStateOf(defaultRegion(code).name) }
    val region = MapRegion.valueOf(regionName)
    var selected by remember { mutableStateOf<List<FederationEventSummary>>(emptyList()) }

    LaunchedEffect(code) { viewModel.load(code) }

    // Header and event rows as one flat list, built once per change (not on every recomposition).
    val rows = remember(state.events) {
        buildList<ListRow> {
            state.events.groupBy { it.startDate.take(7) }.forEach { (ym, group) ->
                add(ListRow.Header(group.first().month, ym.take(4), group.size))
                group.forEach { add(ListRow.Item(it)) }
            }
        }
    }
    val yearsState = rememberLazyListState()
    // The year row starts at its end, where the present year is.
    LaunchedEffect(state.years.size) { if (state.years.isNotEmpty()) yearsState.scrollToItem(state.years.lastIndex) }
    // A selection that the filters have since removed must not linger on the map.
    LaunchedEffect(state.events) { selected = selected.filter { s -> state.events.any { it.id == s.id } } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.federationName.ifBlank { code.uppercase() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(strings.dataTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
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
            onRefresh = { scope.launch { viewModel.load(code, forceRefresh = true) } },
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding + 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.isOffline) item { OfflineBanner(isOffline = true, message = strings.offlineMessage) }
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        SegmentedButton(state.viewMode == EventsViewMode.UPCOMING, { scope.launch { viewModel.setViewMode(EventsViewMode.UPCOMING) } },
                            SegmentedButtonDefaults.itemShape(0, 3)) { Text(strings.upcoming, maxLines = 1) }
                        SegmentedButton(state.viewMode == EventsViewMode.PAST, { scope.launch { viewModel.setViewMode(EventsViewMode.PAST) } },
                            SegmentedButtonDefaults.itemShape(1, 3)) { Text(strings.past, maxLines = 1) }
                        SegmentedButton(state.viewMode == EventsViewMode.YEAR, { scope.launch { viewModel.setViewMode(EventsViewMode.YEAR) } },
                            SegmentedButtonDefaults.itemShape(2, 3)) { Text(strings.allYear, maxLines = 1) }
                    }
                }
                if (state.viewMode == EventsViewMode.YEAR) item {
                    LazyRow(state = yearsState, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.years, key = { it.year }) { y ->
                            YearChip(y.year, y.eventCount, y.year == state.year) { scope.launch { viewModel.selectYear(y.year) } }
                        }
                    }
                }
                item {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = viewModel::setQuery,
                            placeholder = { Text(strings.searchHint) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (state.query.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = AppStrings.current.commonActions.cancel)
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (state.typeCounts.isNotEmpty()) item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.typeCounts.toList(), key = { it.first }) { (type, n) ->
                            FilterChip(
                                selected = type in state.selectedTypes,
                                onClick = { viewModel.toggleType(type) },
                                label = { Text("${strings.competitionType(type)} · $n") }
                            )
                        }
                    }
                }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = state.month == null, onClick = { viewModel.selectMonth(null) }, label = { Text(strings.allYear) }) }
                        items((1..12).toList()) { m ->
                            val n = state.monthCounts[m] ?: 0
                            FilterChip(
                                selected = state.month == m,
                                enabled = n > 0 || state.month == m,
                                onClick = { viewModel.selectMonth(if (state.month == m) null else m) },
                                label = { Text(strings.monthShort(m)) },
                                leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(monthColor(m))) }
                            )
                        }
                    }
                }
                item {
                    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${state.events.size} / ${state.allEvents.size} ${strings.eventsSuffix}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(!state.showMap, { viewModel.setShowMap(false) }, SegmentedButtonDefaults.itemShape(0, 2),
                                icon = { Icon(Icons.Filled.ViewList, contentDescription = null, modifier = Modifier.size(18.dp)) }) { Text(strings.list) }
                            SegmentedButton(state.showMap, { viewModel.setShowMap(true) }, SegmentedButtonDefaults.itemShape(1, 2),
                                icon = { Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp)) }) { Text(strings.map) }
                        }
                    }
                }

                when {
                    state.error != null && state.allEvents.isEmpty() -> item {
                        ErrorState(state.error!!.message, fullPage = false, onRetry = { scope.launch { viewModel.load(code, forceRefresh = true) } })
                    }
                    state.isLoading && state.allEvents.isEmpty() -> item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
                    }
                    state.showMap -> item {
                        MapSection(state.events, region, selected, onRegion = { regionName = it.name; selected = emptyList() },
                            onSelect = { selected = it }, onOpenEvent = onOpenEvent, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    state.events.isEmpty() -> item { EmptyState(strings.noEvents, modifier = Modifier.padding(32.dp)) }
                    else -> items(rows, key = { it.key }, contentType = { it::class }) { row ->
                        when (row) {
                            is ListRow.Header -> MonthHeader(row.month, row.year, row.count)
                            is ListRow.Item -> EventRow(row.event, Modifier.padding(horizontal = 16.dp), onClick = { onOpenEvent(row.event.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearChip(year: Int, count: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(year.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("$count ${AppStrings.current.danceOrg.eventsSuffix}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MonthHeader(month: Int, year: String, count: Int) {
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(monthColor(month)))
        Text(
            "  ${AppStrings.current.danceOrg.monthShort(month)} $year",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MapSection(
    events: List<FederationEventSummary>,
    region: MapRegion,
    selected: List<FederationEventSummary>,
    onRegion: (MapRegion) -> Unit,
    onSelect: (List<FederationEventSummary>) -> Unit,
    onOpenEvent: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = AppStrings.current.danceOrg
    if (events.none { it.hasCoordinates }) {
        EmptyState(strings.noMapEvents, modifier = modifier.padding(32.dp))
        return
    }
    Box(modifier.fillMaxWidth().height(460.dp)) {
        EventMap(
            events = events,
            region = region,
            selectedIds = selected.map { it.id }.toSet(),
            onSelect = onSelect,
            modifier = Modifier.fillMaxSize(),
            overlayTop = { RegionPicker(region, onRegion) }
        )
        if (selected.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selected.sortedBy { it.startDate }, key = { it.id }) { e ->
                    EventRow(e, Modifier.width(if (selected.size == 1) 340.dp else 300.dp), onClick = { onOpenEvent(e.id) })
                }
            }
        }
    }
}

@Composable
private fun RegionPicker(region: MapRegion, onSelect: (MapRegion) -> Unit) {
    val strings = AppStrings.current.danceOrg
    fun label(r: MapRegion) = when (r) {
        MapRegion.WORLD -> strings.mapWorld
        MapRegion.CZECHIA -> strings.mapCzechia
        MapRegion.SLOVAKIA -> strings.mapSlovakia
    }
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 2.dp
        ) {
            Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label(region), style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MapRegion.entries.forEach { r -> DropdownMenuItem(text = { Text(label(r)) }, onClick = { onSelect(r); open = false }) }
        }
    }
}

@Composable
internal fun EventRow(event: FederationEventSummary, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val strings = AppStrings.current.danceOrg
    val accent = monthColor(event.month)
    val day = event.startDate.substring(8, 10).trimStart('0')
    Card(
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.18f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(day, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(strings.monthShort(event.month), style = MaterialTheme.typography.labelSmall, color = accent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    event.name ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (event.isCancelled) TextDecoration.LineThrough else null
                )
                val where = listOfNotNull(event.place, event.country).distinct().joinToString(", ")
                if (where.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" $where", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val end = event.endDate
                if (end != null && end.take(10) != event.startDate.take(10)) {
                    Text(formatDateRange(event.startDate, end), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (event.competitionCount > 0) Text(
                        "${strings.competitionsCount}: ${event.competitionCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (event.hasResults) Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = strings.withResults,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    if (event.isCancelled) Text(strings.statusCancelled, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
