package com.tkolymp.shared.network

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
class NetworkMonitorIos : NetworkMonitor {
    @kotlin.concurrent.Volatile
    private var isOnline: Boolean = true

    init {
        val monitor = nw_path_monitor_create()
        if (monitor != null) {
            nw_path_monitor_set_update_handler(monitor) { path ->
                isOnline = path != null && nw_path_get_status(path) == nw_path_status_satisfied
            }
            nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
            nw_path_monitor_start(monitor)
        }
    }

    override fun isConnected(): Boolean = isOnline
}
