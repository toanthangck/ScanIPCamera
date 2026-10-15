package com.scanipcamera.app.ui.components

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scanipcamera.app.data.config.CameraConfigService
import com.scanipcamera.app.data.config.CameraImageSettings
import com.scanipcamera.app.data.config.CameraVideoSettings
import com.scanipcamera.app.data.model.CameraDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraSettingsDialog(
    device: CameraDevice,
    currentUsername: String = "admin",
    currentPassword: String = "",
    onDismiss: () -> Unit,
    onUpdatePassword: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configService = remember { CameraConfigService() }

    var isOperating by remember { mutableStateOf(false) }
    var operationResult by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Chống ngược sáng & Màu sắc, 1: Độ phân giải & Video

    // Editable credentials inside dialog (initialized from master credentials)
    var user by remember { mutableStateOf(currentUsername.ifBlank { "admin" }) }
    var password by remember { mutableStateOf(currentPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedChannel by remember { mutableIntStateOf(1) }

    // Live Snapshot states
    var snapshotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingSnapshot by remember { mutableStateOf(false) }
    var snapshotError by remember { mutableStateOf<String?>(null) }
    var autoRefreshSnapshot by remember { mutableStateOf(false) }
    var lastSnapshotTime by remember { mutableStateOf("") }

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
    var selectedResolution by remember { mutableStateOf("1920x1080") }
    var selectedCodec by remember { mutableStateOf("H.265") }
    var selectedFps by remember { mutableIntStateOf(25) }

    // Function to load camera snapshot
    fun refreshSnapshot() {
        if (password.isBlank()) {
            snapshotError = "Vui lòng nhập mật khẩu camera/đầu ghi để tải hình ảnh!"
            return
        }
        isLoadingSnapshot = true
        snapshotError = null
        coroutineScope.launch {
            val res = configService.fetchCameraSnapshot(
                device = device,
                user = user.trim(),
                pass = password.trim(),
                channel = selectedChannel
            )
            isLoadingSnapshot = false
            if (res.isSuccess) {
                snapshotBitmap = res.getOrNull()
                lastSnapshotTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                snapshotError = null
            } else {
                snapshotError = res.exceptionOrNull()?.message ?: "Lỗi tải ảnh"
            }
        }
    }

    // Auto-refresh timer loop
    LaunchedEffect(autoRefreshSnapshot, user, password, selectedChannel) {
        while (autoRefreshSnapshot && password.isNotBlank()) {
            delay(2500)
            val res = configService.fetchCameraSnapshot(
                device = device,
                user = user.trim(),
                pass = password.trim(),
                channel = selectedChannel
            )
            if (res.isSuccess) {
                snapshotBitmap = res.getOrNull()
                lastSnapshotTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            }
        }
    }

    // Initial snapshot fetch
    LaunchedEffect(Unit) {
        refreshSnapshot()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 12.dp),
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
                                text = "Cài Đặt Hình Ảnh & Ngược Sáng",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.5.sp
                            )
                            Text(
                                text = "${device.brand.displayName} • ${device.ipv4Address}:${device.httpPort} (${device.model.ifEmpty { "Camera IP" }})",
                                fontSize = 11.sp,
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
                    actions = {
                        IconButton(onClick = { refreshSnapshot() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Làm mới ảnh")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                )

                // Result status notification
                operationResult?.let { msg ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(msg, fontSize = 11.5.sp, color = Color(0xFF0284C7), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // ==========================================
                // CREDENTIAL & CHANNEL BAR
                // ==========================================
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Title + sync note
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Xác thực thiết bị (Mặc định lấy từ màn hình chính):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (password.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Đã điền",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Input fields: User, Password, Load Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Username
                            OutlinedTextField(
                                value = user,
                                onValueChange = { user = it },
                                label = { Text("Tài khoản", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            )

                            // Password with Visibility Toggle
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Mật khẩu", fontSize = 10.sp) },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(
                                        onClick = { passwordVisible = !passwordVisible },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            )

                            // Load Snapshot Button
                            Button(
                                onClick = {
                                    refreshSnapshot()
                                    onUpdatePassword?.invoke(password.trim())
                                },
                                modifier = Modifier.height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Tải ảnh", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Quick Password Chips Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("Mẫu:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            listOf("Admin@123456", "admin12345", "admin123", "123456", "admin").forEach { samplePass ->
                                Surface(
                                    onClick = {
                                        password = samplePass
                                        onUpdatePassword?.invoke(samplePass)
                                        refreshSnapshot()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (password == samplePass) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        0.8.dp,
                                        if (password == samplePass) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = samplePass,
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = if (password == samplePass) FontWeight.Bold else FontWeight.Normal,
                                            color = if (password == samplePass) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Channel Selector Chips (Kênh 1, Kênh 2, Kênh 3, Kênh 4)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Kênh:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            listOf(1, 2, 3, 4).forEach { ch ->
                                val isSelected = (selectedChannel == ch)
                                Surface(
                                    onClick = {
                                        selectedChannel = ch
                                        refreshSnapshot()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        0.8.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Kênh $ch",
                                            fontSize = 9.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ==========================================
                    // 1. LIVE CAMERA VIEWFINDER / SNAPSHOT BOX
                    // ==========================================
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B132B)),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF1E293B))
                    ) {
                        Column {
                            // Viewfinder Screen
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .background(Color(0xFF020617)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (snapshotBitmap != null) {
                                    Image(
                                        bitmap = snapshotBitmap!!.asImageBitmap(),
                                        contentDescription = "Live Camera Snapshot",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (isLoadingSnapshot) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = Color(0xFF00E5FF),
                                            strokeWidth = 2.8.dp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Đang kết nối camera & tải ảnh trực tiếp...",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF00E5FF)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${device.ipv4Address}:${device.httpPort} • Kênh $selectedChannel",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (snapshotError?.contains("401") == true) Icons.Default.Lock else Icons.Default.VideocamOff,
                                            contentDescription = null,
                                            tint = if (snapshotError?.contains("401") == true) Color(0xFFEF4444) else Color(0xFF94A3B8),
                                            modifier = Modifier.size(38.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = snapshotError ?: "Chưa có hình ảnh trực tiếp từ camera",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (snapshotError?.contains("401") == true) Color(0xFFFCA5A5) else Color(0xFFE2E8F0),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        )
                                        if (snapshotError?.contains("401") == true) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Gợi ý: Hãy bấm các nút mật khẩu mẫu ở trên hoặc gõ mật khẩu đúng",
                                                fontSize = 10.sp,
                                                color = Color(0xFFFCD34D),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = { refreshSnapshot() },
                                            modifier = Modifier.height(34.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Thử tải lại ảnh", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Overlay: Top Badges
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .background(Color(0x99000000))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (snapshotBitmap != null) Color(0xFFEF4444) else Color(0xFF64748B),
                                            modifier = Modifier.size(8.dp)
                                        ) {}
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (lastSnapshotTime.isNotEmpty()) "TRỰC TIẾP • $lastSnapshotTime (Kênh $selectedChannel)" else "HÌNH ẢNH CAMERA",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // Auto refresh toggle
                                        Surface(
                                            onClick = { autoRefreshSnapshot = !autoRefreshSnapshot },
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (autoRefreshSnapshot) Color(0xFF10B981) else Color(0x66FFFFFF),
                                            modifier = Modifier.height(22.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (autoRefreshSnapshot) Icons.Default.Check else Icons.Default.Bolt,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = if (autoRefreshSnapshot) "Auto 2s: Bật" else "Auto: Tắt",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        // Manual refresh
                                        Surface(
                                            onClick = { refreshSnapshot() },
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0x66FFFFFF),
                                            modifier = Modifier.height(22.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text("Làm mới", fontSize = 9.5.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }

                                // Overlay: Bottom IP Bar (Only visible when image is loaded)
                                if (snapshotBitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .background(Color(0x99000000))
                                            .padding(horizontal = 10.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "IP: ${device.ipv4Address} • Kênh $selectedChannel • WDR: $selectedWdrMode • Màu: $dayNightMode • Sáng: ${brightness.toInt()}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFFE2E8F0)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Navigation Tabs (2 Tabs: Ngược Sáng & Màu sắc, Độ Phân Giải)
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Ngược Sáng & Màu", fontSize = 11.5.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Độ Phân Giải", fontSize = 11.5.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.Hd, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }

                    // TAB 0: CHỐNG NGƯỢC SÁNG & ĐIỀU CHỈNH HÌNH ẢNH
                    if (selectedTab == 0) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 1. Backlight / WDR Mode (Redesigned with spacious 2x2 grid)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Chế độ Ngược sáng (Backlight / WDR):",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    val wdrOptions = listOf(
                                        Triple("Tắt (Off)", "Tiêu chuẩn ban ngày", "Off"),
                                        Triple("WDR Thực", "Chống ngược sáng", "WDR"),
                                        Triple("Bù sáng BLC", "Tăng sáng trung tâm", "BLC"),
                                        Triple("Chống lóa HLC", "Giảm lóa đèn pha", "HLC")
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            wdrOptions.take(2).forEach { (title, subtitle, value) ->
                                                val isSelected = selectedWdrMode == value
                                                Surface(
                                                    onClick = { selectedWdrMode = value },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        1.2.dp,
                                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                    ),
                                                    modifier = Modifier.weight(1f).height(46.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        RadioButton(
                                                            selected = isSelected,
                                                            onClick = { selectedWdrMode = value },
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Column {
                                                            Text(
                                                                text = title,
                                                                fontSize = 11.sp,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text(
                                                                text = subtitle,
                                                                fontSize = 9.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            wdrOptions.drop(2).forEach { (title, subtitle, value) ->
                                                val isSelected = selectedWdrMode == value
                                                Surface(
                                                    onClick = { selectedWdrMode = value },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        1.2.dp,
                                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                    ),
                                                    modifier = Modifier.weight(1f).height(46.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        RadioButton(
                                                            selected = isSelected,
                                                            onClick = { selectedWdrMode = value },
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Column {
                                                            Text(
                                                                text = title,
                                                                fontSize = 11.sp,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text(
                                                                text = subtitle,
                                                                fontSize = 9.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // WDR Level Slider
                                    if (selectedWdrMode == "WDR") {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("Cường độ chống ngược sáng (WDR):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(2.dp)
                                                    ) {
                                                        Text(
                                                            text = "${wdrLevel.toInt()}%",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                Slider(
                                                    value = wdrLevel,
                                                    onValueChange = { wdrLevel = it },
                                                    valueRange = 0f..100f,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    listOf(25f, 50f, 75f, 100f).forEach { step ->
                                                        SuggestionChip(
                                                            onClick = { wdrLevel = step },
                                                            label = { Text("${step.toInt()}%", fontSize = 9.5.sp) },
                                                            modifier = Modifier.height(24.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                // 2. Day / Night Mode (Redesigned with comfortable 42dp height)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Chế độ Ngày - Đêm (Day / Night):",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    val dayNightOptions = listOf(
                                        Triple("Tự động (ICR)", Icons.Default.BrightnessAuto, "Auto"),
                                        Triple("Màu sắc (Color)", Icons.Default.Palette, "Color"),
                                        Triple("Trắng đen (Night)", Icons.Default.NightsStay, "Night")
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        dayNightOptions.forEach { (label, icon, value) ->
                                            val isSelected = dayNightMode == value
                                            Surface(
                                                onClick = { dayNightMode = value },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.2.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                                                ),
                                                modifier = Modifier.weight(1f).height(44.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = null,
                                                        tint = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = label,
                                                        fontSize = 10.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                // 3. Flip & Mirror Toggles
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Xoay - Đối xứng (Flip / Mirror):",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            onClick = { isMirror = !isMirror },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isMirror) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isMirror) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            modifier = Modifier.weight(1f).height(38.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isMirror,
                                                    onCheckedChange = { isMirror = it },
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Lật gương (Mirror)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }

                                        Surface(
                                            onClick = { isFlip = !isFlip },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isFlip) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isFlip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            modifier = Modifier.weight(1f).height(38.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isFlip,
                                                    onCheckedChange = { isFlip = it },
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Lật dọc (Flip 180°)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                // 4. Basic Image Sliders (Brightness, Contrast, Saturation, Sharpness)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Điều chỉnh hình ảnh cơ bản:",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        TextButton(
                                            onClick = {
                                                brightness = 50f
                                                contrast = 50f
                                                saturation = 50f
                                                sharpness = 50f
                                            },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("Về chuẩn (50)", fontSize = 10.5.sp)
                                        }
                                    }

                                    ProfessionalSliderRow(label = "Độ sáng (Brightness)", value = brightness, onValueChange = { brightness = it })
                                    ProfessionalSliderRow(label = "Độ tương phản (Contrast)", value = contrast, onValueChange = { contrast = it })
                                    ProfessionalSliderRow(label = "Độ bão hòa (Saturation)", value = saturation, onValueChange = { saturation = it })
                                    ProfessionalSliderRow(label = "Độ sắc nét (Sharpness)", value = sharpness, onValueChange = { sharpness = it })
                                }

                                // Apply Button with Instant Live Preview Feedback
                                Button(
                                    onClick = {
                                        isOperating = true
                                        operationResult = null
                                        coroutineScope.launch {
                                            val res = configService.applyBacklightAndImage(
                                                device, user.trim(), password.trim(),
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
                                            if (res.isSuccess) {
                                                operationResult = res.getOrNull()
                                                Toast.makeText(context, "Đã lưu cài đặt! Đang làm mới khung hình...", Toast.LENGTH_SHORT).show()
                                                delay(500)
                                                refreshSnapshot()
                                            } else {
                                                operationResult = res.exceptionOrNull()?.message
                                            }
                                        }
                                    },
                                    enabled = !isOperating,
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isOperating) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Đang lưu & cập nhật hình ảnh...", fontSize = 12.sp)
                                    } else {
                                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("ÁP DỤNG & XEM KẾT QUẢ TRỰC TIẾP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // TAB 1: ĐỘ PHÂN GIẢI & MÃ HÓA VIDEO
                    if (selectedTab == 1) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "ĐỘ PHÂN GIẢI & TỐC ĐỘ KHUNG HÌNH (FPS):",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        "2560x1440" to "2K QHD (4 Megapixel) - Nét cao",
                                        "1920x1080" to "Full HD 1080P (2 Megapixel) - Phổ biến",
                                        "1280x720" to "HD 720P (1 Megapixel) - Tiết kiệm dung lượng"
                                    ).forEach { (res, desc) ->
                                        Surface(
                                            onClick = { selectedResolution = res },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (selectedResolution == res) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (selectedResolution == res) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            modifier = Modifier.fillMaxWidth().height(42.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = selectedResolution == res,
                                                    onClick = { selectedResolution = res },
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text = "$res ($desc)", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "CHUẨN NÉN HÌNH ẢNH (CODEC):",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("H.265 (Smart)", "H.264 (Cơ bản)", "H.265+ (Siêu nén)").forEach { codec ->
                                        val cVal = codec.substringBefore(" ")
                                        val isSelected = selectedCodec == cVal
                                        Surface(
                                            onClick = { selectedCodec = cVal },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            modifier = Modifier.weight(1f).height(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = codec,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        isOperating = true
                                        operationResult = null
                                        coroutineScope.launch {
                                            val res = configService.applyVideoEncoding(
                                                device, user.trim(), password.trim(),
                                                CameraVideoSettings(
                                                    resolution = selectedResolution,
                                                    fps = selectedFps,
                                                    codec = selectedCodec
                                                )
                                            )
                                            isOperating = false
                                            operationResult = res.getOrNull() ?: res.exceptionOrNull()?.message
                                            Toast.makeText(context, operationResult, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = !isOperating,
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("LƯU ĐỘ PHÂN GIẢI & MÃ HÓA", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Bottom Close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Đóng cửa sổ cài đặt")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfessionalSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = "${value.toInt()}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth().height(26.dp)
            )
        }
    }
}
