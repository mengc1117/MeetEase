package com.cs407.meetease.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MeetingReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_MEETING_DAY = "meeting_day"
        const val EXTRA_MEETING_TIME = "meeting_time"
        const val EXTRA_ATTENDEES_COUNT = "attendees_count"
        private const val TAG = "MeetingReminderReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Meeting reminder received")
        
        val meetingDay = intent.getStringExtra(EXTRA_MEETING_DAY) ?: return
        val meetingTime = intent.getStringExtra(EXTRA_MEETING_TIME) ?: return
        val attendeesCount = intent.getIntExtra(EXTRA_ATTENDEES_COUNT, 0)

        val notificationManager = MeetEaseNotificationManager(context)
        
        if (notificationManager.hasNotificationPermission()) {
            notificationManager.showMeetingReminderNotification(
                meetingDay,
                meetingTime,
                attendeesCount
            )
        } else {
            Log.w(TAG, "Notification permission not granted")
        }
    }
}

class MeetingReminderScheduler(private val context: Context) {

    companion object {
        private const val REQUEST_CODE_BASE = 2000
        private const val TAG = "MeetingReminderScheduler"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleMeetingReminder(
        meetingDay: String,
        meetingTimeRange: String,
        attendeesCount: Int
    ) {
        try {
            val reminderTime = calculateReminderTime(meetingDay, meetingTimeRange)
            
            if (reminderTime == null || reminderTime <= System.currentTimeMillis()) {
                Log.w(TAG, "Meeting time is in the past or invalid, not scheduling reminder")
                return
            }

            val intent = Intent(context, MeetingReminderReceiver::class.java).apply {
                putExtra(MeetingReminderReceiver.EXTRA_MEETING_DAY, meetingDay)
                putExtra(MeetingReminderReceiver.EXTRA_MEETING_TIME, extractStartTime(meetingTimeRange))
                putExtra(MeetingReminderReceiver.EXTRA_ATTENDEES_COUNT, attendeesCount)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_BASE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Schedule exact alarm (requires permission for Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    reminderTime,
                    pendingIntent
                )
            }

            Log.d(TAG, "Meeting reminder scheduled for: ${java.util.Date(reminderTime)}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule meeting reminder", e)
        }
    }

    fun cancelMeetingReminder() {
        val intent = Intent(context, MeetingReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Meeting reminder cancelled")
    }

    private fun calculateReminderTime(meetingDay: String, meetingTimeRange: String): Long? {
        try {
            // Parse meeting day and time
            val startTime = extractStartTime(meetingTimeRange)
            val meetingDateTime = parseMeetingDateTime(meetingDay, startTime)
            
            // Subtract 10 minutes for reminder
            val reminderTime = meetingDateTime - (10 * 60 * 1000) // 10 minutes in milliseconds
            
            return reminderTime
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating reminder time", e)
            return null
        }
    }

    private fun extractStartTime(timeRange: String): String {
        // Extract start time from "HH:mm - HH:mm" format
        return timeRange.split(" - ").firstOrNull() ?: timeRange
    }

    private fun parseMeetingDateTime(meetingDay: String, meetingTime: String): Long {
        // Get current calendar
        val calendar = Calendar.getInstance()
        
        // Parse day of week from meetingDay string (e.g. "Monday Nov 18")
        val dayParts = meetingDay.split(" ")
        val dayOfWeek = when (dayParts.firstOrNull()?.lowercase()) {
            "sunday" -> Calendar.SUNDAY
            "monday" -> Calendar.MONDAY
            "tuesday" -> Calendar.TUESDAY
            "wednesday" -> Calendar.WEDNESDAY
            "thursday" -> Calendar.THURSDAY
            "friday" -> Calendar.FRIDAY
            "saturday" -> Calendar.SATURDAY
            else -> calendar.get(Calendar.DAY_OF_WEEK)
        }

        // Set to next occurrence of this day
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        var daysToAdd = dayOfWeek - currentDayOfWeek
        if (daysToAdd < 0) {
            daysToAdd += 7 // Next week
        }
        
        calendar.add(Calendar.DAY_OF_MONTH, daysToAdd)

        // Parse and set time (format: "HH:mm")
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val time = timeFormat.parse(meetingTime)
        
        time?.let {
            val timeCalendar = Calendar.getInstance()
            timeCalendar.time = it
            
            calendar.set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY))
            calendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE))
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        }

        return calendar.timeInMillis
    }
}