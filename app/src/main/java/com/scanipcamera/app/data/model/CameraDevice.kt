package com.scanipcamera.app.data.model

enum class DeviceCategory(val displayName: String) {
    CAMERA("Camera IP"),
    NVR("Đầu ghi NVR"),
    DVR("Đầu ghi DVR / XVR"),
    OTHER("Thiết bị mạng")
}

data class CameraDevice(
    val id: String, // MAC address or UUID
    val brand: DeviceBrand,
    val category: DeviceCategory = DeviceCategory.CAMERA,
    val channelCount: Int = 0, // e.g. 4, 8, 16, 32, 64 (0 for standard camera)
    val model: String,
    val serialNumber: String = "",
    val macAddress: String,
    val ipv4Address: String,
    val ipv4SubnetMask: String = "255.255.255.0",
    val ipv4Gateway: String = "",
    val dns1: String = "8.8.8.8",
    val dns2: String = "8.8.4.4",
    val isDhcpEnabled: Boolean = false,
    val isActivated: Boolean = true,
    val httpPort: Int = 80,
    val sdkPort: Int = 8000, // Hikvision: 8000, Dahua: 37777
    val rtspPort: Int = 554,
    val firmwareVersion: String = "",
    val rawProtocol: String = "UNKNOWN", // SADP, DHIP, ONVIF, PING/PORT
    val customName: String = "",
    val installLocation: String = "", // e.g. "Cổng chính", "Phòng khách"
    val handoverUser: String = "admin",
    val handoverPass: String = "admin123",
    val verifiedOpenPorts: Set<Int> = emptySet()
) {
    companion object {
        fun detectCategoryAndChannels(model: String, description: String = ""): Pair<DeviceCategory, Int> {
            val text = ("$model $description").uppercase()

            // 0. Explicit non-CCTV devices (Routers, PCs, Phones, Printers, TVs, Gateways, NAS, Servers, VMs)
            val isExplicitOther = text.contains("ROUTER") ||
                    text.contains("GATEWAY") ||
                    text.contains("MODEM") ||
                    text.contains("SWITCH") ||
                    text.contains("ACCESS POINT") ||
                    (text.contains(" AP") && (text.contains("WIFI") || text.contains("WIRELESS"))) ||
                    text.contains("PRINTER") ||
                    text.contains("WINDOWS") ||
                    text.contains("DESKTOP") ||
                    text.contains("LAPTOP") ||
                    text.contains("ANDROID") ||
                    text.contains("IPHONE") ||
                    text.contains("SYNOLOGY") ||
                    text.contains("QNAP") ||
                    text.contains("SERVER") ||
                    text.contains("MÁY CHỦ") ||
                    text.contains("UVICORN") ||
                    text.contains("NGINX") ||
                    text.contains("APACHE") ||
                    text.contains("CAMDDNS") ||
                    text.contains("VIRTUAL") ||
                    text.contains("VMWARE") ||
                    text.contains("PROXMOX") ||
                    text.contains("THIẾT BỊ MẠNG") ||
                    text.contains("NETWORK DEVICE")

            if (isExplicitOther) {
                return Pair(DeviceCategory.OTHER, 0)
            }

            // 1. Check for NVR (Network Video Recorder)
            val isNvr = text.contains("NVR") ||
                    text.contains("EMBEDDED NET NVR") ||
                    text.contains("NETWORK VIDEO RECORDER") ||
                    Regex("DS-[789]\\d{3}N").containsMatchIn(text) ||
                    Regex("(?:DHI-|DH-|KX-)NVR").containsMatchIn(text) ||
                    Regex("NVR\\d{3,4}").containsMatchIn(text) ||
                    text.contains("NET NVR")

            // 2. Check for DVR / XVR / AHD / Turbo HD (Digital Video Recorder)
            val isDvr = text.contains("DVR") ||
                    text.contains("XVR") ||
                    text.contains("HVR") ||
                    text.contains("HCVR") ||
                    text.contains("TURBO HD") ||
                    Regex("DS-7[123]\\d{2}[H|F]").containsMatchIn(text) ||
                    Regex("(?:DHI-|DH-|KX-)XVR").containsMatchIn(text) ||
                    Regex("(?:DHI-|DH-|KX-)DVR").containsMatchIn(text) ||
                    Regex("KX-[78]\\d{3}T").containsMatchIn(text) ||
                    Regex("XVR\\d{4}").containsMatchIn(text)

            // 3. Check for IP Camera indicators
            val isCamera = text.contains("CAMERA") ||
                    text.contains("CAM") ||
                    text.contains("IPC") ||
                    text.contains("ONVIF") ||
                    text.contains("IMOU") ||
                    text.contains("LECHANGE") ||
                    text.contains("RANGER") ||
                    text.contains("CRUISER") ||
                    text.contains("CUE") ||
                    text.contains("PAJA") ||
                    text.contains("BULLET") ||
                    text.contains("DOME") ||
                    text.contains("TURRET") ||
                    text.contains("PTZ") ||
                    text.contains("SPEED DOME") ||
                    text.contains("CUBE") ||
                    Regex("DS-2C[DVE]").containsMatchIn(text) ||
                    Regex("DS-2DE").containsMatchIn(text) ||
                    Regex("(?:DHI-|DH-|KX-)IPC").containsMatchIn(text) ||
                    Regex("IPC-[A-Z0-9]+").containsMatchIn(text) ||
                    Regex("CS-[A-Z0-9]+").containsMatchIn(text) ||
                    Regex("IPC\\d{3,4}").containsMatchIn(text) ||
                    Regex("(?:\\b|[_-])[4-9][D|H|L|K][0-9A-Z]{5,}", RegexOption.IGNORE_CASE).containsMatchIn(text)

            val category = when {
                isNvr -> DeviceCategory.NVR
                isDvr -> DeviceCategory.DVR
                isCamera -> DeviceCategory.CAMERA
                else -> {
                    // Check if known CCTV brand from text
                    val brand = DeviceBrand.fromModelOrVendor(text)
                    if (brand != DeviceBrand.UNKNOWN) DeviceCategory.CAMERA else DeviceCategory.OTHER
                }
            }

            // Extract channel count
            var channels = 0
            if (category == DeviceCategory.NVR || category == DeviceCategory.DVR) {
                // Match common channel suffixes: e.g. -04, -08, -16, -32, -64 or 7608, 4116
                val chMatch1 = Regex("(?:NI|HQHI|HUHI|NVR|XVR|DVR|301-|302-)[A-Z0-9]*?(04|08|16|32|64)").find(text)
                val chMatch2 = Regex("(?:76|77|96|71|72|73|41|51|52)(04|08|16|32|64)").find(text)
                val chStr = chMatch1?.groups?.get(1)?.value ?: chMatch2?.groups?.get(1)?.value
                channels = chStr?.toIntOrNull() ?: 0

                // Explicit text like "8CH", "16CH", "32CH", "4CH", "8 Kênh"
                if (channels == 0) {
                    val chMatch3 = Regex("(\\d{1,2})\\s*(?:CH|CHANNEL|KÊNH)").find(text)
                    channels = chMatch3?.groups?.get(1)?.value?.toIntOrNull() ?: 0
                }
            }

            return Pair(category, channels)
        }
    }
}
