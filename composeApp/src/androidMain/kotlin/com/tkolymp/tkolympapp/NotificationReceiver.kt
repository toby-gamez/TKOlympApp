package com.tkolymp.tkolympapp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tkolymp.tkolympapp.widget.deepLinkIntent

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val idString = intent.getStringExtra("notificationId") ?: "0"
        val id = idString.hashCode()
        val title = intent.getStringExtra("title") ?: "Událost"
        val text = intent.getStringExtra("text") ?: "Událost právě začíná"
        val eventId = intent.getLongExtra("eventId", -1L).takeIf { it != -1L }
        val tab = intent.getIntExtra("tab", 0)

        val channelId = "tkolymp_events"

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (eventId != null) {
            val route = "event/$eventId?tab=$tab"
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val pendingIntent = PendingIntent.getActivity(context, id, deepLinkIntent(context, route), flags)
            builder.setContentIntent(pendingIntent)
        }

        with(NotificationManagerCompat.from(context)) {
            notify(id, builder.build())
        }
    }
}
