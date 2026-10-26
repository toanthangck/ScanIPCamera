package com.scanipcamera.app.data.scanner

import android.util.Log
import com.company.NetSDK.CB_fSearchDevicesCB
import com.company.NetSDK.DEVICE_IP_SEARCH_INFO
import com.company.NetSDK.DEVICE_NET_INFO_EX
import com.company.NetSDK.INetSDK
import com.company.NetSDK.NET_PARAM
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceBrand
import com.scanipcamera.app.data.model.DeviceCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        val brand = DeviceBrand.fromModelOrVendor("$vendor $model $serialNo").let {
            if (it == DeviceBrand.UNKNOWN || it == DeviceBrand.ONVIF) DeviceBrand.DAHUA else it
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
}
