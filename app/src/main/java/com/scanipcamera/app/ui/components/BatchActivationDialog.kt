package com.scanipcamera.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
fun BatchActivationDialog(
    unactivatedDevices: List<CameraDevice>,
    defaultPassword: String = "",
    defaultEmail: String = "",
    suggestedStartIp: String = "192.168.1.201",
    phoneGateway: String = "192.168.1.1",
    phoneSubnetMask: String = "255.255.255.0",
    onDismiss: () -> Unit,
    onApplyBatch: (
        selectedDevices: List<CameraDevice>,
        password: String,
        email: String,
        startIp: String,
        isDhcp: Boolean
    ) -> Unit
) {
    var selectedIds by remember { mutableStateOf(unactivatedDevices.map { it.id }.toSet()) }
    var password by remember { mutableStateOf(defaultPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf(defaultEmail) }
    var startIp by remember { mutableStateOf(suggestedStartIp) }
    var isDhcp by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Khởi tạo hàng loạt (${unactivatedDevices.size} thiết bị)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng", modifier = Modifier.size(18.dp))
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
                Text(
                    text = "Kích hoạt đồng thời nhiều camera/đầu ghi mới khui hộp và tự động chia dải IP để không bị trùng IP .108/.64:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // List of unactivated devices to select
                Text("Chọn thiết bị cần khởi tạo:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        unactivatedDevices.forEach { dev ->
                            val isChecked = selectedIds.contains(dev.id)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) selectedIds + dev.id else selectedIds - dev.id
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${dev.brand.displayName} - ${dev.model.ifEmpty { "Thiết bị" }}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                    Text(
                                        text = "IP hiện tại: ${dev.ipv4Address} • MAC: ${dev.macAddress}",
                                        fontSize = 9.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Password & Email Fields
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Mật khẩu chung cho tất cả (tối thiểu 8 ký tự)", fontSize = 11.sp) },
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
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email khôi phục mật khẩu (Khuyến nghị)", fontSize = 11.sp) },
                    placeholder = { Text("admin@example.com", fontSize = 11.sp) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )

                // Sequential IP Assignment
                Text("Cấu hình dải IP tuần tự:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = !isDhcp,
                                    onClick = { isDhcp = false }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gán IP tĩnh tuần tự tăng dần:", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                            if (!isDhcp) {
                                OutlinedTextField(
                                    value = startIp,
                                    onValueChange = { startIp = it },
                                    label = { Text("Địa chỉ IP bắt đầu") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 4.dp)
                                )
                                val prefix = startIp.substringBeforeLast(".", "192.168.1.") + "."
                                val startNum = startIp.substringAfterLast(".", "201").toIntOrNull() ?: 201
                                val endNum = startNum + (selectedIds.size - 1).coerceAtLeast(0)
                                Text(
                                    text = "➔ Tự động gán: $prefix$startNum đến $prefix$endNum",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 32.dp, top = 2.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = isDhcp,
                                    onClick = { isDhcp = true }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Bật DHCP cho tất cả (Router tự cấp IP)", fontSize = 11.sp)
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedIds.isEmpty()) {
                        errorMessage = "Vui lòng chọn ít nhất 1 thiết bị để khởi tạo"
                    } else if (password.length < 8) {
                        errorMessage = "Mật khẩu phải có ít nhất 8 ký tự"
                    } else {
                        val targets = unactivatedDevices.filter { selectedIds.contains(it.id) }
                        onApplyBatch(targets, password, email, startIp, isDhcp)
                    }
                },
                enabled = selectedIds.isNotEmpty() && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
            ) {
                Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Khởi tạo (${selectedIds.size} máy)", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
