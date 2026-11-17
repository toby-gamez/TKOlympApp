package com.tkolymp.tkolympapp.screens

import com.tkolymp.tkolympapp.components.CampCard
import com.tkolymp.tkolympapp.components.NoticeCard
import com.tkolymp.tkolympapp.components.TimelineDot
import com.tkolymp.tkolympapp.components.TimelineRow
import com.tkolymp.tkolympapp.components.cardLineBorder
import com.tkolymp.tkolympapp.components.successColor
import com.tkolymp.tkolympapp.components.surfaceCardColors
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.foundation.layout.PaddingValues
import com.tkolymp.tkolympapp.components.Button
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.tkolympapp.ui.theme.Spacing
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.viewmodels.OnboardingViewModel
import com.tkolymp.shared.models.UserRole
import com.tkolymp.tkolympapp.components.BackgroundPluses
import com.tkolymp.tkolympapp.platform.AppLogo
import com.tkolymp.tkolympapp.ui.brandLightPrimary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 5
private data class Feature(val icon: ImageVector, val label: String)

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = viewModel(),
    onFinish: () -> Unit
) {
    val strings = AppStrings.current
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val isWelcomePage = pagerState.currentPage == 0
    val isLastPage = pagerState.currentPage == PAGE_COUNT - 1

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
                0 -> WelcomeOnboardingPage(
                    onNext = { scope.launch { pagerState.animateScrollToPage(1) } }
                )
                1 -> CalendarOnboardingPage(title = strings.onboardingTitle2, description = strings.onboardingDesc2)
                2 -> EventsOnboardingPage(title = strings.onboardingTitle1, description = strings.onboardingDesc1)
                3 -> BoardOnboardingPage(title = strings.onboardingTitle3, description = strings.onboardingDesc3)
                4 -> OtherFeaturesOnboardingPage(
                    title = strings.onboarding.onboardingTitle5,
                    description = strings.onboarding.onboardingDesc5
                )
                else -> {}
            }
        }

        AnimatedVisibility(
            visible = !isWelcomePage,
            enter = fadeIn(tween(280)) + expandVertically(tween(280)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            // Dot indicators
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = Spacing.large)
            ) {
                (1 until PAGE_COUNT).forEach { index ->
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
                    if (isLastPage) {
                        scope.launch {
                            try { viewModel.completeOnboarding() }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) {}
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
                    .padding(horizontal = Spacing.xl)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isLastPage) strings.start else strings.next,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            }
        }
    }
}

@Composable
private fun WelcomeOnboardingPage(onNext: () -> Unit) {
    val logoScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var clicking by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundPluses(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(logoScale.value)
                .clip(CircleShape)
                .background(brandLightPrimary()),
            contentAlignment = Alignment.Center
        ) {
            AppLogo(size = 180.dp)
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "TK Olymp",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = Spacing.default),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = AppStrings.current.onboarding.welcomeSubtitle,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (clicking) return@Button
                clicking = true
                scope.launch {
                    logoScale.animateTo(0.45f, animationSpec = tween(320))
                    onNext()
                    logoScale.snapTo(1f)
                    clicking = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(88.dp),
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = AppStrings.current.commonActions.next,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
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
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(brandLightPrimary()),
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

/** Mock of the calendar day list, built from the real timeline row so the tour shows the actual look. */
@Composable
private fun CalendarMockupCard(modifier: Modifier = Modifier) {
    val strings = AppStrings.current
    val primary = MaterialTheme.colorScheme.primary
    Card(modifier = modifier, shape = MockCardShape, colors = surfaceCardColors(), border = cardLineBorder()) {
        Column(modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.medium)) {
            Text(strings.timeline.today.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.small))
            TimelineRow("16:00", TimelineDot.Filled(Color(0xFF43A047)), "Zelená skupina STT", "ZŠ Holečkova · Žaneta Brizgalová", true, false, {})
            TimelineRow("17:30", TimelineDot.Filled(Color(0xFFC9A000)), "Zlatá skupina LAT", "ZŠ Holečkova · Miroslav Hýža", false, false, {})
            TimelineRow("18:15", TimelineDot.Filled(primary), "Novák - Nováková", "Filip Karásek · BEST Sportcentrum · 45 min", false, false, {})
            TimelineRow("19:00", TimelineDot.Free, strings.overview.freeLesson, "Roman Pecha · BEST Sportcentrum · 45 min", false, true, {}, titleColor = successColor())
        }
    }
}

