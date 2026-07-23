package com.example.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.data.vLockDatabase
import com.example.data.vLockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            val pendingResult = goAsync()
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = vLockDatabase.getDatabase(appContext)
                    val repository = vLockRepository(db)
                    for (sms in messages) {
                        val sender = sms.originatingAddress ?: ""
                        val body = sms.messageBody ?: ""
                        val timestamp = if (sms.timestampMillis > 0) sms.timestampMillis else System.currentTimeMillis()
                        if (body.isNotBlank()) {
                            val updatedLog = repository.recordSmsReply(body, sender, timestamp)
                            withContext(Dispatchers.Main) {
                                SmsReplyHandler.onSmsReceived(sender, body, timestamp, updatedLog)
                            }
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
}
