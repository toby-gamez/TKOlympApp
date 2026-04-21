package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(AppStrings.current.misc.paymentsTabPending, AppStrings.current.misc.paymentsTabPaid)

    val vm = remember { com.tkolymp.shared.payments.PaymentsViewModel() }
    DisposableEffect(Unit) { onDispose { vm.clear() } }

    val isLoading by vm.isLoading.collectAsState()
    val payments by vm.items.collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(
            title = { Text(AppStrings.current.otherScreen.payments) },
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
                .padding(bottom = 0.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    PrimaryTabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                        tabs.forEachIndexed { index, title ->
                            Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                        }
                    }

                    val waitingItems = payments.filter { it.isUnpaid == true || (it.isUnpaid == null && (it.payment?.status.isNullOrBlank() || !it.payment!!.status.equals("PAID", ignoreCase = true))) }
                    val paidItems = payments.filter { it.isUnpaid == false || (!it.payment?.status.isNullOrBlank() && it.payment?.status.equals("PAID", ignoreCase = true)) }

                    when (selectedTab) {
                        0 -> {
                            if (waitingItems.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Filled.EmojiEvents,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(AppStrings.current.misc.paymentsEmptyNoDebts, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Top) {
                                    items(waitingItems) { it -> PaymentItemCard(it) }
                                }
                            }
                        }
                        else -> {
                            if (paidItems.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(AppStrings.current.misc.paymentsEmptyNoPaid) }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Top) {
                                    items(paidItems) { it -> PaymentItemCard(it) }
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
private fun PaymentItemCard(item: com.tkolymp.shared.payments.PaymentDebtorItem) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp), colors = CardDefaults.cardColors()) {
        val name = listOfNotNull(item.person?.firstName, item.person?.lastName).joinToString(" ")
        val amount = item.price?.amount?.let { a -> if (a % 1.0 == 0.0) a.toInt().toString() else String.format("%.2f", a) } ?: "-"

        fun fmtDate(raw: String?): String {
            val ldt = parseToLocal(raw) ?: return raw ?: "-"
            return formatShortDateTime(ldt.date, ldt.hour, ldt.minute)
        }

        val isPaid = when {
            item.isUnpaid != null -> item.isUnpaid == false
            !item.payment?.status.isNullOrBlank() -> item.payment?.status.equals("PAID", ignoreCase = true)
            else -> false
        }

        Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text(text = name.ifEmpty { "${AppStrings.current.misc.personFallback}${item.personId ?: "?"}" }, style = MaterialTheme.typography.titleMedium)
                Text(text = "${AppStrings.current.misc.amountLabel} ${amount} ${item.price?.currency ?: "CZK"}", style = MaterialTheme.typography.bodySmall)
                Text(text = "${AppStrings.current.misc.variableSymbolLabel} ${item.payment?.variableSymbol ?: "-"}", style = MaterialTheme.typography.bodySmall)
                Text(text = "${AppStrings.current.misc.dueLabel} ${fmtDate(item.payment?.dueAt)}", style = MaterialTheme.typography.bodySmall)
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
