package com.tkolymp.tkolympapp.Screens

// no manual refresh button
 
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.TextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import java.text.Normalizer
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.viewmodels.PeopleViewModel
import com.tkolymp.shared.people.Person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import com.tkolymp.tkolympapp.SwipeToReload

private enum class SortMode { ALPHABETICAL, BIRTHDAY }
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(onPersonClick: (String) -> Unit = {}, onBack: () -> Unit = {}) {
    val viewModel = remember { PeopleViewModel() }
    val state by viewModel.state.collectAsState()
    var sortMode by remember { mutableStateOf<SortMode>(SortMode.ALPHABETICAL) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(showSearch) {
        if (showSearch) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadPeople()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Lidé") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Zpět")
                }
            },
            actions = {
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = "Hledat")
                }
            }
        )
    }) { padding ->
        val scope = rememberCoroutineScope()

        SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch { viewModel.loadPeople() } },
            modifier = Modifier.padding(padding)
        ) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val people = state.people.filterIsInstance<Person>()

                Column(modifier = Modifier) {
            // search field
            if (showSearch) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = "") },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (searchQuery.isNotBlank()) searchQuery = "" else showSearch = false
                        }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "Zavřít")
                        }
                    },
                    placeholder = { Text("Hledat podle jména nebo příjmení") }
                )
            }

            // sort controls
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { sortMode = SortMode.ALPHABETICAL }) {
                    Text("Abecedně", color = if (sortMode == SortMode.ALPHABETICAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { sortMode = SortMode.BIRTHDAY }) {
                    Text("Narozeniny", color = if (sortMode == SortMode.BIRTHDAY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }

            // groups (cohorts) filter with multi-selection (default: show all)
            var selectedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }
            val groups = remember(people) {
                people.flatMap { p -> p.cohortMembershipsList.mapNotNull { it.cohort } }
                    .mapNotNull { c ->
                        // only include visible cohorts
                        if (c.isVisible == false) return@mapNotNull null
                        val id = c.id ?: return@mapNotNull null
                        val name = c.name ?: id
                        id to name
                    }
                    .distinctBy { it.first }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    // 'Vše' clears selection (empty = show all)
                    FilterChip(
                        selected = selectedGroups.isEmpty() || selectedGroups.size == groups.size,
                        onClick = { selectedGroups = emptySet() },
                        label = { Text("Vše") }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    groups.forEach { (id, name) ->
                        FilterChip(
                            selected = selectedGroups.contains(id),
                            onClick = {
                                selectedGroups = if (selectedGroups.contains(id)) selectedGroups - id else selectedGroups + id
                            },
                            label = { Text(name) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }

            }

            val displayed = remember(people, sortMode, selectedGroups, groups, searchQuery) {
                val filtered = if (selectedGroups.isEmpty() || selectedGroups.size == groups.size) people else people.filter { p ->
                    p.cohortMembershipsList
                        .mapNotNull { it.cohort }
                        .filter { it.isVisible != false }
                        .any { it.id?.let { id -> selectedGroups.contains(id) } == true }
                }
                fun normalizeForSearch(s: String?): String {
                    if (s.isNullOrBlank()) return ""
                    val n = Normalizer.normalize(s, Normalizer.Form.NFD)
                    return n.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
                }

                val searched = if (searchQuery.isBlank()) filtered else filtered.filter { p ->
                    val q = normalizeForSearch(searchQuery.trim())
                    listOf(p.firstName, p.lastName)
                        .filterNotNull()
                        .any { normalizeForSearch(it).contains(q) }
                }
                when (sortMode) {
                    SortMode.ALPHABETICAL -> searched.sortedBy { p -> listOf(p.firstName, p.lastName, p.suffixTitle).filterNotNull().filter { it.isNotBlank() }.joinToString(" ").lowercase() }
                    SortMode.BIRTHDAY -> searched.sortedBy { daysUntilNextBirthday(it.birthDate) }
                }
            }
            // manual refresh removed; initial load happens in LaunchedEffect

            if (displayed.isEmpty()) {
                Text("Žádní lidé k zobrazení.", modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(modifier = Modifier.padding(4.dp)) {
                    items(displayed) { p ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .clickable { onPersonClick(p.id) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val isBirthdayToday = daysUntilNextBirthday(p.birthDate) == 0
                                    val base = listOf(p.prefixTitle, p.firstName, p.lastName).filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
                                    val name = if (!p.suffixTitle.isNullOrBlank()) "$base, ${p.suffixTitle}" else base
                                    Text(
                                        name.ifBlank { p.id },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isBirthdayToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    p.birthDate?.let { raw ->
                                        val formatted = formatDateString(raw)
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            if (isBirthdayToday) {
                                                Icon(
                                                    imageVector = Icons.Filled.Cake,
                                                    contentDescription = "Dnes mají narozeniny",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Text(
                                                formatted ?: raw,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isBirthdayToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // show cohort color(s) as small circles on the right (similar to CalendarScreen)
                                val cohortColors = p.cohortMembershipsList
                                                .mapNotNull { it.cohort }
                                                .filter { it.isVisible != false }
                                                .mapNotNull { it.colorRgb }
                                                .mapNotNull { hex ->
                                                    try {
                                                        parseColorOrDefault(hex)
                                                    } catch (_: Exception) { null }
                                                }

                                if (cohortColors.isEmpty()) {
                                    val cohortName = p.cohortMembershipsList
                                        .mapNotNull { it.cohort }
                                        .firstOrNull { it.isVisible != false }
                                        ?.name
                                    cohortName?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                                } else {
                                    Row {
                                        cohortColors.forEachIndexed { idx, color ->
                                            Box(modifier = Modifier
                                                .size(12.dp)
                                                .background(color, CircleShape)
                                            )
                                            if (idx != cohortColors.lastIndex) Spacer(modifier = Modifier.width(6.dp))
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
    }
}

internal fun parseColorOrDefault(hex: String?): Color {
    if (hex.isNullOrBlank()) return Color.Gray
    return try {
        var s = hex.trim()
        if (!s.startsWith("#")) s = "#" + s
        Color(android.graphics.Color.parseColor(s))
    } catch (e: Exception) {
        Color.Gray
    }
}

internal fun formatDateString(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val s = raw.trim()
    val datePrefix = Regex("\\d{4}-\\d{2}-\\d{2}").find(s)?.value
    val formatterOut = DateTimeFormatter.ofPattern("d. M. yyyy")
    try {
        if (datePrefix != null && datePrefix.length == 10) {
            val ld = LocalDate.parse(datePrefix)
            return ld.format(formatterOut)
        }
        val odt = OffsetDateTime.parse(s)
        return odt.toLocalDate().format(formatterOut)
    } catch (_: DateTimeParseException) {
    }
    try {
        val zdt = ZonedDateTime.parse(s)
        return zdt.toLocalDate().format(formatterOut)
    } catch (_: DateTimeParseException) {
    }
    try {
        val instant = Instant.parse(s)
        val ld = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        return ld.format(formatterOut)
    } catch (_: DateTimeParseException) {
    }
    return null
}

internal fun daysUntilNextBirthday(raw: String?): Int {
    if (raw.isNullOrBlank()) return Int.MAX_VALUE
    val s = raw.trim()
    val datePrefix = Regex("\\d{4}-\\d{2}-\\d{2}").find(s)?.value
    val today = LocalDate.now()
    val ld: LocalDate = try {
        if (datePrefix != null && datePrefix.length == 10) {
            LocalDate.parse(datePrefix)
        } else {
            try {
                OffsetDateTime.parse(s).toLocalDate()
            } catch (_: DateTimeParseException) {
                try {
                    ZonedDateTime.parse(s).toLocalDate()
                } catch (_: DateTimeParseException) {
                    try {
                        Instant.parse(s).atZone(ZoneId.systemDefault()).toLocalDate()
                    } catch (_: DateTimeParseException) {
                        return Int.MAX_VALUE
                    }
                }
            }
        }
    } catch (_: Exception) {
        return Int.MAX_VALUE
    }

    val month = ld.monthValue
    val day = ld.dayOfMonth
    val candidate = try {
        LocalDate.of(today.year, month, day)
    } catch (_: Exception) {
        // handle Feb 29 on non-leap years by using Feb 28
        if (month == 2 && day == 29) LocalDate.of(today.year, 2, 28) else return Int.MAX_VALUE
    }

    var next = if (candidate.isBefore(today)) candidate.plusYears(1) else candidate
    // ensure next exists (leap-year edge-case)
    if (next.monthValue == 2 && next.dayOfMonth == 29) {
        // if target year isn't leap, shift to Feb 28
        if (!java.time.Year.isLeap(next.year.toLong())) next = LocalDate.of(next.year, 2, 28)
    }

    return try {
        ChronoUnit.DAYS.between(today, next).toInt()
    } catch (_: Exception) {
        Int.MAX_VALUE
    }
}
