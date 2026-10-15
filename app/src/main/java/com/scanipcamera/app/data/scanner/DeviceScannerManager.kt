package com.scanipcamera.app.data.scanner

import android.content.Context
import android.util.Log
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.network.NetworkUtils
import com.scanipcamera.app.data.network.WifiNetworkInfo
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ScanMode {
    UDP_DISCOVERY, // SADP + Dahua + ONVIF
    SUBNET_PING    // TCP Port probe 1..254
}

class DeviceScannerManager(private val context: Context) {

    val networkUtils = NetworkUtils(context)
    private val hikvisionScanner = HikvisionSADPScanner()
    private val dahuaScanner = DahuaDiscoveryScanner()
    private val onvifScanner = OnvifWsDiscoveryScanner()
    private val subnetScanner = SubnetIpScanner()

    private val discoveredMap = mutableMapOf<String, CameraDevice>()
    private val _devices = MutableStateFlow<List<CameraDevice>>(emptyList())
    val devices: Flow<List<CameraDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: Flow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow<Pair<Int, Int>?>(null) // (current, total)
    val scanProgress: Flow<Pair<Int, Int>?> = _scanProgress.asStateFlow()

    private val _wifiInfo = MutableStateFlow(WifiNetworkInfo())
    val wifiInfo: Flow<WifiNetworkInfo> = _wifiInfo.asStateFlow()

    fun refreshWifiInfo() {
        _wifiInfo.value = networkUtils.getCurrentWifiInfo()
    }

    private fun addOrUpdateDevice(device: CameraDevice) {
        synchronized(discoveredMap) {
            val key = if (device.macAddress.isNotBlank()) device.macAddress else device.ipv4Address
            val existing = discoveredMap[key]

            if (existing == null) {
                discoveredMap[key] = device
            } else {
                // If existing was found via Ping or ONVIF, enhance with SADP/DHIP info
                if (existing.rawProtocol == "PING/PORT" && device.rawProtocol != "PING/PORT") {
                    discoveredMap[key] = device
                } else if (existing.rawProtocol == "ONVIF" && (device.rawProtocol == "SADP" || device.rawProtocol == "DHIP")) {
                    discoveredMap[key] = device
                } else if (existing.serialNumber.isEmpty() && device.serialNumber.isNotEmpty()) {
                    discoveredMap[key] = device
                }
            }
            _devices.value = discoveredMap.values.toList()
        }
    }

    suspend fun startScan(timeoutMs: Int = 3500, clearExisting: Boolean = true) {
        if (_isScanning.value) return

        refreshWifiInfo()
        _isScanning.value = true
        _scanProgress.value = null
        if (clearExisting) {
            synchronized(discoveredMap) {
                discoveredMap.clear()
            }
            _devices.value = emptyList()
        }

        try {
            networkUtils.acquireMulticastLock()
            val broadcastAddresses = networkUtils.getBroadcastAddresses()

            coroutineScope {
                launch {
                    hikvisionScanner.scan(timeoutMs, broadcastAddresses) { device ->
                        addOrUpdateDevice(device)
                    }
                }
                launch {
                    dahuaScanner.scan(timeoutMs, broadcastAddresses) { device ->
                        addOrUpdateDevice(device)
                    }
                }
                launch {
                    onvifScanner.scan(timeoutMs, broadcastAddresses) { device ->
                        addOrUpdateDevice(device)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "UDP Scan orchestration error", e)
        } finally {
            networkUtils.releaseMulticastLock()
            _isScanning.value = false
        }
    }

    suspend fun startSubnetScan(clearExisting: Boolean = false) {
        if (_isScanning.value) return

        refreshWifiInfo()
        val currentIp = _wifiInfo.value.ipAddress.ifBlank { networkUtils.getLocalIpAddress() ?: "" }
        if (currentIp.isBlank()) {
            Log.w(TAG, "Cannot start subnet scan: No local IP found")
            return
        }

        _isScanning.value = true
        if (clearExisting) {
            synchronized(discoveredMap) {
                discoveredMap.clear()
            }
            _devices.value = emptyList()
        }

        try {
            subnetScanner.scanSubnet(
                localIp = currentIp,
                onProgress = { current, total ->
                    _scanProgress.value = Pair(current, total)
                },
                onDeviceFound = { device ->
                    addOrUpdateDevice(device)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Subnet scan error", e)
        } finally {
            _scanProgress.value = null
            _isScanning.value = false
        }
    }

    companion object {
        private const val TAG = "DeviceScannerManager"
    }
}
