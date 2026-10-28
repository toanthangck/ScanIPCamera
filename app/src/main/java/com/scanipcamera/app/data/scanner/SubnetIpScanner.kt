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
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-speed Subnet IP Scanner.
 * Uses a 2-stage pipeline (Instant Alive Check -> Targeted CCTV Port & Deep Probe)
 * to scan 1..254 in ~2-3 seconds instead of 60+ seconds.
 */
class SubnetIpScanner {

    companion object {
        private const val TAG = "SubnetIpScanner"
        private const val SENTINEL_TIMEOUT_MS = 80
        private const val MAX_CONCURRENCY = 64

        // Common vendor MAC OUI prefixes (upper 3 bytes)
        private val DAHUA_OUIS = setOf(
            "38:AF:29", "3C:EF:8C", "A0:BD:1D", "E0:50:8B", "4C:11:BF", "14:A7:8B",
            "BC:32:5F", "90:02:A9", "54:C4:15", "B8:F8:83", "70:B1:3D",
            "68:9C:E2", "40:2C:76", "E4:24:9C", "88:29:9C", "A8:9C:ED",
            "6C:1C:71", "54:89:98", "F4:39:09", "64:DB:8B", "2C:26:17",
            "00:1A:07", "04:CF:8C", "44:32:C8", "9C:14:63"
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
            "38:AF:29", "D4:40:F0", "74:AC:B9", "5C:E2:8C", "3C:A6:2F", "B4:0C:25"
        )
        private val UNIVIEW_OUIS = setOf(
            "00:0F:7C", "2C:6B:7D"
        )

        fun getArpTable(): Map<String, String> {
            val map = mutableMapOf<String, String>()
            try {
                BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val tokens = line!!.split("\\s+".toRegex())
                        if (tokens.size >= 4) {
                            val ip = tokens[0]
                            val mac = tokens[3]
                            if (mac != "00:00:00:00:00:00" && !mac.contains("00:00:00")) {
                                map[ip] = mac.uppercase()
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            return map
        }

        fun getMacFromArp(ip: String): String {
            return getArpTable()[ip] ?: ""
        }
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
        val scannedCounter = AtomicInteger(0)
        val semaphore = Semaphore(MAX_CONCURRENCY)

        // Read ARP table at start for instantaneous MAC cache matching
        val arpMap = getArpTable()

        coroutineScope {
            (1..totalHosts).map { host ->
                val targetIp = "$prefix.$host"
                launch {
                    semaphore.withPermit {
                        try {
                            val inArp = arpMap.containsKey(targetIp)

                            // Stage 1: Ultra-fast sentinel check (only check if host is alive)
                            val isAlive = inArp || checkSentinelPorts(targetIp)
                            if (isAlive) {
                                // Stage 2: Fast parallel check of all CCTV ports
                                val (webPort, openPorts) = PortScannerUtils.detectWebAndOpenPorts(targetIp)

                                if (openPorts.isNotEmpty()) {
                                    val mac = arpMap[targetIp] ?: getMacFromArp(targetIp)
                                    val (brand, modelName, serial) = detectBrandAndModel(targetIp, openPorts, mac)

                                    val hasServerIndicators = openPorts.contains(22) && !openPorts.contains(554) && !openPorts.contains(37777)
                                    val isExplicitWebServer = modelName.contains("Server", ignoreCase = true) || modelName.contains("Máy chủ", ignoreCase = true)

                                    val isGenuineCctv = !hasServerIndicators && !isExplicitWebServer && (
                                        openPorts.contains(554) ||
                                        openPorts.contains(37777) ||
                                        openPorts.contains(34567) ||
                                        openPorts.contains(8899) ||
                                        (brand == DeviceBrand.HIKVISION && openPorts.contains(8000) && HIKVISION_OUIS.any { mac.startsWith(it, ignoreCase = true) }) ||
                                        (brand != DeviceBrand.UNKNOWN && mac.isNotBlank() && (openPorts.contains(80) || openPorts.contains(8000)))
                                    )

                                    if (isGenuineCctv) {
                                        val (category, channels) = CameraDevice.detectCategoryAndChannels(modelName, "$brand $targetIp $serial")
                                        if (category != DeviceCategory.OTHER) {
                                            val detectedRtsp = if (openPorts.contains(554)) 554 else if (openPorts.contains(8554)) 8554 else 0
                                            val detectedSdk = when {
                                                openPorts.contains(37777) -> 37777
                                                openPorts.contains(8000) && brand == DeviceBrand.HIKVISION -> 8000
                                                openPorts.contains(34567) -> 34567
                                                brand == DeviceBrand.DAHUA || brand == DeviceBrand.IMOU || brand == DeviceBrand.KBVISION -> if (openPorts.contains(37777)) 37777 else 0
                                                else -> 0
                                            }

                                            val device = CameraDevice(
                                                id = if (mac.isNotBlank()) mac else (if (serial.isNotBlank()) serial else targetIp),
                                                brand = brand,
                                                category = category,
                                                channelCount = channels,
                                                model = modelName,
                                                serialNumber = serial,
                                                macAddress = mac,
                                                ipv4Address = targetIp,
                                                httpPort = webPort,
                                                sdkPort = detectedSdk,
                                                rtspPort = detectedRtsp,
                                                rawProtocol = "PING/PORT",
                                                verifiedOpenPorts = openPorts
                                            )
                                            onDeviceFound(device)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.v(TAG, "Host error $targetIp: ${e.message}")
                        } finally {
                            val curr = scannedCounter.incrementAndGet()
                            onProgress(curr, totalHosts)
                        }
                    }
                }
            }.joinAll()
        }
    }

    /**
     * Fast sentinel check: probes 80, 554, 37777, 8000 in parallel with a short timeout.
     */
    private suspend fun checkSentinelPorts(ip: String): Boolean = withContext(Dispatchers.IO) {
        val sentinelPorts = intArrayOf(80, 554, 37777, 8000)
        var alive = false
        coroutineScope {
            sentinelPorts.map { port ->
                launch {
                    if (alive) return@launch
                    var s: Socket? = null
                    try {
                        s = Socket()
                        s.connect(InetSocketAddress(ip, port), SENTINEL_TIMEOUT_MS)
                        alive = true
                    } catch (_: Exception) {
                    } finally {
                        try { s?.close() } catch (_: Exception) {}
                    }
                }
            }.joinAll()
        }
        alive
    }

    private fun detectBrandAndModel(ip: String, ports: Set<Int>, mac: String): Triple<DeviceBrand, String, String> {
        // 1. MAC OUI matching
        if (mac.length >= 8) {
            val oui = mac.take(8).uppercase()
            when {
                IMOU_OUIS.contains(oui) -> return Triple(DeviceBrand.IMOU, "Camera IMOU", "")
                DAHUA_OUIS.contains(oui) -> return Triple(DeviceBrand.DAHUA, "Dahua Device", "")
                HIKVISION_OUIS.contains(oui) -> return Triple(DeviceBrand.HIKVISION, "Hikvision Device", "")
                EZVIZ_OUIS.contains(oui) -> return Triple(DeviceBrand.EZVIZ, "EZVIZ Device", "")
                UNIVIEW_OUIS.contains(oui) -> return Triple(DeviceBrand.UNIVIEW, "Uniview Device", "")
            }
        }

        // 2. Active HTTP banner probe on web ports
        val webPorts = ports.filter { it in setOf(80, 8080, 8089, 8000, 8899) }
        for (port in webPorts) {
            val bannerMatch = checkHttpBanner(ip, port, mac)
            if (bannerMatch != null) {
                return bannerMatch
            }
        }

        // 3. Proprietary port matching
        return when {
            ports.contains(37777) -> {
                if (mac.startsWith("38:AF:29", ignoreCase = true) || IMOU_OUIS.any { mac.startsWith(it, ignoreCase = true) }) {
                    Triple(DeviceBrand.IMOU, "Camera IMOU", "")
                } else {
                    Triple(DeviceBrand.DAHUA, "Dahua Device", "")
                }
            }
            ports.contains(34567) -> Triple(DeviceBrand.XIONGMAI, "Xiongmai Net DVR/NVR", "")
            ports.contains(8899) -> Triple(DeviceBrand.XIONGMAI, "Xiongmai Device", "")
            // Port 8000 is ONLY Hikvision if MAC is Hikvision or port 554 (RTSP) is open!
            ports.contains(8000) && (HIKVISION_OUIS.any { mac.startsWith(it, ignoreCase = true) } || ports.contains(554)) ->
                Triple(DeviceBrand.HIKVISION, "Hikvision Device", "")
            ports.contains(554) -> Triple(DeviceBrand.ONVIF, "Camera ONVIF", "")
            else -> Triple(DeviceBrand.UNKNOWN, "Thiết bị mạng", "")
        }
    }

    private fun checkHttpBanner(ip: String, port: Int, mac: String = ""): Triple<DeviceBrand, String, String>? {
        try {
            val url = URL("http://$ip:$port/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 250
                readTimeout = 250
                requestMethod = "GET"
                instanceFollowRedirects = false
            }
            conn.connect()
            val server = (conn.getHeaderField("Server") ?: "").lowercase()
            val auth = conn.getHeaderField("WWW-Authenticate") ?: ""
            var title = ""
            var body = ""
            try {
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                body = stream?.bufferedReader()?.use { it.readText().take(1500) } ?: ""
                val titleMatch = Regex("<title>([^<]+)</title>", RegexOption.IGNORE_CASE).find(body)
                title = titleMatch?.groups?.get(1)?.value?.trim() ?: ""
            } catch (_: Exception) {}

            // Explicit Web Server / Virtual Machine filter (e.g. uvicorn, nginx, apache)
            val isExplicitServer = server.contains("uvicorn") || server.contains("gunicorn") ||
                    server.contains("nginx") || server.contains("apache") || server.contains("caddy") ||
                    server.contains("werkzeug") || server.contains("python") || server.contains("openssh")
            if (isExplicitServer) {
                return Triple(DeviceBrand.UNKNOWN, if (title.isNotBlank()) title else "Máy chủ Web ($server)", "")
            }

            // Extract Dahua/IMOU realm serial from auth header
            var serial = ""
            if (auth.isNotBlank()) {
                val realmMatch = Regex("realm=[\"']Login to ([A-Za-z0-9]+)[\"']", RegexOption.IGNORE_CASE).find(auth)
                if (realmMatch != null) {
                    serial = realmMatch.groupValues[1].trim()
                }
            }

            // Quick Dahua/IMOU magicBox probe if port 80/8080 didn't yield realm
            if (serial.isBlank() && (port == 80 || port == 8080)) {
                try {
                    val mConn = (URL("http://$ip:$port/cgi-bin/magicBox.cgi?action=getSystemInfo").openConnection() as HttpURLConnection).apply {
                        connectTimeout = 200
                        readTimeout = 200
                        requestMethod = "GET"
                    }
                    val mAuth = mConn.getHeaderField("WWW-Authenticate") ?: ""
                    val rMatch = Regex("realm=[\"']Login to ([A-Za-z0-9]+)[\"']", RegexOption.IGNORE_CASE).find(mAuth)
                    if (rMatch != null) {
                        serial = rMatch.groupValues[1].trim()
                    }
                } catch (_: Exception) {}
            }

            val isImou = body.contains("lechange", ignoreCase = true) ||
                    body.contains("imou", ignoreCase = true) ||
                    title.contains("imou", ignoreCase = true) ||
                    serial.contains("PAJA", ignoreCase = true) ||
                    mac.startsWith("38:AF:29", ignoreCase = true) ||
                    Regex("^[4-9][D|H|L|K]", RegexOption.IGNORE_CASE).containsMatchIn(serial)

            if (isImou) {
                val modelName = if (serial.isNotBlank()) "Camera IMOU ($serial)" else "Camera IMOU"
                return Triple(DeviceBrand.IMOU, modelName, serial)
            }

            if (serial.isNotBlank()) {
                return Triple(DeviceBrand.DAHUA, "Dahua Device ($serial)", serial)
            }

            val banner = "$server $auth $title $body".lowercase()
            return when {
                banner.contains("hikvision") || banner.contains("dvr-webs") || banner.contains("hikvision-webs") ->
                    Triple(DeviceBrand.HIKVISION, if (title.isNotBlank()) title else "Hikvision Device", "")
                banner.contains("dahua") || banner.contains("app-webs") || banner.contains("dhip") ->
                    Triple(DeviceBrand.DAHUA, if (title.isNotBlank()) title else "Dahua Device", "")
                banner.contains("ezviz") ->
                    Triple(DeviceBrand.EZVIZ, if (title.isNotBlank()) title else "EZVIZ Device", "")
                banner.contains("kbvision") ->
                    Triple(DeviceBrand.KBVISION, if (title.isNotBlank()) title else "KBVISION Device", "")
                banner.contains("uniview") || banner.contains("unv") ->
                    Triple(DeviceBrand.UNIVIEW, if (title.isNotBlank()) title else "Uniview Device", "")
                banner.contains("xiongmai") || banner.contains("netsurveillance") || banner.contains("uc-httpd") ->
                    Triple(DeviceBrand.XIONGMAI, if (title.isNotBlank()) title else "Xiongmai Device", "")
                else -> null
            }
        } catch (_: Exception) {
            return null
        }
    }
}
