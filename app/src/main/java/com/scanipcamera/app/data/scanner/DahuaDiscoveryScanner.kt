package com.scanipcamera.app.data.scanner

import android.util.Log
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.UUID

class DahuaDiscoveryScanner {

    companion object {
        private const val TAG = "DahuaDiscoveryScanner"
        const val DAHUA_PORT = 37810
        const val DAHUA_MULTICAST_GROUP = "239.255.255.251"
        private const val BUFFER_SIZE = 4096
    }

    suspend fun scan(
        timeoutMs: Int = 3000,
        broadcastAddresses: List<InetAddress>,
        onDeviceFound: (CameraDevice) -> Unit
    ) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 400
            }

            // Dahua JSON Discovery probe
            val probeJson = JSONObject().apply {
                put("method", "client.search")
                put("params", JSONObject().apply {
                    put("mac", "")
                })
            }.toString()
            val probeBytes = probeJson.toByteArray(Charsets.UTF_8)

            // 1. Send to Dahua multicast group
            try {
                val multicastGroup = InetAddress.getByName(DAHUA_MULTICAST_GROUP)
                val multicastPacket = DatagramPacket(probeBytes, probeBytes.size, multicastGroup, DAHUA_PORT)
                socket.send(multicastPacket)
            } catch (e: Exception) {
                Log.w(TAG, "Failed sending to Dahua multicast: ${e.message}")
            }

            // 2. Send to broadcast addresses
            for (broadcastAddr in broadcastAddresses) {
                try {
                    val broadcastPacket = DatagramPacket(probeBytes, probeBytes.size, broadcastAddr, DAHUA_PORT)
                    socket.send(broadcastPacket)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed sending Dahua probe to ${broadcastAddr.hostAddress}: ${e.message}")
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
                    val device = parseDahuaResponse(responseStr, packet.address.hostAddress ?: "")
                    if (device != null) {
                        onDeviceFound(device)
                    }
                } catch (e: SocketTimeoutException) {
                    // Periodic timeout, continue
                } catch (e: Exception) {
                    Log.e(TAG, "Error receiving Dahua packet: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Dahua Scan failed", e)
        } finally {
            socket?.close()
        }
    }

    private fun parseDahuaResponse(response: String, fallbackIp: String): CameraDevice? {
        try {
            // Find start of JSON if packet contains binary prefix (e.g. DHIP...)
            val jsonStartIndex = response.indexOf('{')
            if (jsonStartIndex == -1) return null
            val jsonStr = response.substring(jsonStartIndex)

            val root = JSONObject(jsonStr)
            val params = root.optJSONObject("params") ?: return null
            val deviceObj = params.optJSONObject("device") ?: params

            val ipv4Obj = deviceObj.optJSONObject("IPv4Address")
            val hostIp = ipv4Obj?.optString("HostIP")?.takeIf { it.isNotBlank() } ?: fallbackIp
            val gateway = ipv4Obj?.optString("GateWay") ?: ""
            val subnetMask = ipv4Obj?.optString("SubnetMask") ?: "255.255.255.0"

            val mac = (deviceObj.optString("MAC").takeIf { it.isNotBlank() }
                ?: deviceObj.optString("mac")).uppercase().replace("-", ":")

            val serialNo = deviceObj.optString("SerialNo").takeIf { it.isNotBlank() }
                ?: deviceObj.optString("sn")

            val machineName = deviceObj.optString("MachineName").takeIf { it.isNotBlank() }
                ?: deviceObj.optString("deviceType").takeIf { it.isNotBlank() }
                ?: "Dahua Device"

            val tcpPort = deviceObj.optInt("Port", 37777)
            val httpPort = deviceObj.optInt("HttpPort", 80)
            val version = deviceObj.optString("Version", "")
            
            // InitStatus: 2 = Initialized/Activated, 1 or 0 = Not Initialized
            val initStatus = deviceObj.optInt("InitStatus", 2)
            val isActivated = (initStatus == 2)
            val isDhcp = deviceObj.optBoolean("DHCP", false)

            val brand = DeviceBrand.fromModelOrVendor(machineName + " " + serialNo)
            val (category, channels) = CameraDevice.detectCategoryAndChannels(machineName, serialNo)

            return CameraDevice(
                id = if (mac.isNotBlank()) mac else UUID.randomUUID().toString(),
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
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Dahua JSON response", e)
            return null
        }
    }
}
