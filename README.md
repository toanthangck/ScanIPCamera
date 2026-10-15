# ScanIPCamera (CamConfig / SADP Tool for Mobile)

Ứng dụng Android (Kotlin + Jetpack Compose Material 3) chuyên dụng cho kỹ thuật viên CCTV, camera an ninh và mạng thông minh. Hỗ trợ quét dò tìm, chuẩn đoán, trích xuất luồng RTSP và cấu hình IP hàng loạt trực tiếp trên điện thoại mà không cần mang theo laptop.

---

## 🎯 Tính năng toàn diện

### 1. 🔍 Hai chế độ quét mạng linh hoạt
- **Chế độ 1: Đa giao thức đồng thời (UDP Multicast & Broadcast):**
  - **Hikvision / EZVIZ / HiLook**: Giao thức **SADP** qua UDP `37020` (`239.255.255.250` & Broadcast).
  - **Dahua / IMOU / KBVISION**: Giao thức **DHIP / ConfigTool** qua UDP `37810` (`239.255.255.251` & Broadcast).
  - **Chuẩn ONVIF**: Chuẩn **WS-Discovery** qua UDP `3702` (nhận diện Uniview, VIGI, Axis, Hanwha,...).
- **Chế độ 2: Quét Ping & Port dải IP (1..254):**
  - Quét kiểm tra toàn bộ 254 host trong dải mạng nội bộ với Coroutine đa luồng siêu tốc.
  - Tự động dò các cổng CCTV đặc thù: `80` (HTTP), `554` (RTSP), `8000` (Hikvision SDK), `37777` (Dahua TCP), `8899` (Xiongmai XMeye), `5000` (Uniview).
  - Đọc bảng ARP nhân Linux (`/proc/net/arp`) để lấy địa chỉ MAC ngay cả khi thiết bị chặn Broadcast.

### 2. ⚡ Cấu hình mạng & Xử lý lệch dải mạng (Subnet Mismatch)
- Hiển thị thanh trạng thái Wi-Fi hiện tại (Tên Wi-Fi, IP điện thoại, Gateway, Subnet Mask).
- Tự động cảnh báo nhãn cam **"Khác dải IP ⚠️"** khi camera nằm ngoài dải mạng của điện thoại (ví dụ: điện thoại `192.168.1.x` nhưng camera mới bóc hộp `192.168.0.64`).
- Nút tính năng nhanh **"⚡ Đổi cùng lớp mạng Wi-Fi"**: Tự động điền Gateway, Subnet và đề xuất IP khả dụng trong dải mạng hiện tại, tiết kiệm thời gian gõ phím.
- Hỗ trợ đổi IP tĩnh hoặc bật DHCP tự động nhận IP kèm xác thực mật khẩu admin.

### 3. 🔓 Kích hoạt camera mới (Hikvision & Dahua / IMOU)
- Khởi tạo mật khẩu quản trị ban đầu cho camera mới xuất xưởng (Uninitialized / Inactivated) trực tiếp từ điện thoại.
- Hỗ trợ giao thức SADP Activate (Hikvision) và `client.initDevice` (Dahua/IMOU).

### 4. 🎥 Trích xuất luồng hình ảnh RTSP & Xem thử
- Tự động tạo link RTSP chuẩn hóa theo đúng thương hiệu:
  - **Hikvision / EZVIZ**: `rtsp://admin:pass@IP:554/Streaming/Channels/101` (Main) & `102` (Sub).
  - **Dahua / IMOU / KBVISION**: `rtsp://admin:pass@IP:554/cam/realmonitor?channel=1&subtype=0` & `subtype=1`.
  - **Uniview (UNV)**: `rtsp://admin:pass@IP:554/unicast/c1/s0/live`.
  - **Xiongmai / XMeye**: `rtsp://admin:pass@IP:554/user=...&channel=1&stream=0.sdp`.
  - **ONVIF tiêu chuẩn**: `rtsp://admin:pass@IP:554/onvif1` & `rtsp://admin:pass@IP:554/live/ch0`.
- Cho phép nhập tài khoản/mật khẩu, sao chép URL hoặc mở xem ngay bằng ứng dụng phát ngoài (VLC, MX Player).

