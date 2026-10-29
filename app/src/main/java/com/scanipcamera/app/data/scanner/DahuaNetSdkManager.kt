package com.scanipcamera.app.data.scanner

import android.util.Log
import com.company.NetSDK.CB_fSearchDevicesCB
import com.company.NetSDK.CtrlType
import com.company.NetSDK.DEVICE_IP_SEARCH_INFO
import com.company.NetSDK.DEVICE_NET_INFO_EX
import com.company.NetSDK.EM_LOGIN_SPAC_CAP_TYPE
import com.company.NetSDK.FinalVar
import com.company.NetSDK.INetSDK
import com.company.NetSDK.NET_DEVICEINFO_Ex
import com.company.NetSDK.NET_IN_INIT_DEVICE_ACCOUNT
import com.company.NetSDK.NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY
import com.company.NetSDK.NET_OUT_INIT_DEVICE_ACCOUNT
import com.company.NetSDK.NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY
import com.company.NetSDK.NET_PARAM
import com.company.NetSDK.NET_TIME
import com.company.NetSDK.SDK_HARDDISK_STATE
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceBrand
import com.scanipcamera.app.data.model.DeviceCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DevHddInfo(
    val index: Int,
    val totalGb: Double,
    val freeGb: Double,
    val status: Int,
    val statusText: String
)

/**
 * High-performance wrapper around Dahua's official NetSDK (libnetsdk.so / INetSDK.jar).
 * Provides native discovery, IP modification, and device capability detection.
 */
object DahuaNetSdkManager {
    private const val TAG = "DahuaNetSdkManager"
    private var isInitialized = false
    private var searchHandle: Long = 0

    @Synchronized
    fun init(): Boolean {
        if (isInitialized) return true
        return try {
            INetSDK.LoadLibrarys()
            val ok = INetSDK.Init { _, _, _ -> }
            if (ok) {
                INetSDK.SetGDPREnable(true)
                val netParam = NET_PARAM().apply {
                    nConnectTime = 5000
                    nWaittime = 5000
                    nSearchRecordTime = 10000
                }
                INetSDK.SetNetworkParam(netParam)
                isInitialized = true
                Log.d(TAG, "Dahua NetSDK initialized successfully.")
            } else {
                Log.e(TAG, "Dahua NetSDK INetSDK.Init() returned false")
            }
            ok
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize Dahua NetSDK", t)
            false
        }
    }

    private fun parseString(bytes: ByteArray): String {
        val len = bytes.indexOfFirst { it == 0.toByte() }.let { if (it >= 0) it else bytes.size }
        return String(bytes, 0, len, Charsets.UTF_8).trim()
    }

    fun parseDeviceNetInfo(info: DEVICE_NET_INFO_EX): CameraDevice? {
        val ip = parseString(info.szIP)
        if (ip.isBlank() || ip.contains("/")) return null

        val serialNo = parseString(info.szSerialNo)
        val rawMac = parseString(info.szMac)
        val mac = when {
            rawMac.contains(":") || rawMac.contains("-") -> rawMac.uppercase().replace("-", ":")
            info.szMac.size >= 6 && info.szMac.take(6).any { it != 0.toByte() } -> {
                info.szMac.take(6).joinToString(":") { "%02X".format(it) }
            }
            else -> rawMac
        }

        val detailType = parseString(info.szDetailType)
        val devType = parseString(info.szDeviceType)
        val vendor = parseString(info.szVendor)
        val model = when {
            detailType.isNotBlank() -> detailType
            devType.isNotBlank() -> devType
            else -> "Dahua Device"
        }

        val softVer = parseString(info.szDevSoftVersion)
        val submask = parseString(info.szSubmask)
        val gateway = parseString(info.szGateway)

        val httpPort = if (info.nHttpPort > 0) info.nHttpPort else 80
        val sdkPort = if (info.nPort > 0) info.nPort else 37777
        val channels = if (info.wVideoInputCh > 0) info.wVideoInputCh else 0

        val (category, detectedChannels) = CameraDevice.detectCategoryAndChannels(model, "$vendor $serialNo")
        val finalChannels = if (channels > 0) channels else detectedChannels
        val brand = DeviceBrand.fromModelOrVendor("$vendor $model $serialNo $mac").let {
            if (it == DeviceBrand.UNKNOWN || it == DeviceBrand.ONVIF) {
                val macBrand = DeviceBrand.fromMacAddress(mac)
                if (macBrand != DeviceBrand.UNKNOWN) macBrand else DeviceBrand.DAHUA
            } else it
        }

        val isActivated = info.byInitStatus != 1.toByte()

        return CameraDevice(
            id = if (mac.isNotBlank()) mac else (if (serialNo.isNotBlank()) serialNo else ip),
            brand = brand,
            category = category,
            channelCount = finalChannels,
            model = model,
            serialNumber = serialNo,
            macAddress = mac,
            ipv4Address = ip,
            ipv4SubnetMask = if (submask.isNotBlank()) submask else "255.255.255.0",
            ipv4Gateway = if (gateway.isNotBlank()) gateway else "192.168.1.1",
            isDhcpEnabled = info.bDhcpEn,
            isActivated = isActivated,
            httpPort = httpPort,
            sdkPort = sdkPort,
            rtspPort = 554,
            firmwareVersion = softVer,
            rawProtocol = "DHIP/NETSDK",
            verifiedOpenPorts = setOfNotNull(httpPort, sdkPort, 554)
        )
    }

