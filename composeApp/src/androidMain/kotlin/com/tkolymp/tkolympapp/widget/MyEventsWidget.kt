package com.tkolymp.tkolympapp.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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

class MyEventsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val loggedIn = WidgetDataProvider.isLoggedIn(context)
        val events = if (loggedIn) WidgetDataProvider.fetchMyUpcomingEvents(context) else emptyList()

        provideContent {
            GlanceTheme(colors = WidgetColorProviders) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(16.dp)
                        .padding(horizontal = 12.dp)
                        .clickable(actionStartActivity(deepLinkIntent(context, "calendar")))
                ) {
                    Spacer(GlanceModifier.height(12.dp))
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = AppStrings.current.widget.myTrainings,
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
                    Spacer(GlanceModifier.height(8.dp))
                    when {
                        !loggedIn -> WidgetEmptyState(AppStrings.current.widget.notLoggedIn)
                        events.isEmpty() -> WidgetEmptyState(AppStrings.current.widget.noUpcomingEvents)
                        else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                            items(
                                events,
                                itemId = { group -> group.eventId ?: group.title.hashCode().toLong() }
                            ) { group ->
                                EventGroupRow(group)
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun EventGroupRow(group: GroupedWidgetEvent) {
    val isLesson = group.colorRgb == null
    val hasLocation = !group.location.isNullOrBlank()

    // Bar height: title ~18dp + spacer 4dp + [location 20dp + spacer 4dp]? + slots row 20dp
    val barHeight: Dp = if (hasLocation) 72.dp else 48.dp

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Accent bar: surfaceVariant for lessons (matches LessonView), cohort color for events
        if (isLesson) {
            Box(
                modifier = GlanceModifier
                    .width(6.dp)
                    .height(barHeight)
                    .background(GlanceTheme.colors.surfaceVariant)
                    .cornerRadius(6.dp)
            ) {}
        } else {
            val cohortColor = runCatching {
                Color(android.graphics.Color.parseColor(group.colorRgb))
            }.getOrDefault(Color(0xFFEE1733))
            Box(
                modifier = GlanceModifier
                    .width(6.dp)
                    .height(barHeight)
                    .background(ColorProvider(cohortColor))
                    .cornerRadius(6.dp)
            ) {}
        }

        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = group.title,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSurface
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(4.dp))
            // Shared location badge — shown once for all slots, like LessonView
            if (hasLocation) {
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
                        text = group.location!!,
                        style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurface),
                        maxLines = 1
                    )
                }
                Spacer(GlanceModifier.height(4.dp))
            }
            // Time slot badges in a row — one per slot (up to 3, then +N overflow)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val visibleSlots = group.slots.take(3)
                val overflow = group.slots.size - visibleSlots.size
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
                            style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurface),
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
                            style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant)
                        )
                    }
                }
            }
        }
    }
}

class MyEventsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MyEventsWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdateWorker.scheduleOneshot(context)
        WidgetUpdateWorker.schedule(context)
    }
}
