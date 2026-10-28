package com.scanipcamera.app.data.network

import com.scanipcamera.app.data.scanner.DahuaNetSdkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class PingResult(
    val isReachable: Boolean,
    val latencyMs: Long,
    val message: String
)

data class PingStabilityResult(
    val packetsSent: Int,
    val packetsReceived: Int,
    val lossRatePercent: Int,
    val minLatencyMs: Long,
    val avgLatencyMs: Long,
    val maxLatencyMs: Long,
    val latencies: List<Long>
)

data class SecurityAuditResult(
    val isVulnerable: Boolean,
    val matchedUser: String? = null,
    val matchedPassword: String? = null,
    val message: String
)

data class PortStatus(
    val port: Int,
    val serviceName: String,
    val isOpen: Boolean
)

object NetworkDiagnosticUtils {

    suspend fun testPing(ip: String, timeoutMs: Int = 1500): PingResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            // First try ICMP / system reachable
            val address = InetAddress.getByName(ip)
            val reachable = address.isReachable(timeoutMs)
            val elapsed = System.currentTimeMillis() - startTime
            if (reachable) {
                return@withContext PingResult(true, elapsed, "Phản hồi trong ${elapsed}ms")
            }

            // Fallback: TCP probe to common ports (some routers/cams block ICMP echo)
            val tcpPorts = listOf(80, 554, 8000, 37777, 443)
            for (port in tcpPorts) {
                var socket: Socket? = null
                try {
                    val sStart = System.currentTimeMillis()
                    socket = Socket()
                    socket.connect(InetSocketAddress(ip, port), 600)
                    val sElapsed = System.currentTimeMillis() - sStart
                    return@withContext PingResult(true, sElapsed, "Kết nối TCP port $port trong ${sElapsed}ms")
                } catch (ignored: Exception) {
                } finally {
                    try { socket?.close() } catch (e: Exception) {}
                }
            }

            PingResult(false, -1, "Không có phản hồi (Timeout)")
        } catch (e: Exception) {
            PingResult(false, -1, "Lỗi kết nối: ${e.message}")
        }
    }

    suspend fun testPingStability(
        ip: String,
        count: Int = 4,
        probePort: Int = 80
    ): PingStabilityResult = withContext(Dispatchers.IO) {
        val latencies = mutableListOf<Long>()
        val fallbackPorts = listOf(probePort, 80, 554, 37777, 8000).distinct()

        for (i in 0 until count) {
            val start = System.currentTimeMillis()
            var ok = false
            try {
                if (InetAddress.getByName(ip).isReachable(350)) {
                    latencies.add(System.currentTimeMillis() - start)
                    ok = true
                }
            } catch (_: Exception) {}

            if (!ok) {
                for (p in fallbackPorts) {
                    var s: Socket? = null
                    try {
                        val pStart = System.currentTimeMillis()
                        s = Socket()
                        s.connect(InetSocketAddress(ip, p), 350)
                        latencies.add(System.currentTimeMillis() - pStart)
                        ok = true
                        break
                    } catch (_: Exception) {
                    } finally {
                        try { s?.close() } catch (_: Exception) {}
                    }
                }
            }
            if (i < count - 1) {
                delay(80)
            }
        }

        val received = latencies.size
        val lossPercent = ((count - received) * 100) / count
        val min = if (received > 0) latencies.minOrNull() ?: 0L else 0L
        val max = if (received > 0) latencies.maxOrNull() ?: 0L else 0L
        val avg = if (received > 0) latencies.average().toLong() else 0L

        PingStabilityResult(
            packetsSent = count,
            packetsReceived = received,
            lossRatePercent = lossPercent,
            minLatencyMs = min,
            avgLatencyMs = avg,
            maxLatencyMs = max,
            latencies = latencies
        )
    }

    suspend fun auditDefaultCredentials(
        ip: String,
        sdkPort: Int = 37777,
        isDahua: Boolean = true
    ): SecurityAuditResult = withContext(Dispatchers.IO) {
        val candidates = listOf(
            "admin" to "admin",
            "admin" to "admin123",
            "admin" to "123456",
            "admin" to "888888",
            "admin" to "admin12345",
            "admin" to ""
        )

        for ((user, pwd) in candidates) {
            if (isDahua) {
                val handle = DahuaNetSdkManager.login(ip, if (sdkPort > 0) sdkPort else 37777, user, pwd)
                if (handle != 0L) {
                    DahuaNetSdkManager.logout(handle)
                    return@withContext SecurityAuditResult(
                        isVulnerable = true,
                        matchedUser = user,
                        matchedPassword = pwd.ifEmpty { "(trống)" },
                        message = "Phát hiện mật khẩu mặc định nguy hiểm: '$user' / '${pwd.ifEmpty { "(trống)" }}'!"
                    )
                }
            }
        }

        SecurityAuditResult(
            isVulnerable = false,
            message = "An toàn: Không phát hiện sử dụng mật khẩu mặc định thông dụng."
        )
    }

    suspend fun checkCommonCameraPorts(ip: String, customPorts: List<Int>? = null): List<PortStatus> = withContext(Dispatchers.IO) {
        val portList = customPorts ?: listOf(80, 554, 8000, 37777, 8899, 443, 8080)
        portList.map { port ->
            val isOpen = isPortOpen(ip, port, timeoutMs = 400)
            val name = when (port) {
                80 -> "HTTP Web"
                443 -> "HTTPS Web"
                554 -> "RTSP Video Stream"
                8000 -> "Hikvision SDK / Control"
                37777 -> "Dahua TCP Service"
                8899 -> "Xiongmai ONVIF / Media"
                34567 -> "Xiongmai Net DVR/NVR"
                5000 -> "Uniview Media"
                1935 -> "RTMP Stream"
                8080 -> "Alternate HTTP"
                else -> "Port $port"
            }
            PortStatus(port, name, isOpen)
        }
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            true
        } catch (e: Exception) {
            false
        } finally {
            try { socket?.close() } catch (ignored: Exception) {}
        }
    }
}
