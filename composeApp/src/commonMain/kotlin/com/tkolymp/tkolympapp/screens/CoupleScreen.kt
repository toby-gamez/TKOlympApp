package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
import com.tkolymp.tkolympapp.util.StaggeredItem
import com.tkolymp.tkolympapp.ui.theme.Spacing
import com.tkolymp.tkolympapp.components.skeleton.SkeletonProfile
import com.tkolymp.tkolympapp.components.CoupleHeader
import com.tkolymp.tkolympapp.components.InitialsAvatar
import com.tkolymp.tkolympapp.components.ScheduleCard
import com.tkolymp.tkolympapp.components.SectionLabel
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
    var cardsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(couple) { if (couple != null) cardsVisible = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.current.profile.coupleTitle) },
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
                    SkeletonProfile()
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
                    val displayName = couple.name?.takeIf { it.isNotBlank() } ?: partners.joinToString(" - ") { it.name }
                    val strings = AppStrings.current
                    val isActive = couple.status.equals("ACTIVE", ignoreCase = true) || (couple.status.isNullOrBlank() && couple.until.isNullOrBlank())
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.default, vertical = Spacing.small),
                        verticalArrangement = Arrangement.spacedBy(Spacing.small)
                    ) {
                        CoupleHeader(
                            name = displayName,
                            manName = couple.man?.name,
                            womanName = couple.woman?.name,
                            manImageUrl = withImageWidth(couple.man?.imageUrl, 240),
                            womanImageUrl = withImageWidth(couple.woman?.imageUrl, 240),
                            details = listOfNotNull(couple.ageGroup?.takeIf { it.isNotBlank() }?.let { strings.competition.term(it) }, period),
                            meta = listOf(if (isActive) strings.profile.coupleActive else strings.profile.couplePast)
                        )
                        Spacer(Modifier.height(Spacing.small))
                        StaggeredItem(index = 0, visible = cardsVisible, baseDelayMs = 50) {
                            ScheduleCard(Modifier.fillMaxWidth()) {
                                Column {
                                    listOfNotNull(
                                        couple.man?.let { it to strings.profile.partnerMan },
                                        couple.woman?.let { it to strings.profile.partnerWoman }
                                    ).forEachIndexed { i, (partner, role) ->
                                        if (i > 0) RowDivider()
                                        PartnerRow(partner, role, onOpenPerson)
                                    }
                                }
                            }
                        }
                        // The couple's own id at each federation (not a person's ČSTS IDT / WDSF MIN)
                        val ids = couple.federations.mapNotNull { f ->
                            val id = f.externalId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            "${federationLabel(f.federation)} ${strings.profile.coupleId}" to id
                        }
                        if (ids.isNotEmpty()) {
                            StaggeredItem(index = 1, visible = cardsVisible, baseDelayMs = 50) {
                                Column {
                                    SectionLabel(couple.federations.joinToString(" · ") { federationLabel(it.federation) }, horizontalPadding = 0.dp)
                                    ScheduleCard(Modifier.fillMaxWidth()) {
                                        Column {
                                            ids.forEachIndexed { i, (label, id) ->
                                                if (i > 0) RowDivider()
                                                ProfileValueRow(label, id)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        val competition = strings.competition
                        var index = 2
                        couple.progress.groupBy { it.federation?.equals("csts", ignoreCase = true) == true }.entries.sortedByDescending { it.key }.forEach { (isCsts, list) ->
                            StaggeredItem(index = index++, visible = cardsVisible, baseDelayMs = 50) {
                                WdsfProgressCard(list, title = if (isCsts) competition.cstsProgress else competition.wdsfProgress)
                            }
                        }
                        if (couple.rankings.isNotEmpty()) StaggeredItem(index = index++, visible = cardsVisible, baseDelayMs = 50) { FederationRankingsCard(couple.rankings) }
                        if (couple.results.isNotEmpty()) StaggeredItem(index = index, visible = cardsVisible, baseDelayMs = 50) { CoupleResultsCard(couple.results, onOpenFederationEvent) }
                        Spacer(Modifier.height(Spacing.default))
                    }
                }
            }
        }
    }
}

@Composable
private fun PartnerRow(partner: CouplePartner, role: String, onOpenPerson: ((String) -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onOpenPerson != null) Modifier.pressClickable(0.99f) { onOpenPerson(partner.id) } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InitialsAvatar(name = partner.name, size = 40.dp, fontSize = 14.sp, imageUrl = withImageWidth(partner.imageUrl, 120))
        Column(Modifier.weight(1f)) {
            Text(role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(partner.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        if (onOpenPerson != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
