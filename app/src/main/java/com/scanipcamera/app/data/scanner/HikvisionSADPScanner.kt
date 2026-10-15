package com.scanipcamera.app.data.scanner

import android.util.Log
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.UUID

class HikvisionSADPScanner {

    companion object {
        private const val TAG = "HikvisionSADPScanner"
        const val SADP_PORT = 37020
        const val SADP_MULTICAST_GROUP = "239.255.255.250"
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
                soTimeout = 400 // Short read timeout per cycle
            }

            // SADP Probe XML message
            val uuid = UUID.randomUUID().toString()
            val probeXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                    "<Probe>" +
                    "<Uuid>$uuid</Uuid>" +
                    "<Types>inquiry</Types>" +
                    "</Probe>"
            val probeBytes = probeXml.toByteArray(Charsets.UTF_8)

            // 1. Send to SADP Multicast group
            try {
                val multicastGroup = InetAddress.getByName(SADP_MULTICAST_GROUP)
                val multicastPacket = DatagramPacket(probeBytes, probeBytes.size, multicastGroup, SADP_PORT)
                socket.send(multicastPacket)
            } catch (e: Exception) {
                Log.w(TAG, "Failed sending to multicast group: ${e.message}")
            }

            // 2. Also send to all local broadcast addresses
            val targets = (broadcastAddresses + InetAddress.getByName("255.255.255.255")).distinct()
            for (broadcastAddr in targets) {
                try {
                    val broadcastPacket = DatagramPacket(probeBytes, probeBytes.size, broadcastAddr, SADP_PORT)
                    socket.send(broadcastPacket)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed sending to broadcast ${broadcastAddr.hostAddress}: ${e.message}")
                }
            }

            // Listen for responses until total timeout expires
            val startTime = System.currentTimeMillis()
            val receiveBuffer = ByteArray(BUFFER_SIZE)

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
                try {
                    socket.receive(packet)
                    val responseStr = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val device = parseSadpResponse(responseStr, packet.address.hostAddress ?: "")
                    if (device != null) {
                        onDeviceFound(device)
                    }
                } catch (e: SocketTimeoutException) {
                    // Timeout hit for this receive call, loop continues until overall timeout
                } catch (e: Exception) {
                    Log.e(TAG, "Error receiving SADP packet: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SADP Scan failed", e)
        } finally {
            socket?.close()
        }
    }

    private fun parseSadpResponse(xml: String, fallbackIp: String): CameraDevice? {
        if (!xml.contains("<ProbeMatch") && !xml.contains("<Types>inquiry</Types>")) {
            return null
        }

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentTag = ""

            var deviceDescription = ""
            var deviceType = ""
            var deviceSn = ""
            var ipv4Address = ""
            var ipv4SubnetMask = "255.255.255.0"
            var ipv4Gateway = ""
            var ipv4Port = 8000
            var httpPort = 80
            var mac = ""
            var softwareVersion = ""
            var isActivated = true
            var isDhcp = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> currentTag = parser.name
                    XmlPullParser.TEXT -> {
                        val text = parser.text.trim()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "DeviceDescription" -> deviceDescription = text
                                "DeviceType" -> deviceType = text
                                "DeviceSN" -> deviceSn = text
                                "IPv4Address" -> ipv4Address = text
                                "IPv4SubnetMask" -> ipv4SubnetMask = text
                                "IPv4Gateway" -> ipv4Gateway = text
                                "IPv4Port" -> ipv4Port = text.toIntOrNull() ?: 8000
                                "HttpPort" -> httpPort = text.toIntOrNull() ?: 80
                                "MAC" -> mac = text.uppercase().replace("-", ":")
                                "SoftwareVersion" -> softwareVersion = text
                                "Activated" -> isActivated = text.equals("true", ignoreCase = true)
                                "DHCP" -> isDhcp = text.equals("true", ignoreCase = true)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            val finalIp = if (ipv4Address.isNotBlank()) ipv4Address else fallbackIp
            val modelName = when {
                deviceDescription.isNotBlank() -> deviceDescription
                deviceType.isNotBlank() -> deviceType
                else -> "Hikvision/EZVIZ Device"
            }

            val brand = DeviceBrand.fromModelOrVendor(modelName + " " + deviceSn)
            val (category, channels) = CameraDevice.detectCategoryAndChannels(modelName, deviceDescription)

            return CameraDevice(
                id = if (mac.isNotBlank()) mac else UUID.randomUUID().toString(),
                brand = brand,
                category = category,
                channelCount = channels,
                model = modelName,
                serialNumber = deviceSn,
                macAddress = mac,
                ipv4Address = finalIp,
                ipv4SubnetMask = ipv4SubnetMask,
                ipv4Gateway = ipv4Gateway,
                isDhcpEnabled = isDhcp,
                isActivated = isActivated,
                httpPort = httpPort,
                sdkPort = ipv4Port,
                firmwareVersion = softwareVersion,
                rawProtocol = "SADP"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SADP response XML", e)
            return null
        }
    }
}
