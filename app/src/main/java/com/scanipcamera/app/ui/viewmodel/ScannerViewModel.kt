package com.scanipcamera.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scanipcamera.app.data.config.DahuaConfigurator
import com.scanipcamera.app.data.config.HikvisionConfigurator
import com.scanipcamera.app.data.export.DeviceExportHelper
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceBrand
import com.scanipcamera.app.data.model.DeviceCategory
import com.scanipcamera.app.data.model.NetworkConfigPayload
import com.scanipcamera.app.data.network.WanInfo
import com.scanipcamera.app.data.network.WanNetworkUtils
import com.scanipcamera.app.data.network.WifiNetworkInfo
import com.scanipcamera.app.data.scanner.DeviceScannerManager
import com.scanipcamera.app.data.scanner.ScanMode
import kotlinx.coroutines.flow.*
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

    val scanProgress = scannerManager.scanProgress.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val wifiInfo: StateFlow<WifiNetworkInfo> = scannerManager.wifiInfo.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WifiNetworkInfo()
    )

    private val _wanInfo = MutableStateFlow<WanInfo?>(null)
    val wanInfo: StateFlow<WanInfo?> = _wanInfo.asStateFlow()

    private val _selectedBrandFilter = MutableStateFlow<DeviceBrand?>(null)
    val selectedBrandFilter: StateFlow<DeviceBrand?> = _selectedBrandFilter

    private val _selectedCategoryFilter = MutableStateFlow<DeviceCategory?>(null)
    val selectedCategoryFilter: StateFlow<DeviceCategory?> = _selectedCategoryFilter

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _scanMode = MutableStateFlow(ScanMode.UDP_DISCOVERY)
    val scanMode: StateFlow<ScanMode> = _scanMode

    // Filter devices based on brand, category, and search query
    val devices: StateFlow<List<CameraDevice>> = combine(
        scannerManager.devices,
        _selectedBrandFilter,
        _selectedCategoryFilter,
        _searchQuery
    ) { deviceList, brandFilter, catFilter, query ->
        val trimmed = query.trim().lowercase()
        deviceList.filter { device ->
            val matchBrand = (brandFilter == null || device.brand == brandFilter)
            val matchCategory = (catFilter == null || device.category == catFilter)
            val matchQuery = if (trimmed.isEmpty()) {
                true
            } else {
                device.ipv4Address.contains(trimmed) ||
                        device.macAddress.lowercase().contains(trimmed) ||
                        device.model.lowercase().contains(trimmed) ||
                        device.serialNumber.lowercase().contains(trimmed)
            }
            matchBrand && matchCategory && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state for Dialogs
    private val _editingDevice = MutableStateFlow<CameraDevice?>(null)
    val editingDevice: StateFlow<CameraDevice?> = _editingDevice

    private val _activatingDevice = MutableStateFlow<CameraDevice?>(null)
    val activatingDevice: StateFlow<CameraDevice?> = _activatingDevice

    private val _rtspDevice = MutableStateFlow<CameraDevice?>(null)
    val rtspDevice: StateFlow<CameraDevice?> = _rtspDevice

    private val _diagnosticDevice = MutableStateFlow<CameraDevice?>(null)
    val diagnosticDevice: StateFlow<CameraDevice?> = _diagnosticDevice

    private val _wanPortDevice = MutableStateFlow<CameraDevice?>(null)
    val wanPortDevice: StateFlow<CameraDevice?> = _wanPortDevice

    private val _rebootingDevice = MutableStateFlow<CameraDevice?>(null)
    val rebootingDevice: StateFlow<CameraDevice?> = _rebootingDevice

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage

    init {
        scannerManager.refreshWifiInfo()
        fetchWanInfo()
    }

    fun fetchWanInfo() {
        viewModelScope.launch {
            _wanInfo.value = WanNetworkUtils.getPublicWanInfo()
        }
    }

    fun startScanning() {
        viewModelScope.launch {
            fetchWanInfo()
            if (_scanMode.value == ScanMode.UDP_DISCOVERY) {
                scannerManager.startScan()
            } else {
                scannerManager.startSubnetScan()
            }
        }
    }

    fun setScanMode(mode: ScanMode) {
        _scanMode.value = mode
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setBrandFilter(brand: DeviceBrand?) {
        _selectedBrandFilter.value = brand
    }

    fun setCategoryFilter(category: DeviceCategory?) {
        _selectedCategoryFilter.value = category
    }

    fun refreshWifi() {
        scannerManager.refreshWifiInfo()
        fetchWanInfo()
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

    fun openRtspDialog(device: CameraDevice) {
        _rtspDevice.value = device
    }

    fun closeRtspDialog() {
        _rtspDevice.value = null
    }

    fun openDiagnosticDialog(device: CameraDevice) {
        _diagnosticDevice.value = device
    }

    fun closeDiagnosticDialog() {
        _diagnosticDevice.value = null
    }

    fun openWanPortDialog(device: CameraDevice? = null) {
        _wanPortDevice.value = device
    }

    fun closeWanPortDialog() {
        _wanPortDevice.value = null
    }

    fun openRebootDialog(device: CameraDevice) {
        _rebootingDevice.value = device
    }

    fun closeRebootDialog() {
        _rebootingDevice.value = null
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
                DeviceBrand.DAHUA, DeviceBrand.IMOU, DeviceBrand.KBVISION -> {
                    dahuaConfigurator.configureNetwork(device, payload)
                }
                else -> {
                    val hikRes = hikvisionConfigurator.configureNetwork(device, payload)
                    if (hikRes.isSuccess) hikRes else dahuaConfigurator.configureNetwork(device, payload)
                }
            }

            result.onSuccess { msg ->
                _userMessage.value = msg
                closeConfigDialog()
                startScanning()
            }.onFailure { err ->
                _userMessage.value = "Lỗi: ${err.message}"
            }
        }
    }

    fun activateDevice(device: CameraDevice, newPassword: String) {
        viewModelScope.launch {
            val result = when (device.brand) {
                DeviceBrand.DAHUA, DeviceBrand.IMOU, DeviceBrand.KBVISION -> {
                    dahuaConfigurator.activateDevice(device.macAddress, device.ipv4Address, newPassword)
                }
                else -> {
                    hikvisionConfigurator.activateDevice(device.macAddress, device.ipv4Address, newPassword)
                }
            }

            result.onSuccess { msg ->
                _userMessage.value = msg
                closeActivationDialog()
                startScanning()
            }.onFailure { err ->
                _userMessage.value = "Kích hoạt thất bại: ${err.message}"
            }
        }
    }

    fun rebootDevice(device: CameraDevice, password: String) {
        viewModelScope.launch {
            val result = when (device.brand) {
                DeviceBrand.DAHUA, DeviceBrand.IMOU, DeviceBrand.KBVISION -> {
                    dahuaConfigurator.rebootDevice(device, password)
                }
                else -> {
                    hikvisionConfigurator.rebootDevice(device, password)
                }
            }

            result.onSuccess { msg ->
                _userMessage.value = msg
                closeRebootDialog()
            }.onFailure { err ->
                _userMessage.value = "Khởi động lại thất bại: ${err.message}"
            }
        }
    }

    fun exportDeviceReport(asCsv: Boolean = false) {
        val currentList = devices.value
        if (currentList.isEmpty()) {
            _userMessage.value = "Chưa có thiết bị nào để xuất báo cáo!"
            return
        }
        val report = if (asCsv) {
            DeviceExportHelper.generateCsvReport(currentList)
        } else {
            DeviceExportHelper.generateTextReport(currentList)
        }
        DeviceExportHelper.shareReport(getApplication(), report)
    }
}
