package com.scanipcamera.app.data.model

data class SiteProject(
    val id: String,
    val siteName: String,
    val customerName: String,
    val customerPhone: String,
    val address: String,
    val technicianName: String = "Nguyễn Toàn Thắng",
    val technicianPhone: String = "0965620086",
    val createdAt: Long = System.currentTimeMillis(),
    val wanIp: String = "",
    val wifiSsid: String = "",
    val devices: List<CameraDevice> = emptyList()
)
