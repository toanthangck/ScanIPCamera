package com.scanipcamera.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.NetworkConfigPayload

@Composable
fun ConfigDialog(
    device: CameraDevice,
    onDismiss: () -> Unit,
    onSave: (NetworkConfigPayload) -> Unit
) {
    var ipAddress by remember { mutableStateOf(device.ipv4Address) }
    var subnetMask by remember { mutableStateOf(device.ipv4SubnetMask.ifEmpty { "255.255.255.0" }) }
    var gateway by remember { mutableStateOf(device.ipv4Gateway) }
    var port by remember { mutableStateOf(device.sdkPort.toString()) }
    var isDhcp by remember { mutableStateOf(device.isDhcpEnabled) }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Cấu hình mạng - ${device.brand.displayName}")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "MAC: ${device.macAddress}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // DHCP Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Bật DHCP (Tự nhận IP)", fontSize = 14.sp)
                    Switch(
                        checked = isDhcp,
                        onCheckedChange = { isDhcp = it }
                    )
                }

                if (!isDhcp) {
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        label = { Text("Địa chỉ IP") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = subnetMask,
                        onValueChange = { subnetMask = it },
                        label = { Text("Subnet Mask") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = gateway,
                        onValueChange = { gateway = it },
                        label = { Text("Default Gateway") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Cổng dịch vụ (SDK Port)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mật khẩu thiết bị (Bắt buộc)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Cần nhập đúng mật khẩu admin của camera để lưu") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        NetworkConfigPayload(
                            macAddress = device.macAddress,
                            newIp = ipAddress,
                            newSubnetMask = subnetMask,
                            newGateway = gateway,
                            newPort = port.toIntOrNull() ?: device.sdkPort,
                            isDhcp = isDhcp,
                            currentPassword = password
                        )
                    )
                },
                enabled = password.isNotBlank() && (isDhcp || ipAddress.isNotBlank())
            ) {
                Text("Lưu cấu hình")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
