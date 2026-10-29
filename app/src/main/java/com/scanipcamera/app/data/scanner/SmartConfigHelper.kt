package com.scanipcamera.app.data.scanner

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object SmartConfigHelper {

    private const val TAG = "SmartConfigHelper"
    private var job: Job? = null

    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting = _isBroadcasting.asStateFlow()

    private val _countdownSec = MutableStateFlow(60)
    val countdownSec = _countdownSec.asStateFlow()

    fun startSmartConfig(
        ssid: String,
        password: String,
        scope: CoroutineScope,
        onComplete: () -> Unit
    ) {
        stopSmartConfig()
        _isBroadcasting.value = true
        _countdownSec.value = 60

        job = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 100
                }

                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val targetPort = 35932 // Standard SmartConfig broadcast port

                // Encode SSID and password lengths and payload into varying packet lengths
                val ssidBytes = ssid.toByteArray(Charsets.UTF_8)
                val passBytes = password.toByteArray(Charsets.UTF_8)

                var remaining = 60
                while (isActive && remaining > 0) {
                    _countdownSec.value = remaining

                    // 1. Send sync lead sequence
                    for (len in listOf(1, 2, 3, 4, 5)) {
                        val buf = ByteArray(len) { 0x55.toByte() }
                        socket.send(DatagramPacket(buf, buf.size, broadcastAddr, targetPort))
                        delay(5)
                    }

                    // 2. Send SSID bytes
                    for (b in ssidBytes) {
                        val len = (b.toInt() and 0xFF) + 40
                        val buf = ByteArray(len) { 0xAA.toByte() }
                        socket.send(DatagramPacket(buf, buf.size, broadcastAddr, targetPort))
                        delay(6)
                    }

                    // 3. Send Password bytes
                    for (b in passBytes) {
                        val len = (b.toInt() and 0xFF) + 40
                        val buf = ByteArray(len) { 0xCC.toByte() }
                        socket.send(DatagramPacket(buf, buf.size, broadcastAddr, targetPort))
                        delay(6)
                    }

                    delay(250)
                    remaining--
                }
            } catch (e: Exception) {
                Log.w(TAG, "SmartConfig error: ${e.message}")
            } finally {
                socket?.close()
                _isBroadcasting.value = false
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    fun stopSmartConfig() {
        job?.cancel()
        job = null
        _isBroadcasting.value = false
        _countdownSec.value = 0
    }
}
