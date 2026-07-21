package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.widget.Toast
import com.example.data.SentSmsLog
import com.example.data.vLockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsSender {
    suspend fun sendSms(
        context: Context,
        repository: vLockRepository,
        buttonName: String,
        smsCode: String,
        receiverNumber: String,
        sendingMode: String, // "Background" or "Default App"
        showToast: Boolean,
        vibrate: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (receiverNumber.isBlank()) {
            withContext(Dispatchers.Main) {
                if (showToast) {
                    Toast.makeText(context, "Error: Receiver phone number not set in Settings!", Toast.LENGTH_LONG).show()
                }
            }
            repository.insertLog(
                SentSmsLog(
                    buttonName = buttonName,
                    smsCode = smsCode,
                    receiverNumber = receiverNumber,
                    sendingMode = sendingMode,
                    status = "FAILURE",
                    errorMessage = "Receiver number not configured"
                )
            )
            return@withContext false
        }

        if (sendingMode.equals("Background", ignoreCase = true)) {
            val hasPermission = context.checkSelfPermission(android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                withContext(Dispatchers.Main) {
                    if (showToast) {
                        Toast.makeText(context, "Error: SMS permission denied!", Toast.LENGTH_LONG).show()
                    }
                }
                repository.insertLog(
                    SentSmsLog(
                        buttonName = buttonName,
                        smsCode = smsCode,
                        receiverNumber = receiverNumber,
                        sendingMode = "Background",
                        status = "FAILURE",
                        errorMessage = "SEND_SMS permission not granted"
                    )
                )
                return@withContext false
            }

            try {
                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                smsManager.sendTextMessage(receiverNumber, null, smsCode, null, null)

                if (vibrate) {
                    VibratorUtils.vibrate(context, 100)
                }

                withContext(Dispatchers.Main) {
                    if (showToast) {
                        Toast.makeText(context, "SMS Sent: $smsCode to $receiverNumber", Toast.LENGTH_SHORT).show()
                    }
                }

                repository.insertLog(
                    SentSmsLog(
                        buttonName = buttonName,
                        smsCode = smsCode,
                        receiverNumber = receiverNumber,
                        sendingMode = "Background",
                        status = "SUCCESS"
                    )
                )
                return@withContext true
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (showToast) {
                        Toast.makeText(context, "Error sending SMS: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
                repository.insertLog(
                    SentSmsLog(
                        buttonName = buttonName,
                        smsCode = smsCode,
                        receiverNumber = receiverNumber,
                        sendingMode = "Background",
                        status = "FAILURE",
                        errorMessage = e.localizedMessage
                    )
                )
                return@withContext false
            }
        } else {
            // Default Messaging App Mode
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$receiverNumber")
                    putExtra("sms_body", smsCode)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)

                if (vibrate) {
                    VibratorUtils.vibrate(context, 100)
                }

                withContext(Dispatchers.Main) {
                    if (showToast) {
                        Toast.makeText(context, "Opened SMS app with: $smsCode", Toast.LENGTH_SHORT).show()
                    }
                }

                repository.insertLog(
                    SentSmsLog(
                        buttonName = buttonName,
                        smsCode = smsCode,
                        receiverNumber = receiverNumber,
                        sendingMode = "Default App",
                        status = "OPENED"
                    )
                )
                return@withContext true
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (showToast) {
                        Toast.makeText(context, "Failed to open SMS app: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
                repository.insertLog(
                    SentSmsLog(
                        buttonName = buttonName,
                        smsCode = smsCode,
                        receiverNumber = receiverNumber,
                        sendingMode = "Default App",
                        status = "FAILURE",
                        errorMessage = e.localizedMessage
                    )
                )
                return@withContext false
            }
        }
    }
}
