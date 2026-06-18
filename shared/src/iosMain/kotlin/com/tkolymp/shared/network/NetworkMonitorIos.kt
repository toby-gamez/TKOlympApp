package com.tkolymp.shared.network

import platform.Network.NWPathMonitor
import platform.Network.NWPathStatus
import platform.darwin.dispatch_get_main_queue

class NetworkMonitorIos : NetworkMonitor {
    @Volatile private var isOnline: Boolean = true
    private val monitor = NWPathMonitor()

    init {
        monitor.pathUpdateHandler = { path ->
            isOnline = path.status == NWPathStatus.satisfied
        }
        monitor.startWithQueue(dispatch_get_main_queue())
    }

    override fun isConnected(): Boolean = isOnline
}
