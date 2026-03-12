package com.masterdnsvpn.android

import android.net.VpnService
import android.util.Log

object VpnSocketProtector {
    private const val TAG = "VpnSocketProtector"

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

        val activeService = service
        if (activeService == null) {
            Log.w(TAG, "protectFd called before VpnService attachment. fd=$fd")
            return false
        }

        return try {
            activeService.protect(fd)
        } catch (throwable: Throwable) {
            Log.e(TAG, "protectFd failed for fd=$fd: ${throwable.message ?: throwable.javaClass.name}", throwable)
            false
        }
    }
}
