package com.scanipcamera.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scanipcamera.app.data.config.CameraConfigService
import com.scanipcamera.app.data.model.CameraDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimeSyncDialog(
    device: CameraDevice,
    currentUsername: String = "admin",
    currentPassword: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configService = remember { CameraConfigService() }

    var isCheckingTime by remember { mutableStateOf(false) }
    var isOperating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    var deviceTimeStr by remember { mutableStateOf<String?>(null) }
    var phoneTimeStr by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
    }
    var offsetSeconds by remember { mutableStateOf<Long?>(null) }

    var selectedNtpServer by remember { mutableStateOf("vn.pool.ntp.org") }

    val popularNtpServers = remember {
        listOf(
            "vn.pool.ntp.org" to "Việt Nam (Khuyên dùng)",
            "pool.ntp.org" to "NTP Toàn Cầu",
            "time.google.com" to "Google NTP",
            "time.windows.com" to "Microsoft NTP",
            "time.cloudflare.com" to "Cloudflare NTP",
            "asia.pool.ntp.org" to "Châu Á NTP"
        )
    }

    // Function to read device time and compute offset using credentials from main screen
    fun checkDeviceTime() {
        if (currentPassword.isBlank()) {
            errorMessage = "Vui lòng nhập mật khẩu trên màn hình chính để đồng bộ giờ thiết bị!"
            return
        }
        isCheckingTime = true
        errorMessage = null
        successMessage = null

        coroutineScope.launch {
            val phoneNow = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            phoneTimeStr = phoneNow

            val result = configService.getCurrentDeviceTime(device, currentUsername.trim(), currentPassword.trim())
            isCheckingTime = false

            if (result.isSuccess) {
                val dTime = result.getOrNull() ?: ""
                deviceTimeStr = dTime
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val devDate = sdf.parse(dTime)
                    val phoneDate = sdf.parse(phoneNow)
                    if (devDate != null && phoneDate != null) {
                        offsetSeconds = abs((phoneDate.time - devDate.time) / 1000)
                    }
                } catch (e: Exception) {
                    offsetSeconds = null
                }
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Không thể kết nối tới thiết bị!"
                deviceTimeStr = null
                offsetSeconds = null
            }
        }
    }

    // Auto check time immediately on open
    LaunchedEffect(Unit) {
        checkDeviceTime()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Đồng Bộ Giờ & Cấu Hình NTP",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "${device.brand.displayName} • ${device.ipv4Address}:${device.httpPort} (${device.model.ifEmpty { "Camera IP" }})",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Credentials source badge (Taken directly from main screen)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Xác thực: $currentUsername (Mật khẩu lấy từ màn hình chính)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { checkDeviceTime() },
                                modifier = Modifier.size(28.dp),
                                enabled = !isCheckingTime
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Kiểm tra lại", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Loading Indicator
                    if (isCheckingTime) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Đang kết nối & đọc giờ thực tế từ thiết bị...", fontSize = 12.sp)
                            }
                        }
                    }

                    // Error Alert Banner
                    if (errorMessage != null && !isCheckingTime) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = errorMessage!!,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = "💡 Lưu ý: Hãy đảm bảo bạn đã điền đúng mật khẩu của thiết bị ở thanh đầu màn hình chính.",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                                Button(
                                    onClick = { checkDeviceTime() },
                                    modifier = Modifier.align(Alignment.End).height(32.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Thử kết nối lại", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Success Alert Banner
                    if (successMessage != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = successMessage!!,
                                    color = Color(0xFF047857),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Dual Clock Comparison Display (Camera Clock vs Phone Clock)
                    if (deviceTimeStr != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "SO SÁNH THỜI GIAN THỰC TẾ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Camera Clock Card
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Giờ Camera / Đầu ghi", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = deviceTimeStr!!.substringAfter(" ", deviceTimeStr!!),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = deviceTimeStr!!.substringBefore(" "),
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Phone Clock Card
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Smartphone, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Giờ Điện Thoại (GMT+7)", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = phoneTimeStr.substringAfter(" ", phoneTimeStr),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF059669)
                                            )
                                            Text(
                                                text = phoneTimeStr.substringBefore(" "),
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Offset Badge & Sync Decision
                                if (offsetSeconds != null) {
                                    val offset = offsetSeconds!!
                                    if (offset <= 5) {
                                        // Accurate
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = "✓ Thời gian thiết bị ĐÃ CHUẨN XÁC (Lệch ${offset}s)",
                                                        color = Color(0xFF047857),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                    Text(
                                                        text = "Giờ camera khớp hoàn toàn với điện thoại. Bạn không cần phải đồng bộ lại!",
                                                        color = Color(0xFF065F46),
                                                        fontSize = 10.5.sp
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // Out of sync
                                        val minutes = offset / 60
                                        val seconds = offset % 60
                                        val diffStr = if (minutes > 0) "$minutes phút $seconds giây" else "$seconds giây"

                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFEF4444).copy(alpha = 0.12f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = "⚠️ Thiết bị đang lệch $diffStr",
                                                        color = Color(0xFFB91C1C),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                    Text(
                                                        text = "Thời gian sai lệch có thể khiến đầu ghi lưu sai mốc thời gian hoặc khó tra cứu video sự kiện.",
                                                        color = Color(0xFF991B1B),
                                                        fontSize = 10.5.sp
                                                    )
                                                }
                                            }
                                        }

                                        // Big Sync Button
                                        Button(
                                            onClick = {
                                                isOperating = true
                                                errorMessage = null
                                                successMessage = null
                                                coroutineScope.launch {
                                                    val res = configService.syncCameraTimeToPhone(device, currentUsername.trim(), currentPassword.trim())
                                                    isOperating = false
                                                    if (res.isSuccess) {
                                                        successMessage = res.getOrNull()
                                                        delay(500)
                                                        checkDeviceTime()
                                                    } else {
                                                        errorMessage = res.exceptionOrNull()?.message ?: "Lỗi đồng bộ giờ!"
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().height(42.dp),
                                            enabled = !isOperating,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            if (isOperating) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Đang đồng bộ giờ...", fontSize = 12.sp)
                                            } else {
                                                Icon(Icons.Default.Update, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("ĐỒNG BỘ THEO GIỜ ĐIỆN THOẠI NGAY", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // NTP Server Configuration
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "CẤU HÌNH MÁY CHỦ THỜI GIAN NTP",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Thiết bị sẽ tự động đồng bộ giờ chuẩn liên tục qua Internet",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = selectedNtpServer,
                                onValueChange = { selectedNtpServer = it },
                                label = { Text("Địa chỉ Máy chủ NTP (NTP Server)") },
                                placeholder = { Text("vn.pool.ntp.org") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp)
                            )

                            // List of popular NTP chips
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Các máy chủ NTP phổ biến & ổn định nhất:",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    popularNtpServers.forEach { (host, label) ->
                                        FilterChip(
                                            selected = selectedNtpServer == host,
                                            onClick = { selectedNtpServer = host },
                                            label = {
                                                Text(
                                                    text = "$host ($label)",
                                                    fontSize = 10.sp,
                                                    fontWeight = if (selectedNtpServer == host) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            modifier = Modifier.height(28.dp)
                                        )
                                    }
                                }
                            }

                            // Notice for GMT+7
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Múi giờ cài đặt mặc định: GMT+7 (Hà Nội, Băng Cốc, Jakarta). Chu kỳ cập nhật: 60 phút.",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Save NTP Button
                            Button(
                                onClick = {
                                    if (currentPassword.isBlank()) {
                                        errorMessage = "Vui lòng nhập mật khẩu trên màn hình chính để lưu cấu hình NTP!"
                                        return@Button
                                    }
                                    isOperating = true
                                    errorMessage = null
                                    successMessage = null
                                    coroutineScope.launch {
                                        val res = configService.configureNtpServer(
                                            device = device,
                                            ntpServer = selectedNtpServer.trim(),
                                            port = 123,
                                            user = currentUsername.trim(),
                                            pass = currentPassword.trim()
                                        )
                                        isOperating = false
                                        if (res.isSuccess) {
                                            successMessage = res.getOrNull()
                                            Toast.makeText(context, "Đã lưu cài đặt NTP thành công!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            errorMessage = res.exceptionOrNull()?.message ?: "Lỗi cấu hình NTP!"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                enabled = !isOperating && selectedNtpServer.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isOperating) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Đang lưu cấu hình NTP...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("LƯU CẤU HÌNH NTP CHO THIẾT BỊ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Bottom Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Đóng")
                    }
                }
            }
        }
    }
}
