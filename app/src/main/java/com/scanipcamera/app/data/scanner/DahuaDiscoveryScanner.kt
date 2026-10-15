package com.scanipcamera.app.data.scanner

import android.util.Log
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.FileReader
import java.net.*
import java.util.UUID

class DahuaDiscoveryScanner {

    companion object {
        private const val TAG = "DahuaDiscoveryScanner"
        const val DAHUA_PORT = 37810
        const val DAHUA_MULTICAST_GROUP = "239.255.255.251"
        private const val BUFFER_SIZE = 8192
    }

    suspend fun scan(
        timeoutMs: Int = 3500,
        broadcastAddresses: List<InetAddress>,
        onDeviceFound: (CameraDevice) -> Unit
    ) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            // Try to bind to port 37810 with SO_REUSEADDR so we receive Dahua broadcast responses
            socket = try {
                MulticastSocket(DAHUA_PORT).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 400
                    try {
                        joinGroup(InetAddress.getByName(DAHUA_MULTICAST_GROUP))
                    } catch (ignored: Exception) {}
                }
            } catch (e: Exception) {
                DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 400
                }
            }

            val localPort = socket.localPort

            // 1. Standard Dahua client.search with uniPort
            val probeJson1 = JSONObject().apply {
                put("method", "client.search")
                put("params", JSONObject().apply {
                    put("mac", "")
                    put("uniPort", localPort)
                })
            }.toString()

            // 2. Fallback plain client.search without uniPort
            val probeJson2 = "{\"method\":\"client.search\",\"params\":{\"mac\":\"\"}}"

            val packetsToSend = listOf(
                probeJson1.toByteArray(Charsets.UTF_8),
                probeJson2.toByteArray(Charsets.UTF_8)
            )

            // Send to Multicast Group 239.255.255.251
            try {
                val group = InetAddress.getByName(DAHUA_MULTICAST_GROUP)
                for (bytes in packetsToSend) {
                    socket.send(DatagramPacket(bytes, bytes.size, group, DAHUA_PORT))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Dahua multicast send error: ${e.message}")
            }

            // Send to Local Broadcast Addresses
            for (broadcastAddr in broadcastAddresses) {
                for (bytes in packetsToSend) {
                    try {
                        socket.send(DatagramPacket(bytes, bytes.size, broadcastAddr, DAHUA_PORT))
                    } catch (e: Exception) {
                        Log.v(TAG, "Dahua broadcast send error to ${broadcastAddr.hostAddress}: ${e.message}")
                    }
                }
            }

            // Listen for responses
            val startTime = System.currentTimeMillis()
            val receiveBuffer = ByteArray(BUFFER_SIZE)

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
                try {
                    socket.receive(packet)
                    val responseStr = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val devices = parseDahuaResponses(responseStr, packet.address.hostAddress ?: "")
                    for (dev in devices) {
                        onDeviceFound(dev)
                    }
                } catch (e: SocketTimeoutException) {
                    // Periodic read timeout, continue listening
                } catch (e: Exception) {
                    Log.v(TAG, "Dahua receive error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Dahua discovery scan failed", e)
        } finally {
            socket?.close()
        }
    }

    private fun parseDahuaResponses(response: String, fallbackIp: String): List<CameraDevice> {
        val resultList = mutableListOf<CameraDevice>()
        try {
            val jsonStartIndex = response.indexOf('{')
            if (jsonStartIndex == -1) return emptyList()
            val jsonStr = response.substring(jsonStartIndex)

            val root = JSONObject(jsonStr)
            val params = root.optJSONObject("params") ?: return emptyList()

            // Some firmwares return a "device" object, others an array "devices", others flat inside params
            val deviceObjects = mutableListOf<JSONObject>()
            when {
                params.has("device") -> params.optJSONObject("device")?.let { deviceObjects.add(it) }
                params.has("devices") -> {
                    val arr = params.optJSONArray("devices")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            arr.optJSONObject(i)?.let { deviceObjects.add(it) }
                        }
                    }
                }
                else -> deviceObjects.add(params)
            }

            for (deviceObj in deviceObjects) {
                val ipv4Obj = deviceObj.optJSONObject("IPv4Address")
                val hostIp = ipv4Obj?.optString("HostIP")?.takeIf { it.isNotBlank() } ?: fallbackIp
                val gateway = ipv4Obj?.optString("GateWay") ?: ""
                val subnetMask = ipv4Obj?.optString("SubnetMask") ?: "255.255.255.0"

                var mac = (deviceObj.optString("MAC").takeIf { it.isNotBlank() }
                    ?: deviceObj.optString("mac")
                    ?: deviceObj.optString("MacAddress")).uppercase().replace("-", ":")

                // If MAC missing, resolve from ARP table
                if (mac.isBlank()) {
                    mac = getMacFromArp(hostIp)
                }

                val serialNo = deviceObj.optString("SerialNo").takeIf { it.isNotBlank() }
                    ?: deviceObj.optString("sn").takeIf { it.isNotBlank() }
                    ?: deviceObj.optString("SN").takeIf { it.isNotBlank() }
                    ?: deviceObj.optString("serialNumber").takeIf { it.isNotBlank() }
                    ?: deviceObj.optString("deviceSN")

                val machineName = deviceObj.optString("MachineName").takeIf { it.isNotBlank() }
                    ?: deviceObj.optString("deviceType").takeIf { it.isNotBlank() }
                    ?: deviceObj.optString("DeviceType").takeIf { it.isNotBlank() }
                    ?: deviceObj.optString("model").takeIf { it.isNotBlank() }
                    ?: "Dahua Device"

                val tcpPort = deviceObj.optInt("Port", 37777)
                val httpPort = deviceObj.optInt("HttpPort", 80)
                val version = deviceObj.optString("Version", "")

                val initStatus = deviceObj.optInt("InitStatus", 2)
                val isActivated = (initStatus == 2)
                val isDhcp = deviceObj.optBoolean("DHCP", false)

                val brand = DeviceBrand.fromModelOrVendor(machineName + " " + serialNo)
                val (category, channels) = CameraDevice.detectCategoryAndChannels(machineName, serialNo)

                val device = CameraDevice(
                    id = if (mac.isNotBlank()) mac else (if (serialNo.isNotBlank()) serialNo else hostIp),
                    brand = if (brand == DeviceBrand.ONVIF) DeviceBrand.DAHUA else brand,
                    category = category,
                    channelCount = channels,
                    model = machineName,
                    serialNumber = serialNo,
                    macAddress = mac,
                    ipv4Address = hostIp,
                    ipv4SubnetMask = subnetMask,
                    ipv4Gateway = gateway,
                    isDhcpEnabled = isDhcp,
                    isActivated = isActivated,
                    httpPort = httpPort,
                    sdkPort = tcpPort,
                    firmwareVersion = version,
                    rawProtocol = "DHIP"
                )
                resultList.add(device)
            }
        } catch (e: Exception) {
            Log.v(TAG, "Error parsing Dahua JSON: ${e.message}")
        }
        return resultList
    }

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
        } catch (ignored: Exception) {}
        return ""
    }
}
