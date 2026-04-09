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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.ViewWeek
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.viewmodels.OnboardingViewModel
import com.tkolymp.tkolympapp.platform.AppLogo
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = OnboardingViewModel(),
    onFinish: () -> Unit
) {
    val strings = AppStrings.current
    val pageCount = 5 // Calendar, Events, Board, other features, calendar view picker
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
                0 -> CalendarOnboardingPage(title = strings.onboardingTitle2, description = strings.onboardingDesc2)
                1 -> EventsOnboardingPage(title = strings.onboardingTitle1, description = strings.onboardingDesc1)
                2 -> BoardOnboardingPage(title = strings.onboardingTitle3, description = strings.onboardingDesc3)
                3 -> OtherFeaturesOnboardingPage(
                    title = strings.onboarding.onboardingTitle5,
                    description = strings.onboarding.onboardingDesc5
                )
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
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            AppLogo(size = 100.dp)
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
        MaterialTheme.colorScheme.primary
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
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp).then(iconModifier)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
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
private fun EventsOnboardingPage(title: String, description: String) {
    OnboardingPageScaffold(
        title = title,
        description = description,
        mockup = { EventsMockupCard(Modifier.fillMaxWidth()) }
    )
}

@Composable
private fun BoardOnboardingPage(title: String, description: String) {
    OnboardingPageScaffold(
        title = title,
        description = description,
        mockup = { BoardMockupCard(Modifier.fillMaxWidth()) }
    )
}

@Composable
private fun OtherFeaturesOnboardingPage(title: String, description: String) {
    OnboardingPageScaffold(
        title = title,
        description = description,
        mockup = { OtherFeaturesIconGrid(Modifier.fillMaxWidth()) }
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
        // App logo in primary circle (~2 cm)
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            AppLogo(size = 100.dp)
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
// Mockup cards — match the real app screen design
// ──────────────────────────────────────────────────

private val MockCardShape = RoundedCornerShape(16.dp)

@Composable
private fun CalendarMockupCard(modifier: Modifier = Modifier) {
    val strings = AppStrings.current
    Card(
        modifier = modifier.shadow(4.dp, MockCardShape),
        shape = MockCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(strings.timeline.today, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
            MockCalendarGroupCard("Kruháč 1", "ZŠ Holečkova", "16:00 - 16:45", dotColor = null)
            MockCalendarGroupCard("Zlatá skupina LAT", "ZŠ Holečkova", "17:30 - 18:15", dotColor = Color(0xFFFFD700))
            Spacer(Modifier.height(4.dp))
            Text("tomorrow", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
            MockCalendarLessonCard("Filip Karásek", "BEST Sportcentrum", "15:00 - 15:45", "Novák - Nováková", "45'")
        }
    }
}

@Composable
private fun MockCalendarGroupCard(name: String, location: String, time: String, dotColor: Color?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("group", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (dotColor != null) Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(dotColor))
            }
        }
    }
}

@Composable
private fun MockCalendarLessonCard(trainer: String, location: String, time: String, names: String, duration: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(trainer, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text("lesson", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(76.dp))
                Text(names, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(duration, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EventsMockupCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.shadow(4.dp, MockCardShape),
        shape = MockCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("28. March", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
            MockEventCard("Soustředění MINI Olympteam - zelená a fialová skupina", "ZŠ Holečkova", "09:00 - 17:00")
            Spacer(Modifier.height(4.dp))
            Text("13. March", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
            MockEventCard("Desítkové soustředění Prštice 2026", "TJ Sokol Prštice", "13.3.–15.3.2026")
            Spacer(Modifier.height(4.dp))
            Text("22. February", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
            MockEventCard("Přípravný Camp před MČR LAT - Podbořany", "Filip Karásek", "09:00 - 20:00")
        }
    }
}

@Composable
private fun MockEventCard(name: String, location: String, time: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
            Spacer(Modifier.height(2.dp))
            Text(location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BoardMockupCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.shadow(4.dp, MockCardShape),
        shape = MockCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MockBoardPostCard("Termíny externích trenérů - jaro/léto 2026", "Filip Karásek", "úterý 7. 4. - Martin Odstrčil…")
            MockBoardPostCard("Seznam soutěží - jaro 2026", "Roman Pecha", "Níže je tabulka zveřejněných naplánovaných soutěží…")
            MockBoardPostCard("Plán přípravy na MČR 2026", "Miroslav Hýža", "Vážení sportovci, průběžně budou přidávány akce…")
            MockBoardPostCard("Členská schůze", "Miroslav Hýža", "Vážení členové a rodiče, …")
        }
    }
}

@Composable
private fun MockBoardPostCard(title: String, author: String, preview: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Text(preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OtherFeaturesIconGrid(modifier: Modifier = Modifier) {
    val strings = AppStrings.current
    data class Feature(val icon: ImageVector, val label: String)
    val features = listOf(
        Feature(Icons.Filled.Groups, strings.otherScreen.people),
        Feature(Icons.Filled.EmojiEvents, strings.otherScreen.leaderboard),
        Feature(Icons.Filled.AccountCircle, strings.otherScreen.myAccount),
        Feature(Icons.Filled.School, strings.otherScreen.trainingGroups),
        Feature(Icons.Filled.BarChart, strings.stats.statsTitle),
        Feature(Icons.Filled.Notifications, strings.otherScreen.notificationSettings),
    )
    Card(
        modifier = modifier.shadow(6.dp, MockCardShape),
        shape = MockCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            features.chunked(3).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { feature ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = feature.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                feature.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}