    @Synchronized
    fun startSearch(onDeviceFound: (CameraDevice) -> Unit): Boolean {
        if (!init()) return false
        stopSearch()

        val callback = CB_fSearchDevicesCB { info ->
            if (info != null) {
                parseDeviceNetInfo(info)?.let { device ->
                    onDeviceFound(device)
                }
            }
        }

        searchHandle = INetSDK.StartSearchDevices(callback)
        Log.d(TAG, "Started Dahua NetSDK device search, handle=$searchHandle")
        return searchHandle != 0L
    }

    @Synchronized
    fun stopSearch() {
        if (searchHandle != 0L) {
            try {
                INetSDK.StopSearchDevices(searchHandle)
            } catch (t: Throwable) {
                Log.w(TAG, "Error stopping search: ${t.message}")
            }
            searchHandle = 0L
        }
    }

    suspend fun searchDeviceByIp(ip: String): CameraDevice? = withContext(Dispatchers.IO) {
        if (!init() || ip.isBlank()) return@withContext null
        var foundDevice: CameraDevice? = null
        val info = DEVICE_IP_SEARCH_INFO().apply {
            nIpNum = 1
            val ipBytes = ip.toByteArray(Charsets.UTF_8)
            System.arraycopy(ipBytes, 0, szIP[0], 0, minOf(ipBytes.size, 63))
        }
        val callback = CB_fSearchDevicesCB { netInfo ->
            if (netInfo != null) {
                val dev = parseDeviceNetInfo(netInfo)
                if (dev != null && dev.ipv4Address == ip) {
                    foundDevice = dev
                }
            }
        }
        val ok = INetSDK.SearchDevicesByIPs(info, callback, 1500)
        if (ok && foundDevice != null) {
            foundDevice
        } else {
            null
        }
    }

    suspend fun modifyDevice(
        currentIp: String,
        macAddress: String,
        newIp: String,
        newMask: String,
        newGateway: String,
        username: String = "admin",
        password: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        if (!init()) return@withContext false
        try {
            val netInfo = DEVICE_NET_INFO_EX().apply {
                iIPVersion = 4
                val macClean = macAddress.trim()
                val macBytes = macClean.toByteArray(Charsets.UTF_8)
                System.arraycopy(macBytes, 0, szMac, 0, minOf(macBytes.size, szMac.size))

                val ipBytes = newIp.trim().toByteArray(Charsets.UTF_8)
                System.arraycopy(ipBytes, 0, szIP, 0, minOf(ipBytes.size, szIP.size))

                val maskBytes = newMask.trim().toByteArray(Charsets.UTF_8)
                System.arraycopy(maskBytes, 0, szSubmask, 0, minOf(maskBytes.size, szSubmask.size))

                val gwBytes = newGateway.trim().toByteArray(Charsets.UTF_8)
                System.arraycopy(gwBytes, 0, szGateway, 0, minOf(gwBytes.size, szGateway.size))

                if (username.isNotBlank()) {
                    val userBytes = username.trim().toByteArray(Charsets.UTF_8)
                    System.arraycopy(userBytes, 0, szUserName, 0, minOf(userBytes.size, szUserName.size))
                }
                if (password.isNotBlank()) {
                    val pwdBytes = password.trim().toByteArray(Charsets.UTF_8)
                    System.arraycopy(pwdBytes, 0, szPassWord, 0, minOf(pwdBytes.size, szPassWord.size))
                }
            }
            val ok = INetSDK.ModifyDevice(netInfo, 3000)
            Log.d(TAG, "INetSDK.ModifyDevice returned $ok for IP $newIp")
            ok
        } catch (t: Throwable) {
            Log.e(TAG, "ModifyDevice failed", t)
            false
        }
    }

