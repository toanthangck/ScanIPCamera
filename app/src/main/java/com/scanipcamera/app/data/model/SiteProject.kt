package com.scanipcamera.app.data.model

data class SiteProject(
    val id: String,
    val siteName: String,
    val customerName: String,
    val customerPhone: String,
    val address: String,
    val companyName: String = "ĐƠN VỊ THI CÔNG CAMERA",
    val technicianName: String = "Kỹ thuật viên",
    val technicianPhone: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val wanIp: String = "",
    val wifiSsid: String = "",
    val warrantyMonths: Int = 24,
    val notes: String = "",
    val handoverChecklist: List<String> = listOf(
        "Đã cài đặt phần mềm xem camera qua điện thoại cho gia chủ",
        "Đã hướng dẫn xem lại video và đổi mật khẩu an toàn",
        "Đã kiểm tra góc nhìn bao quát, rõ nét ban ngày và hồng ngoại ban đêm",
        "Đã cấu hình đồng bộ thời gian chuẩn NTP cho hệ thống",
        "Đã bàn giao đầy đủ nguồn adapter, phụ kiện & tài khoản đăng nhập"
    ),
    val devices: List<CameraDevice> = emptyList(),
    val lastSyncTime: Long = 0L
)
