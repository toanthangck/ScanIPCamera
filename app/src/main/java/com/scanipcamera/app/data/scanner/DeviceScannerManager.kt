package com.scanipcamera.app.data.scanner

import android.content.Context
import android.util.Log
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceBrand
import com.scanipcamera.app.data.model.DeviceCategory
import com.scanipcamera.app.data.network.NetworkUtils
import com.scanipcamera.app.data.network.WifiNetworkInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

enum class ScanMode {
    UDP_DISCOVERY, // SADP + Dahua + ONVIF + Smart Subnet Probe
    SUBNET_PING    // Pure TCP Port probe 1..254
}

class DeviceScannerManager(private val context: Context) {

    val networkUtils = NetworkUtils(context)
    private val hikvisionScanner = HikvisionSADPScanner()
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

    private fun addOrUpdateDevice(device: CameraDevice, scope: CoroutineScope? = null) {
        // STRICT CCTV FILTER: Completely exclude any non-camera / non-recorder device
        if (device.category == DeviceCategory.OTHER) {
            Log.d(TAG, "Excluding non-CCTV device: ${device.ipv4Address} (${device.model})")
            return
        }
        if (device.brand == DeviceBrand.UNKNOWN && device.rtspPort == 0 && device.sdkPort == 0 && device.rawProtocol == "PING/PORT") {
            Log.d(TAG, "Excluding unverified unknown device: ${device.ipv4Address}")
            return
        }

        var needsDeepProbe = false
        var targetIp = ""

        synchronized(discoveredMap) {
            // Find existing device matching either MAC address or IPv4 address
            val existingEntry = discoveredMap.entries.firstOrNull { (_, dev) ->
                (dev.macAddress.isNotBlank() && device.macAddress.isNotBlank() && dev.macAddress.equals(device.macAddress, ignoreCase = true)) ||
                (dev.ipv4Address.isNotBlank() && dev.ipv4Address == device.ipv4Address)
            }

            val finalKey = if (device.macAddress.isNotBlank()) device.macAddress else (existingEntry?.value?.macAddress?.takeIf { it.isNotBlank() } ?: device.ipv4Address)

            if (existingEntry == null) {
                discoveredMap[finalKey] = device
                if (device.macAddress.isBlank() || device.serialNumber.isBlank()) {
                    needsDeepProbe = true
                    targetIp = device.ipv4Address
                }
            } else {
                val existing = existingEntry.value
                val existingKey = existingEntry.key

                val mergedMac = if (existing.macAddress.isNotBlank()) existing.macAddress else device.macAddress
                val mergedSerial = if (existing.serialNumber.isNotBlank()) existing.serialNumber else device.serialNumber
                val mergedBrand = if (existing.brand != DeviceBrand.ONVIF && existing.brand != DeviceBrand.UNKNOWN) existing.brand else device.brand
                val mergedModel = if (existing.model != "ONVIF Camera" && existing.model != "Dahua Device" && existing.model.isNotBlank()) existing.model else device.model
                val mergedCategory = if (existing.category != DeviceCategory.CAMERA) existing.category else device.category
                val mergedChannels = if (existing.channelCount > 0) existing.channelCount else device.channelCount
                val mergedProtocol = if (device.rawProtocol == "SADP" || device.rawProtocol == "DHIP") device.rawProtocol else existing.rawProtocol

                val merged = existing.copy(
                    id = if (mergedMac.isNotBlank()) mergedMac else (if (mergedSerial.isNotBlank()) mergedSerial else existing.ipv4Address),
                    macAddress = mergedMac,
                    serialNumber = mergedSerial,
                    brand = mergedBrand,
                    model = mergedModel,
                    category = mergedCategory,
                    channelCount = mergedChannels,
                    rawProtocol = mergedProtocol,
                    httpPort = if (device.httpPort > 0) device.httpPort else existing.httpPort,
                    sdkPort = if (device.sdkPort > 0) device.sdkPort else existing.sdkPort,
                    verifiedOpenPorts = existing.verifiedOpenPorts + device.verifiedOpenPorts
                )

                if (existingKey != finalKey) {
                    discoveredMap.remove(existingKey)
                }
                discoveredMap[finalKey] = merged

                if (merged.macAddress.isBlank() || merged.serialNumber.isBlank()) {
                    needsDeepProbe = true
                    targetIp = merged.ipv4Address
                }
            }
            _devices.value = discoveredMap.values.filter {
                it.category == DeviceCategory.CAMERA || it.category == DeviceCategory.NVR || it.category == DeviceCategory.DVR
            }.toList()
        }

        // Asynchronously probe devices that are missing MAC or Serial Number
        if (needsDeepProbe && targetIp.isNotBlank() && scope != null) {
            scope.launch {
                try {
                    val probed = DeviceDeepProbe.probeDevice(targetIp)
                    if (probed != null && (probed.macAddress.isNotBlank() || probed.serialNumber.isNotBlank())) {
                        addOrUpdateDevice(probed, null)
                    }
                } catch (ignored: Exception) {}
            }
        }

        // Mặc định kiểm tra dải cổng từ 80 đến 9000 trên đầu ghi (NVR/DVR) để tìm chính xác port web và nhấp nháy
        val isRecorder = (device.category == DeviceCategory.NVR || device.category == DeviceCategory.DVR)
        if (isRecorder && device.ipv4Address.isNotBlank() && scope != null) {
            val checkIp = device.ipv4Address
            scope.launch {
                val open = PortScannerUtils.scanPortsRange(checkIp, 80, 9000).toSet()
                if (open.isNotEmpty()) {
                    synchronized(discoveredMap) {
                        val currentEntry = discoveredMap.entries.firstOrNull { (_, dev) -> dev.ipv4Address == checkIp }
                        if (currentEntry != null) {
                            val current = currentEntry.value
                            // Tìm chính xác cổng web thực sự trả về HTTP (hỗ trợ bất kỳ port nào từ 80-9000, e.g. 8089)
                            val httpCandidates = open.filter { it != 554 && it != 8554 && it != 37777 && it != 34567 }
                            val detectedWeb = httpCandidates.firstOrNull { PortScannerUtils.isHttpPort(checkIp, it) }
                                ?: (if (open.contains(current.httpPort)) current.httpPort else httpCandidates.firstOrNull())
                                ?: current.httpPort

                            val updatedRtspPort = if (open.contains(554)) 554 else if (open.contains(8554)) 8554 else current.rtspPort
                            val updatedSdkPort = if (open.contains(37777)) 37777 else if (open.contains(8000) && current.brand == DeviceBrand.HIKVISION) 8000 else if (open.contains(34567)) 34567 else current.sdkPort

                            val updated = current.copy(
                                httpPort = detectedWeb,
                                rtspPort = updatedRtspPort,
                                sdkPort = updatedSdkPort,
                                verifiedOpenPorts = current.verifiedOpenPorts + open
                            )
                            discoveredMap[currentEntry.key] = updated
                            _devices.value = discoveredMap.values.filter {
                                it.category == DeviceCategory.CAMERA || it.category == DeviceCategory.NVR || it.category == DeviceCategory.DVR
                            }.toList()
                        }
                    }
                }
            }
        }
    }