    fun login(ip: String, port: Int = 37777, username: String = "admin", password: String = ""): Long {
        if (!init()) return 0L
        try {
            val stuIn = NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY().apply {
                val ipBytes = ip.trim().toByteArray(Charsets.UTF_8)
                System.arraycopy(ipBytes, 0, szIP, 0, minOf(ipBytes.size, szIP.size))
                nPort = port
                val userBytes = username.trim().toByteArray(Charsets.UTF_8)
                System.arraycopy(userBytes, 0, szUserName, 0, minOf(userBytes.size, szUserName.size))
                val pwdBytes = password.trim().toByteArray(Charsets.UTF_8)
                System.arraycopy(pwdBytes, 0, szPassword, 0, minOf(pwdBytes.size, szPassword.size))
                emSpecCap = EM_LOGIN_SPAC_CAP_TYPE.EM_LOGIN_SPEC_CAP_TCP
            }
            val stuOut = NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY()
            val handle = INetSDK.LoginWithHighLevelSecurity(stuIn, stuOut)
            if (handle != 0L) return handle

            val devInfo = NET_DEVICEINFO_Ex()
            return INetSDK.LoginEx2(ip.trim(), port, username.trim(), password.trim(), 0, null, devInfo, 0)
        } catch (t: Throwable) {
            Log.e(TAG, "Login failed for $ip", t)
            return 0L
        }
    }

    fun logout(handle: Long) {
        if (handle != 0L) {
            try {
                INetSDK.Logout(handle)
            } catch (t: Throwable) {
                Log.w(TAG, "Logout error: ${t.message}")
            }
        }
    }

