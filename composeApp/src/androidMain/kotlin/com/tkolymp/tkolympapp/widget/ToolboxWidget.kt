package com.tkolymp.tkolympapp.widget

import android.appwidget.AppWidgetManager
import android.content.Context
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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.tkolymp.tkolympapp.R

private data class ToolboxItem(val iconRes: Int, val label: String, val route: String)

private val toolboxItems = listOf(
    ToolboxItem(R.drawable.ic_widget_calendar, "Calendar", "calendar"),
    ToolboxItem(R.drawable.ic_widget_board, "Board", "board"),
    ToolboxItem(R.drawable.ic_widget_events, "Events", "events"),
    ToolboxItem(R.drawable.ic_widget_competitions, "Comps", "competitions"),
)

class ToolboxWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme(colors = WidgetColorProviders) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Use defaultWeight() in RowScope to distribute 4 items equally
                    toolboxItems.forEach { item ->
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight()
                                .clickable(actionStartActivity(deepLinkIntent(context, item.route))),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                provider = ImageProvider(item.iconRes),
                                contentDescription = item.label,
                                modifier = GlanceModifier.size(28.dp),
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface)
                            )
                            Spacer(GlanceModifier.height(4.dp))
                            Text(
                                text = item.label,
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    color = GlanceTheme.colors.onSurfaceVariant
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

class ToolboxWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ToolboxWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}
