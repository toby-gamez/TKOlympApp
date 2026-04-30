package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.utils.formatTimesWithDate
import com.tkolymp.shared.utils.translateEventType

internal fun parseColorOrDefault(hex: String?): Color {
    if (hex.isNullOrBlank()) return Color.Gray
    return try {
        var s = hex.trim().trimStart('#')
        if (s.length == 6) s = "FF$s"
        Color(s.toLong(16).toInt())
    } catch (_: Exception) {
        Color.Gray
    }
}

@Composable
internal fun RenderSingleEventCard(item: EventInstance, onEventClick: (Long) -> Unit, showType: Boolean = true, modifier: Modifier = Modifier) {
    val name = item.event?.name ?: "(no name)"
    val cancelled = item.isCancelled
    val eventObj = item.event
    val locationOrTrainer = listOfNotNull(
        eventObj?.locationText?.takeIf { !it.isNullOrBlank() },
        eventObj?.location?.name?.takeIf { !it.isNullOrBlank() },
        eventObj?.eventTrainersList?.firstOrNull()?.takeIf { !it.isNullOrBlank() }
    ).firstOrNull().orEmpty()
    val timeText = formatTimesWithDate(item.since, item.until)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                (item.event?.id as? Number)?.toLong()?.let { onEventClick(it) }
            }
            .then(Modifier),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            val cohorts = item.event?.eventTargetCohortsList ?: emptyList()
            val cohortColors = cohorts.mapNotNull { tc ->
                val hex = tc.cohort?.colorRgb
                if (hex.isNullOrBlank()) null else try { parseColorOrDefault(hex) } catch (_: Exception) { null }
            }

            Column(
                modifier = Modifier
                    .width(6.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                if (cohortColors.isNotEmpty()) {
                    cohortColors.forEach { color ->
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(color)
                        )
                    }
                } else {
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium.copy(textDecoration = if (cancelled) TextDecoration.LineThrough else TextDecoration.None)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (showType) {
                        val typeText = item.event?.type ?: ""
                        val displayType = translateEventType(typeText)
                        if (!displayType.isNullOrBlank()) {
                            Box(modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                androidx.compose.material3.Text(displayType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                val boxBg = MaterialTheme.colorScheme.surfaceVariant
                FlowRow(modifier = Modifier.fillMaxWidth()) {
                    if (locationOrTrainer.isNotBlank()) {
                        Box(modifier = Modifier
                            .padding(end = 8.dp, bottom = 8.dp)
                            .background(boxBg, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                androidx.compose.material3.Text(locationOrTrainer, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                    }

                    if (timeText.isNotBlank()) {
                        Box(modifier = Modifier
                            .padding(end = 8.dp, bottom = 8.dp)
                            .background(boxBg, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                androidx.compose.material3.Text(timeText, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
