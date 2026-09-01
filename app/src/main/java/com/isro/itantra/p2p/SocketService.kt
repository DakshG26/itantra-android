package com.isro.itantra.p2p

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class SocketService(
    private val onPacketReceived: (RadioPacket) -> Unit,
    private val onStatusChanged: (String) -> Unit
) {

    private val port = 8888
    private var serverSocket: ServerSocket? = null
    private val activeClients = CopyOnWriteArrayList<Socket>()
    private var outboundSocket: Socket? = null
    private var outboundWriter: PrintWriter? = null

    private var isRunning = false
    private var isServerActive = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun startServer() {
        if (isServerActive) return
        isRunning = true
        isServerActive = true

        scope.launch {
            try {
                serverSocket = ServerSocket(port).apply { reuseAddress = true }
                postStatus("Radio Server Active • Port $port (Ready)")
                Log.d("SocketService", "ServerSocket listening on port $port")

                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    activeClients.add(socket)
                    Log.d("SocketService", "Incoming radio link from: ${socket.inetAddress.hostAddress}")
                    postStatus("🟢 Radio Link Established with ${socket.inetAddress.hostAddress}")

                    // Read incoming stream from this socket
                    scope.launch {
                        handleClientStream(socket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.w("SocketService", "ServerSocket error: $e")
                }
            } finally {
                isServerActive = false
            }
        }
    }

    fun connectToHost(hostAddress: String) {
        scope.launch {
            try {
                postStatus("Connecting to Node at $hostAddress…")
                val socket = Socket()
                socket.connect(InetSocketAddress(hostAddress, port), 5000)
                outboundSocket = socket
                outboundWriter = PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream())), true)
                activeClients.add(socket)

                postStatus("🟢 Radio Link Established with $hostAddress")
                Log.d("SocketService", "Connected outbound to $hostAddress:$port")

                handleClientStream(socket)
            } catch (e: Exception) {
                Log.e("SocketService", "Client connection error: $e")
                postStatus("Link Failed ($hostAddress). Use Hotspot for 100% reliability.")
            }
        }
    }

    private fun handleClientStream(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            while (isRunning && !socket.isClosed) {
                val line = reader.readLine() ?: break
                val packet = RadioPacket.fromJson(line)
                if (packet != null) {
                    mainHandler.post {
                        onPacketReceived(packet.copy(isSentByMe = false))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SocketService", "Stream read finished/error: $e")
        } finally {
            activeClients.remove(socket)
            try {
                socket.close()
            } catch (ignored: Exception) {}
        }
    }

    fun sendPacket(packet: RadioPacket): Boolean {
        return try {
            scope.launch {
                val json = packet.toJson()

                // Send via outbound writer if connected
                outboundWriter?.println(json)
                outboundWriter?.flush()

                // Also broadcast to any active incoming clients
                for (client in activeClients) {
                    try {
                        val pw = PrintWriter(BufferedWriter(OutputStreamWriter(client.getOutputStream())), true)
                        pw.println(json)
                        pw.flush()
                    } catch (e: Exception) {
                        Log.w("SocketService", "Client broadcast error: $e")
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("SocketService", "Send error: $e")
            false
        }
    }

    private fun postStatus(status: String) {
        mainHandler.post { onStatusChanged(status) }
    }

    fun isConnected(): Boolean {
        return activeClients.isNotEmpty() || (outboundSocket?.isConnected == true && !outboundSocket!!.isClosed)
    }

    fun stop() {
        isRunning = false
        isServerActive = false
        try {
            outboundWriter?.close()
            outboundSocket?.close()
            for (c in activeClients) {
                try { c.close() } catch (ignored: Exception) {}
            }
            activeClients.clear()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w("SocketService", "Stop error: $e")
        }
        outboundSocket = null
        outboundWriter = null
        serverSocket = null
    }
}
