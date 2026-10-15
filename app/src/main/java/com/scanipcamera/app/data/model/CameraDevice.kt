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
    val rawProtocol: String = "UNKNOWN" // SADP, DHIP, ONVIF, PING/PORT
) {
    companion object {
        fun detectCategoryAndChannels(model: String, description: String = ""): Pair<DeviceCategory, Int> {
            val text = ("$model $description").uppercase()

            // 1. Check for NVR
            val isNvr = text.contains("NVR") ||
                    text.contains("EMBEDDED NET NVR") ||
                    text.contains("NETWORK VIDEO RECORDER") ||
                    Regex("DS-[789]\\d{3}N").containsMatchIn(text) ||
                    Regex("DHI-NVR|DH-NVR").containsMatchIn(text)

            // 2. Check for DVR / XVR / AHD / Turbo HD
            val isDvr = text.contains("DVR") ||
                    text.contains("XVR") ||
                    text.contains("HVR") ||
                    text.contains("HCVR") ||
                    text.contains("TURBO HD") ||
                    Regex("DS-7[123]\\d{2}H").containsMatchIn(text) ||
                    Regex("DHI-XVR|DH-XVR").containsMatchIn(text)

            val category = when {
                isNvr -> DeviceCategory.NVR
                isDvr -> DeviceCategory.DVR
                text.contains("SWITCH") || text.contains("ROUTER") -> DeviceCategory.OTHER
                else -> DeviceCategory.CAMERA
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
