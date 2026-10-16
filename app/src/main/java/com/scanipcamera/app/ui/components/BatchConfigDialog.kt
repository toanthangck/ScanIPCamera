package com.scanipcamera.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.NetworkConfigPayload

@Composable
fun BatchConfigDialog(
    devices: List<CameraDevice>,
    phoneGateway: String,
    phoneSubnetMask: String,
    suggestedFreeStartIp: String,
    defaultPassword: String = "admin123",
    onDismiss: () -> Unit,
    onApplyBatch: (payloads: List<Pair<CameraDevice, NetworkConfigPayload>>) -> Unit
) {
    val selectedDevices = remember { mutableStateMapOf<String, Boolean>() }
    var startIp by remember { mutableStateOf(suggestedFreeStartIp.ifEmpty { "192.168.1.200" }) }
    var subnetMask by remember { mutableStateOf(phoneSubnetMask.ifEmpty { "255.255.255.0" }) }
    var gateway by remember { mutableStateOf(phoneGateway.ifEmpty { "192.168.1.1" }) }
    var password by remember { mutableStateOf(defaultPassword) }

    // Initially select all devices
    LaunchedEffect(devices) {
        devices.forEach { dev -> selectedDevices[dev.id] = true }
    }

    val selectedList = remember(devices, selectedDevices.toMap()) {
        devices.filter { selectedDevices[it.id] == true }
    }

    // Custom per-device IP overrides
    val customIps = remember { mutableStateMapOf<String, String>() }
    var editingDeviceForIp by remember { mutableStateOf<CameraDevice?>(null) }
    var editingIpText by remember { mutableStateOf("") }

    // Helper to compute effective IP for a device (custom or auto-assigned)
    fun getEffectiveIp(dev: CameraDevice, index: Int): String {
        customIps[dev.id]?.let { if (it.isNotBlank()) return it }
        val parts = startIp.split(".")
        return if (parts.size == 4) {
            val prefix = "${parts[0]}.${parts[1]}.${parts[2]}."
            val startOctet = parts[3].toIntOrNull() ?: 200
            val newOctet = (startOctet + index).coerceAtMost(254)
            "$prefix$newOctet"
        } else {
            startIp
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 20.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DynamicForm, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Đổi IP Hàng Loạt (Batch IP)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Tự động gán dải IP liên tiếp • Chạm vào ô IP để chỉnh riêng theo ý mình", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Input controls (Start IP, Subnet, Gateway, Password)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startIp,
                        onValueChange = { startIp = it },
                        label = { Text("IP Bắt đầu", fontSize = 11.sp) },
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp),
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = gateway,
                        onValueChange = { gateway = it },
                        label = { Text("Gateway", fontSize = 11.sp) },
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = subnetMask,
                        onValueChange = { subnetMask = it },
                        label = { Text("Subnet Mask", fontSize = 11.sp) },
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp),
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Mật khẩu admin", fontSize = 11.sp) },
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick selector & Select All / Deselect All
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Đã chọn ${selectedList.size} / ${devices.size} thiết bị",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = { devices.forEach { selectedDevices[it.id] = true } },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Chọn tất cả", fontSize = 11.sp)
                        }
                        TextButton(
                            onClick = { selectedDevices.clear() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Bỏ chọn", fontSize = 11.sp)
                        }
                    }
                }

                // Table of devices with Checkboxes & Proposed IP
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(devices) { index, device ->
                            val isChecked = selectedDevices[device.id] == true
                            val effectiveIp = getEffectiveIp(device, selectedList.indexOfFirst { it.id == device.id }.coerceAtLeast(0))
                            val isCustom = customIps[device.id]?.isNotBlank() == true

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isChecked) MaterialTheme.colorScheme.surface else Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isChecked) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else Color.Transparent
                                ),
                                onClick = { selectedDevices[device.id] = !isChecked },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { selectedDevices[device.id] = it },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "[${device.brand.displayName}] ${device.model.ifEmpty { "Camera IP" }}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        )
                                        Text(
                                            text = "IP cũ: ${device.ipv4Address} • MAC: ${device.macAddress}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (isChecked) {
                                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            color = if (isCustom) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF0284C7).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isCustom) Color(0xFF10B981) else Color(0xFF0284C7).copy(alpha = 0.35f)
                                            ),
                                            modifier = Modifier.clickable {
                                                editingDeviceForIp = device
                                                editingIpText = effectiveIp
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = effectiveIp,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (isCustom) Color(0xFF059669) else Color(0xFF0284C7)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Chỉnh sửa IP này",
                                                    modifier = Modifier.size(12.dp),
                                                    tint = if (isCustom) Color(0xFF059669) else Color(0xFF0284C7)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Hủy", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val payloads = selectedList.mapIndexed { index, dev ->
                                val effectiveIp = getEffectiveIp(dev, index)
                                dev to NetworkConfigPayload(
                                    macAddress = dev.macAddress,
                                    newIp = effectiveIp,
                                    newSubnetMask = subnetMask,
                                    newGateway = gateway,
                                    newPort = dev.httpPort,
                                    isDhcp = false,
                                    currentPassword = password
                                )
                            }
                            onApplyBatch(payloads)
                        },
                        enabled = selectedList.isNotEmpty() && startIp.isNotBlank(),
                        modifier = Modifier.weight(1.5f).height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Áp dụng (${selectedList.size} cam)", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }

    // Individual IP editing modal
    editingDeviceForIp?.let { targetDev ->
        AlertDialog(
            onDismissRequest = { editingDeviceForIp = null },
            icon = {
                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            title = {
                Text("Tùy chỉnh IP riêng", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "[${targetDev.brand.displayName}] ${targetDev.model.ifEmpty { "Camera" }}",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "MAC: ${targetDev.macAddress} • IP hiện tại: ${targetDev.ipv4Address}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editingIpText,
                        onValueChange = { editingIpText = it },
                        label = { Text("Địa chỉ IP mới") },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = editingIpText.trim()
                        if (trimmed.isNotBlank()) {
                            customIps[targetDev.id] = trimmed
                        }
                        editingDeviceForIp = null
                    }
                ) {
                    Text("Lưu IP")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (customIps.containsKey(targetDev.id)) {
                        TextButton(
                            onClick = {
                                customIps.remove(targetDev.id)
                                editingDeviceForIp = null
                            }
                        ) {
                            Text("Tự động lại", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { editingDeviceForIp = null }) {
                        Text("Hủy")
                    }
                }
            }
        )
    }
}
