package com.scanipcamera.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
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
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.network.NetworkDiagnosticUtils
import com.scanipcamera.app.data.network.PingResult
import com.scanipcamera.app.data.network.PortStatus
import com.scanipcamera.app.ui.theme.StatusActive
import com.scanipcamera.app.ui.theme.StatusInactive

@Composable
fun DiagnosticDialog(
    device: CameraDevice,
    onDismiss: () -> Unit
) {
    var isChecking by remember { mutableStateOf(true) }
    var pingResult by remember { mutableStateOf<PingResult?>(null) }
    var portStatuses by remember { mutableStateOf<List<PortStatus>>(emptyList()) }

    val runCheck: () -> Unit = {
        isChecking = true
        // LaunchedEffect triggers or manual rerun
    }

    LaunchedEffect(device) {
        isChecking = true
        pingResult = NetworkDiagnosticUtils.testPing(device.ipv4Address)
        portStatuses = NetworkDiagnosticUtils.checkCommonCameraPorts(device.ipv4Address)
        isChecking = false
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
                    Text(text = "Kiểm tra kết nối", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = {
                        isChecking = true
                    },
                    enabled = !isChecking
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Kiểm tra lại")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Địa chỉ kiểm tra: ${device.ipv4Address} (${device.model})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isChecking) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Đang kiểm tra Ping & Các cổng dịch vụ...", fontSize = 13.sp)
                        }
                    }
                } else {
                    // Ping Result Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (pingResult?.isReachable == true) StatusActive.copy(alpha = 0.1f) else StatusInactive.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (pingResult?.isReachable == true) StatusActive else StatusInactive)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (pingResult?.isReachable == true) "Đang Online (Kết nối tốt)" else "Mất kết nối / Offline",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (pingResult?.isReachable == true) StatusActive else StatusInactive
                                )
                                Text(
                                    text = pingResult?.message ?: "",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Ports Table
                    Text(
                        text = "Trạng thái cổng dịch vụ (Port Scan):",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            portStatuses.forEach { portStatus ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp, horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = portStatus.serviceName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Port ${portStatus.port}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Surface(
                                        color = if (portStatus.isOpen) StatusActive.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (portStatus.isOpen) "Đang mở" else "Đóng",
                                            color = if (portStatus.isOpen) StatusActive else Color.Gray,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}
