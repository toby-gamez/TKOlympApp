package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.achievements.BadgeCategory
import com.tkolymp.shared.language.AchievementStrings
import com.tkolymp.shared.viewmodels.BadgeUiState
import com.tkolymp.tkolympapp.ui.theme.AppTheme

/**
 * Emits a category header + 3-column tile grid for [badges] into an existing [LazyGridScope].
 * Meant to be called from inside the screen's own `LazyVerticalGrid` (nesting a second
 * independently-scrolling grid inside a scrollable column isn't supported by Compose).
 */
fun LazyGridScope.badgeGridSections(
    badges: List<BadgeUiState>,
    strings: AchievementStrings,
    onBadgeClick: (BadgeUiState) -> Unit,
) {
    val grouped = badges.groupBy { it.definition.category }
    val order = listOf(
        BadgeCategory.CAMP,
        BadgeCategory.MEMBERSHIP,
        BadgeCategory.ATTENDANCE,
        BadgeCategory.COMPETITIONS,
        BadgeCategory.REPERTOIRE,
        BadgeCategory.RHYTHM,
    )
    order.forEach { category ->
        val items = grouped[category].orEmpty()
        if (items.isEmpty()) return@forEach

        item(span = { GridItemSpan(maxLineSpan) }) {
            val earnedInCategory = items.count { it.earned }
            Column {
                Text(
                    text = when (category) {
                        BadgeCategory.CAMP -> strings.sectionCamps
                        BadgeCategory.MEMBERSHIP -> strings.sectionMembership
                        BadgeCategory.ATTENDANCE -> strings.sectionAttendance
                        BadgeCategory.COMPETITIONS -> strings.sectionCompetitions
                        BadgeCategory.REPERTOIRE -> strings.sectionRepertoire
                        BadgeCategory.RHYTHM -> strings.sectionRhythm
                    } + " · $earnedInCategory/${items.size}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                LinearProgressIndicator(
                    progress = { earnedInCategory.toFloat() / items.size },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
            }
        }

        items(items, key = { it.definition.id }) { badge ->
            val progressText = badge.progress
                ?.takeIf { !badge.earned }
                ?.let { (current, target) -> "$current/$target" }
            BadgeTile(
                icon = badge.definition.icon,
                label = badge.definition.title(strings),
                earned = badge.earned,
                progressText = progressText,
                isNew = badge.isNew,
                onClick = { onBadgeClick(badge) },
            )
        }
    }
}

@Preview(name = "BadgeGrid — Light")
@Composable
private fun BadgeGridPreviewLight() {
    AppTheme(darkTheme = false) {
        LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            badgeGridSections(previewBadges(), AchievementStrings(), onBadgeClick = {})
        }
    }
}

@Preview(name = "BadgeGrid — Dark")
@Composable
private fun BadgeGridPreviewDark() {
    AppTheme(darkTheme = true) {
        LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            badgeGridSections(previewBadges(), AchievementStrings(), onBadgeClick = {})
        }
    }
}

private fun previewBadges(): List<BadgeUiState> =
    com.tkolymp.shared.achievements.BadgeRegistry.all.mapIndexed { index, definition ->
        BadgeUiState(
            definition = definition,
            earned = index % 2 == 0,
            earnedOn = null,
            progress = null,
            isNew = false,
        )
    }
