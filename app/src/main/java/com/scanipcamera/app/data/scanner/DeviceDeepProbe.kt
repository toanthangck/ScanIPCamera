package com.scanipcamera.app.data.scanner

import android.util.Log
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.StringReader
import java.net.*
import java.util.UUID

object DeviceDeepProbe {

    private const val TAG = "DeviceDeepProbe"
    private const val TIMEOUT_MS = 450

    /**
     * Actively probes an IP using unicast UDP (Dahua, SADP) and HTTP/ONVIF to reliably extract
     * MAC address and Serial Number even when multicast/broadcast or ARP is blocked by Android/router.
     */
    suspend fun probeDevice(
        ip: String,
        knownPorts: List<Int> = emptyList()
    ): CameraDevice? = withContext(Dispatchers.IO) {
        if (ip.isBlank()) return@withContext null

        // 1. Try Dahua Unicast UDP (Port 37810) - Fastest & most accurate for Dahua/KBVISION/Imou/XVR
        val dahuaDevice = probeDahuaUdp(ip)
        if (dahuaDevice != null && dahuaDevice.macAddress.isNotBlank() && dahuaDevice.serialNumber.isNotBlank()) {
            return@withContext dahuaDevice
        }

        // 2. Try Hikvision SADP Unicast UDP (Port 37020)
        val hikDevice = probeHikvisionUdp(ip)
        if (hikDevice != null && hikDevice.macAddress.isNotBlank() && hikDevice.serialNumber.isNotBlank()) {
            return@withContext hikDevice
        }

        // 3. Try Dahua HTTP APIs (Port 80 or 8080)
        val dahuaHttpDevice = probeDahuaHttp(ip)
        if (dahuaHttpDevice != null && (dahuaHttpDevice.macAddress.isNotBlank() || dahuaHttpDevice.serialNumber.isNotBlank())) {
            // Merge with any UDP fields found
            return@withContext mergeDevices(dahuaDevice, dahuaHttpDevice)
        }

        // 4. Try Hikvision ISAPI (Port 80 or 8080)
        val hikHttpDevice = probeHikvisionHttp(ip)
        if (hikHttpDevice != null && (hikHttpDevice.macAddress.isNotBlank() || hikHttpDevice.serialNumber.isNotBlank())) {
            return@withContext mergeDevices(hikDevice, hikHttpDevice)
        }

        // 5. Try ONVIF SOAP GetDeviceInformation on open ports (80, 8080, 8899, 5000)
        val testPorts = (knownPorts + listOf(80, 8080, 8899, 5000)).distinct()
        for (port in testPorts) {
            val onvifDevice = probeOnvifSoap(ip, port)
            if (onvifDevice != null && (onvifDevice.macAddress.isNotBlank() || onvifDevice.serialNumber.isNotBlank())) {
                return@withContext onvifDevice
            }
        }

        // Return best effort device if any partial data was obtained
        dahuaDevice ?: hikDevice
    }

    /**
     * Unicast UDP probe to Dahua port 37810.
     */
    private fun probeDahuaUdp(ip: String): CameraDevice? {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                soTimeout = TIMEOUT_MS
            }
            val target = InetAddress.getByName(ip)

            // Send DHIP Binary + DHDiscover.search packet
            val dhipQuery = "{\"method\":\"DHDiscover.search\",\"params\":{\"mac\":\"\",\"uni\":1}}"
            val binaryPacket = DahuaDiscoveryScanner.buildDhipPacket(dhipQuery)
            socket.send(DatagramPacket(binaryPacket, binaryPacket.size, target, DahuaDiscoveryScanner.DAHUA_PORT))

