package com.isro.itantra

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognizerIntent
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.isro.itantra.audio.OfflineTTS
import com.isro.itantra.audio.VoiceRecorder
import com.isro.itantra.compression.TokenCompressor
import com.isro.itantra.p2p.BluetoothRadioManager
import com.isro.itantra.p2p.RadioPacket
import com.isro.itantra.p2p.SocketService
import com.isro.itantra.p2p.UdpAutoDiscovery
import com.isro.itantra.ui.MessageAdapter
import java.net.NetworkInterface
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var tvChannelCode: TextView
    private lateinit var etTargetCode: EditText
    private lateinit var btnConnectCode: Button
    private lateinit var btnAutoFoundPeer: Button
    private lateinit var tvP2pStatus: TextView
    private lateinit var statusIndicator: View
    private lateinit var btnBluetoothScan: Button
    private lateinit var btnSos: Button
    private lateinit var spLanguage: Spinner
    private lateinit var tvCompressionStats: TextView
    private lateinit var rvMessages: RecyclerView
    private lateinit var tvHexTokenBar: TextView
    private lateinit var btnPtt: Button
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton

    // Presets
    private lateinit var btnPresetHindi: Button
    private lateinit var btnPresetTamil: Button
    private lateinit var btnPresetMarathi: Button
    private lateinit var btnPresetEnglish: Button

    private lateinit var messageAdapter: MessageAdapter
    private lateinit var offlineTTS: OfflineTTS
    private lateinit var voiceRecorder: VoiceRecorder
    private lateinit var socketService: SocketService
    private lateinit var bluetoothManager: BluetoothRadioManager
    private lateinit var udpDiscovery: UdpAutoDiscovery

    private var myNodeCode = "NODE-" + UUID.randomUUID().toString().substring(0, 4).uppercase()
    private var myChannelCode = "001"
    private var autoFoundIp: String? = null

    private val discoveredBtDevices = mutableListOf<BluetoothDevice>()
    private var btDevicesAdapter: ArrayAdapter<String>? = null
    private var btDialog: AlertDialog? = null

    private var selectedLanguage = "hi"
    private val PERMISSION_REQUEST_CODE = 101

    // Speech Recognizer Result Launcher
    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val spokenText = matches[0]
                etMessage.setText(spokenText)
                sendRadioMessage(spokenText, isSos = false)
                etMessage.text.clear()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        computeChannelCode()
        initViews()
        initServices()
        checkAndRequestPermissions()

        // Auto-start Radio Servers
        socketService.startServer()
        bluetoothManager.startServer()
        udpDiscovery.start()
    }

    private fun computeChannelCode() {
        val ip = getLocalIpAddress()
        if (ip != null && ip.contains(".")) {
            val lastOctet = ip.substringAfterLast(".").toIntOrNull() ?: 1
            myChannelCode = "%03d".format(lastOctet)
        } else {
            myChannelCode = "%03d".format((1..254).random())
        }
    }

    private fun initViews() {
        tvChannelCode = findViewById(R.id.tvChannelCode)
        etTargetCode = findViewById(R.id.etTargetCode)
        btnConnectCode = findViewById(R.id.btnConnectCode)
        btnAutoFoundPeer = findViewById(R.id.btnAutoFoundPeer)
        tvP2pStatus = findViewById(R.id.tvP2pStatus)
        statusIndicator = findViewById(R.id.statusIndicator)
        btnBluetoothScan = findViewById(R.id.btnBluetoothScan)
        btnSos = findViewById(R.id.btnSos)
        spLanguage = findViewById(R.id.spLanguage)
        tvCompressionStats = findViewById(R.id.tvCompressionStats)
        rvMessages = findViewById(R.id.rvMessages)
        tvHexTokenBar = findViewById(R.id.tvHexTokenBar)
        btnPtt = findViewById(R.id.btnPtt)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        btnPresetHindi = findViewById(R.id.btnPresetHindi)
        btnPresetTamil = findViewById(R.id.btnPresetTamil)
        btnPresetMarathi = findViewById(R.id.btnPresetMarathi)
        btnPresetEnglish = findViewById(R.id.btnPresetEnglish)

        val myIp = getLocalIpAddress() ?: "127.0.0.1"
        tvChannelCode.text = "#$myChannelCode ($myIp)"

        // Setup RecyclerView
        messageAdapter = MessageAdapter { packet ->
            offlineTTS.speak(packet.text, packet.language)
        }
        rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvMessages.adapter = messageAdapter

        // Setup Language Spinner
        val languages = arrayOf("Hindi (हिंदी)", "Tamil (தமிழ்)", "Marathi (मराठी)", "Telugu (తెలుగు)", "Bengali (বাংলা)", "English")
        val langCodes = arrayOf("hi", "ta", "mr", "te", "bn", "en")

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages)
        spLanguage.adapter = spinnerAdapter
        spLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedLanguage = langCodes[position]
                offlineTTS.setLanguage(selectedLanguage)
                updateHexPreview(etMessage.text.toString())
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 3-Digit Channel Connect Button
        btnConnectCode.setOnClickListener {
            val codeStr = etTargetCode.text.toString().trim()
            if (codeStr.isNotEmpty()) {
                connectBy3DigitCode(codeStr)
            } else {
                Toast.makeText(this, "Enter target 3-digit channel code (e.g. $myChannelCode)", Toast.LENGTH_SHORT).show()
            }
        }

        // Auto-Found Peer Tap to Link Button
        btnAutoFoundPeer.setOnClickListener {
            val targetIp = autoFoundIp
            if (targetIp != null) {
                socketService.connectToHost(targetIp)
                btnAutoFoundPeer.visibility = View.GONE
            }
        }

        // Send Text Message
        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendRadioMessage(text, isSos = false)
                etMessage.text.clear()
            }
        }

        // Mic Push-To-Talk
        btnPtt.setOnClickListener {
            val speechIntent = voiceRecorder.getSpeechIntent(selectedLanguage)
            try {
                speechLauncher.launch(speechIntent)
            } catch (e: Exception) {
                voiceRecorder.startListening(selectedLanguage)
            }
        }

        // Emergency SOS
        btnSos.setOnClickListener {
            triggerEmergencySos()
        }

        // Bluetooth Scan
        btnBluetoothScan.setOnClickListener {
            showBluetoothScanDialog()
        }

        // Presets
        btnPresetHindi.setOnClickListener {
            selectedLanguage = "hi"
            spLanguage.setSelection(0)
            sendRadioMessage("चमोली में बादल फटा है, तुरंत मेडिकल टीम भेजो।", isSos = false)
        }
        btnPresetTamil.setOnClickListener {
            selectedLanguage = "ta"
            spLanguage.setSelection(1)
            sendRadioMessage("கப்பல் புயலில் சிக்கியுள்ளது, மீட்புப் படகை அனுப்புங்கள்.", isSos = false)
        }
        btnPresetMarathi.setOnClickListener {
            selectedLanguage = "mr"
            spLanguage.setSelection(2)
            sendRadioMessage("घाटमाथ्यावर दरड कोसळली आहे, रस्ता बंद आहे.", isSos = false)
        }
        btnPresetEnglish.setOnClickListener {
            selectedLanguage = "en"
            spLanguage.setSelection(5)
            sendRadioMessage("Base Station 4, oxygen supply critical, initiate evacuation plan.", isSos = false)
        }
    }

    private fun initServices() {
        // 1. Offline TTS Engine
        offlineTTS = OfflineTTS(this)

        // 2. Voice Recorder
        voiceRecorder = VoiceRecorder(
            context = this,
            onResult = { recognizedText ->
                etMessage.setText(recognizedText)
                sendRadioMessage(recognizedText, isSos = false)
                etMessage.text.clear()
            },
            onError = { errMsg ->
                Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show()
            }
        )

        // 3. Socket P2P Service
        socketService = SocketService(
            onPacketReceived = { packet ->
                handleIncomingPacket(packet)
            },
            onStatusChanged = { statusMsg ->
                updateStatusText(statusMsg)
            }
        )

        // 4. Bluetooth Radio Manager
        bluetoothManager = BluetoothRadioManager(
            context = this,
            onPacketReceived = { packet ->
                handleIncomingPacket(packet)
            },
            onStatusUpdate = { statusMsg ->
                updateStatusText(statusMsg)
            },
            onDeviceFound = { device ->
                addDiscoveredBluetoothDevice(device)
            }
        )

        // 5. UDP Auto-Discovery (Zero-Touch Peer Discovery on Wi-Fi / Hotspot)
        udpDiscovery = UdpAutoDiscovery(
            context = this,
            myNodeCode = myNodeCode,
            myChannelCode = myChannelCode,
            onNodeDiscovered = { senderIp, channelCode, nodeName ->
                if (!socketService.isConnected() && !bluetoothManager.isConnected()) {
                    autoFoundIp = senderIp
                    btnAutoFoundPeer.text = "📡 Found Radio Node #$channelCode ($nodeName) — Tap to Link"
                    btnAutoFoundPeer.visibility = View.VISIBLE
                }
            }
        )
    }

    private fun connectBy3DigitCode(codeStr: String) {
        val targetIp = if (codeStr.contains(".")) {
            codeStr
        } else {
            val targetNumber = codeStr.toIntOrNull() ?: 1
            val myIp = getLocalIpAddress()

            if (myIp != null && myIp.contains(".")) {
                val subnet = myIp.substringBeforeLast(".")
                "$subnet.$targetNumber"
            } else {
                "192.168.43.$targetNumber"
            }
        }

        Toast.makeText(this, "Linking to Radio Channel at $targetIp…", Toast.LENGTH_SHORT).show()
        socketService.connectToHost(targetIp)
    }

    private fun handleIncomingPacket(packet: RadioPacket) {
        messageAdapter.addMessage(packet)
        rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
        updateHexDisplay(packet.tokensHex)

        if (packet.isSos) {
            triggerVibration()
        }

        // Auto-speak incoming voice packet
        offlineTTS.speak(packet.text, packet.language)
    }

    private fun updateStatusText(statusMsg: String) {
        tvP2pStatus.text = statusMsg
        val isConnected = statusMsg.contains("Active") || statusMsg.contains("Established")
        statusIndicator.setBackgroundColor(
            if (isConnected) ContextCompat.getColor(this, R.color.tactical_green)
            else ContextCompat.getColor(this, R.color.tactical_amber)
        )
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr.hostAddress?.indexOf(':') == -1) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (ignored: Exception) {}
        return null
    }

    private fun sendRadioMessage(text: String, isSos: Boolean) {
        val tokenBytes = TokenCompressor.encodeToTokens(text, selectedLanguage, isSos)
        val hexString = TokenCompressor.bytesToHex(tokenBytes)
        val ratio = TokenCompressor.calculateCompressionRatio(text)

        tvCompressionStats.text = "18B • ${ratio}× savings"
        updateHexDisplay(hexString)

        val packet = RadioPacket(
            text = text,
            language = selectedLanguage,
            isSos = isSos,
            senderId = myNodeCode,
            tokensHex = hexString,
            isSentByMe = true
        )

        messageAdapter.addMessage(packet)
        rvMessages.scrollToPosition(messageAdapter.itemCount - 1)

        // Transmit over Bluetooth if connected, otherwise over Wi-Fi socket
        if (bluetoothManager.isConnected()) {
            bluetoothManager.sendPacket(packet)
        } else {
            socketService.sendPacket(packet)
        }
    }

    private fun triggerEmergencySos() {
        triggerVibration()
        val sosText = "🚨 EMERGENCY SOS: Level 0 Alert from Channel #$myChannelCode [GPS: 30.42°N, 79.33°E]"
        sendRadioMessage(sosText, isSos = true)
        offlineTTS.speak("Emergency SOS broadcast transmitted", "en")
    }

    private fun triggerVibration() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator?.vibrate(500)
        }
    }

    private fun updateHexPreview(text: String) {
        if (text.isEmpty()) return
        val tokens = TokenCompressor.encodeToTokens(text, selectedLanguage, false)
        updateHexDisplay(TokenCompressor.bytesToHex(tokens))
    }

    private fun updateHexDisplay(hex: String) {
        val formatted = hex.chunked(2).joinToString(" ") { "0x$it" }
        tvHexTokenBar.text = "[$formatted]"
    }

    // ─── BLUETOOTH DIALOG & SCANNING ───
    @SuppressLint("MissingPermission")
    private fun showBluetoothScanDialog() {
        discoveredBtDevices.clear()
        val paired = bluetoothManager.getPairedDevices()
        discoveredBtDevices.addAll(paired)

        val displayList = discoveredBtDevices.map { dev ->
            val isBonded = paired.contains(dev)
            val name = dev.name ?: "Unknown Device"
            if (isBonded) "🔗 [Paired] $name (${dev.address})" else "📡 [Discovered] $name (${dev.address})"
        }.toMutableList()

        btDevicesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList)

        val builder = AlertDialog.Builder(this)
            .setTitle("🔵 Bluetooth Radio Scan")
            .setAdapter(btDevicesAdapter) { _, which ->
                if (which < discoveredBtDevices.size) {
                    val targetDevice = discoveredBtDevices[which]
                    bluetoothManager.connectToDevice(targetDevice)
                }
            }
            .setNeutralButton("Rescan") { _, _ ->
                bluetoothManager.startDiscovery()
                showBluetoothScanDialog()
            }
            .setNegativeButton("Cancel", null)

        btDialog = builder.show()
        bluetoothManager.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun addDiscoveredBluetoothDevice(device: BluetoothDevice) {
        if (!discoveredBtDevices.any { it.address == device.address }) {
            discoveredBtDevices.add(device)
            val name = device.name ?: "Unknown Signal"
            btDevicesAdapter?.add("📡 [Discovered] $name (${device.address})")
            btDevicesAdapter?.notifyDataSetChanged()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onResume() {
        super.onResume()
        bluetoothManager.registerReceiver()
        computeChannelCode()
        val myIp = getLocalIpAddress() ?: "127.0.0.1"
        tvChannelCode.text = "#$myChannelCode ($myIp)"
    }

    override fun onPause() {
        super.onPause()
        bluetoothManager.unregisterReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        offlineTTS.shutdown()
        voiceRecorder.destroy()
        socketService.stop()
        bluetoothManager.stop()
        udpDiscovery.stop()
    }
}
