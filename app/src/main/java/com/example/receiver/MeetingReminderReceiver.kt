package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class MeetingReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val meetingId = intent.getStringExtra("MEETING_ID") ?: return
        val meetingTitle = intent.getStringExtra("MEETING_TITLE") ?: "Yaklaşan Toplantı"
        val meetingDate = intent.getStringExtra("MEETING_DATE") ?: ""

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "meeting_reminders",
                "Toplantı Hatırlatıcıları",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Planlanan toplantılar için hatırlatıcılar"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent to open app
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            meetingId.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "meeting_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Toplantı Hatırlatması")
            .setContentText("$meetingTitle birazdan başlayacak ($meetingDate).")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(meetingId.hashCode(), notification)
    }
}
