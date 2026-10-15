package com.scanipcamera.app.data.scanner

import android.util.Log
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID

class OnvifWsDiscoveryScanner {

    companion object {
        private const val TAG = "OnvifWsDiscoveryScanner"
        const val ONVIF_PORT = 3702
        const val ONVIF_MULTICAST_GROUP = "239.255.255.250"
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
                val multicastPacket = DatagramPacket(probeBytes, probeBytes.size, group, ONVIF_PORT)
                socket.send(multicastPacket)
            } catch (e: Exception) {
                Log.w(TAG, "Failed sending ONVIF multicast: ${e.message}")
            }

            // Send to broadcast
            for (broadcastAddr in broadcastAddresses) {
                try {
                    val broadcastPacket = DatagramPacket(probeBytes, probeBytes.size, broadcastAddr, ONVIF_PORT)
                    socket.send(broadcastPacket)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed sending ONVIF broadcast to ${broadcastAddr.hostAddress}: ${e.message}")
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
                    // Loop until overall timeout
                } catch (e: Exception) {
                    Log.e(TAG, "Error receiving ONVIF packet: ${e.message}")
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
            // Extract XAddrs (e.g. http://192.168.1.64:80/onvif/device_service)
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
                    Log.w(TAG, "Failed parsing XAddr URL: $xaddrUrl")
                }
            }

            // Extract Scopes for Hardware/Name/Location
            val scopesRegex = "<[^>]*Scopes[^>]*>([^<]+)</[^>]*Scopes>".toRegex()
            val scopesMatch = scopesRegex.find(xml)
            val scopesStr = scopesMatch?.groups?.get(1)?.value ?: ""

            var modelName = "ONVIF Camera"
            val scopesList = scopesStr.split(" ")
            for (scope in scopesList) {
                if (scope.contains("hardware/")) {
                    modelName = scope.substringAfter("hardware/").replace("_", " ")
                } else if (scope.contains("name/") && modelName == "ONVIF Camera") {
                    modelName = scope.substringAfter("name/").replace("_", " ")
                }
            }

            val brand = DeviceBrand.fromModelOrVendor(modelName + " " + scopesStr)
            val (category, channels) = CameraDevice.detectCategoryAndChannels(modelName, scopesStr)

            // Extract MAC address from scopes or UUID if present
            var mac = ""
            val macRegex = "([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})".toRegex()
            val macMatch = macRegex.find(scopesStr)
            if (macMatch != null) {
                mac = macMatch.value.uppercase().replace("-", ":")
            }

            return CameraDevice(
                id = if (mac.isNotBlank()) mac else "$ip:$port",
                brand = brand,
                category = category,
                channelCount = channels,
                model = modelName,
                macAddress = mac,
                ipv4Address = ip,
                httpPort = port,
                rawProtocol = "ONVIF"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ONVIF XML", e)
            return null
        }
    }
}
