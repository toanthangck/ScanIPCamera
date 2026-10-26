package com.scanipcamera.app.data.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections

object PortScannerUtils {

    val COMMON_CCTV_PORTS = intArrayOf(
        80, 8080, 8089, 81, 88, 8000, 8081, 8088, 8888, 82, 83, 85, 89,
        8008, 8082, 8085, 8090, 8686, 9000, 554, 8554, 37777, 34567, 8899, 443, 8443
    )

    fun isHttpPort(ip: String, port: Int, timeoutMs: Int = 300): Boolean {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            socket.soTimeout = timeoutMs
            val out = socket.getOutputStream()
            val req = "GET / HTTP/1.1\r\nHost: $ip\r\nUser-Agent: Mozilla/5.0\r\nConnection: close\r\n\r\n"
            out.write(req.toByteArray(Charsets.US_ASCII))
            out.flush()

            val buf = ByteArray(512)
            val read = socket.getInputStream().read(buf)
            if (read > 0) {
                val resp = String(buf, 0, read, Charsets.US_ASCII)
                return resp.startsWith("HTTP/") ||
                        resp.contains("<html", ignoreCase = true) ||
                        resp.contains("<title", ignoreCase = true) ||
                        resp.contains("Server:", ignoreCase = true)
            }
        } catch (_: Exception) {
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
        return false
    }

    suspend fun scanPortsRange(
        ip: String,
        minPort: Int = 80,
        maxPort: Int = 9000,
        timeoutMs: Int = 150,
        concurrency: Int = 80
    ): List<Int> = withContext(Dispatchers.IO) {
        val open = Collections.synchronizedList(mutableListOf<Int>())
        val semaphore = Semaphore(concurrency)

        // 1. Quét ưu tiên các cổng CCTV thông dụng trước (Phát hiện ngay trong ~50ms)
        val prioritySet = COMMON_CCTV_PORTS.filter { it in minPort..maxPort }.toSet()
        val priorityJobs = prioritySet.map { port ->
            launch {
                semaphore.withPermit {
                    var s: Socket? = null
                    try {
                        s = Socket()
                        s.connect(InetSocketAddress(ip, port), timeoutMs)
                        open.add(port)
                    } catch (_: Exception) {
                    } finally {
                        try { s?.close() } catch (_: Exception) {}
                    }
                }
            }
        }
        priorityJobs.joinAll()

        // 2. Quét toàn bộ dải cổng còn lại từ 80 đến 9000
        val remaining = (minPort..maxPort).filter { it !in prioritySet }
        val remainingJobs = remaining.map { port ->
            launch {
                semaphore.withPermit {
                    var s: Socket? = null
                    try {
                        s = Socket()
                        s.connect(InetSocketAddress(ip, port), timeoutMs)
                        open.add(port)
                    } catch (_: Exception) {
                    } finally {
                        try { s?.close() } catch (_: Exception) {}
                    }
                }
            }
        }
        remainingJobs.joinAll()

        open.distinct().sorted()
    }
}
