package com.isro.itantra.p2p

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpAutoDiscovery(
    private val context: Context,
    private val myNodeCode: String,
    private val myChannelCode: String,
    private val onNodeDiscovered: (String, String, String) -> Unit // ip, channelCode, nodeName
) {

    private val udpPort = 8889
    private var isRunning = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var broadcastSocket: DatagramSocket? = null
    private var listenSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start() {
        stop()
        isRunning = true

        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("iTantraUdpMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w("UdpDiscovery", "MulticastLock error: $e")
        }

        // 1. Listen for incoming UDP beacons from other phones
        scope.launch {
            try {
                listenSocket = DatagramSocket(udpPort).apply { reuseAddress = true; broadcast = true }
                val buffer = ByteArray(1024)

                while (isRunning) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    listenSocket?.receive(packet)

                    val senderIp = packet.address.hostAddress ?: continue
                    val message = String(packet.data, 0, packet.length).trim()

                    // Format: "iTantra:NODE-ALPHA:001"
                    if (message.startsWith("iTantra:")) {
                        val parts = message.split(":")
                        if (parts.size >= 3) {
                            val nodeName = parts[1]
                            val channelCode = parts[2]

                            if (nodeName != myNodeCode) {
                                mainHandler.post {
                                    onNodeDiscovered(senderIp, channelCode, nodeName)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.w("UdpDiscovery", "Listen exception: $e")
                }
            }
        }

        // 2. Broadcast own beacon every 2 seconds to local subnet
        scope.launch {
            try {
                broadcastSocket = DatagramSocket().apply { broadcast = true }
                val broadcastPayload = "iTantra:$myNodeCode:$myChannelCode".toByteArray()

                while (isRunning) {
                    try {
                        val broadcastIp = InetAddress.getByName("255.255.255.255")
                        val packet = DatagramPacket(broadcastPayload, broadcastPayload.size, broadcastIp, udpPort)
                        broadcastSocket?.send(packet)
                    } catch (e: Exception) {
                        // Suppress log noise if Wi-Fi is temporarily disconnected
                    }
                    delay(2000)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.w("UdpDiscovery", "Broadcast init error: $e")
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
            listenSocket?.close()
            broadcastSocket?.close()
        } catch (e: Exception) {
            Log.w("UdpDiscovery", "Close error: $e")
        }
        multicastLock = null
        listenSocket = null
        broadcastSocket = null
    }
}
