package com.tkolymp.tkolympapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val idString = intent.getStringExtra("notificationId") ?: "0"
        val id = idString.hashCode()
        val title = intent.getStringExtra("title") ?: "Událost"
        val text = intent.getStringExtra("text") ?: "Událost právě začíná"

        val channelId = "tkolymp_events"

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(id, builder.build())
        }
    }
}
