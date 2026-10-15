package com.scanipcamera.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    val settingsDevice by viewModel.settingsDevice.collectAsState()
    val activatingDevice by viewModel.activatingDevice.collectAsState()
    val rtspDevice by viewModel.rtspDevice.collectAsState()
    val diagnosticDevice by viewModel.diagnosticDevice.collectAsState()
    val wanPortDevice by viewModel.wanPortDevice.collectAsState()
    val rebootingDevice by viewModel.rebootingDevice.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val conflictingIps by viewModel.conflictingIps.collectAsState()
    val siteProjects by viewModel.siteProjects.collectAsState()
    val googleUser by viewModel.googleUser.collectAsState()
    val editingProject by viewModel.editingProject.collectAsState()
    val timeSyncDevice by viewModel.timeSyncDevice.collectAsState()
    val showDefaultCredentialsDialog by viewModel.showDefaultCredentialsDialog.collectAsState()

    var defaultUser by remember(googleUser.defaultUsername) { mutableStateOf(googleUser.defaultUsername) }
    var defaultPassword by remember(googleUser.defaultCameraPassword) { mutableStateOf(googleUser.defaultCameraPassword) }

    var showWanDialogDirect by remember { mutableStateOf(false) }
    var showNetworkCard by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showAuthorDialog by remember { mutableStateOf(false) }
    var showBatchDialog by remember { mutableStateOf(false) }
    var showHandoverDialog by remember { mutableStateOf(false) }
    var showSiteProjectsDialog by remember { mutableStateOf(false) }
    var showGoogleSyncDialog by remember { mutableStateOf(false) }

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
                            fontSize = 17.sp
                        )
                        Text(
                            text = "SADP, Dahua & Port Forwarding Tool",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // 1. Toggle Search Bar
                    IconButton(
                        onClick = { showSearchBar = !showSearchBar }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Bật/Tắt tìm kiếm",
                            tint = if (showSearchBar || searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 2. Toggle Network (Wi-Fi + WAN) Banner
                    IconButton(
                        onClick = { showNetworkCard = !showNetworkCard }
                    ) {
                        Icon(
                            imageVector = if (showNetworkCard) Icons.Default.WifiTethering else Icons.Default.Wifi,
                            contentDescription = "Bật/Tắt thông tin Wi-Fi & WAN",
                            tint = if (showNetworkCard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 3. Author & Ownership Info (Verified Badge)
                    IconButton(
                        onClick = { showAuthorDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Thông tin tác giả & Hỗ trợ",
                            tint = Color(0xFF0284C7)
                        )
                    }

                    // 4. Export / Share Report button
                    IconButton(
                        onClick = { viewModel.exportDeviceReport() },
                        enabled = devices.isNotEmpty()
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Xuất báo cáo")
                    }

                    // 5. Google Account & Cloud Sync profile button
                    IconButton(
                        onClick = { showGoogleSyncDialog = true }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1A73E8),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = googleUser.displayName.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }

                    // 6. Refresh button
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
            // ConfigTool Mobile style: Credentials input row (Compact, non-clipped BasicTextField)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactCredentialField(
                    value = defaultUser,
                    onValueChange = { defaultUser = it },
                    placeholder = "Tài khoản",
                    leadingIcon = Icons.Default.Person,
                    modifier = Modifier.weight(1f)
                )
                CompactCredentialField(
                    value = defaultPassword,
                    onValueChange = { defaultPassword = it },
                    placeholder = "Mật khẩu",
                    leadingIcon = Icons.Default.Lock,
                    isPassword = true,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    onClick = { viewModel.openDefaultCredentialsDialog() },
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Cấu hình mật khẩu mặc định",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Technician Quick Action Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = { showBatchDialog = true },
                    shape = RoundedCornerShape(7.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.55f)),
                    modifier = Modifier.weight(1f).height(30.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Đổi IP loạt", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7), maxLines = 1)
                    }
                }

                Surface(
                    onClick = { showHandoverDialog = true },
                    shape = RoundedCornerShape(7.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.55f)),
                    modifier = Modifier.weight(1f).height(30.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Biên bản giao", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669), maxLines = 1)
                    }
                }

                Surface(
                    onClick = { showSiteProjectsDialog = true },
                    shape = RoundedCornerShape(7.dp),
                    color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.55f)),
                    modifier = Modifier.weight(1f).height(30.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Dự án (${siteProjects.size})", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED), maxLines = 1)
                    }
                }

                Surface(
                    onClick = { showGoogleSyncDialog = true },
                    shape = RoundedCornerShape(7.dp),
                    color = Color(0xFF4285F4).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4285F4).copy(alpha = 0.55f)),
                    modifier = Modifier.weight(0.85f).height(30.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF1A73E8), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Google", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A73E8), maxLines = 1)
                    }
                }
            }

            // IP Conflict Warning Alert Banner
            AnimatedVisibility(visible = conflictingIps.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Xung đột IP: ${conflictingIps.joinToString(", ")}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626)
                                )
                                Text(
                                    text = "Trùng địa chỉ IP khiến camera chập chờn!",
                                    fontSize = 9.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = { showBatchDialog = true },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Sửa ngay", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Compact 1-line Network & WAN Bar with Toggle Button (Saves massive screen space!)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
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

            // Scan Mode Selector (Compact 28dp segmented buttons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = { viewModel.setScanMode(ScanMode.UDP_DISCOVERY) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (scanMode == ScanMode.UDP_DISCOVERY) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (scanMode == ScanMode.UDP_DISCOVERY) MaterialTheme.colorScheme.primary else Color.Transparent
                    ),
                    modifier = Modifier.weight(1f).height(28.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Sensors,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (scanMode == ScanMode.UDP_DISCOVERY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Đa giao thức (SADP/Dahua/ONVIF)",
                            fontSize = 10.sp,
                            fontWeight = if (scanMode == ScanMode.UDP_DISCOVERY) FontWeight.Bold else FontWeight.Normal,
                            color = if (scanMode == ScanMode.UDP_DISCOVERY) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    onClick = { viewModel.setScanMode(ScanMode.SUBNET_PING) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (scanMode == ScanMode.SUBNET_PING) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (scanMode == ScanMode.SUBNET_PING) MaterialTheme.colorScheme.primary else Color.Transparent
                    ),
                    modifier = Modifier.weight(1f).height(28.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Lan,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (scanMode == ScanMode.SUBNET_PING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Quét dải IP (1..254)",
                            fontSize = 10.sp,
                            fontWeight = if (scanMode == ScanMode.SUBNET_PING) FontWeight.Bold else FontWeight.Normal,
                            color = if (scanMode == ScanMode.SUBNET_PING) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            // Progress indicator
            AnimatedVisibility(visible = isScanning) {
                if (scanMode == ScanMode.SUBNET_PING && scanProgress != null) {
                    val progressFloat = scanProgress!!.first.toFloat() / scanProgress!!.second.toFloat()
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)) {
                        LinearProgressIndicator(
                            progress = { progressFloat },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Tiến độ quét IP: ${scanProgress!!.first}/${scanProgress!!.second} hosts (${(progressFloat * 100).toInt()}%)",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            // Search Bar (Toggleable to save maximum vertical space)
            AnimatedVisibility(visible = showSearchBar || searchQuery.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text("Tìm theo IP, MAC, Model, Serial...", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Xóa", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(
                            onClick = {
                                showSearchBar = false
                                viewModel.setSearchQuery("")
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Ẩn tìm kiếm", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Category Filter Chips (Camera vs NVR vs DVR)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                FilterChip(
                    selected = selectedCategoryFilter == null,
                    onClick = { viewModel.setCategoryFilter(null) },
                    label = { Text("Tất cả (${devices.size})", fontSize = 10.5.sp) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = selectedCategoryFilter == DeviceCategory.CAMERA,
                    onClick = {
                        viewModel.setCategoryFilter(if (selectedCategoryFilter == DeviceCategory.CAMERA) null else DeviceCategory.CAMERA)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(13.dp))
                    },
                    label = { Text("Camera IP", fontSize = 10.5.sp) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = selectedCategoryFilter == DeviceCategory.NVR,
                    onClick = {
                        viewModel.setCategoryFilter(if (selectedCategoryFilter == DeviceCategory.NVR) null else DeviceCategory.NVR)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(13.dp))
                    },
                    label = { Text("Đầu ghi NVR", fontSize = 10.5.sp) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = selectedCategoryFilter == DeviceCategory.DVR,
                    onClick = {
                        viewModel.setCategoryFilter(if (selectedCategoryFilter == DeviceCategory.DVR) null else DeviceCategory.DVR)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(13.dp))
                    },
                    label = { Text("Đầu ghi DVR / XVR", fontSize = 10.5.sp) },
                    modifier = Modifier.height(28.dp)
                )
            }

            // Brand Filter Chips (Filtered: Excluded Xiongmai and Unknown as requested!)
            val visibleBrands = remember {
                DeviceBrand.entries.filter { it != DeviceBrand.XIONGMAI && it != DeviceBrand.UNKNOWN }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                FilterChip(
                    selected = selectedBrandFilter == null,
                    onClick = { viewModel.setBrandFilter(null) },
                    label = { Text("Tất cả hãng", fontSize = 10.5.sp) },
                    modifier = Modifier.height(28.dp)
                )

                visibleBrands.forEach { brand ->
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
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(brand.badgeColor)
                            )
                        },
                        label = { Text(brand.displayName, fontSize = 10.5.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            // Author / Owner Quick Contact Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 1.dp)
                    .clickable { showAuthorDialog = true },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Bản quyền: toanhangts@gmail.com • Hotline/Zalo: 0965.620.086",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
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
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(devices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            phoneIp = wifiInfo.ipAddress,
                            phoneMask = wifiInfo.subnetMask,
                            isIpConflict = conflictingIps.contains(device.ipv4Address),
                            onConfigureClick = { viewModel.openConfigDialog(device) },
                            onSettingsClick = { viewModel.openSettingsDialog(device) },
                            onActivateClick = { viewModel.openActivationDialog(device) },
                            onRtspClick = { viewModel.openRtspDialog(device) },
                            onDiagnosticClick = { viewModel.openDiagnosticDialog(device) },
                            onWanPortClick = { viewModel.openWanPortDialog(device) },
                            onRebootClick = { viewModel.openRebootDialog(device) },
                            onTimeSyncClick = { viewModel.openTimeSyncDialog(device) }
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

    // Author & Ownership Dialog
    if (showAuthorDialog) {
        AuthorInfoDialog(onDismiss = { showAuthorDialog = false })
    }

    // Camera Settings Dialog (Image, WDR/BLC/HLC, Day/Night, Resolution, Codec, NTP, Live Sync)
    settingsDevice?.let { dev ->
        CameraSettingsDialog(
            device = dev,
            defaultUser = defaultUser,
            defaultPass = defaultPassword,
            onDismiss = { viewModel.closeSettingsDialog() }
        )
    }

    // Batch IP Configuration Dialog
    if (showBatchDialog) {
        BatchConfigDialog(
            devices = devices,
            phoneGateway = wifiInfo.gateway,
            phoneSubnetMask = wifiInfo.subnetMask,
            suggestedFreeStartIp = viewModel.getSuggestedFreeStartIp(),
            defaultPassword = defaultPassword,
            onDismiss = { showBatchDialog = false },
            onApplyBatch = { payloads ->
                viewModel.applyBatchNetworkConfig(payloads)
                showBatchDialog = false
            }
        )
    }

    // Handover Report & Add/Edit Project Dialog
    if (showHandoverDialog || editingProject != null) {
        HandoverReportDialog(
            devices = devices,
            wanIp = wanInfo?.ip ?: "",
            wifiSsid = wifiInfo.ssid,
            initialProject = editingProject,
            defaultTechnicianName = googleUser.technicianName,
            defaultTechnicianPhone = googleUser.technicianPhone,
            onDismiss = {
                showHandoverDialog = false
                viewModel.closeEditProjectDialog()
            },
            onSaveProject = { project ->
                viewModel.saveSiteProject(project)
                showHandoverDialog = false
                viewModel.closeEditProjectDialog()
            }
        )
    }

    // Site Projects Archive Dialog
    if (showSiteProjectsDialog) {
        SiteProjectsDialog(
            projects = siteProjects,
            onDismiss = { showSiteProjectsDialog = false },
            onAddNewProject = {
                showSiteProjectsDialog = false
                viewModel.closeEditProjectDialog()
                showHandoverDialog = true
            },
            onEditProject = { project ->
                showSiteProjectsDialog = false
                viewModel.openEditProjectDialog(project)
            },
            onDeleteProject = { id -> viewModel.deleteSiteProject(id) },
            onOpenGoogleSync = {
                showSiteProjectsDialog = false
                showGoogleSyncDialog = true
            }
        )
    }

    // Google Account & Cloud Sync Dialog
    if (showGoogleSyncDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        GoogleSyncDialog(
            user = googleUser,
            projectCount = siteProjects.size,
            onDismiss = { showGoogleSyncDialog = false },
            onSignIn = { email, name -> viewModel.signInGoogle(email, name) },
            onSignOut = { viewModel.signOutGoogle() },
            onSyncToGoogle = {
                val intent = viewModel.syncAllToGoogleCloud()
                if (intent != null) {
                    context.startActivity(android.content.Intent.createChooser(intent, "Sao lưu dự án lên Google Drive"))
                }
            },
            onRestoreFromJson = { json -> viewModel.restoreBackupFromJson(json) },
            onExportBackup = {
                val intent = viewModel.syncAllToGoogleCloud()
                if (intent != null) {
                    context.startActivity(android.content.Intent.createChooser(intent, "Chia sẻ file sao lưu"))
                }
            },
            onUpdateTechnicianProfile = { name, phone ->
                viewModel.updateTechnicianProfile(name, phone)
            },
            onUpdateDefaultCredentials = { u, camP, nvrP ->
                viewModel.updateDefaultCredentials(u, camP, nvrP)
            }
        )
    }

    // Time Sync & NTP Dialog
    timeSyncDevice?.let { dev ->
        val effectivePass = if (dev.category == DeviceCategory.NVR || dev.category == DeviceCategory.DVR) {
            googleUser.defaultNvrPassword
        } else {
            defaultPassword
        }
        TimeSyncDialog(
            device = dev,
            defaultUser = defaultUser,
            defaultPass = effectivePass,
            onDismiss = { viewModel.closeTimeSyncDialog() }
        )
    }

    // Default Credentials Dialog
    if (showDefaultCredentialsDialog) {
        DefaultCredentialsDialog(
            currentUsername = googleUser.defaultUsername,
            currentCameraPassword = googleUser.defaultCameraPassword,
            currentNvrPassword = googleUser.defaultNvrPassword,
            onDismiss = { viewModel.closeDefaultCredentialsDialog() },
            onSave = { u, camP, nvrP ->
                viewModel.updateDefaultCredentials(u, camP, nvrP)
            }
        )
    }
}

@Composable
private fun CompactCredentialField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(!isPassword) }

    Surface(
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace
                    ),
                    visualTransformation = if (!passwordVisible)
                        PasswordVisualTransformation()
                    else VisualTransformation.None,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (isPassword && value.isNotEmpty()) {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
