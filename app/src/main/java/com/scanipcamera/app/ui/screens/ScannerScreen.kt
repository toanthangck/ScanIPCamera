package com.scanipcamera.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanipcamera.app.data.model.DeviceBrand
import com.scanipcamera.app.data.model.DeviceCategory
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
    val wanInfo by viewModel.wanInfo.collectAsState()
    val scanMode by viewModel.scanMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedBrandFilter by viewModel.selectedBrandFilter.collectAsState()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsState()

    val editingDevice by viewModel.editingDevice.collectAsState()
    val activatingDevice by viewModel.activatingDevice.collectAsState()
    val rtspDevice by viewModel.rtspDevice.collectAsState()
    val diagnosticDevice by viewModel.diagnosticDevice.collectAsState()
    val wanPortDevice by viewModel.wanPortDevice.collectAsState()
    val rebootingDevice by viewModel.rebootingDevice.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    var showWanDialogDirect by remember { mutableStateOf(false) }
    var showNetworkCard by remember { mutableStateOf(false) }

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
                            text = "Scan IP Camera & NVR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "SADP, Dahua & Port Forwarding Tool",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Toggle Network (Wi-Fi + WAN) Banner
                    IconButton(
                        onClick = { showNetworkCard = !showNetworkCard }
                    ) {
                        Icon(
                            imageVector = if (showNetworkCard) Icons.Default.WifiTethering else Icons.Default.Wifi,
                            contentDescription = "Bật/Tắt thông tin Wi-Fi & WAN",
                            tint = if (showNetworkCard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FloatingActionButton(
                    onClick = { viewModel.startScanning() },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    containerColor = Color(0xFF0091EA),
                    contentColor = Color.White,
                    modifier = Modifier.size(66.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Text("QUÉT", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 2.dp
                ) {
                    Text(
                        text = "${devices.size} Thiết bị đã phát hiện",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ConfigTool Mobile style: Credentials input row
            var defaultUser by remember { mutableStateOf("admin") }
            var defaultPassword by remember { mutableStateOf("admin123") }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = defaultUser,
                        onValueChange = { defaultUser = it },
                        placeholder = { Text("Tài khoản", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).height(44.dp)
                    )
                    OutlinedTextField(
                        value = defaultPassword,
                        onValueChange = { defaultPassword = it },
                        placeholder = { Text("Mật khẩu", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).height(44.dp)
                    )
                }
            }

            // Compact 1-line Network & WAN Bar with Toggle Button (Saves massive screen space!)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 3.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                onClick = { showNetworkCard = !showNetworkCard }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = if (wifiInfo.isConnected) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = wifiInfo.ssid.ifEmpty { "Wi-Fi" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        if (wifiInfo.ipAddress.isNotBlank()) {
                            Text(
                                text = " (${wifiInfo.ipAddress})",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = { showNetworkCard = !showNetworkCard },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                imageVector = if (showNetworkCard) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (showNetworkCard) "Thu gọn" else "Mạng & WAN",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Expandable Detailed Network Dashboard Banner (LAN + WAN + Test Port)
            AnimatedVisibility(visible = showNetworkCard) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        // Row 1: LAN Wi-Fi info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                                        text = "IP: ${wifiInfo.ipAddress.ifEmpty { "N/A" }} | GW: ${wifiInfo.gateway.ifEmpty { "N/A" }}",
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
                                Icon(imageVector = Icons.Default.Sync, contentDescription = "Làm mới", modifier = Modifier.size(16.dp))
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Row 2: WAN Public IP & Port Forwarding Checker Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = Color(0xFF00897B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "WAN IP: ${wanInfo?.ip ?: "Đang lấy..."}",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!wanInfo?.isp.isNullOrBlank()) {
                                        Text(
                                            text = "${wanInfo?.isp} (${wanInfo?.country})",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            FilledTonalButton(
                                onClick = { showWanDialogDirect = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Test Port WAN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Scan Mode Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = scanMode == ScanMode.UDP_DISCOVERY,
                    onClick = { viewModel.setScanMode(ScanMode.UDP_DISCOVERY) },
                    label = { Text("Đa giao thức (SADP/Dahua/ONVIF)", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                )

                FilterChip(
                    selected = scanMode == ScanMode.SUBNET_PING,
                    onClick = { viewModel.setScanMode(ScanMode.SUBNET_PING) },
                    label = { Text("Quét dải IP (1..254)", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Lan, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                )
            }

            // Progress indicator
            AnimatedVisibility(visible = isScanning) {
                if (scanMode == ScanMode.SUBNET_PING && scanProgress != null) {
                    val progressFloat = scanProgress!!.first.toFloat() / scanProgress!!.second.toFloat()
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp)) {
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
                placeholder = { Text("Tìm theo IP, MAC, Model, Đầu ghi NVR...", fontSize = 13.sp) },
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

            // Category Filter Chips (Camera vs NVR vs DVR)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCategoryFilter == null,
                    onClick = { viewModel.setCategoryFilter(null) },
                    label = { Text("Tất cả (${devices.size})", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedCategoryFilter == DeviceCategory.CAMERA,
                    onClick = {
                        viewModel.setCategoryFilter(if (selectedCategoryFilter == DeviceCategory.CAMERA) null else DeviceCategory.CAMERA)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    label = { Text("Camera IP", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedCategoryFilter == DeviceCategory.NVR,
                    onClick = {
                        viewModel.setCategoryFilter(if (selectedCategoryFilter == DeviceCategory.NVR) null else DeviceCategory.NVR)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    label = { Text("Đầu ghi NVR", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedCategoryFilter == DeviceCategory.DVR,
                    onClick = {
                        viewModel.setCategoryFilter(if (selectedCategoryFilter == DeviceCategory.DVR) null else DeviceCategory.DVR)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    label = { Text("Đầu ghi DVR / XVR", fontSize = 11.sp) }
                )
            }

            // Brand Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedBrandFilter == null,
                    onClick = { viewModel.setBrandFilter(null) },
                    label = { Text("Tất cả hãng", fontSize = 11.sp) }
                )

                DeviceBrand.entries.forEach { brand ->
                    FilterChip(
                        selected = selectedBrandFilter == brand,
                        onClick = {
                            viewModel.setBrandFilter(
                                if (selectedBrandFilter == brand) null else brand
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(brand.badgeColor)
                            )
                        },
                        label = { Text(brand.displayName, fontSize = 11.sp) }
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
                            text = if (searchQuery.isNotEmpty()) "Không tìm thấy thiết bị phù hợp" else "Chưa tìm thấy camera hay đầu ghi nào",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                "Hãy thử tìm với IP hoặc từ khóa khác."
                            } else {
                                "1. Hãy đảm bảo điện thoại đang bắt cùng Wi-Fi với Camera / Đầu ghi.\n2. Bấm nút bên dưới để quét dò thiết bị."
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
                            onWanPortClick = { viewModel.openWanPortDialog(device) },
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

    // WAN Port Dialog (triggered from a device or direct button)
    if (showWanDialogDirect || wanPortDevice != null) {
        val initialPort = wanPortDevice?.let { dev ->
            if (dev.sdkPort > 0) dev.sdkPort else dev.httpPort
        } ?: 80
        WanPortDialog(
            wanInfo = wanInfo,
            initialPort = initialPort,
            onDismiss = {
                showWanDialogDirect = false
                viewModel.closeWanPortDialog()
            }
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
