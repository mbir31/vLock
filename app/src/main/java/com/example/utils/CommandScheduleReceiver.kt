package com.example.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.data.SentSmsLog
import com.example.data.vLockDatabase
import com.example.data.vLockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class CommandScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra("SCHEDULE_ID", -1L)
        val buttonIdExtra = intent.getStringExtra("BUTTON_ID") ?: ""

        if (scheduleId == -1L && buttonIdExtra.isBlank()) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = vLockDatabase.getDatabase(context)
                val schedule = db.commandScheduleDao().getById(scheduleId)

                if (schedule != null && schedule.isEnabled) {
                    val currentCalendar = Calendar.getInstance()
                    val todayDay = currentCalendar.get(Calendar.DAY_OF_WEEK)

                    // Verify if today is an active day for this schedule
                    if (ScheduleManager.isDaySelected(schedule.daysOfWeek, todayDay)) {
                        val buttons = db.buttonConfigDao().getAllList()
                        val targetButton = buttons.find { it.id == schedule.buttonId || it.id == buttonIdExtra }
                        val settings = db.appSettingDao().getAll().associate { it.key to it.value }

                        val receiverNumber = settings["receiver_number"] ?: ""
                        val sendingMode = settings["sending_mode"] ?: "Background"

                        if (targetButton != null && receiverNumber.isNotBlank()) {
                            val repository = vLockRepository(db)
                            SmsSender.sendSms(
                                context = context,
                                repository = repository,
                                buttonName = "${targetButton.name} (Scheduled)",
                                smsCode = targetButton.smsCode,
                                receiverNumber = receiverNumber,
                                sendingMode = sendingMode,
                                showToast = true,
                                vibrate = true
                            )
                        }
                    }

                    // Schedule next run
                    ScheduleManager.scheduleAlarm(context, schedule)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
