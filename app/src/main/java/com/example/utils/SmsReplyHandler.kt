package com.example.utils

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

    fun onSmsReceived(sender: String, message: String) {
        viewModelRef?.get()?.triggerReplyReceived(sender, message)
    }
}
