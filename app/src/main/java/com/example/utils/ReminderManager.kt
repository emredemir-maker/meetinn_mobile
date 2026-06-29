package com.example.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.network.MeetingDto
import com.example.receiver.MeetingReminderReceiver

class ReminderManager(private val context: Context) {
    fun scheduleMeetingReminder(meeting: MeetingDto) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, MeetingReminderReceiver::class.java).apply {
            putExtra("MEETING_ID", meeting.id)
            putExtra("MEETING_TITLE", meeting.title)
            putExtra("MEETING_DATE", meeting.date)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            meeting.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Parse meeting date to long (milliseconds)
        // For demonstration, we'll just schedule it 10 seconds from now.
        // In a real app, you would parse `meeting.date` and subtract e.g. 15 minutes.
        val triggerTime = System.currentTimeMillis() + 10_000 // 10 seconds for demo

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            // Fallback for not having exact alarm permission
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }
}
