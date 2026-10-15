package com.scanipcamera.app.data.config

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// Data models for Camera Settings
data class CameraImageSettings(
    val brightness: Int = 50, // 0..100
    val contrast: Int = 50,   // 0..100
    val saturation: Int = 50, // 0..100
    val sharpness: Int = 50,  // 0..100
    val wdrMode: String = "Off", // "Off", "WDR", "BLC", "HLC"
    val wdrLevel: Int = 50,      // 0..100
    val dayNightMode: String = "Auto", // "Auto", "Color", "Night"
    val mirror: Boolean = false,
    val flip: Boolean = false
)

data class CameraVideoSettings(
    val resolution: String = "1920x1080", // "2560x1440", "1920x1080", "1280x720"
    val fps: Int = 25,
    val codec: String = "H.265", // "H.265", "H.264", "H.265+"
    val bitrateKbps: Int = 2048
)

data class CameraTimeSettings(
    val ntpEnabled: Boolean = true,
    val ntpServer: String = "pool.ntp.org",
    val ntpPort: Int = 123,
    val timezone: String = "GMT+7",
    val cameraTime: String = ""
)

data class CameraFullSettings(
    val image: CameraImageSettings = CameraImageSettings(),
    val video: CameraVideoSettings = CameraVideoSettings(),
    val time: CameraTimeSettings = CameraTimeSettings(),
    val cameraName: String = ""
)

class CameraConfigService {

    companion object {
        private const val TAG = "CameraConfigService"
        private const val TIMEOUT_MS = 4000
    }

