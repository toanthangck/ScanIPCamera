package com.scanipcamera.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanipcamera.app.data.model.DeviceBrand
import com.scanipcamera.app.data.scanner.ScanMode
import com.scanipcamera.app.ui.components.*
import com.scanipcamera.app.ui.viewmodel.ScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    modifier: Modifier = Modifier
) {
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val wifiInfo by viewModel.wifiInfo.collectAsState()
    val scanMode by viewModel.scanMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedBrandFilter by viewModel.selectedBrandFilter.collectAsState()

    val editingDevice by viewModel.editingDevice.collectAsState()
    val activatingDevice by viewModel.activatingDevice.collectAsState()
    val rtspDevice by viewModel.rtspDevice.collectAsState()
    val diagnosticDevice by viewModel.diagnosticDevice.collectAsState()
    val rebootingDevice by viewModel.rebootingDevice.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Scan IP Camera",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "SADP & Dahua ConfigTool Mobile",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Export / Share Report button
                    IconButton(
                        onClick = { viewModel.exportDeviceReport() },
                        enabled = devices.isNotEmpty()
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Xuất báo cáo")
                    }

                    // Refresh button
                    IconButton(
                        onClick = { viewModel.startScanning() },
                        enabled = !isScanning
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Quét lại")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.startScanning() },
                icon = {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    }
                },
                text = {
                    Text(
                        if (isScanning) {
                            if (scanMode == ScanMode.SUBNET_PING && scanProgress != null) {
                                "Đang quét: ${scanProgress!!.first}/${scanProgress!!.second}"
                            } else {
                                "Đang dò mạng..."
                            }
                        } else {
                            if (scanMode == ScanMode.UDP_DISCOVERY) "Quét thiết bị" else "Quét dải IP"
                        }
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Wi-Fi Status Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = if (wifiInfo.isConnected) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = wifiInfo.ssid,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "IP đt: ${wifiInfo.ipAddress.ifEmpty { "Chưa có IP" }} | GW: ${wifiInfo.gateway.ifEmpty { "N/A" }}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.refreshWifi() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Làm mới Wi-Fi",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Scan Mode Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = scanMode == ScanMode.UDP_DISCOVERY,
                    onClick = { viewModel.setScanMode(ScanMode.UDP_DISCOVERY) },
                    label = { Text("Đa giao thức (SADP/Dahua/ONVIF)", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )

                FilterChip(
                    selected = scanMode == ScanMode.SUBNET_PING,
                    onClick = { viewModel.setScanMode(ScanMode.SUBNET_PING) },
                    label = { Text("Quét dải IP (1..254)", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Lan, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }

            // Progress indicator
            AnimatedVisibility(visible = isScanning) {
                if (scanMode == ScanMode.SUBNET_PING && scanProgress != null) {
                    val progressFloat = scanProgress!!.first.toFloat() / scanProgress!!.second.toFloat()
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                        LinearProgressIndicator(
                            progress = { progressFloat },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Tiến độ quét IP: ${scanProgress!!.first}/${scanProgress!!.second} hosts (${(progressFloat * 100).toInt()}%)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Tìm theo IP, MAC, Model, Serial...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Xóa", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp)
            )

            // Brand Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedBrandFilter == null,
                    onClick = { viewModel.setBrandFilter(null) },
                    label = { Text("Tất cả (${devices.size})", fontSize = 12.sp) }
                )

                DeviceBrand.entries.forEach { brand ->
                    FilterChip(
                        selected = selectedBrandFilter == brand,
                        onClick = {
                            viewModel.setBrandFilter(
                                if (selectedBrandFilter == brand) null else brand
                            )
                        },
                        label = { Text(brand.displayName, fontSize = 12.sp) }
                    )
                }
            }

            // Device List or Empty State
            if (devices.isEmpty() && !isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Không có kết quả khớp với từ khóa" else "Chưa tìm thấy camera nào",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                "Thử tìm kiếm với địa chỉ IP hoặc MAC khác."
                            } else {
                                "1. Đảm bảo điện thoại kết nối cùng mạng Wi-Fi với Camera / Đầu ghi.\n2. Nếu switch chặn Multicast, hãy chuyển sang chế độ \"Quét dải IP (1..254)\"."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.startScanning() }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bắt đầu quét ngay")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(devices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            phoneIp = wifiInfo.ipAddress,
                            phoneMask = wifiInfo.subnetMask,
                            onConfigureClick = { viewModel.openConfigDialog(device) },
                            onActivateClick = { viewModel.openActivationDialog(device) },
                            onRtspClick = { viewModel.openRtspDialog(device) },
                            onDiagnosticClick = { viewModel.openDiagnosticDialog(device) },
                            onRebootClick = { viewModel.openRebootDialog(device) }
                        )
                    }
                }
            }
        }
    }

    // Config Dialog
    editingDevice?.let { device ->
        ConfigDialog(
            device = device,
            phoneIp = wifiInfo.ipAddress,
            phoneSubnetMask = wifiInfo.subnetMask,
            phoneGateway = wifiInfo.gateway,
            onDismiss = { viewModel.closeConfigDialog() },
            onSave = { payload -> viewModel.applyNetworkConfig(device, payload) }
        )
    }

    // Activation Dialog
    activatingDevice?.let { device ->
        ActivationDialog(
            device = device,
            onDismiss = { viewModel.closeActivationDialog() },
            onActivate = { newPassword -> viewModel.activateDevice(device, newPassword) }
        )
    }

    // RTSP Dialog
    rtspDevice?.let { device ->
        RtspDialog(
            device = device,
            onDismiss = { viewModel.closeRtspDialog() }
        )
    }

    // Diagnostic Ping & Port Dialog
    diagnosticDevice?.let { device ->
        DiagnosticDialog(
            device = device,
            onDismiss = { viewModel.closeDiagnosticDialog() }
        )
    }

    // Reboot Dialog
    rebootingDevice?.let { device ->
        RebootDialog(
            device = device,
            onDismiss = { viewModel.closeRebootDialog() },
            onConfirmReboot = { password -> viewModel.rebootDevice(device, password) }
        )
    }
}
