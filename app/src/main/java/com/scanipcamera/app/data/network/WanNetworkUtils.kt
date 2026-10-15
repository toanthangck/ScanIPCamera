package com.scanipcamera.app.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

data class WanInfo(
    val ip: String,
    val isp: String = "",
    val city: String = "",
    val country: String = ""
)

data class WanPortCheckResult(
    val port: Int,
    val serviceName: String,
    val isOpen: Boolean,
    val message: String
)

object WanNetworkUtils {

    private const val TAG = "WanNetworkUtils"

    /**
     * Retrieve the public WAN IP and ISP info using lightweight public APIs.
     */
    suspend fun getPublicWanInfo(): WanInfo? = withContext(Dispatchers.IO) {
        // Try ip-api.com first for IP + ISP details
        try {
            val url = URL("http://ip-api.com/json/?fields=status,message,country,city,isp,query")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val jsonStr = reader.readText()
                reader.close()
                val json = JSONObject(jsonStr)
                if (json.optString("status") == "success") {
                    return@withContext WanInfo(
                        ip = json.optString("query"),
                        isp = json.optString("isp"),
                        city = json.optString("city"),
                        country = json.optString("country")
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "ip-api failed: ${e.message}")
        }

        // Fallback: api.ipify.org
        try {
            val url = URL("https://api.ipify.org?format=json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val jsonStr = reader.readText()
                reader.close()
                val json = JSONObject(jsonStr)
                val ip = json.optString("ip")
                if (ip.isNotBlank()) {
                    return@withContext WanInfo(ip = ip)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "ipify fallback failed: ${e.message}")
        }

        null
    }

    /**
     * Check if a specific port is forwarded/open on the WAN IP from external perspective.
     * Uses public port query endpoints or TCP socket connect (with NAT Loopback support).
     */
    suspend fun checkWanPort(wanIp: String, port: Int): WanPortCheckResult = withContext(Dispatchers.IO) {
        val serviceName = when (port) {
            80 -> "HTTP Web"
            443 -> "HTTPS Web"
            554 -> "RTSP Stream"
            8000 -> "Hikvision SDK (Cổng Server)"
            37777 -> "Dahua TCP (Cổng Đầu Ghi)"
            8899 -> "Xiongmai Media (XM)"
            5000 -> "Uniview Media"
            8080 -> "Alternate Web"
            else -> "Port $port"
        }

        // Strategy 1: Check via public port check API
        try {
            val apiUrl = "https://portchecker.io/api/v1/query?host=$wanIp&port=$port"
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "ScanIPCamera-Mobile")
            }
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val resp = reader.readText()
                reader.close()
                val json = JSONObject(resp)
                val isOpen = json.optBoolean("status") || json.optString("status").equals("open", ignoreCase = true)
                return@withContext WanPortCheckResult(
                    port = port,
                    serviceName = serviceName,
                    isOpen = isOpen,
                    message = if (isOpen) "Cổng MỞ (Đã NAT thành công)" else "Cổng ĐÓNG (Chưa mở hoặc sai IP NAT)"
                )
            }
        } catch (e: Exception) {
            Log.v(TAG, "External API check error: ${e.message}")
        }

        // Strategy 2: Direct TCP Socket connection to WAN IP (works if Router supports NAT Loopback / Hairpinning)
        var socket: Socket? = null
        try {
            val start = System.currentTimeMillis()
            socket = Socket()
            socket.connect(InetSocketAddress(wanIp, port), 2500)
            val elapsed = System.currentTimeMillis() - start
            return@withContext WanPortCheckResult(
                port = port,
                serviceName = serviceName,
                isOpen = true,
                message = "Cổng MỞ (Phản hồi trong ${elapsed}ms qua NAT Loopback)"
            )
        } catch (e: Exception) {
            WanPortCheckResult(
                port = port,
                serviceName = serviceName,
                isOpen = false,
                message = "Cổng ĐÓNG hoặc chưa mở qua Modem"
            )
        } finally {
            try { socket?.close() } catch (ignored: Exception) {}
        }
    }
}
