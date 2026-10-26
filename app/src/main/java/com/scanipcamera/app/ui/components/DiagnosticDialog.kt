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
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.network.NetworkDiagnosticUtils
import com.scanipcamera.app.data.network.PingResult
import com.scanipcamera.app.data.network.PortStatus
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
    var portStatuses by remember { mutableStateOf<List<PortStatus>>(emptyList()) }

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
            portStatuses = NetworkDiagnosticUtils.checkCommonCameraPorts(
                device.ipv4Address,
                selectedPorts.toList().sorted()
            )
            isChecking = false
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
