package com.scanipcamera.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scanipcamera.app.data.auth.GoogleAuthManager
import com.scanipcamera.app.data.auth.GoogleUser
import com.scanipcamera.app.data.config.CameraConfigService
import com.scanipcamera.app.data.config.DahuaConfigurator
import com.scanipcamera.app.data.config.HikvisionConfigurator
import com.scanipcamera.app.data.export.DeviceExportHelper
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceBrand
import com.scanipcamera.app.data.model.DeviceCategory
import com.scanipcamera.app.data.model.NetworkConfigPayload
import com.scanipcamera.app.data.model.SiteProject
import com.scanipcamera.app.data.network.WanInfo
import com.scanipcamera.app.data.network.WanNetworkUtils
import com.scanipcamera.app.data.network.WifiNetworkInfo
import com.scanipcamera.app.data.repository.SiteProjectRepository
import com.scanipcamera.app.data.scanner.DeviceScannerManager
import com.scanipcamera.app.data.scanner.ScanMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val scannerManager = DeviceScannerManager(application)
    private val hikvisionConfigurator = HikvisionConfigurator()
    private val dahuaConfigurator = DahuaConfigurator()
    private val cameraConfigService = CameraConfigService()
    private val siteProjectRepository = SiteProjectRepository(application)
    private val googleAuthManager = GoogleAuthManager(application)

    val googleUser: StateFlow<GoogleUser> = googleAuthManager.currentUser

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

    // Manual overrides for devices (e.g. user manually corrects brand or name)
    private val _deviceOverrides = MutableStateFlow<Map<String, CameraDevice>>(emptyMap())

    // Filter devices based on brand, category, and search query
    val devices: StateFlow<List<CameraDevice>> = combine(
        scannerManager.devices,
        _selectedBrandFilter,
        _selectedCategoryFilter,
        _searchQuery,
        _deviceOverrides
    ) { deviceList, brandFilter, catFilter, query, overrides ->
        val trimmed = query.trim().lowercase()
        deviceList.map { dev ->
            overrides[dev.id] ?: dev
        }.filter { device ->
            // Completely filter out any non-camera / non-recorder devices
            if (device.category == DeviceCategory.OTHER) return@filter false

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
        }.sortedWith(
            compareBy<CameraDevice> { dev ->
                // Ưu tiên đầu ghi (NVR/DVR) lên trước camera
                if (dev.category == DeviceCategory.NVR || dev.category == DeviceCategory.DVR) 0 else 1
            }.thenBy { dev ->
                // Sắp xếp địa chỉ IP từ thấp đến cao (theo giá trị số thực tế)
                ipToLong(dev.ipv4Address)
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Detect conflicting IP addresses (more than 1 device with same IP in subnet)
    val conflictingIps: StateFlow<Set<String>> = scannerManager.devices.map { list ->
        list.groupBy { it.ipv4Address }
            .filter { (ip, devicesWithIp) ->
                ip.isNotBlank() && ip != "0.0.0.0" && devicesWithIp.size > 1
            }
            .keys
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // UI state for Dialogs
    private val _editingDevice = MutableStateFlow<CameraDevice?>(null)
    val editingDevice: StateFlow<CameraDevice?> = _editingDevice

    private val _settingsDevice = MutableStateFlow<CameraDevice?>(null)
    val settingsDevice: StateFlow<CameraDevice?> = _settingsDevice

    private val _activatingDevice = MutableStateFlow<CameraDevice?>(null)
    val activatingDevice: StateFlow<CameraDevice?> = _activatingDevice

    private val _rtspDevice = MutableStateFlow<CameraDevice?>(null)
    val rtspDevice: StateFlow<CameraDevice?> = _rtspDevice

    private val _liveViewDevice = MutableStateFlow<CameraDevice?>(null)
    val liveViewDevice: StateFlow<CameraDevice?> = _liveViewDevice

    private val _diagnosticDevice = MutableStateFlow<CameraDevice?>(null)
    val diagnosticDevice: StateFlow<CameraDevice?> = _diagnosticDevice

    private val _wanPortDevice = MutableStateFlow<CameraDevice?>(null)
    val wanPortDevice: StateFlow<CameraDevice?> = _wanPortDevice

    private val _rebootingDevice = MutableStateFlow<CameraDevice?>(null)
    val rebootingDevice: StateFlow<CameraDevice?> = _rebootingDevice

    private val _editingProject = MutableStateFlow<SiteProject?>(null)
    val editingProject: StateFlow<SiteProject?> = _editingProject

    private val _timeSyncDevice = MutableStateFlow<CameraDevice?>(null)
    val timeSyncDevice: StateFlow<CameraDevice?> = _timeSyncDevice

    private val _playbackDevice = MutableStateFlow<CameraDevice?>(null)
    val playbackDevice: StateFlow<CameraDevice?> = _playbackDevice

    private val _showDefaultCredentialsDialog = MutableStateFlow(false)
    val showDefaultCredentialsDialog: StateFlow<Boolean> = _showDefaultCredentialsDialog

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage

    // Site Projects
    private val _siteProjects = MutableStateFlow<List<SiteProject>>(emptyList())
    val siteProjects: StateFlow<List<SiteProject>> = _siteProjects.asStateFlow()

    init {
        scannerManager.refreshWifiInfo()
        fetchWanInfo()
        loadSiteProjects()
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

    fun openSettingsDialog(device: CameraDevice) {
        _settingsDevice.value = device
    }

    fun closeSettingsDialog() {
        _settingsDevice.value = null
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

    fun openLiveViewDialog(device: CameraDevice) {
        _liveViewDevice.value = device
    }

    fun closeLiveViewDialog() {
        _liveViewDevice.value = null
    }

    fun openPlaybackDialog(device: CameraDevice) {
        _playbackDevice.value = device
    }

    fun closePlaybackDialog() {
        _playbackDevice.value = null
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

    fun openEditProjectDialog(project: SiteProject?) {
        _editingProject.value = project
    }

    fun closeEditProjectDialog() {
        _editingProject.value = null
    }

    fun openTimeSyncDialog(device: CameraDevice) {
        _timeSyncDevice.value = device
    }

    fun closeTimeSyncDialog() {
        _timeSyncDevice.value = null
    }

    fun openDefaultCredentialsDialog() {
        _showDefaultCredentialsDialog.value = true
    }

    fun closeDefaultCredentialsDialog() {
        _showDefaultCredentialsDialog.value = false
    }

    fun updateTechnicianProfile(name: String, phone: String, company: String = "") {
        googleAuthManager.updateTechnicianProfile(name, phone, company)
    }

    fun updateDefaultCredentials(user: String, camPass: String, nvrPass: String) {
        googleAuthManager.updateDefaultCredentials(user, camPass, nvrPass)
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    /**
     * Finds the next clean/free sequential IP starting point in the current LAN subnet
     */
    fun getSuggestedFreeStartIp(): String {
        val baseIp = wifiInfo.value.ipAddress.ifEmpty { "192.168.1.100" }
        val parts = baseIp.split(".")
        if (parts.size != 4) return "192.168.1.200"
        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}."

        val occupiedOctets = devices.value.mapNotNull { dev ->
            val p = dev.ipv4Address.split(".")
            if (p.size == 4 && "${p[0]}.${p[1]}.${p[2]}." == prefix) p[3].toIntOrNull() else null
        }.toSet()

        // Check 200..240 first
        for (cand in 200..240) {
            if (!occupiedOctets.contains(cand)) {
                return "$prefix$cand"
            }
        }
        // Fallback to 100..199
        for (cand in 100..199) {
            if (!occupiedOctets.contains(cand)) {
                return "$prefix$cand"
            }
        }
        return "${prefix}200"
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
                kotlinx.coroutines.delay(1200)
                startScanning()
            }.onFailure { err ->
                _userMessage.value = "Lỗi: ${err.message}"
            }
        }
    }

    /**
     * Batch IP assignment for multiple cameras sequentially
     */
    fun applyBatchNetworkConfig(payloads: List<Pair<CameraDevice, NetworkConfigPayload>>) {
        viewModelScope.launch {
            if (payloads.isEmpty()) return@launch
            var successCount = 0
            var failCount = 0
            val total = payloads.size
            _userMessage.value = "Đang đổi IP hàng loạt cho $total thiết bị..."

            for ((device, payload) in payloads) {
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
                if (result.isSuccess) successCount++ else failCount++
            }

            _userMessage.value = "Đổi IP hàng loạt: $successCount thành công, $failCount thất bại"
            kotlinx.coroutines.delay(1200)
            startScanning()
        }
    }

    /**
     * Quick Time Sync to Phone Clock
     */
    fun syncDeviceTime(device: CameraDevice, password: String = "admin123") {
        viewModelScope.launch {
            _userMessage.value = "Đang đồng bộ giờ cho ${device.ipv4Address}..."
            val res = cameraConfigService.syncCameraTimeToPhone(device, "admin", password)
            res.onSuccess { msg ->
                _userMessage.value = "Đồng bộ giờ thành công: $msg"
            }.onFailure { err ->
                _userMessage.value = "Đồng bộ giờ thất bại: ${err.message}"
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

    // Site Projects management
    fun loadSiteProjects() {
        viewModelScope.launch {
            _siteProjects.value = siteProjectRepository.getAllProjects()
        }
    }

    fun saveSiteProject(project: SiteProject) {
        viewModelScope.launch {
            siteProjectRepository.saveProject(project)
            loadSiteProjects()
            _userMessage.value = "Đã lưu công trình: ${project.siteName}"
        }
    }

    fun deleteSiteProject(projectId: String) {
        viewModelScope.launch {
            siteProjectRepository.deleteProject(projectId)
            loadSiteProjects()
            _userMessage.value = "Đã xóa công trình"
        }
    }

    // Google Auth & Cloud Sync
    fun signInGoogle(email: String, name: String) {
        googleAuthManager.signIn(email, name)
        _userMessage.value = "Đã liên kết tài khoản Google: $email"
    }

    fun signOutGoogle() {
        googleAuthManager.signOut()
        _userMessage.value = "Đã đăng xuất tài khoản Google"
    }

    fun syncAllToGoogleCloud(): Intent? {
        var intent: Intent? = null
        viewModelScope.launch {
            val backupJson = siteProjectRepository.exportAllProjectsJson()
            googleAuthManager.markSynced()
            _userMessage.value = "Đã đồng bộ ${_siteProjects.value.size} dự án lên Google Cloud!"
        }
        val backupJson = runCatching {
            kotlinx.coroutines.runBlocking { siteProjectRepository.exportAllProjectsJson() }
        }.getOrNull()

        if (backupJson != null) {
            intent = googleAuthManager.shareBackupToGoogleDrive(backupJson)
        }
        return intent
    }

    fun restoreBackupFromJson(jsonStr: String) {
        viewModelScope.launch {
            val result = siteProjectRepository.importAllProjectsJson(jsonStr)
            result.onSuccess { count ->
                loadSiteProjects()
                googleAuthManager.markSynced()
                _userMessage.value = "Khôi phục thành công $count công trình từ bản sao lưu!"
            }.onFailure { err ->
                _userMessage.value = "Khôi phục thất bại: ${err.message}"
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

    companion object {
        fun ipToLong(ip: String): Long {
            val parts = ip.trim().split(".")
            if (parts.size != 4) return Long.MAX_VALUE
            return try {
                (parts[0].toLong() shl 24) or
                (parts[1].toLong() shl 16) or
                (parts[2].toLong() shl 8) or
                parts[3].toLong()
            } catch (_: Exception) {
                Long.MAX_VALUE
            }
        }
    }
}
