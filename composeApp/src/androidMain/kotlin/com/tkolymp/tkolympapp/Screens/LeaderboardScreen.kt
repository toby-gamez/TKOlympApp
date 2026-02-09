package com.tkolymp.tkolympapp.Screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Alignment as ComposeAlignment
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(onBack: () -> Unit = {}, bottomPadding: Dp = 0.dp) {
    val entriesState = remember { mutableStateOf<List<com.tkolymp.shared.people.ScoreboardEntry>>(emptyList()) }
    val loading = remember { mutableStateOf(true) }
    val error = remember { mutableStateOf<String?>(null) }
    val currentPersonId = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading.value = true
        try {
            val since = "2025-09-01"
            val until = LocalDate.now().toString()
            val list = withContext(Dispatchers.IO) { ServiceLocator.peopleService.fetchScoreboard(null, since, until) }
            entriesState.value = list.sortedWith(compareBy({ it.ranking ?: Int.MAX_VALUE }, { it.personLastName ?: "" }, { it.personFirstName ?: "" }))
            // load cached personId to highlight current user name
            try {
                currentPersonId.value = withContext(Dispatchers.IO) { ServiceLocator.userService.getCachedPersonId() }
            } catch (_: Throwable) { /* ignore */ }
        } catch (ex: Exception) {
            error.value = ex.message
        } finally {
            loading.value = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Žebříček") }, navigationIcon = {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier
                .clickable { onBack() }
                .padding(12.dp))
        }) }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding(), bottom = bottomPadding)) {
            when {
                loading.value -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Načítání...") }
                error.value != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error.value ?: "Chyba") }
                else -> {
                    val entries = entriesState.value
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
                        itemsIndexed(entries) { index, item ->
                            val rank = (item.ranking ?: (index + 1))
                            val isTop = rank in 1..3
                            val colors = when (rank) {
                                1 -> CardDefaults.cardColors(containerColor = Color(0xFFFFD700).copy(alpha = 0.12f))
                                2 -> CardDefaults.cardColors(containerColor = Color(0xFFC0C0C0).copy(alpha = 0.12f))
                                3 -> CardDefaults.cardColors(containerColor = Color(0xFFCD7F32).copy(alpha = 0.12f))
                                else -> CardDefaults.cardColors()
                            }

                            Card(modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp), colors = colors) {
                                Row(modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp), verticalAlignment = ComposeAlignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val name = listOfNotNull(item.personFirstName, item.personLastName).joinToString(" ")
                                        val isCurrent = item.personId != null && item.personId == currentPersonId.value
                                        Text(
                                            text = "${rank}. $name",
                                            style = if (isTop) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // Right side: show total score only (no abbreviations)
                                    Column(modifier = Modifier.wrapContentWidth(align = ComposeAlignment.End), horizontalAlignment = ComposeAlignment.End) {
                                        val total = item.totalScore
                                        val totalText = total?.let { if (it % 1.0 == 0.0) it.toInt().toString() else String.format("%.1f", it) } ?: "-"
                                        Text(text = totalText, style = MaterialTheme.typography.titleMedium)
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

private fun formatScore(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)