    private suspend fun checkRecorderPorts(ip: String, candidatePorts: List<Int>): Set<Int> = withContext(Dispatchers.IO) {
        val targets = candidatePorts.filter { it > 0 }.distinct()
        val open = mutableSetOf<Int>()
        val jobs = targets.map { port ->
            launch {
                var socket: Socket? = null
                try {
                    socket = Socket()
                    socket.connect(InetSocketAddress(ip, port), 400)
                    synchronized(open) { open.add(port) }
                } catch (_: Exception) {
                } finally {
                    try { socket?.close() } catch (_: Exception) {}
                }
            }
        }
        jobs.forEach { it.join() }
        open
    }

    suspend fun startScan(timeoutMs: Int = 4000, clearExisting: Boolean = true) {
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
            val wifiInterface = networkUtils.getWifiNetworkInterface()
            val dahuaScanner = DahuaDiscoveryScanner(wifiInterface)

            coroutineScope {
                val scope = this

                // 1. Dahua Multicast + Broadcast + DHIP Binary Framing Scanner
                launch {
                    dahuaScanner.scan(timeoutMs, broadcastAddresses) { device ->
                        addOrUpdateDevice(device, scope)
                    }
                }

                // 2. Hikvision SADP Discovery Scanner
                launch {
                    hikvisionScanner.scan(timeoutMs, broadcastAddresses) { device ->
                        addOrUpdateDevice(device, scope)
                    }
                }

                // 3. ONVIF WS-Discovery Scanner
                launch {
                    onvifScanner.scan(timeoutMs, broadcastAddresses) { device ->
                        addOrUpdateDevice(device, scope)
                    }
                }

                // 4. Concurrently scan the subnet so devices behind routers with multicast/broadcast filtering are ALSO found
                launch {
                    val currentIp = _wifiInfo.value.ipAddress.ifBlank { networkUtils.getLocalIpAddress() ?: "" }
                    if (currentIp.isNotBlank()) {
                        subnetScanner.scanSubnet(
                            localIp = currentIp,
                            onProgress = { current, total ->
                                _scanProgress.value = Pair(current, total)
                            },
                            onDeviceFound = { device ->
                                addOrUpdateDevice(device, scope)
                            }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scan orchestration error", e)
        } finally {
            networkUtils.releaseMulticastLock()
            _scanProgress.value = null
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
            coroutineScope {
                val scope = this
                subnetScanner.scanSubnet(
                    localIp = currentIp,
                    onProgress = { current, total ->
                        _scanProgress.value = Pair(current, total)
                    },
                    onDeviceFound = { device ->
                        addOrUpdateDevice(device, scope)
                    }
                )
            }
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
