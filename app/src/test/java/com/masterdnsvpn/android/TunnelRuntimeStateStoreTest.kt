package com.masterdnsvpn.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TunnelRuntimeStateStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: TunnelRuntimeStateStore

    @Before
    fun setUp() {
        context.getSharedPreferences("tunnel_runtime_state_v1", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = TunnelRuntimeStateStore(context)
    }

    @Test
    fun load_returnsIdleDefaults() {
        val snapshot = store.load()

        assertFalse(snapshot.desiredRunning)
        assertTrue(snapshot.autoReconnect)
        assertNull(snapshot.configPath)
        assertEquals(TunnelStatus.IDLE, snapshot.status)
    }

    @Test
    fun saveCommandAndStatus_roundTripsSnapshot() {
        store.saveCommandState(
            desiredRunning = true,
            autoReconnect = false,
            configPath = "/tmp/client_config.toml",
        )
        store.saveStatus(
            TunnelEvent(
                type = "status",
                status = TunnelStatus.CONNECTED,
                message = "VPN tunnel connected.",
                code = "VPN_CONNECTED",
                timestamp = "2026-03-15T12:00:00Z",
            ),
        )

        val snapshot = store.load()

        assertTrue(snapshot.desiredRunning)
        assertFalse(snapshot.autoReconnect)
        assertEquals("/tmp/client_config.toml", snapshot.configPath)
        assertEquals(TunnelStatus.CONNECTED, snapshot.status)
        assertEquals("VPN tunnel connected.", snapshot.statusMessage)
        assertEquals("VPN_CONNECTED", snapshot.lastCode)
        assertEquals("2026-03-15T12:00:00Z", snapshot.lastTimestamp)
    }

    @Test
    fun snapshotWithoutDesiredRunning_normalizesConnectedStatusToIdle() {
        store.saveCommandState(
            desiredRunning = false,
            autoReconnect = true,
            configPath = "/tmp/client_config.toml",
        )
        store.saveStatus(
            TunnelEvent(
                type = "status",
                status = TunnelStatus.CONNECTED,
                message = "VPN tunnel connected.",
                code = "VPN_CONNECTED",
            ),
        )

        val event = store.load().toStatusEvent(context)

        assertEquals(TunnelStatus.IDLE, event.status)
        assertEquals(context.getString(R.string.status_idle), event.message)
    }
}
