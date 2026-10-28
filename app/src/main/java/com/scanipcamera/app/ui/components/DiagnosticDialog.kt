package com.scanipcamera.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceBrand
import com.scanipcamera.app.data.model.DeviceCategory
import com.scanipcamera.app.data.network.NetworkDiagnosticUtils
import com.scanipcamera.app.data.network.PingResult
import com.scanipcamera.app.data.network.PingStabilityResult
import com.scanipcamera.app.data.network.PortStatus
import com.scanipcamera.app.data.network.SecurityAuditResult
import com.scanipcamera.app.data.scanner.DahuaNetSdkManager
import com.scanipcamera.app.data.scanner.DevHddInfo
import com.scanipcamera.app.ui.theme.StatusActive
import com.scanipcamera.app.ui.theme.StatusInactive
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticDialog(
    device: CameraDevice,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var isChecking by remember { mutableStateOf(false) }
    var pingResult by remember { mutableStateOf<PingResult?>(null) }
    var pingStability by remember { mutableStateOf<PingStabilityResult?>(null) }
    var portStatuses by remember { mutableStateOf<List<PortStatus>>(emptyList()) }

    // Security Audit state
    var isAuditingSecurity by remember { mutableStateOf(false) }
    var securityAudit by remember { mutableStateOf<SecurityAuditResult?>(null) }

    // Storage / HDD state
    val isDahuaDevice = remember(device) {
        device.brand == DeviceBrand.DAHUA || device.brand == DeviceBrand.IMOU ||
        device.brand == DeviceBrand.KBVISION || device.category == DeviceCategory.NVR ||
        device.category == DeviceCategory.DVR || device.verifiedOpenPorts.contains(37777)
    }
    var hddPassword by remember { mutableStateOf("admin12345") }
    var isCheckingHdd by remember { mutableStateOf(false) }
    var hddList by remember { mutableStateOf<List<DevHddInfo>?>(null) }
    var hddMessage by remember { mutableStateOf<String?>(null) }

    // Standard camera ports list
    val defaultPorts = remember(device) {
        val list = mutableListOf(80, 554, 8000, 37777, 8899, 34567, 443, 8080)
        if (device.httpPort > 0 && !list.contains(device.httpPort)) list.add(device.httpPort)
        if (device.sdkPort > 0 && !list.contains(device.sdkPort)) list.add(device.sdkPort)
        if (device.rtspPort > 0 && !list.contains(device.rtspPort)) list.add(device.rtspPort)
        list
    }

    val availablePorts = remember { mutableStateListOf<Int>().apply { addAll(defaultPorts) } }

    // Pre-select the most relevant ports based on device brand/config
    var selectedPorts by remember {
        mutableStateOf(
            buildSet {
                add(80)
                add(554)
                if (device.httpPort > 0) add(device.httpPort)
                if (device.sdkPort > 0) add(device.sdkPort)
                if (device.rtspPort > 0) add(device.rtspPort)
                add(8000)
                add(37777)
            }
        )
    }

    var customPortInput by remember { mutableStateOf("") }

    fun runScan() {
        if (selectedPorts.isEmpty()) return
        isChecking = true
        coroutineScope.launch {
            pingResult = NetworkDiagnosticUtils.testPing(device.ipv4Address)
            pingStability = NetworkDiagnosticUtils.testPingStability(
                device.ipv4Address,
                count = 4,
                probePort = if (device.httpPort > 0) device.httpPort else 80
            )
            portStatuses = NetworkDiagnosticUtils.checkCommonCameraPorts(
                device.ipv4Address,
                selectedPorts.toList().sorted()
            )
            isChecking = false
        }
    }

    fun runSecurityAudit() {
        isAuditingSecurity = true
        coroutineScope.launch {
            securityAudit = NetworkDiagnosticUtils.auditDefaultCredentials(
                ip = device.ipv4Address,
                sdkPort = if (device.sdkPort > 0) device.sdkPort else 37777,
                isDahua = isDahuaDevice
            )
            isAuditingSecurity = false
        }
    }

    fun runHddCheck() {
        isCheckingHdd = true
        hddMessage = null
        coroutineScope.launch {
            val res = DahuaNetSdkManager.queryHddState(
                ip = device.ipv4Address,
                port = if (device.sdkPort > 0) device.sdkPort else 37777,
                user = "admin",
                pass = hddPassword.trim()
            )
            res.onSuccess { list ->
                hddList = list
                if (list.isEmpty()) {
                    hddMessage = "Không tìm thấy ổ cứng hoặc đầu ghi chưa gắn ổ cứng!"
                }
            }.onFailure { err ->
                hddList = null
                hddMessage = "Lỗi đọc ổ cứng: ${err.message}"
            }
            isCheckingHdd = false
        }
    }

    fun addCustomPort() {
        val port = customPortInput.trim().toIntOrNull()
        if (port != null && port in 1..65535) {
            if (!availablePorts.contains(port)) {
                availablePorts.add(port)
            }
            selectedPorts = selectedPorts + port
            customPortInput = ""
        }
    }

    // Run initial scan on open
    LaunchedEffect(device) {
        runScan()
    }

    fun getPortLabel(port: Int): String {
        return when (port) {
            80 -> "80 (HTTP)"
            554 -> "554 (RTSP)"
            8000 -> "8000 (Hik SDK)"
            37777 -> "37777 (Dahua)"
            8899 -> "8899 (XM ONVIF)"
            34567 -> "34567 (XM Net)"
            443 -> "443 (HTTPS)"
            8080 -> "8080 (Web)"
            5000 -> "5000 (Uniview)"
            1935 -> "1935 (RTMP)"
            device.httpPort -> "$port (Web Device)"
            device.sdkPort -> "$port (SDK Device)"
            device.rtspPort -> "$port (RTSP Device)"
            else -> "Port $port"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Kiểm tra kết nối & Cổng", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = { runScan() },
                    enabled = !isChecking && selectedPorts.isNotEmpty()
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Quét lại")
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
                // Device Sub-header
                Text(
                    text = "${device.ipv4Address} • [${device.brand.displayName}] ${device.model.ifEmpty { "Camera IP" }}",
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Ping Result Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (pingResult?.isReachable == true) StatusActive.copy(alpha = 0.12f) else StatusInactive.copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (pingResult?.isReachable == true) StatusActive else StatusInactive)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (pingResult?.isReachable == true) "Đang Online (Kết nối tốt)" else "Không phản hồi (Timeout / Offline)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (pingResult?.isReachable == true) StatusActive else StatusInactive
                            )
                            if (!pingResult?.message.isNullOrBlank()) {
                                Text(
                                    text = pingResult?.message ?: "",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (pingStability != null) {
                                Text(
                                    text = "Độ trễ: ${pingStability?.avgLatencyMs}ms (Min: ${pingStability?.minLatencyMs}ms, Max: ${pingStability?.maxLatencyMs}ms) • Rớt gói: ${pingStability?.lossRatePercent}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if ((pingStability?.lossRatePercent ?: 0) == 0) StatusActive else Color(0xFFDC2626),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Security Audit Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            securityAudit?.isVulnerable == true -> Color(0xFFEF4444).copy(alpha = 0.12f)
                            securityAudit?.isVulnerable == false -> Color(0xFF10B981).copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (securityAudit?.isVulnerable == true) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Bảo mật mật khẩu",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                            FilledTonalButton(
                                onClick = { runSecurityAudit() },
                                enabled = !isAuditingSecurity,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                if (isAuditingSecurity) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Kiểm tra pass mặc định", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (securityAudit != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = securityAudit?.message ?: "",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (securityAudit?.isVulnerable == true) Color(0xFFDC2626) else Color(0xFF059669)
                            )
                        }
                    }
                }

                // Storage / HDD Card
                if (isDahuaDevice) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Ổ cứng lưu trữ (Storage)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { runHddCheck() },
                                    enabled = !isCheckingHdd,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    if (isCheckingHdd) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("Kiểm tra ổ cứng", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            if (hddList != null && hddList!!.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                hddList!!.forEach { disk ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Ổ ${disk.index}: ${String.format(java.util.Locale.US, "%.1f GB", disk.totalGb)} (Trống ${String.format(java.util.Locale.US, "%.1f GB", disk.freeGb)})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = disk.statusText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (disk.status == 0) StatusActive else Color(0xFFDC2626)
                                        )
                                    }
                                }
                            } else if (hddMessage != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = hddMessage!!,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // PORT SELECTION SECTION
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chọn cổng muốn quét (${selectedPorts.size}):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row {
                        TextButton(
                            onClick = { selectedPorts = availablePorts.toSet() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Tất cả", fontSize = 11.sp)
                        }
                        TextButton(
                            onClick = { selectedPorts = emptySet() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Bỏ chọn", fontSize = 11.sp)
                        }
                    }
                }

                // Interactive Port Selection Chips FlowRow
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availablePorts.forEach { port ->
                        val isSelected = selectedPorts.contains(port)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedPorts = if (isSelected) selectedPorts - port else selectedPorts + port
                            },
                            label = {
                                Text(
                                    text = getPortLabel(port),
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            } else null,
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }

                // Custom Port Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = customPortInput,
                        onValueChange = { customPortInput = it },
                        label = { Text("Thêm cổng khác (1..65535)", fontSize = 11.sp) },
                        placeholder = { Text("VD: 8081, 9000, 34567...", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addCustomPort() }),
                        singleLine = true,
                        modifier = Modifier.weight(1f).height(50.dp)
                    )

                    FilledTonalButton(
                        onClick = { addCustomPort() },
                        enabled = customPortInput.trim().toIntOrNull()?.let { it in 1..65535 } == true,
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Thêm", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Thêm", fontSize = 12.sp)
                    }
                }

                // Action Scan Button
                Button(
                    onClick = { runScan() },
                    enabled = !isChecking && selectedPorts.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đang quét ${selectedPorts.size} cổng...", fontSize = 12.sp)
                    } else {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (selectedPorts.isEmpty()) "Hãy chọn ít nhất 1 cổng" else "Quét ${selectedPorts.size} cổng đã chọn",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // SCAN RESULTS DISPLAY
                if (portStatuses.isNotEmpty()) {
                    val openCount = portStatuses.count { it.isOpen }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "KẾT QUẢ QUÉT CỔNG:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$openCount/${portStatuses.size} cổng đang mở",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (openCount > 0) StatusActive else Color.Gray
                        )
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            portStatuses.forEachIndexed { idx, portStatus ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp, horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (portStatus.isOpen) StatusActive else Color.Gray.copy(alpha = 0.6f))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = portStatus.serviceName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Port ${portStatus.port}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Surface(
                                        color = if (portStatus.isOpen) StatusActive.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (portStatus.isOpen) StatusActive.copy(alpha = 0.4f) else Color.Transparent
                                        )
                                    ) {
                                        Text(
                                            text = if (portStatus.isOpen) "Đang mở" else "Đóng",
                                            color = if (portStatus.isOpen) StatusActive else Color.Gray,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (idx < portStatuses.size - 1) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}
