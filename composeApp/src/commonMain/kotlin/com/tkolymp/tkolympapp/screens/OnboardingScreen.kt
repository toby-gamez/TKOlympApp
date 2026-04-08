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
import androidx.compose.ui.draw.shadow
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

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = OnboardingViewModel(),
    onFinish: () -> Unit
) {
    val strings = AppStrings.current
    val pageCount = 4 // 3 feature pages + calendar view picker
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val isViewPickerPage = pagerState.currentPage == pageCount - 1
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
            when (pageIndex) {
                0 -> EventsOnboardingPage(title = strings.onboardingTitle1, description = strings.onboardingDesc1)
                1 -> CalendarOnboardingPage(title = strings.onboardingTitle2, description = strings.onboardingDesc2)
                2 -> PeopleOnboardingPage(title = strings.onboardingTitle3, description = strings.onboardingDesc3)
                else -> CalendarViewPickerPage(
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
            (0 until pageCount).forEach { index ->
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
private fun EventsOnboardingPage(title: String, description: String) {
    OnboardingPageScaffold(
        title = title,
        description = description,
        mockup = { EventsMockupCard(Modifier.fillMaxWidth()) }
    )
}

@Composable
private fun CalendarOnboardingPage(title: String, description: String) {
    OnboardingPageScaffold(
        title = title,
        description = description,
        mockup = { CalendarMockupCard(Modifier.fillMaxWidth()) }
    )
}

@Composable
private fun PeopleOnboardingPage(title: String, description: String) {
    OnboardingPageScaffold(
        title = title,
        description = description,
        mockup = { PeopleMockupCard(Modifier.fillMaxWidth()) }
    )
}

@Composable
private fun OnboardingPageScaffold(
    title: String,
    description: String,
    mockup: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo pill at top
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            AppLogo(size = 20.dp)
            Text(
                text = "TK Olymp",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // App UI mockup
        mockup()

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )
    }
}

// ──────────────────────────────────────────────────
// Mockup cards — styled to look like real app UI
// ──────────────────────────────────────────────────

private val MockCardShape = RoundedCornerShape(16.dp)
private val Gold = Color(0xFFFFD700)
private val Silver = Color(0xFFC0C0C0)
private val Bronze = Color(0xFFCD7F32)
private val TrainingBlue = Color(0xFF2979FF)
private val TrainingGreen = Color(0xFF43A047)
private val TrainingOrange = Color(0xFFFB8C00)

@Composable
private fun EventsMockupCard(modifier: Modifier = Modifier) {
    val brand = Color(0xFFEE1733)
    Card(
        modifier = modifier.shadow(6.dp, MockCardShape),
        shape = MockCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            // Screen header bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brand)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "  Závody & výsledky",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // Event rows
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                MockEventRow(medalColor = Gold,   rank = "1.", name = "Mistrovství ČR",    detail = "Praha · 12. 4.")
                MockEventRow(medalColor = Silver, rank = "2.", name = "Pohár TK Olymp",    detail = "Brno · 5. 3.")
                MockEventRow(medalColor = Bronze, rank = "3.", name = "Krajský přebor",    detail = "Olomouc · 15. 2.")
            }
        }
    }
}

@Composable
private fun MockEventRow(medalColor: Color, rank: String, name: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(medalColor),
            contentAlignment = Alignment.Center
        ) {
            Text(rank, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Column {
            Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(detail, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalendarMockupCard(modifier: Modifier = Modifier) {
    val brand = Color(0xFFEE1733)
    Card(
        modifier = modifier.shadow(6.dp, MockCardShape),
        shape = MockCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brand)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "  Tréninkový plán",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                MockTrainingRow(accentColor = TrainingBlue,   time = "17:00–18:30", name = "Standard · Sál A")
                MockTrainingRow(accentColor = TrainingGreen,  time = "18:30–20:00", name = "Latinskoamerická · Sál B")
                MockTrainingRow(accentColor = TrainingOrange, time = "09:00–10:30", name = "Začátečníci · Sál A")
            }
        }
    }
}

@Composable
private fun MockTrainingRow(accentColor: Color, time: String, name: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(44.dp)
                .background(accentColor, RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
        )
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(time, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun PeopleMockupCard(modifier: Modifier = Modifier) {
    val brand = Color(0xFFEE1733)
    Card(
        modifier = modifier.shadow(6.dp, MockCardShape),
        shape = MockCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brand)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "  Členové & pořadí",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // Avatar row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy((-8).dp)
            ) {
                val avatarColors = listOf(Color(0xFFEE1733), Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFF57F17), Color(0xFF6A1B9A))
                val initials = listOf("J", "P", "E", "M", "T")
                avatarColors.zip(initials).forEach { (color, initial) ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(initial, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "+42",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            // Leaderboard
            Column(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                MockLeaderboardRow(rank = 1, name = "Jana K.", score = "94.5")
                MockLeaderboardRow(rank = 2, name = "Petr N.", score = "91.2")
                MockLeaderboardRow(rank = 3, name = "Eva M.", score = "88.7")
            }
        }
    }
}

@Composable
private fun MockLeaderboardRow(rank: Int, name: String, score: String) {
    val medalColor = when (rank) {
        1 -> Gold
        2 -> Silver
        else -> Bronze
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(20.dp).clip(CircleShape).background(medalColor),
                contentAlignment = Alignment.Center
            ) {
                Text("$rank", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text(name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(score, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

