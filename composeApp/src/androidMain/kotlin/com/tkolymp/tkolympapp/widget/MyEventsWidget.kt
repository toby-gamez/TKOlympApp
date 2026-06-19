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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import com.tkolymp.shared.calendar.TimelineEvent
import com.tkolymp.tkolympapp.R

class MyEventsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val loggedIn = WidgetDataProvider.isLoggedIn(context)
        val events = if (loggedIn) WidgetDataProvider.fetchMyUpcomingEvents(context, limit = 10) else emptyList()

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
                    Text(
                        text = "My Events",
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                    Spacer(GlanceModifier.height(8.dp))
                    when {
                        !loggedIn -> WidgetEmptyState("Not logged in")
                        events.isEmpty() -> WidgetEmptyState("No upcoming events")
                        else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                            items(events, itemId = { it.startTime.toString().hashCode().toLong() }) { event ->
                                EventRow(event)
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
private fun EventRow(event: TimelineEvent) {
    val cohortColor = runCatching {
        Color(android.graphics.Color.parseColor(event.colorRgb))
    }.getOrDefault(Color(0xFFEE1733))

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = GlanceModifier
                .width(6.dp)
                .height(44.dp)
                .background(ColorProvider(cohortColor))
                .cornerRadius(6.dp)
        ) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = event.title,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSurface
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        text = formatWidgetTime(event.startTime),
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = GlanceTheme.colors.onSurface
                        ),
                        maxLines = 1
                    )
                }
                val location = event.event?.locationText
                if (!location.isNullOrBlank()) {
                    Spacer(GlanceModifier.width(4.dp))
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
                            text = location,
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

class MyEventsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MyEventsWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdateWorker.scheduleOneshot(context)
        WidgetUpdateWorker.schedule(context)
    }
}
