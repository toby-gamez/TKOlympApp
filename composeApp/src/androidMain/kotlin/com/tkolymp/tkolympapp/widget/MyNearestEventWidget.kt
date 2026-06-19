package com.tkolymp.tkolympapp.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.tkolympapp.R
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

class MyNearestEventWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val loggedIn = WidgetDataProvider.isLoggedIn(context)
        val result = if (loggedIn) WidgetDataProvider.fetchNearestTrainingDay(context) else null

        val firstEvent = result?.events?.firstOrNull()
        val eventRoute = if (firstEvent?.eventId != null) {
            "event/${firstEvent.eventId}" +
                if (firstEvent.instanceId != null) "?instanceId=${firstEvent.instanceId}" else ""
        } else "calendar"

        provideContent {
            GlanceTheme(colors = WidgetColorProviders) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(16.dp)
                        .clickable(actionStartActivity(deepLinkIntent(context, eventRoute)))
                ) {
                    // Left accent bar — cohort color for events, surfaceVariant for lessons
                    val barColor = if (firstEvent?.colorRgb != null) {
                        runCatching {
                            ColorProvider(Color(android.graphics.Color.parseColor(firstEvent.colorRgb)))
                        }.getOrElse { ColorProvider(Color(0xFFEE1733)) }
                    } else {
                        GlanceTheme.colors.surfaceVariant
                    }
                    Box(
                        modifier = GlanceModifier
                            .width(6.dp)
                            .fillMaxHeight()
                            .background(barColor)
                            .cornerRadius(6.dp)
                    ) {}

                    Column(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        // Header
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = AppStrings.current.widget.myNearestEvent,
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = GlanceTheme.colors.onSurfaceVariant
                                )
                            )
                            Image(
                                provider = ImageProvider(R.drawable.ic_launcher_monochrome),
                                contentDescription = null,
                                modifier = GlanceModifier.size(26.dp),
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                            )
                        }

                        when {
                            !loggedIn -> {
                                Spacer(GlanceModifier.height(8.dp))
                                WidgetEmptyState(AppStrings.current.widget.notLoggedIn)
                            }
                            result == null || firstEvent == null -> {
                                Spacer(GlanceModifier.height(8.dp))
                                WidgetEmptyState(AppStrings.current.widget.noNearestEvent)
                            }
                            else -> {
                                val days = daysUntil(result.dateString)

                                // Event/trainer name — most prominent
                                Spacer(GlanceModifier.height(6.dp))
                                Text(
                                    text = firstEvent.title,
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = GlanceTheme.colors.onSurface
                                    ),
                                    maxLines = 2,
                                    modifier = GlanceModifier.fillMaxWidth()
                                )

                                Spacer(GlanceModifier.height(8.dp))

                                // Countdown — same layout as NextCompetitionWidget
                                if (days == 0) {
                                    Text(
                                        text = AppStrings.current.timeline.today,
                                        style = TextStyle(
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GlanceTheme.colors.primary
                                        )
                                    )
                                } else {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = "$days",
                                            style = TextStyle(
                                                fontSize = 26.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = GlanceTheme.colors.primary
                                            )
                                        )
                                        Spacer(GlanceModifier.width(4.dp))
                                        Column {
                                            Spacer(GlanceModifier.height(6.dp))
                                            Text(
                                                text = if (days == 1) AppStrings.current.widget.dayAway
                                                       else AppStrings.current.widget.daysAway,
                                                style = TextStyle(
                                                    fontSize = 10.sp,
                                                    color = GlanceTheme.colors.onSurfaceVariant
                                                )
                                            )
                                        }
                                    }
                                }

                                Spacer(GlanceModifier.height(8.dp))

                                // Metadata pill: first slot time · location
                                val timeStr = firstEvent.slots.firstOrNull()?.let { slot ->
                                    "${slot.hour.toString().padStart(2, '0')}:${slot.minute.toString().padStart(2, '0')}"
                                }
                                val meta = listOfNotNull(timeStr, firstEvent.location).joinToString(" · ")
                                if (meta.isNotBlank()) {
                                    Row(
                                        modifier = GlanceModifier
                                            .background(GlanceTheme.colors.surfaceVariant)
                                            .cornerRadius(8.dp)
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = meta,
                                            style = TextStyle(
                                                fontSize = 10.sp,
                                                color = GlanceTheme.colors.onSurface
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun daysUntil(dateString: String): Int {
        return try {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val target = LocalDate.parse(dateString)
            (target.toEpochDays() - today.toEpochDays()).toInt().coerceAtLeast(0)
        } catch (_: Exception) { 0 }
    }
}

class MyNearestEventWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MyNearestEventWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdateWorker.scheduleOneshot(context)
        WidgetUpdateWorker.schedule(context)
    }
}