            // Also send plain JSON query
            val plainBytes = dhipQuery.toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(plainBytes, plainBytes.size, target, DahuaDiscoveryScanner.DAHUA_PORT))

            val buf = ByteArray(8192)
            val packet = DatagramPacket(buf, buf.size)
            socket.receive(packet)

            val resp = String(packet.data, 0, packet.length, Charsets.UTF_8)
            val scanner = DahuaDiscoveryScanner()
            return scanner.parseDahuaPacket(resp, ip)
        } catch (ignored: Exception) {
            return null
        } finally {
            try { socket?.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Unicast UDP probe to Hikvision SADP port 37020.
     */
    private fun probeHikvisionUdp(ip: String): CameraDevice? {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                soTimeout = TIMEOUT_MS
            }
            val target = InetAddress.getByName(ip)

            val uuid = UUID.randomUUID().toString()
            val probeXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?><Probe><Uuid>$uuid</Uuid><Types>inquiry</Types></Probe>"
            val bytes = probeXml.toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(bytes, bytes.size, target, HikvisionSADPScanner.SADP_PORT))

            val buf = ByteArray(4096)
            val packet = DatagramPacket(buf, buf.size)
            socket.receive(packet)

            val resp = String(packet.data, 0, packet.length, Charsets.UTF_8)
            return parseSadpXml(resp, ip)
        } catch (ignored: Exception) {
            return null
        } finally {
            try { socket?.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Query Dahua HTTP magicBox & netApp endpoints.
     */
    private fun probeDahuaHttp(ip: String): CameraDevice? {
        val ports = listOf(80, 8080)
        for (port in ports) {
            try {
                // 1. Get System Info (Model & Serial)
                val sysInfoUrl = URL("http://$ip:$port/cgi-bin/magicBox.cgi?action=getSystemInfo")
                val conn = (sysInfoUrl.openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "GET"
                }

                var serial = ""
                var model = ""
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    for (line in body.lines()) {
                        if (line.contains("serialNumber=")) {
                            serial = line.substringAfter("serialNumber=").trim()
                        }
                        if (line.contains("deviceType=")) {
                            model = line.substringAfter("deviceType=").trim()
                        }
                    }
                }

                // 2. Get Net Interface (MAC)
                val netUrl = URL("http://$ip:$port/cgi-bin/netApp.cgi?action=getNetInterface")
                val netConn = (netUrl.openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "GET"
                }

                var mac = ""
                if (netConn.responseCode == 200) {
                    val body = netConn.inputStream.bufferedReader().use { it.readText() }
                    val macRegex = "([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})".toRegex()
                    mac = macRegex.find(body)?.value?.uppercase()?.replace("-", ":") ?: ""
                }

                if (serial.isNotBlank() || mac.isNotBlank() || model.isNotBlank()) {
                    val brand = DeviceBrand.fromModelOrVendor("$model $serial")
                    val (cat, ch) = CameraDevice.detectCategoryAndChannels(model, serial)
                    return CameraDevice(
                        id = if (mac.isNotBlank()) mac else (if (serial.isNotBlank()) serial else ip),
                        brand = if (brand == DeviceBrand.UNKNOWN) DeviceBrand.DAHUA else brand,
                        category = cat,
                        channelCount = ch,
                        model = if (model.isNotBlank()) model else "Dahua Device",
                        serialNumber = serial,
                        macAddress = mac,
                        ipv4Address = ip,
                        httpPort = port,
                        sdkPort = 37777,
                        rawProtocol = "DHIP/HTTP"
                    )
                }
            } catch (ignored: Exception) {}
        }
        return null
    }

    /**
     * Query Hikvision ISAPI deviceInfo endpoint.
     */
    private fun probeHikvisionHttp(ip: String): CameraDevice? {
        val ports = listOf(80, 8080)
        for (port in ports) {
            try {
                val url = URL("http://$ip:$port/ISAPI/System/deviceInfo")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "GET"
                }

                if (conn.responseCode == 200) {
                    val xml = conn.inputStream.bufferedReader().use { it.readText() }
                    val snRegex = "<[^>]*serialNumber[^>]*>([^<]+)</[^>]*serialNumber>".toRegex()
                    val macRegex = "<[^>]*macAddress[^>]*>([^<]+)</[^>]*macAddress>".toRegex()
                    val modelRegex = "<[^>]*model[^>]*>([^<]+)</[^>]*model>".toRegex()

                    val serial = snRegex.find(xml)?.groups?.get(1)?.value?.trim() ?: ""
                    val mac = (macRegex.find(xml)?.groups?.get(1)?.value?.trim() ?: "").uppercase().replace("-", ":")
                    val model = modelRegex.find(xml)?.groups?.get(1)?.value?.trim() ?: "Hikvision Device"

                    if (serial.isNotBlank() || mac.isNotBlank()) {
                        val (cat, ch) = CameraDevice.detectCategoryAndChannels(model, serial)
                        return CameraDevice(
                            id = if (mac.isNotBlank()) mac else (if (serial.isNotBlank()) serial else ip),
                            brand = DeviceBrand.HIKVISION,
                            category = cat,
                            channelCount = ch,
                            model = model,
                            serialNumber = serial,
                            macAddress = mac,
                            ipv4Address = ip,
                            httpPort = port,
                            sdkPort = 8000,
                            rawProtocol = "ISAPI"
                        )
                    }
                }
            } catch (ignored: Exception) {}
        }
        return null
    }

    /**
     * Query ONVIF SOAP GetDeviceInformation.
     */
    private fun probeOnvifSoap(ip: String, port: Int): CameraDevice? {
        try {
            val url = URL("http://$ip:$port/onvif/device_service")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
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
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val snRegex = "<[^>]*SerialNumber[^>]*>([^<]+)</[^>]*SerialNumber>".toRegex()
                val modelRegex = "<[^>]*Model[^>]*>([^<]+)</[^>]*Model>".toRegex()
                val hwIdRegex = "<[^>]*HardwareId[^>]*>([^<]+)</[^>]*HardwareId>".toRegex()

                val serial = snRegex.find(resp)?.groups?.get(1)?.value?.trim() ?: ""
                val model = modelRegex.find(resp)?.groups?.get(1)?.value?.trim() ?: "ONVIF Device"
                val hwId = hwIdRegex.find(resp)?.groups?.get(1)?.value?.trim() ?: ""

                var mac = ""
                val macRegex = "([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})".toRegex()
                val macMatch = macRegex.find(hwId) ?: macRegex.find(resp)
                if (macMatch != null) {
                    mac = macMatch.value.uppercase().replace("-", ":")
                }

                if (serial.isNotBlank() || mac.isNotBlank()) {
                    val brand = DeviceBrand.fromModelOrVendor("$model $serial")
                    val (cat, ch) = CameraDevice.detectCategoryAndChannels(model, serial)
                    return CameraDevice(
                        id = if (mac.isNotBlank()) mac else (if (serial.isNotBlank()) serial else ip),
                        brand = brand,
                        category = cat,
                        channelCount = ch,
                        model = model,
                        serialNumber = serial,
                        macAddress = mac,
                        ipv4Address = ip,
                        httpPort = port,
                        sdkPort = if (brand == DeviceBrand.DAHUA) 37777 else 8000,
                        rawProtocol = "ONVIF/SOAP"
                    )
                }
            }
        } catch (ignored: Exception) {}
        return null
    }

    private fun parseSadpXml(xml: String, fallbackIp: String): CameraDevice? {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentTag = ""
            var deviceDescription = ""
            var deviceSn = ""
            var ipv4Address = fallbackIp
            var ipv4SubnetMask = "255.255.255.0"
            var ipv4Gateway = ""
            var ipv4Port = 8000
            var httpPort = 80
            var mac = ""
            var softwareVersion = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> currentTag = parser.name
                    XmlPullParser.TEXT -> {
                        val text = parser.text.trim()
                        if (text.isNotEmpty()) {
                            when (currentTag.lowercase()) {
                                "devicedescription" -> deviceDescription = text
                                "devicesn" -> deviceSn = text
                                "ipv4address" -> ipv4Address = text
                                "ipv4subnetmask" -> ipv4SubnetMask = text
                                "ipv4gateway" -> ipv4Gateway = text
                                "ipv4port" -> ipv4Port = text.toIntOrNull() ?: 8000
                                "httpport" -> httpPort = text.toIntOrNull() ?: 80
                                "mac" -> mac = text.uppercase().replace("-", ":")
                                "softwareversion" -> softwareVersion = text
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            if (deviceSn.isBlank() && mac.isBlank()) return null

            val brand = DeviceBrand.fromModelOrVendor("$deviceDescription $deviceSn")
            val (category, channels) = CameraDevice.detectCategoryAndChannels(deviceDescription, deviceSn)

            return CameraDevice(
                id = if (mac.isNotBlank()) mac else (if (deviceSn.isNotBlank()) deviceSn else ipv4Address),
                brand = if (brand == DeviceBrand.UNKNOWN) DeviceBrand.HIKVISION else brand,
                category = category,
                channelCount = channels,
                model = if (deviceDescription.isNotBlank()) deviceDescription else "Hikvision Device",
                serialNumber = deviceSn,
                macAddress = mac,
                ipv4Address = ipv4Address,
                ipv4SubnetMask = ipv4SubnetMask,
                ipv4Gateway = ipv4Gateway,
                httpPort = httpPort,
                sdkPort = ipv4Port,
                firmwareVersion = softwareVersion,
                rawProtocol = "SADP"
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun mergeDevices(primary: CameraDevice?, secondary: CameraDevice?): CameraDevice? {
        if (primary == null) return secondary
        if (secondary == null) return primary

        val mac = if (primary.macAddress.isNotBlank()) primary.macAddress else secondary.macAddress
        val serial = if (primary.serialNumber.isNotBlank()) primary.serialNumber else secondary.serialNumber
        val model = if (primary.model != "Dahua Device" && primary.model.isNotBlank()) primary.model else secondary.model

        return primary.copy(
            id = if (mac.isNotBlank()) mac else (if (serial.isNotBlank()) serial else primary.ipv4Address),
            macAddress = mac,
            serialNumber = serial,
            model = model
        )
    }
}
