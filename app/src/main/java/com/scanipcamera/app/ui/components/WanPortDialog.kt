package com.scanipcamera.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanipcamera.app.data.network.WanInfo
import com.scanipcamera.app.data.network.WanNetworkUtils
import com.scanipcamera.app.data.network.WanPortCheckResult
import com.scanipcamera.app.ui.theme.StatusActive
import com.scanipcamera.app.ui.theme.StatusInactive
import kotlinx.coroutines.launch

@Composable
fun WanPortDialog(
    wanInfo: WanInfo?,
    initialPort: Int = 80,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var portInput by remember { mutableStateOf(initialPort.toString()) }
    var isChecking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<WanPortCheckResult?>(null) }

    val commonPorts = listOf(
        Pair(80, "Web (80)"),
        Pair(8000, "Hik Server (8000)"),
        Pair(37777, "Dahua TCP (37777)"),
        Pair(554, "RTSP (554)"),
        Pair(8899, "XM Media (8899)")
    )

    fun testPort(portNum: Int) {
        val currentWan = wanInfo?.ip ?: ""
        if (currentWan.isBlank()) return
        isChecking = true
        checkResult = null
        coroutineScope.launch {
            checkResult = WanNetworkUtils.checkWanPort(currentWan, portNum)
            isChecking = false
        }
    }

    LaunchedEffect(wanInfo) {
        if (wanInfo != null && wanInfo.ip.isNotBlank()) {
            testPort(initialPort)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Kiểm tra mở cổng WAN (NAT)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // WAN Info Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Địa chỉ IP WAN công cộng:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = wanInfo?.ip?.ifEmpty { "Đang lấy IP WAN..." } ?: "Đang lấy IP WAN...",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!wanInfo?.isp.isNullOrBlank()) {
                            Text(
                                text = "Nhà mạng: ${wanInfo?.isp} (${wanInfo?.city}, ${wanInfo?.country})",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Quick Port Selector Chips
                Text(text = "Chọn cổng camera thường dùng:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    commonPorts.take(3).forEach { (port, label) ->
                        FilterChip(
                            selected = portInput == port.toString(),
                            onClick = {
                                portInput = port.toString()
                                testPort(port)
                            },
                            label = { Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f).height(32.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    commonPorts.drop(3).forEach { (port, label) ->
                        FilterChip(
                            selected = portInput == port.toString(),
                            onClick = {
                                portInput = port.toString()
                                testPort(port)
                            },
                            label = { Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f).height(32.dp)
                        )
                    }
                }

                // Custom Port Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it },
                        label = { Text("Cổng cần kiểm tra") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val p = portInput.toIntOrNull() ?: 80
                            testPort(p)
                        },
                        enabled = !isChecking && portInput.isNotBlank(),
                        modifier = Modifier.height(56.dp)
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Kiểm tra")
                        }
                    }
                }

                // Result Box
                checkResult?.let { res ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (res.isOpen) StatusActive.copy(alpha = 0.12f) else StatusInactive.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (res.isOpen) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = if (res.isOpen) StatusActive else StatusInactive,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (res.isOpen) "CỔNG ${res.port} ĐANG MỞ (OPEN)" else "CỔNG ${res.port} ĐANG ĐÓNG (CLOSED)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (res.isOpen) StatusActive else StatusInactive
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = res.message,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Technician guide note
                Text(
                    text = "💡 Lưu ý kỹ thuật: Nếu cổng ĐÓNG, hãy kiểm tra lại cấu hình NAT/Port Forwarding trên modem quang, DMZ hoặc đảm bảo nhà mạng không chặn dải IP NAT (CGNAT).",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}
