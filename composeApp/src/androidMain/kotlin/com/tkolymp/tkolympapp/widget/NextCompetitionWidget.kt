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
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.size
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.language.CompetitionStrings
import com.tkolymp.tkolympapp.R
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

class NextCompetitionWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val loggedIn = WidgetDataProvider.isLoggedIn(context)
        val competition = if (loggedIn) WidgetDataProvider.fetchNextCompetition(context) else null

        provideContent {
            GlanceTheme(colors = WidgetColorProviders) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(16.dp)
                        .clickable(actionStartActivity(deepLinkIntent(context, "competitions")))
                ) {
                    Box(
                        modifier = GlanceModifier
                            .width(6.dp)
                            .fillMaxHeight()
                            .background(ColorProvider(Color(0xFFEE1733)))
                            .cornerRadius(6.dp)
                    ) {}

                    Column(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = AppStrings.current.competition.nearestCompetition,
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
                            competition == null -> {
                                Spacer(GlanceModifier.height(8.dp))
                                WidgetEmptyState(AppStrings.current.competition.noUpcoming)
                            }
                            else -> {
                                val days = daysUntil(competition.competitionDate)

                                // Event name — most prominent
                                Spacer(GlanceModifier.height(6.dp))
                                competition.eventName?.let { name ->
                                    Text(
                                        text = name,
                                        style = TextStyle(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = GlanceTheme.colors.onSurface
                                        ),
                                        maxLines = 2,
                                        modifier = GlanceModifier.fillMaxWidth()
                                    )
                                }

                                Spacer(GlanceModifier.height(8.dp))

                                // Countdown row: large number + label inline
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
                                            text = if (days == 1) AppStrings.current.widget.dayAway else AppStrings.current.widget.daysAway,
                                            style = TextStyle(
                                                fontSize = 10.sp,
                                                color = GlanceTheme.colors.onSurfaceVariant
                                            )
                                        )
                                    }
                                }

                                Spacer(GlanceModifier.height(8.dp))

                                // Metadata pill: location · formatted type
                                val strings = CompetitionStrings()
                                val typeLabel = competition.competitionType
                                    ?.let { strings.formatType(it) }
                                    ?.ifBlank { null }
                                    ?: competition.category?.name

                                val meta = listOfNotNull(
                                    competition.eventLocation,
                                    typeLabel
                                ).joinToString(" · ")
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

    private fun daysUntil(competitionDate: String): Int {
        return try {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val compDate = LocalDate.parse(competitionDate)
            (compDate.toEpochDays() - today.toEpochDays()).toInt().coerceAtLeast(0)
        } catch (_: Exception) { 0 }
    }
}

class NextCompetitionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NextCompetitionWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdateWorker.scheduleOneshot(context)
        WidgetUpdateWorker.schedule(context)
    }
}
