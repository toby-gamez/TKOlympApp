package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.formatShortDateTime
import com.tkolymp.shared.utils.parseToLocal
import com.tkolymp.tkolympapp.SwipeToReload
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(onBack: () -> Unit = {}, bottomPadding: Dp = 0.dp) {
    val vm = remember { com.tkolymp.shared.payments.PaymentsViewModel() }
    DisposableEffect(Unit) { onDispose { vm.clear() } }

    val isLoading by vm.isLoading.collectAsState()
    val items by vm.items.collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(
            title = { Text("Platby") },
            navigationIcon = {
                onBack?.let {
                    IconButton(onClick = it) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                    }
                }
            }
        ) },
    ) { padding ->
        SwipeToReload(
            isRefreshing = isLoading,
            onRefresh = { scope.launch { vm.load() } },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = bottomPadding)
        ) {
            when {
                isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                items.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Žádné dlužné položky") }
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Top) {
                        items(items) { it ->
                            Card(modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp), colors = CardDefaults.cardColors()) {
                                val name = listOfNotNull(it.person?.firstName, it.person?.lastName).joinToString(" ")
                                val amount = it.price?.amount?.let { a -> if (a % 1.0 == 0.0) a.toInt().toString() else String.format("%.2f", a) } ?: "-"

                                fun fmtDate(raw: String?): String {
                                    val ldt = parseToLocal(raw) ?: return raw ?: "-"
                                    return formatShortDateTime(ldt.date, ldt.hour, ldt.minute)
                                }

                                val isPaid = when {
                                    it.isUnpaid != null -> it.isUnpaid == false
                                    !it.payment?.status.isNullOrBlank() -> it.payment?.status.equals("PAID", ignoreCase = true)
                                    else -> false
                                }

                                Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                    Column(modifier = Modifier.align(Alignment.CenterStart)) {
                                        Text(text = name.ifEmpty { "Osoba #${it.personId ?: "?"}" }, style = MaterialTheme.typography.titleMedium)
                                        Text(text = "Částka: ${amount} ${it.price?.currency ?: "CZK"}")
                                        Text(text = "Variabilní symbol: ${it.payment?.variableSymbol ?: "-"}")
                                        Text(text = "Splatné: ${fmtDate(it.payment?.dueAt)}")
                                    }

                                    if (isPaid) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Paid",
                                            modifier = Modifier.align(Alignment.CenterEnd).padding(start = 10.dp).size(25.dp)
                                        )
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
