package com.scanipcamera.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class PingResult(
    val isReachable: Boolean,
    val latencyMs: Long,
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
