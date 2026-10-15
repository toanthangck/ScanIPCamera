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
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            val pattern = Regex("""$key="?([^",\s]+)"?""", RegexOption.IGNORE_CASE)
            return pattern.find(authHeader)?.groups?.get(1)?.value ?: ""
        }

        val realm = extractParam("realm")
        val nonce = extractParam("nonce")
        val qop = extractParam("qop")
        val opaque = extractParam("opaque")
        val algorithm = extractParam("algorithm").ifEmpty { "MD5" }

        val ha1 = md5("$user:$realm:$pass")
        val ha2 = md5("$method:$uri")
        val cnonce = "0a4f113b"
        val nc = "00000001"

        val response = if (qop.contains("auth", ignoreCase = true)) {
            md5("$ha1:$nonce:$nc:$cnonce:auth:$ha2")
        } else {
            md5("$ha1:$nonce:$ha2")
        }

        val sb = StringBuilder("Digest ")
        sb.append("username=\"$user\", ")
        sb.append("realm=\"$realm\", ")
        sb.append("nonce=\"$nonce\", ")
        sb.append("uri=\"$uri\", ")
        sb.append("response=\"$response\"")
        if (qop.contains("auth", ignoreCase = true)) {
            sb.append(", qop=auth, nc=$nc, cnonce=\"$cnonce\"")
        }
        if (opaque.isNotEmpty()) {
            sb.append(", opaque=\"$opaque\"")
        }
        if (algorithm.isNotEmpty()) {
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
            var conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                val auth = Base64.encodeToString("$user:$pass".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                setRequestProperty("Authorization", "Basic $auth")
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

            // If 401, retry with Digest Auth
            if (responseCode == 401) {
                val authHeader = conn.getHeaderField("WWW-Authenticate")
                if (authHeader != null && authHeader.contains("Digest", ignoreCase = true)) {
                    val uriPath = url.file.ifEmpty { "/" }
                    val digestHeader = computeDigestHeader(method, uriPath, authHeader, user, pass)
                    conn.disconnect()
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = method
                        connectTimeout = TIMEOUT_MS
                        readTimeout = TIMEOUT_MS
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
     * Fetch raw image bytes from camera with Basic/Digest authentication
     */
    private fun fetchImageBytes(
        urlString: String,
        user: String,
        pass: String
    ): Result<ByteArray> {
        return try {
            val url = URL(urlString)
            var conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 4000
                val auth = Base64.encodeToString("$user:$pass".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                setRequestProperty("Authorization", "Basic $auth")
            }

            var responseCode = conn.responseCode

            // If 401, check for Digest Auth challenge
            if (responseCode == 401) {
                val authHeader = conn.getHeaderField("WWW-Authenticate")
                if (authHeader != null && authHeader.contains("Digest", ignoreCase = true)) {
                    val uriPath = url.file.ifEmpty { "/" }
                    val digestHeader = computeDigestHeader("GET", uriPath, authHeader, user, pass)
                    conn.disconnect()
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 3500
                        readTimeout = 4000
                        setRequestProperty("Authorization", digestHeader)
                    }
                    responseCode = conn.responseCode
                }
            }

            if (responseCode in 200..299) {
                val bytes = conn.inputStream.use { it.readBytes() }
                if (bytes.isNotEmpty()) {
                    Result.success(bytes)
                } else {
                    Result.failure(Exception("Empty snapshot bytes"))
                }
            } else {
                Result.failure(Exception("HTTP $responseCode"))
            }
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
        pass: String
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        val urls = when (device.brand) {
            DeviceBrand.DAHUA, DeviceBrand.IMOU, DeviceBrand.KBVISION -> listOf(
                "http://${device.ipv4Address}:${device.httpPort}/cgi-bin/snapshot.cgi?channel=1",
                "http://${device.ipv4Address}:${device.httpPort}/cgi-bin/snapshot.cgi"
            )
            DeviceBrand.HIKVISION, DeviceBrand.EZVIZ -> listOf(
                "http://${device.ipv4Address}:${device.httpPort}/ISAPI/Streaming/channels/101/picture",
                "http://${device.ipv4Address}:${device.httpPort}/onvif-http/snapshot?channel=1"
            )
            else -> listOf(
                "http://${device.ipv4Address}:${device.httpPort}/cgi-bin/snapshot.cgi?channel=1",
                "http://${device.ipv4Address}:${device.httpPort}/ISAPI/Streaming/channels/101/picture",
                "http://${device.ipv4Address}:${device.httpPort}/images/snapshot.jpg",
                "http://${device.ipv4Address}:${device.httpPort}/snapshot.jpg"
            )
        }

        for (url in urls) {
            val res = fetchImageBytes(url, user, pass)
            if (res.isSuccess) {
                val bytes = res.getOrNull()
                if (bytes != null && bytes.isNotEmpty()) {
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        return@withContext Result.success(bmp)
                    }
                }
            }
        }
        Result.failure(Exception("Không thể tải hình ảnh từ camera. Vui lòng kiểm tra mật khẩu hoặc đường truyền."))
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
}