### 5. 📡 Chuẩn đoán mạng (Ping Latency & Port Check)
- Đo độ trễ Ping (ms) kiểm tra camera có phản hồi ổn định hay bị chập chờn / rớt gói.
- Bảng kiểm tra trạng thái đóng/mở của các cổng dịch vụ quan trọng (HTTP 80, RTSP 554, SDK 8000, Dahua 37777, XM 8899).

### 6. 🔄 Khởi động lại thiết bị từ xa (Remote Reboot)
- Gửi lệnh reboot camera từ xa qua giao thức mạng mà không cần rút nguồn thiết bị.

### 7. 📋 Xuất & Chia sẻ danh sách thiết bị
- Định dạng xuất báo cáo chuyên nghiệp (Báo cáo Text hoặc File CSV).
- Chia sẻ 1 chạm qua Zalo, Telegram, Gmail, Drive hoặc sao chép vào bộ nhớ tạm phục vụ biên bản nghiệm thu bàn giao cho khách hàng.

### 8. 🔎 Tìm kiếm & Lọc theo hãng
- Tìm kiếm tức thời theo địa chỉ IP, địa chỉ MAC, Tên Model hoặc Số Serial.
- Lọc theo từng hãng: Tất cả, Hikvision, Dahua, EZVIZ, IMOU, Uniview, KBVISION, Xiongmai, ONVIF.

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
│       │   │   │   ├── NetworkConfigPayload.kt
│       │   │   │   └── RtspHelper.kt (Sinh link RTSP theo từng hãng)
│       │   │   ├── network/
│       │   │   │   ├── NetworkUtils.kt (WifiInfo, Subnet matching, MulticastLock)
│       │   │   │   └── NetworkDiagnosticUtils.kt (Kiểm tra Ping & Port status)
│       │   │   ├── scanner/
│       │   │   │   ├── DeviceScannerManager.kt (Điều phối quét đa giao thức & dải IP)
│       │   │   │   ├── HikvisionSADPScanner.kt (UDP 37020)
│       │   │   │   ├── DahuaDiscoveryScanner.kt (UDP 37810)
│       │   │   │   ├── OnvifWsDiscoveryScanner.kt (UDP 3702)
│       │   │   │   └── SubnetIpScanner.kt (Dò cổng 1..254 & đọc ARP table)
│       │   │   ├── config/
│       │   │   │   ├── HikvisionConfigurator.kt (SADP modify, activate, reboot)
│       │   │   │   └── DahuaConfigurator.kt (JSON client.modify, initDevice, reboot)
│       │   │   └── export/
│       │   │       └── DeviceExportHelper.kt (Xuất báo cáo nghiệm thu & Share Zalo)
│       │   └── ui/
│       │       ├── theme/ (Color, Theme, Type)
│       │       ├── viewmodel/ScannerViewModel.kt
│       │       ├── components/
│       │       │   ├── DeviceCard.kt (Thẻ thiết bị, cảnh báo lệch subnet)
│       │       │   ├── ConfigDialog.kt (Đổi IP + Nút gợi ý cùng lớp mạng)
│       │       │   ├── ActivationDialog.kt (Kích hoạt thiết bị)
│       │       │   ├── RtspDialog.kt (Xem và lấy link RTSP)
│       │       │   ├── DiagnosticDialog.kt (Bảng test Ping & Port)
│       │       │   └── RebootDialog.kt (Hộp thoại khởi động lại)
│       │       └── screens/ScannerScreen.kt (Màn hình chính, Banner Wi-Fi, Tìm kiếm)
│       └── res/ (drawable, mipmap, strings, colors, themes, network_security_config)
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
   - Mở **Android Studio** (Koala, Ladybug hoặc mới hơn).
   - Chọn **Open** và trỏ đến thư mục `/root/ScanIPCamera`.
   - Đợi Gradle Sync và tải dependencies hoàn tất.

2. **Chạy ứng dụng:**
   - Cắm điện thoại Android thật (bật USB Debugging).
   - Kết nối cùng mạng Wi-Fi với Camera / Đầu ghi hình.
   - Nhấn **Run (Shift + F10)** trên Android Studio.
