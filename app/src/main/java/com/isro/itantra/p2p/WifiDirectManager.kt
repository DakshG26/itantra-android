package com.isro.itantra.p2p

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.os.Build
import android.util.Log

class WifiDirectManager(
    private val context: Context,
    private val onPeersAvailable: (List<WifiP2pDevice>) -> Unit,
    private val onConnectionInfoAvailable: (WifiP2pInfo) -> Unit,
    private val onStatusUpdate: (String) -> Unit
) {

    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, context.mainLooper, null)
    private var receiver: BroadcastReceiver? = null

    val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    init {
        setupReceiver()
    }

    private fun setupReceiver() {
        receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                            onStatusUpdate("Wi-Fi Direct Active (0% Internet)")
                        } else {
                            onStatusUpdate("Wi-Fi Direct Disabled. Please enable Wi-Fi.")
                        }
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        manager?.requestPeers(channel) { peerList ->
                            val peers = peerList.deviceList.toList()
                            onPeersAvailable(peers)
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        manager?.requestConnectionInfo(channel) { info ->
                            onConnectionInfoAvailable(info)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        if (manager == null || channel == null) return
        onStatusUpdate("Scanning for nearby phones via Wi-Fi Direct…")
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("WifiDirect", "Peer discovery started successfully")
            }

            override fun onFailure(reasonCode: Int) {
                onStatusUpdate("Discovery failed ($reasonCode)")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun createGroup() {
        if (manager == null || channel == null) return
        onStatusUpdate("Creating autonomous P2P group…")
        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onStatusUpdate("Radio Node Group Created ✓")
            }

            override fun onFailure(reason: Int) {
                onStatusUpdate("Failed to create group ($reason)")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: WifiP2pDevice) {
        if (manager == null || channel == null) return
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }

        onStatusUpdate("Connecting to ${device.deviceName}…")
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("WifiDirect", "Connection initiated with ${device.deviceName}")
            }

            override fun onFailure(reason: Int) {
                onStatusUpdate("Connection failed ($reason)")
            }
        })
    }

    fun register() {
        try {
            context.registerReceiver(receiver, intentFilter)
        } catch (e: Exception) {
            Log.e("WifiDirect", "Register error: $e")
        }
    }

    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e("WifiDirect", "Unregister error: $e")
        }
    }
}
