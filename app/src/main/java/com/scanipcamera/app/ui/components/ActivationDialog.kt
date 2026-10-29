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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceCategory

@Composable
fun ActivationDialog(
    device: CameraDevice,
    defaultPassword: String = "",
    defaultEmail: String = "",
    suggestedNewIp: String = "",
    phoneSubnetMask: String = "255.255.255.0",
    phoneGateway: String = "192.168.1.1",
    onDismiss: () -> Unit,
    onActivateWithConfig: (password: String, email: String, shouldChangeIp: Boolean, newIp: String, isDhcp: Boolean, syncTime: Boolean) -> Unit
) {
    var newPassword by remember { mutableStateOf(defaultPassword) }
    var confirmPassword by remember { mutableStateOf(defaultPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    var emailRecovery by remember { mutableStateOf(defaultEmail) }

    var shouldChangeIp by remember { mutableStateOf(true) }
    var newIp by remember { mutableStateOf(suggestedNewIp.ifEmpty { device.ipv4Address }) }
    var isDhcp by remember { mutableStateOf(false) }
    var syncTime by remember { mutableStateOf(true) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isRecorder = (device.category == DeviceCategory.NVR || device.category == DeviceCategory.DVR)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isRecorder) Icons.Default.Dns else Icons.Default.Videocam,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRecorder) "Khởi tạo Đầu ghi hình (NVR/DVR)" else "Khởi tạo Camera mới",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
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
                // Device info banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${device.brand.displayName} - ${device.model.ifEmpty { if (isRecorder) "Đầu ghi" else "Camera" }}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFD97706)
                            ) {
                                Text(
                                    text = "CHƯA ACTIVE",
                                    color = Color.White,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "IP xuất xưởng: ${device.ipv4Address} • MAC: ${device.macAddress}",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // 1. Password Section
                Text("1. Mật khẩu quản trị (Admin):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    label = { Text("Mật khẩu mới (tối thiểu 8 ký tự)") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    visualTransformation = if (!passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("Xác nhận mật khẩu") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    visualTransformation = if (!passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                )

                // 2. Email Recovery Section
                Text("2. Email khôi phục mật khẩu (Khuyến nghị):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = emailRecovery,
                    onValueChange = { emailRecovery = it },
                    label = { Text("Email kỹ thuật / Khách hàng") },
                    placeholder = { Text("admin@example.com") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                )

                // 3. Network IP Re-assignment Section
                Text("3. Cấu hình mạng ngay khi kích hoạt:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = shouldChangeIp,
                                onCheckedChange = { shouldChangeIp = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = "Đổi ngay sang IP mới (tránh trùng .108 / .64)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Camera mới thường có IP mặc định trùng nhau gây xung đột mạng.",
                                    fontSize = 9.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (shouldChangeIp) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp)
                            ) {
                                RadioButton(
                                    selected = !isDhcp,
                                    onClick = { isDhcp = false }
                                )
                                Text("IP tĩnh:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(6.dp))
                                OutlinedTextField(
                                    value = newIp,
                                    onValueChange = { newIp = it },
                                    enabled = !isDhcp,
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).height(46.dp)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp)
                            ) {
                                RadioButton(
                                    selected = isDhcp,
                                    onClick = { isDhcp = true }
                                )
                                Text("Tự động nhận IP từ Router (DHCP)", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // 4. Time Sync Option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = syncTime,
                        onCheckedChange = { syncTime = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Đồng bộ giờ điện thoại (GMT+7) vào thiết bị",
                        fontSize = 11.sp
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPassword.length < 8) {
                        errorMessage = "Mật khẩu phải có ít nhất 8 ký tự (gồm chữ và số)"
                    } else if (newPassword != confirmPassword) {
                        errorMessage = "Mật khẩu xác nhận không khớp"
                    } else {
                        onActivateWithConfig(
                            newPassword,
                            emailRecovery,
                            shouldChangeIp,
                            newIp,
                            isDhcp,
                            syncTime
                        )
                    }
                },
                enabled = newPassword.isNotBlank() && confirmPassword.isNotBlank()
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Kích hoạt thiết bị", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
