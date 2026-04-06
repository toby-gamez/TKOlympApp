package com.tkolymp.tkolympapp.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.ViewWeek
import com.tkolymp.tkolympapp.platform.AppLogo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkolymp.shared.viewmodels.OnboardingViewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.tkolympapp.ui.brandLightPrimary
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = OnboardingViewModel(),
    onFinish: () -> Unit
) {
    val strings = AppStrings.current
    val pages = listOf(
        OnboardingPage(icon = Icons.Filled.EmojiEvents, title = strings.onboardingTitle1, description = strings.onboardingDesc1),
        OnboardingPage(icon = Icons.Filled.CalendarMonth, title = strings.onboardingTitle2, description = strings.onboardingDesc2),
        OnboardingPage(icon = Icons.Filled.Groups, title = strings.onboardingTitle3, description = strings.onboardingDesc3),
    )
    val pagerState = rememberPagerState(pageCount = { pages.size + 1 }) // +1 for view picker page
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.size // pages.size = index of picker page
    val isViewPickerPage = isLastPage
    var preferTimeline by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { pageIndex ->
            if (pageIndex < pages.size) {
                OnboardingPageContent(page = pages[pageIndex], pageIndex = pageIndex)
            } else {
                CalendarViewPickerPage(
                    title = strings.onboarding.onboardingTitle4,
                    description = strings.onboarding.onboardingDesc4,
                    listLabel = strings.onboarding.calendarViewList,
                    listDesc = strings.onboarding.calendarViewListDesc,
                    timelineLabel = strings.onboarding.calendarViewTimeline,
                    timelineDesc = strings.onboarding.calendarViewTimelineDesc,
                    preferTimeline = preferTimeline,
                    onSelect = { preferTimeline = it }
                )
            }
        }

        // Dot indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 24.dp)
        ) {
            (0..pages.size).forEach { index ->
                val width by animateDpAsState(
                    targetValue = if (pagerState.currentPage == index) 24.dp else 8.dp,
                    label = "dot_width"
                )
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(width)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (pagerState.currentPage == index)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                )
            }
        }

        // CTA button
        Button(
            onClick = {
                if (isViewPickerPage) {
                    scope.launch {
                        viewModel.setPreferTimeline(preferTimeline)
                        viewModel.completeOnboarding()
                        onFinish()
                    }
                } else {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = if (isViewPickerPage) strings.start else strings.next,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CalendarViewPickerPage(
    title: String,
    description: String,
    listLabel: String,
    listDesc: String,
    timelineLabel: String,
    timelineDesc: String,
    preferTimeline: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(brandLightPrimary())
        ) {
            Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(60.dp)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            CalendarViewOptionCard(
                icon = Icons.Filled.ViewWeek,
                label = listLabel,
                description = listDesc,
                selected = !preferTimeline,
                onClick = { onSelect(false) },
                modifier = Modifier.weight(1f),
                iconModifier = Modifier.rotate(90f)
            )
            CalendarViewOptionCard(
                icon = Icons.Filled.ViewTimeline,
                label = timelineLabel,
                description = timelineDesc,
                selected = preferTimeline,
                onClick = { onSelect(true) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CalendarViewOptionCard(
    icon: ImageVector,
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val containerColor = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp).then(iconModifier)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage, pageIndex: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(brandLightPrimary())
        ) {
            if (pageIndex == 0) {
                AppLogo(size = 96.dp)
            } else {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(60.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )
    }
}

