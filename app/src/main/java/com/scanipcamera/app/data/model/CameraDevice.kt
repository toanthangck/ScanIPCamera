package com.scanipcamera.app.data.model

data class CameraDevice(
    val id: String, // Typically MAC address or UUID
    val brand: DeviceBrand,
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
    val rawProtocol: String = "UNKNOWN" // SADP, DHIP, ONVIF
)
