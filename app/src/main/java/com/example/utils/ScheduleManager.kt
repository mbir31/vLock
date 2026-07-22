package com.example.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.CommandSchedule
import com.example.data.vLockDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

object ScheduleManager {

    fun scheduleAlarm(context: Context, schedule: CommandSchedule) {
        if (!schedule.isEnabled) {
            cancelAlarm(context, schedule.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, CommandScheduleReceiver::class.java).apply {
            action = "com.example.ACTION_EXECUTE_SCHEDULED_COMMAND"
            putExtra("SCHEDULE_ID", schedule.id)
            putExtra("BUTTON_ID", schedule.buttonId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            schedule.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = calculateNextTriggerTime(schedule.hour, schedule.minute)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancelAlarm(context: Context, scheduleId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, CommandScheduleReceiver::class.java).apply {
            action = "com.example.ACTION_EXECUTE_SCHEDULED_COMMAND"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduleId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun rescheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = vLockDatabase.getDatabase(context)
            val activeSchedules = db.commandScheduleDao().getActiveSchedules()
            activeSchedules.forEach { schedule ->
                scheduleAlarm(context, schedule)
            }
        }
    }

    private fun calculateNextTriggerTime(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If time is in the past for today, schedule for tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return calendar.timeInMillis
    }

    fun isDaySelected(daysOfWeekStr: String, calendarDay: Int): Boolean {
        if (daysOfWeekStr.isBlank() || daysOfWeekStr.equals("Everyday", ignoreCase = true) || daysOfWeekStr.equals("Daily", ignoreCase = true)) {
            return true
        }
        val dayName = when (calendarDay) {
            Calendar.SUNDAY -> "Sun"
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            Calendar.SATURDAY -> "Sat"
            else -> ""
        }
        return daysOfWeekStr.contains(dayName, ignoreCase = true)
    }
}
