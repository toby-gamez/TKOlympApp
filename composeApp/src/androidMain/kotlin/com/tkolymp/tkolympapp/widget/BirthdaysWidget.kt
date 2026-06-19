package com.tkolymp.tkolympapp.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class BirthdaysWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val loggedIn = WidgetDataProvider.isLoggedIn(context)
        val birthdays = if (loggedIn) WidgetDataProvider.fetchUpcomingBirthdays(context, limit = 10) else emptyList()

        provideContent {
            GlanceTheme(colors = WidgetColorProviders) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(16.dp)
                        .padding(horizontal = 12.dp)
                        .clickable(actionStartActivity(deepLinkIntent(context, "people")))
                ) {
                    Spacer(GlanceModifier.height(12.dp))
                    Text(
                        text = "Birthdays",
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                    Spacer(GlanceModifier.height(8.dp))
                    when {
                        !loggedIn -> WidgetEmptyState("Not logged in")
                        birthdays.isEmpty() -> WidgetEmptyState("No upcoming birthdays")
                        else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                            items(
                                birthdays,
                                itemId = { entry ->
                                    (entry.person.id ?: entry.person.firstName).hashCode().toLong()
                                }
                            ) { entry ->
                                BirthdayRow(entry)
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
private fun BirthdayRow(entry: BirthdayEntry) {
    // Use the person's first active cohort color, fall back to primary red
    val cohortColorRgb = entry.person.cohortMembershipsList.firstOrNull()?.cohort?.colorRgb
    val accentColor = runCatching {
        Color(android.graphics.Color.parseColor(cohortColorRgb))
    }.getOrDefault(Color(0xFFEE1733))

    val isToday = entry.daysUntil == 0

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
                .background(ColorProvider(accentColor))
                .cornerRadius(6.dp)
        ) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            val fullName = listOfNotNull(entry.person.firstName, entry.person.lastName)
                .joinToString(" ").ifBlank { "Unknown" }
            Text(
                text = fullName,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isToday) GlanceTheme.colors.primary else GlanceTheme.colors.onSurface
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(4.dp))
            Row(
                modifier = GlanceModifier
                    .background(GlanceTheme.colors.surfaceVariant)
                    .cornerRadius(8.dp)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatBirthdayLabel(entry.daysUntil, entry.age),
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

class BirthdaysWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = BirthdaysWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdateWorker.scheduleOneshot(context)
        WidgetUpdateWorker.schedule(context)
    }
}