/** Mock of the camps list using the real camp card. */
@Composable
private fun EventsMockupCard(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        CampCard(listOf(Color(0xFFE53935), Color(0xFF1E88E5)), "13.–15.", "března", "Desítkové soustředění Prštice 2026", "TJ Sokol Prštice", AppStrings.current.registration.signedUp, {}, whenLabel = "za 12 dní")
        CampCard(listOf(Color(0xFF2E9E5B)), "28.", "března", "Soustředění MINI Olympteam", "ZŠ Holečkova", null, {}, whenLabel = "za 27 dní")
    }
}

/** Mock of the board using the real notice cards. */
@Composable
private fun BoardMockupCard(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NoticeCard(
            authorName = "Miroslav Hýža",
            meta = "Miroslav Hýža · 2 d",
            title = "Plán přípravy na MČR 2026",
            body = "Vážení sportovci, průběžně budou přidávány akce a termíny společných příprav.",
            onClick = {},
            featured = true,
            badge = AppStrings.current.announcements.newBadge
        )
        NoticeCard(
            authorName = "Roman Pecha",
            meta = "Roman Pecha · 5 d",
            title = "Seznam soutěží - jaro 2026",
            body = "Níže je tabulka zveřejněných naplánovaných soutěží.",
            onClick = {}
        )
    }
}

@Composable
private fun OtherFeaturesIconGrid(modifier: Modifier = Modifier) {
    val strings = AppStrings.current
    val features = listOf(
        Feature(Icons.Filled.Groups, strings.otherScreen.people),
        Feature(Icons.Filled.EmojiEvents, strings.otherScreen.leaderboard),
        Feature(Icons.Filled.AccountCircle, strings.otherScreen.myAccount),
        Feature(Icons.Filled.School, strings.otherScreen.trainingGroups),
        Feature(Icons.Filled.BarChart, strings.stats.statsTitle),
        Feature(Icons.Filled.Notifications, strings.otherScreen.notificationSettings),
    )
    Card(
        modifier = modifier,
        shape = MockCardShape,
        colors = surfaceCardColors(),
        border = cardLineBorder(),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.default, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(Spacing.default)) {
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

@Composable
private fun RoleSelectionPage(
    title: String,
    description: String,
    selectedRole: UserRole,
    onSelect: (UserRole) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(brandLightPrimary()),
            contentAlignment = Alignment.Center
        ) { AppLogo(size = 100.dp) }

        Spacer(Modifier.height(28.dp))

        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.small))
        Text(description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(Spacing.large))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.default), modifier = Modifier.fillMaxWidth()) {
            RoleOptionCard(
                icon = Icons.Filled.Groups,
                label = AppStrings.current.onboarding.roleDancer,
                description = AppStrings.current.onboarding.roleDancerDesc,
                selected = selectedRole == UserRole.DANCER,
                onClick = { onSelect(UserRole.DANCER) },
                modifier = Modifier.weight(1f)
            )
            RoleOptionCard(
                icon = Icons.Filled.AccountCircle,
                label = AppStrings.current.onboarding.roleParent,
                description = AppStrings.current.onboarding.roleParentDesc,
                selected = selectedRole == UserRole.PARENT,
                onClick = { onSelect(UserRole.PARENT) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RoleOptionCard(
    icon: ImageVector,
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant

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
                .padding(Spacing.default),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            Text(description, style = MaterialTheme.typography.bodySmall, color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun RoleSelectionScreen(
    viewModel: OnboardingViewModel = viewModel(),
    onFinish: () -> Unit
) {
    val strings = AppStrings.current
    var selectedRole by rememberSaveable { mutableStateOf(UserRole.DANCER) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.weight(1f)) {
            RoleSelectionPage(
                title = strings.onboarding.roleSelectionTitle,
                description = strings.onboarding.roleSelectionDescription,
                selectedRole = selectedRole,
                onSelect = { selectedRole = it }
            )
        }

        Button(
            onClick = {
                scope.launch {
                    try { viewModel.setUserRole(selectedRole) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) {}
                    onFinish()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(strings.start, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
