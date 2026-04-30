package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.durationMinutes
import com.tkolymp.shared.utils.formatTimes

@Composable
internal fun LessonView(
    trainerName: String,
    instances: List<EventInstance>,
    isAllTab: Boolean,
    myPersonId: String?,
    myCoupleIds: List<String>,
    onEventClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Text(trainerName, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        androidx.compose.material3.Text(AppStrings.current.eventCalendarTabs.lessonLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                val groupLocation = instances.mapNotNull { inst ->
                    inst.event?.locationText?.takeIf { !it.isNullOrBlank() } ?: inst.event?.location?.name?.takeIf { !it.isNullOrBlank() }
                }.firstOrNull().orEmpty()
                if (groupLocation.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val boxBg = MaterialTheme.colorScheme.surfaceVariant
                    Box(modifier = Modifier
                        .background(boxBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            androidx.compose.material3.Text(groupLocation, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                instances.sortedBy { it.since }.forEach { inst ->
                    val time = formatTimes(inst.since, inst.until)
                    val regs = inst.event?.eventRegistrationsList ?: emptyList()
                    val parts: List<Pair<String, Boolean>> = regs.mapNotNull { r ->
                        val display = r.person?.name ?: run {
                            val man = r.couple?.man
                            val woman = r.couple?.woman
                            if (man != null && woman != null) {
                                val manSurname = man.lastName?.takeIf { it.isNotBlank() } ?: man.firstName?.takeIf { it.isNotBlank() } ?: ""
                                val womanSurname = woman.lastName?.takeIf { it.isNotBlank() } ?: woman.firstName?.takeIf { it.isNotBlank() } ?: ""
                                val pair = listOfNotNull(manSurname.takeIf { it.isNotBlank() }, womanSurname.takeIf { it.isNotBlank() }).joinToString(" - ")
                                if (pair.isNotBlank()) pair else null
                            } else null
                        }
                        if (display == null) null else {
                            val personIdStr = r.person?.id?.toString()
                            val coupleIdStr = r.couple?.id?.toString()
                            val isMine = (myPersonId != null && personIdStr == myPersonId) || (coupleIdStr != null && myCoupleIds.contains(coupleIdStr))
                            Pair(display, isMine)
                        }
                    }
                    val participantsEmpty = parts.isEmpty()
                    val durationMin = durationMinutes(inst.since, inst.until)
                    val deco = if (inst.isCancelled) TextDecoration.LineThrough else TextDecoration.None

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val evId = inst.event?.id ?: return@clickable
                                onEventClick(evId)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(30.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                androidx.compose.material3.Text(
                                    time,
                                    style = MaterialTheme.typography.bodySmall.copy(textDecoration = deco)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            if (participantsEmpty) {
                                androidx.compose.material3.Text(
                                    "VOLNO",
                                    style = MaterialTheme.typography.bodySmall.copy(textDecoration = deco, fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFF4CAF50)
                                )
                            } else {
                                val annotated = buildAnnotatedString {
                                    parts.forEachIndexed { idx, (display, isMine) ->
                                        if (isAllTab && isMine) {
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append(display) }
                                        } else append(display)
                                        if (idx != parts.lastIndex) append(", ")
                                    }
                                }
                                androidx.compose.material3.Text(annotated, style = MaterialTheme.typography.bodyMedium.copy(textDecoration = deco))
                            }
                        }

                        val boxBg = MaterialTheme.colorScheme.surfaceVariant
                        Box(
                            modifier = Modifier
                                .background(boxBg, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val resId = remember { context.resources.getIdentifier("clock_loader_80", "drawable", context.packageName) }
                                if (resId != 0) {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(id = resId),
                                        contentDescription = null,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                androidx.compose.material3.Text(
                                    durationMin ?: "",
                                    style = MaterialTheme.typography.bodySmall.copy(textDecoration = deco)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}
