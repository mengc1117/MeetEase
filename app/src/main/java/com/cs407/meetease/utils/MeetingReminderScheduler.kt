package com.cs407.meetease.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
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
        private const val TAG = "MeetingReminderScheduler"
        
        // Generate unique request code based on meeting details
        private fun generateRequestCode(meetingDay: String, meetingTime: String): Int {
            return (meetingDay + meetingTime).hashCode()
        }
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleMeetingReminder(
        meetingDay: String,
        meetingTimeRange: String,
        attendeesCount: Int
    ): Boolean {
        try {
            Log.d(TAG, "Attempting to schedule reminder for: $meetingDay at $meetingTimeRange")
            
            // Check if we can schedule exact alarms on Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!canScheduleExactAlarms()) {
                    Log.e(TAG, "Cannot schedule exact alarms - permission not granted. Please enable in Settings.")
                    return false
                }
            }
            
            val reminderTime = calculateReminderTime(meetingDay, meetingTimeRange)
            
            if (reminderTime == null) {
                Log.e(TAG, "Failed to calculate reminder time for: $meetingDay $meetingTimeRange")
                return false
            }
            
            val now = System.currentTimeMillis()
            Log.d(TAG, "Reminder time: ${java.util.Date(reminderTime)}, Current time: ${java.util.Date(now)}")
            
            if (reminderTime <= now) {
                Log.w(TAG, "Meeting time is in the past (reminder: ${java.util.Date(reminderTime)} vs now: ${java.util.Date(now)})")
                return false
            }

            val startTime = extractStartTime(meetingTimeRange)
            val requestCode = generateRequestCode(meetingDay, startTime)
            
            val intent = Intent(context, MeetingReminderReceiver::class.java).apply {
                putExtra(MeetingReminderReceiver.EXTRA_MEETING_DAY, meetingDay)
                putExtra(MeetingReminderReceiver.EXTRA_MEETING_TIME, startTime)
                putExtra(MeetingReminderReceiver.EXTRA_ATTENDEES_COUNT, attendeesCount)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Schedule exact alarm
            try {
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
                Log.d(TAG, "Meeting reminder scheduled for: ${java.util.Date(reminderTime)} (RequestCode: $requestCode)")
                return true
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: Cannot schedule exact alarm", e)
                return false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule meeting reminder", e)
            return false
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.S)
    private fun canScheduleExactAlarms(): Boolean {
        return alarmManager.canScheduleExactAlarms()
    }

    fun cancelMeetingReminder(meetingDay: String, meetingTimeRange: String) {
        try {
            val startTime = extractStartTime(meetingTimeRange)
            val requestCode = generateRequestCode(meetingDay, startTime)
            
            val intent = Intent(context, MeetingReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Meeting reminder cancelled (RequestCode: $requestCode)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel meeting reminder", e)
        }
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
        try {
            Log.d(TAG, "Parsing meetingDay='$meetingDay', meetingTime='$meetingTime'")
            
            // Parse the date format "EEE M/d" (e.g., "Wed 12/10")
            // SimpleDateFormat with day-of-week can be lenient, so we'll parse just the date part
            val datePart = meetingDay.substringAfter(" ") // Extract "M/d" from "EEE M/d"
            Log.d(TAG, "Date part extracted: '$datePart'")
            
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            
            // Parse the date (M/d format) and add current year
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val dateFormat = SimpleDateFormat("M/d/yyyy", Locale.US)
            dateFormat.isLenient = false // Strict parsing
            val dateWithYear = "$datePart/$currentYear"
            Log.d(TAG, "Parsing date: '$dateWithYear'")
            val date = dateFormat.parse(dateWithYear)
            
            // Parse the time
            val time = timeFormat.parse(meetingTime)
            
            // Combine date and time
            val calendar = Calendar.getInstance()
            if (date != null) {
                calendar.time = date
            }
            
            if (time != null) {
                val timeCalendar = Calendar.getInstance()
                timeCalendar.time = time
                calendar.set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY))
                calendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE))
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            
            Log.d(TAG, "Parsed calendar: ${calendar.time}")
            
            // If the date is in the past, add a year
            val now = Calendar.getInstance()
            if (calendar.before(now)) {
                Log.d(TAG, "Date is in the past, checking if we need to add a year")
                // Only add a year if the month/day combination has already passed this year
                val testCal = Calendar.getInstance()
                testCal.set(Calendar.MONTH, calendar.get(Calendar.MONTH))
                testCal.set(Calendar.DAY_OF_MONTH, calendar.get(Calendar.DAY_OF_MONTH))
                testCal.set(Calendar.HOUR_OF_DAY, calendar.get(Calendar.HOUR_OF_DAY))
                testCal.set(Calendar.MINUTE, calendar.get(Calendar.MINUTE))
                testCal.set(Calendar.SECOND, 0)
                testCal.set(Calendar.MILLISECOND, 0)
                
                if (testCal.before(now)) {
                    calendar.add(Calendar.YEAR, 1)
                    Log.d(TAG, "Added 1 year, new date: ${calendar.time}")
                }
            }
            
            Log.d(TAG, "Final meeting time: ${calendar.time} (${calendar.timeInMillis})")
            return calendar.timeInMillis
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing meeting date/time: $meetingDay $meetingTime", e)
            throw e
        }
    }
}
