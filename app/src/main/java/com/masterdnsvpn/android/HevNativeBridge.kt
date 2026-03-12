package com.masterdnsvpn.android

internal object HevNativeBridge {
    init {
        System.loadLibrary("mdvpn_tunnel_jni")
    }

    external fun nativeStart(configYaml: String, tunFd: Int): Int

    external fun nativeStop(): Int

    external fun nativeIsRunning(): Boolean

    external fun nativeLastResult(): Int
}
