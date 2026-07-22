package com.example.utils

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.example.R
import com.example.data.ButtonConfig
import com.example.data.SentSmsLog
import com.example.data.vLockDatabase
import com.example.data.vLockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class vLockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateWidgetsInternal(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_WIDGET_BUTTON_CLICK) {
            val buttonId = intent.getStringExtra(EXTRA_BUTTON_ID) ?: return
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = vLockDatabase.getDatabase(context)
                    val buttons = db.buttonConfigDao().getAllList()
                    val targetButton = buttons.find { it.id == buttonId }
                    val settings = db.appSettingDao().getAll().associate { it.key to it.value }

                    val receiverNumber = settings["receiver_number"] ?: ""
                    val sendingMode = settings["sending_mode"] ?: "Background"

                    if (targetButton != null && receiverNumber.isNotBlank()) {
                        val repository = vLockRepository(db)
                        SmsSender.sendSms(
                            context = context,
                            repository = repository,
                            buttonName = "${targetButton.name} (Widget)",
                            smsCode = targetButton.smsCode,
                            receiverNumber = receiverNumber,
                            sendingMode = sendingMode,
                            showToast = true,
                            vibrate = true
                        )

                        // Refresh widget UI status
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        val appWidgetIds = appWidgetManager.getAppWidgetIds(
                            ComponentName(context, vLockWidgetProvider::class.java)
                        )
                        updateWidgetsInternal(context, appWidgetManager, appWidgetIds, "Sent ${targetButton.name}")
                    } else if (receiverNumber.isBlank()) {
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(
                                context,
                                "vLock Widget: Set receiver phone number in Settings first!",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_BUTTON_CLICK = "com.example.ACTION_WIDGET_BUTTON_CLICK"
        const val EXTRA_BUTTON_ID = "EXTRA_BUTTON_ID"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, vLockWidgetProvider::class.java)
            )
            CoroutineScope(Dispatchers.IO).launch {
                updateWidgetsInternal(context, appWidgetManager, appWidgetIds)
            }
        }

        private suspend fun updateWidgetsInternal(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            statusMessage: String? = null
        ) {
            val db = vLockDatabase.getDatabase(context)
            val allButtons = db.buttonConfigDao().getAllList().filter { it.isEnabled }
            val settings = db.appSettingDao().getAll().associate { it.key to it.value }

            val savedIdsStr = settings["widget_button_ids"] ?: ""
            val selectedButtonIds = if (savedIdsStr.isNotBlank()) {
                savedIdsStr.split(",").map { it.trim() }
            } else {
                allButtons.take(4).map { it.id }
            }

            val activeButtons = selectedButtonIds.mapNotNull { id -> allButtons.find { it.id == id } }.take(4)

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.vlock_widget_layout)

                if (!statusMessage.isNullOrBlank()) {
                    views.setTextViewText(R.id.widget_status, statusMessage)
                } else {
                    views.setTextViewText(R.id.widget_status, "Ready")
                }

                // Bind Button 1
                bindButtonSlot(
                    context, views, appWidgetId,
                    button = activeButtons.getOrNull(0),
                    containerId = R.id.widget_btn_1_container,
                    iconId = R.id.widget_btn_1_icon,
                    labelId = R.id.widget_btn_1_label
                )

                // Bind Button 2
                bindButtonSlot(
                    context, views, appWidgetId,
                    button = activeButtons.getOrNull(1),
                    containerId = R.id.widget_btn_2_container,
                    iconId = R.id.widget_btn_2_icon,
                    labelId = R.id.widget_btn_2_label
                )

                // Bind Button 3
                bindButtonSlot(
                    context, views, appWidgetId,
                    button = activeButtons.getOrNull(2),
                    containerId = R.id.widget_btn_3_container,
                    iconId = R.id.widget_btn_3_icon,
                    labelId = R.id.widget_btn_3_label
                )

                // Bind Button 4
                bindButtonSlot(
                    context, views, appWidgetId,
                    button = activeButtons.getOrNull(3),
                    containerId = R.id.widget_btn_4_container,
                    iconId = R.id.widget_btn_4_icon,
                    labelId = R.id.widget_btn_4_label
                )

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun bindButtonSlot(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            button: ButtonConfig?,
            containerId: Int,
            iconId: Int,
            labelId: Int
        ) {
            if (button == null) {
                views.setViewVisibility(containerId, View.GONE)
            } else {
                views.setViewVisibility(containerId, View.VISIBLE)
                views.setTextViewText(labelId, button.name)
                views.setTextViewText(iconId, getEmojiForButton(button))

                val intent = Intent(context, vLockWidgetProvider::class.java).apply {
                    action = ACTION_WIDGET_BUTTON_CLICK
                    putExtra(EXTRA_BUTTON_ID, button.id)
                }

                val requestCode = (appWidgetId * 10) + containerId.hashCode()
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                views.setOnClickPendingIntent(containerId, pendingIntent)
            }
        }

        private fun getEmojiForButton(button: ButtonConfig): String {
            return when (button.id.lowercase()) {
                "status" -> "⚡"
                "location" -> "📍"
                "lock" -> "🔒"
                "unlock" -> "🔓"
                "alarm_on" -> "🚨"
                "alarm_off" -> "🔕"
                "vs_call" -> "📞"
                "theft" -> "🛡️"
                "low_power" -> "🔋"
                "two_m_lock" -> "🔐"
                "sensitivity" -> "⚙️"
                "rf_remote" -> "📻"
                "apn" -> "🌐"
                "forgot_pin" -> "❓"
                "pin_reset" -> "🔢"
                "sub_admin" -> "👤"
                else -> "🔘"
            }
        }
    }
}
