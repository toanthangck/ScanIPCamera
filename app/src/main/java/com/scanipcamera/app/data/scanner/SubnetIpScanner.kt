package com.scanipcamera.app.data.scanner

import android.util.Log
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceBrand
import com.scanipcamera.app.data.model.DeviceCategory
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

class SubnetIpScanner {

    companion object {
        private const val TAG = "SubnetIpScanner"
        private val CCTV_PORTS = intArrayOf(80, 554, 8000, 37777, 8899, 5000, 8080)
        private const val CONNECT_TIMEOUT_MS = 250
        private const val MAX_CONCURRENCY = 30
    }

    suspend fun scanSubnet(
        localIp: String,
        onProgress: (scanned: Int, total: Int) -> Unit,
        onDeviceFound: (CameraDevice) -> Unit
    ) = withContext(Dispatchers.IO) {
        val parts = localIp.trim().split(".")
        if (parts.size != 4) return@withContext

        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
        val totalHosts = 254
        var scannedCount = 0
        val semaphore = Semaphore(MAX_CONCURRENCY)

        val jobs = (1..totalHosts).map { host ->
            val targetIp = "$prefix.$host"
            launch {
                semaphore.withPermit {
                    try {
                        val openPorts = checkOpenPorts(targetIp)
                        if (openPorts.isNotEmpty()) {
                            val mac = getMacFromArp(targetIp)
                            val brand = inferBrandFromPorts(openPorts)
                            val modelName = "${brand.displayName} (Port ${openPorts.first()})"
                            val category = if (openPorts.contains(37777) || openPorts.contains(8000)) DeviceCategory.NVR else DeviceCategory.CAMERA
                            val device = CameraDevice(
                                id = if (mac.isNotBlank()) mac else targetIp,
                                brand = brand,
                                category = category,
                                model = modelName,
                                macAddress = mac,
                                ipv4Address = targetIp,
                                httpPort = if (openPorts.contains(80)) 80 else if (openPorts.contains(8080)) 8080 else 80,
                                sdkPort = if (openPorts.contains(8000)) 8000 else if (openPorts.contains(37777)) 37777 else 8000,
                                rtspPort = if (openPorts.contains(554)) 554 else 554,
                                rawProtocol = "PING/PORT"
                            )
                            onDeviceFound(device)
                        }
                    } catch (e: Exception) {
                        Log.v(TAG, "Host check error $targetIp: ${e.message}")
                    } finally {
                        synchronized(this@SubnetIpScanner) {
                            scannedCount++
                            onProgress(scannedCount, totalHosts)
                        }
                    }
                }
            }
        }

        jobs.joinAll()
    }

    private fun checkOpenPorts(ip: String): List<Int> {
        val openPorts = mutableListOf<Int>()
        for (port in CCTV_PORTS) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
                openPorts.add(port)
                // If we found a known camera port, we can fast-track
                if (port == 8000 || port == 37777 || port == 8899 || port == 554) {
                    break
                }
            } catch (e: Exception) {
                // Port closed or host unreachable
            } finally {
                try {
                    socket?.close()
                } catch (ignored: Exception) {}
            }
        }
        return openPorts
    }

    private fun inferBrandFromPorts(ports: List<Int>): DeviceBrand {
        return when {
            ports.contains(8000) -> DeviceBrand.HIKVISION
            ports.contains(37777) -> DeviceBrand.DAHUA
            ports.contains(8899) -> DeviceBrand.XIONGMAI
            ports.contains(5000) -> DeviceBrand.UNIVIEW
            ports.contains(554) -> DeviceBrand.ONVIF
            else -> DeviceBrand.UNKNOWN
        }
    }

    /**
     * Read the Linux kernel ARP table to resolve the MAC address of an IP.
     */
    private fun getMacFromArp(ip: String): String {
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val tokens = line!!.split("\\s+".toRegex())
                    if (tokens.size >= 4 && tokens[0] == ip) {
                        val mac = tokens[3]
                        if (mac != "00:00:00:00:00:00") {
                            return mac.uppercase()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.v(TAG, "Error reading /proc/net/arp: ${e.message}")
        }
        return ""
    }
}
