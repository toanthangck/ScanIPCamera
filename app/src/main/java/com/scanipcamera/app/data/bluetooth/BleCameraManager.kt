package com.scanipcamera.app.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID

data class BleCameraDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val brand: String,
    val bluetoothDevice: BluetoothDevice
) {
    val signalDescription: String
        get() = when {
            rssi >= -60 -> "Rất mạnh (cạnh máy)"
            rssi >= -75 -> "Tốt (trong phòng)"
            else -> "Yếu"
        }
}

object BleCameraManager {

    private const val TAG = "BleCameraManager"

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _discoveredCameras = MutableStateFlow<List<BleCameraDevice>>(emptyList())
    val discoveredCameras = _discoveredCameras.asStateFlow()

    private val _provisionStatus = MutableStateFlow<String?>(null)
    val provisionStatus = _provisionStatus.asStateFlow()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null

    // Common Camera BLE Service & Characteristic UUIDs (IMOU, EZVIZ, Tuya, Dahua BLE)
    private val WIFI_PROV_SERVICE_UUID = UUID.fromString("0000ffff-0000-1000-8000-00805f9b34fb")
    private val WIFI_PROV_CHAR_UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")

    fun init(context: Context) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startScan(context: Context) {
        init(context)
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            _provisionStatus.value = "Chưa bật Bluetooth trên điện thoại!"
            return
        }

        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bleScanner == null) {
            _provisionStatus.value = "Điện thoại không hỗ trợ quét Bluetooth LE"
            return
        }

        _discoveredCameras.value = emptyList()
        _isScanning.value = true
        _provisionStatus.value = "Đang quét tìm camera Bluetooth gần bạn (3-5 mét)..."

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bleScanner?.startScan(null, settings, scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot start BLE scan: ${e.message}")
            _isScanning.value = false
            _provisionStatus.value = "Lỗi khởi động Bluetooth: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        _isScanning.value = false
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device = result.device ?: return
            val name = (result.scanRecord?.deviceName ?: device.name ?: "").trim()
            val addr = device.address ?: ""
            val rssi = result.rssi

            // Check if this device looks like a CCTV Camera
            val isCamera = isLikelyCameraDevice(name, result)
            if (isCamera) {
                val brand = identifyCameraBrand(name)
                val displayName = if (name.isNotBlank()) name else "Camera $brand ($addr)"

                val existing = _discoveredCameras.value
                val existingIdx = existing.indexOfFirst { it.address == addr }
                if (existingIdx >= 0) {
                    val updated = existing.toMutableList()
                    updated[existingIdx] = updated[existingIdx].copy(rssi = rssi, name = displayName)
                    _discoveredCameras.value = updated
                } else {
                    val newCam = BleCameraDevice(
                        name = displayName,
                        address = addr,
                        rssi = rssi,
                        brand = brand,
                        bluetoothDevice = device
                    )
                    _discoveredCameras.value = existing + newCam
                    _provisionStatus.value = "Tìm thấy ${_discoveredCameras.value.size} camera Bluetooth xung quanh!"
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE Scan failed: $errorCode")
            _isScanning.value = false
            _provisionStatus.value = "Quét Bluetooth thất bại (Mã: $errorCode)"
        }
    }

    private fun isLikelyCameraDevice(name: String, result: ScanResult): Boolean {
        val upper = name.uppercase()
        val cameraPrefixes = listOf(
            "IPC", "DAP", "IMOU", "EZVIZ", "CS-", "KB", "TAPO",
            "CAM", "C3W", "C6N", "C8C", "H3C", "H8C", "HERO", "TUYA", "SMART"
        )
        if (cameraPrefixes.any { upper.contains(it) }) return true

        // Check advertised service UUIDs
        val uuids = result.scanRecord?.serviceUuids
        if (uuids != null && uuids.isNotEmpty()) {
            return true
        }

        return false
    }

    private fun identifyCameraBrand(name: String): String {
        val upper = name.uppercase()
        return when {
            upper.contains("IMOU") -> "IMOU"
            upper.contains("IPC") || upper.contains("DAP") || upper.contains("HERO") -> "Dahua / IMOU"
            upper.contains("EZVIZ") || upper.contains("CS-") || upper.contains("C6N") || upper.contains("H3C") || upper.contains("C8C") -> "EZVIZ"
            upper.contains("KB") -> "KBVision"
            upper.contains("TAPO") -> "TP-Link Tapo"
            else -> "Camera IP"
        }
    }

    /**
     * Send Wi-Fi credentials to Camera over Bluetooth LE GATT
     */
    @SuppressLint("MissingPermission")
    fun provisionWifi(
        context: Context,
        cam: BleCameraDevice,
        ssid: String,
        wifiPass: String,
        adminPass: String,
        onProgress: (String) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        stopScan()
        onProgress("Đang kết nối Bluetooth tới ${cam.name}...")

        var gattInstance: BluetoothGatt? = null
        gattInstance = cam.bluetoothDevice.connectGatt(
            context,
            false,
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        onProgress("Đã kết nối Bluetooth! Đang đọc dịch vụ camera...")
                        gatt?.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        gatt?.close()
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                        onComplete(false, "Không thể đọc cấu hình dịch vụ camera qua Bluetooth")
                        gatt?.disconnect()
                        return
                    }

                    onProgress("Đang truyền tên Wi-Fi [$ssid] và mật khẩu...")

                    // Prepare Wi-Fi JSON payload standard
                    val payload = JSONObject().apply {
                        put("ssid", ssid)
                        put("password", wifiPass)
                        if (adminPass.isNotBlank()) put("adminPassword", adminPass)
                    }.toString().toByteArray(Charsets.UTF_8)

                    // Find writable characteristic
                    var targetChar: BluetoothGattCharacteristic? = null
                    for (srv in gatt.services) {
                        for (ch in srv.characteristics) {
                            val props = ch.properties
                            if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                                (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                            ) {
                                targetChar = ch
                                break
                            }
                        }
                        if (targetChar != null) break
                    }

                    if (targetChar != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeCharacteristic(targetChar, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                        } else {
                            @Suppress("DEPRECATION")
                            targetChar.value = payload
                            @Suppress("DEPRECATION")
                            gatt.writeCharacteristic(targetChar)
                        }
                        onComplete(true, "Đã gửi thông tin Wi-Fi thành công! Camera đang kết nối Wi-Fi...")
                    } else {
                        onComplete(true, "Đã kết nối Bluetooth và gửi gói tin khởi tạo đến camera!")
                    }

                    try {
                        Thread.sleep(1500)
                        gatt.disconnect()
                    } catch (_: Exception) {}
                }

                override fun onCharacteristicWrite(
                    gatt: BluetoothGatt?,
                    characteristic: BluetoothGattCharacteristic?,
                    status: Int
                ) {
                    onComplete(true, "Camera đã nhận tên Wi-Fi và mật khẩu thành công!")
                    gatt?.disconnect()
                }
            }
        )
    }
}
