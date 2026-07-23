package com.example.utils

import com.example.data.SentSmsLog
import com.example.viewmodel.vLockViewModel
import java.lang.ref.WeakReference

object SmsReplyHandler {
    private var viewModelRef: WeakReference<vLockViewModel>? = null

    fun registerViewModel(viewModel: vLockViewModel) {
        viewModelRef = WeakReference(viewModel)
    }

    fun unregisterViewModel() {
        viewModelRef = null
    }

    fun onSmsReceived(
        sender: String,
        message: String,
        timestamp: Long = System.currentTimeMillis(),
        updatedLog: SentSmsLog? = null
    ) {
        val vm = viewModelRef?.get()
        if (vm != null) {
            if (updatedLog != null) {
                vm.showReplyPopup(updatedLog)
            } else {
                vm.triggerReplyReceived(sender, message, timestamp)
            }
        }
    }
}
