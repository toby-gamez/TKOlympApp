package com.tkolymp.shared.device

import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

actual object DeviceInfo {
    actual val platformName: String = "iOS"

    actual val osVersion: String
        get() = "iOS ${UIDevice.currentDevice.systemVersion}"

    actual val deviceModel: String
        get() = UIDevice.currentDevice.model

    actual val appVersion: String
        get() {
            val info = NSBundle.mainBundle.infoDictionary
            val version = info?.get("CFBundleShortVersionString") as? String ?: "unknown"
            val build = info?.get("CFBundleVersion") as? String ?: "?"
            return "$version ($build)"
        }
}
