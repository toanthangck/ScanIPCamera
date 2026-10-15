package com.scanipcamera.app.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scanipcamera.app.data.config.CameraConfigService
import com.scanipcamera.app.data.config.CameraImageSettings
import com.scanipcamera.app.data.config.CameraTimeSettings
import com.scanipcamera.app.data.config.CameraVideoSettings
import com.scanipcamera.app.data.model.CameraDevice
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraSettingsDialog(
    device: CameraDevice,
    defaultUser: String = "admin",
    defaultPass: String = "admin123",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configService = remember { CameraConfigService() }

    var user by remember { mutableStateOf(defaultUser) }
    var password by remember { mutableStateOf(defaultPass) }
    var isOperating by remember { mutableStateOf(false) }
    var operationResult by remember { mutableStateOf<String?>(null) }

    // Section states
    var expandedSection by remember { mutableStateOf<String?>("camera") } // "camera", "video", "ntp", "auth"

    // Image & Backlight states
    var brightness by remember { mutableFloatStateOf(50f) }
    var contrast by remember { mutableFloatStateOf(50f) }
    var saturation by remember { mutableFloatStateOf(50f) }
    var sharpness by remember { mutableFloatStateOf(50f) }
    var selectedWdrMode by remember { mutableStateOf("WDR") } // "Off", "WDR", "BLC", "HLC"
    var wdrLevel by remember { mutableFloatStateOf(50f) }
    var dayNightMode by remember { mutableStateOf("Auto") } // "Auto", "Color", "Night"
    var isMirror by remember { mutableStateOf(false) }
    var isFlip by remember { mutableStateOf(false) }

    // Video Resolution & Codec states
    var selectedResolution by remember { mutableStateOf("1920x1080") } // "2560x1440 (2K)", "1920x1080 (Full HD)", "1280x720 (HD)"
    var selectedCodec by remember { mutableStateOf("H.265") } // "H.265", "H.264", "H.265+"
    var selectedFps by remember { mutableIntStateOf(25) }

    // NTP & Time states
    var ntpServer by remember { mutableStateOf("pool.ntp.org") }
    var ntpEnabled by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top App Bar
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Cài đặt thiết bị",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "${device.model.ifEmpty { device.brand.displayName }} (${device.ipv4Address})",
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                )

                // Result status notification
                operationResult?.let { msg ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(msg, fontSize = 12.sp, color = Color(0xFF0284C7), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Scrollable Setting Sections
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Quick Action: Time Sync to Phone button
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0284C7).copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Đồng bộ giờ điện thoại", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Khắc phục lỗi camera về năm 1970/2000", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    isOperating = true
                                    coroutineScope.launch {
                                        val res = configService.syncCameraTimeToPhone(device, user, password)
                                        isOperating = false
                                        operationResult = res.getOrNull() ?: res.exceptionOrNull()?.message
                                        Toast.makeText(context, operationResult, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isOperating,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Đồng bộ ngay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // SECTION 1: CAMERA (HÌNH ẢNH, CHỐNG NGƯỢC SÁNG, XOAY LẬT)
                    SettingAccordionHeader(
                        title = "Camera & Hình ảnh",
                        subtitle = "Chống ngược sáng (WDR/BLC), Ngày - Đêm, Xoay lật",
                        icon = Icons.Default.CameraAlt,
                        isExpanded = expandedSection == "camera",
                        onClick = { expandedSection = if (expandedSection == "camera") null else "camera" }
                    )

                    AnimatedVisibility(visible = expandedSection == "camera") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // 1. Backlight / WDR mode
                                Text("Chế độ Ngược sáng (Backlight / WDR):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Tắt" to "Off", "WDR Thực" to "WDR", "Bù sáng BLC" to "BLC", "Chống lóa HLC" to "HLC").forEach { (label, value) ->
                                        FilterChip(
                                            selected = selectedWdrMode == value,
                                            onClick = { selectedWdrMode = value },
                                            label = { Text(label, fontSize = 10.5.sp) },
                                            modifier = Modifier.weight(1f).height(30.dp)
                                        )
                                    }
                                }

                                if (selectedWdrMode == "WDR") {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Mức độ WDR: ${wdrLevel.toInt()}%", fontSize = 11.sp, modifier = Modifier.width(110.dp))
                                        Slider(
                                            value = wdrLevel,
                                            onValueChange = { wdrLevel = it },
                                            valueRange = 0f..100f,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                // 2. Day / Night mode
                                Text("Chế độ Ngày - Đêm (Day / Night):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Tự động (Auto ICR)" to "Auto", "Màu sắc (Color)" to "Color", "Trắng đen (Night)" to "Night").forEach { (label, value) ->
                                        FilterChip(
                                            selected = dayNightMode == value,
                                            onClick = { dayNightMode = value },
                                            label = { Text(label, fontSize = 10.5.sp) },
                                            modifier = Modifier.weight(1f).height(30.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                // 3. Flip & Mirror
                                Text("Xoay - Đối xứng (Flip / Mirror):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = isMirror,
                                        onClick = { isMirror = !isMirror },
                                        label = { Text("Lật gương ngang (Mirror)", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f).height(32.dp)
                                    )
                                    FilterChip(
                                        selected = isFlip,
                                        onClick = { isFlip = !isFlip },
                                        label = { Text("Lật dọc (Flip 180°)", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f).height(32.dp)
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                // 4. Brightness & Contrast sliders
                                Text("Điều chỉnh hình ảnh cơ bản:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                SettingSliderRow(label = "Độ sáng", value = brightness, onValueChange = { brightness = it })
                                SettingSliderRow(label = "Độ tương phản", value = contrast, onValueChange = { contrast = it })
                                SettingSliderRow(label = "Độ bão hòa màu", value = saturation, onValueChange = { saturation = it })

                                Button(
                                    onClick = {
                                        isOperating = true
                                        coroutineScope.launch {
                                            val res = configService.applyBacklightAndImage(
                                                device, user, password,
                                                CameraImageSettings(
                                                    brightness = brightness.toInt(),
                                                    contrast = contrast.toInt(),
                                                    saturation = saturation.toInt(),
                                                    sharpness = sharpness.toInt(),
                                                    wdrMode = selectedWdrMode,
                                                    wdrLevel = wdrLevel.toInt(),
                                                    dayNightMode = dayNightMode,
                                                    mirror = isMirror,
                                                    flip = isFlip
                                                )
                                            )
                                            isOperating = false
                                            operationResult = res.getOrNull() ?: res.exceptionOrNull()?.message
                                            Toast.makeText(context, operationResult, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = !isOperating,
                                    modifier = Modifier.fillMaxWidth().height(38.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Áp dụng hình ảnh & Chống ngược sáng", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // SECTION 2: VIDEO (ĐỘ PHÂN GIẢI, FPS, CODEC H.265)
                    SettingAccordionHeader(
                        title = "Mã hóa & Độ phân giải",
                        subtitle = "Độ phân giải 2K/1080P, Chuẩn nén H.265/H.264, FPS",
                        icon = Icons.Default.Videocam,
                        isExpanded = expandedSection == "video",
                        onClick = { expandedSection = if (expandedSection == "video") null else "video" }
                    )

                    AnimatedVisibility(visible = expandedSection == "video") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Chọn độ phân giải:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("4MP / 2K (1440P)" to "2560x1440", "Full HD (1080P)" to "1920x1080", "HD (720P)" to "1280x720").forEach { (label, res) ->
                                        FilterChip(
                                            selected = selectedResolution == res,
                                            onClick = { selectedResolution = res },
                                            label = { Text(label, fontSize = 10.5.sp) },
                                            modifier = Modifier.weight(1f).height(32.dp)
                                        )
                                    }
                                }

                                Text("Chuẩn nén Video (Codec):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("H.265 (Tiết kiệm)" to "H.265", "H.264 (Tương thích)" to "H.264", "H.265+ (Smart)" to "H.265+").forEach { (label, codec) ->
                                        FilterChip(
                                            selected = selectedCodec == codec,
                                            onClick = { selectedCodec = codec },
                                            label = { Text(label, fontSize = 10.5.sp) },
                                            modifier = Modifier.weight(1f).height(30.dp)
                                        )
                                    }
                                }

                                Text("Tốc độ khung hình (Frame Rate):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(15, 20, 25, 30).forEach { fps ->
                                        FilterChip(
                                            selected = selectedFps == fps,
                                            onClick = { selectedFps = fps },
                                            label = { Text("$fps FPS", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f).height(30.dp)
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        isOperating = true
                                        coroutineScope.launch {
                                            val res = configService.applyVideoEncoding(
                                                device, user, password,
                                                CameraVideoSettings(
                                                    resolution = selectedResolution,
                                                    codec = selectedCodec,
                                                    fps = selectedFps
                                                )
                                            )
                                            isOperating = false
                                            operationResult = res.getOrNull() ?: res.exceptionOrNull()?.message
                                            Toast.makeText(context, operationResult, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = !isOperating,
                                    modifier = Modifier.fillMaxWidth().height(38.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Lưu độ phân giải & Chuẩn nén", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // SECTION 3: NTP & THỜI GIAN
                    SettingAccordionHeader(
                        title = "Thời gian & Máy chủ NTP",
                        subtitle = "Cấu hình NTP Server, Múi giờ chuẩn GMT+7",
                        icon = Icons.Default.Schedule,
                        isExpanded = expandedSection == "ntp",
                        onClick = { expandedSection = if (expandedSection == "ntp") null else "ntp" }
                    )

                    AnimatedVisibility(visible = expandedSection == "ntp") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = ntpServer,
                                    onValueChange = { ntpServer = it },
                                    label = { Text("Máy chủ NTP (NTP Server)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("pool.ntp.org", "time.google.com", "time.windows.com").forEach { srv ->
                                        AssistChip(
                                            onClick = { ntpServer = srv },
                                            label = { Text(srv, fontSize = 10.sp) },
                                            modifier = Modifier.weight(1f).height(28.dp)
                                        )
                                    }
                                }

                                Text("Múi giờ: GMT+7:00 (Bangkok, Hanoi, Jakarta)", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)

                                Button(
                                    onClick = {
                                        isOperating = true
                                        coroutineScope.launch {
                                            val res = configService.applyNtpConfig(
                                                device, user, password,
                                                CameraTimeSettings(
                                                    ntpServer = ntpServer,
                                                    ntpEnabled = true,
                                                    timezone = "GMT+7"
                                                )
                                            )
                                            isOperating = false
                                            operationResult = res.getOrNull() ?: res.exceptionOrNull()?.message
                                            Toast.makeText(context, operationResult, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = !isOperating,
                                    modifier = Modifier.fillMaxWidth().height(38.dp)
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Lưu cấu hình NTP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // SECTION 4: TÀI KHOẢN XÁC THỰC
                    SettingAccordionHeader(
                        title = "Tài khoản Camera",
                        subtitle = "Tài khoản và mật khẩu quản trị để gửi lệnh cài đặt",
                        icon = Icons.Default.Lock,
                        isExpanded = expandedSection == "auth",
                        onClick = { expandedSection = if (expandedSection == "auth") null else "auth" }
                    )

                    AnimatedVisibility(visible = expandedSection == "auth") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = user,
                                    onValueChange = { user = it },
                                    label = { Text("Tài khoản", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Mật khẩu", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Bottom Dismiss
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Text("Đóng cửa sổ cài đặt", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingAccordionHeader(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = subtitle, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "$label: ${value.toInt()}", fontSize = 11.sp, modifier = Modifier.width(110.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f)
        )
    }
}
