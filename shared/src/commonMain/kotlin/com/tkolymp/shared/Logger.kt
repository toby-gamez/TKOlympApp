package com.tkolymp.shared

expect object Logger {
    var isDebug: Boolean
    fun d(tag: String, msg: String)
    fun w(tag: String, msg: String, throwable: Throwable? = null)
    fun e(tag: String, msg: String, throwable: Throwable? = null)
}
