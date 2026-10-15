package com.scanipcamera.app.ui.components

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.NetworkConfigPayload
import com.scanipcamera.app.data.network.NetworkUtils

@Composable
fun ConfigDialog(
    device: CameraDevice,
    phoneIp: String = "",
    phoneSubnetMask: String = "255.255.255.0",
    phoneGateway: String = "",
    defaultPassword: String = "",
    onDismiss: () -> Unit,
    onSave: (NetworkConfigPayload) -> Unit
) {
    var ipAddress by remember { mutableStateOf(device.ipv4Address) }
    var subnetMask by remember { mutableStateOf(device.ipv4SubnetMask.ifEmpty { "255.255.255.0" }) }

    // Pre-calculate fallback gateway so it's never empty
    val initialGw = remember(device.ipv4Gateway, phoneGateway, device.ipv4Address) {
        if (device.ipv4Gateway.isNotBlank()) {
            device.ipv4Gateway
        } else if (phoneGateway.isNotBlank()) {
            phoneGateway
        } else {
            val parts = device.ipv4Address.split(".")
            if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.1" else "192.168.1.1"
        }
    }
    var gateway by remember { mutableStateOf(initialGw) }

    val defaultSdkPort = if (device.sdkPort > 0) device.sdkPort.toString() else "37777"
    var port by remember { mutableStateOf(defaultSdkPort) }
    var isDhcp by remember { mutableStateOf(device.isDhcpEnabled) }
    var password by remember { mutableStateOf(defaultPassword) }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SettingsEthernet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cấu hình mạng - ${device.brand.displayName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Thiết bị: ${device.model} • MAC: ${device.macAddress.ifEmpty { "N/A" }}",
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick Subnet Match Button
                if (phoneIp.isNotBlank() && phoneGateway.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            isDhcp = false
                            subnetMask = phoneSubnetMask.ifBlank { "255.255.255.0" }
                            gateway = phoneGateway
                            ipAddress = NetworkUtils.suggestSameSubnetIp(phoneIp)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "⚡ Đổi cùng lớp mạng Wi-Fi ($phoneGateway)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // DHCP Switch in a subtle rounded surface
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Bật DHCP", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Router tự cấp phát IP động",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isDhcp,
                            onCheckedChange = { isDhcp = it }
                        )
                    }
                }

                if (!isDhcp) {
                    // IP Address (Rounded with Sensors icon)
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        label = { Text("Địa chỉ IP mới") },
                        leadingIcon = {
                            Icon(Icons.Default.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Subnet Mask (Rounded with Layers icon)
                    OutlinedTextField(
                        value = subnetMask,
                        onValueChange = { subnetMask = it },
                        label = { Text("Subnet Mask") },
                        leadingIcon = {
                            Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Default Gateway (Pre-filled, Rounded with Router icon)
                    OutlinedTextField(
                        value = gateway,
                        onValueChange = { gateway = it },
                        label = { Text("Default Gateway") },
                        leadingIcon = {
                            Icon(Icons.Default.Router, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Cổng dịch vụ SDK (Bo tròn đẹp mắt, font số monospace, icon Dns)
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Cổng dịch vụ (SDK Port)") },
                        leadingIcon = {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Mật khẩu thiết bị (Bo tròn đẹp hơn, có icon khóa, toggle ẩn/hiện, ghi chú rõ ràng)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mật khẩu thiết bị (Bắt buộc)") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Ẩn mật khẩu" else "Hiện mật khẩu",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = {
                        Text(
                            text = "💡 Mật khẩu admin của thiết bị (tự động lấy từ màn hình chính)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalGateway = if (gateway.isNotBlank()) {
                        gateway.trim()
                    } else {
                        val parts = ipAddress.split(".")
                        if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.1" else "192.168.1.1"
                    }
                    onSave(
                        NetworkConfigPayload(
                            macAddress = device.macAddress,
                            newIp = ipAddress.trim(),
                            newSubnetMask = subnetMask.trim(),
                            newGateway = finalGateway,
                            newPort = port.trim().toIntOrNull() ?: device.sdkPort,
                            isDhcp = isDhcp,
                            currentPassword = password.trim()
                        )
                    )
                },
                shape = RoundedCornerShape(10.dp),
                enabled = password.isNotBlank() && (isDhcp || ipAddress.isNotBlank())
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Lưu cấu hình", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Hủy")
            }
        }
    )
}
