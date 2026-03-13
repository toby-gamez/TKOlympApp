package com.tkolymp.shared

import platform.Foundation.NSLog

actual object Logger {
    actual fun d(tag: String, msg: String) {
        NSLog("[$tag] D $msg")
    }

    actual fun w(tag: String, msg: String) {
        NSLog("[$tag] W $msg")
    }

    actual fun e(tag: String, msg: String, throwable: Throwable?) {
        if (throwable != null) {
            NSLog("[$tag] E $msg — ${throwable.message}")
        } else {
            NSLog("[$tag] E $msg")
        }
    }
}
