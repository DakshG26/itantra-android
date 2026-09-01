package com.isro.itantra.p2p

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.util.UUID

class BluetoothRadioManager(
    private val context: Context,
    private val onPacketReceived: (RadioPacket) -> Unit,
    private val onStatusUpdate: (String) -> Unit,
    private val onDeviceFound: (BluetoothDevice) -> Unit
) {

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    // Standard Bluetooth Serial Port Profile (SPP) UUID
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val serviceName = "iTantraSerialPort"

    private var serverSocket: BluetoothServerSocket? = null
    private var connectedSocket: BluetoothSocket? = null
    private var writer: PrintWriter? = null
    private var isRunning = false
    private var isServerListening = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        mainHandler.post { onDeviceFound(device) }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    postStatus("Scanning for nearby Bluetooth signals…")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    postStatus("Bluetooth scan complete.")
                }
            }
        }
    }

    init {
        registerReceiver()
    }

    fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        try {
            context.registerReceiver(discoveryReceiver, filter)
        } catch (e: Exception) {
            Log.w("BluetoothRadio", "Receiver register error: $e")
        }
    }

    fun unregisterReceiver() {
        try {
            context.unregisterReceiver(discoveryReceiver)
        } catch (e: Exception) {
            Log.w("BluetoothRadio", "Unregister error: $e")
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (bluetoothAdapter == null) {
            postStatus("Bluetooth not supported on this device")
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            postStatus("Bluetooth is OFF. Please turn ON Bluetooth.")
            return
        }
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        bluetoothAdapter.startDiscovery()
        postStatus("Scanning for Bluetooth signals…")
    }

    @SuppressLint("MissingPermission")
    fun startServer() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled || isServerListening) return
        isServerListening = true
        isRunning = true

        scope.launch {
            try {
                serverSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(serviceName, sppUuid)
                Log.d("BluetoothRadio", "Bluetooth SPP Server listening on RFCOMM...")

                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    val devName = socket.remoteDevice.name ?: socket.remoteDevice.address
                    Log.d("BluetoothRadio", "Incoming Bluetooth connection from $devName")

                    // Handle connection on separate coroutine so server keeps listening
                    scope.launch {
                        manageConnectedSocket(socket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.w("BluetoothRadio", "Server socket error: $e")
                }
            } finally {
                isServerListening = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        if (bluetoothAdapter == null) return

        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }

        val devName = device.name ?: device.address
        postStatus("Connecting to $devName via Bluetooth RFCOMM…")

        scope.launch {
            delay(300) // Allow Bluetooth hardware to settle after discovery cancel

            var socket: BluetoothSocket? = null

            // Strategy 1: Standard Insecure SPP RFCOMM
            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(sppUuid)
                socket.connect()
            } catch (e1: Exception) {
                Log.w("BluetoothRadio", "Standard SPP failed ($e1), attempting RFCOMM channel 1 fallback...")
                try {
                    socket?.close()
                } catch (ignored: Exception) {}

                // Strategy 2: Reflection fallback on channel 1 (works on Samsung/Android 12+)
                try {
                    val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    socket = method.invoke(device, 1) as BluetoothSocket
                    socket.connect()
                } catch (e2: Exception) {
                    Log.e("BluetoothRadio", "All Bluetooth connection strategies failed: $e2")
                    postStatus("Bluetooth Link Failed: ${e2.message}")
                    return@launch
                }
            }

            if (socket != null && socket.isConnected) {
                Log.d("BluetoothRadio", "Connected successfully to $devName")
                manageConnectedSocket(socket)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun manageConnectedSocket(socket: BluetoothSocket) {
        connectedSocket = socket
        writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket.outputStream)), true)
        val devName = socket.remoteDevice.name ?: socket.remoteDevice.address
        postStatus("🔵 Bluetooth Radio Link Active with $devName")

        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            while (isRunning && socket.isConnected) {
                val line = reader.readLine() ?: break
                val packet = RadioPacket.fromJson(line)
                if (packet != null) {
                    mainHandler.post {
                        onPacketReceived(packet.copy(isSentByMe = false))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("BluetoothRadio", "Socket stream read error: $e")
        } finally {
            postStatus("Bluetooth Radio Link Closed")
        }
    }

    fun sendPacket(packet: RadioPacket): Boolean {
        return try {
            scope.launch {
                val json = packet.toJson()
                writer?.println(json)
                writer?.flush()
            }
            true
        } catch (e: Exception) {
            Log.e("BluetoothRadio", "Send error: $e")
            false
        }
    }

    private fun postStatus(msg: String) {
        mainHandler.post { onStatusUpdate(msg) }
    }

    fun isConnected(): Boolean {
        return connectedSocket?.isConnected == true
    }

    fun stop() {
        isRunning = false
        isServerListening = false
        try {
            writer?.close()
            connectedSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w("BluetoothRadio", "Close error: $e")
        }
        connectedSocket = null
        serverSocket = null
        writer = null
    }
}
