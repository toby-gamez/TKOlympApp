package com.tkolymp.shared

import android.util.Log
import com.tkolymp.tkolympapp.shared.BuildConfig

actual object Logger {
    actual fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    actual fun w(tag: String, msg: String) {
        Log.w(tag, msg)
    }

    actual fun e(tag: String, msg: String, throwable: Throwable?) {
        Log.e(tag, msg, throwable)
    }
}
