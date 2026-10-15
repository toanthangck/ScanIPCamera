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
    val warrantyMonths: Int = 24,
    val notes: String = "",
    val handoverChecklist: List<String> = listOf(
        "Đã cài đặt xem qua điện thoại cho chủ nhà",
        "Đã hướng dẫn xem lại video và đổi mật khẩu",
        "Đã kiểm tra góc nhìn ban ngày và hồng ngoại ban đêm",
        "Đã bàn giao đầy đủ nguồn và phụ kiện"
    ),
    val devices: List<CameraDevice> = emptyList(),
    val lastSyncTime: Long = 0L
)
