package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Card
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.formatShortDateTime
import com.tkolymp.shared.utils.parseToLocal
import com.tkolymp.tkolympapp.SwipeToReload
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(vm: com.tkolymp.shared.payments.PaymentsViewModel, onBack: () -> Unit = {}, bottomPadding: Dp = 0.dp) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(AppStrings.current.misc.paymentsTabPending, AppStrings.current.misc.paymentsTabPaid)

    // `vm` is provided by the platform (lifecycle-aware wrapper) to avoid
    // manual construction inside a Composable which is not lifecycle-aware.

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

                    val (waitingItems, paidItems) = payments.partition { item ->
                        item.isUnpaid == true || (item.isUnpaid == null && (item.payment?.status.isNullOrBlank() || item.payment?.status?.equals("PAID", ignoreCase = true) != true))
                    }

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
                                LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.Top) {
                                    items(waitingItems) { it -> PaymentItemCard(it) }
                                }
                            }
                        }
                        else -> {
                            if (paidItems.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(AppStrings.current.misc.paymentsEmptyNoPaid) }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.Top) {
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
private fun PaymentItemCard(item: com.tkolymp.shared.payments.PaymentDebtorItem, modifier: Modifier = Modifier) {
    val isPaid = when {
        item.isUnpaid != null -> item.isUnpaid == false
        !item.payment?.status.isNullOrBlank() -> item.payment?.status.equals("PAID", ignoreCase = true)
        else -> false
    }
    val name = listOfNotNull(item.person?.firstName, item.person?.lastName).joinToString(" ")
    val amount = item.price?.amount?.let { a -> if (a % 1.0 == 0.0) a.toInt().toString() else String.format("%.2f", a) } ?: "-"

    fun fmtDate(raw: String?): String {
        val ldt = parseToLocal(raw) ?: return raw ?: "-"
        return formatShortDateTime(ldt.date, ldt.hour, ldt.minute)
    }

    val stripColor = if (isPaid) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(stripColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name.ifEmpty { "${AppStrings.current.misc.personFallback}${item.personId ?: "?"}" },
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                val boxBg = MaterialTheme.colorScheme.surfaceVariant
                FlowRow(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp, bottom = 8.dp)
                            .background(boxBg, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("$amount ${item.price?.currency ?: "CZK"}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val dueText = fmtDate(item.payment?.dueAt)
                    if (dueText != "-") {
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp, bottom = 8.dp)
                                .background(boxBg, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${AppStrings.current.misc.dueLabel} $dueText", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    val vs = item.payment?.variableSymbol
                    if (!vs.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp, bottom = 8.dp)
                                .background(boxBg, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(vs, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
