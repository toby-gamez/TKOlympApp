package com.tkolymp.tkolympapp.Screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.viewmodels.TrainersLocationsViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainersLocationsScreen(onBack: () -> Unit = {}) {
    val vm = remember { TrainersLocationsViewModel() }
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(Unit) { vm.load() }

    val scope = rememberCoroutineScope()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Trenéři a tréninkové prostory") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Zpět")
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
            if (state.error != null) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(state.error ?: "")
                }
            } else if (club == null || (club.locations.isEmpty() && club.trainers.isEmpty())) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Žádná data")
                }
            } else {
                LazyColumn(modifier = Modifier) {
            // Locations header
            item {
                Text("Tréninkové prostory", modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            // show locations except explicit "ZRUŠENO"
            val visibleLocations = club.locations.filter { loc ->
                val n = loc.name?.trim()
                !n.isNullOrBlank() && !n.equals("ZRUŠENO", ignoreCase = true)
            }

            if (visibleLocations.isNotEmpty()) {
                items(visibleLocations) { loc ->
                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable {
                            val q = loc.name ?: return@clickable
                            val geoUri = Uri.parse("geo:0,0?q=${Uri.encode(q)}")
                            val intent = Intent(Intent.ACTION_VIEW, geoUri)
                            try {
                                ctx.startActivity(intent)
                            } catch (ae: ActivityNotFoundException) {
                                val web = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(q)}")
                                val i2 = Intent(Intent.ACTION_VIEW, web)
                                try { ctx.startActivity(i2) } catch (_: Exception) { }
                            } catch (_: Exception) { }
                        }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Filled.Place, contentDescription = "Tréninkové prostory", modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = loc.name ?: "(bez názvu)", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            } else {
                item { Text("Žádné tréninkové prostory", modifier = Modifier.padding(16.dp)) }
            }

            // Trainers header
            item {
                Text("Trenéři", modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            // helper to format price
            fun formatPrice(amount: Double?, currency: String?): String? {
                if (amount == null) return null
                val isWhole = amount.rem(1.0) == 0.0
                val amtStr = if (isWhole) amount.toInt().toString() else String.format("%.2f", amount)
                val cur = currency?.uppercase()
                return when (cur) {
                    "EUR" -> "$amtStr € /pár, 45'"
                    "USD" -> "$$amtStr /pár, 45'"
                    else -> "$amtStr,- /pár, 45'"
                }
            }

            val visibleTrainers = club.trainers.filter { t ->
                // skip trainers explicitly not visible
                if (t.isVisible == false) return@filter false
                // skip trainers without a meaningful name
                val p = t.person
                val name = listOfNotNull(p?.firstName?.takeIf { it.isNotBlank() }, p?.lastName?.takeIf { it.isNotBlank() }).joinToString(" ")
                name.isNotBlank()
            }

            if (visibleTrainers.isNotEmpty()) {
                items(visibleTrainers) { t ->
                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Filled.Person, contentDescription = "Trenér", modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    val p = t.person
                                    val name = listOfNotNull(p?.prefixTitle, p?.firstName, p?.lastName).joinToString(" ").trim()
                                    val displayName = if (!p?.suffixTitle.isNullOrBlank()) "$name, ${p?.suffixTitle}" else name
                                    Text(text = if (displayName.isBlank()) (p?.id ?: "(trenér)") else displayName, style = MaterialTheme.typography.bodyLarge)
                                }
                            }

                            // price on the right
                            val priceStr = formatPrice(t.guestPrice45Min?.amount, t.guestPrice45Min?.currency)
                            if (priceStr != null) {
                                Text(text = priceStr, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            } else {
                item { Text("Žádní trenéři", modifier = Modifier.padding(16.dp)) }
            }
        }
    }
}}}
