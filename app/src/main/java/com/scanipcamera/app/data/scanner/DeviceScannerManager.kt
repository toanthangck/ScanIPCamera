package com.scanipcamera.app.data.scanner

import android.content.Context
import android.util.Log
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.network.NetworkUtils
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceScannerManager(private val context: Context) {

    private val networkUtils = NetworkUtils(context)
    private val hikvisionScanner = HikvisionSADPScanner()
    private val dahuaScanner = DahuaDiscoveryScanner()
    private val onvifScanner = OnvifWsDiscoveryScanner()

    private val _devices = MutableStateFlow<List<CameraDevice>>(emptyList())
    val devices: Flow<List<CameraDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: Flow<Boolean> = _isScanning.asStateFlow()

    suspend fun startScan(timeoutMs: Int = 3500) {
        if (_isScanning.value) return

        _isScanning.value = true
        val discoveredMap = mutableMapOf<String, CameraDevice>()
        _devices.value = emptyList()

        try {
            // Android requires MulticastLock during scanning
            networkUtils.acquireMulticastLock()
            val broadcastAddresses = networkUtils.getBroadcastAddresses()

            val onDeviceFound: (CameraDevice) -> Unit = { device ->
                synchronized(discoveredMap) {
                    val key = if (device.macAddress.isNotBlank()) device.macAddress else device.ipv4Address
                    val existing = discoveredMap[key]

                    // If device already found via ONVIF, update with richer SADP/DHIP info
                    if (existing == null || (existing.rawProtocol == "ONVIF" && device.rawProtocol != "ONVIF")) {
                        discoveredMap[key] = device
                    } else if (existing.serialNumber.isEmpty() && device.serialNumber.isNotEmpty()) {
                        discoveredMap[key] = device
                    }
                    _devices.value = discoveredMap.values.toList()
                }
            }

            // Run all 3 scanner protocols concurrently
            coroutineScope {
                launch {
                    hikvisionScanner.scan(timeoutMs, broadcastAddresses, onDeviceFound)
                }
                launch {
                    dahuaScanner.scan(timeoutMs, broadcastAddresses, onDeviceFound)
                }
                launch {
                    onvifScanner.scan(timeoutMs, broadcastAddresses, onDeviceFound)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scan orchestration error", e)
        } finally {
            networkUtils.releaseMulticastLock()
            _isScanning.value = false
        }
    }

    companion object {
        private const val TAG = "DeviceScannerManager"
    }
}
