package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.people.CouplePartner
import com.tkolymp.shared.utils.formatShortDate
import com.tkolymp.shared.utils.parseToLocal
import com.tkolymp.shared.utils.withImageWidth
import com.tkolymp.shared.viewmodels.CoupleViewModel
import com.tkolymp.tkolympapp.SwipeToReload
import com.tkolymp.tkolympapp.components.CoupleAvatar
import com.tkolymp.tkolympapp.components.ErrorState
import com.tkolymp.tkolympapp.components.InitialsAvatar
import com.tkolymp.tkolympapp.components.ScheduleCard
import com.tkolymp.tkolympapp.components.RowDivider
import com.tkolymp.tkolympapp.components.TopAppBar
import com.tkolymp.tkolympapp.components.pressClickable
import com.tkolymp.tkolympapp.components.skeleton.SkeletonDetail
import com.tkolymp.tkolympapp.util.SkeletonCrossfade
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleScreen(
    coupleId: String,
    onBack: () -> Unit = {},
    onOpenPerson: ((String) -> Unit)? = null,
    onOpenFederationEvent: (String) -> Unit = {},
    bottomPadding: Dp = 0.dp,
) {
    val vm = viewModel<CoupleViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(coupleId) { vm.load(coupleId) }
    val couple = state.couple

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.current.profile.couple) },
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
            onRefresh = { scope.launch { vm.load(coupleId) } },
            modifier = Modifier.padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
        ) {
            SkeletonCrossfade(couple == null && state.isLoading && state.error == null) { showSkeleton ->
                if (showSkeleton) {
                    SkeletonDetail()
                } else if (couple == null) {
                    state.error?.let { ErrorState(error = it, onRetry = { scope.launch { vm.load(coupleId) } }) }
                } else {
                    val partners = listOfNotNull(couple.man, couple.woman)
                    val since = parseToLocal(couple.since)?.date?.let { formatShortDate(it) }
                    val until = parseToLocal(couple.until)?.date?.let { formatShortDate(it) }
                    val period = when {
                        since != null && until != null -> "$since – $until"
                        since != null -> "$since –"
                        else -> null
                    }
                    val displayName = couple.name?.takeIf { it.isNotBlank() }
                        ?: partners.joinToString(" - ") { it.name }
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CoupleAvatar(
                            womanName = couple.woman?.name,
                            manName = couple.man?.name,
                            size = 72.dp,
                            fontSize = 24.sp,
                            womanImageUrl = withImageWidth(couple.woman?.imageUrl, 240),
                            manImageUrl = withImageWidth(couple.man?.imageUrl, 240)
                        )
                        Text(displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val federationChips = couple.federations.map { f ->
                            listOfNotNull(federationEmoji(f.federation), federationLabel(f.federation), f.externalId).joinToString(" ")
                        }
                        val chips = listOfNotNull(couple.ageGroup?.takeIf { it.isNotBlank() }, period) + federationChips
                        if (chips.isNotEmpty()) {
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                                chips.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
                            }
                        }
                        ScheduleCard(Modifier.fillMaxWidth()) {
                            Column {
                                partners.forEachIndexed { i, partner ->
                                    if (i > 0) RowDivider()
                                    PartnerRow(partner, onOpenPerson)
                                }
                            }
                        }
                        val competition = AppStrings.current.competition
                        couple.progress.groupBy { it.federation?.equals("csts", ignoreCase = true) == true }.entries.sortedByDescending { it.key }.forEach { (isCsts, list) ->
                            WdsfProgressCard(list, title = if (isCsts) competition.cstsProgress else competition.wdsfProgress)
                        }
                        if (couple.rankings.isNotEmpty()) FederationRankingsCard(couple.rankings)
                        if (couple.results.isNotEmpty()) CoupleResultsCard(couple.results, onOpenFederationEvent)
                    }
                }
            }
        }
    }
}

@Composable
private fun PartnerRow(partner: CouplePartner, onOpenPerson: ((String) -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onOpenPerson != null) Modifier.pressClickable(0.99f) { onOpenPerson(partner.id) } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InitialsAvatar(name = partner.name, size = 40.dp, fontSize = 14.sp, imageUrl = withImageWidth(partner.imageUrl, 120))
        Text(partner.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        if (onOpenPerson != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
