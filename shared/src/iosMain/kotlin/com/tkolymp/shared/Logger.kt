package com.tkolymp.shared

import platform.Foundation.NSLog

/**
 * NSLog output lands in the device's unified log, readable by anyone with the phone on a Mac (Console.app)
 * and included in sysdiagnose bundles, so — like the Android Logger — nothing is logged unless [isDebug].
 * [isDebug] is set from the binary's build type in initNetworking().
 */
actual object Logger {
    actual var isDebug: Boolean = false

    actual fun d(tag: String, msg: String) {
        if (isDebug) NSLog("[$tag] D $msg")
    }

    actual fun w(tag: String, msg: String, throwable: Throwable?) {
        if (!isDebug) return
        if (throwable != null) {
            NSLog("[$tag] W $msg — ${throwable.message}")
        } else {
            NSLog("[$tag] W $msg")
        }
    }

    actual fun e(tag: String, msg: String, throwable: Throwable?) {
        if (!isDebug) return
        if (throwable != null) {
            NSLog("[$tag] E $msg — ${throwable.message}")
        } else {
            NSLog("[$tag] E $msg")
        }
    }
}
