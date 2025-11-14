package com.cs407.meetease.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cs407.meetease.MainActivity
import com.cs407.meetease.R

class MeetEaseNotificationManager(private val context: Context) {

    companion object {
        const val MEETING_REMINDER_CHANNEL_ID = "meeting_reminder_channel"
        const val MEETING_REMINDER_CHANNEL_NAME = "Meeting Reminders"
        const val MEETING_REMINDER_NOTIFICATION_ID = 1001
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val meetingChannel = NotificationChannel(
                MEETING_REMINDER_CHANNEL_ID,
                MEETING_REMINDER_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for upcoming meetings"
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(meetingChannel)
        }
    }

    fun showMeetingReminderNotification(
        meetingDay: String,
        meetingTime: String,
        attendeesCount: Int
    ) {
        // Create intent to open app when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            MEETING_REMINDER_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notification = NotificationCompat.Builder(context, MEETING_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Meeting Starting Soon!")
            .setContentText("Your meeting on $meetingDay at $meetingTime starts in 10 minutes")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Your meeting on $meetingDay at $meetingTime starts in 10 minutes. $attendeesCount members are expected to attend.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Open App",
                pendingIntent
            )
            .build()

        // Show notification
        try {
            NotificationManagerCompat.from(context).notify(
                MEETING_REMINDER_NOTIFICATION_ID,
                notification
            )
        } catch (e: SecurityException) {
            // Handle case where notification permission is not granted
            // This will be caught and handled by the calling code
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            true // Permission not required for older Android versions
        }
    }

    fun cancelMeetingReminder() {
        NotificationManagerCompat.from(context).cancel(MEETING_REMINDER_NOTIFICATION_ID)
    }
}