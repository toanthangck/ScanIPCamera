package com.scanipcamera.app.data.scanner

import android.util.Log
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DahuaDiscoveryScanner(private val networkInterface: NetworkInterface? = null) {

    companion object {
        private const val TAG = "DahuaDiscoveryScanner"
        const val DAHUA_PORT = 37810
        const val DAHUA_MULTICAST_GROUP = "239.255.255.251"
        private const val BUFFER_SIZE = 8192

        /**
         * Construct the proprietary 32-byte binary DHIP header prepended to JSON queries.
         */
        fun buildDhipPacket(jsonString: String): ByteArray {
            val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)
            val jsonLen = jsonBytes.size
            val header = ByteBuffer.allocate(32).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                putInt(0x20) // Header length: 32 bytes (0x20)
                order(ByteOrder.BIG_ENDIAN)
                put("DHIP".toByteArray(Charsets.US_ASCII)) // Protocol Magic
                order(ByteOrder.LITTLE_ENDIAN)
                putLong(0L) // Session ID (4 bytes) + Request ID (4 bytes)
                putInt(jsonLen) // Total length
                putInt(0) // Fragment index
                putInt(jsonLen) // Message length
                putInt(0) // Data length
            }.array()

            val packet = ByteArray(header.size + jsonBytes.size)
            System.arraycopy(header, 0, packet, 0, header.size)
            System.arraycopy(jsonBytes, 0, packet, header.size, jsonBytes.size)
            return packet
        }

        /**
         * Recursively searches for the first non-blank value matching any of the given keys in a JSONObject.
         */
        fun findValueInJson(obj: Any?, vararg keys: String): String {
            if (obj == null) return ""
            if (obj is JSONObject) {
                for (key in keys) {
                    if (obj.has(key)) {
                        val v = obj.optString(key)
                        if (v.isNotBlank() && v != "null") return v.trim()
                    }
                }
                val it = obj.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val child = obj.opt(k)
                    val res = findValueInJson(child, *keys)
                    if (res.isNotBlank()) return res
                }
            } else if (obj is JSONArray) {
                for (i in 0 until obj.length()) {
                    val res = findValueInJson(obj.opt(i), *keys)
                    if (res.isNotBlank()) return res
                }
            }
            return ""
        }
    }

    suspend fun scan(
        timeoutMs: Int = 3500,
        broadcastAddresses: List<InetAddress>,
        onDeviceFound: (CameraDevice) -> Unit
    ) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = try {
                MulticastSocket(DAHUA_PORT).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 350
                    if (networkInterface != null) {
                        try {
                            setNetworkInterface(networkInterface)
                            joinGroup(InetSocketAddress(InetAddress.getByName(DAHUA_MULTICAST_GROUP), DAHUA_PORT), networkInterface)
                        } catch (e: Exception) {
                            Log.v(TAG, "Join multicast with interface failed: ${e.message}")
                            try { joinGroup(InetAddress.getByName(DAHUA_MULTICAST_GROUP)) } catch (ignored: Exception) {}
                        }
                    } else {
                        try { joinGroup(InetAddress.getByName(DAHUA_MULTICAST_GROUP)) } catch (ignored: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Port $DAHUA_PORT busy or restricted, using ephemeral socket: ${e.message}")
                DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 350
                }
            }

            val localPort = socket.localPort

            // Payloads:
            // 1. Binary DHIP Header + DHDiscover.search (The official ConfigTool protocol)
            val dhipQuery = "{\"method\":\"DHDiscover.search\",\"params\":{\"mac\":\"\",\"uni\":1}}"
            val dhipBinaryPacket = buildDhipPacket(dhipQuery)

            // 2. Plain JSON DHDiscover.search
            val plainDhipPacket = dhipQuery.toByteArray(Charsets.UTF_8)

            // 3. Plain JSON client.search with ephemeral local port for unicast response
            val clientSearchWithPort = "{\"method\":\"client.search\",\"params\":{\"mac\":\"\",\"uniPort\":$localPort}}"
            val clientSearchWithPortBytes = clientSearchWithPort.toByteArray(Charsets.UTF_8)

            // 4. Plain JSON client.search
            val clientSearchBytes = "{\"method\":\"client.search\",\"params\":{\"mac\":\"\"}}".toByteArray(Charsets.UTF_8)

            val packetsToSend = listOf(
                dhipBinaryPacket,
                plainDhipPacket,
                clientSearchWithPortBytes,
                clientSearchBytes
            )

            // Multicast destination
            try {
                val group = InetAddress.getByName(DAHUA_MULTICAST_GROUP)
                for (bytes in packetsToSend) {
                    socket.send(DatagramPacket(bytes, bytes.size, group, DAHUA_PORT))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Dahua multicast send error: ${e.message}")
            }

            // Broadcast destinations (Global 255.255.255.255 and Subnet Broadcasts)
            val targets = (broadcastAddresses + InetAddress.getByName("255.255.255.255")).distinct()
            for (broadcastAddr in targets) {
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
                    val hostIp = packet.address?.hostAddress ?: ""
                    val device = parseDahuaPacket(responseStr, hostIp)
                    if (device != null) {
                        onDeviceFound(device)
                    }
                } catch (e: SocketTimeoutException) {
                    // Normal timeout per read cycle
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

    /**
     * Parses a Dahua discovery packet (both binary framed and plain JSON) to extract device details.
     */
    fun parseDahuaPacket(response: String, fallbackIp: String): CameraDevice? {
        try {
            val jsonStartIndex = response.indexOf('{')
            if (jsonStartIndex == -1) return null
            val jsonStr = response.substring(jsonStartIndex)
            val root = JSONObject(jsonStr)

            // Recursive extraction guarantees fields are found in any firmware format
            val hostIp = findValueInJson(root, "HostIP", "IPAddress", "ip", "HostIp").ifBlank { fallbackIp }
            if (hostIp.isBlank()) return null

            var mac = findValueInJson(root, "MACAddress", "MacAddress", "MAC", "mac", "macAddress", "Mac")
                .uppercase().replace("-", ":")

            val serialNo = findValueInJson(root, "SerialNo", "serialNo", "SN", "sn", "serialNumber", "deviceSN")
            val machineName = findValueInJson(root, "DeviceType", "deviceType", "MachineName", "machineName", "model", "Model")
                .ifBlank { "Dahua Device" }

            val gateway = findValueInJson(root, "GateWay", "Gateway", "gateway")
            val subnetMask = findValueInJson(root, "SubnetMask", "subnetMask").ifBlank { "255.255.255.0" }

            val httpPortStr = findValueInJson(root, "HttpPort", "httpPort", "webPort")
            val httpPort = httpPortStr.toIntOrNull() ?: 80

            val sdkPortStr = findValueInJson(root, "Port", "TCPPort", "tcpPort")
            val sdkPort = sdkPortStr.toIntOrNull() ?: 37777

            val version = findValueInJson(root, "Version", "version", "SoftwareVersion")
            val dhcpVal = findValueInJson(root, "dhcp", "DHCP")
            val isDhcp = (dhcpVal.equals("true", ignoreCase = true) || dhcpVal == "1")

            val brand = DeviceBrand.fromModelOrVendor("$machineName $serialNo")
            val (category, channels) = CameraDevice.detectCategoryAndChannels(machineName, serialNo)

            return CameraDevice(
                id = if (mac.isNotBlank()) mac else (if (serialNo.isNotBlank()) serialNo else hostIp),
                brand = if (brand == DeviceBrand.ONVIF || brand == DeviceBrand.UNKNOWN) DeviceBrand.DAHUA else brand,
                category = category,
                channelCount = channels,
                model = machineName,
                serialNumber = serialNo,
                macAddress = mac,
                ipv4Address = hostIp,
                ipv4SubnetMask = subnetMask,
                ipv4Gateway = gateway,
                isDhcpEnabled = isDhcp,
                isActivated = true,
                httpPort = httpPort,
                sdkPort = sdkPort,
                firmwareVersion = version,
                rawProtocol = "DHIP"
            )
        } catch (e: Exception) {
            Log.v(TAG, "Error parsing Dahua JSON: ${e.message}")
            return null
        }
    }
}
