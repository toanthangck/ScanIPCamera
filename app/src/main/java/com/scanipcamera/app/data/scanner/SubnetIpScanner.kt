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
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

class SubnetIpScanner {

    companion object {
        private const val TAG = "SubnetIpScanner"
        private val CCTV_PORTS = intArrayOf(80, 554, 8000, 37777, 8899, 5000, 8080)
        private const val CONNECT_TIMEOUT_MS = 250
        private const val MAX_CONCURRENCY = 30

        // Common vendor MAC OUI prefixes (upper 3 bytes)
        private val DAHUA_OUIS = setOf(
            "3C:EF:8C", "A0:BD:1D", "E0:50:8B", "4C:11:BF", "14:A7:8B",
            "BC:32:5F", "90:02:A9", "54:C4:15", "B8:F8:83", "70:B1:3D",
            "68:9C:E2", "40:2C:76", "E4:24:9C", "88:29:9C", "A8:9C:ED",
            "6C:1C:71", "54:89:98", "F4:39:09", "64:DB:8B", "2C:26:17"
        )
        private val HIKVISION_OUIS = setOf(
            "44:19:B6", "C4:2F:90", "BC:54:36", "28:57:BE", "18:68:CB",
            "68:6D:BC", "70:AF:6A", "8C:E7:48", "A4:14:37", "C0:56:E3",
            "08:EA:44", "10:D0:7A", "24:18:C6", "38:1C:1A", "48:EA:63",
            "54:27:58", "60:21:C0", "74:03:BD", "80:61:5F", "84:9A:40",
            "94:E1:AC", "A0:41:5E", "AC:64:17", "C8:02:8F", "D4:6E:5C",
            "E4:1F:13", "EC:69:FE", "F8:4D:FC"
        )
        private val EZVIZ_OUIS = setOf(
            "B0:D5:9D", "38:D2:69", "E4:AA:EC", "44:03:77", "D8:B0:4C"
        )
        private val IMOU_OUIS = setOf(
            "D4:40:F0", "74:AC:B9", "5C:E2:8C", "3C:A6:2F"
        )
        private val UNIVIEW_OUIS = setOf(
            "00:0F:7C", "2C:6B:7D"
        )
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
                            // Deep probe to actively extract MAC, Serial, and precise Model directly over unicast
                            val probed = DeviceDeepProbe.probeDevice(targetIp, openPorts)
                            if (probed != null && (probed.brand != DeviceBrand.UNKNOWN || probed.macAddress.isNotBlank())) {
                                onDeviceFound(probed)
                            } else {
                                val mac = getMacFromArp(targetIp)
                                val (brand, modelName) = detectBrandAndModel(targetIp, openPorts, mac)
                                val category = when {
                                    openPorts.contains(37777) || (openPorts.contains(8000) && brand == DeviceBrand.HIKVISION) -> DeviceCategory.NVR
                                    openPorts.contains(554) || brand != DeviceBrand.UNKNOWN -> DeviceCategory.CAMERA
                                    else -> DeviceCategory.OTHER
                                }

                                val device = CameraDevice(
                                    id = if (mac.isNotBlank()) mac else targetIp,
                                    brand = brand,
                                    category = category,
                                    model = modelName,
                                    macAddress = mac,
                                    ipv4Address = targetIp,
                                    httpPort = if (openPorts.contains(80)) 80 else if (openPorts.contains(8080)) 8080 else openPorts.first(),
                                    sdkPort = if (openPorts.contains(37777)) 37777 else if (openPorts.contains(8000) && brand == DeviceBrand.HIKVISION) 8000 else 0,
                                    rtspPort = if (openPorts.contains(554)) 554 else 0,
                                    rawProtocol = "PING/PORT"
                                )
                                onDeviceFound(device)
                            }
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

    /**
     * Accurately detect brand and model:
     * 1. Check MAC OUI against vendor lists
     * 2. Active HTTP probe for Server header, realm, and page title
     * 3. Proprietary ports (37777 for Dahua, 8899 for Xiongmai)
     * 4. NEVER falsely classify generic port 80/8000 as Hikvision!
     */
    private fun detectBrandAndModel(ip: String, ports: List<Int>, mac: String): Pair<DeviceBrand, String> {
        // 1. MAC OUI matching
        if (mac.length >= 8) {
            val oui = mac.take(8).uppercase()
            when {
                DAHUA_OUIS.contains(oui) -> return Pair(DeviceBrand.DAHUA, "Dahua Device")
                HIKVISION_OUIS.contains(oui) -> return Pair(DeviceBrand.HIKVISION, "Hikvision Device")
                EZVIZ_OUIS.contains(oui) -> return Pair(DeviceBrand.EZVIZ, "EZVIZ Device")
                IMOU_OUIS.contains(oui) -> return Pair(DeviceBrand.IMOU, "IMOU Device")
                UNIVIEW_OUIS.contains(oui) -> return Pair(DeviceBrand.UNIVIEW, "Uniview Device")
            }
        }

        // 2. Active HTTP banner probe on web ports
        val webPorts = ports.filter { it in setOf(80, 8080, 8000, 8899) }
        for (port in webPorts) {
            val bannerMatch = checkHttpBanner(ip, port)
            if (bannerMatch != null) {
                return bannerMatch
            }
        }

        // 3. Proprietary port matching
        return when {
            ports.contains(37777) -> Pair(DeviceBrand.DAHUA, "Dahua Device")
            ports.contains(8899) -> Pair(DeviceBrand.XIONGMAI, "Xiongmai Device")
            ports.contains(5000) -> Pair(DeviceBrand.UNIVIEW, "Uniview Device")
            ports.contains(554) -> Pair(DeviceBrand.ONVIF, "Camera ONVIF (Port 554)")
            else -> {
                val portStr = ports.joinToString("/")
                Pair(DeviceBrand.UNKNOWN, "Thiết bị mạng (Port $portStr)")
            }
        }
    }

    private fun checkHttpBanner(ip: String, port: Int): Pair<DeviceBrand, String>? {
        try {
            val url = URL("http://$ip:$port/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 300
                readTimeout = 300
                requestMethod = "GET"
                instanceFollowRedirects = false
            }
            conn.connect()
            val server = conn.getHeaderField("Server") ?: ""
            val auth = conn.getHeaderField("WWW-Authenticate") ?: ""
            var title = ""
            try {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val titleMatch = Regex("<title>([^<]+)</title>", RegexOption.IGNORE_CASE).find(body)
                title = titleMatch?.groups?.get(1)?.value?.trim() ?: ""
            } catch (ignored: Exception) {}

            val banner = "$server $auth $title".lowercase()
            return when {
                banner.contains("hikvision") || banner.contains("dvr-webs") || banner.contains("hikvision-webs") ->
                    Pair(DeviceBrand.HIKVISION, if (title.isNotBlank()) title else "Hikvision Device")
                banner.contains("dahua") || banner.contains("app-webs") || banner.contains("dhip") ->
                    Pair(DeviceBrand.DAHUA, if (title.isNotBlank()) title else "Dahua Device")
                banner.contains("ezviz") ->
                    Pair(DeviceBrand.EZVIZ, if (title.isNotBlank()) title else "EZVIZ Device")
                banner.contains("imou") ->
                    Pair(DeviceBrand.IMOU, if (title.isNotBlank()) title else "IMOU Device")
                banner.contains("kbvision") ->
                    Pair(DeviceBrand.KBVISION, if (title.isNotBlank()) title else "KBVISION Device")
                banner.contains("uniview") || banner.contains("unv") ->
                    Pair(DeviceBrand.UNIVIEW, if (title.isNotBlank()) title else "Uniview Device")
                banner.contains("xiongmai") || banner.contains("netsurveillance") || banner.contains("uc-httpd") ->
                    Pair(DeviceBrand.XIONGMAI, if (title.isNotBlank()) title else "Xiongmai Device")
                else -> null
            }
        } catch (ignored: Exception) {
            return null
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