    suspend fun queryDeviceTime(
        ip: String,
        port: Int = 37777,
        user: String = "admin",
        pass: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val handle = login(ip, port, user, pass)
        if (handle == 0L) {
            return@withContext Result.failure(Exception("Đăng nhập thất bại (Mã lỗi: ${INetSDK.GetLastError()})"))
        }
        try {
            val netTime = NET_TIME()
            val ok = INetSDK.QueryDeviceTime(handle, netTime, 3000)
            if (ok) {
                val formatted = String.format(
                    Locale.US,
                    "%04d-%02d-%02d %02d:%02d:%02d",
                    netTime.dwYear, netTime.dwMonth, netTime.dwDay,
                    netTime.dwHour, netTime.dwMinute, netTime.dwSecond
                )
                Result.success(formatted)
            } else {
                Result.failure(Exception("Không đọc được giờ (Mã lỗi: ${INetSDK.GetLastError()})"))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            logout(handle)
        }
    }

    suspend fun syncDeviceTime(
        ip: String,
        port: Int = 37777,
        user: String = "admin",
        pass: String = "",
        targetDate: Date = Date()
    ): Result<String> = withContext(Dispatchers.IO) {
        val handle = login(ip, port, user, pass)
        if (handle == 0L) {
            return@withContext Result.failure(Exception("Đăng nhập thất bại (Mã lỗi: ${INetSDK.GetLastError()})"))
        }
        try {
            val cal = Calendar.getInstance().apply { time = targetDate }
            val netTime = NET_TIME().apply {
                setTime(
                    cal.get(Calendar.YEAR).toLong(),
                    (cal.get(Calendar.MONTH) + 1).toLong(),
                    cal.get(Calendar.DAY_OF_MONTH).toLong(),
                    cal.get(Calendar.HOUR_OF_DAY).toLong(),
                    cal.get(Calendar.MINUTE).toLong(),
                    cal.get(Calendar.SECOND).toLong()
                )
            }
            val ok = INetSDK.SetupDeviceTime(handle, netTime)
            if (ok) {
                val formatted = String.format(
                    Locale.US,
                    "%04d-%02d-%02d %02d:%02d:%02d",
                    netTime.dwYear, netTime.dwMonth, netTime.dwDay,
                    netTime.dwHour, netTime.dwMinute, netTime.dwSecond
                )
                Result.success(formatted)
            } else {
                Result.failure(Exception("Không thể đồng bộ giờ (Mã lỗi: ${INetSDK.GetLastError()})"))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            logout(handle)
        }
    }

    suspend fun rebootDevice(
        ip: String,
        port: Int = 37777,
        user: String = "admin",
        pass: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val handle = login(ip, port, user, pass)
        if (handle == 0L) {
            return@withContext Result.failure(Exception("Đăng nhập thất bại (Mã lỗi: ${INetSDK.GetLastError()})"))
        }
        try {
            val ok = INetSDK.ControlDevice(handle, CtrlType.SDK_CTRL_REBOOT, null, 3000)
            if (ok) {
                Result.success("Khởi động lại thiết bị Dahua thành công qua NetSDK!")
            } else {
                Result.failure(Exception("Lệnh khởi động lại thất bại (Mã lỗi: ${INetSDK.GetLastError()})"))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            logout(handle)
        }
    }

    fun ptzControl(
        loginHandle: Long,
        channel: Int,
        command: Int,
        param1: Byte = 4,
        param2: Byte = 4,
        bStop: Boolean = false
    ): Boolean {
        if (loginHandle == 0L) return false
        return try {
            INetSDK.SDKPTZControl(loginHandle, channel, command, param1, param2, 0.toByte(), bStop)
        } catch (t: Throwable) {
            Log.e(TAG, "ptzControl failed", t)
            false
        }
    }

    suspend fun initDeviceAccount(
        mac: String,
        targetIp: String = "",
        username: String = "admin",
        password: String,
        email: String = "",
        cellphone: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!init()) return@withContext Result.failure(Exception("NetSDK chưa được khởi tạo"))
        try {
            val stuIn = NET_IN_INIT_DEVICE_ACCOUNT().apply {
                val cleanMac = mac.trim()
                val macBytes = cleanMac.toByteArray(Charsets.UTF_8)
                System.arraycopy(macBytes, 0, szMac, 0, minOf(macBytes.size, szMac.size))
                val userBytes = username.trim().toByteArray(Charsets.UTF_8)
                System.arraycopy(userBytes, 0, szUserName, 0, minOf(userBytes.size, szUserName.size))
                val pwdBytes = password.trim().toByteArray(Charsets.UTF_8)
                System.arraycopy(pwdBytes, 0, szPwd, 0, minOf(pwdBytes.size, szPwd.size))
                if (email.isNotBlank()) {
                    val mailBytes = email.trim().toByteArray(Charsets.UTF_8)
                    System.arraycopy(mailBytes, 0, szMail, 0, minOf(mailBytes.size, szMail.size))
                }
                if (cellphone.isNotBlank()) {
                    val phoneBytes = cellphone.trim().toByteArray(Charsets.UTF_8)
                    System.arraycopy(phoneBytes, 0, szCellPhone, 0, minOf(phoneBytes.size, szCellPhone.size))
                }
            }
            val stuOut = NET_OUT_INIT_DEVICE_ACCOUNT()
            val ipArg = if (targetIp.isNotBlank()) targetIp.trim() else null
            val ok = INetSDK.InitDevAccount(stuIn, stuOut, 3500, ipArg)
            if (ok) {
                Result.success("Kích hoạt thiết bị Dahua/IMOU thành công qua NetSDK!")
            } else {
                Result.failure(Exception("Lỗi NetSDK InitDevAccount: mã lỗi ${INetSDK.GetLastError()}"))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun queryHddState(
        ip: String,
        port: Int = 37777,
        user: String = "admin",
        pass: String = ""
    ): Result<List<DevHddInfo>> = withContext(Dispatchers.IO) {
        val handle = login(ip, port, user, pass)
        if (handle == 0L) {
            return@withContext Result.failure(Exception("Đăng nhập thất bại (Mã lỗi: ${INetSDK.GetLastError()})"))
        }
        try {
            val hardDiskState = SDK_HARDDISK_STATE()
            val ok = INetSDK.QueryDevState(handle, FinalVar.SDK_DEVSTATE_DISK, hardDiskState, 3000)
            if (ok) {
                val list = mutableListOf<DevHddInfo>()
                val count = hardDiskState.dwDiskNum
                if (count > 0 && hardDiskState.stDisks != null) {
                    for (i in 0 until minOf(count, hardDiskState.stDisks.size)) {
                        val disk = hardDiskState.stDisks[i]
                        val totalGb = disk.dwVolume.toDouble() / 1024.0
                        val freeGb = disk.dwFreeSpace.toDouble() / 1024.0
                        val statusText = when (disk.dwStatus.toInt()) {
                            0 -> "Hoạt động tốt"
                            1 -> "Ngủ / Tạm dừng"
                            2 -> "Lỗi / Cần format"
                            else -> "Trạng thái (${disk.dwStatus})"
                        }
                        list.add(
                            DevHddInfo(
                                index = i + 1,
                                totalGb = totalGb,
                                freeGb = freeGb,
                                status = disk.dwStatus.toInt(),
                                statusText = statusText
                            )
                        )
                    }
                }
                Result.success(list)
            } else {
                Result.failure(Exception("Không truy vấn được thông tin ổ cứng (Mã lỗi: ${INetSDK.GetLastError()})"))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            logout(handle)
        }
    }
}
