package com.scanipcamera.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scanipcamera.app.data.config.DahuaConfigurator
import com.scanipcamera.app.data.config.HikvisionConfigurator
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceBrand
import com.scanipcamera.app.data.model.NetworkConfigPayload
import com.scanipcamera.app.data.scanner.DeviceScannerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val scannerManager = DeviceScannerManager(application)
    private val hikvisionConfigurator = HikvisionConfigurator()
    private val dahuaConfigurator = DahuaConfigurator()

    val isScanning = scannerManager.isScanning.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    private val _selectedBrandFilter = MutableStateFlow<DeviceBrand?>(null)
    val selectedBrandFilter: StateFlow<DeviceBrand?> = _selectedBrandFilter

    val devices: StateFlow<List<CameraDevice>> = combine(
        scannerManager.devices,
        _selectedBrandFilter
    ) { deviceList, filter ->
        if (filter == null) {
            deviceList
        } else {
            deviceList.filter { it.brand == filter }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state for Dialogs
    private val _editingDevice = MutableStateFlow<CameraDevice?>(null)
    val editingDevice: StateFlow<CameraDevice?> = _editingDevice

    private val _activatingDevice = MutableStateFlow<CameraDevice?>(null)
    val activatingDevice: StateFlow<CameraDevice?> = _activatingDevice

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage

    fun startScanning() {
        viewModelScope.launch {
            scannerManager.startScan()
        }
    }

    fun setBrandFilter(brand: DeviceBrand?) {
        _selectedBrandFilter.value = brand
    }

    fun openConfigDialog(device: CameraDevice) {
        _editingDevice.value = device
    }

    fun closeConfigDialog() {
        _editingDevice.value = null
    }

    fun openActivationDialog(device: CameraDevice) {
        _activatingDevice.value = device
    }

    fun closeActivationDialog() {
        _activatingDevice.value = null
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun applyNetworkConfig(device: CameraDevice, payload: NetworkConfigPayload) {
        viewModelScope.launch {
            val result = when (device.brand) {
                DeviceBrand.HIKVISION, DeviceBrand.EZVIZ -> {
                    hikvisionConfigurator.configureNetwork(device, payload)
                }
                DeviceBrand.DAHUA, DeviceBrand.IMOU -> {
                    dahuaConfigurator.configureNetwork(device, payload)
                }
                else -> {
                    // Try Hikvision then Dahua
                    val hikRes = hikvisionConfigurator.configureNetwork(device, payload)
                    if (hikRes.isSuccess) hikRes else dahuaConfigurator.configureNetwork(device, payload)
                }
            }

            result.onSuccess { msg ->
                _userMessage.value = msg
                closeConfigDialog()
                // Refresh list
                startScanning()
            }.onFailure { err ->
                _userMessage.value = "Lỗi: ${err.message}"
            }
        }
    }

    fun activateDevice(device: CameraDevice, newPassword: String) {
        viewModelScope.launch {
            val result = hikvisionConfigurator.activateDevice(
                device.macAddress,
                device.ipv4Address,
                newPassword
            )
            result.onSuccess { msg ->
                _userMessage.value = msg
                closeActivationDialog()
                startScanning()
            }.onFailure { err ->
                _userMessage.value = "Kích hoạt thất bại: ${err.message}"
            }
        }
    }
}
