package com.tkolymp.shared.device

/** Best-effort platform/app diagnostics used in automatic error reports. */
expect object DeviceInfo {
    val platformName: String
    val osVersion: String
    val appVersion: String
    val deviceModel: String
}
