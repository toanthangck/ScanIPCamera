package com.scanipcamera.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanipcamera.app.data.bluetooth.BleCameraDevice
import com.scanipcamera.app.data.bluetooth.BleCameraManager
import com.scanipcamera.app.data.scanner.SmartConfigHelper

@Composable
fun WifiCameraInitDialog(
    currentWifiSsid: String = "",
    defaultCameraPassword: String = "",
    onDismiss: () -> Unit,
    onSuccessFinish: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Bluetooth BLE, 1 = SmartConfig
    var wifiSsid by remember { mutableStateOf(currentWifiSsid.ifEmpty { "Wi-Fi 2.4GHz" }) }
    var wifiPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var adminPassword by remember { mutableStateOf(defaultCameraPassword) }

    // BLE states
    val isBleScanning by BleCameraManager.isScanning.collectAsState()
    val bleCameras by BleCameraManager.discoveredCameras.collectAsState()
    val bleStatusMsg by BleCameraManager.provisionStatus.collectAsState()
    var selectedBleCam by remember { mutableStateOf<BleCameraDevice?>(null) }
    var isBleProvisioning by remember { mutableStateOf(false) }
    var bleResultMsg by remember { mutableStateOf<String?>(null) }

    // SmartConfig states
    val isBroadcasting by SmartConfigHelper.isBroadcasting.collectAsState()
    val countdownSec by SmartConfigHelper.countdownSec.collectAsState()

    DisposableEffect(Unit) {
        BleCameraManager.startScan(context)
        onDispose {
            BleCameraManager.stopScan()
            SmartConfigHelper.stopSmartConfig()
        }
    }

    AlertDialog(
        onDismissRequest = {
            BleCameraManager.stopScan()
            SmartConfigHelper.stopSmartConfig()
            onDismiss()
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (selectedTab == 0) Icons.Default.BluetoothSearching else Icons.Default.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cài đặt & Khởi tạo Camera Wi-Fi",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = {
                    BleCameraManager.stopScan()
                    SmartConfigHelper.stopSmartConfig()
                    onDismiss()
                }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng", modifier = Modifier.size(18.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tab Selector (Bluetooth BLE vs SmartConfig)
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            BleCameraManager.startScan(context)
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Bluetooth BLE", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            BleCameraManager.stopScan()
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SmartConfig", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }

                // Wi-Fi Credentials Input Row (Common to both modes)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Thông tin Wi-Fi nhà khách hàng (chỉ hỗ trợ 2.4GHz):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = wifiSsid,
                            onValueChange = { wifiSsid = it },
                            label = { Text("Tên Wi-Fi (SSID)", fontSize = 11.sp) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        )
                        OutlinedTextField(
                            value = wifiPassword,
                            onValueChange = { wifiPassword = it },
                            label = { Text("Mật khẩu Wi-Fi", fontSize = 11.sp) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            visualTransformation = if (!passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        )
                    }
                }

                // TAB 0: BLUETOOTH BLE PROVISIONING
                if (selectedTab == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isBleScanning) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = "Camera phát sóng Bluetooth (${bleCameras.size}):",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(
                                onClick = { BleCameraManager.startScan(context) },
                                enabled = !isBleScanning
                            ) {
                                Text("Quét lại", fontSize = 11.sp)
                            }
                        }

                        if (bleCameras.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = bleStatusMsg ?: "Đang dò tìm camera Bluetooth...",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "💡 Lưu ý: Hãy cắm nguồn camera và đứng cách camera 1-3 mét. Các model hỗ trợ: Imou Cruiser 2, Ranger RC, EZVIZ H3c/H6c, Dahua Hero...",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        } else {
                            // List of discovered BLE cameras
                            bleCameras.forEach { cam ->
                                val isSelected = selectedBleCam?.address == cam.address
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedBleCam = cam },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF0284C7).copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(20.dp))
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(cam.name, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                                Text("MAC: ${cam.address} • Sóng: ${cam.rssi} dBm", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(cam.signalDescription, fontSize = 9.5.sp, color = Color(0xFF059669), fontWeight = FontWeight.SemiBold)
                                            }
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedBleCam = cam }
                                        )
                                    }
                                }
                            }

                            // Action button when camera selected
                            if (selectedBleCam != null) {
                                Button(
                                    onClick = {
                                        isBleProvisioning = true
                                        bleResultMsg = null
                                        BleCameraManager.provisionWifi(
                                            context = context,
                                            cam = selectedBleCam!!,
                                            ssid = wifiSsid,
                                            wifiPass = wifiPassword,
                                            adminPass = adminPassword,
                                            onProgress = { bleResultMsg = it },
                                            onComplete = { success, msg ->
                                                isBleProvisioning = false
                                                bleResultMsg = msg
                                                if (success) {
                                                    onSuccessFinish()
                                                }
                                            }
                                        )
                                    },
                                    enabled = !isBleProvisioning && wifiSsid.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth().height(44.dp)
                                ) {
                                    if (isBleProvisioning) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                    } else {
                                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text("Kết nối Bluetooth & Cài đặt Wi-Fi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (bleResultMsg != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = bleResultMsg!!,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // TAB 1: SMARTCONFIG PROVISIONING
                if (selectedTab == 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Hướng dẫn SmartConfig:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF0284C7)
                                )
                                Text(
                                    text = "1. Cắm nguồn camera, bấm giữ nút Reset trên camera đến khi phát tiếng bíp (hoặc đèn nhấp nháy đỏ/xanh).\n2. Điền chính xác mật khẩu Wi-Fi 2.4GHz ở trên.\n3. Bấm nút phát sóng bên dưới. Điện thoại sẽ phát các gói tin cấu hình không dây trong 60 giây để camera tự nhận Wi-Fi.",
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        if (isBroadcasting) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.12f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp, color = Color(0xFF059669))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Đang phát sóng cấu hình SmartConfig: còn $countdownSec giây...",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF059669)
                                    )
                                    Text(
                                        text = "Hãy để điện thoại gần camera. Camera sẽ kêu bíp khi nhận được Wi-Fi.",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = { SmartConfigHelper.stopSmartConfig() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Dừng phát sóng")
                            }
                        } else {
                            Button(
                                onClick = {
                                    SmartConfigHelper.startSmartConfig(
                                        ssid = wifiSsid,
                                        password = wifiPassword,
                                        scope = coroutineScope,
                                        onComplete = { onSuccessFinish() }
                                    )
                                },
                                enabled = wifiSsid.isNotBlank() && wifiPassword.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                            ) {
                                Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bắt đầu phát sóng SmartConfig (60s)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                BleCameraManager.stopScan()
                SmartConfigHelper.stopSmartConfig()
                onDismiss()
            }) {
                Text("Đóng")
            }
        }
    )
}
