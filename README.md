# ScanIPCamera (CamConfig / SADP Tool for Mobile)

Ứng dụng Android (Kotlin + Jetpack Compose) chuyên dụng cho kỹ thuật viên camera và thiết bị thông minh, hỗ trợ quét dò tìm và cấu hình IP hàng loạt mà không cần máy tính.

---

## 🎯 Tính năng chính

1. **Quét thiết bị đa giao thức đồng thời:**
   - **Hikvision / EZVIZ**: Giao thức **SADP** qua UDP `37020` (Multicast `239.255.255.250` & Broadcast).
   - **Dahua / IMOU**: Giao thức **DHIP / ConfigTool** qua UDP `37810` (Multicast `239.255.255.251` & Broadcast).
   - **ONVIF**: Chuẩn **WS-Discovery** qua UDP `3702` (phát hiện tất cả camera hỗ trợ ONVIF: Uniview, VIGI, Axis, Hanwha,...).
2. **Cấu hình mạng (IP / Subnet / Gateway / Port):**
   - Đặt lại IP tĩnh (Static IP) hoặc bật **DHCP** tự nhận IP.
   - Hỗ trợ đổi IP ngay cả khi thiết bị đang khác dải mạng (subnet) với điện thoại.
   - Nhập mật khẩu xác thực trực tiếp.
3. **Kích hoạt camera mới (Inactivated / Uninitialized):**
   - Đặt mật khẩu quản trị ban đầu cho camera mới bóc hộp trực tiếp trên điện thoại.
4. **Mở nhanh Web GUI:**
   - Mở giao diện cài đặt web của camera qua trình duyệt chỉ với 1 chạm.

---

## 🛠️ Cấu trúc dự án

```text
ScanIPCamera/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/scanipcamera/app/
│       │   ├── MainActivity.kt
│       │   ├── ScanCamApp.kt
│       │   ├── data/
│       │   │   ├── model/
│       │   │   │   ├── CameraDevice.kt
│       │   │   │   ├── DeviceBrand.kt
│       │   │   │   └── NetworkConfigPayload.kt
│       │   │   ├── network/
│       │   │   │   └── NetworkUtils.kt (Quản lý MulticastLock & Broadcast)
│       │   │   ├── scanner/
│       │   │   │   ├── DeviceScannerManager.kt (Điều phối quét đa luồng)
│       │   │   │   ├── HikvisionSADPScanner.kt (UDP 37020)
│       │   │   │   ├── DahuaDiscoveryScanner.kt (UDP 37810)
│       │   │   │   └── OnvifWsDiscoveryScanner.kt (UDP 3702)
│       │   │   └── config/
│       │   │       ├── HikvisionConfigurator.kt (SADP modify & activate)
│       │   │       └── DahuaConfigurator.kt (JSON client.modify)
│       │   └── ui/
│       │       ├── theme/ (Color, Theme, Type)
│       │       ├── viewmodel/ScannerViewModel.kt
│       │       ├── components/ (DeviceCard, ConfigDialog, ActivationDialog)
│       │       └── screens/ScannerScreen.kt
│       └── res/ (strings, colors, themes, network_security_config)
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 🚀 Hướng dẫn mở và chạy dự án

1. **Mở bằng Android Studio:**
   - Mở **Android Studio** (phiên bản Iguana, Jellyfish, Koala hoặc mới hơn).
   - Chọn **Open** và trỏ đến thư mục `/root/ScanIPCamera` (hoặc thư mục bạn đã tải về).
   - Đợi Gradle Sync hoàn tất.

2. **Cắm điện thoại thật hoặc máy ảo (Emulator):**
   - **Khuyến nghị**: Nên chạy trên **điện thoại Android thật** kết nối cùng mạng Wi-Fi với Camera/Đầu ghi (do máy ảo Android Emulator chạy qua dải NAT riêng `10.0.2.x`, thường bị hạn chế nhận gói UDP Multicast/Broadcast từ mạng LAN nội bộ).

3. **Quyền hạn cần cấp:**
   - Quyền Wi-Fi & Multicast (`CHANGE_WIFI_MULTICAST_STATE`).
   - Quyền Vị trí / Thiết bị gần đó (`ACCESS_FINE_LOCATION` / `NEARBY_WIFI_DEVICES`) theo quy định của Android để đọc thông tin SSID/Subnet mạng Wi-Fi.