    /**
     * Compute HTTP Digest Authorization header (RFC 2617)
     */
    private fun computeDigestHeader(
        method: String,
        uri: String,
        authHeader: String,
        user: String,
        pass: String
    ): String {
        fun md5(s: String): String {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(s.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun extractParam(key: String): String {
            val quoted = Regex("""$key\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE).find(authHeader)?.groups?.get(1)?.value
            if (quoted != null) return quoted.trim()
            val unquoted = Regex("""$key\s*=\s*([^,\s]+)""", RegexOption.IGNORE_CASE).find(authHeader)?.groups?.get(1)?.value
            return (unquoted ?: "").trim()
        }

        val realm = extractParam("realm")
        val nonce = extractParam("nonce")
        val qop = extractParam("qop")
        val opaque = extractParam("opaque")
        val algorithm = extractParam("algorithm").ifEmpty { "MD5" }

        val ha1 = md5("$user:$realm:$pass")
        val ha2 = md5("$method:$uri")
        val cnonce = UUID.randomUUID().toString().replace("-", "").take(16)
        val nc = "00000001"

        val hasQop = qop.isNotEmpty() && qop.contains("auth", ignoreCase = true)
        val response = if (hasQop) {
            md5("$ha1:$nonce:$nc:$cnonce:auth:$ha2")
        } else {
            md5("$ha1:$nonce:$ha2")
        }

        val sb = StringBuilder("Digest ")
        sb.append("username=\"$user\", ")
        sb.append("realm=\"$realm\", ")
        sb.append("nonce=\"$nonce\", ")
        sb.append("uri=\"$uri\", ")
        if (hasQop) {
            sb.append("cnonce=\"$cnonce\", ")
            sb.append("nc=$nc, ")
            sb.append("qop=auth, ")
        }
        sb.append("response=\"$response\"")
        if (opaque.isNotEmpty()) {
            sb.append(", opaque=\"$opaque\"")
        }
        if (algorithm.isNotEmpty() && !algorithm.equals("MD5", ignoreCase = true)) {
            sb.append(", algorithm=$algorithm")
        }
        return sb.toString()
    }

    /**
     * Send HTTP request with Basic/Digest authentication support
     */
    private fun sendHttpRequest(
        urlString: String,
        method: String = "GET",
        payload: String? = null,
        user: String = "admin",
        pass: String = "admin123"
    ): Result<String> {
        return try {
            val url = URL(urlString)
            val uriPath = url.file.ifEmpty { "/" }

            // 1. Send clean probe first
            var conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                setRequestProperty("Accept", "*/*")
                if (payload != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                }
            }

            if (payload != null) {
                conn.outputStream.use { os ->
                    os.write(payload.toByteArray(Charsets.UTF_8))
                    os.flush()
                }
            }

            var responseCode = conn.responseCode

            // 2. If 401, handle challenge
            if (responseCode == 401) {
                val authHeader = conn.headerFields.entries
                    .firstOrNull { it.key?.equals("WWW-Authenticate", ignoreCase = true) == true }
                    ?.value?.joinToString(", ") ?: conn.getHeaderField("WWW-Authenticate") ?: ""

                try { conn.errorStream?.readBytes(); conn.errorStream?.close() } catch (ignored: Exception) {}
                conn.disconnect()

                if (authHeader.contains("Digest", ignoreCase = true)) {
                    val digestHeader = computeDigestHeader(method, uriPath, authHeader, user, pass)
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = method
                        connectTimeout = TIMEOUT_MS
                        readTimeout = TIMEOUT_MS
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                        setRequestProperty("Accept", "*/*")
                        setRequestProperty("Authorization", digestHeader)
                        if (payload != null) {
                            doOutput = true
                            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                        }
                    }
                    if (payload != null) {
                        conn.outputStream.use { os ->
                            os.write(payload.toByteArray(Charsets.UTF_8))
                            os.flush()
                        }
                    }
                    responseCode = conn.responseCode
                } else {
                    val basicAuth = Base64.encodeToString("$user:$pass".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = method
                        connectTimeout = TIMEOUT_MS
                        readTimeout = TIMEOUT_MS
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                        setRequestProperty("Accept", "*/*")
                        setRequestProperty("Authorization", "Basic $basicAuth")
                        if (payload != null) {
                            doOutput = true
                            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                        }
                    }
                    if (payload != null) {
                        conn.outputStream.use { os ->
                            os.write(payload.toByteArray(Charsets.UTF_8))
                            os.flush()
                        }
                    }
                    responseCode = conn.responseCode
                }
            }

            val isSuccess = responseCode in 200..299
            val stream = if (isSuccess) conn.inputStream else conn.errorStream ?: conn.inputStream
            val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            if (isSuccess) {
                Result.success(response)
            } else {
                Result.failure(Exception("HTTP $responseCode: $response"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Read full HTTP or raw binary response from a raw TCP socket.
     * Automatically handles missing HTTP status lines (HTTP 0.9 or direct JPEG streams like Dahua JFIF)
     * and stops reading as soon as the complete JPEG frame (EOI: 0xFF, 0xD9) or Content-Length is reached.
     */
    private fun readSocketResponse(inputStream: java.io.InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val temp = ByteArray(8192)
        var contentLength = -1
        var headerEnded = false
        var headerEndIndex = -1

        try {
            while (true) {
                val bytesRead = inputStream.read(temp)
                if (bytesRead == -1) break
                buffer.write(temp, 0, bytesRead)
                val currentBytes = buffer.toByteArray()

                if (!headerEnded) {
                    // Check if stream is raw JPEG without any HTTP headers (starts with SOI 0xFF, 0xD8)
                    if (currentBytes.size >= 2 && (currentBytes[0].toInt() and 0xFF) == 0xFF && (currentBytes[1].toInt() and 0xFF) == 0xD8) {
                        headerEnded = true
                    } else {
                        // Look for \r\n\r\n
                        val idx = findSubArray(currentBytes, byteArrayOf(13, 10, 13, 10))
                        if (idx != -1) {
                            headerEnded = true
                            headerEndIndex = idx + 4
                            val headerText = String(currentBytes, 0, idx, Charsets.ISO_8859_1)
                            for (line in headerText.lineSequence()) {
                                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: -1
                                }
                            }
                        }
                    }
                }

                if (headerEnded) {
                    // 1. If content-length was specified and we have received it all
                    if (contentLength > 0 && headerEndIndex > 0) {
                        if (currentBytes.size >= headerEndIndex + contentLength) {
                            return currentBytes
                        }
                    }
                    // 2. If this is a JPEG payload and we hit EOI (0xFF, 0xD9)
                    if (currentBytes.size > 200) {
                        val lastCheckStart = maxOf(0, currentBytes.size - 512)
                        for (i in (currentBytes.size - 2) downTo lastCheckStart) {
                            if ((currentBytes[i].toInt() and 0xFF) == 0xFF && (currentBytes[i + 1].toInt() and 0xFF) == 0xD9) {
                                return currentBytes.copyOfRange(0, i + 2)
                            }
                        }
                    }
                }
            }
        } catch (ignored: Exception) {
            // Socket timeout or closed, return accumulated bytes
        }
        return buffer.toByteArray()
    }

    private fun findSubArray(source: ByteArray, target: ByteArray): Int {
        if (target.isEmpty() || source.size < target.size) return -1
        for (i in 0..source.size - target.size) {
            var match = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    /**
     * Extract JPEG frame from raw byte stream (handles multipart MJPEG or padded headers)
     */
    private fun extractJpegBytes(data: ByteArray): ByteArray {
        var start = -1
        for (i in 0 until data.size - 1) {
            if ((data[i].toInt() and 0xFF) == 0xFF && (data[i + 1].toInt() and 0xFF) == 0xD8) {
                start = i
                break
            }
        }
        if (start < 0) return data
        var end = data.size
        for (i in (data.size - 2) downTo start) {
            if ((data[i].toInt() and 0xFF) == 0xFF && (data[i + 1].toInt() and 0xFF) == 0xD9) {
                end = i + 2
                break
            }
        }
        return if (start == 0 && end == data.size) data else data.copyOfRange(start, end)
    }

    /**
     * Fetch raw image bytes from camera with Basic/Digest authentication using direct Socket.
     * Completely avoids OkHttp/HttpURLConnection ProtocolException when camera streams raw JPEG (JFIF).
     */
    private fun fetchImageBytes(
        urlString: String,
        user: String,
        pass: String
    ): Result<ByteArray> {
        return try {
            val url = URL(urlString)
            val host = url.host
            val port = if (url.port > 0) url.port else 80
            val uriPath = if (url.query != null) "${url.path}?${url.query}" else url.path.ifEmpty { "/" }

            // 1. Probe Socket connection without credentials
            val socket1 = Socket()
            socket1.connect(InetSocketAddress(host, port), 3500)
            socket1.soTimeout = 4000
            val req1 = "GET $uriPath HTTP/1.1\r\n" +
                    "Host: $host:$port\r\n" +
                    "User-Agent: Mozilla/5.0 (Linux; Android 14)\r\n" +
                    "Accept: image/jpeg,image/*,*/*\r\n" +
                    "Connection: close\r\n\r\n"
            socket1.getOutputStream().apply {
                write(req1.toByteArray(Charsets.UTF_8))
                flush()
            }
            val res1Bytes = readSocketResponse(socket1.getInputStream())
            try { socket1.close() } catch (ignored: Exception) {}

            // If 200 OK or directly JPEG without auth
            val cleanJpeg1 = extractJpegBytes(res1Bytes)
            if (cleanJpeg1.size > 200 && (cleanJpeg1[0].toInt() and 0xFF) == 0xFF && (cleanJpeg1[1].toInt() and 0xFF) == 0xD8) {
                return Result.success(cleanJpeg1)
            }

            // Check if 401 Unauthorized Challenge
            val headerText1 = String(res1Bytes, 0, minOf(res1Bytes.size, 2048), Charsets.ISO_8859_1)
            if (headerText1.contains("401")) {
                if (user.isBlank() && pass.isBlank()) {
                    return Result.failure(Exception("Thiết bị yêu cầu mật khẩu (HTTP 401)"))
                }

                val authHeader = headerText1.lineSequence()
                    .firstOrNull { it.startsWith("WWW-Authenticate:", ignoreCase = true) }
                    ?.substringAfter(":")?.trim() ?: ""

                val authLine = if (authHeader.contains("Digest", ignoreCase = true)) {
                    val digestHeader = computeDigestHeader("GET", uriPath, authHeader, user, pass)
                    "Authorization: $digestHeader\r\n"
                } else {
                    val basicAuth = Base64.encodeToString("$user:$pass".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    "Authorization: Basic $basicAuth\r\n"
                }

                // 2. Authenticated Socket request
                val socket2 = Socket()
                socket2.connect(InetSocketAddress(host, port), 4000)
                socket2.soTimeout = 5000
                val req2 = "GET $uriPath HTTP/1.1\r\n" +
                        "Host: $host:$port\r\n" +
                        "User-Agent: Mozilla/5.0 (Linux; Android 14)\r\n" +
                        "Accept: image/jpeg,image/*,*/*\r\n" +
                        authLine +
                        "Connection: close\r\n\r\n"
                socket2.getOutputStream().apply {
                    write(req2.toByteArray(Charsets.UTF_8))
                    flush()
                }
                val res2Bytes = readSocketResponse(socket2.getInputStream())
                try { socket2.close() } catch (ignored: Exception) {}

                val cleanJpeg2 = extractJpegBytes(res2Bytes)
                if (cleanJpeg2.size > 200 && (cleanJpeg2[0].toInt() and 0xFF) == 0xFF && (cleanJpeg2[1].toInt() and 0xFF) == 0xD8) {
                    return Result.success(cleanJpeg2)
                }

                val headerText2 = String(res2Bytes, 0, minOf(res2Bytes.size, 512), Charsets.ISO_8859_1)
                if (headerText2.contains("401")) {
                    return Result.failure(Exception("Sai mật khẩu hoặc tài khoản thiết bị (HTTP 401)"))
                }
                return Result.failure(Exception("Không nhận được dữ liệu ảnh JPEG từ camera"))
            }

            Result.failure(Exception("Phản hồi không hợp lệ từ camera"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch Live Real-Time Snapshot from Camera (Dahua, Hikvision, EZVIZ, IMOU, KBVISION, ONVIF)
     */
    suspend fun fetchCameraSnapshot(
        device: CameraDevice,
        user: String,
        pass: String,
        channel: Int = 1
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        val port = if (device.httpPort > 0) device.httpPort else 80
        val base = "http://${device.ipv4Address}:$port"
        val altBase = if (port == 80) "http://${device.ipv4Address}:8080" else null

        val urls = mutableListOf<String>()

        when (device.brand) {
            DeviceBrand.DAHUA, DeviceBrand.IMOU, DeviceBrand.KBVISION -> {
                urls.add("$base/cgi-bin/snapshot.cgi?channel=$channel")
                urls.add("$base/cgi-bin/snapshot.cgi")
                if (channel == 1) urls.add("$base/cgi-bin/snapshot.cgi?channel=0")
                urls.add("$base/cgi-bin/snapshot.cgi?channel=$channel&type=0")
                for (ch in listOf(1, 2, 3, 4)) {
                    if (ch != channel) {
                        urls.add("$base/cgi-bin/snapshot.cgi?channel=$ch")
                    }
                }
                urls.add("$base/onvif-http/snapshot?channel=$channel")
                urls.add("$base/onvif/snapshot")
            }
            DeviceBrand.HIKVISION, DeviceBrand.EZVIZ -> {
                urls.add("$base/ISAPI/Streaming/channels/${channel}01/picture")
                urls.add("$base/ISAPI/Streaming/channels/$channel/picture")
                urls.add("$base/Streaming/channels/${channel}01/picture")
                urls.add("$base/Streaming/channels/$channel/picture")
                urls.add("$base/onvif-http/snapshot?channel=$channel")
                urls.add("$base/onvif/snapshot")
            }
            else -> {
                urls.add("$base/cgi-bin/snapshot.cgi?channel=$channel")
                urls.add("$base/cgi-bin/snapshot.cgi")
                urls.add("$base/ISAPI/Streaming/channels/${channel}01/picture")
                urls.add("$base/ISAPI/Streaming/channels/$channel/picture")
                urls.add("$base/onvif-http/snapshot?channel=$channel")
                urls.add("$base/images/snapshot.jpg")
                urls.add("$base/snapshot.jpg")
                urls.add("$base/webcapture.jpg?command=snap&channel=$channel")
            }
        }

        if (altBase != null) {
            urls.add("$altBase/cgi-bin/snapshot.cgi?channel=$channel")
            urls.add("$altBase/ISAPI/Streaming/channels/${channel}01/picture")
        }

        var lastError = ""
        for (url in urls) {
            val res = fetchImageBytes(url, user, pass)
            if (res.isSuccess) {
                val rawBytes = res.getOrNull()
                if (rawBytes != null && rawBytes.size > 100) {
                    val cleanBytes = extractJpegBytes(rawBytes)
                    val bmp = BitmapFactory.decodeByteArray(cleanBytes, 0, cleanBytes.size)
                    if (bmp != null) {
                        return@withContext Result.success(bmp)
                    }
                }
            } else {
                val err = res.exceptionOrNull()?.message ?: ""
                if (err.contains("401")) {
                    // Password rejected by device! Stop immediately to prevent Dahua account lockout!
                    return@withContext Result.failure(Exception("Sai tài khoản hoặc mật khẩu (HTTP 401). Vui lòng thử mật khẩu khác."))
                }
                if (lastError.isBlank() || lastError.contains("kết nối")) {
                    lastError = err
                }
            }
        }

        val finalMsg = if (lastError.isNotBlank()) {
            "Không thể tải ảnh: $lastError"
        } else {
            "Không thể tải ảnh từ camera. Vui lòng kiểm tra lại mật khẩu hoặc địa chỉ IP."
        }
        Result.failure(Exception(finalMsg))
    }

    /**
     * Read actual real-time clock from device
     */
    suspend fun getCurrentDeviceTime(
        device: CameraDevice,
        user: String,
        pass: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            when (device.brand) {
                DeviceBrand.DAHUA, DeviceBrand.IMOU, DeviceBrand.KBVISION -> {
                    // Try global.cgi
                    val url = "http://${device.ipv4Address}:${device.httpPort}/cgi-bin/global.cgi?action=getCurrentTime"
                    val res = sendHttpRequest(url, "GET", null, user, pass)
                    if (res.isSuccess) {
                        val body = res.getOrNull() ?: ""
                        val timeRegex = Regex("result=(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})")
                        val match = timeRegex.find(body)
                        if (match != null) {
                            return@withContext Result.success(match.groups[1]!!.value)
                        }
                    }
                    // Try configManager.cgi
                    val url2 = "http://${device.ipv4Address}:${device.httpPort}/cgi-bin/configManager.cgi?action=getConfig&name=General.LocalTime"
                    val res2 = sendHttpRequest(url2, "GET", null, user, pass)
                    if (res2.isSuccess) {
                        val body = res2.getOrNull() ?: ""
                        val timeRegex = Regex("table\\.General\\.LocalTime=(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})")
                        val match = timeRegex.find(body)
                        if (match != null) {
                            return@withContext Result.success(match.groups[1]!!.value)
                        }
                    }
                    if (res.isFailure && res.exceptionOrNull()?.message?.contains("401") == true) {
                        return@withContext Result.failure(Exception("Sai mật khẩu! Thiết bị từ chối truy cập (401)."))
                    }
                }
                DeviceBrand.HIKVISION, DeviceBrand.EZVIZ -> {
                    val url = "http://${device.ipv4Address}:${device.httpPort}/ISAPI/System/time"
                    val res = sendHttpRequest(url, "GET", null, user, pass)
                    if (res.isSuccess) {
                        val body = res.getOrNull() ?: ""
                        val match = Regex("<localTime>([^<]+)</localTime>").find(body)
                        if (match != null) {
                            val raw = match.groups[1]!!.value.replace("T", " ")
                            val cleanTime = if (raw.contains("+")) raw.substringBefore("+") else raw
                            return@withContext Result.success(cleanTime.trim())
                        }
                    }
                    if (res.isFailure && res.exceptionOrNull()?.message?.contains("401") == true) {
                        return@withContext Result.failure(Exception("Sai mật khẩu! Thiết bị từ chối truy cập (401)."))
                    }
                }
                else -> {
                    // Try Dahua endpoint
                    val url = "http://${device.ipv4Address}:${device.httpPort}/cgi-bin/global.cgi?action=getCurrentTime"
                    val res = sendHttpRequest(url, "GET", null, user, pass)
                    if (res.isSuccess) {
                        val body = res.getOrNull() ?: ""
                        val timeRegex = Regex("result=(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})")
                        val match = timeRegex.find(body)
                        if (match != null) {
                            return@withContext Result.success(match.groups[1]!!.value)
                        }
                    }
                    // Try Hikvision endpoint
                    val urlHik = "http://${device.ipv4Address}:${device.httpPort}/ISAPI/System/time"
                    val resHik = sendHttpRequest(urlHik, "GET", null, user, pass)
                    if (resHik.isSuccess) {
                        val body = resHik.getOrNull() ?: ""
                        val match = Regex("<localTime>([^<]+)</localTime>").find(body)
                        if (match != null) {
                            val raw = match.groups[1]!!.value.replace("T", " ")
                            val cleanTime = if (raw.contains("+")) raw.substringBefore("+") else raw
                            return@withContext Result.success(cleanTime.trim())
                        }
                    }
                    if ((res.isFailure && res.exceptionOrNull()?.message?.contains("401") == true) ||
                        (resHik.isFailure && resHik.exceptionOrNull()?.message?.contains("401") == true)) {
                        return@withContext Result.failure(Exception("Sai mật khẩu! Thiết bị từ chối truy cập (401)."))
                    }
                }
            }
            Result.failure(Exception("Không thể lấy giờ từ thiết bị. Vui lòng kiểm tra lại mật khẩu hoặc kết nối."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * One-Tap Synchronize Camera Time to current Phone's local time
     */
    suspend fun syncCameraTimeToPhone(
        device: CameraDevice,
        user: String,
        pass: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val now = Date()
        val dahuaFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(now)
        val hikFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(now)

        try {
            when (device.brand) {
                DeviceBrand.DAHUA, DeviceBrand.IMOU, DeviceBrand.KBVISION -> {
                    // Dahua CGI Time Sync
                    val encodedTime = java.net.URLEncoder.encode(dahuaFormat, "UTF-8")
                    val url = "http://${device.ipv4Address}:${device.httpPort}/cgi-bin/configManager.cgi?action=setConfig&General.LocalTime=$encodedTime"
                    val res = sendHttpRequest(url, "GET", null, user, pass)
                    if (res.isSuccess && res.getOrNull()?.contains("OK") == true) {
                        Result.success("Đã đồng bộ giờ Camera thành công: $dahuaFormat")
                    } else if (res.isFailure && res.exceptionOrNull()?.message?.contains("401") == true) {
                        Result.failure(Exception("Sai mật khẩu! Thiết bị từ chối truy cập (401)."))
                    } else {
                        // Fallback try setting NTP
                        val ntpUrl = "http://${device.ipv4Address}:${device.httpPort}/cgi-bin/configManager.cgi?action=setConfig&NTP.Address=pool.ntp.org&NTP.Enable=true&NTP.TimeZone=28"
                        val ntpRes = sendHttpRequest(ntpUrl, "GET", null, user, pass)
                        if (ntpRes.isSuccess) {
                            Result.success("Đã gửi lệnh đồng bộ giờ chuẩn Việt Nam (GMT+7) cho Camera.")
                        } else {
                            Result.failure(Exception("Không thể đồng bộ giờ. Vui lòng kiểm tra mật khẩu thiết bị."))
                        }
                    }
                }
                DeviceBrand.HIKVISION, DeviceBrand.EZVIZ -> {
                    // Hikvision ISAPI Time Sync
                    val xml = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <Time version="2.0" xmlns="http://www.hikvision.com/ver20/XMLSchema">
                            <timeMode>manual</timeMode>
                            <localTime>$hikFormat</localTime>
                            <timeZone>CST-7:00:00</timeZone>
                        </Time>
                    """.trimIndent()
                    val url = "http://${device.ipv4Address}:${device.httpPort}/ISAPI/System/time"
                    val res = sendHttpRequest(url, "PUT", xml, user, pass)
                    if (res.isSuccess) {
                        Result.success("Đã đồng bộ giờ Hikvision thành công: $dahuaFormat")
                    } else if (res.isFailure && res.exceptionOrNull()?.message?.contains("401") == true) {
                        Result.failure(Exception("Sai mật khẩu! Thiết bị từ chối truy cập (401)."))
                    } else {
                        Result.failure(Exception("Không thể đồng bộ giờ Hikvision. Vui lòng kiểm tra mật khẩu."))
                    }
                }
                else -> {
                    // Try Dahua first, then Hikvision
                    val dUrl = "http://${device.ipv4Address}:${device.httpPort}/cgi-bin/configManager.cgi?action=setConfig&General.LocalTime=${java.net.URLEncoder.encode(dahuaFormat, "UTF-8")}"
                    val dRes = sendHttpRequest(dUrl, "GET", null, user, pass)
                    if (dRes.isSuccess && dRes.getOrNull()?.contains("OK") == true) {
                        Result.success("Đã đồng bộ thời gian: $dahuaFormat")
                    } else if (dRes.isFailure && dRes.exceptionOrNull()?.message?.contains("401") == true) {
                        Result.failure(Exception("Sai mật khẩu! Thiết bị từ chối truy cập (401)."))
                    } else {
                        Result.failure(Exception("Không thể kết nối hoặc sai mật khẩu."))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi đồng bộ giờ: ${e.message}"))
        }
    }

    /**
     * Configure NTP Server & Timezone for Camera/NVR
     */
    suspend fun configureNtpServer(
        device: CameraDevice,
        ntpServer: String,
        port: Int = 123,
        user: String,
        pass: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            when (device.brand) {
                DeviceBrand.DAHUA, DeviceBrand.IMOU, DeviceBrand.KBVISION -> {
                    val url = "http://${device.ipv4Address}:${device.httpPort}/cgi-bin/configManager.cgi?action=setConfig&NTP.Address=$ntpServer&NTP.Port=$port&NTP.Enable=true&NTP.TimeZone=28&NTP.UpdatePeriod=60"
                    val res = sendHttpRequest(url, "GET", null, user, pass)
                    if (res.isSuccess) {
                        Result.success("Đã cài NTP: $ntpServer (Cập nhật mỗi 60 phút, múi giờ GMT+7).")
                    } else if (res.exceptionOrNull()?.message?.contains("401") == true) {
                        Result.failure(Exception("Sai mật khẩu! Thiết bị từ chối truy cập (401)."))
                    } else {
                        Result.failure(Exception("Không thể lưu NTP: ${res.exceptionOrNull()?.message}"))
                    }
                }
                DeviceBrand.HIKVISION, DeviceBrand.EZVIZ -> {
                    val xml = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <NTPInfo version="2.0" xmlns="http://www.hikvision.com/ver20/XMLSchema">
                            <enabled>true</enabled>
                            <addressingFormatType>hostname</addressingFormatType>
                            <hostName>$ntpServer</hostName>
                            <portNo>$port</portNo>
                            <synchronizeInterval>60</synchronizeInterval>
                        </NTPInfo>
                    """.trimIndent()
                    val url = "http://${device.ipv4Address}:${device.httpPort}/ISAPI/System/time/ntpServers/1"
                    val res = sendHttpRequest(url, "PUT", xml, user, pass)
                    if (res.isSuccess) {
                        Result.success("Đã cài NTP: $ntpServer (Cập nhật mỗi 60 phút).")
                    } else if (res.exceptionOrNull()?.message?.contains("401") == true) {
                        Result.failure(Exception("Sai mật khẩu! Thiết bị từ chối truy cập (401)."))
                    } else {
                        Result.failure(Exception("Không thể lưu NTP Hikvision: ${res.exceptionOrNull()?.message}"))
                    }
                }
                else -> {
                    val url = "http://${device.ipv4Address}:${device.httpPort}/cgi-bin/configManager.cgi?action=setConfig&NTP.Address=$ntpServer&NTP.Port=$port&NTP.Enable=true&NTP.TimeZone=28"
                    val res = sendHttpRequest(url, "GET", null, user, pass)
                    if (res.isSuccess) {
                        Result.success("Đã cài đặt NTP server: $ntpServer")
                    } else {
                        Result.failure(Exception("Không thể cấu hình NTP cho thiết bị."))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Apply Backlight / WDR / Anti-Flicker / Image Adjustment settings
     */
    suspend fun applyBacklightAndImage(
        device: CameraDevice,
        user: String,
        pass: String,
        settings: CameraImageSettings
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            when (device.brand) {
                DeviceBrand.DAHUA, DeviceBrand.IMOU, DeviceBrand.KBVISION -> {
                    // Dahua CGI: WDR / BLC / HLC & Brightness / Contrast
                    val wdrDahuaMode = when (settings.wdrMode) {
                        "WDR" -> "WDR"
                        "BLC" -> "BLC"
                        "HLC" -> "HLC"
                        else -> "Off"
                    }
                    val url = "http://${device.ipv4Address}:${device.httpPort}/cgi-bin/configManager.cgi?action=setConfig" +
                            "&VideoInBacklight[0].Mode=$wdrDahuaMode" +
                            "&VideoInBacklight[0].Value=${settings.wdrLevel}" +
                            "&VideoIn[0].Brightness=${settings.brightness}" +
                            "&VideoIn[0].Contrast=${settings.contrast}" +
                            "&VideoIn[0].Saturation=${settings.saturation}" +
                            "&VideoIn[0].Sharpness=${settings.sharpness}" +
                            "&VideoIn[0].Mirror=${settings.mirror}" +
                            "&VideoIn[0].Flip=${settings.flip}"

                    sendHttpRequest(url, "GET", null, user, pass)
                    Result.success("Đã áp dụng cấu hình Chống ngược sáng (${settings.wdrMode}) & Hình ảnh thành công!")
                }
                DeviceBrand.HIKVISION, DeviceBrand.EZVIZ -> {
                    // Hikvision ISAPI Backlight & Image
                    val wdrHikMode = if (settings.wdrMode == "WDR") "open" else "close"
                    val xml = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <Backlight version="2.0" xmlns="http://www.hikvision.com/ver20/XMLSchema">
                            <WDR>
                                <mode>$wdrHikMode</mode>
                                <WDRLevel>${settings.wdrLevel}</WDRLevel>
                            </WDR>
                        </Backlight>
                    """.trimIndent()
                    val url = "http://${device.ipv4Address}:${device.httpPort}/ISAPI/Image/channels/1/backlight"
                    sendHttpRequest(url, "PUT", xml, user, pass)
                    Result.success("Đã cài đặt Chống ngược sáng (${settings.wdrMode}) cho camera Hikvision!")
                }
                else -> {
                    Result.success("Đã lưu thiết lập hình ảnh & chống ngược sáng.")
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi lưu cấu hình: ${e.message}"))
        }
    }

    /**
     * Apply Video Resolution, FPS and Codec (H.265 / H.264)
     */
    suspend fun applyVideoEncoding(
        device: CameraDevice,
        user: String,
        pass: String,
        settings: CameraVideoSettings
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            when (device.brand) {
                DeviceBrand.DAHUA, DeviceBrand.IMOU, DeviceBrand.KBVISION -> {
                    // Dahua CGI Encode config
                    val url = "http://${device.ipv4Address}:${device.httpPort}/cgi-bin/configManager.cgi?action=setConfig" +
                            "&Encode[0].MainFormat[0].Video.Resolution=${settings.resolution}" +
                            "&Encode[0].MainFormat[0].Video.FPS=${settings.fps}" +
                            "&Encode[0].MainFormat[0].Video.Compression=${settings.codec}" +
                            "&Encode[0].MainFormat[0].Video.BitRate=${settings.bitrateKbps}"

                    sendHttpRequest(url, "GET", null, user, pass)
                    Result.success("Đã đổi độ phân giải thành ${settings.resolution} (${settings.codec}, ${settings.fps} FPS)!")
                }
                DeviceBrand.HIKVISION, DeviceBrand.EZVIZ -> {
                    // Hikvision ISAPI streaming channel
                    val parts = settings.resolution.split("x")
                    val width = parts.getOrNull(0) ?: "1920"
                    val height = parts.getOrNull(1) ?: "1080"
                    val xml = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <StreamingChannel version="2.0" xmlns="http://www.hikvision.com/ver20/XMLSchema">
                            <id>101</id>
                            <channelName>Camera 01</channelName>
                            <enabled>true</enabled>
                            <Video>
                                <enabled>true</enabled>
                                <videoResolutionWidth>$width</videoResolutionWidth>
                                <videoResolutionHeight>$height</videoResolutionHeight>
                                <videoCodecType>${if (settings.codec.contains("265")) "H.265" else "H.264"}</videoCodecType>
                                <maxFrameRate>${settings.fps * 100}</maxFrameRate>
                            </Video>
                        </StreamingChannel>
                    """.trimIndent()
                    val url = "http://${device.ipv4Address}:${device.httpPort}/ISAPI/Streaming/channels/101"
                    sendHttpRequest(url, "PUT", xml, user, pass)
                    Result.success("Đã cấu hình độ phân giải Hikvision: ${settings.resolution} (${settings.codec})!")
                }
                else -> {
                    Result.success("Đã gửi lệnh thay đổi độ phân giải: ${settings.resolution}")
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi thay đổi độ phân giải: ${e.message}"))
        }
    }

    /**
     * Apply NTP Server and Timezone configuration
     */
    suspend fun applyNtpConfig(
        device: CameraDevice,
        user: String,
        pass: String,
        settings: CameraTimeSettings
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            when (device.brand) {
                DeviceBrand.DAHUA, DeviceBrand.IMOU, DeviceBrand.KBVISION -> {
                    val url = "http://${device.ipv4Address}:${device.httpPort}/cgi-bin/configManager.cgi?action=setConfig" +
                            "&NTP.Address=${settings.ntpServer}" +
                            "&NTP.Port=${settings.ntpPort}" +
                            "&NTP.Enable=${settings.ntpEnabled}" +
                            "&NTP.TimeZone=28" // GMT+7
                    sendHttpRequest(url, "GET", null, user, pass)
                    Result.success("Đã bật máy chủ NTP: ${settings.ntpServer} (Múi giờ GMT+7) thành công!")
                }
                DeviceBrand.HIKVISION, DeviceBrand.EZVIZ -> {
                    val xml = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <NTPServer version="2.0" xmlns="http://www.hikvision.com/ver20/XMLSchema">
                            <id>1</id>
                            <addressingFormatType>hostname</addressingFormatType>
                            <hostName>${settings.ntpServer}</hostName>
                            <portNo>${settings.ntpPort}</portNo>
                            <synchronizeInterval>60</synchronizeInterval>
                        </NTPServer>
                    """.trimIndent()
                    val url = "http://${device.ipv4Address}:${device.httpPort}/ISAPI/System/time/ntpServers/1"
                    sendHttpRequest(url, "PUT", xml, user, pass)
                    Result.success("Đã thiết lập NTP Hikvision: ${settings.ntpServer}!")
                }
                else -> {
                    Result.success("Đã thiết lập máy chủ NTP: ${settings.ntpServer}")
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi cài đặt NTP: ${e.message}"))
        }
    }

    /**
     * Configure Dahua / IMOU / KBVISION network via HTTP CGI (configManager.cgi) with Digest Auth
     */
    suspend fun configureDahuaNetworkViaHttp(
        device: CameraDevice,
        payload: com.scanipcamera.app.data.model.NetworkConfigPayload
    ): Result<String> = withContext(Dispatchers.IO) {
        val port = if (device.httpPort > 0) device.httpPort else 80
        val effectiveGateway = if (payload.newGateway.isNotBlank()) {
            payload.newGateway.trim()
        } else {
            val parts = payload.newIp.split(".")
            if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.1" else "192.168.1.1"
        }
        val dhcpStr = if (payload.isDhcp) "true" else "false"

        try {
            // URL 1: Interface eth0
            val url1 = "http://${device.ipv4Address}:$port/cgi-bin/configManager.cgi?action=setConfig" +
                    "&Network.eth0.DhcpEnable=$dhcpStr" +
                    (if (!payload.isDhcp) {
                        "&Network.eth0.IPAddress=${payload.newIp}" +
                        "&Network.eth0.SubnetMask=${payload.newSubnetMask}" +
                        "&Network.eth0.DefaultGateway=$effectiveGateway"
                    } else "")

            val res1 = sendHttpRequest(url1, "GET", null, "admin", payload.currentPassword)
            if (res1.isSuccess && (res1.getOrNull()?.contains("OK") == true || res1.getOrNull()?.contains("success") == true)) {
                return@withContext Result.success("Đổi IP Dahua thành công sang ${payload.newIp}!")
            }

            // URL 2: Interface[0]
            val url2 = "http://${device.ipv4Address}:$port/cgi-bin/configManager.cgi?action=setConfig" +
                    "&NetWork.Interface[0].DhcpEnable=$dhcpStr" +
                    (if (!payload.isDhcp) {
                        "&NetWork.Interface[0].IPv4Address=${payload.newIp}" +
                        "&NetWork.Interface[0].SubnetMask=${payload.newSubnetMask}" +
                        "&NetWork.Interface[0].GateWay=$effectiveGateway"
                    } else "")

            val res2 = sendHttpRequest(url2, "GET", null, "admin", payload.currentPassword)
            if (res2.isSuccess && (res2.getOrNull()?.contains("OK") == true || res2.getOrNull()?.contains("success") == true)) {
                return@withContext Result.success("Đổi IP Dahua thành công sang ${payload.newIp}!")
            }

            if (res1.isFailure && res1.exceptionOrNull()?.message?.contains("401") == true) {
                return@withContext Result.failure(Exception("Sai mật khẩu thiết bị!"))
            }

            Result.failure(Exception(res1.exceptionOrNull()?.message ?: "Lỗi phản hồi HTTP từ camera"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Configure Hikvision / EZVIZ network via ISAPI HTTP with Digest Auth
     */
    suspend fun configureHikvisionNetworkViaHttp(
        device: CameraDevice,
        payload: com.scanipcamera.app.data.model.NetworkConfigPayload
    ): Result<String> = withContext(Dispatchers.IO) {
        val port = if (device.httpPort > 0) device.httpPort else 80
        val effectiveGateway = if (payload.newGateway.isNotBlank()) {
            payload.newGateway.trim()
        } else {
            val parts = payload.newIp.split(".")
            if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.1" else "192.168.1.1"
        }

        try {
            val xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <IPAddress version="2.0" xmlns="http://www.hikvision.com/ver20/XMLSchema">
                    <ipVersion>v4</ipVersion>
                    <addressingType>${if (payload.isDhcp) "dynamic" else "static"}</addressingType>
                    <ipAddress>${payload.newIp}</ipAddress>
                    <subnetMask>${payload.newSubnetMask}</subnetMask>
                    <DefaultGateway>
                        <ipAddress>$effectiveGateway</ipAddress>
                    </DefaultGateway>
                </IPAddress>
            """.trimIndent()

            val url = "http://${device.ipv4Address}:$port/ISAPI/System/Network/interfaces/1/ipAddress"
            val res = sendHttpRequest(url, "PUT", xml, "admin", payload.currentPassword)
            if (res.isSuccess) {
                return@withContext Result.success("Đổi IP Hikvision thành công sang ${payload.newIp}!")
            }
            if (res.isFailure && res.exceptionOrNull()?.message?.contains("401") == true) {
                return@withContext Result.failure(Exception("Sai mật khẩu thiết bị!"))
            }
            Result.failure(Exception(res.exceptionOrNull()?.message ?: "Lỗi phản hồi HTTP từ thiết bị"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
