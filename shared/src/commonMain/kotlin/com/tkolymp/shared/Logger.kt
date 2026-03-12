package com.tkolymp.shared

object Logger {
    var isDebugEnabled = false

    fun d(tag: String, msg: String) {
        if (isDebugEnabled) println("[$tag] $msg")
    }
}
