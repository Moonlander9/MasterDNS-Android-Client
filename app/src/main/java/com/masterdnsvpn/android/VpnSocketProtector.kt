package com.masterdnsvpn.android

import android.net.VpnService

object VpnSocketProtector {
    @Volatile
    private var service: VpnService? = null

    @JvmStatic
    fun attach(vpnService: VpnService) {
        service = vpnService
    }

    @JvmStatic
    fun detach(vpnService: VpnService) {
        if (service === vpnService) {
            service = null
        }
    }

    @JvmStatic
    fun protectFd(fd: Int): Boolean {
        if (fd < 0) {
            return false
        }
        return service?.protect(fd) ?: false
    }
}
