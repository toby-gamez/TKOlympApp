package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tkolymp.tkolympapp.components.skeleton.SkeletonPayments
import com.tkolymp.tkolympapp.components.InfoPill
import com.tkolymp.tkolympapp.components.RowDivider
import com.tkolymp.tkolympapp.components.SegmentedToggle
import com.tkolymp.tkolympapp.components.cardLineBorder
import com.tkolymp.tkolympapp.components.successColor
import com.tkolymp.tkolympapp.components.successContainerColor
import com.tkolymp.tkolympapp.components.surfaceCardColors
import com.tkolymp.tkolympapp.ui.theme.Spacing
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.lazy.itemsIndexed
import com.tkolymp.tkolympapp.util.EmptyStateFade
import com.tkolymp.tkolympapp.util.tabContentTransitionSpec
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import com.tkolymp.tkolympapp.components.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.tkolymp.tkolympapp.components.PaymentPayActions
import com.tkolymp.tkolympapp.util.StaggeredItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(vm: com.tkolymp.shared.payments.PaymentsViewModel, onBack: () -> Unit = {}, bottomPadding: Dp = 0.dp) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(AppStrings.current.misc.paymentsTabPending, AppStrings.current.misc.paymentsTabPaid)

    // `vm` is provided by the platform (lifecycle-aware wrapper) to avoid
    // manual construction inside a Composable which is not lifecycle-aware.

    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val payments by vm.items.collectAsState(initial = emptyList())

    val bankAccount by vm.bankAccount.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(
            title = { Text(AppStrings.current.otherScreen.payments) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
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
            if (isLoading && payments.isEmpty()) {
                SkeletonPayments()
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    SegmentedToggle(
                        labels = tabs,
                        selectedIndex = selectedTab,
                        onSelect = { selectedTab = it },
                        fillWidth = true,
                        modifier = Modifier.fillMaxWidth().padding(start = Spacing.default, end = Spacing.default, top = Spacing.small)
                    )

                    val (waitingItems, paidItems) = payments.partition { item ->
                        item.isUnpaid == true || (item.isUnpaid == null && (item.payment?.status.isNullOrBlank() || item.payment?.status?.equals("PAID", ignoreCase = true) != true))
                    }

                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { tabContentTransitionSpec() },
                        label = "paymentsTabContent"
                    ) { tab ->
                        var cardsVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { cardsVisible = true }
                        when (tab) {
                            0 -> {
                                EmptyStateFade(visible = waitingItems.isEmpty()) {
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
                                }
                                if (waitingItems.isNotEmpty()) {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(start = Spacing.default, end = Spacing.default, top = 14.dp, bottom = Spacing.default),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        itemsIndexed(waitingItems, key = { _, item -> item.payment?.id ?: item.personId ?: item.hashCode() }) { index, it ->
                                            StaggeredItem(index = index, visible = cardsVisible, baseDelayMs = 40, modifier = Modifier.animateItem()) {
                                                PaymentItemCard(
                                                    item = it,
                                                    bankAccount = bankAccount,
                                                    onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                if (paidItems.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(AppStrings.current.misc.paymentsEmptyNoPaid) }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(start = Spacing.default, end = Spacing.default, top = 14.dp, bottom = Spacing.default),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        itemsIndexed(paidItems, key = { _, item -> item.payment?.id ?: item.personId ?: item.hashCode() }) { index, it ->
                                            StaggeredItem(index = index, visible = cardsVisible, baseDelayMs = 40, modifier = Modifier.animateItem()) {
                                                PaymentItemCard(item = it)
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

@Composable
private fun PaymentItemCard(
    modifier: Modifier = Modifier,
    item: com.tkolymp.shared.payments.PaymentDebtorItem,
    bankAccount: String? = null,
    onMessage: (String) -> Unit = {}
) {
    val isPaid = when {
        item.isUnpaid != null -> item.isUnpaid == false
        !item.payment?.status.isNullOrBlank() -> item.payment?.status.equals("PAID", ignoreCase = true)
        else -> false
    }
    val name = listOfNotNull(item.person?.firstName, item.person?.lastName).joinToString(" ")
    val amount = item.price?.amount?.let { cents ->
        if (cents % 100L == 0L) (cents / 100L).toString()
        else { val v = cents / 100.0; val s = kotlin.math.round(v * 100).toLong(); "${s / 100}.${kotlin.math.abs(s % 100).toString().padStart(2, '0')}" }
    } ?: "-"

    fun fmtDate(raw: String?): String {
        val ldt = parseToLocal(raw) ?: return raw ?: "-"
        return formatShortDateTime(ldt.date, ldt.hour, ldt.minute)
    }

    val dueText = fmtDate(item.payment?.dueAt).takeIf { it != "-" }
    val ok = successColor()
    val currency = item.price?.currency ?: "CZK"
    val displayName = name.ifEmpty { "${AppStrings.current.misc.personFallback}${item.personId ?: "?"}" }

    if (isPaid) {
        // Paid: compact row with a green check
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = surfaceCardColors(),
            border = cardLineBorder()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                Box(Modifier.size(36.dp).background(successContainerColor(), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = ok, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (dueText != null) Text(dueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("$amount $currency", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        return
    }

    // Pending: big amount card with the due date pill and the pay actions
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = surfaceCardColors(),
        border = cardLineBorder()
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Text(displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (dueText != null) {
                    InfoPill(
                        "${AppStrings.current.misc.dueLabel} $dueText",
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Text(
                "$amount $currency",
                modifier = Modifier.padding(top = Spacing.small),
                fontSize = 36.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold
            )
            val vs = item.payment?.variableSymbol
            if (!vs.isNullOrBlank()) {
                Text("VS $vs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!bankAccount.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.medium))
                RowDivider()
                PaymentPayActions(
                    bankAccount = bankAccount,
                    amountMinor = item.price?.amount,
                    currency = currency,
                    variableSymbol = item.payment?.variableSymbol,
                    specificSymbol = item.payment?.specificSymbol,
                    message = name,
                    onMessage = onMessage
                )
            }
        }
    }
}
