package com.tkolymp.tkolympapp.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.tkolympapp.MainActivity
import kotlinx.datetime.LocalDateTime

fun deepLinkIntent(context: Context, route: String): Intent {
    return Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("DEEP_LINK_ROUTE", route)
    }
}

@Suppress("DEPRECATION")
fun formatWidgetTime(dateTime: LocalDateTime): String {
    val hour = dateTime.hour.toString().padStart(2, '0')
    val minute = dateTime.minute.toString().padStart(2, '0')
    val day = dateTime.day.toString().padStart(2, '0')
    val month = dateTime.monthNumber.toString().padStart(2, '0')
    return "$day.$month $hour:$minute"
}

fun formatBirthdayLabel(daysUntil: Int, age: Int): String =
    AppStrings.current.widget.formatBirthdayLabel(daysUntil, age)

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
fun WidgetEmptyState(message: String) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = TextStyle(
                fontSize = 11.sp,
                color = GlanceTheme.colors.onSurfaceVariant
            )
        )
    }
}
