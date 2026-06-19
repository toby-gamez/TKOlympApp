package com.tkolymp.shared

import android.util.Log

actual object Logger {
    actual var isDebug: Boolean = false

    actual fun d(tag: String, msg: String) {
        if (isDebug) Log.d(tag, msg)
    }

    actual fun w(tag: String, msg: String) {
        if (isDebug) Log.w(tag, msg)
    }

    actual fun e(tag: String, msg: String, throwable: Throwable?) {
        if (isDebug) Log.e(tag, msg, throwable)
    }
}
