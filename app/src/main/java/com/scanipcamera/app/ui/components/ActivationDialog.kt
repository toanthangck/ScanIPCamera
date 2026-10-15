package com.scanipcamera.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanipcamera.app.data.model.CameraDevice

@Composable
fun ActivationDialog(
    device: CameraDevice,
    defaultPassword: String = "",
    onDismiss: () -> Unit,
    onActivate: (String) -> Unit
) {
    var newPassword by remember { mutableStateOf(defaultPassword) }
    var confirmPassword by remember { mutableStateOf(defaultPassword) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Kích hoạt thiết bị mới")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Thiết bị: ${device.model} (${device.macAddress})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Vui lòng đặt mật khẩu quản trị (admin) cho thiết bị (tối thiểu 8 ký tự gồm chữ hoa, chữ thường và số).",
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    label = { Text("Mật khẩu mới") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("Xác nhận mật khẩu") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPassword.length < 8) {
                        errorMessage = "Mật khẩu phải có ít nhất 8 ký tự"
                    } else if (newPassword != confirmPassword) {
                        errorMessage = "Mật khẩu xác nhận không khớp"
                    } else {
                        onActivate(newPassword)
                    }
                },
                enabled = newPassword.isNotBlank() && confirmPassword.isNotBlank()
            ) {
                Text("Kích hoạt ngay")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
