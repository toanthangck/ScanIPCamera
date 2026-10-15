package com.scanipcamera.app.data.scanner

import android.util.Log
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.*
import java.util.UUID

class OnvifWsDiscoveryScanner {

    companion object {
        private const val TAG = "OnvifWsDiscoveryScanner"
        const val ONVIF_PORT = 3702
        const val ONVIF_MULTICAST_GROUP = "239.255.255.250"
        private const val BUFFER_SIZE = 8192
    }

    suspend fun scan(
        timeoutMs: Int = 3500,
        broadcastAddresses: List<InetAddress>,
        onDeviceFound: (CameraDevice) -> Unit
    ) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 400
            }

            val uuid = UUID.randomUUID().toString()
            val wsProbeXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Envelope xmlns:tds="http://www.onvif.org/ver10/device/wsdl" 
                          xmlns="http://www.w3.org/2003/05/soap-envelope" 
                          xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing">
                    <Header>
                        <wsa:MessageID>uuid:$uuid</wsa:MessageID>
                        <wsa:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</wsa:To>
                        <wsa:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</wsa:Action>
                    </Header>
                    <Body>
                        <Probe xmlns="http://schemas.xmlsoap.org/ws/2005/04/discovery">
                            <Types>tds:Device</Types>
                        </Probe>
                    </Body>
                </Envelope>
            """.trimIndent()

            val probeBytes = wsProbeXml.toByteArray(Charsets.UTF_8)

            // Send to multicast
            try {
                val group = InetAddress.getByName(ONVIF_MULTICAST_GROUP)
                socket.send(DatagramPacket(probeBytes, probeBytes.size, group, ONVIF_PORT))
            } catch (e: Exception) {
                Log.w(TAG, "Failed sending ONVIF multicast: ${e.message}")
            }

            // Send to broadcast
            val targets = (broadcastAddresses + InetAddress.getByName("255.255.255.255")).distinct()
            for (broadcastAddr in targets) {
                try {
                    socket.send(DatagramPacket(probeBytes, probeBytes.size, broadcastAddr, ONVIF_PORT))
                } catch (e: Exception) {
                    Log.v(TAG, "Failed sending ONVIF broadcast to ${broadcastAddr.hostAddress}: ${e.message}")
                }
            }

            val startTime = System.currentTimeMillis()
            val receiveBuffer = ByteArray(BUFFER_SIZE)

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
                try {
                    socket.receive(packet)
                    val responseStr = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val device = parseWsDiscoveryResponse(responseStr, packet.address.hostAddress ?: "")
                    if (device != null) {
                        onDeviceFound(device)
                    }
                } catch (e: SocketTimeoutException) {
                    // Loop until timeout
                } catch (e: Exception) {
                    Log.v(TAG, "Error receiving ONVIF packet: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ONVIF scan failed", e)
        } finally {
            socket?.close()
        }
    }

    private fun parseWsDiscoveryResponse(xml: String, fallbackIp: String): CameraDevice? {
        if (!xml.contains("ProbeMatches") && !xml.contains("XAddrs")) return null

        try {
            // 1. Extract XAddrs (e.g. http://192.168.1.64:80/onvif/device_service)
            val xaddrsRegex = "<[^>]*XAddrs[^>]*>([^<]+)</[^>]*XAddrs>".toRegex()
            val xaddrsMatch = xaddrsRegex.find(xml)
            val xaddrUrl = xaddrsMatch?.groups?.get(1)?.value?.trim()?.split(" ")?.firstOrNull()

            var ip = fallbackIp
            var port = 80

            if (xaddrUrl != null) {
                try {
                    val url = URL(xaddrUrl)
                    ip = url.host
                    port = if (url.port != -1) url.port else 80
                } catch (e: Exception) {
                    Log.v(TAG, "Failed parsing XAddr URL: $xaddrUrl")
                }
            }

            // 2. Extract Scopes string
            val scopesRegex = "<[^>]*Scopes[^>]*>([^<]+)</[^>]*Scopes>".toRegex()
            val scopesMatch = scopesRegex.find(xml)
            val scopesStr = scopesMatch?.groups?.get(1)?.value ?: ""

            var modelName = "ONVIF Camera"
            var serial = ""
            var mac = ""

            val scopesList = scopesStr.split(" ")
            for (scope in scopesList) {
                when {
                    scope.contains("hardware/") -> {
                        modelName = scope.substringAfter("hardware/").replace("_", " ")
                    }
                    scope.contains("name/") && modelName == "ONVIF Camera" -> {
                        modelName = scope.substringAfter("name/").replace("_", " ")
                    }
                    scope.contains("serial/") || scope.contains("sn/") -> {
                        serial = scope.substringAfter("serial/").substringAfter("sn/")
                    }
                    scope.contains("mac/") -> {
                        mac = scope.substringAfter("mac/").uppercase().replace("-", ":")
                    }
                }
            }

            // 3. Fallback MAC from Regex matching within Scopes
            if (mac.isBlank()) {
                val macRegex = "([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})".toRegex()
                val macMatch = macRegex.find(scopesStr)
                if (macMatch != null) {
                    mac = macMatch.value.uppercase().replace("-", ":")
                }
            }

            // 4. Fallback MAC from UUID (last 12 hex chars often match MAC address)
            if (mac.isBlank()) {
                val uuidRegex = "urn:uuid:[0-9a-fA-F-]+?([0-9a-fA-F]{12})".toRegex()
                val uuidMatch = uuidRegex.find(xml)
                if (uuidMatch != null) {
                    val rawHex = uuidMatch.groups[1]?.value ?: ""
                    if (rawHex.length == 12 && !rawHex.all { it == '0' }) {
                        mac = rawHex.chunked(2).joinToString(":").uppercase()
                    }
                }
            }

            // 5. Fallback MAC from ARP Table
            if (mac.isBlank() && ip.isNotBlank()) {
                mac = getMacFromArp(ip)
            }

            // 6. Fallback Serial from XML tags or scope regex
            if (serial.isBlank()) {
                val snRegex = "(?:serial|sn|Serial|DeviceSN)[^>]*>([A-Za-z0-9_-]+)<".toRegex()
                val snMatch = snRegex.find(xml)
                serial = snMatch?.groups?.get(1)?.value ?: ""
            }

            // 7. If Serial is still blank, perform a fast direct ONVIF GetDeviceInformation HTTP probe
            if (serial.isBlank() && ip.isNotBlank()) {
                val fetchedInfo = fetchOnvifDeviceInfo(ip, port)
                if (fetchedInfo != null) {
                    if (fetchedInfo.first.isNotBlank()) serial = fetchedInfo.first
                    if (fetchedInfo.second.isNotBlank() && (modelName == "ONVIF Camera" || modelName.isBlank())) {
                        modelName = fetchedInfo.second
                    }
                }
            }

            val brand = DeviceBrand.fromModelOrVendor(modelName + " " + scopesStr + " " + serial)
            val (category, channels) = CameraDevice.detectCategoryAndChannels(modelName, scopesStr)

            return CameraDevice(
                id = if (mac.isNotBlank()) mac else (if (serial.isNotBlank()) serial else "$ip:$port"),
                brand = brand,
                category = category,
                channelCount = channels,
                model = modelName,
                serialNumber = serial,
                macAddress = mac,
                ipv4Address = ip,
                httpPort = port,
                rawProtocol = "ONVIF"
            )
        } catch (e: Exception) {
            Log.v(TAG, "Error parsing ONVIF XML: ${e.message}")
            return null
        }
    }

    /**
     * Query ONVIF GetDeviceInformation via HTTP SOAP to extract Serial Number and Model.
     */
    private fun fetchOnvifDeviceInfo(ip: String, port: Int): Pair<String, String>? {
        return try {
            val url = URL("http://$ip:$port/onvif/device_service")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 800
                readTimeout = 800
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8")
            }

            val soapReq = """
                <?xml version="1.0" encoding="utf-8"?>
                <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:tds="http://www.onvif.org/ver10/device/wsdl">
                    <soap:Body>
                        <tds:GetDeviceInformation/>
                    </soap:Body>
                </soap:Envelope>
            """.trimIndent()

            OutputStreamWriter(conn.outputStream).use { it.write(soapReq) }

            if (conn.responseCode == 200) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val snRegex = "<[^>]*SerialNumber[^>]*>([^<]+)</[^>]*SerialNumber>".toRegex()
                val modelRegex = "<[^>]*Model[^>]*>([^<]+)</[^>]*Model>".toRegex()
                val sn = snRegex.find(resp)?.groups?.get(1)?.value?.trim() ?: ""
                val model = modelRegex.find(resp)?.groups?.get(1)?.value?.trim() ?: ""
                Pair(sn, model)
            } else {
                null
            }
        } catch (ignored: Exception) {
            null
        }
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
