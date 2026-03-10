package com.tkolymp.tkolympapp

// coroutine helpers not needed here; avoid importing isActive directly
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.viewmodels.OtherViewModel
import com.tkolymp.shared.language.AppStrings
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.coroutines.cancellation.CancellationException

private enum class MainItem { PEOPLE, TRAINERS, GROUPS, LEADERBOARD }
private enum class SettingsItem { LANGUAGES, ABOUT, NOTIFICATIONS, PRIVACY }

// Helper: do not surface internal/cancellation/compose runtime messages to the UI
private fun shouldShowErrorMessage(msg: String?): Boolean {
    if (msg.isNullOrBlank()) return false
    val low = msg.lowercase()
    if (low.contains("remembercoroutinescope") || low.contains("left the composition") || low.contains("left composition") ) return false
    if (low.contains("compose") && low.contains("coroutine")) return false
    return true
}

private fun shouldShowError(t: Throwable?): Boolean {
    if (t == null) return false
    if (t is CancellationException) return false
    val m = t.message ?: return true
    return shouldShowErrorMessage(m)
}

private fun formatDateString(raw: String): String? {
    val s = raw.trim()
    // Quick extract if value contains a date-like prefix
    val datePrefix = Regex("\\d{4}-\\d{2}-\\d{2}").find(s)?.value
    val formatterOut = DateTimeFormatter.ofPattern("d. M. yyyy")
    try {
        // Try plain local date
        if (datePrefix != null && datePrefix.length == 10) {
            val ld = LocalDate.parse(datePrefix)
            return ld.format(formatterOut)
        }
        // Try ISO local date-time / offset
        val odt = OffsetDateTime.parse(s)
        return odt.toLocalDate().format(formatterOut)
    } catch (_: DateTimeParseException) {
    }
    try {
        val zdt = ZonedDateTime.parse(s)
        return zdt.toLocalDate().format(formatterOut)
    } catch (_: DateTimeParseException) {
    }
    try {
        val instant = Instant.parse(s)
        val ld = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        return ld.format(formatterOut)
    } catch (_: DateTimeParseException) {
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherScreen(onProfileClick: () -> Unit = {}, onPeopleClick: () -> Unit = {}, onTrainersClick: () -> Unit = {}, onGroupsClick: () -> Unit = {}, onLeaderboardClick: () -> Unit = {}, onAboutClick: () -> Unit = {}, onPrivacyClick: () -> Unit = {}, onNotificationsClick: () -> Unit = {}, onLanguagesClick: () -> Unit = {}, bottomPadding: Dp = 0.dp) {
    val viewModel = remember { OtherViewModel() }
    val state by viewModel.state.collectAsState()
    var showDebug by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(AppStrings.current.other) }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(bottom = bottomPadding),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            // Profile card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clickable { onProfileClick() },
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = AppStrings.current.myProfile,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.name ?: AppStrings.current.myAccount,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (state.subtitle != null) Text(
                            state.subtitle!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        if (state.personDob != null && !showDebug) {
                            val formatted = formatDateString(state.personDob!!)
                            Text(
                                formatted ?: state.personDob!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }

            if (state.error != null) Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))

            Spacer(modifier = Modifier.width(16.dp))

            // První sekce - Členové a klub
            Text(
                text = AppStrings.current.membersAndClub,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )

            val mainItems = listOf(
                Pair(MainItem.PEOPLE, Icons.Filled.People),
                Pair(MainItem.TRAINERS, Icons.Filled.FitnessCenter),
                Pair(MainItem.GROUPS, Icons.Filled.Groups),
                Pair(MainItem.LEADERBOARD, Icons.Filled.EmojiEvents)
            )

            mainItems.forEach { (item, icon) ->
                val label = when (item) {
                    MainItem.PEOPLE -> AppStrings.current.people
                    MainItem.TRAINERS -> AppStrings.current.trainersAndSpaces
                    MainItem.GROUPS -> AppStrings.current.trainingGroups
                    MainItem.LEADERBOARD -> AppStrings.current.leaderboard
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable {
                            when (item) {
                                MainItem.PEOPLE -> onPeopleClick()
                                MainItem.TRAINERS -> onTrainersClick()
                                MainItem.GROUPS -> onGroupsClick()
                                MainItem.LEADERBOARD -> onLeaderboardClick()
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Druhá sekce - Aplikace
            Text(
                text = AppStrings.current.appSection,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
            )

            val settingsItems = listOf(
                Pair(SettingsItem.LANGUAGES, Icons.Filled.Language),
                Pair(SettingsItem.ABOUT, Icons.Filled.Info),
                Pair(SettingsItem.NOTIFICATIONS, Icons.Filled.Notifications),
                Pair(SettingsItem.PRIVACY, Icons.Filled.Security)
            )

            settingsItems.forEach { (item, icon) ->
                val label = when (item) {
                    SettingsItem.LANGUAGES -> AppStrings.current.languages
                    SettingsItem.ABOUT -> AppStrings.current.aboutApp
                    SettingsItem.NOTIFICATIONS -> AppStrings.current.notificationSettings
                    SettingsItem.PRIVACY -> AppStrings.current.privacyPolicy
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable {
                            when (item) {
                                SettingsItem.LANGUAGES -> onLanguagesClick()
                                SettingsItem.ABOUT -> onAboutClick()
                                SettingsItem.NOTIFICATIONS -> onNotificationsClick()
                                SettingsItem.PRIVACY -> onPrivacyClick()
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

                if (showDebug) {
                    Text("personId: ${state.personId ?: "(null)"}", style = MaterialTheme.typography.bodySmall)
                    Text("coupleIds: ${if (state.coupleIds.isEmpty()) "[]" else state.coupleIds.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                    if (state.rawJson != null) Text("raw: ${state.rawJson}", style = MaterialTheme.typography.bodySmall)
                    if (state.personDetailsRaw != null) Text("person: ${state.personDetailsRaw}", style = MaterialTheme.typography.bodySmall)
                    if (state.personDob != null) Text("birth: ${state.personDob}", style = MaterialTheme.typography.bodySmall)
                }
        }
    }
}
