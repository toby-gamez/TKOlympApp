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

class MyNearestEventWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val loggedIn = WidgetDataProvider.isLoggedIn(context)
        val event = if (loggedIn) WidgetDataProvider.fetchMyUpcomingEvents(context, limit = 1).firstOrNull() else null

        provideContent {
            GlanceTheme(colors = WidgetColorProviders) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(16.dp)
                        .clickable(actionStartActivity(deepLinkIntent(context, "calendar")))
                ) {
                    // Left accent bar
                    val barColor = if (event?.colorRgb != null) {
                        runCatching {
                            ColorProvider(Color(android.graphics.Color.parseColor(event.colorRgb)))
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
                            event == null -> {
                                Spacer(GlanceModifier.height(8.dp))
                                WidgetEmptyState(AppStrings.current.widget.noNearestEvent)
                            }
                            else -> {
                                Spacer(GlanceModifier.height(8.dp))

                                // Event title — prominent
                                Text(
                                    text = event.title,
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = GlanceTheme.colors.onSurface
                                    ),
                                    maxLines = 2,
                                    modifier = GlanceModifier.fillMaxWidth()
                                )

                                Spacer(GlanceModifier.height(8.dp))

                                // Time slots
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val visibleSlots = event.slots.take(3)
                                    val overflow = event.slots.size - visibleSlots.size
                                    visibleSlots.forEach { slotTime ->
                                        Row(
                                            modifier = GlanceModifier
                                                .background(GlanceTheme.colors.surfaceVariant)
                                                .cornerRadius(8.dp)
                                                .padding(horizontal = 6.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Image(
                                                provider = ImageProvider(R.drawable.ic_widget_time),
                                                contentDescription = null,
                                                modifier = GlanceModifier.size(12.dp),
                                                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                                            )
                                            Spacer(GlanceModifier.width(3.dp))
                                            Text(
                                                text = formatWidgetTime(slotTime),
                                                style = TextStyle(
                                                    fontSize = 10.sp,
                                                    color = GlanceTheme.colors.onSurface
                                                ),
                                                maxLines = 1
                                            )
                                        }
                                        Spacer(GlanceModifier.width(4.dp))
                                    }
                                    if (overflow > 0) {
                                        Row(
                                            modifier = GlanceModifier
                                                .background(GlanceTheme.colors.surfaceVariant)
                                                .cornerRadius(8.dp)
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "+$overflow",
                                                style = TextStyle(
                                                    fontSize = 10.sp,
                                                    color = GlanceTheme.colors.onSurfaceVariant
                                                )
                                            )
                                        }
                                    }
                                }

                                // Location badge
                                if (!event.location.isNullOrBlank()) {
                                    Spacer(GlanceModifier.height(6.dp))
                                    Row(
                                        modifier = GlanceModifier
                                            .background(GlanceTheme.colors.surfaceVariant)
                                            .cornerRadius(8.dp)
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(
                                            provider = ImageProvider(R.drawable.ic_widget_place),
                                            contentDescription = null,
                                            modifier = GlanceModifier.size(12.dp),
                                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                                        )
                                        Spacer(GlanceModifier.width(4.dp))
                                        Text(
                                            text = event.location!!,
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
}

class MyNearestEventWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MyNearestEventWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdateWorker.scheduleOneshot(context)
        WidgetUpdateWorker.schedule(context)
    }
}
